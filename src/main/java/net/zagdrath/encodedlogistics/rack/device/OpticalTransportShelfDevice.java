/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.elcl.exec.RoutingCommands;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.optical.FiberTrace;
import net.zagdrath.encodedlogistics.optical.Multiplexer;
import net.zagdrath.encodedlogistics.optical.OpticalChannels;
import net.zagdrath.encodedlogistics.optical.SpanLoss;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.chassis.Chassis;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisLayout;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPart;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPartItem;

// The Optical Transport Shelf (6U, the optical handoff 4): carrier transport between two shelves, paired end to end
// with a Link Card. Ten slots: two Controller Cards (the second takes over if the first fails) and eight line cards:
//  Transponder Card: one client network (a Link Card used on it) onto one long-haul channel (1-40, its setting) -
//   channel n at one shelf joins its network with channel n's at the other (OpticalChannels), up to a Fiber Cable's
//   lanes less the span's loss.
//  Mux Card: eight transponder channels onto the line (without one the line carries a single channel); up to
//   shelfChannels a shelf.
//  Amplifier Card: launches the line amplified - each one at either end adds fiberSpanThreshold blocks of reach before
//   the span loses lanes.
//  Protection Card: 1+1 protection. The shelf's lines to its partner leave its rack at its connection points
//   (FiberTrace: through amplifiers, racks patched through a rack amplifier, a Network Bridge pair). The shortest is the
//   working path, another from a different connection point by a different fibre the protect path; each keeps its
//   connection point from then on. When the working
//   path fails it switches to protect within a tick (*PROTSWITCH), and back when it's restored if revertive (the card's
//   setting); CHGPROT switches by hand (for testing). Without a Protection Card it only uses the working path.
// Performance monitoring: each path's length, loss, lanes delivered, signal quality (100 % - 4 % a lane lost, 0 down)
// and protection state (DSPOPT, RTVOPTSTS). It drains chassisDrain FE/t and chassisCardDrain a card.
public class OpticalTransportShelfDevice extends RackDevice implements Chassis {
    public static final int ACTION_CHANNEL_DOWN = 0, ACTION_CHANNEL_UP = 1, ACTION_REVERTIVE = 2, ACTION_UNPAIR = 3, ACTION_CLEAR_NETWORK = 4;
    private static final int REFRESH_TICKS = 40, MUX_CHANNELS = 8, MAX_CHANNEL = 40;

    public enum Path {
        WORKING, PROTECT
    }

    private int[] shownParts = new int[0];
    private UUID id = UUID.randomUUID();
    private @Nullable BlockPos partnerRack;
    private @Nullable UUID partnerId;
    private Path active = Path.WORKING;
    // The connection points its working and protect paths leave the rack from (RackGeometry.Point ordinals; -1 none yet).
    private int workingStart = -1, protectStart = -1;
    private @Nullable Path forced;
    private long switchedAt = -1;
    private String switchReason = "";
    private int lastLost = -1, lastControllers = -1;

    // As last looked at.
    private FiberTrace.@Nullable Result working, protect;
    private boolean partnerRunning;
    private String partnerName = "";
    private final List<Channel> channels = new ArrayList<>();
    private int lastGeneration = -1, timer;

    // A transponder's channel as last looked at: its bay, number, network here and there, and whether it's carried and up.
    public record Channel(int slot, int number, @Nullable GlobalPos network, @Nullable GlobalPos far, boolean carried, boolean up) {}

    public OpticalTransportShelfDevice(RackDeviceType type) {
        super(type);
    }

    public UUID id() {
        return id;
    }

    @Override
    public ChassisLayout layout() {
        return ChassisLayout.SHELF;
    }

    @Override
    public OpticalTransportShelfDevice device() {
        return this;
    }

    @Override
    public int[] shownParts() {
        return shownParts;
    }

    @Override
    public void setShownParts(int[] parts) {
        shownParts = parts;
    }

    @Override
    public double drain() {
        return Chassis.drain(this);
    }

    // --- What it has ---

    public int controllers() {
        return count(ChassisPart.CONTROLLER_CARD);
    }

