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
import net.zagdrath.encodedlogistics.network.NetworkDevice;
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
import net.zagdrath.encodedlogistics.rack.device.L3SwitchDevice;
import net.zagdrath.encodedlogistics.rack.device.NetworkControllerDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModSounds;

// The Server Rack's master block entity: the devices by the unit they sit at (bottom U), the doors, and the rack's place
// on its network. It's one network device (RackNode), draining what its devices drain. Its lanes: each switch's uplink,
// plus a lane for each device its switches don't pool (Lanes), each its own demand with the device's priority; the
// LaneSolver grants them over any of the rack's uplinks (RackLanes), and a device is online while the rack is and it has
// its lanes (hasLanes: granted, or pooled by a granted switch, or using none).
//
// It also knows the segments its devices can serve (networks beyond Segment Isolators, linked with a Link Card): a
// pooled device set to segment i serves that network instead of the rack's own (network(device)). And it runs the
// rack's Scheduler (RackScheduler) when it has Compute and Memory Servers.
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
    // Linked segments, each found through a block on it (the one next to a Segment Isolator's end).
    private final List<GlobalPos> segments = new ArrayList<>();
    private final RackScheduler scheduler = new RackScheduler(this);
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
    private boolean syncPending;
    private int syncCooldown;
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
        device.onRemoved();
        device.detach();
        device.setSegment(0);
        deviceChanged(true);
        return device;
    }

    // A player mounts the device in their hand at u (the open front, or the screen).
    public boolean installFromHand(ServerPlayer player, ItemStack stack, int u) {
        RackDeviceType type = RackDeviceType.of(stack);
        if (type == null || !(level instanceof ServerLevel serverLevel) || !NetworkAccess.check(serverLevel, worldPosition, player, RackPermission.BUILD)) {
            return false;
        }
        if (!fits(u, type.size())) {
            player.sendOverlayMessage(Component.translatable("gui.encodedlogistics.rack.no_room", type.size()));
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
        if (!(level instanceof ServerLevel serverLevel) || deviceAt(u) == null
                || !NetworkAccess.check(serverLevel, worldPosition, player, RackPermission.BUILD)) {
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

    // A device's settings or state changed.
    public void deviceChanged(boolean topology) {
        if (level instanceof ServerLevel serverLevel) {
            setChanged();
            if (topology) {
                ControllerStructures.get(serverLevel).markTopologyChanged();
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
            ControllerStructures.get(level).markTopologyChanged();
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
            int filled = ControllerStructures.fill(serverLevel.getServer(), network, allowed, transaction);
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
            controllerStructure = structures.addRack(worldPosition);
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
        if (lanes(rackLanes.granted()).isPooled(device)) {
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
    public Lanes lanes() {
        return lanes(null);
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

    // Segments: 0 the rack's own network, then the linked ones.
    public int segmentCount() {
        return 1 + segments.size();
    }

    public Component segmentName(int index) {
        return index == 0 ? Component.translatable("gui.encodedlogistics.switch.segment.default")
                : Component.translatable("gui.encodedlogistics.rack.segment", index);
    }

    // The network a segment is, if it's on one right now.
    public @Nullable NetworkRef segmentNetwork(int index) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (index == 0) {
            return ControllerStructures.networkOf(serverLevel, worldPosition);
        }
        return index - 1 < segments.size() ? RouterDevice.networkAt(serverLevel.getServer(), segments.get(index - 1)) : null;
    }

    public enum SegmentResult {
        LINKED, UNLINKED, FULL
    }

    // Links a segment (a Link Card's); linking one it already has unlinks it.
    public SegmentResult toggleSegment(GlobalPos node) {
        int index = segments.indexOf(node);
        if (index >= 0) {
            unlinkSegment(index + 1);
            return SegmentResult.UNLINKED;
        }
        if (segments.size() >= MAX_SEGMENTS) {
            return SegmentResult.FULL;
        }
        segments.add(node);
        deviceChanged(false);
        return SegmentResult.LINKED;
    }

    // Devices on it go back to the rack's own network; later segments move down one, as do L3 routes through them.
    private void unlinkSegment(int index) {
        segments.remove(index - 1);
        for (RackDevice device : devices.values()) {
            if (device.segment() == index) {
                device.setSegment(0);
            } else if (device.segment() > index) {
                device.setSegment(device.segment() - 1);
            }
            if (device instanceof L3SwitchDevice l3) {
                List<ItemRouting.Route> kept = ItemRouting.withoutEndpoint(l3.routes(), index);
                l3.routes().clear();
                l3.routes().addAll(kept);
            }
        }
        deviceChanged(false);
    }

    // The next (or previous) segment for the device at u, if a switch pools it.
    public void cycleSegment(int u, int step) {
        RackDevice device = deviceAt(u);
        if (device == null || device.lanePool() != null || !lanes().isPooled(device)) {
            return;
        }
        device.setSegment(Math.floorMod(device.segment() + step, segmentCount()));
        deviceChanged(false);
    }

    // The network a device serves: its segment's while a switch pools it, otherwise the rack's own.
    public @Nullable NetworkRef network(RackDevice device) {
        if (device.segment() > 0 && device.segment() < segmentCount() && lanes().isPooled(device)) {
            return segmentNetwork(device.segment());
        }
        return segmentNetwork(0);
    }

    // Its devices serving a network.
    public List<RackDevice> devicesServing(NetworkRef network) {
        List<RackDevice> serving = new ArrayList<>();
        NetworkRef own = segmentNetwork(0);
        Lanes lanes = lanes();
        for (RackDevice device : devices.values()) {
            NetworkRef served = device.segment() > 0 && device.segment() < segmentCount() && lanes.isPooled(device) ? segmentNetwork(device.segment())
                    : own;
            if (network.equals(served)) {
                serving.add(device);
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
        rack.checkControllerStructure(serverLevel);
        rack.checkHome(serverLevel);
        rack.powerThisTick = 0;
        for (RackDevice device : List.copyOf(rack.devices.values())) {
            device.tick(serverLevel);
        }
        rack.scheduler.tick(serverLevel);
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

    private void syncNow() {
        syncPending = false;
        syncCooldown = SYNC_INTERVAL;
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- The screen ---

    public void openMenu(ServerPlayer player) {
        if (!NetworkAccess.check(player.level(), worldPosition, player, RackPermission.VIEW)) {
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
        input.read("segments", GlobalPos.CODEC.listOf()).ifPresent(segments::addAll);
        devices.clear();
        for (ValueInput child : input.childrenListOrEmpty("devices")) {
            RackDevice device = read(child);
            if (device != null) {
                device.load(child.childOrEmpty("data"));
                device.setSegment(child.getIntOr("segment", 0));
                child.getInt("priority").ifPresent(id -> device.setLanePriority(RackDevice.Priority.byId(id)));
                device.setDeviceName(child.getStringOr("device_name", ""));
                devices.put(device.u(), device);
            }
        }
        scheduler.load(input);
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
            if (device.segment() > 0) {
                child.putInt("segment", device.segment());
            }
            child.putInt("priority", device.lanePriority().ordinal());
            if (!device.deviceName().isEmpty()) {
                child.putString("device_name", device.deviceName());
            }
            device.save(child.child("data"));
        }
        if (!segments.isEmpty()) {
            output.store("segments", GlobalPos.CODEC.listOf(), segments);
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
    }
}
