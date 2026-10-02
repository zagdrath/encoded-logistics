/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.ControllerState;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.CapacitorBankBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.network.LaneResult;
import net.zagdrath.encodedlogistics.network.LaneSolver;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.NetworkDiscovery;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkPart;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import net.zagdrath.encodedlogistics.part.CablePart;
import net.zagdrath.encodedlogistics.part.InventoryTapPart;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.DriveView;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageView;

// Every Network Controller structure in a level, by id, saved with the level. Each controller block entity holds its
// structure's id.
//
// Placing or breaking a controller queues the positions around it; once per tick the queued positions are flood-filled
// into groups, each group is validated (ControllerFrame) and keeps the id most of it had, or gets a new one. Then every
// structure ticks: its energy (buffer, drain, FE received over the last 20 ticks), its network (rediscovered and
// re-solved only when the topology changed) and the FORMED / STATE of its blocks.
//
// Devices (anything using lanes: Drive Bays, terminals) are told every tick whether they're online: on a powered
// network, with their lanes. A part reaches its network's storage (the drives in online Drive Bays and the inventories
// online Inventory Taps face) through storageAt, and takes FE for its work through drawEnergy. Cables and part hosts
// with ticking parts tick from here, after the networks.
//
// A network's energy is its controllers' buffers plus the Capacitor Banks on it. FE coming in through Power Inlets
// (fill) goes to the controllers first, then the banks; the network's drain comes out of the banks first, so the
// controllers stay topped up.
public class ControllerStructures extends SavedData {
    // A connected group of controllers bigger than this is cut off where the flood fill stops (it's invalid anyway).
    private static final int MAX_GROUP = 4096;
    private static final int GENERATION_WINDOW = 20;

