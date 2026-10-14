/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.optical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.OpticalMultiplexerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.OpticalMultiplexerDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// An Optical Multiplexer's work, block or rack form (the optical handoff 2). It has multiplexerChannels channels, each
// given a client network (a Link Card copied from any block on it - or a segment, from a Segment Isolator's end) or
// left free. Paired with another multiplexer (a Link Card copied from one and used on the other), with a Fiber Cable
// line between them (the block's line port at its front; a rack one's line leaves its rack at any connection point),
// channel n at one end joins its network with channel n's at the other (OpticalChannels): up to a Fiber Cable's lanes
// each, less the line's span loss - the line carries every channel, not one cable's lanes. A channel is up while the
// line is, both multiplexers are online and both ends have a network on it.
//
// It looks at its line whenever the networks change, and every REFRESH_TICKS anyway.
public final class Multiplexer {
    public static final int ACTION_SELECT = 0, ACTION_CLEAR = 1, ACTION_UNPAIR = 2;
    private static final int REFRESH_TICKS = 40;

    // What it's in: the block or the rack device.
    public interface Host {
        @Nullable ServerLevel serverLevel();

        // Where it is: the block's position, or the rack's master.
        @Nullable GlobalPos where();

        boolean online();

        String deviceName();

        // Its line toward a partner (Wanted), or null when no fibre leads there.
        FiberTrace.@Nullable Result trace(ServerLevel level, FiberTrace.Wanted wanted);

        void multiplexerChanged();
    }

    public enum LineState {
        UNPAIRED, PARTNER_GONE, NO_LINE, UP
    }

    private final Host host;
    private UUID id = UUID.randomUUID();
    private @Nullable GlobalPos partnerAt;
    private @Nullable UUID partnerId;
    private final GlobalPos[] channels;
    private int selected = -1;

    // As last looked at.
    private LineState line = LineState.UNPAIRED;
    private FiberTrace.@Nullable Result trace;
    private boolean partnerOnline;
    private String partnerName = "";
    private final boolean[] up;
    private int lastGeneration = -1, timer;

    public Multiplexer(Host host) {
        this.host = host;
        int count = Config.MULTIPLEXER_CHANNELS.getAsInt();
        this.channels = new GlobalPos[count];
        this.up = new boolean[count];
    }

    public UUID id() {
        return id;
    }

    public int channelCount() {
        return channels.length;
    }

    public @Nullable GlobalPos channel(int index) {
        return index >= 0 && index < channels.length ? channels[index] : null;
    }

    public boolean up(int index) {
        return index >= 0 && index < up.length && up[index];
    }

    public int upCount() {
        int count = 0;
        for (boolean on : up) {
            count += on ? 1 : 0;
        }
        return count;
    }

    public int assigned() {
        return (int) Arrays.stream(channels).filter(java.util.Objects::nonNull).count();
    }

    public LineState lineState() {
        return line;
    }

    public FiberTrace.@Nullable Result trace() {
        return trace;
    }

    public boolean paired() {
        return partnerAt != null;
    }

    public String partnerName() {
        return partnerName;
    }

    // The lanes each up channel carries: a Fiber Cable's, less the line's loss.
    public int channelLanes() {
        return trace != null ? trace.lanes() : 0;
    }

    // Line down while paired: the fault look.
    public boolean fault() {
        return partnerAt != null && line != LineState.UP;
    }

    // --- Pairing and channels ---

    public void setPartner(@Nullable GlobalPos at, @Nullable UUID partner) {
        partnerAt = at;
        partnerId = partner;
        lastGeneration = -1;
        host.multiplexerChanged();
    }

    // Pairs two multiplexers with each other, unpairing any old partners (false: the same one).
    public static boolean pair(MinecraftServer server, Multiplexer a, Multiplexer b) {
        GlobalPos atA = a.host.where(), atB = b.host.where();
        if (a == b || atA == null || atB == null) {
            return false;
        }
        for (Multiplexer end : new Multiplexer[] { a, b }) {
            Multiplexer old = end.partner(server);
            if (old != null && old != a && old != b) {
                old.setPartner(null, null);
            }
        }
        a.setPartner(atB, b.id);
        b.setPartner(atA, a.id);
        return true;
    }

    public void unpair(MinecraftServer server) {
        Multiplexer old = partner(server);
        if (old != null && id.equals(old.partnerId)) {
            old.setPartner(null, null);
        }
        setPartner(null, null);
    }

