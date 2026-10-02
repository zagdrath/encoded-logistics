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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.LaneResult;
import net.zagdrath.encodedlogistics.network.LaneSolver;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.NetworkDiscovery;
import net.zagdrath.encodedlogistics.network.NetworkGraph;
import net.zagdrath.encodedlogistics.network.NetworkLink;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkPart;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import net.zagdrath.encodedlogistics.network.NodePos;
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
// re-solved only when the topology changed, anywhere: a Bridge can carry a network into another dimension) and the
// FORMED / STATE of its blocks.
//
// Devices (anything using lanes: Drive Bays, terminals) are told every tick whether they're online: on a powered
// network, with their lanes. A part reaches its network's storage (the drives in online Drive Bays and the inventories
// online Inventory Taps face) through storageAt, and takes FE for its work through drawEnergy. Autocrafting finds the
// network's Fabricators and Gateways (providersAt) and Schedulers (schedulersAt) the same way, Handheld Terminals its
// Relay Antennas (relays). Which network a node is on is kept across dimensions (NetworkIndex), so these work from any
// level. Cables and part hosts with ticking parts tick from here, after the networks.
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
    // Cables and part hosts holding parts that tick.
    private final Set<CableBlockEntity> ticking = new LinkedHashSet<>();
    private boolean topologyChanged;
    // The NetworkIndex generation this level's networks were last solved for.
    private int seenGeneration = -1;

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

    // A network node was added, removed or reconnected somewhere: re-solve every network (in every level) next tick.
    public void markTopologyChanged() {
        topologyChanged = true;
    }

    // --- Ticking ---

    public void tick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        NetworkIndex index = NetworkIndex.get(server);
        processPending(level, index);
        if (topologyChanged) {
            topologyChanged = false;
            index.generation++;
        }
        boolean topology = index.generation != seenGeneration;
        seenGeneration = index.generation;
        List<NodePos> online = new ArrayList<>();
        for (Structure structure : List.copyOf(structures.values())) {
            Runtime runtime = runtimes.computeIfAbsent(structure.id(), id -> new Runtime());
            if (topology) {
                runtime.dirty = true;
            }
            Set<NodePos> before = runtime.online;
            tickStructure(level, index, structure, runtime);
            for (NodePos pos : before) {
                if (!runtime.online.contains(pos)) {
                    setDeviceOnline(server, pos, false);
                }
            }
            online.addAll(runtime.online);
        }
        for (NodePos pos : online) {
            setDeviceOnline(server, pos, true);
        }
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

    private static void setDeviceOnline(MinecraftServer server, NodePos pos, boolean online) {
        if (blockEntity(server, pos) instanceof NetworkDevice device) {
            device.setNetworkOnline(online);
        }
    }

    private void processPending(ServerLevel level, NetworkIndex index) {
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
                Runtime runtime = runtimes.remove(id);
                if (runtime != null) {
                    // Its devices go offline and leave the index.
                    runtime.online.forEach(pos -> setDeviceOnline(level.getServer(), pos, false));
                    NetworkRef ref = new NetworkRef(level.dimension(), id);
                    runtime.members.forEach(pos -> index.members.remove(pos, ref));
                }
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

    private void tickStructure(ServerLevel level, NetworkIndex index, Structure structure, Runtime runtime) {
        List<NetworkControllerBlockEntity> blocks = new ArrayList<>(structure.members().size());
        for (BlockPos pos : structure.members()) {
            // Part of it unloaded: the structure waits until all of it is back (and its devices are offline meanwhile).
            if (!level.isLoaded(pos)) {
                runtime.online = Set.of();
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller) || controller.getStructureId() != structure.id()) {
                queue(pos);
                runtime.online = Set.of();
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
        List<CapacitorBankBlockEntity> banks = banks(level.getServer(), runtime);
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
            setNetworkNodes(level, index, structure.id(), runtime, null);
        } else {
            if (runtime.dirty || runtime.discovered == null || runtime.lanes == null) {
                runtime.discovered = NetworkDiscovery.discover(level, structure.id(), structure.members(),
                        Config.LANES_PER_CONTROLLER_FACE.getAsInt());
                runtime.lanes = LaneSolver.solve(runtime.discovered.graph(), Config.ADHOC_MAX_DEVICES.getAsInt());
                runtime.dirty = false;
                setNetworkNodes(level, index, structure.id(), runtime,
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
        Set<NodePos> online = new HashSet<>();
        if (status == NetworkStatus.ONLINE && runtime.lanes != null) {
            for (NodePos device : runtime.devices) {
                if (runtime.lanes.hasLane(device)) {
                    online.add(device);
                }
            }
        }
        runtime.online = online;
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

    // Records what's on this structure's network, in the index and by kind: its Capacitor Banks (its energy), its
    // devices, Drive Bays, part hosts, autocrafting blocks and Relay Antennas. Nothing when it has no working network.
    private void setNetworkNodes(ServerLevel level, NetworkIndex index, long id, Runtime runtime, NetworkDiscovery.@Nullable Discovered discovered) {
        NetworkRef ref = new NetworkRef(level.dimension(), id);
        runtime.members.forEach(pos -> index.members.remove(pos, ref));
        List<NodePos> members = new ArrayList<>(), banks = new ArrayList<>(), devices = new ArrayList<>(), driveBays = new ArrayList<>(),
                partHosts = new ArrayList<>(), providers = new ArrayList<>(), schedulers = new ArrayList<>(), relays = new ArrayList<>();
        if (discovered != null) {
            discovered.graph().entries().forEach((pos, node) -> {
                if (node.isController()) {
                    return;
                }
                members.add(pos);
                index.members.put(pos, ref);
                if (node.laneCost() > 0) {
                    devices.add(pos);
                }
                BlockEntity blockEntity = blockEntity(level.getServer(), pos);
                if (blockEntity instanceof CapacitorBankBlockEntity) {
                    banks.add(pos);
                } else if (blockEntity instanceof DriveBayBlockEntity) {
                    driveBays.add(pos);
                } else if (blockEntity instanceof CraftingProvider) {
                    providers.add(pos);
                } else if (blockEntity instanceof SchedulerCoreBlockEntity && node.laneCost() > 0) {
                    schedulers.add(pos);
                } else if (blockEntity instanceof RelayAntennaBlockEntity) {
                    relays.add(pos);
                } else if (blockEntity instanceof CableBlockEntity host && host.getAttachments().hasParts()) {
                    partHosts.add(pos);
                }
            });
        }
        for (List<NodePos> list : List.of(banks, driveBays, partHosts, providers, schedulers, relays)) {
            list.sort(NetworkGraph.ORDER);
        }
        runtime.members = List.copyOf(members);
        runtime.banks = List.copyOf(banks);
        runtime.devices = List.copyOf(devices);
        runtime.driveBays = List.copyOf(driveBays);
        runtime.partHosts = List.copyOf(partHosts);
        runtime.providers = List.copyOf(providers);
        runtime.schedulers = List.copyOf(schedulers);
        runtime.relays = List.copyOf(relays);
    }

    private static List<CapacitorBankBlockEntity> banks(MinecraftServer server, Runtime runtime) {
        List<CapacitorBankBlockEntity> banks = new ArrayList<>(runtime.banks.size());
        for (NodePos pos : runtime.banks) {
            if (blockEntity(server, pos) instanceof CapacitorBankBlockEntity bank) {
                banks.add(bank);
            }
        }
        return banks;
    }

    // The block entity at a position in any dimension, if that's loaded.
    private static @Nullable BlockEntity blockEntity(MinecraftServer server, NodePos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        return level != null && level.isLoaded(pos.pos()) ? level.getBlockEntity(pos.pos()) : null;
    }

    // --- Finding a node's network ---

    // A network found from one of its nodes: its home level and structures, the structure and its live state.
    private record Owner(ServerLevel home, Structure structure, Runtime runtime, NetworkRef ref) {}

    private static @Nullable Owner owner(MinecraftServer server, @Nullable NetworkRef ref) {
        if (ref == null) {
            return null;
        }
        ServerLevel home = server.getLevel(ref.dimension());
        if (home == null) {
            return null;
        }
        ControllerStructures structures = get(home);
        Structure structure = structures.structures.get(ref.id());
        Runtime runtime = structures.runtimes.get(ref.id());
        return structure != null && runtime != null ? new Owner(home, structure, runtime, ref) : null;
    }

    private static @Nullable Owner owner(ServerLevel level, BlockPos pos) {
        return owner(level.getServer(), networkOf(level, pos));
    }

    // The network the node at pos is on (a controller's network that isn't in conflict), or null.
    public static @Nullable NetworkRef networkOf(ServerLevel level, BlockPos pos) {
        return NetworkIndex.get(level.getServer()).members.get(NetworkGraph.at(level.dimension(), pos));
    }

    // Whether a network runs: valid, powered and not in conflict.
    public static boolean isOnline(MinecraftServer server, @Nullable NetworkRef ref) {
        Owner owner = owner(server, ref);
        return owner != null && owner.runtime.status == NetworkStatus.ONLINE;
    }

    // --- Storage ---

    // Whether the device at pos is on a powered network with its lanes (as of the last tick).
    public boolean isDeviceOnline(ServerLevel level, BlockPos pos) {
        Owner owner = owner(level, pos);
        return owner != null && owner.runtime.online.contains(NetworkGraph.at(level.dimension(), pos));
    }

    // The storage a device at pos (a terminal, port, sensor) reaches: the drives in the online Drive Bays on its network
    // and the inventories its online Inventory Taps face. Null while the device itself is offline.
    public @Nullable NetworkStorage storageAt(ServerLevel level, BlockPos device) {
        Owner owner = owner(level, device);
        if (owner == null || !owner.runtime.online.contains(NetworkGraph.at(level.dimension(), device))) {
            return null;
        }
        MinecraftServer server = level.getServer();
        List<StorageView> views = new ArrayList<>();
        DriveStorage drives = DriveStorage.get(server);
        for (NodePos pos : owner.runtime.driveBays) {
            if (owner.runtime.online.contains(pos) && blockEntity(server, pos) instanceof DriveBayBlockEntity bay) {
                for (int slot = 0; slot < DriveBayBlockEntity.SLOTS; slot++) {
                    ItemStack stack = bay.drive(slot);
                    if (stack != null && stack.getItem() instanceof StorageDriveItem drive) {
                        views.add(new DriveView(drives, bay, slot, StorageDriveItem.id(stack), drive.getTier()));
                    }
                }
            }
        }
        for (NodePos pos : owner.runtime.partHosts) {
            if (owner.runtime.online.contains(pos) && blockEntity(server, pos) instanceof CableBlockEntity host
                    && host.getLevel() instanceof ServerLevel hostLevel) {
                for (CablePart part : host.parts()) {
                    if (part instanceof InventoryTapPart tap) {
                        StorageView view = tap.view(hostLevel);
                        if (view != null) {
                            views.add(view);
                        }
                    }
                }
            }
        }
        return new NetworkStorage(views);
    }

    // --- Autocrafting ---

    // The online Fabricators and Gateways on the network a device at pos is on (empty while it's offline).
    public List<CraftingProvider> providersAt(ServerLevel level, BlockPos device) {
        Runtime runtime = onlineRuntime(level, device);
        List<CraftingProvider> providers = new ArrayList<>();
        if (runtime != null) {
            for (NodePos pos : runtime.providers) {
                if (runtime.online.contains(pos) && blockEntity(level.getServer(), pos) instanceof CraftingProvider provider) {
                    providers.add(provider);
                }
            }
        }
        return providers;
    }

    // The online, formed Schedulers on the network a device at pos is on.
    public List<SchedulerCoreBlockEntity> schedulersAt(ServerLevel level, BlockPos device) {
        Runtime runtime = onlineRuntime(level, device);
        List<SchedulerCoreBlockEntity> schedulers = new ArrayList<>();
        if (runtime != null) {
            for (NodePos pos : runtime.schedulers) {
                if (runtime.online.contains(pos) && blockEntity(level.getServer(), pos) instanceof SchedulerCoreBlockEntity core && core.formed()) {
                    schedulers.add(core);
                }
            }
        }
        return schedulers;
    }

    // Whether two nodes in this level are on the same network.
    public boolean sameNetwork(ServerLevel level, BlockPos a, BlockPos b) {
        NetworkRef ref = networkOf(level, a);
        return ref != null && ref.equals(networkOf(level, b));
    }

    private static @Nullable Runtime onlineRuntime(ServerLevel level, BlockPos device) {
        Owner owner = owner(level, device);
        return owner != null && owner.runtime.online.contains(NetworkGraph.at(level.dimension(), device)) ? owner.runtime : null;
    }

    // --- Wireless and links ---

    // The online Relay Antennas of a network, wherever they are.
    public static List<RelayAntennaBlockEntity> relays(MinecraftServer server, @Nullable NetworkRef ref) {
        Owner owner = owner(server, ref);
        List<RelayAntennaBlockEntity> relays = new ArrayList<>();
        if (owner != null && owner.runtime.status == NetworkStatus.ONLINE) {
            for (NodePos pos : owner.runtime.relays) {
                if (owner.runtime.online.contains(pos) && blockEntity(server, pos) instanceof RelayAntennaBlockEntity relay) {
                    relays.add(relay);
                }
            }
        }
        return relays;
    }

    // Lanes running over the remote link between two nodes (a Bridge pair), as last solved; 0 when they aren't linked.
    public static int remoteUsage(MinecraftServer server, NodePos a, NodePos b) {
        Owner owner = owner(server, NetworkIndex.get(server).members.get(a));
        if (owner == null || owner.runtime.discovered == null || owner.runtime.lanes == null) {
            return 0;
        }
        NetworkLink link = owner.runtime.discovered.graph().remoteLink(a, b);
        return link != null ? owner.runtime.lanes.usage(link) : 0;
    }

    // Whether the remote link between two nodes is part of a network right now.
    public static boolean remoteLinked(MinecraftServer server, NodePos a, NodePos b) {
        Owner owner = owner(server, NetworkIndex.get(server).members.get(a));
        return owner != null && owner.runtime.discovered != null && owner.runtime.discovered.graph().remoteLink(a, b) != null;
    }

    // --- Energy for work ---

    // Takes up to amount FE from the energy of the network a device at pos is on (banks first, then controllers), for
    // work beyond its passive drain. Returns what it got.
    public int drawEnergy(ServerLevel level, BlockPos device, int amount) {
        Owner owner = owner(level, device);
        if (owner == null || owner.runtime.status != NetworkStatus.ONLINE || amount <= 0) {
            return 0;
        }
        int left = amount;
        for (CapacitorBankBlockEntity bank : banks(level.getServer(), owner.runtime)) {
            if (left <= 0) {
                break;
            }
            left -= bank.drain(left);
        }
        for (BlockPos pos : owner.structure.members()) {
            if (left <= 0) {
                break;
            }
            if (owner.home.isLoaded(pos) && owner.home.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller
                    && controller.getStructureId() == owner.ref.id()) {
                left -= controller.drain(left);
            }
        }
        return amount - left;
    }

    // --- Energy from outside ---

    // The network whose energy a Power Inlet or Capacitor Bank at pos is part of, or null when it isn't on a
    // controller's network.
    public @Nullable NetworkRef energyNetworkOf(ServerLevel level, BlockPos pos) {
        return networkOf(level, pos);
    }

    // Puts up to amount FE into a network's energy: its controllers first, then its banks. Returns what went in.
    public static int fill(MinecraftServer server, NetworkRef network, int amount, TransactionContext transaction) {
        Owner owner = owner(server, network);
        if (owner == null || owner.runtime.status.isError()) {
            return 0;
        }
        int left = amount;
        for (BlockPos pos : owner.structure.members()) {
            if (left <= 0) {
                break;
            }
            if (owner.home.isLoaded(pos) && owner.home.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller
                    && controller.getStructureId() == network.id()) {
                left -= controller.fill(left, transaction);
            }
        }
        for (CapacitorBankBlockEntity bank : banks(server, owner.runtime)) {
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
            runtime.discovered.graph().entries().forEach((pos, node) -> {
                if (node.isController()) {
                    return;
                }
                boolean missingLane = online && node.laneCost() > 0 && !lanes.hasLane(pos);
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
                Item item = runtime.discovered.items().get(pos);
                if (item == null || item == Items.AIR) {
                    return;
                }
                int[] count = counts.computeIfAbsent(item, key -> new int[2]);
                count[0]++;
                if (missingLane && node.parts().isEmpty()) {
                    count[1]++;
                }
                drains.merge(item, node.passiveDrain() - partsDrain, Double::sum);
            });
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
        // Every node on the network but the controllers (as entered in the index), and by kind.
        List<NodePos> members = List.of(), banks = List.of(), devices = List.of(), driveBays = List.of(), partHosts = List.of(),
                providers = List.of(), schedulers = List.of(), relays = List.of();
        // The devices online as of the last tick.
        Set<NodePos> online = Set.of();
        final int[] received = new int[GENERATION_WINDOW];
        int receivedIndex;
        double drainCarry;
        NetworkStatus status = NetworkStatus.NO_POWER;
        long stored, capacity;
        double usage, generation;
        int comparator = -1;
    }
}
