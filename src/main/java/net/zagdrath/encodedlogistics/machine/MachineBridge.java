/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.display.SmallWirelessBridgeBlock;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.wireless.Wireless;
import net.zagdrath.encodedlogistics.wireless.WirelessClient;
import net.zagdrath.encodedlogistics.wireless.WirelessDevice;
import net.zagdrath.encodedlogistics.wireless.WirelessLink;

// A Small Wireless Bridge on one face of another mod's machine (MachineBridges keeps them, by the block it's on): a
// wireless client like a Wireless Port - linked with a Link Card to a Wireless Controller, it's a one-lane device on that
// controller's network while the controller admits it, and drains smallWirelessBridgeDrain over the air - through which
// the network sees and works the machine (MachineAccess). Not a block entity: the machine's block is the other mod's.
// It keeps the machine's own position (a multiblock's controller: one bridge per machine), the block it's on (when
// that's gone, so is the bridge), what the machine was last seen as, its device name (ARCCRU01) and its settings.
public final class MachineBridge implements WirelessClient, WirelessDevice {
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final Direction face;
    private final Identifier block;
    private BlockPos machine;
    private String type;
    private Component shown;
    private @Nullable WirelessLink link;
    private String deviceName;
    private boolean powerFromNetwork;
    // The Gateway that feeds it and takes its outputs over the air, or null for none.
    private @Nullable GlobalPos gateway;
    // Not saved: the level's bridges it's one of, whether the network has it online, and its LED.
    @Nullable MachineBridges owner;
    private boolean online;
    private SmallWirelessBridgeBlock.State led = SmallWirelessBridgeBlock.State.UNLINKED;
    // FE it put into the machine last tick, from the network.
    private int powered;
    private MachineAccess.@Nullable Listener events;

    MachineBridge(ResourceKey<Level> dimension, BlockPos pos, Direction face, Identifier block, BlockPos machine, String type, Component shown,
            @Nullable WirelessLink link, String deviceName, boolean powerFromNetwork, @Nullable GlobalPos gateway) {
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.face = face;
        this.block = block;
        this.machine = machine.immutable();
        this.type = type;
        this.shown = shown;
        this.link = link;
        this.deviceName = deviceName;
        this.powerFromNetwork = powerFromNetwork;
        this.gateway = gateway;
    }