    public boolean running() {
        return controllers() > 0;
    }

    public boolean protection() {
        return count(ChassisPart.PROTECTION_CARD) > 0;
    }

    // Channels it can carry: eight per Mux Card (one without), up to shelfChannels.
    public int maxChannels() {
        int muxes = count(ChassisPart.MUX_CARD);
        return Math.min(Config.SHELF_CHANNELS.getAsInt(), muxes == 0 ? 1 : muxes * MUX_CHANNELS);
    }

    public boolean revertive() {
        for (ChassisLayout.Bay bay : layout().bays()) {
            if (part(bay) == ChassisPart.PROTECTION_CARD) {
                return ChassisPartItem.settings(stack(bay)).getBooleanOr("revertive", true);
            }
        }
        return true;
    }

    public Path activePath() {
        return active;
    }

    public FiberTrace.@Nullable Result path(Path path) {
        return path == Path.WORKING ? working : protect;
    }

    public FiberTrace.@Nullable Result activeTrace() {
        return path(active);
    }

    public boolean paired() {
        return partnerRack != null;
    }

    public String partnerName() {
        return partnerName;
    }

    public List<Channel> channels() {
        return channels;
    }

    public long switchedAt() {
        return switchedAt;
    }

    public String switchReason() {
        return switchReason;
    }

    // A path's lanes after its loss: a span shortened by the amplifier cards at both ends.
    public int lost(FiberTrace.@Nullable Result path) {
        if (path == null) {
            return 0;
        }
        int amplifiers = Math.min(1, count(ChassisPart.AMPLIFIER_CARD)) + partnerAmplifiers;
        return SpanLoss.lost(Math.max(0, path.span() - amplifiers * Config.FIBER_SPAN_THRESHOLD.getAsInt()));
    }

    public int lanes(FiberTrace.@Nullable Result path) {
        return path == null ? 0 : Math.max(1, SpanLoss.fiberLanes() - lost(path));
    }

    public int quality(FiberTrace.@Nullable Result path) {
        return path == null ? 0 : SpanLoss.quality(lost(path));
    }

    private int partnerAmplifiers;

    // Its span's name: both ends' names, this one first.
    public String spanName() {
        return deviceName() + "-" + (partnerName.isEmpty() ? "?" : partnerName);
    }

    // --- Pairing ---

    public void setPartner(@Nullable BlockPos rack, @Nullable UUID partner) {
        partnerRack = rack;
        partnerId = partner;
        workingStart = protectStart = -1;
        lastGeneration = -1;
        changed(false);
    }

    public static boolean pair(OpticalTransportShelfDevice a, OpticalTransportShelfDevice b) {
        if (a == b || a.rack() == null || b.rack() == null || a.rack() == b.rack() || a.rack().getLevel() != b.rack().getLevel()) {
            return false;
        }
        MinecraftServer server = a.rack().getLevel().getServer();
        for (OpticalTransportShelfDevice end : new OpticalTransportShelfDevice[] { a, b }) {
            OpticalTransportShelfDevice old = end.partner(server);
            if (old != null && old != a && old != b) {
                old.setPartner(null, null);
            }
        }
        a.setPartner(b.rack().getBlockPos(), b.id);
        b.setPartner(a.rack().getBlockPos(), a.id);
        return true;
    }

    // The shelf with an id in the rack at a place, if it's loaded.
    public static @Nullable OpticalTransportShelfDevice find(MinecraftServer server, GlobalPos rack, UUID id) {
        ServerLevel level = server.getLevel(rack.dimension());
        if (level == null || !level.isLoaded(rack.pos()) || !(level.getBlockEntity(rack.pos()) instanceof RackBlockEntity found)) {
            return null;
        }
        for (RackDevice device : found.devices()) {
            if (device instanceof OpticalTransportShelfDevice shelf && shelf.id.equals(id)) {
                return shelf;
            }
        }
        return null;
    }

