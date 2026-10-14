/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.menu.RackMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.TopologyCause;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.part.TrunkPortPart;
import net.zagdrath.encodedlogistics.network.NetworkDiscovery;
import net.zagdrath.encodedlogistics.network.NetworkPart;
import net.zagdrath.encodedlogistics.network.RackLanes;
import net.zagdrath.encodedlogistics.network.RackNode;
import net.zagdrath.encodedlogistics.network.RemoteLink;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceItem;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.RackScheduler;
import net.zagdrath.encodedlogistics.rack.device.RackConsoleDevice;
import net.zagdrath.encodedlogistics.rack.device.L3SwitchDevice;
import net.zagdrath.encodedlogistics.rack.device.NetworkControllerDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModSounds;
import net.zagdrath.encodedlogistics.registry.ModItems;

// The Server Rack's master block entity: the devices by the unit they sit at (bottom U), the doors, and the rack's place
// on its network. It's one network device (RackNode), draining what its devices drain. Its lanes: each switch's uplink,
// plus a lane for each device its switches don't pool (Lanes), each its own demand with the device's priority; the
// LaneSolver grants them over any of the rack's uplinks (RackLanes), and a device is online while the rack is and it has
// its lanes (hasLanes: granted, or pooled by a granted switch, or using none).
//
// It also knows the segments its devices can serve (networks beyond Segment Isolators, linked with a Link Card): a
// pooled device set to segment i serves that network instead of the rack's own (network(device)). And it has a
// RackScheduler: the network's Compute and Memory Servers pool across its racks into one Scheduler, run by the rack
// that heads the pool.
//
// Clients get the doors, whether each device is on, off or faulted, and what each device's front shows (writeClient).
// Door changes are sent at once; device changes at most every SYNC_INTERVAL ticks. The doors animate on the client
// (DOOR_TICKS, smoothstep).
public class RackBlockEntity extends BlockEntity implements NetworkDevice {
    public static final int DOOR_TICKS = 10;
    private static final int SYNC_INTERVAL = 10;

    // Segments it can link beyond its own network.
    public static final int MAX_SEGMENTS = 5;

    private final TreeMap<Integer, RackDevice> devices = new TreeMap<>();
    // Linked segments, each found through a block on it (the one next to a Segment Isolator's end), with its segment
    // (VLAN) ID: 1 is the rack's own network.
    private final List<Segment> segments = new ArrayList<>();

    public static final int OWN_ID = 1, MAX_ID = 4094;

    public record Segment(int id, GlobalPos node) {}