    static Codec<MachineBridge> codec(ResourceKey<Level> dimension) {
        return RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(MachineBridge::pos),
                Direction.CODEC.fieldOf("face").forGetter(MachineBridge::face),
                Identifier.CODEC.fieldOf("block").forGetter(bridge -> bridge.block),
                BlockPos.CODEC.fieldOf("machine").forGetter(MachineBridge::machine),
                Codec.STRING.optionalFieldOf("type", "").forGetter(MachineBridge::type),
                ComponentSerialization.CODEC.optionalFieldOf("shown", Component.empty()).forGetter(MachineBridge::shown),
                WirelessLink.CODEC.optionalFieldOf("link").forGetter(bridge -> Optional.ofNullable(bridge.link)),
                Codec.STRING.optionalFieldOf("device_name", "").forGetter(MachineBridge::deviceName),
                Codec.BOOL.optionalFieldOf("power_from_network", false).forGetter(MachineBridge::powerFromNetwork),
                GlobalPos.CODEC.optionalFieldOf("gateway").forGetter(bridge -> Optional.ofNullable(bridge.gateway)))
                .apply(i, (pos, face, block, machine, type, shown, link, name, power, gateway) -> new MachineBridge(dimension, pos, face, block, machine,
                        type, shown, link.orElse(null), name, power, gateway.orElse(null))));
    }

    public BlockPos pos() {
        return pos;
    }

    // The face of the machine's block it's on (it sits in the space in front of that face).
    public Direction face() {
        return face;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    Identifier block() {
        return block;
    }

    // The machine's own position: the block, or a multiblock's controller.
    public BlockPos machine() {
        return machine;
    }

    // The machine's type id (arcforge:arc_crusher), as last seen.
    public String type() {
        return type;
    }

    // The machine's name (Arc Crusher), as last seen.
    public Component shown() {
        return shown;
    }

    public boolean powerFromNetwork() {
        return powerFromNetwork;
    }

    public boolean isOnline() {
        return online;
    }

    public @Nullable GlobalPos gateway() {
        return gateway;
    }

    // FE the network put into the machine last tick (power from network).
    public int powered() {
        return powered;
    }

    void setPowered(int powered) {
        this.powered = powered;
    }

    public SmallWirelessBridgeBlock.State led() {
        return led;
    }

    // --- Changes (MachineBridges saves and shows them) ---

    // What the machine is now: its position, type and name. True when that changed.
    boolean seen(MachineInfo info) {
        boolean changed = !info.position().equals(machine) || !info.type().equals(type) || !info.name().equals(shown);
        machine = info.position().immutable();
        type = info.type();
        shown = info.name();
        return changed;
    }

    boolean setOnline(boolean online) {
        boolean changed = this.online != online;
        this.online = online;
        return changed;
    }

    boolean setLed(SmallWirelessBridgeBlock.State led) {
        boolean changed = this.led != led;
        this.led = led;
        return changed;
    }

    public void setGateway(@Nullable GlobalPos gateway) {
        if (!java.util.Objects.equals(this.gateway, gateway)) {
            this.gateway = gateway;
            if (owner != null) {
                owner.setDirty();
            }
        }
    }

    public void setPowerFromNetwork(boolean on) {
        if (powerFromNetwork != on) {
            powerFromNetwork = on;
            if (owner != null) {
                owner.setDirty();
            }
        }
    }

    // The machine's device-name prefix, from its type: the first three letters of its first two words, or the first
    // six of a one-word type (arcforge:arc_crusher -> ARCCRU, arcforge:electrolyzer -> ELECTR); MCH if it has none.
    public static String typeCode(String type) {
        String path = type.substring(type.indexOf(':') + 1).toUpperCase(Locale.ROOT);
        List<String> words = new ArrayList<>();
        for (String word : path.split("[^A-Z0-9]+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        String code = words.isEmpty() ? "" : words.size() == 1 ? cut(words.getFirst(), 6) : cut(words.get(0), 3) + cut(words.get(1), 3);
        if (code.isEmpty()) {
            return "MCH";
        }
        return Character.isLetter(code.charAt(0)) ? code : "M" + cut(code, 5);
    }

    private static String cut(String text, int length) {
        return text.substring(0, Math.min(length, text.length()));
    }

    public String typeCode() {
        return typeCode(type);
    }

    // --- Events ---

    // What it tells ELCL of its machine while it's on a network (triggers): *MCHIDLE, *MCHFAULT and *MCHNOPWR as the
    // machine's status becomes idle, faulted or short of power; *MCHDONE for each operation it finishes.
    MachineAccess.Listener events(ServerLevel level) {
        if (events == null) {
            events = new MachineAccess.Listener() {
                @Override
                public void statusChanged(MachineInfo.State previous, MachineInfo.State current, Component reason) {
                    String event = switch (current) {
                        case IDLE -> "*MCHIDLE";
                        case FAULT -> "*MCHFAULT";
                        case NO_POWER -> "*MCHNOPWR";
                        default -> null;
                    };
                    NetworkRef network = ControllerStructures.networkOf(level, pos);
                    if (event != null && online && network != null) {
                        ElclEvents.machineStatus(level.getServer(), network, event, deviceName, reason.getString());
                    }
                }

                @Override
                public void operationCompleted(List<ItemStack> produced) {
                    NetworkRef network = ControllerStructures.networkOf(level, pos);
                    if (online && network != null) {
                        ElclEvents.machineDone(level.getServer(), network, deviceName, produced);
                    }
                }
            };
        }
        return events;
    }

    // --- WirelessClient ---

    @Override
    public Kind wirelessKind() {
        return Kind.MACHINE;
    }

    @Override
    public @Nullable WirelessLink link() {
        return link;
    }

    @Override
    public void setLink(@Nullable WirelessLink link) {
        if (java.util.Objects.equals(this.link, link)) {
            return;
        }
        this.link = link;
        if (owner != null) {
            owner.relinked();
        }
    }

    @Override
    public GlobalPos self() {
        return GlobalPos.of(dimension, pos);
    }

    // --- WirelessDevice ---

    @Override
    public String deviceName() {
        return deviceName;
    }

    @Override
    public void setDeviceName(String name) {
        if (!deviceName.equals(name)) {
            deviceName = name;
            if (owner != null) {
                owner.setDirty();
            }
        }
    }

    @Override
    public String controllerName(MinecraftServer server) {
        WirelessControllerDevice controller = Wireless.controller(server, link);
        return controller != null ? Wireless.name(controller) : "";
    }

    // The machine now, or null (Arcforge off, the machine unloaded or gone).
    public @Nullable MachineInfo info(MinecraftServer server) {
        MachineAccess access = MachineBridges.access();
        ServerLevel level = server.getLevel(dimension);
        return access != null && level != null && level.isLoaded(pos) ? access.info(level, pos) : null;
    }

    // Its status as the popup and Work with Devices show it: the link's problem first (not linked, no controller, no
    // Access Points, over capacity), then offline, then the machine's own (missing or not formed: a fault; held up -
    // no power, no input, output blocked, switched off: a warning, in the machine's words; idle or running: online).
    public RackDeviceInfo.Status status(MinecraftServer server, @Nullable MachineInfo info) {
        Wireless.Problem problem = Wireless.problem(server, this);
        if (problem != Wireless.Problem.NONE) {
            return problem.status();
        }
        if (!online) {
            return RackDeviceInfo.Status.OFFLINE;
        }
        if (info == null) {
            return RackDeviceInfo.Status.FAULT;
        }
        return switch (info.status()) {
            case IDLE, RUNNING -> RackDeviceInfo.Status.ONLINE;
            case NO_POWER, NO_INPUT, OUTPUT_BLOCKED, DISABLED -> RackDeviceInfo.Status.WARNING;
            case NOT_FORMED, FAULT, UNKNOWN -> RackDeviceInfo.Status.FAULT;
        };
    }

    public Component statusText(MinecraftServer server, @Nullable MachineInfo info) {
        Wireless.Problem problem = Wireless.problem(server, this);
        if (problem != Wireless.Problem.NONE) {
            return problem.text();
        }
        if (!online) {
            return RackDeviceInfo.Status.OFFLINE.text();
        }
        if (info == null) {
            return Component.translatable("hud.encodedlogistics.machine.missing");
        }
        return info.reason().getString().isEmpty() ? info.status().text() : info.reason();
    }

    // The popup's lines: the controller, then the machine's progress (with ticks left), energy, heat, what it's making
    // and whether the network powers it.
    @Override
    public RackDeviceInfo describe(MinecraftServer server) {
        MachineInfo info = info(server);
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        String controller = controllerName(server);
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.controller"),
                link == null ? Component.translatable("hud.encodedlogistics.wireless.use_card")
                        : controller.isEmpty() ? Component.translatable("hud.encodedlogistics.wireless.none") : Component.literal(controller)));
        if (info != null) {
            if (info.progress().isPresent()) {
                int percent = info.percent();
                Component value = info.ticksRemaining().isPresent()
                        ? Component.translatable("hud.encodedlogistics.machine.progress_left", percent, Math.max(0, info.ticksRemaining().getAsInt() + 19) / 20)
                        : Component.translatable("hud.encodedlogistics.machine.progress", percent);
                lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.machine.progress_label"), value,
                        new RackDeviceInfo.Bar(percent / 100F, RackDeviceInfo.BarStyle.NORMAL)));
            }
            info.energy().ifPresent(energy -> lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.machine.energy"),
                    Component.translatable("hud.encodedlogistics.machine.energy_value", String.format(Locale.ROOT, "%,d", energy.stored()),
                            String.format(Locale.ROOT, "%,d", energy.capacity()), energy.perTick()),
                    new RackDeviceInfo.Bar(energy.fraction(), energy.fraction() < 0.1F ? RackDeviceInfo.BarStyle.WARN : RackDeviceInfo.BarStyle.NORMAL))));
            info.heat().ifPresent(heat -> lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.machine.heat"),
                    Component.translatable("hud.encodedlogistics.machine.heat_value", heat.temperature(), heat.maxTemperature()))));
            String recipe = info.recipe();
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.machine.recipe"),
                    recipe.isEmpty() ? Component.translatable("hud.encodedlogistics.wireless.none") : Component.literal(recipe)));
            if (powerFromNetwork && info.energy().isPresent()) {
                lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.machine.power"),
                        Component.translatable("hud.encodedlogistics.machine.power_network", powered)));
            }
        }
        Component name = shown.getString().isEmpty() ? ModItems.SMALL_WIRELESS_BRIDGE.get().getName(ModItems.SMALL_WIRELESS_BRIDGE.toStack()) : shown;
        return new RackDeviceInfo(name, status(server, info), statusText(server, info), lines);
    }

    // The bridge's own popup (the crosshair on the bridge, not the machine), as an Access Point's: its link's status, then
    // its controller, the machine it's on, its lane and drain, the Gateway that feeds the machine and power from network.
    public RackDeviceInfo describeBridge(MinecraftServer server) {
        MachineInfo info = info(server);
        Wireless.Problem problem = Wireless.problem(server, this);
        RackDeviceInfo.Status status = problem != Wireless.Problem.NONE ? problem.status() : !online ? RackDeviceInfo.Status.OFFLINE
                : info == null ? RackDeviceInfo.Status.FAULT : RackDeviceInfo.Status.ONLINE;
        Component text = problem != Wireless.Problem.NONE ? problem.text() : info == null && online
                ? Component.translatable("hud.encodedlogistics.machine.missing") : status.text();
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        String controller = controllerName(server);
        Component none = Component.translatable("hud.encodedlogistics.wireless.none");
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.controller"),
                link == null ? Component.translatable("hud.encodedlogistics.wireless.use_card") : controller.isEmpty() ? none : Component.literal(controller)));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.machine.machine"), shown.getString().isEmpty() ? none : shown));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.lanes"),
                Component.literal(online ? "1 / 1" : "0 / 1")));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.machine.drain"),
                Component.translatable("hud.encodedlogistics.machine.fe_per_tick", String.format(Locale.ROOT, "%.1f", Config.SMALL_WIRELESS_BRIDGE_DRAIN.getAsDouble()))));
        String gatewayName = gatewayName(server);
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.machine.gateway"),
                gatewayName.isEmpty() ? none : Component.literal(gatewayName)));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.machine.power"), powerFromNetwork
                ? Component.translatable("hud.encodedlogistics.machine.power_network", powered) : Component.translatable("hud.encodedlogistics.machine.power_off")));
        return new RackDeviceInfo(ModItems.SMALL_WIRELESS_BRIDGE.get().getName(ModItems.SMALL_WIRELESS_BRIDGE.toStack()), status, text, lines);
    }

    // The device name of the Gateway it's set to (GATEWAY01), "" for none; "?" when it's gone or unloaded.
    public String gatewayName(MinecraftServer server) {
        if (gateway == null) {
            return "";
        }
        return ControllerStructures.blockEntity(server, NodePos.of(gateway)) instanceof GatewayBlockEntity found
                ? found.deviceName().isEmpty() ? "GATEWAY" : found.deviceName() : "?";
    }

    @Override
    public Component shownName() {
        return deviceName.isEmpty() ? shown : Component.literal(deviceName);
    }

    // Whether the network it's on is the one at the controller's end (it's in the network's graph).
    public boolean onNetwork(MinecraftServer server) {
        ServerLevel level = server.getLevel(dimension);
        return level != null && ControllerStructures.networkOf(level, pos) != null;
    }
}