    public @Nullable OpticalTransportShelfDevice partner(@Nullable MinecraftServer server) {
        RackBlockEntity rack = rack();
        if (server == null || rack == null || partnerRack == null || partnerId == null || !(rack.getLevel() instanceof ServerLevel level)
                || !level.isLoaded(partnerRack) || !(level.getBlockEntity(partnerRack) instanceof RackBlockEntity other)) {
            return null;
        }
        for (RackDevice device : other.devices()) {
            if (device instanceof OpticalTransportShelfDevice shelf && shelf.id.equals(partnerId)) {
                return shelf;
            }
        }
        return null;
    }

    // --- Its cards' settings ---

    // A transponder's channel (its setting, else its place among the transponders) and client network.
    private int channelOf(ChassisLayout.Bay bay) {
        CompoundTag settings = ChassisPartItem.settings(stack(bay));
        int fallback = 0;
        for (ChassisLayout.Bay other : layout().bays()) {
            if (part(other) == ChassisPart.TRANSPONDER_CARD) {
                fallback++;
                if (other == bay) {
                    break;
                }
            }
        }
        return Math.clamp(settings.getIntOr("channel", fallback), 1, MAX_CHANNEL);
    }

    public static @Nullable GlobalPos networkOf(ItemStack transponder) {
        CompoundTag settings = ChassisPartItem.settings(transponder);
        String dimension = settings.getStringOr("net_dim", "");
        Identifier key = Identifier.tryParse(dimension);
        if (dimension.isEmpty() || key == null) {
            return null;
        }
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, key), BlockPos.of(settings.getLongOr("net_pos", 0)));
    }

    private void setting(ChassisLayout.Bay bay, java.util.function.Consumer<CompoundTag> change) {
        CompoundTag settings = ChassisPartItem.settings(stack(bay));
        change.accept(settings);
        items().set(bay.slot(), ChassisPartItem.withSettings(stack(bay), settings));
        lastGeneration = -1;
        changed(false);
    }

    // A Link Card's network on the transponder looked at (true when there's one there).
    public boolean setNetwork(ServerPlayer player, GlobalPos network) {
        ChassisLayout.Bay bay = Chassis.pick(this, player.getEyePosition(), player.getViewVector(1.0F), false);
        if (bay == null || part(bay) != ChassisPart.TRANSPONDER_CARD) {
            return false;
        }
        setting(bay, settings -> {
            settings.putString("net_dim", network.dimension().identifier().toString());
            settings.putLong("net_pos", network.pos().asLong());
        });
        return true;
    }

    // --- Looking at its paths ---

    @Override
    public void tick(ServerLevel level) {
        int generation = ControllerStructures.generation(level.getServer());
        if (generation == lastGeneration && ++timer < REFRESH_TICKS) {
            return;
        }
        lastGeneration = generation;
        timer = 0;
        refresh(level);
    }

    private void refresh(ServerLevel level) {
        MinecraftServer server = level.getServer();
        RackBlockEntity rack = rack();
        OpticalTransportShelfDevice partner = partner(server);
        working = protect = null;
        partnerRunning = false;
        partnerAmplifiers = 0;
        if (rack != null && partner != null && id.equals(partner.partnerId)) {
            partnerName = partner.deviceName();
            partnerRunning = partner.running() && partner.isOnline();
            partnerAmplifiers = Math.min(1, partner.count(ChassisPart.AMPLIFIER_CARD));
            BlockPos far = partnerRack;
            FiberTrace.Wanted wanted = new FiberTrace.Wanted() {
                @Override
                public boolean mux(BlockPos pos) {
                    return false;
                }

                @Override
                public boolean rack(BlockPos master) {
                    return master.equals(far);
                }
            };
            List<FiberTrace.Result> found = new ArrayList<>();
            for (RackGeometry.Point point : RackGeometry.Point.values()) {
                FiberTrace.Result result = FiberTrace.fromRack(level, rack, point, wanted);
                if (result != null) {
                    found.add(result);
                }
            }
            found.sort(Comparator.comparingInt(FiberTrace.Result::length));
            // The paths keep the connection points they were first found from: the working path is the shortest line
            // found while there was none, the protect path another from a different point by a different fibre.
            if (workingStart < 0 && !found.isEmpty()) {
                workingStart = found.getFirst().start();
            }
            working = from(found, workingStart);
            if (protectStart < 0 || protectStart == workingStart) {
                protectStart = -1;
                for (FiberTrace.Result other : found) {
                    if (other.start() != workingStart && (working == null || !other.last().equals(working.last()))) {
                        protectStart = other.start();
                        break;
                    }
                }
            }
            protect = from(found, protectStart);
        } else if (partnerRack == null) {
            partnerName = "";
        }
        protectionSwitch(level);
        // Its channels: transponders by channel number, the first maxChannels carried.
        boolean running = running() && isOnline() && partnerRunning && activeTrace() != null;
        List<ChassisLayout.Bay> transponders = new ArrayList<>();
        for (ChassisLayout.Bay bay : layout().bays()) {
            if (part(bay) == ChassisPart.TRANSPONDER_CARD) {
                transponders.add(bay);
            }
        }
        transponders.sort(Comparator.comparingInt(this::channelOf));
        channels.clear();
        List<OpticalChannels.Channel> links = new ArrayList<>();
        int lanes = Math.min(SpanLoss.fiberLanes(), lanes(activeTrace()));
        for (int i = 0; i < transponders.size(); i++) {
            ChassisLayout.Bay bay = transponders.get(i);
            int number = channelOf(bay);
            GlobalPos network = networkOf(stack(bay));
            GlobalPos far = running && partner != null ? partner.channelNetwork(number) : null;
            boolean carried = i < maxChannels();
            boolean up = running && carried && network != null && far != null;
            channels.add(new Channel(bay.slot(), number, network, far, carried, up));
            if (up && network.dimension().equals(far.dimension())) {
                links.add(new OpticalChannels.Channel(NodePos.of(network), NodePos.of(far), lanes));
            }
        }
        if (running && partner != null && id.compareTo(partner.id) < 0) {
            OpticalChannels.set(server, id, links);
        } else {
            OpticalChannels.clear(server, id);
        }
        FiberTrace.Result line = activeTrace();
        if (running && line != null) {
            FiberTrace.powerAmplifiers(level, line);
        }
        // *SPANLOSS when the active path's loss changes.
        int lost = line != null ? lost(line) : -1;
        if (lost != lastLost && lastLost >= 0 && lost >= 0) {
            ElclEvents.hardware(server, rack != null ? rack.network(this) : null, "*SPANLOSS", deviceName(), spanName(), Integer.toString(lost));
        }
        lastLost = lost;
        // Synced only when what clients see changed.
        String signature = active + "|" + (working != null ? working.length() : -1) + "|" + (protect != null ? protect.length() : -1) + "|" + channels
                + "|" + partnerName + "|" + lost;
        if (!signature.equals(lastSignature)) {
            lastSignature = signature;
            changed(false);
        }
    }

    private String lastSignature = "";

    // The line found from a connection point (by its ordinal), or null.
    private static FiberTrace.@Nullable Result from(List<FiberTrace.Result> found, int start) {
        for (FiberTrace.Result result : found) {
            if (result.start() == start) {
                return result;
            }
        }
        return null;
    }

    // The far end's network on a channel it carries, or null.
    private @Nullable GlobalPos channelNetwork(int number) {
        List<ChassisLayout.Bay> transponders = new ArrayList<>();
        for (ChassisLayout.Bay bay : layout().bays()) {
            if (part(bay) == ChassisPart.TRANSPONDER_CARD) {
                transponders.add(bay);
            }
        }
        transponders.sort(Comparator.comparingInt(this::channelOf));
        for (int i = 0; i < transponders.size() && i < maxChannels(); i++) {
            if (channelOf(transponders.get(i)) == number) {
                return networkOf(stack(transponders.get(i)));
            }
        }
        return null;
    }

    // 1+1: onto protect when working fails, back when it's restored (revertive); a forced path holds while it's up.
    private void protectionSwitch(ServerLevel level) {
        boolean workingUp = working != null, protectUp = protection() && protect != null;
        if (!protection()) {
            active = Path.WORKING;
            forced = null;
            return;
        }
        if (forced != null) {
            if (path(forced) != null) {
                setActive(level, forced, "manual");
                return;
            }
            forced = null;
        }
        if (active == Path.WORKING && !workingUp && protectUp) {
            setActive(level, Path.PROTECT, "working path down");
        } else if (active == Path.PROTECT && !protectUp && workingUp) {
            setActive(level, Path.WORKING, "protect path down");
        } else if (active == Path.PROTECT && workingUp && revertive()) {
            setActive(level, Path.WORKING, "working path restored");
        }
    }

    private void setActive(ServerLevel level, Path path, String reason) {
        if (active == path) {
            return;
        }
        active = path;
        switchedAt = level.getGameTime();
        switchReason = reason;
        ElclEvents.hardware(level.getServer(), rack() != null ? rack().network(this) : null, "*PROTSWITCH", deviceName(), spanName(),
                "*" + path.name() + " " + reason);
    }

    // CHGPROT: onto a path by hand (false: no such path up, or no Protection Card).
    public boolean force(Path path) {
        if (!protection() || path(path) == null || !(rack() != null && rack().getLevel() instanceof ServerLevel level)) {
            return false;
        }
        forced = path;
        setActive(level, path, "manual");
        changed(false);
        return true;
    }

    @Override
    public void onRemoved() {
        if (rack() != null && rack().getLevel() instanceof ServerLevel level) {
            OpticalChannels.clear(level.getServer(), id);
        }
    }

    @Override
    public void itemsChanged() {
        RackBlockEntity rack = rack();
        int now = controllers();
        if (lastControllers >= 0 && now < lastControllers && rack != null && rack.getLevel() instanceof ServerLevel level) {
            ElclEvents.hardware(level.getServer(), rack.network(this), "*CARDFAULT", deviceName(), "C",
                    now > 0 ? "controller failover" : "no controller");
        }
        lastControllers = now;
        lastGeneration = -1;
        changed(true);
    }

    // --- Status ---

    public boolean onProtect() {
        return protection() && active == Path.PROTECT;
    }

    @Override
    public RackDeviceInfo.Status status() {
        if (!isOnline()) {
            return RackDeviceInfo.Status.OFFLINE;
        }
        if (!running() || paired() && activeTrace() == null) {
            return RackDeviceInfo.Status.FAULT;
        }
        return onProtect() || controllers() < 2 ? RackDeviceInfo.Status.WARNING : RackDeviceInfo.Status.ONLINE;
    }

    @Override
    public Component statusText() {
        if (status() == RackDeviceInfo.Status.WARNING) {
            return Component.translatable(onProtect() ? "hud.encodedlogistics.shelf.on_protect" : "hud.encodedlogistics.chassis.degraded");
        }
        return super.statusText();
    }

    @Override
    public @Nullable String modelVariant() {
        return status() == RackDeviceInfo.Status.WARNING ? RackDeviceType.DEGRADED : null;
    }

    public int channelsUp() {
        return (int) channels.stream().filter(Channel::up).count();
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.shelf.channels"),
                Component.literal(channelsUp() + " / " + maxChannels())));
        FiberTrace.Result line = activeTrace();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.shelf.quality"),
                Component.literal(line != null ? quality(line) + "%" : "—")));
        if (paired()) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.shelf.span"), Component.literal(spanName())));
        }
        return lines;
    }

    // --- Its bays ---

    @Override
    public PartState partState(ChassisLayout.Bay bay) {
        ChassisPart part = part(bay);
        if (part == null) {
            return PartState.empty(Component.empty());
        }
        boolean on = isOnline() && running();
        RackDeviceInfo.Status status = on ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE;
        String code = on ? "*ONLINE" : "*OFFLINE";
        return switch (part) {
            case CONTROLLER_CARD -> {
                boolean first = true;
                for (ChassisLayout.Bay other : layout().bays()) {
                    if (other.slot() < bay.slot() && part(other) == ChassisPart.CONTROLLER_CARD) {
                        first = false;
                    }
                }
                boolean live = isOnline();
                yield new PartState(live ? first ? "*ACTIVE" : "*STANDBY" : "*OFFLINE", live ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE,
                        Component.empty(), live);
            }
            case TRANSPONDER_CARD -> {
                Channel channel = channels.stream().filter(c -> c.slot() == bay.slot()).findFirst().orElse(null);
                int number = channelOf(bay);
                Component detail;
                if (networkOf(stack(bay)) == null) {
                    detail = Component.translatable("hud.encodedlogistics.shelf.no_network", number);
                } else if (channel != null && !channel.carried()) {
                    detail = Component.translatable("hud.encodedlogistics.shelf.no_capacity", number);
                } else if (channel != null && channel.up()) {
                    detail = Component.translatable("hud.encodedlogistics.shelf.channel_up", number, lanes(activeTrace()));
                } else {
                    detail = Component.translatable("hud.encodedlogistics.shelf.channel_down", number);
                }
                boolean up = on && channel != null && channel.up();
                yield new PartState(up ? "*ONLINE" : on ? "*FAILED" : "*OFFLINE", up ? RackDeviceInfo.Status.ONLINE : on ? RackDeviceInfo.Status.WARNING
                        : RackDeviceInfo.Status.OFFLINE, detail, up);
            }
            case MUX_CARD -> new PartState(code, status, Component.translatable("hud.encodedlogistics.shelf.mux", MUX_CHANNELS), on);
            case AMPLIFIER_CARD -> new PartState(code, status, Component.translatable("hud.encodedlogistics.shelf.reach",
                    Config.FIBER_SPAN_THRESHOLD.getAsInt()), on);
            case PROTECTION_CARD -> {
                if (on && active == Path.PROTECT) {
                    yield new PartState("*PROTECT", RackDeviceInfo.Status.WARNING, Component.literal(switchReason), true);
                }
                Component detail = protect == null ? Component.translatable("hud.encodedlogistics.shelf.no_protect")
                        : Component.translatable(revertive() ? "hud.encodedlogistics.shelf.revertive" : "hud.encodedlogistics.shelf.non_revertive");
                yield new PartState(code, protect == null && on ? RackDeviceInfo.Status.WARNING : status, detail, on);
            }
            default -> new PartState("*FAILED", RackDeviceInfo.Status.FAULT, Component.empty(), false);
        };
    }

    // --- Using it ---

    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, boolean atUnit) {
        return atUnit && Chassis.useOnBay(this, player, stack);
    }

    // Parts in and out; its cards' settings (BUILD on its network): a transponder's channel, the revertive switch, a
    // transponder's network cleared; unpairing.
    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (Chassis.handle(this, player, action, value)) {
            return;
        }
        RackBlockEntity rack = rack();
        if (rack == null || !NetworkAccess.check(player.level().getServer(), rack.network(this), player, RackPermission.BUILD)) {
            return;
        }
        if (action == ACTION_UNPAIR) {
            OpticalTransportShelfDevice partner = partner(player.level().getServer());
            if (partner != null && id.equals(partner.partnerId)) {
                partner.setPartner(null, null);
            }
            setPartner(null, null);
            return;
        }
        ChassisLayout.Bay bay = layout().bay(value);
        if (bay == null) {
            return;
        }
        ChassisPart part = part(bay);
        switch (action) {
            case ACTION_CHANNEL_DOWN, ACTION_CHANNEL_UP -> {
                if (part == ChassisPart.TRANSPONDER_CARD) {
                    int channel = Math.floorMod(channelOf(bay) - 1 + (action == ACTION_CHANNEL_UP ? 1 : -1), MAX_CHANNEL) + 1;
                    setting(bay, settings -> settings.putInt("channel", channel));
                }
            }
            case ACTION_CLEAR_NETWORK -> {
                if (part == ChassisPart.TRANSPONDER_CARD) {
                    setting(bay, settings -> {
                        settings.remove("net_dim");
                        settings.remove("net_pos");
                    });
                }
            }
            case ACTION_REVERTIVE -> {
                if (part == ChassisPart.PROTECTION_CARD) {
                    boolean now = ChassisPartItem.settings(stack(bay)).getBooleanOr("revertive", true);
                    setting(bay, settings -> settings.putBoolean("revertive", !now));
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
        Chassis.writePanel(this, output);
        MinecraftServer server = viewer.level().getServer();
        output.putString("span", spanName());
        output.putBoolean("paired", paired());
        output.putString("partner", partnerName);
        output.putBoolean("protection", protection());
        output.putString("active", active.name().toLowerCase(Locale.ROOT));
        output.putBoolean("revertive", revertive());
        output.putInt("controllers", controllers());
        output.putInt("max_channels", maxChannels());
        output.putInt("channels_up", channelsUp());
        output.putString("switched", switchedAt >= 0 ? RoutingCommands.clock(server, switchedAt).substring(0, 5) : "");
        output.putString("reason", switchReason);
        FiberTrace.Result line = activeTrace();
        output.putInt("quality", quality(line));
        ValueOutput.ValueOutputList paths = output.childrenList("paths");
        for (Path path : Path.values()) {
            FiberTrace.Result trace = path(path);
            ValueOutput child = paths.addChild();
            child.putString("name", path.name().toLowerCase(Locale.ROOT));
            child.putBoolean("up", trace != null);
            child.putBoolean("active", path == active);
            child.putInt("length", trace != null ? trace.length() : 0);
            child.putInt("loss", lost(trace));
            child.putInt("lanes", lanes(trace));
            child.putInt("quality", quality(trace));
            child.putBoolean("bridged", trace != null && trace.bridged());
        }
        ValueOutput.ValueOutputList list = output.childrenList("channel_list");
        for (Channel channel : channels) {
            ValueOutput child = list.addChild();
            child.putInt("slot", channel.slot());
            child.putInt("number", channel.number());
            child.putString("network", Multiplexer.networkName(server, channel.network()));
            child.putString("far", channel.far() != null ? Multiplexer.networkName(server, channel.far()) : "-");
            child.putBoolean("carried", channel.carried());
            child.putBoolean("up", channel.up());
            child.putInt("lanes", channel.up() ? lanes(line) : 0);
        }
    }

    @Override
    public void writeClient(ValueOutput output) {
        Chassis.writeClient(this, output);
    }

    @Override
    public void readClient(ValueInput input) {
        Chassis.readClient(this, input);
    }

    // --- Saving: its pairing goes with its item; its cards come out as items ---

    @Override
    public void saveSettings(ValueOutput output) {
        output.store("shelf_id", UUIDUtil.CODEC, id);
        if (partnerRack != null && partnerId != null) {
            output.putLong("partner_rack", partnerRack.asLong());
            output.store("partner_id", UUIDUtil.CODEC, partnerId);
        }
    }

    @Override
    public void loadSettings(ValueInput input) {
        id = input.read("shelf_id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        partnerRack = input.getLong("partner_rack").map(BlockPos::of).orElse(null);
        partnerId = input.read("partner_id", UUIDUtil.CODEC).orElse(null);
        lastGeneration = -1;
    }

    @Override
    public void save(ValueOutput output) {
        saveSettings(output);
        saveItems(output);
        output.putString("active_path", active.name());
        if (forced != null) {
            output.putString("forced", forced.name());
        }
        output.putInt("working_start", workingStart);
        output.putInt("protect_start", protectStart);
        output.putLong("switched_at", switchedAt);
        output.putString("switch_reason", switchReason);
    }

    @Override
    public void load(ValueInput input) {
        loadSettings(input);
        loadItems(input);
        active = "PROTECT".equals(input.getStringOr("active_path", "WORKING")) ? Path.PROTECT : Path.WORKING;
        String forcedName = input.getStringOr("forced", "");
        forced = forcedName.isEmpty() ? null : "PROTECT".equals(forcedName) ? Path.PROTECT : Path.WORKING;
        workingStart = input.getIntOr("working_start", -1);
        protectStart = input.getIntOr("protect_start", -1);
        switchedAt = input.getLongOr("switched_at", -1);
        switchReason = input.getStringOr("switch_reason", "");
        lastControllers = controllers();
    }
}