    // Gives a channel a network (the selected channel, else the first free one); the channel given, or -1 when full.
    public int assign(GlobalPos network) {
        int channel = selected >= 0 && selected < channels.length ? selected : -1;
        if (channel < 0) {
            for (int i = 0; i < channels.length; i++) {
                if (channels[i] == null) {
                    channel = i;
                    break;
                }
            }
        }
        if (channel < 0) {
            return -1;
        }
        channels[channel] = network;
        selected = -1;
        lastGeneration = -1;
        host.multiplexerChanged();
        return channel;
    }

    // The partner, if it's loaded (and still there).
    public @Nullable Multiplexer partner(MinecraftServer server) {
        return partnerAt != null && partnerId != null ? find(server, partnerAt, partnerId) : null;
    }

    // The multiplexer with an id at a place (a block, or a rack's master), if loaded.
    public static @Nullable Multiplexer find(MinecraftServer server, GlobalPos at, UUID id) {
        ServerLevel level = server.getLevel(at.dimension());
        if (level == null || !level.isLoaded(at.pos())) {
            return null;
        }
        if (level.getBlockEntity(at.pos()) instanceof OpticalMultiplexerBlockEntity block) {
            return block.multiplexer().id.equals(id) ? block.multiplexer() : null;
        }
        if (level.getBlockEntity(at.pos()) instanceof RackBlockEntity rack) {
            for (RackDevice device : rack.devices()) {
                if (device instanceof OpticalMultiplexerDevice mux && mux.multiplexer().id.equals(id)) {
                    return mux.multiplexer();
                }
            }
        }
        return null;
    }