    public record Structure(long id, List<BlockPos> members, ControllerFrame.Problem problem) {
        public static final Codec<Structure> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Structure::id),
                BlockPos.CODEC.listOf().fieldOf("members").forGetter(Structure::members),
                Codec.STRING.xmap(ControllerStructures::problemByName, ControllerFrame.Problem::name).fieldOf("problem")
                        .forGetter(Structure::problem))
                .apply(i, Structure::new));

        // INVALID_SHAPE or TOO_LARGE when the shape is wrong, otherwise null.
        public @Nullable NetworkStatus shapeStatus() {
            return switch (problem) {
                case NONE -> null;
                case INVALID_SHAPE -> NetworkStatus.INVALID_SHAPE;
                case TOO_LARGE -> NetworkStatus.TOO_LARGE;
            };
        }

        public BlockPos min() {
            return members.stream().reduce(members.getFirst(), ControllerStructures::min);
        }

        public BlockPos max() {
            return members.stream().reduce(members.getFirst(), ControllerStructures::max);
        }
    }

    private static final Codec<ControllerStructures> CODEC = RecordCodecBuilder.create(i -> i.group(
            Structure.CODEC.listOf().fieldOf("structures").forGetter(data -> List.copyOf(data.structures.values())),
            Codec.LONG.fieldOf("next_id").forGetter(data -> data.nextId))
            .apply(i, ControllerStructures::new));

    public static final SavedDataType<ControllerStructures> TYPE = new SavedDataType<>(
            EncodedLogistics.id("controller_structures"), ControllerStructures::new, CODEC);

    private final Map<Long, Structure> structures = new LinkedHashMap<>();
    private long nextId = 1;

    // Not saved: rebuilt from the world as structures tick.
    private final Set<BlockPos> pending = new HashSet<>();
    private final Set<Long> touched = new HashSet<>();
    private final Map<Long, Runtime> runtimes = new HashMap<>();
    // Power Inlets and Capacitor Banks on a controller's network, by position: which structure's energy they're part of.
    private final Map<BlockPos, Long> energyNetworks = new HashMap<>();
    // Devices on a controller's network, by position, and the ones online as of the last tick.
    private final Map<BlockPos, Long> deviceNetworks = new HashMap<>();
    private Set<BlockPos> onlineDevices = new HashSet<>();
    // Cables and part hosts holding parts that tick.
    private final Set<CableBlockEntity> ticking = new LinkedHashSet<>();
    private boolean topologyChanged;

    public ControllerStructures() {}

    private ControllerStructures(List<Structure> saved, long nextId) {
        saved.forEach(structure -> structures.put(structure.id(), structure));
        this.nextId = nextId;
    }

    public static ControllerStructures get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Structure get(long id) {
        return structures.get(id);
    }

    // --- Changes ---

    public void queue(BlockPos pos) {
        pending.add(pos.immutable());
    }

    public void queueNeighbours(BlockPos pos) {
        for (Direction side : Direction.values()) {
            pending.add(pos.relative(side));
        }
    }

    // A controller was removed: its old structure is rebuilt from whatever of it is left.
    public void queueRemoved(BlockPos pos, long structureId) {
        if (structureId > 0) {
            touched.add(structureId);
        }
        queueNeighbours(pos);
    }

    // A network node was added, removed or reconnected somewhere: re-solve every network next tick.
    public void markTopologyChanged() {
        topologyChanged = true;
    }

    // --- Ticking ---

    public void tick(ServerLevel level) {
        processPending(level);
        boolean topology = topologyChanged;
        topologyChanged = false;
        Set<BlockPos> online = new HashSet<>();
        for (Structure structure : List.copyOf(structures.values())) {
            Runtime runtime = runtimes.computeIfAbsent(structure.id(), id -> new Runtime());
            if (topology) {
                runtime.dirty = true;
            }
            tickStructure(level, structure, runtime, online);
        }
        for (BlockPos pos : onlineDevices) {
            if (!online.contains(pos)) {
                setDeviceOnline(level, pos, false);
            }
        }
        for (BlockPos pos : online) {
            setDeviceOnline(level, pos, true);
        }
        onlineDevices = online;
        for (CableBlockEntity host : List.copyOf(ticking)) {
            if (host.isRemoved()) {
                ticking.remove(host);
            } else {
                host.tickParts(level);
            }
        }
    }

    // A cable or part host starts or stops holding parts that tick.
    public void setTicking(CableBlockEntity host, boolean ticks) {
        if (ticks) {
            ticking.add(host);
        } else {
            ticking.remove(host);
        }
    }

    private static void setDeviceOnline(ServerLevel level, BlockPos pos, boolean online) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof NetworkDevice device) {
            device.setNetworkOnline(online);
        }
    }

    private void processPending(ServerLevel level) {
        if (pending.isEmpty() && touched.isEmpty()) {
            return;
        }
        List<BlockPos> order = new ArrayList<>(pending);
        order.sort(Comparator.naturalOrder());
        pending.clear();
        Set<BlockPos> done = new HashSet<>();
        Set<Long> claimed = new HashSet<>();
        int maxSize = Config.CONTROLLER_MAX_SIZE.getAsInt();
        for (BlockPos start : order) {
            if (done.contains(start) || !isController(level, start)) {
                continue;
            }
            List<BlockPos> group = floodFill(level, start);
            done.addAll(group);
            group.sort(Comparator.naturalOrder());
            // Keep the lowest id the group's blocks had that no other group took this tick.
            TreeSet<Long> oldIds = new TreeSet<>();
            for (BlockPos pos : group) {
                if (level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller && controller.getStructureId() > 0) {
                    oldIds.add(controller.getStructureId());
                }
            }
            long id = oldIds.stream().filter(old -> !claimed.contains(old)).findFirst().orElseGet(() -> nextId++);
            claimed.add(id);
            touched.addAll(oldIds);
            ControllerFrame.Problem problem = ControllerFrame.validate(group, maxSize).problem();
            structures.put(id, new Structure(id, List.copyOf(group), problem));
            runtimes.computeIfAbsent(id, key -> new Runtime()).dirty = true;
            for (BlockPos pos : group) {
                if (level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller) {
                    controller.setStructureId(id);
                }
            }
        }
        for (long id : touched) {
            if (!claimed.contains(id)) {
                structures.remove(id);
                runtimes.remove(id);
                energyNetworks.values().removeIf(owner -> owner == id);
                deviceNetworks.values().removeIf(owner -> owner == id);
            }
        }
        touched.clear();
        // A structure appearing or disappearing can change the networks next to it.
        topologyChanged = true;
        setDirty();
    }

    private static List<BlockPos> floodFill(ServerLevel level, BlockPos start) {
        List<BlockPos> group = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && group.size() < MAX_GROUP) {
            BlockPos pos = queue.poll();
            group.add(pos);
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (seen.add(next) && isController(level, next)) {
                    queue.add(next);
                }
            }
        }
        return group;
    }

    private static boolean isController(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof NetworkControllerBlock;
    }

    private void tickStructure(ServerLevel level, Structure structure, Runtime runtime, Set<BlockPos> online) {
        List<NetworkControllerBlockEntity> blocks = new ArrayList<>(structure.members().size());
        for (BlockPos pos : structure.members()) {
            // Part of it unloaded: the structure waits until all of it is back.
            if (!level.isLoaded(pos)) {
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller) || controller.getStructureId() != structure.id()) {
                queue(pos);
                return;
            }
            blocks.add(controller);
        }

        int received = 0;
        long stored = 0, capacity = 0;
        for (NetworkControllerBlockEntity controller : blocks) {
            received += controller.takeReceived();
            stored += controller.getEnergy();
            capacity += controller.getCapacity();
        }
        List<CapacitorBankBlockEntity> banks = banks(level, runtime);
        for (CapacitorBankBlockEntity bank : banks) {
            received += bank.lastInput();
            stored += bank.getStored();
            capacity += bank.getCapacity();
        }
        runtime.received[runtime.receivedIndex] = received;
        runtime.receivedIndex = (runtime.receivedIndex + 1) % GENERATION_WINDOW;
        long window = 0;
        for (int amount : runtime.received) {
            window += amount;
        }

        NetworkStatus status = structure.shapeStatus();
        double usage = 0;
        if (status != null) {
            runtime.discovered = null;
            runtime.lanes = null;
            runtime.dirty = true;
            setNetworkNodes(level, structure.id(), runtime, null);
        } else {
            if (runtime.dirty || runtime.discovered == null || runtime.lanes == null) {
                runtime.discovered = NetworkDiscovery.discover(level, structure.id(), structure.members(),
                        Config.LANES_PER_CONTROLLER_FACE.getAsInt());
                runtime.lanes = LaneSolver.solve(runtime.discovered.graph(), Config.ADHOC_MAX_DEVICES.getAsInt());
                runtime.dirty = false;
                setNetworkNodes(level, structure.id(), runtime,
                        runtime.lanes.status() == NetworkStatus.CONFLICT ? null : runtime.discovered);
            }
            if (runtime.lanes.status() == NetworkStatus.CONFLICT) {
                status = NetworkStatus.CONFLICT;
            } else {
                usage = Config.CONTROLLER_DRAIN.getAsDouble() * blocks.size();
                for (NetworkNode node : runtime.discovered.graph().nodes()) {
                    if (!node.isController()) {
                        usage += node.passiveDrain();
                    }
                }
                runtime.drainCarry += usage;
                int toDrain = (int) runtime.drainCarry;
                runtime.drainCarry -= toDrain;
                int left = toDrain;
                for (CapacitorBankBlockEntity bank : banks) {
                    if (left <= 0) {
                        break;
                    }
                    left -= bank.drain(left);
                }
                for (NetworkControllerBlockEntity controller : blocks) {
                    if (left <= 0) {
                        break;
                    }
                    left -= controller.drain(left);
                }
                stored -= toDrain - left;
                status = stored > 0 ? NetworkStatus.ONLINE : NetworkStatus.NO_POWER;
                if (status == NetworkStatus.NO_POWER) {
                    runtime.drainCarry = 0;
                }
            }
        }
        runtime.status = status;
        if (status == NetworkStatus.ONLINE && runtime.lanes != null) {
            for (BlockPos device : runtime.devices) {
                if (runtime.lanes.hasLane(device)) {
                    online.add(device);
                }
            }
        }
        runtime.stored = stored;
        runtime.capacity = capacity;
        runtime.usage = usage;
        runtime.generation = (double) window / GENERATION_WINDOW;

        applyStates(level, structure, status);

        int signal = capacity <= 0 || stored <= 0 ? 0 : 1 + (int) (stored * 14 / capacity);
        if (signal != runtime.comparator) {
            runtime.comparator = signal;
            for (BlockPos pos : structure.members()) {
                level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
            }
        }
    }

    // Records what's on this structure's network: its Power Inlets and Capacitor Banks (its energy), its devices and
    // Drive Bays. Nothing when it has no working network.
    private void setNetworkNodes(ServerLevel level, long id, Runtime runtime, NetworkDiscovery.@Nullable Discovered discovered) {
        energyNetworks.values().removeIf(owner -> owner == id);
        deviceNetworks.values().removeIf(owner -> owner == id);
        List<BlockPos> banks = new ArrayList<>(), devices = new ArrayList<>(), driveBays = new ArrayList<>(), partHosts = new ArrayList<>();
        if (discovered != null) {
            for (NetworkNode node : discovered.graph().nodes()) {
                if (node.laneCost() > 0) {
                    devices.add(node.pos());
                    deviceNetworks.put(node.pos(), id);
                }
                var blockEntity = level.getBlockEntity(node.pos());
                if (blockEntity instanceof CapacitorBankBlockEntity) {
                    banks.add(node.pos());
                    energyNetworks.put(node.pos(), id);
                } else if (blockEntity instanceof PowerInletBlockEntity) {
                    energyNetworks.put(node.pos(), id);
                } else if (blockEntity instanceof DriveBayBlockEntity) {
                    driveBays.add(node.pos());
                } else if (blockEntity instanceof CableBlockEntity host && host.getAttachments().hasParts()) {
                    partHosts.add(node.pos());
                }
            }
        }
        banks.sort(Comparator.naturalOrder());
        driveBays.sort(Comparator.naturalOrder());
        runtime.banks = List.copyOf(banks);
        runtime.devices = List.copyOf(devices);
        runtime.driveBays = List.copyOf(driveBays);
        partHosts.sort(Comparator.naturalOrder());
        runtime.partHosts = List.copyOf(partHosts);
    }

    private static List<CapacitorBankBlockEntity> banks(ServerLevel level, Runtime runtime) {
        List<CapacitorBankBlockEntity> banks = new ArrayList<>(runtime.banks.size());
        for (BlockPos pos : runtime.banks) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CapacitorBankBlockEntity bank) {
                banks.add(bank);
            }
        }
        return banks;
    }

    // --- Storage ---

    // Whether the device at pos is on a powered network with its lanes (as of the last tick).
    public boolean isDeviceOnline(BlockPos pos) {
        return onlineDevices.contains(pos);
    }

    // The storage a device at pos (a terminal, port, sensor) reaches: the drives in the online Drive Bays on its network
    // and the inventories its online Inventory Taps face. Null while the device itself is offline.
    public @Nullable NetworkStorage storageAt(ServerLevel level, BlockPos device) {
        Long id = deviceNetworks.get(device);
        Runtime runtime = id != null ? runtimes.get(id) : null;
        if (runtime == null || !onlineDevices.contains(device)) {
            return null;
        }
        List<StorageView> views = new ArrayList<>();
        DriveStorage drives = DriveStorage.get(level.getServer());
        for (BlockPos pos : runtime.driveBays) {
            if (onlineDevices.contains(pos) && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof DriveBayBlockEntity bay) {
                for (int slot = 0; slot < DriveBayBlockEntity.SLOTS; slot++) {
                    ItemStack stack = bay.drive(slot);
                    if (stack != null && stack.getItem() instanceof StorageDriveItem drive) {
                        views.add(new DriveView(drives, bay, slot, StorageDriveItem.id(stack), drive.getTier()));
                    }
                }
            }
        }
        for (BlockPos pos : runtime.partHosts) {
            if (onlineDevices.contains(pos) && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CableBlockEntity host) {
                for (CablePart part : host.parts()) {
                    if (part instanceof InventoryTapPart tap) {
                        StorageView view = tap.view(level);
                        if (view != null) {
                            views.add(view);
                        }
                    }
                }
            }
        }
        return new NetworkStorage(views);
    }

    // --- Energy for work ---

    // Takes up to amount FE from the energy of the network a device at pos is on (banks first, then controllers), for
    // work beyond its passive drain. Returns what it got.
    public int drawEnergy(ServerLevel level, BlockPos device, int amount) {
        Long id = deviceNetworks.get(device);
        Structure structure = id != null ? structures.get(id) : null;
        Runtime runtime = id != null ? runtimes.get(id) : null;
        if (structure == null || runtime == null || runtime.status != NetworkStatus.ONLINE || amount <= 0) {
            return 0;
        }
        int left = amount;
        for (CapacitorBankBlockEntity bank : banks(level, runtime)) {
            if (left <= 0) {
                break;
            }
            left -= bank.drain(left);
        }
        for (BlockPos pos : structure.members()) {
            if (left <= 0) {
                break;
            }
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller && controller.getStructureId() == id) {
                left -= controller.drain(left);
            }
        }
        return amount - left;
    }

    // --- Energy from outside ---

    // The structure whose energy a Power Inlet or Capacitor Bank at pos is part of, or 0 when it isn't on a
    // controller's network.
    public long energyNetworkOf(BlockPos pos) {
        return energyNetworks.getOrDefault(pos, 0L);
    }

    // Puts up to amount FE into a structure's energy: its controllers first, then its banks. Returns what went in.
    public int fill(ServerLevel level, long id, int amount, TransactionContext transaction) {
        Structure structure = structures.get(id);
        Runtime runtime = runtimes.get(id);
        if (structure == null || runtime == null || runtime.status.isError()) {
            return 0;
        }
        int left = amount;
        for (BlockPos pos : structure.members()) {
            if (left <= 0) {
                break;
            }
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller
                    && controller.getStructureId() == id) {
                left -= controller.fill(left, transaction);
            }
        }
        for (CapacitorBankBlockEntity bank : banks(level, runtime)) {
            if (left <= 0) {
                break;
            }
            left -= bank.fill(left, transaction);
        }
        return amount - left;
    }

    private static void applyStates(ServerLevel level, Structure structure, NetworkStatus status) {
        boolean formed = structure.problem() == ControllerFrame.Problem.NONE && structure.members().size() > 1;
        ControllerState shown = status.isError() ? ControllerState.ERROR
                : status == NetworkStatus.ONLINE ? ControllerState.ONLINE : ControllerState.OFFLINE;
        for (BlockPos pos : structure.members()) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof NetworkControllerBlock
                    && (state.getValue(NetworkControllerBlock.FORMED) != formed || state.getValue(NetworkControllerBlock.STATE) != shown)) {
                level.setBlock(pos, state.setValue(NetworkControllerBlock.FORMED, formed).setValue(NetworkControllerBlock.STATE, shown),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    // --- Reading ---

    public int comparatorSignal(long id) {
        Runtime runtime = runtimes.get(id);
        return runtime != null ? Math.max(0, runtime.comparator) : 0;
    }

    public NetworkSnapshot snapshot(long id) {
        Structure structure = structures.get(id);
        if (structure == null) {
            return NetworkSnapshot.EMPTY;
        }
        Runtime runtime = runtimes.getOrDefault(id, new Runtime());
        BlockPos min = structure.min(), max = structure.max();
        LaneResult lanes = runtime.lanes;
        List<NetworkSnapshot.DeviceEntry> devices = new ArrayList<>();
        boolean online = runtime.status == NetworkStatus.ONLINE;
        // The structure's own blocks come first in the list (sorted with the rest by count).
        devices.add(new NetworkSnapshot.DeviceEntry(BuiltInRegistries.ITEM.getKey(ModItems.NETWORK_CONTROLLER.get()), structure.members().size(),
                Config.CONTROLLER_DRAIN.getAsDouble() * structure.members().size(), 0, !online));
        if (runtime.discovered != null && lanes != null) {
            Map<Item, int[]> counts = new LinkedHashMap<>();
            Map<Item, Double> drains = new HashMap<>();
            for (NetworkNode node : runtime.discovered.graph().nodes()) {
                if (node.isController()) {
                    continue;
                }
                boolean missingLane = online && node.laneCost() > 0 && !lanes.hasLane(node.pos());
                // Parts (terminals) are listed on their own; the block they're on keeps the rest of the drain.
                double partsDrain = 0;
                for (NetworkPart part : node.parts()) {
                    int[] count = counts.computeIfAbsent(part.item(), key -> new int[2]);
                    count[0]++;
                    if (missingLane) {
                        count[1]++;
                    }
                    drains.merge(part.item(), part.passiveDrain(), Double::sum);
                    partsDrain += part.passiveDrain();
                }
                Item item = runtime.discovered.items().get(node.pos());
                if (item == null || item == Items.AIR) {
                    continue;
                }
                int[] count = counts.computeIfAbsent(item, key -> new int[2]);
                count[0]++;
                if (missingLane && node.parts().isEmpty()) {
                    count[1]++;
                }
                drains.merge(item, node.passiveDrain() - partsDrain, Double::sum);
            }
            counts.forEach((item, count) -> {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                devices.add(new NetworkSnapshot.DeviceEntry(itemId, count[0], drains.getOrDefault(item, 0.0), count[1], !online));
            });
        }
        devices.sort(Comparator.comparingInt(NetworkSnapshot.DeviceEntry::count).reversed()
                .thenComparing(entry -> entry.item().toString()));
        return new NetworkSnapshot(runtime.status, runtime.stored, runtime.capacity, runtime.usage, runtime.generation,
                lanes != null ? lanes.used() : 0, lanes != null ? lanes.capacity() : 0,
                max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1, structure.members().size(),
                devices);
    }

    // --- Helpers ---

    private static ControllerFrame.Problem problemByName(String name) {
        for (ControllerFrame.Problem problem : ControllerFrame.Problem.values()) {
            if (problem.name().equals(name)) {
                return problem;
            }
        }
        return ControllerFrame.Problem.INVALID_SHAPE;
    }

    private static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    // A structure's live state: its network, energy figures and what its blocks show.
    private static final class Runtime {
        boolean dirty = true;
        NetworkDiscovery.@Nullable Discovered discovered;
        @Nullable LaneResult lanes;
        List<BlockPos> banks = List.of(), devices = List.of(), driveBays = List.of(), partHosts = List.of();
        final int[] received = new int[GENERATION_WINDOW];
        int receivedIndex;
        double drainCarry;
        NetworkStatus status = NetworkStatus.NO_POWER;
        long stored, capacity;
        double usage, generation;
        int comparator = -1;
    }
}