    // A segment the rack knows: its own network (OWN_ID), its own linked ones, then those the Trunk Ports on its network
    // carry (trunk; an ID it has itself wins).
    public record KnownSegment(int id, @Nullable GlobalPos node, boolean trunk) {}
    private final RackScheduler scheduler = new RackScheduler(this);
    // Memory Dies owed for upgraded Rack Consoles converted on load, given back on the next tick.
    private int migrationDies;
    private boolean frontOpen, rearOpen;
    private boolean online;
    // As last solved: which units got their lanes, and the rack's uplinks.
    private RackLanes rackLanes = RackLanes.NONE;
    // Its controller structure (ControllerStructures) while it holds rack Network Controllers, else 0.
    private long controllerStructure;
    // The network it joined first (kept across reloads): a connection point cabled to another one is a mismatch and
    // carries nothing (no network, no power). Points checked by search are cached until the topology changes.
    private @Nullable NetworkRef home;
    private final Map<RackGeometry.Point, Long> beyond = new java.util.EnumMap<>(RackGeometry.Point.class);
    private int beyondGeneration = -1;
    // A search beyond a point runs into other racks, which mustn't search in turn.
    private static boolean searching;
    // FE taken in through its connection points this tick (rackPowerMaxInput a tick, across them all).
    private int powerThisTick;
    private final PowerPort powerPort = new PowerPort();
    // lanes() as last worked out, for the devices as they were then (NO_EPOCH: work it out again), and lanes(granted)
    // for the rack lanes it was worked out for.
    private static final int NO_EPOCH = Integer.MIN_VALUE;
    private @Nullable Lanes lanesCache;
    private int lanesEpoch = NO_EPOCH;
    private @Nullable Lanes grantedLanesCache;
    private @Nullable RackLanes grantedLanesFor;
    private int grantedLanesEpoch = NO_EPOCH;
    private boolean syncPending;
    private int syncCooldown;
    // What clients were last sent (getUpdateTag): a pending sync that wouldn't change it isn't sent.
    private @Nullable CompoundTag lastSent;
    // Ticks until the front door closes again after a visit (visitFront); 0: none pending.
    private int closeFrontIn;
    // Client: door animation, 0 (closed) to DOOR_TICKS (open), this tick and last.
    private int frontTicks, rearTicks, lastFrontTicks, lastRearTicks;
    private boolean synced;

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SERVER_RACK.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(ServerRackBlock.FACING);
    }

    // --- Devices ---

    public Collection<RackDevice> devices() {
        return Collections.unmodifiableCollection(devices.values());
    }

    // The device taking up unit u, if any.
    public @Nullable RackDevice deviceAt(int u) {
        Map.Entry<Integer, RackDevice> entry = devices.floorEntry(u);
        return entry != null && entry.getValue().occupies(u) ? entry.getValue() : null;
    }

    // Whether size units from u up are all inside the rack and free.
    public boolean fits(int u, int size) {
        if (u < 1 || u + size - 1 > RackGeometry.UNITS) {
            return false;
        }
        for (int unit = u; unit < u + size; unit++) {
            if (deviceAt(unit) != null) {
                return false;
            }
        }
        return true;
    }

    // Whether a device of that type fits at u: its units free, and (a Rack Console) clear of the other consoles.
    public boolean fits(int u, RackDeviceType type) {
        return fits(u, type.size()) && consoleInTheWay(u, type) == null;
    }

    // For a Rack Console at u: the installed console it would clip with when both are open (RackConsoleDevice.SPACING),
    // else null.
    public @Nullable RackDevice consoleInTheWay(int u, RackDeviceType type) {
        if (!type.isConsole()) {
            return null;
        }
        for (RackDevice device : devices.values()) {
            if (device.type().isConsole() && RackConsoleDevice.tooClose(u, device.u())) {
                return device;
            }
        }
        return null;
    }

    // The lowest unit a device of that size fits at, or 0.
    public int lowestFit(int size) {
        for (int u = 1; u + size - 1 <= RackGeometry.UNITS; u++) {
            if (fits(u, size)) {
                return u;
            }
        }
        return 0;
    }

    public int freeUnits() {
        int used = 0;
        for (RackDevice device : devices.values()) {
            used += device.size();
        }
        return RackGeometry.UNITS - used;
    }

    public void install(RackDevice device, int u, @Nullable ServerPlayer by) {
        device.attach(this, u);
        devices.put(u, device);
        devicesChanged();
        device.setOnline(online && hasLanes(device));
        device.onInstalled(by);
        deviceChanged(true);
    }

    public @Nullable RackDevice remove(int u) {
        RackDevice device = deviceAt(u);
        if (device == null) {
            return null;
        }
        devices.remove(device.u());
        devicesChanged();
        device.onRemoved();
        device.detach();
        device.setSegment(0);
        deviceChanged(true);
        return device;
    }

    // Devices went in or out: the lanes are worked out again, and what's cached from the networks goes
    // (ControllerStructures.contentChanged).
    private void devicesChanged() {
        lanesEpoch = NO_EPOCH;
        grantedLanesEpoch = NO_EPOCH;
        if (level instanceof ServerLevel) {
            ControllerStructures.contentChanged();
        }
    }

    // A segment linked or unlinked, or a device moved to another: which network its devices serve changed.
    private void segmentsChanged() {
        deviceChanged(false);
        if (level instanceof ServerLevel) {
            ControllerStructures.contentChanged();
        }
    }

    // A player mounts the device in their hand at u (the open front, or the screen); rack access on its network.
    public boolean installFromHand(ServerPlayer player, ItemStack stack, int u) {
        RackDeviceType type = RackDeviceType.of(stack);
        if (type == null || !(level instanceof ServerLevel serverLevel) || !NetworkAccess.rack(serverLevel, worldPosition, player)) {
            return false;
        }
        if (!fits(u, type.size())) {
            player.sendOverlayMessage(Component.translatable("gui.encodedlogistics.rack.no_room", type.size()));
            return false;
        }
        RackDevice console = consoleInTheWay(u, type);
        if (console != null) {
            player.sendOverlayMessage(Component.translatable("gui.encodedlogistics.rack.console_spacing", RackConsoleDevice.SPACING, console.u()));
            return false;
        }
        RackDevice device = RackDeviceItem.create(stack, serverLevel.registryAccess());
        if (device == null) {
            return false;
        }
        install(device, u, player);
        stack.consume(1, player);
        return true;
    }

    // A player takes the device at u out: it (with its settings) and whatever it held go to their inventory.
    public boolean takeOut(ServerPlayer player, int u) {
        if (!(level instanceof ServerLevel serverLevel) || deviceAt(u) == null || !NetworkAccess.rack(serverLevel, worldPosition, player)) {
            return false;
        }
        RackDevice device = remove(u);
        if (device == null) {
            return false;
        }
        player.getInventory().placeItemBackInInventory(RackDeviceItem.toStack(device, serverLevel.registryAccess()), Prediction.SERVER_ONLY);
        device.contents().forEach(stack -> player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY));
        device.clearContents();
        return true;
    }

    // A device's state changed in a way only saving cares about (a tape op's progress, a controller's energy): saved
    // with the chunk. Not setChanged, which also updates comparators (the rack has no comparator output) and is called
    // every tick for these.
    public void markUnsaved() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    // A device's settings or state changed.
    public void deviceChanged(boolean topology) {
        if (level instanceof ServerLevel serverLevel) {
            setChanged();
            if (topology) {
                ControllerStructures.topologyChanged(serverLevel, worldPosition, TopologyCause.RACK);
            }
            syncPending = true;
        }
    }

    // --- Doors ---

    public boolean isFrontOpen() {
        return frontOpen;
    }

    public boolean isRearOpen() {
        return rearOpen;
    }

    public void setFrontOpen(boolean open) {
        // Any other open or close ends a visit (a player who opens it keeps it open).
        closeFrontIn = 0;
        if (frontOpen != open) {
            frontOpen = open;
            doorMoved(open);
            if (level != null && !level.isClientSide()) {
                for (RackDevice device : List.copyOf(devices.values())) {
                    device.frontDoorChanged(open);
                }
            }
        }
    }

    // Something works on the devices through the front (a Rack Elevation installing): the door swings open, with its
    // sound, and closes again once it has had time to - unless it was open already, when it's left so.
    public void visitFront() {
        if (!frontOpen) {
            setFrontOpen(true);
            closeFrontIn = DOOR_TICKS + 20;
        }
    }

    public void setRearOpen(boolean open) {
        if (rearOpen != open) {
            rearOpen = open;
            doorMoved(open);
        }
    }

    private void doorMoved(boolean open) {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.playSound(null, worldPosition, (open ? ModSounds.RACK_DOOR_OPEN : ModSounds.RACK_DOOR_CLOSE).value(), SoundSource.BLOCKS, 0.8F,
                    0.95F + level.getRandom().nextFloat() * 0.1F);
            syncNow();
        }
    }

    // Client: how open a door is, 0 to 1, eased.
    public float frontOpenness(float partialTick) {
        return RackGeometry.ease((lastFrontTicks + (frontTicks - lastFrontTicks) * partialTick) / DOOR_TICKS);
    }

    public float rearOpenness(float partialTick) {
        return RackGeometry.ease((lastRearTicks + (rearTicks - lastRearTicks) * partialTick) / DOOR_TICKS);
    }

    // --- Network ---

    public RackNode networkNode(Set<Direction> sides) {
        double drain = 0;
        List<NetworkPart> parts = new ArrayList<>();
        List<RemoteLink> links = new ArrayList<>();
        for (RackDevice device : devices.values()) {
            drain += device.drain();
            parts.add(new NetworkPart(device.type().item(), device.drain()));
            links.addAll(device.remoteLinks());
        }
        List<RackNode.RackController> controllers = new ArrayList<>();
        for (NetworkControllerDevice controller : controllers()) {
            controllers.add(new RackNode.RackController(controller.u(), controller.size(), controller.lanes(), controller.usable()));
        }
        return new RackNode(worldPosition.immutable(), sides, drain, parts, demands(), controllers, controllerStructure, links);
    }

    // Each connection point with its state, for a controller's uplink chips: up (it carries the network toward a source,
    // or out from this rack's controller), down (a cable there leads nowhere), unused (nothing there) or mismatch (a cable
    // from another network); the cable there and its lanes.
    public enum PointState {
        UP, DOWN, UNUSED, MISMATCH
    }

    public void writeUplinks(ValueOutput.ValueOutputList list) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (RackGeometry.Point point : RackGeometry.Point.values()) {
            BlockPos outside = point.outside(worldPosition, facing());
            PointState state = pointState(serverLevel, point);
            ValueOutput child = list.addChild();
            child.putInt("state", state.ordinal());
            child.putString("point", Component.translatable(point.key()).getString());
            child.putString("cable", state == PointState.UNUSED ? "" : serverLevel.getBlockState(outside).getBlock().getName().getString());
            int lanes = 0;
            for (RackLanes.Uplink uplink : rackLanes.uplinks()) {
                if (uplink.outside().pos().equals(outside)) {
                    lanes = uplink.capacity();
                }
            }
            child.putInt("lanes", lanes);
        }
    }

    public PointState pointState(ServerLevel level, RackGeometry.Point point) {
        BlockPos outside = point.outside(worldPosition, facing());
        if (isMismatched(level, point)) {
            return PointState.MISMATCH;
        }
        for (RackLanes.Uplink uplink : rackLanes.uplinks()) {
            if (uplink.outside().pos().equals(outside) && uplink.outside().dimension().equals(level.dimension())) {
                return uplink.active() ? PointState.UP : PointState.DOWN;
            }
        }
        return PointState.UNUSED;
    }

    // A cable from another network than the rack's at a point: it carries nothing. Its network is what the index says
    // the block there is on, or else the controller a search beyond it finds. A rack with its own controllers never has
    // one (two controller systems meeting there are a conflict, or a pair). Before it has joined a network, the lowest
    // network found at its points is the one it'll join, and the others are mismatches.
    public boolean isMismatched(ServerLevel level, RackGeometry.Point point) {
        if (!controllers().isEmpty()) {
            return false;
        }
        NetworkRef joined = home != null && ControllerStructures.exists(level.getServer(), home) ? home : null;
        if (joined == null) {
            NetworkRef lowest = null;
            for (RackGeometry.Point each : RackGeometry.Point.values()) {
                NetworkRef there = networkAt(level, each);
                if (there != null && (lowest == null || there.id() < lowest.id())) {
                    lowest = there;
                }
            }
            joined = lowest;
        }
        NetworkRef there = networkAt(level, point);
        return joined != null && there != null && !there.equals(joined);
    }

    // The network a cable at a point leads to, if any.
    private @Nullable NetworkRef networkAt(ServerLevel level, RackGeometry.Point point) {
        BlockPos outside = point.outside(worldPosition, facing());
        if (!level.isLoaded(outside)) {
            return null;
        }
        NetworkRef indexed = ControllerStructures.networkOf(level, outside);
        if (indexed != null) {
            return indexed;
        }
        int generation = ControllerStructures.generation(level.getServer());
        if (generation != beyondGeneration) {
            beyond.clear();
            beyondGeneration = generation;
        }
        Long id = beyond.get(point);
        if (id == null) {
            if (searching) {
                return null;
            }
            Set<BlockPos> own = new HashSet<>();
            for (int index = 0; index < RackGeometry.PARTS; index++) {
                own.add(RackGeometry.partPos(worldPosition, facing(), index));
            }
            searching = true;
            try {
                Long found = NetworkDiscovery.controllerBeyond(level, outside, point.side(facing()), own);
                id = found != null ? found : 0L;
            } finally {
                searching = false;
            }
            beyond.put(point, id);
        }
        return id > 0 ? ControllerStructures.networkOfStructure(level.getServer(), new NetworkRef(level.dimension(), id)) : null;
    }

    // Whether a cable on that side of one of its blocks joins it (a connection point, and not a mismatch).
    public boolean joins(int index, Direction side) {
        RackGeometry.Point point = RackGeometry.Point.at(index, facing(), side);
        return point == null || !(level instanceof ServerLevel serverLevel) || !isMismatched(serverLevel, point);
    }

    // Keeps the network it joined, once it's on one.
    private void checkHome(ServerLevel level) {
        NetworkRef current = ControllerStructures.networkOf(level, worldPosition);
        if (current != null && !current.equals(home)) {
            home = current;
            setChanged();
        } else if (current == null && home != null && !ControllerStructures.exists(level.getServer(), home)) {
            home = null;
            setChanged();
            ControllerStructures.topologyChanged(level, worldPosition, TopologyCause.RACK);
        }
    }

    // --- Power ---

    // FE into the rack at a connection point (any mod's cable): it goes to its network's energy, as a Power Inlet's does.
    // Not on a point (or at a mismatched one), nothing.
    public @Nullable EnergyHandler energyHandler(int index, @Nullable Direction side) {
        if (side == null) {
            return null;
        }
        RackGeometry.Point point = RackGeometry.Point.at(index, facing(), side);
        return point != null && level instanceof ServerLevel serverLevel && !isMismatched(serverLevel, point) ? powerPort : null;
    }

    private static int powerMaxInput() {
        int max = Config.RACK_POWER_MAX_INPUT.getAsInt();
        return max < 0 ? Config.INLET_MAX_INPUT.getAsInt() : max;
    }

    private final class PowerPort implements EnergyHandler {
        private final SnapshotJournal<Integer> journal = new SnapshotJournal<>() {
            @Override
            protected Integer createSnapshot() {
                return powerThisTick;
            }

            @Override
            protected void revertToSnapshot(Integer snapshot) {
                powerThisTick = snapshot;
            }
        };

        @Override
        public long getAmountAsLong() {
            return 0;
        }

        @Override
        public long getCapacityAsLong() {
            return 0;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            if (amount <= 0 || !(level instanceof ServerLevel serverLevel)) {
                return 0;
            }
            NetworkRef network = ControllerStructures.networkOf(serverLevel, worldPosition);
            int allowed = Math.min(amount, Math.max(0, powerMaxInput() - powerThisTick));
            if (network == null || allowed <= 0) {
                return 0;
            }
            // Into the network's pending buffer; it reaches the drives, controllers and banks when the network ticks.
            int filled = ControllerStructures.bufferPower(serverLevel.getServer(), network, allowed, transaction);
            if (filled > 0) {
                journal.updateSnapshots(transaction);
                powerThisTick += filled;
            }
            return filled;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    }

    // --- The popup's header ---

    // Above every device's popup: the rack, its lanes over its working uplinks and how many there are; amber and
    // "Degraded" while a device is shed, an uplink is down or a point is mismatched. Nothing while it isn't on a network.
    public RackDeviceInfo.@Nullable Header header() {
        if (!(level instanceof ServerLevel serverLevel) || rackLanes.equals(RackLanes.NONE)) {
            return null;
        }
        Component name = getBlockState().getBlock().getName();
        int mismatched = 0;
        for (RackGeometry.Point point : RackGeometry.Point.values()) {
            if (isMismatched(serverLevel, point)) {
                mismatched++;
            }
        }
        int uplinks = rackLanes.uplinks().size() + mismatched, active = rackLanes.activeUplinks();
        if (rackLanes.degraded() || mismatched > 0) {
            return new RackDeviceInfo.Header(Component.translatable("hud.encodedlogistics.rack.degraded", rackLanes.available(), rackLanes.total(), active,
                    uplinks), true);
        }
        return new RackDeviceInfo.Header(Component.translatable(active == 1 ? "hud.encodedlogistics.rack.uplinks.one" : "hud.encodedlogistics.rack.uplinks",
                name, rackLanes.available(), rackLanes.total(), active), false);
    }


    // --- Rack Network Controllers ---

    public List<NetworkControllerDevice> controllers() {
        List<NetworkControllerDevice> controllers = new ArrayList<>();
        for (RackDevice device : devices.values()) {
            if (device instanceof NetworkControllerDevice controller) {
                controllers.add(controller);
            }
        }
        return controllers;
    }

    public long controllerStructure() {
        return controllerStructure;
    }

    // Holding controllers, it has a controller structure of its own; without, none.
    private void checkControllerStructure(ServerLevel level) {
        ControllerStructures structures = ControllerStructures.get(level);
        boolean wanted = devices.values().stream().anyMatch(device -> device instanceof NetworkControllerDevice);
        ControllerStructures.Structure structure = controllerStructure > 0 ? structures.get(controllerStructure) : null;
        boolean valid = structure != null && structure.rack() && structure.members().contains(worldPosition);
        if (wanted && !valid) {
            controllerStructure = structures.addRack(level, worldPosition);
            setChanged();
        } else if (!wanted && controllerStructure > 0) {
            if (valid) {
                structures.removeRack(level, controllerStructure);
            }
            controllerStructure = 0;
            setChanged();
        }
    }

    // What each device needs from the network: each switch its uplink, each device no switch pools its own lanes.
    public List<RackNode.LaneDemand> demands() {
        Lanes lanes = lanes();
        List<RackNode.LaneDemand> demands = new ArrayList<>();
        for (RackDevice device : devices.values()) {
            int cost = device.lanePool() != null ? device.lanePool().uplinkCost() : lanes.isPooled(device) ? 0 : device.laneCost();
            if (cost > 0) {
                demands.add(new RackNode.LaneDemand(device.u(), cost, device.lanePriority().ordinal()));
            }
        }
        return demands;
    }

    // The rack's lanes as last solved (server).
    public RackLanes rackLanes() {
        return rackLanes;
    }

    public void setRackLanes(RackLanes lanes) {
        if (!rackLanes.equals(lanes)) {
            rackLanes = lanes;
            refreshDevices();
            syncPending = true;
        }
    }

    // Whether a device has what it needs from the network: its own lanes granted, or a place in the pool of the switches
    // that got theirs, or no lanes to need.
    public boolean hasLanes(RackDevice device) {
        if (device.lanePool() != null) {
            return device.lanePool().uplinkCost() <= 0 || rackLanes.granted().contains(device.u());
        }
        if (grantedLanes().isPooled(device)) {
            return true;
        }
        return device.laneCost() <= 0 || rackLanes.granted().contains(device.u());
    }

    private void refreshDevices() {
        for (RackDevice device : List.copyOf(devices.values())) {
            device.setOnline(online && hasLanes(device));
        }
    }

    // How the rack's lanes work out: the switches' pool (its capacity and how much of it is used), which devices it
    // pools, and the lanes the rack needs from the network.
    public record Lanes(int poolCapacity, int poolUsed, Set<RackDevice> pooledDevices, int networkLanes) {
        public static final Lanes NONE = new Lanes(0, 0, Set.of(), 0);

        public boolean isPooled(RackDevice device) {
            return pooledDevices.contains(device);
        }

        public int pooled() {
            return pooledDevices.size();
        }
    }

    // The switches pool their capacity for the other devices, in unit order until it's used; each switch costs its
    // uplink on the network, every device it doesn't pool its own lanes.
    // Kept until devices go in or out, or anything cached from the networks changes (a switch's capacity is a setting).
    public Lanes lanes() {
        int epoch = ControllerStructures.contentEpoch();
        if (lanesCache == null || lanesEpoch != epoch) {
            lanesCache = lanes(null);
            lanesEpoch = epoch;
        }
        return lanesCache;
    }

    // The same with only the switches that got their uplinks pooling, for the rack lanes as last solved.
    private Lanes grantedLanes() {
        int epoch = ControllerStructures.contentEpoch();
        if (grantedLanesCache == null || grantedLanesEpoch != epoch || grantedLanesFor != rackLanes) {
            grantedLanesCache = lanes(rackLanes.granted());
            grantedLanesEpoch = epoch;
            grantedLanesFor = rackLanes;
        }
        return grantedLanesCache;
    }

    // The same with only the switches at the given units (null: all of them) pooling.
    private Lanes lanes(@Nullable Set<Integer> switches) {
        int capacity = 0, network = 0;
        for (RackDevice device : devices.values()) {
            if (device.lanePool() != null && (switches == null || switches.contains(device.u()))) {
                capacity += device.lanePool().capacity();
                network += device.lanePool().uplinkCost();
            }
        }
        int used = 0;
        Set<RackDevice> pooled = new HashSet<>();
        for (RackDevice device : devices.values()) {
            if (device.lanePool() != null) {
                continue;
            }
            if (used + device.laneCost() <= capacity) {
                used += device.laneCost();
                pooled.add(device);
            } else {
                network += device.laneCost();
            }
        }
        return new Lanes(capacity, used, pooled, network);
    }

    // --- Segments ---

    // The segments it knows, in order: its own network, its own linked segments, the Trunk Ports' (L3 Switch routes
    // name them by this order; a Trunk Port's come after the rack's own, so those keep their places).
    public List<KnownSegment> knownSegments() {
        long key = level instanceof ServerLevel serverLevel ? ControllerStructures.cacheKey(serverLevel.getServer()) : -1;
        if (knownCache != null && key == knownKey && key != -1) {
            return knownCache;
        }
        List<KnownSegment> known = new ArrayList<>();
        known.add(new KnownSegment(OWN_ID, null, false));
        Set<Integer> ids = new HashSet<>();
        ids.add(OWN_ID);
        for (Segment segment : segments) {
            known.add(new KnownSegment(segment.id(), segment.node(), false));
            ids.add(segment.id());
        }
        if (level instanceof ServerLevel serverLevel) {
            for (TrunkPortPart.Segment carried : TrunkPortPart.carriedOn(serverLevel.getServer(), ControllerStructures.networkOf(serverLevel, worldPosition))) {
                if (ids.add(carried.id())) {
                    known.add(new KnownSegment(carried.id(), carried.node(), true));
                }
            }
        }
        knownCache = List.copyOf(known);
        knownKey = key;
        return knownCache;
    }

    // knownSegments as last worked out, until anything networks are made of changes (segmentsChanged does).
    private @Nullable List<KnownSegment> knownCache;
    private long knownKey = -1;

    // Segments: 0 the rack's own network, then the others it knows (by index in knownSegments).
    public int segmentCount() {
        return level instanceof ServerLevel ? knownSegments().size() : 1 + segments.size();
    }

    public Component segmentName(int index) {
        List<KnownSegment> known = knownSegments();
        if (index <= 0 || index >= known.size()) {
            return Component.translatable("gui.encodedlogistics.switch.segment.default");
        }
        return Component.translatable(known.get(index).trunk() ? "gui.encodedlogistics.rack.segment_trunk" : "gui.encodedlogistics.rack.segment_id",
                known.get(index).id());
    }

    // The ID of the segment at index, or OWN_ID.
    public int segmentId(int index) {
        List<KnownSegment> known = knownSegments();
        return index > 0 && index < known.size() ? known.get(index).id() : OWN_ID;
    }

    // The network a segment (by index) is, if it's on one right now.
    public @Nullable NetworkRef segmentNetwork(int index) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (index == 0) {
            return ControllerStructures.networkOf(serverLevel, worldPosition);
        }
        List<KnownSegment> known = knownSegments();
        return index < known.size() && known.get(index).node() != null ? RouterDevice.networkAt(serverLevel.getServer(), known.get(index).node()) : null;
    }

    // The network a segment (by ID) is, if the rack knows it and it's on one right now.
    public @Nullable NetworkRef segmentNetworkById(int id) {
        if (id <= OWN_ID) {
            return segmentNetwork(0);
        }
        List<KnownSegment> known = knownSegments();
        for (int i = 1; i < known.size(); i++) {
            if (known.get(i).id() == id) {
                return segmentNetwork(i);
            }
        }
        return null;
    }

    // The IDs a device serves: an access port's segment; a trunk's native one and the allowed ones (all it knows, or
    // those picked).
    public List<Integer> servedIds(RackDevice device) {
        int own = device.segment() <= OWN_ID ? OWN_ID : device.segment();
        if (!device.trunk()) {
            return List.of(own);
        }
        List<Integer> ids = new ArrayList<>();
        ids.add(own);
        for (KnownSegment segment : knownSegments()) {
            if ((device.allowAll() || device.allowed().contains(segment.id())) && !ids.contains(segment.id())) {
                ids.add(segment.id());
            }
        }
        return ids;
    }

    // A pooled device's port: access or trunk (an L2 / L3 Switch's Ports tab).
    public void togglePortMode(int u) {
        RackDevice device = deviceAt(u);
        if (device == null || device.lanePool() != null || !lanes().isPooled(device)) {
            return;
        }
        device.setTrunk(!device.trunk());
        segmentsChanged();
    }

    // A trunk's allowed segments: id added or taken off (the first pick turns "all" into a list; -1: back to all).
    public void togglePortAllowed(int u, int id) {
        RackDevice device = deviceAt(u);
        if (device == null || device.lanePool() != null || !lanes().isPooled(device) || !device.trunk()) {
            return;
        }
        if (id < 0) {
            device.allowed().clear();
            device.setAllowAll(true);
        } else if (device.allowAll()) {
            device.setAllowAll(false);
            device.allowed().add(id);
        } else if (!device.allowed().remove(id)) {
            device.allowed().add(id);
        }
        segmentsChanged();
    }

    public enum SegmentResult {
        LINKED, UNLINKED, FULL
    }

    // Links a segment (a Link Card's) with the next free ID; linking one it already has unlinks it.
    public SegmentResult toggleSegment(GlobalPos node) {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).node().equals(node)) {
                unlinkSegment(i + 1);
                return SegmentResult.UNLINKED;
            }
        }
        if (segments.size() >= MAX_SEGMENTS) {
            return SegmentResult.FULL;
        }
        int id = OWN_ID + 1;
        for (Segment segment : segments) {
            id = Math.max(id, segment.id() + 1);
        }
        segments.add(new Segment(Math.min(MAX_ID, id), node));
        segmentsChanged();
        return SegmentResult.LINKED;
    }

    // Devices on it go back to the rack's own network (trunks drop it); L3 routes through it go.
    private void unlinkSegment(int index) {
        int id = segments.remove(index - 1).id();
        for (RackDevice device : devices.values()) {
            if (device.segment() == id) {
                device.setSegment(OWN_ID);
            }
            device.allowed().remove(id);
            if (device instanceof L3SwitchDevice l3) {
                l3.routes().removeIf(route -> route.source() == id || route.dest() == id);
            }
        }
        segmentsChanged();
    }

    // The next (or previous) segment (a trunk's native one) for the device at u, if a switch pools it.
    public void cycleSegment(int u, int step) {
        RackDevice device = deviceAt(u);
        if (device == null || device.lanePool() != null || !lanes().isPooled(device)) {
            return;
        }
        List<KnownSegment> known = knownSegments();
        int at = 0;
        for (int i = 0; i < known.size(); i++) {
            if (known.get(i).id() == Math.max(OWN_ID, device.segment())) {
                at = i;
            }
        }
        device.setSegment(known.get(Math.floorMod(at + step, known.size())).id());
        segmentsChanged();
    }

    // The network a device serves: its segment's (a trunk's native one) while a switch pools it - none while that
    // segment's network can't be reached (it's unloaded or down: the device never falls back to another network) -
    // otherwise the rack's own (and its own once its segment isn't known any more: a Trunk Port stopped carrying it).
    public @Nullable NetworkRef network(RackDevice device) {
        if (device.segment() > OWN_ID && lanes().isPooled(device)) {
            for (KnownSegment segment : knownSegments()) {
                if (segment.id() == device.segment()) {
                    return segmentNetworkById(device.segment());
                }
            }
        }
        return segmentNetwork(0);
    }

    // Its devices serving a network: on its segment, or a trunk carrying it.
    public List<RackDevice> devicesServing(NetworkRef network) {
        List<RackDevice> serving = new ArrayList<>();
        Lanes lanes = lanes();
        for (RackDevice device : devices.values()) {
            if (network.equals(network(device))) {
                serving.add(device);
            } else if (device.trunk() && lanes.isPooled(device)) {
                for (int id : servedIds(device)) {
                    if (network.equals(segmentNetworkById(id))) {
                        serving.add(device);
                        break;
                    }
                }
            }
        }
        return serving;
    }

    // --- The rack's Scheduler ---

    public RackScheduler scheduler() {
        return scheduler;
    }

    @Override
    public void setNetworkOnline(boolean online) {
        if (this.online != online) {
            this.online = online;
            refreshDevices();
        }
    }

    public boolean isOnline() {
        return online;
    }

    // --- Ticking ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, RackBlockEntity rack) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (rack.migrationDies > 0) {
            // The Memory Dies of consoles converted on load, back out of the rack.
            Block.popResource(serverLevel, pos, new ItemStack(ModItems.MEMORY_DIE.get(), rack.migrationDies));
            rack.migrationDies = 0;
            rack.setChanged();
        }
        rack.checkControllerStructure(serverLevel);
        rack.checkHome(serverLevel);
        rack.powerThisTick = 0;
        for (RackDevice device : List.copyOf(rack.devices.values())) {
            device.tick(serverLevel);
        }
        rack.scheduler.tick(serverLevel);
        if (rack.closeFrontIn > 0 && --rack.closeFrontIn == 0) {
            rack.setFrontOpen(false);
        }
        if (rack.syncCooldown > 0) {
            rack.syncCooldown--;
        } else if (rack.syncPending) {
            rack.syncNow();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RackBlockEntity rack) {
        rack.lastFrontTicks = rack.frontTicks;
        rack.lastRearTicks = rack.rearTicks;
        rack.frontTicks = Math.clamp(rack.frontTicks + (rack.frontOpen ? 1 : -1), 0, DOOR_TICKS);
        rack.rearTicks = Math.clamp(rack.rearTicks + (rack.rearOpen ? 1 : -1), 0, DOOR_TICKS);
        for (RackDevice device : rack.devices.values()) {
            device.clientTick();
        }
    }

    // Sends clients what they see of the rack, unless it's what they were last sent (a device's change that clients
    // don't see: items moving through a drive whose fill light stays the same).
    private void syncNow() {
        syncPending = false;
        syncCooldown = SYNC_INTERVAL;
        if (level != null) {
            CompoundTag shown = getUpdateTag(level.registryAccess());
            if (shown.equals(lastSent)) {
                return;
            }
            lastSent = shown;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Loading and unloading ---

    // A rack coming or going (its chunk loading or unloading, or broken) changes which racks are there to cache.
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            ControllerStructures.contentChanged();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel) {
            ControllerStructures.contentChanged();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel) {
            ControllerStructures.contentChanged();
        }
    }

    // --- The screen ---

    public void openMenu(ServerPlayer player) {
        if (!NetworkAccess.rack(player.level(), worldPosition, player)) {
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RackMenu(id, inventory, this),
                Component.translatable("block.encodedlogistics.server_rack")), buf -> buf.writeBlockPos(worldPosition));
    }

    // The whole rack, for rendering.
    public AABB bounds() {
        return RackGeometry.bounds(worldPosition, facing());
    }

    // --- Breaking ---

    // The devices, with their settings, and what they hold.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            for (RackDevice device : devices.values()) {
                Block.popResource(serverLevel, pos, RackDeviceItem.toStack(device, serverLevel.registryAccess()));
                device.contents().forEach(stack -> Block.popResource(serverLevel, pos, stack));
                device.clearContents();
                device.onRemoved();
            }
            devices.clear();
            lanesEpoch = grantedLanesEpoch = NO_EPOCH;
            scheduler.dropAll(serverLevel);
            if (controllerStructure > 0) {
                ControllerStructures.get(serverLevel).removeRack(serverLevel, controllerStructure);
                controllerStructure = 0;
            }
        }
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        frontOpen = input.getBooleanOr("front_open", false);
        rearOpen = input.getBooleanOr("rear_open", false);
        controllerStructure = input.getLongOr("controller_structure", 0L);
        home = input.read("home", NetworkRef.CODEC).orElse(null);
        segments.clear();
        // Racks saved before segment IDs: their segments get IDs in order (index i is ID i + 1; the rack's own is 1),
        // and so do their devices' segments; every port loads as an access port.
        boolean ids = input.getBooleanOr("segment_ids", false);
        if (ids) {
            for (ValueInput child : input.childrenListOrEmpty("segment_list")) {
                child.read("node", GlobalPos.CODEC).ifPresent(node -> segments.add(new Segment(Math.clamp(child.getIntOr("id", OWN_ID + 1), OWN_ID + 1, MAX_ID), node)));
            }
        } else {
            input.read("segments", GlobalPos.CODEC.listOf()).ifPresent(nodes -> {
                for (GlobalPos node : nodes) {
                    segments.add(new Segment(segments.size() + 2, node));
                }
            });
        }
        devices.clear();
        migrationDies = input.getIntOr("migration_dies", 0);
        for (ValueInput child : input.childrenListOrEmpty("devices")) {
            RackDevice read = read(child);
            if (read != null) {
                read.load(child.childOrEmpty("data"));
                // A Rack Console with the old Memory Die upgrade: a Rack Fabrication Console now, with its grid; the die
                // comes back out of the rack (serverTick).
                if (read instanceof RackConsoleDevice old && read.type() == RackDeviceType.RACK_CONSOLE
                        && RackConsoleDevice.upgraded(child.childOrEmpty("data"))) {
                    RackConsoleDevice converted = (RackConsoleDevice) RackDeviceType.RACK_FABRICATION_CONSOLE.create();
                    converted.attach(this, old.u());
                    converted.adoptUpgraded(old);
                    read = converted;
                    migrationDies++;
                }
                RackDevice device = read;
                int segment = child.getIntOr("segment", 0);
                device.setSegment(ids ? segment : segment + 1);
                device.setTrunk(child.getBooleanOr("trunk", false));
                device.setAllowAll(child.getBooleanOr("allow_all", true));
                for (int id : child.getIntArray("allowed").orElse(new int[0])) {
                    device.allowed().add(id);
                }
                child.getInt("priority").ifPresent(id -> device.setLanePriority(RackDevice.Priority.byId(id)));
                device.setDeviceName(child.getStringOr("device_name", ""));
                devices.put(device.u(), device);
            }
        }
        scheduler.load(input);
        lanesEpoch = grantedLanesEpoch = NO_EPOCH;
    }

    // A device's type and place from a saved or synced entry, or null if it's unknown or doesn't fit.
    private @Nullable RackDevice read(ValueInput child) {
        Identifier id = Identifier.tryParse(child.getStringOr("type", ""));
        RackDeviceType type = id != null ? RackDeviceType.byId(id) : null;
        int u = child.getIntOr("u", 0);
        if (type == null || !fits(u, type.size())) {
            return null;
        }
        RackDevice device = type.create();
        device.attach(this, u);
        return device;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("front_open", frontOpen);
        output.putBoolean("rear_open", rearOpen);
        if (controllerStructure > 0) {
            output.putLong("controller_structure", controllerStructure);
        }
        if (home != null) {
            output.store("home", NetworkRef.CODEC, home);
        }
        ValueOutput.ValueOutputList list = output.childrenList("devices");
        for (RackDevice device : devices.values()) {
            ValueOutput child = list.addChild();
            child.putString("type", device.type().id().toString());
            child.putInt("u", device.u());
            if (device.segment() > OWN_ID) {
                child.putInt("segment", device.segment());
            }
            if (device.trunk()) {
                child.putBoolean("trunk", true);
            }
            if (!device.allowAll()) {
                child.putBoolean("allow_all", false);
                child.putIntArray("allowed", device.allowed().stream().mapToInt(Integer::intValue).toArray());
            }
            child.putInt("priority", device.lanePriority().ordinal());
            if (!device.deviceName().isEmpty()) {
                child.putString("device_name", device.deviceName());
            }
            device.save(child.child("data"));
        }
        output.putBoolean("segment_ids", true);
        ValueOutput.ValueOutputList segmentList = output.childrenList("segment_list");
        for (Segment segment : segments) {
            ValueOutput child = segmentList.addChild();
            child.putInt("id", segment.id());
            child.store("node", GlobalPos.CODEC, segment.node());
        }
        if (migrationDies > 0) {
            output.putInt("migration_dies", migrationDies);
        }
        scheduler.save(output);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.putBoolean("front_open", frontOpen);
        output.putBoolean("rear_open", rearOpen);
        output.putBoolean("online", online);
        ValueOutput.ValueOutputList list = output.childrenList("devices");
        for (RackDevice device : devices.values()) {
            ValueOutput child = list.addChild();
            child.putString("type", device.type().id().toString());
            child.putInt("u", device.u());
            child.putInt("status", device.status().ordinal());
            child.putBoolean("online", device.isOnline());
            child.putInt("priority", device.lanePriority().ordinal());
            String variant = device.modelVariant();
            if (variant != null) {
                child.putString("variant", variant);
            }
            device.writeClient(child.child("client"));
        }
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        readUpdate(input);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        readUpdate(input);
    }

    private void readUpdate(ValueInput input) {
        frontOpen = input.getBooleanOr("front_open", false);
        rearOpen = input.getBooleanOr("rear_open", false);
        // The first time (the chunk loading), the doors are where they are rather than swinging there.
        if (!synced) {
            synced = true;
            frontTicks = lastFrontTicks = frontOpen ? DOOR_TICKS : 0;
            rearTicks = lastRearTicks = rearOpen ? DOOR_TICKS : 0;
        }
        online = input.getBooleanOr("online", false);
        // A device still at its unit keeps its client copy (and so its animations).
        Map<Integer, RackDevice> old = new HashMap<>(devices);
        devices.clear();
        for (ValueInput child : input.childrenListOrEmpty("devices")) {
            RackDevice device = read(child);
            RackDevice kept = device != null ? old.get(device.u()) : null;
            if (kept != null && kept.type() == device.type()) {
                device = kept;
            }
            if (device != null) {
                device.setOnline(child.getBooleanOr("online", false));
                device.setShownStatus(RackDeviceInfo.Status.byId(child.getIntOr("status", 1)));
                device.setLanePriority(RackDevice.Priority.byId(child.getIntOr("priority", 1)));
                device.setShownVariant(child.getString("variant").orElse(null));
                device.readClient(child.childOrEmpty("client"));
                devices.put(device.u(), device);
            }
        }
        lanesEpoch = grantedLanesEpoch = NO_EPOCH;
    }
}