    // --- Looking at the line ---

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
        LineState before = line;
        boolean[] was = up.clone();
        Multiplexer partner = partner(server);
        GlobalPos self = host.where();
        trace = null;
        partnerOnline = false;
        if (partnerAt == null || self == null) {
            line = LineState.UNPAIRED;
            partnerName = "";
        } else if (partner == null || !id.equals(partner.partnerId)) {
            line = LineState.PARTNER_GONE;
        } else {
            partnerName = partner.host.deviceName();
            partnerOnline = partner.host.online();
            GlobalPos far = partnerAt;
            trace = far.dimension().equals(level.dimension()) ? host.trace(level, new FiberTrace.Wanted() {
                @Override
                public boolean mux(BlockPos pos) {
                    return pos.equals(far.pos());
                }

                @Override
                public boolean rack(BlockPos master) {
                    return master.equals(far.pos());
                }
            }) : null;
            line = trace != null ? LineState.UP : LineState.NO_LINE;
        }
        boolean running = line == LineState.UP && host.online() && partnerOnline;
        List<OpticalChannels.Channel> links = new ArrayList<>();
        for (int i = 0; i < channels.length; i++) {
            GlobalPos far = running ? partner.channel(i) : null;
            up[i] = running && channels[i] != null && far != null;
            if (up[i] && channels[i].dimension().equals(far.dimension())) {
                links.add(new OpticalChannels.Channel(NodePos.of(channels[i]), NodePos.of(far), Math.min(SpanLoss.fiberLanes(), trace.lanes())));
            }
        }
        // The end with the lower id sets the pair's channels.
        if (running && id.compareTo(partner.id) < 0) {
            OpticalChannels.set(server, id, links);
        } else {
            OpticalChannels.clear(server, id);
        }
        if (running && trace != null) {
            FiberTrace.powerAmplifiers(level, trace);
        }
        if (before != line || !Arrays.equals(was, up)) {
            host.multiplexerChanged();
        }
    }

    // Taken out, broken or unloaded: its channels go.
    public void removed(ServerLevel level) {
        OpticalChannels.clear(level.getServer(), id);
    }

    // --- Its popup, panel and ELCL ---

    // A network's name as the Router's tables show it (ELNET02), or where it is.
    public static String networkName(MinecraftServer server, @Nullable GlobalPos node) {
        if (node == null) {
            return "-";
        }
        ServerLevel level = server.getLevel(node.dimension());
        NetworkRef network = level != null ? ControllerStructures.networkOf(level, node.pos()) : null;
        return network != null ? RouterDevice.networkName(server, network)
                : String.format(Locale.ROOT, "%d,%d,%d", node.pos().getX(), node.pos().getY(), node.pos().getZ());
    }

    public List<RackDeviceInfo.InfoLine> lines() {
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.mux.channels"),
                Component.translatable("hud.encodedlogistics.mux.channels_value", upCount(), channels.length)));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.mux.line"), lineText()));
        return lines;
    }

    public Component lineText() {
        return switch (line) {
            case UNPAIRED -> Component.translatable("hud.encodedlogistics.mux.unpaired");
            case PARTNER_GONE -> Component.translatable("hud.encodedlogistics.mux.partner_gone");
            case NO_LINE -> Component.translatable("hud.encodedlogistics.mux.no_line");
            case UP -> Component.translatable("hud.encodedlogistics.mux.length", length(trace.length()));
        };
    }

    // "612 m", "2.1 km".
    public static String length(int blocks) {
        return blocks >= 1000 ? String.format(Locale.ROOT, "%.1f km", blocks / 1000.0) : blocks + " m";
    }

    public RackDeviceInfo.Status status() {
        if (!host.online()) {
            return RackDeviceInfo.Status.OFFLINE;
        }
        return fault() ? RackDeviceInfo.Status.FAULT : RackDeviceInfo.Status.ONLINE;
    }

    public void writePanel(ValueOutput output, MinecraftServer server) {
        output.putInt("selected", selected);
        output.putString("line", line.name().toLowerCase(Locale.ROOT));
        output.putString("partner", partnerName);
        output.putBoolean("partner_online", partnerOnline);
        output.putBoolean("online", host.online());
        if (trace != null) {
            output.putInt("length", trace.length());
            output.putInt("span", trace.span());
            output.putInt("loss", trace.lost());
            output.putInt("lanes", trace.lanes());
            output.putInt("quality", trace.quality());
            output.putInt("amplifiers", trace.amplifiers().size());
        }
        ValueOutput.ValueOutputList list = output.childrenList("channels");
        for (int i = 0; i < channels.length; i++) {
            ValueOutput child = list.addChild();
            child.putBoolean("assigned", channels[i] != null);
            child.putString("network", channels[i] != null ? networkName(server, channels[i]) : "");
            child.putBoolean("up", up[i]);
            child.putInt("lanes", up[i] ? channelLanes() : 0);
        }
    }

    // Panel actions: select a channel for the next Link Card, clear one, unpair. BUILD on its network.
    public void handle(ServerPlayer player, @Nullable NetworkRef network, int action, int value) {
        if (!NetworkAccess.check(player.level().getServer(), network, player, RackPermission.BUILD)) {
            return;
        }
        switch (action) {
            case ACTION_SELECT -> {
                selected = selected == value || value < 0 || value >= channels.length ? -1 : value;
                host.multiplexerChanged();
            }
            case ACTION_CLEAR -> {
                if (value >= 0 && value < channels.length && channels[value] != null) {
                    channels[value] = null;
                    lastGeneration = -1;
                    host.multiplexerChanged();
                }
            }
            case ACTION_UNPAIR -> unpair(player.level().getServer());
            default -> {
            }
        }
    }

    // --- Saving ---

    public void save(ValueOutput output) {
        output.store("mux_id", UUIDUtil.CODEC, id);
        if (partnerAt != null && partnerId != null) {
            output.store("partner_at", GlobalPos.CODEC, partnerAt);
            output.store("partner_id", UUIDUtil.CODEC, partnerId);
        }
        ValueOutput.ValueOutputList list = output.childrenList("mux_channels");
        for (int i = 0; i < channels.length; i++) {
            if (channels[i] != null) {
                ValueOutput child = list.addChild();
                child.putInt("ch", i);
                child.store("node", GlobalPos.CODEC, channels[i]);
            }
        }
    }

    public void load(ValueInput input) {
        id = input.read("mux_id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        partnerAt = input.read("partner_at", GlobalPos.CODEC).orElse(null);
        partnerId = input.read("partner_id", UUIDUtil.CODEC).orElse(null);
        Arrays.fill(channels, null);
        for (ValueInput child : input.childrenListOrEmpty("mux_channels")) {
            int ch = child.getIntOr("ch", -1);
            Optional<GlobalPos> node = child.read("node", GlobalPos.CODEC);
            if (ch >= 0 && ch < channels.length && node.isPresent()) {
                channels[ch] = node.get();
            }
        }
        lastGeneration = -1;
    }
}
