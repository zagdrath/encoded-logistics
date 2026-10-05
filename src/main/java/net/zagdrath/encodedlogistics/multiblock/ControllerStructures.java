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
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.zagdrath.encodedlogistics.blockentity.ControlInterfaceBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlockEntity;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.machine.MachineBridge;
import net.zagdrath.encodedlogistics.machine.MachineBridges;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;
import net.zagdrath.encodedlogistics.midrange.TapeDriveBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.LaneResult;
import net.zagdrath.encodedlogistics.network.LaneSolver;
import net.zagdrath.encodedlogistics.network.ListedDevice;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.plc.PlcBlockEntity;
import net.zagdrath.encodedlogistics.network.NetworkDiscovery;
import net.zagdrath.encodedlogistics.network.NetworkGraph;
import net.zagdrath.encodedlogistics.network.NetworkLink;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkPart;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.network.RackNode;
import net.zagdrath.encodedlogistics.network.RackPartNode;
import net.zagdrath.encodedlogistics.part.CablePart;
import net.zagdrath.encodedlogistics.part.InventoryTapPart;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.TapeRecalls;
import net.zagdrath.encodedlogistics.rack.TapeSource;
import net.zagdrath.encodedlogistics.rack.TapeTier;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.rack.device.NetworkControllerDevice;
import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.DriveHolder;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.DriveView;
import net.zagdrath.encodedlogistics.storage.EnergyDrives;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.SharedView;
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

    // A structure is a group of Network Controller blocks, or (rack) a Server Rack holding rack Network Controllers: its
    // one member is the rack's master, or (midrange) a Midrange System or Integrated Midrange System, its network's
    // controller on its own: its one member is its master block.
    public record Structure(long id, List<BlockPos> members, ControllerFrame.Problem problem, boolean rack, boolean midrange) {
        public static final Codec<Structure> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Structure::id),
                BlockPos.CODEC.listOf().fieldOf("members").forGetter(Structure::members),
                Codec.STRING.xmap(ControllerStructures::problemByName, ControllerFrame.Problem::name).fieldOf("problem")
                        .forGetter(Structure::problem),
                Codec.BOOL.optionalFieldOf("rack", false).forGetter(Structure::rack),
                Codec.BOOL.optionalFieldOf("midrange", false).forGetter(Structure::midrange))
                .apply(i, Structure::new));

        public Structure(long id, List<BlockPos> members, ControllerFrame.Problem problem) {
            this(id, members, problem, false, false);
        }

        // Not controller blocks: a rack's or a Midrange System's.
        public boolean single() {
            return rack || midrange;
        }

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
    // Networks just solved again: their devices get any names they're missing at the end of the tick (ElclDevices).
    private final Set<NetworkRef> namesDue = new LinkedHashSet<>();
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
        for (NetworkRef network : List.copyOf(namesDue)) {
            ElclDevices.list(server, network);
        }
        namesDue.clear();
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
        } else {
            MachineBridges.setOnline(server, pos, online);
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
                    runtime.racks.forEach(index.racks::remove);
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
        RackBlockEntity rack = null;
        MidrangeSystemBlockEntity midrange = null;
        if (structure.midrange()) {
            BlockPos pos = structure.members().getFirst();
            if (!level.isLoaded(pos)) {
                runtime.online = Set.of();
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof MidrangeSystemBlockEntity found) || found.controllerStructure() != structure.id()) {
                dropStructure(level, index, structure.id());
                return;
            }
            midrange = found;
        } else if (structure.rack()) {
            BlockPos pos = structure.members().getFirst();
            if (!level.isLoaded(pos)) {
                runtime.online = Set.of();
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof RackBlockEntity found) || found.controllerStructure() != structure.id() || found.controllers().isEmpty()) {
                dropStructure(level, index, structure.id());
                return;
            }
            rack = found;
        } else {
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
        }

        NetworkStatus status = structure.shapeStatus();
        if (status != null) {
            runtime.discovered = null;
            runtime.lanes = null;
            runtime.dirty = true;
            setNetworkNodes(level, index, structure.id(), runtime, null);
        } else if (runtime.dirty || runtime.discovered == null || runtime.lanes == null) {
            int lanesPerFace = Config.LANES_PER_CONTROLLER_FACE.getAsInt();
            runtime.discovered = rack != null ? NetworkDiscovery.discoverRack(level, rack.getBlockPos(), lanesPerFace)
                    : midrange != null ? NetworkDiscovery.discoverRack(level, midrange.getBlockPos(), lanesPerFace)
                    : NetworkDiscovery.discover(level, structure.id(), structure.members(), lanesPerFace);
            runtime.lanes = LaneSolver.solve(runtime.discovered.graph(), Config.ADHOC_MAX_DEVICES.getAsInt());
            runtime.dirty = false;
            // Racks with controllers paired up: the one with the lowest structure id runs the network.
            runtime.lead = rack != null ? lead(runtime.discovered.graph(), structure.id()) : structure.id();
            boolean runs = runtime.lanes.status() != NetworkStatus.CONFLICT && runtime.lead == structure.id();
            setNetworkNodes(level, index, structure.id(), runtime, runs ? runtime.discovered : null);
            if (runs) {
                for (NodePos pos : runtime.racks) {
                    if (blockEntity(level.getServer(), pos) instanceof RackBlockEntity onNetwork) {
                        onNetwork.setRackLanes(runtime.lanes.rack(pos));
                    }
                }
            }
            runtime.deviceCount = countDevices(runtime.discovered.graph());
            if (runs) {
                namesDue.add(new NetworkRef(level.dimension(), structure.id()));
            }
        }
        boolean conflict = status == null && runtime.lanes != null && runtime.lanes.status() == NetworkStatus.CONFLICT;
        if (status == null && !conflict && runtime.lead != structure.id()) {
            // Its partner's structure runs the network (and both controllers).
            Runtime lead = runtimes.get(runtime.lead);
            runtime.status = lead != null ? lead.status : NetworkStatus.NO_POWER;
            runtime.online = Set.of();
            return;
        }

        // The buffers that are the network's energy: the controller blocks, or the working rack controller.
        List<ControllerBuffer> buffers = new ArrayList<>(blocks);
        if (midrange != null) {
            buffers.add(midrange);
        }
        runtime.working = null;
        runtime.standbys = List.of();
        if (rack != null && status == null && !conflict) {
            NetworkControllerDevice working = runPair(level, runtime);
            if (working != null) {
                runtime.working = working;
                buffers.add(working);
            }
            runtime.standbys.forEach(NetworkControllerDevice::takeReceived);
        }
        int received = 0;
        long stored = 0, capacity = 0;
        for (ControllerBuffer buffer : buffers) {
            received += buffer.takeReceived();
            stored += buffer.getEnergy();
            capacity += buffer.getCapacity();
        }
        List<CapacitorBankBlockEntity> banks = banks(level.getServer(), runtime);
        for (CapacitorBankBlockEntity bank : banks) {
            received += bank.lastInput();
            stored += bank.getStored();
            capacity += bank.getCapacity();
        }
        // Energy Storage Drives in the network's drive holders: pool capacity, drained and filled with the banks.
        List<EnergyDrives.Cell> driveCells = driveCells(level.getServer(), runtime, new NetworkRef(level.dimension(), structure.id()));
        long driveCapacity = 0;
        for (EnergyDrives.Cell cell : driveCells) {
            stored += cell.getStored();
            driveCapacity += cell.getCapacity();
        }
        capacity += driveCapacity;
        List<EnergyCell> cells = new ArrayList<>(banks);
        cells.addAll(driveCells);
        runtime.received[runtime.receivedIndex] = received;
        runtime.receivedIndex = (runtime.receivedIndex + 1) % GENERATION_WINDOW;
        long window = 0;
        for (int amount : runtime.received) {
            window += amount;
        }

        double usage = 0;
        if (conflict) {
            status = NetworkStatus.CONFLICT;
        } else if (status == null) {
            // A rack controller's own drain is part of its rack's.
            usage = rack == null ? Config.CONTROLLER_DRAIN.getAsDouble() * blocks.size() : 0;
            for (NetworkNode node : runtime.discovered.graph().nodes()) {
                if (!node.isController()) {
                    usage += node.passiveDrain();
                }
            }
            runtime.drainCarry += usage;
            int toDrain = (int) runtime.drainCarry;
            runtime.drainCarry -= toDrain;
            // UPSes cover a shortfall in what came in this tick before the buffers are touched (UpsDevice). Buffers
            // within a tick's drain of full can't take anything in, so nothing coming in then isn't a failure: the
            // supply counts as covering the drain (if it has really failed, the buffers drop and the next tick
            // says so). While they aren't covering, they recharge from the top half of the buffers, which the
            // supply refills; the network always keeps the lower half.
            List<UpsDevice> upses = upses(level.getServer(), runtime);
            int supply = capacity - stored <= toDrain ? Math.max(received, toDrain) : received;
            int fromUps = upses.isEmpty() ? 0 : UpsDevice.cover(upses, toDrain, supply, level.getGameTime());
            stored -= drain(cells, buffers, toDrain - fromUps);
            if (!upses.isEmpty() && fromUps == 0) {
                long spare = Math.max(0, stored - capacity / 2);
                int charge = drain(cells, buffers, UpsDevice.wantedCharge(upses, (int) Math.min(Integer.MAX_VALUE, spare)));
                UpsDevice.charge(upses, charge);
                stored -= charge;
            }
            status = stored > 0 || fromUps > 0 ? NetworkStatus.ONLINE : NetworkStatus.NO_POWER;
            if (status == NetworkStatus.NO_POWER) {
                runtime.drainCarry = 0;
            } else if (runtime.failoverTarget != null) {
                status = NetworkStatus.FAILOVER;
            }
        }
        runtime.status = status;
        Set<NodePos> online = new HashSet<>();
        if ((status == NetworkStatus.ONLINE || status == NetworkStatus.FAILOVER) && runtime.lanes != null) {
            for (NodePos device : runtime.devices) {
                if (runtime.lanes.hasLane(device)) {
                    online.add(device);
                }
            }
        }
        // A Midrange System that runs its network is on it, as a device is.
        if (midrange != null && (status == NetworkStatus.ONLINE || status == NetworkStatus.FAILOVER)) {
            online.add(NetworkGraph.at(level.dimension(), midrange.getBlockPos()));
        }
        runtime.online = online;
        runtime.stored = stored;
        runtime.capacity = capacity;
        runtime.usage = usage;
        drivesTicked(runtime, driveCells, driveCapacity);
        runtime.generation = (double) window / GENERATION_WINDOW;

        if (rack != null) {
            showControllers(level.getServer(), runtime, conflict);
            return;
        }
        if (midrange != null) {
            midrange.setNetworkStatus(status);
            return;
        }
        applyStates(level, structure, status);

        int signal = capacity <= 0 || stored <= 0 ? 0 : 1 + (int) (stored * 14 / capacity);
        if (signal != runtime.comparator) {
            runtime.comparator = signal;
            for (BlockPos pos : structure.members()) {
                level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
            }
        }
    }

    // --- Rack Network Controllers ---

    // A rack that has rack Network Controllers gets a structure of its own: its id, kept by the rack.
    public long addRack(BlockPos rack) {
        long id = nextId++;
        structures.put(id, new Structure(id, List.of(rack.immutable()), ControllerFrame.Problem.NONE, true, false));
        runtimes.computeIfAbsent(id, key -> new Runtime()).dirty = true;
        topologyChanged = true;
        setDirty();
        return id;
    }

    // A Midrange System's structure: its network's controller on its own (MidrangeSystemBlockEntity keeps the id).
    public long addMidrange(BlockPos master) {
        long id = nextId++;
        structures.put(id, new Structure(id, List.of(master.immutable()), ControllerFrame.Problem.NONE, false, true));
        runtimes.computeIfAbsent(id, key -> new Runtime()).dirty = true;
        topologyChanged = true;
        setDirty();
        return id;
    }

    // Its last controller went, or the rack did (or the Midrange System).
    public void removeRack(ServerLevel level, long id) {
        dropStructure(level, NetworkIndex.get(level.getServer()), id);
    }

    private void dropStructure(ServerLevel level, NetworkIndex index, long id) {
        structures.remove(id);
        Runtime runtime = runtimes.remove(id);
        if (runtime != null) {
            runtime.online.forEach(pos -> setDeviceOnline(level.getServer(), pos, false));
            NetworkRef ref = new NetworkRef(level.dimension(), id);
            runtime.members.forEach(pos -> index.members.remove(pos, ref));
            runtime.racks.forEach(index.racks::remove);
        }
        topologyChanged = true;
        setDirty();
    }

    // The lowest controller structure among the racks with controllers on a graph (own when there's none).
    private static long lead(NetworkGraph graph, long own) {
        long lead = Long.MAX_VALUE;
        for (NetworkNode node : graph.nodes()) {
            if (node instanceof RackNode rack && !rack.controllers().isEmpty() && rack.structure() > 0) {
                lead = Math.min(lead, rack.structure());
            }
        }
        return lead == Long.MAX_VALUE ? own : lead;
    }

    // Devices on a network, as its controllers' popups count them: a rack's each count.
    private static int countDevices(NetworkGraph graph) {
        int count = 0;
        for (NetworkNode node : graph.nodes()) {
            if (node instanceof RackNode) {
                count += node.parts().size();
            } else if (node.isDevice()) {
                count += Math.max(1, node.parts().size());
            }
        }
        return count;
    }

    // The rack Network Controllers on a network, by rack (NetworkGraph.ORDER) then unit.
    private static List<NetworkControllerDevice> rackControllers(MinecraftServer server, NetworkGraph graph) {
        List<Map.Entry<NodePos, NetworkNode>> entries = new ArrayList<>(graph.entries().entrySet());
        entries.sort(Map.Entry.comparingByKey(NetworkGraph.ORDER));
        List<NetworkControllerDevice> controllers = new ArrayList<>();
        for (Map.Entry<NodePos, NetworkNode> entry : entries) {
            if (entry.getValue() instanceof RackNode node && !node.controllers().isEmpty()
                    && blockEntity(server, entry.getKey()) instanceof RackBlockEntity rack) {
                controllers.addAll(rack.controllers());
            }
        }
        return controllers;
    }

    // Decides which controller runs the network this tick: the active one, or the standby taking over. Which is active is
    // kept on the controllers; when the active one can't run (faulted, gone, or the network out of power) and the standby
    // can, or Switch over was asked for, the standby takes over after FAILOVER_TICKS. The one handing over stays as
    // standby; nothing fails back on its own.
    private static @Nullable NetworkControllerDevice runPair(ServerLevel level, Runtime runtime) {
        List<NetworkControllerDevice> all = rackControllers(level.getServer(), runtime.discovered.graph());
        runtime.pair = all;
        NetworkControllerDevice active = null;
        boolean requested = false;
        for (NetworkControllerDevice controller : all) {
            requested |= controller.takeSwitchRequest();
            if (controller.isActive()) {
                if (active == null) {
                    active = controller;
                } else {
                    controller.setActive(false);
                }
            }
        }
        NetworkControllerDevice target = runtime.failoverTarget;
        if (target != null && (!all.contains(target) || !target.usable())) {
            runtime.failoverTarget = target = null;
        }
        if (target != null && --runtime.failoverTicks <= 0) {
            long now = level.getServer().overworld().getOverworldClockTime();
            for (NetworkControllerDevice controller : all) {
                controller.setActive(controller == target);
                controller.setLastFailover(now);
            }
            active = target;
            runtime.failoverTarget = target = null;
        }
        if (target == null) {
            NetworkControllerDevice current = active;
            NetworkControllerDevice standby = all.stream().filter(c -> c != current && c.usable() && c.getEnergy() > 0).findFirst().orElse(null);
            boolean lost = active == null ? runtime.hadActive : !active.usable() || runtime.status == NetworkStatus.NO_POWER;
            boolean asked = requested && active != null && active.usable() && standby != null;
            if (standby != null && (lost || asked)) {
                runtime.failoverTarget = target = standby;
                runtime.failoverTicks = NetworkControllerDevice.FAILOVER_TICKS;
            } else if (active == null && !all.isEmpty()) {
                active = all.stream().filter(c -> c.usable() && c.getEnergy() > 0).findFirst()
                        .orElse(all.stream().filter(NetworkControllerDevice::usable).findFirst().orElse(all.getFirst()));
                for (NetworkControllerDevice controller : all) {
                    controller.setActive(controller == active);
                }
            }
        }
        runtime.hadActive = active != null || target != null;
        runtime.active = active;
        NetworkControllerDevice working = target != null ? target : active;
        List<NetworkControllerDevice> standbys = new ArrayList<>();
        for (NetworkControllerDevice controller : all) {
            if (controller != working && controller.usable()) {
                standbys.add(controller);
            }
        }
        runtime.standbys = standbys;
        return working;
    }

    // Tells each rack controller on the network how it stands.
    private static void showControllers(MinecraftServer server, Runtime runtime, boolean conflict) {
        if (runtime.discovered == null) {
            return;
        }
        NetworkGraph graph = runtime.discovered.graph();
        if (conflict) {
            List<NetworkControllerDevice> all = rackControllers(server, graph);
            NodePos block = null;
            for (Map.Entry<NodePos, NetworkNode> entry : graph.entries().entrySet()) {
                if (entry.getValue().isController() && (block == null || NetworkGraph.ORDER.compare(entry.getKey(), block) < 0)) {
                    block = entry.getKey();
                }
            }
            for (NetworkControllerDevice controller : all) {
                Component with = block != null ? Component.empty().append(ModItems.NETWORK_CONTROLLER.get().getName(ModItems.NETWORK_CONTROLLER.get().getDefaultInstance()))
                        .append(" " + block.pos().getX() + "," + block.pos().getY() + "," + block.pos().getZ())
                        : all.stream().filter(other -> other != controller).findFirst().map(other -> describe(other, controller, true)).orElse(Component.empty());
                controller.view(NetworkControllerDevice.Shown.CONFLICT, with, 0, 0, 0, 0, 0, 0);
            }
            return;
        }
        LaneResult lanes = runtime.lanes;
        for (NetworkControllerDevice controller : runtime.pair) {
            NetworkControllerDevice.Shown shown;
            if (!controller.usable()) {
                shown = NetworkControllerDevice.Shown.FAULT;
            } else if (runtime.failoverTarget != null) {
                shown = controller == runtime.failoverTarget ? NetworkControllerDevice.Shown.TAKING_OVER : NetworkControllerDevice.Shown.HANDING_OVER;
            } else if (controller == runtime.active) {
                shown = runtime.pair.size() > 1 ? NetworkControllerDevice.Shown.ACTIVE_PAIR : NetworkControllerDevice.Shown.ACTIVE;
            } else {
                shown = NetworkControllerDevice.Shown.STANDBY;
            }
            Component partner = runtime.pair.stream().filter(other -> other != controller).findFirst().map(other -> describe(other, controller, false))
                    .orElse(Component.empty());
            controller.view(shown, partner, lanes != null ? lanes.used() : 0, lanes != null ? lanes.capacity() : 0, runtime.deviceCount, runtime.stored,
                    runtime.capacity, runtime.usage);
        }
    }

    // Another controller as one names it: "U13" in its own rack, else where its rack is too (with its name, for a conflict).
    private static Component describe(NetworkControllerDevice other, NetworkControllerDevice from, boolean named) {
        RackBlockEntity rack = other.rack();
        String unit = "U" + other.u();
        MutableComponent text = named ? other.name().copy().append(" ") : Component.empty();
        if (rack == null || rack == from.rack()) {
            return text.append(unit);
        }
        BlockPos pos = rack.getBlockPos();
        return text.append(pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + unit);
    }

    // Takes up to amount FE out of a network's banks and Energy Storage Drives, then its controllers; returns what it got.
    private static int drain(List<? extends EnergyCell> banks, List<ControllerBuffer> controllers, int amount) {
        int left = amount;
        for (EnergyCell bank : banks) {
            if (left <= 0) {
                break;
            }
            left -= bank.drain(left);
        }
        for (ControllerBuffer controller : controllers) {
            if (left <= 0) {
                break;
            }
            left -= controller.drain(left);
        }
        return amount - Math.max(0, left);
    }

    // The UPSes in the network's racks that have their lanes, whether or not the network has power: they're what keeps
    // it up.
    private static List<UpsDevice> upses(MinecraftServer server, Runtime runtime) {
        List<UpsDevice> upses = new ArrayList<>();
        for (NodePos pos : runtime.racks) {
            if (runtime.lanes != null && runtime.lanes.hasLane(pos) && blockEntity(server, pos) instanceof RackBlockEntity rack) {
                for (RackDevice device : rack.devices()) {
                    if (device instanceof UpsDevice ups && rack.hasLanes(ups)) {
                        upses.add(ups);
                    }
                }
            }
        }
        return upses;
    }

    // Records what's on this structure's network, in the index and by kind: its Capacitor Banks (its energy), its
    // devices, Drive Bays, part hosts, autocrafting blocks and Relay Antennas. Nothing when it has no working network.
    private void setNetworkNodes(ServerLevel level, NetworkIndex index, long id, Runtime runtime, NetworkDiscovery.@Nullable Discovered discovered) {
        NetworkRef ref = new NetworkRef(level.dimension(), id);
        runtime.members.forEach(pos -> index.members.remove(pos, ref));
        runtime.racks.forEach(index.racks::remove);
        List<NodePos> members = new ArrayList<>(), banks = new ArrayList<>(), devices = new ArrayList<>(), driveBays = new ArrayList<>(),
                partHosts = new ArrayList<>(), providers = new ArrayList<>(), schedulers = new ArrayList<>(), relays = new ArrayList<>(),
                racks = new ArrayList<>();
        if (discovered != null) {
            discovered.graph().entries().forEach((pos, node) -> {
                if (node.isController()) {
                    return;
                }
                members.add(pos);
                index.members.put(pos, ref);
                if (node.isDevice()) {
                    devices.add(pos);
                }
                BlockEntity blockEntity = blockEntity(level.getServer(), pos);
                if (blockEntity instanceof CapacitorBankBlockEntity) {
                    banks.add(pos);
                } else if (blockEntity instanceof DriveHolder || blockEntity instanceof TapeDriveBlockEntity) {
                    driveBays.add(pos);
                } else if (blockEntity instanceof CraftingProvider) {
                    providers.add(pos);
                } else if (blockEntity instanceof SchedulerCoreBlockEntity && node.laneCost() > 0) {
                    schedulers.add(pos);
                } else if (blockEntity instanceof RelayAntennaBlockEntity) {
                    relays.add(pos);
                } else if (blockEntity instanceof RackBlockEntity) {
                    racks.add(pos);
                } else if (blockEntity instanceof CableBlockEntity host && host.getAttachments().hasParts()) {
                    partHosts.add(pos);
                }
            });
        }
        for (List<NodePos> list : List.of(banks, driveBays, partHosts, providers, schedulers, relays, racks)) {
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
        runtime.racks = List.copyOf(racks);
        index.racks.addAll(racks);
    }

    // A network's Capacitor Banks, then the Energy Storage Drives in its drive holders: its energy besides the controllers.
    private static List<EnergyCell> cells(MinecraftServer server, Owner owner) {
        List<EnergyCell> cells = new ArrayList<>(banks(server, owner.runtime));
        cells.addAll(driveCells(server, owner.runtime, owner.ref));
        return cells;
    }

    // The Energy Storage Drives in a network's Drive Bays and Disk Drives (whether or not those have their lanes: like
    // banks, they keep the network powered) and in the rack storage devices serving it.
    private static List<EnergyDrives.Cell> driveCells(MinecraftServer server, Runtime runtime, NetworkRef ref) {
        List<EnergyDrives.Cell> cells = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (NodePos pos : runtime.driveBays) {
            if (blockEntity(server, pos) instanceof DriveHolder bay) {
                for (int slot = 0; slot < bay.driveSlots(); slot++) {
                    ItemStack stack = bay.drive(slot);
                    if (stack != null && EnergyDrives.is(stack) && seen.add(StorageDriveItem.id(stack))) {
                        int changed = slot;
                        cells.add(new EnergyDrives.Cell(stack, () -> bay.driveChanged(changed)));
                    }
                }
            }
        }
        for (RackDevice device : rackDevicesServing(server, ref)) {
            if (device instanceof StorageDevice storage) {
                cells.addAll(storage.energyCells(seen));
            }
        }
        return cells;
    }

    // After a tick's energy: the drives' share of it, and which drives are charging (their lights).
    private static void drivesTicked(Runtime runtime, List<EnergyDrives.Cell> cells, long capacity) {
        Map<UUID, Long> charge = new HashMap<>();
        long stored = 0;
        for (EnergyDrives.Cell cell : cells) {
            long now = cell.getStored();
            stored += now;
            UUID id = cell.id();
            if (id != null) {
                Long before = runtime.driveCharge.get(id);
                charge.put(id, now);
                cell.charging(before != null && now > before);
            }
        }
        for (UUID gone : runtime.driveCharge.keySet()) {
            if (!charge.containsKey(gone)) {
                EnergyDrives.forget(gone);
            }
        }
        runtime.driveCharge = charge;
        runtime.driveStored = stored;
        runtime.driveCapacity = capacity;
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
    public static @Nullable BlockEntity blockEntity(MinecraftServer server, NodePos pos) {
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

    // Whether a network is loaded and running: its structure exists, its controllers' chunk is loaded and it has been
    // worked out (scripts' schedule entries and triggers only fire then).
    public static boolean loaded(MinecraftServer server, @Nullable NetworkRef ref) {
        Owner owner = owner(server, ref);
        return owner != null && owner.runtime.discovered != null && !owner.structure.members().isEmpty()
                && owner.home().isLoaded(owner.structure.members().getFirst());
    }

    // Whether a network (its structure) still exists.
    public static boolean exists(MinecraftServer server, @Nullable NetworkRef ref) {
        return owner(server, ref) != null;
    }

    // The network a controller structure's devices are on: its own, or (a rack controller paired with another) the one
    // its partner's structure runs.
    public static NetworkRef networkOfStructure(MinecraftServer server, NetworkRef structure) {
        ServerLevel home = server.getLevel(structure.dimension());
        Runtime runtime = home != null ? get(home).runtimes.get(structure.id()) : null;
        return runtime != null && runtime.lead > 0 && runtime.lead != structure.id() ? new NetworkRef(structure.dimension(), runtime.lead) : structure;
    }

    // Goes up whenever any network's topology changes (NetworkIndex).
    public static int generation(MinecraftServer server) {
        return NetworkIndex.get(server).generation;
    }

    // A network's status as of the last tick (NO_POWER for an unknown one).
    public static NetworkStatus statusOf(MinecraftServer server, @Nullable NetworkRef ref) {
        Owner owner = owner(server, ref);
        return owner != null ? owner.runtime.status : NetworkStatus.NO_POWER;
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
        if (owner == null || !owner.runtime.online.contains(NetworkGraph.at(level.dimension(), device))
                || owner.runtime.status == NetworkStatus.FAILOVER) {
            return null;
        }
        return storage(level.getServer(), owner);
    }

    // The storage of a network as a whole (what a Router moves items between), or null while it's down.
    public static @Nullable NetworkStorage storageOf(MinecraftServer server, @Nullable NetworkRef network) {
        Owner owner = owner(server, network);
        return owner != null && owner.runtime.status == NetworkStatus.ONLINE ? storage(server, owner) : null;
    }

    private static NetworkStorage storage(MinecraftServer server, Owner owner) {
        return storage(server, owner, null);
    }

    // crafting: null for the network's own storage alone; false with everything Share routes share into it; true with
    // only the shares that allow crafting.
    private static NetworkStorage storage(MinecraftServer server, Owner owner, @Nullable Boolean crafting) {
        List<StorageView> views = new ArrayList<>();
        DriveStorage drives = DriveStorage.get(server);
        // A copied drive (creative pick-block) shares its id, and so its contents, with the original: each id counts once.
        // So does a drive reached both here and through a share.
        Set<UUID> seen = new HashSet<>();
        List<TapeSource> libraries = new ArrayList<>();
        views(server, owner, seen, views, libraries);
        if (crafting != null) {
            // Only the source's own storage, not what's shared into it: shares don't chain.
            for (ItemRouting.ShareLink link : shareLinks(server)) {
                Owner source = owner(server, link.from());
                if (!link.into().equals(owner.ref) || crafting && !link.route().crafting() || source == null || source.runtime.status != NetworkStatus.ONLINE) {
                    continue;
                }
                List<StorageView> theirs = new ArrayList<>();
                views(server, source, seen, theirs, new ArrayList<>());
                for (StorageView view : theirs) {
                    views.add(new SharedView(view, link.route()::matches, link.route().readWrite(), link.fromName()));
                }
            }
        }
        Runtime runtime = owner.runtime;
        // Gateways' runs waiting for outputs claim them as they come in, whatever way they come.
        NetworkStorage.Claim claim = (key, amount, simulate) -> {
            long taken = 0;
            for (NodePos pos : runtime.providers) {
                if (taken >= amount) {
                    break;
                }
                if (runtime.online.contains(pos) && blockEntity(server, pos) instanceof GatewayBlockEntity gateway) {
                    taken += gateway.claim(key, amount - taken, simulate);
                }
            }
            return taken;
        };
        return new NetworkStorage(views, moved -> runtime.itemsMoved += moved, new TapeTier(libraries, drives, runtime.recalls), claim);
    }

    // Every active Share on any network: the online L3 Switches' and Routers' Share routes.
    private static List<ItemRouting.ShareLink> shareLinks(MinecraftServer server) {
        List<ItemRouting.ShareLink> links = new ArrayList<>();
        for (RackBlockEntity rack : allRacks(server)) {
            for (RackDevice device : rack.devices()) {
                if (device.isOnline() && device instanceof ItemRouting.ShareSource source) {
                    links.addAll(source.shareLinks(server));
                }
            }
        }
        return links;
    }

    // A network's own storage: its online Drive Bays' and Disk Drives' drives, its online Inventory Taps' inventories, its
    // rack storage devices; and its Tape Libraries and Tape Drives.
    private static void views(MinecraftServer server, Owner owner, Set<UUID> seen, List<StorageView> views, List<TapeSource> libraries) {
        DriveStorage drives = DriveStorage.get(server);
        for (NodePos pos : owner.runtime.driveBays) {
            if (owner.runtime.online.contains(pos) && blockEntity(server, pos) instanceof TapeDriveBlockEntity tape) {
                libraries.add(tape);
            }
            if (owner.runtime.online.contains(pos) && blockEntity(server, pos) instanceof DriveHolder bay) {
                for (int slot = 0; slot < bay.driveSlots(); slot++) {
                    ItemStack stack = bay.drive(slot);
                    if (stack != null && stack.getItem() instanceof StorageDriveItem drive && drive.getType() != ResourceType.ENERGY
                            && seen.add(StorageDriveItem.id(stack))) {
                        views.add(new DriveView(drives, bay, slot, StorageDriveItem.id(stack), drive.getTier(), drive.getType()));
                    }
                }
            }
        }
        for (NodePos pos : owner.runtime.partHosts) {
            if (owner.runtime.online.contains(pos) && blockEntity(server, pos) instanceof CableBlockEntity host
                    && host.getLevel() instanceof ServerLevel hostLevel) {
                for (CablePart part : host.parts()) {
                    if (part instanceof InventoryTapPart tap && tap.enabled()) {
                        StorageView view = tap.view(hostLevel);
                        if (view != null) {
                            views.add(view);
                        }
                    }
                }
            }
        }
        for (RackDevice device : rackDevicesServing(server, owner.ref)) {
            if (device instanceof StorageDevice storageDevice) {
                views.addAll(storageDevice.views(server, seen));
            } else if (device instanceof TapeLibraryDevice library && library.isOnline()) {
                libraries.add(library);
            }
        }
    }

    // A network's storage with what Share routes share into it, for terminals (crafting false) and crafting (true: only
    // shares that allow it). Its own storage comes first; null while it's down.
    public static @Nullable NetworkStorage sharedStorageOf(MinecraftServer server, @Nullable NetworkRef network, boolean crafting) {
        Owner owner = owner(server, network);
        return owner != null && owner.runtime.status == NetworkStatus.ONLINE ? storage(server, owner, crafting) : null;
    }

    // The same for a device at pos, while it's online.
    public @Nullable NetworkStorage sharedStorageAt(ServerLevel level, BlockPos device, boolean crafting) {
        Owner owner = owner(level, device);
        if (owner == null || !owner.runtime.online.contains(NetworkGraph.at(level.dimension(), device))
                || owner.runtime.status == NetworkStatus.FAILOVER) {
            return null;
        }
        return storage(level.getServer(), owner, crafting);
    }

    // A network's tape recall queue (TapeRecalls), or null for an unknown network.
    public static @Nullable TapeRecalls recalls(MinecraftServer server, @Nullable NetworkRef network) {
        Owner owner = owner(server, network);
        return owner != null ? owner.runtime.recalls : null;
    }

    // --- Racks ---

    // The racks on a network (powered or not), in a stable order.
    public static List<RackBlockEntity> racks(MinecraftServer server, @Nullable NetworkRef network) {
        Owner owner = owner(server, network);
        List<RackBlockEntity> racks = new ArrayList<>();
        if (owner != null) {
            for (NodePos pos : owner.runtime.racks) {
                if (blockEntity(server, pos) instanceof RackBlockEntity rack) {
                    racks.add(rack);
                }
            }
        }
        return racks;
    }

    // Every loaded Server Rack on any network, in a stable order.
    public static List<RackBlockEntity> allRacks(MinecraftServer server) {
        List<NodePos> positions = new ArrayList<>(NetworkIndex.get(server).racks);
        positions.sort(NetworkGraph.ORDER);
        List<RackBlockEntity> racks = new ArrayList<>();
        for (NodePos pos : positions) {
            if (blockEntity(server, pos) instanceof RackBlockEntity rack) {
                racks.add(rack);
            }
        }
        return racks;
    }

    // The rack devices serving a network: in racks on it, or on a segment of another rack set to it (online or not),
    // by rack then unit.
    public static List<RackDevice> rackDevicesServing(MinecraftServer server, @Nullable NetworkRef network) {
        List<RackDevice> devices = new ArrayList<>();
        if (network != null) {
            for (RackBlockEntity rack : allRacks(server)) {
                devices.addAll(rack.devicesServing(network));
            }
        }
        return devices;
    }

    // The Firewall a network uses: the first serving it (by rack position, then unit), or null.
    public static @Nullable FirewallDevice firewall(MinecraftServer server, @Nullable NetworkRef network) {
        for (RackDevice device : rackDevicesServing(server, network)) {
            if (device instanceof FirewallDevice firewall) {
                return firewall;
            }
        }
        return null;
    }

    public static int firewalls(MinecraftServer server, @Nullable NetworkRef network) {
        return (int) rackDevicesServing(server, network).stream().filter(device -> device instanceof FirewallDevice).count();
    }

    // --- Autocrafting ---

    // The online Fabricators and Gateways on the network a device at pos is on (empty while it's offline).
    public List<CraftingProvider> providersAt(ServerLevel level, BlockPos device) {
        Owner owner = owner(level, device);
        return owner != null && owner.runtime.online.contains(NetworkGraph.at(level.dimension(), device)) ? providersOf(level.getServer(), owner.ref)
                : new ArrayList<>();
    }

    // The online Fabricators, Gateways and Fabrication Servers of a network.
    public static List<CraftingProvider> providersOf(MinecraftServer server, @Nullable NetworkRef network) {
        Owner owner = owner(server, network);
        List<CraftingProvider> providers = new ArrayList<>();
        if (owner == null || owner.runtime.status != NetworkStatus.ONLINE) {
            return providers;
        }
        for (NodePos pos : owner.runtime.providers) {
            if (owner.runtime.online.contains(pos) && blockEntity(server, pos) instanceof CraftingProvider provider) {
                providers.add(provider);
            }
        }
        for (RackDevice device : rackDevicesServing(server, network)) {
            if (device.isOnline() && device instanceof CraftingProvider provider) {
                providers.add(provider);
            }
        }
        return providers;
    }

    // The online, formed Schedulers on the network a device at pos is on, then its racks' Rack Schedulers.
    public List<JobHost> schedulersAt(ServerLevel level, BlockPos device) {
        Owner owner = owner(level, device);
        if (owner == null || !owner.runtime.online.contains(NetworkGraph.at(level.dimension(), device))) {
            return new ArrayList<>();
        }
        return schedulersOf(level.getServer(), owner.ref);
    }

    // The online, formed Schedulers of a network, then the Rack Schedulers serving it, then its running Midrange Systems.
    public static List<JobHost> schedulersOf(MinecraftServer server, @Nullable NetworkRef network) {
        Owner owner = owner(server, network);
        List<JobHost> schedulers = new ArrayList<>();
        if (owner == null || owner.runtime.status != NetworkStatus.ONLINE) {
            return schedulers;
        }
        for (NodePos pos : owner.runtime.schedulers) {
            if (owner.runtime.online.contains(pos) && blockEntity(server, pos) instanceof SchedulerCoreBlockEntity core && core.formed()) {
                schedulers.add(core);
            }
        }
        for (RackBlockEntity rack : allRacks(server)) {
            if (rack.scheduler().active() && owner.ref.equals(rack.scheduler().network())) {
                schedulers.add(rack.scheduler());
            }
        }
        for (MidrangeSystemBlockEntity midrange : onNetwork(server, network, MidrangeSystemBlockEntity.class, true)) {
            if (midrange.takesJobs()) {
                schedulers.add(midrange);
            }
        }
        return schedulers;
    }

    // A crafting job finished on the network a job host at pos is on (counted for Monitoring Servers).
    public static void jobFinished(ServerLevel level, BlockPos host) {
        jobFinished(level.getServer(), networkOf(level, host));
    }

    public static void jobFinished(MinecraftServer server, @Nullable NetworkRef network) {
        Owner owner = owner(server, network);
        if (owner != null) {
            owner.runtime.jobsDone++;
        }
    }

    // What a Monitoring Server samples: cumulative items moved in or out of storage and jobs finished, and the drain
    // and lane use as of the last tick.
    public record NetworkStats(boolean online, long itemsMoved, double usage, int lanesUsed, int laneCapacity, long jobsDone) {}

    public static @Nullable NetworkStats stats(MinecraftServer server, @Nullable NetworkRef network) {
        Owner owner = owner(server, network);
        if (owner == null) {
            return null;
        }
        Runtime runtime = owner.runtime;
        LaneResult lanes = runtime.lanes;
        return new NetworkStats(runtime.status == NetworkStatus.ONLINE, runtime.itemsMoved, runtime.usage, lanes != null ? lanes.used() : 0,
                lanes != null ? lanes.capacity() : 0, runtime.jobsDone);
    }


    // --- What the Terminal Desk lists ---

    // A network's snapshot (as its controller's screen shows it), or the empty one.
    public static NetworkSnapshot snapshotOf(MinecraftServer server, @Nullable NetworkRef network) {
        Owner owner = owner(server, network);
        return owner != null ? get(owner.home).snapshot(owner.ref.id()) : NetworkSnapshot.EMPTY;
    }

    // A network's Drive Bays (loaded), online or not.
    public static List<DriveBayBlockEntity> driveBays(MinecraftServer server, @Nullable NetworkRef network) {
        Owner owner = owner(server, network);
        List<DriveBayBlockEntity> bays = new ArrayList<>();
        if (owner != null) {
            for (NodePos pos : owner.runtime.driveBays) {
                if (blockEntity(server, pos) instanceof DriveBayBlockEntity bay) {
                    bays.add(bay);
                }
            }
        }
        return bays;
    }

    // A job's number on a network (given the first time it's asked for); 0 for an unknown network.
    public static int jobNumber(MinecraftServer server, @Nullable NetworkRef network, UUID job) {
        Owner owner = owner(server, network);
        if (owner == null) {
            return 0;
        }
        Runtime runtime = owner.runtime;
        // A number the history still has (from before a restart) is skipped, so C0042 stays one job.
        return runtime.jobNumbers.computeIfAbsent(job, id -> {
            int number = runtime.nextJob;
            for (int tries = 0; tries < 9_999 && network != null && CraftLog.numberUsed(server, network, number); tries++) {
                number = number % 9_999 + 1;
            }
            runtime.nextJob = number % 9_999 + 1;
            return number;
        });
    }

    public static @Nullable UUID jobByNumber(MinecraftServer server, @Nullable NetworkRef network, int number) {
        Owner owner = owner(server, network);
        if (owner == null) {
            return null;
        }
        for (Map.Entry<UUID, Integer> entry : owner.runtime.jobNumbers.entrySet()) {
            if (entry.getValue() == number) {
                return entry.getKey();
            }
        }
        return null;
    }

    // One device on a network as the Terminal Desk lists it: its kind ("Controller", "Drive Bay", "Rack", "Part",
    // "Terminal", "Device"; a rack's devices are listed under it, depth 1), its name, where it is, the lanes it takes,
    // whether it's online and whether it's short of a lane.
    public record DeviceRow(String type, Component name, NodePos pos, int lanes, boolean online, boolean laneMissing, @Nullable RackDevice rackDevice,
            int depth) {}

    // The network's topology as the Terminal Desk lists it: each Server Rack (depth 0) with the devices in it under it
    // (depth 1, top unit first; a rack's Network Controllers among them), then everything else on the network beside
    // the racks (depth 0): a controller structure, Drive Bays, terminals, parts on cables, other devices. Each device
    // once; cables and a rack's other blocks aren't listed.
    public static List<DeviceRow> deviceRows(MinecraftServer server, @Nullable NetworkRef network) {
        Owner owner = owner(server, network);
        List<DeviceRow> rows = new ArrayList<>();
        if (owner == null || owner.runtime.discovered == null) {
            return rows;
        }
        Runtime runtime = owner.runtime;
        boolean networkOnline = runtime.status == NetworkStatus.ONLINE || runtime.status == NetworkStatus.FAILOVER;
        List<DeviceRow> others = new ArrayList<>();
        if (!owner.structure.single() && !owner.structure.members().isEmpty()) {
            others.add(new DeviceRow("Controller", ModItems.NETWORK_CONTROLLER.get().getName(ModItems.NETWORK_CONTROLLER.get().getDefaultInstance()),
                    NetworkGraph.at(owner.ref.dimension(), owner.structure.members().getFirst()), 0, networkOnline, false, null, 0));
        }
        List<Map.Entry<NodePos, NetworkNode>> entries = new ArrayList<>(runtime.discovered.graph().entries().entrySet());
        entries.sort(Map.Entry.comparingByKey(NetworkGraph.ORDER));
        for (Map.Entry<NodePos, NetworkNode> entry : entries) {
            NodePos pos = entry.getKey();
            NetworkNode node = entry.getValue();
            if (node.isController() || node instanceof RackPartNode) {
                continue;
            }
            boolean online = runtime.online.contains(pos);
            boolean missing = networkOnline && node.isDevice() && runtime.lanes != null
                    && (!runtime.lanes.hasLane(pos) || runtime.lanes.rack(pos).shed());
            if (node instanceof RackNode && blockEntity(server, pos) instanceof RackBlockEntity rack) {
                rows.add(new DeviceRow("Rack", rack.getBlockState().getBlock().getName(), pos, rack.rackLanes().used(), online, missing, null, 0));
                List<RackDevice> devices = new ArrayList<>(rack.devices());
                devices.sort(Comparator.comparingInt(RackDevice::u).reversed());
                for (RackDevice device : devices) {
                    rows.add(new DeviceRow("Unit", device.name(), pos, device.laneCost(), device.isOnline(), false, device, 1));
                }
                continue;
            }
            for (NetworkPart part : node.parts()) {
                others.add(new DeviceRow("Part", part.item().getName(part.item().getDefaultInstance()), pos, 0, online, missing, null, 0));
            }
            // A machine with a Small Wireless Bridge on: listed by the machine's own name.
            MachineBridge bridge = MachineBridges.at(server, pos);
            if (bridge != null) {
                others.add(new DeviceRow("Machine", bridge.shown(), pos, node.laneCost(), online, missing, null, 0));
                continue;
            }
            Item item = runtime.discovered.items().get(pos);
            // A cable (lanes through it, none of its own) or a part host (its parts are listed instead); a Wireless Bridge,
            // with no lanes of its own either, is listed all the same, as is a ListedDevice (a Midrange peripheral).
            boolean listed = blockEntity(server, pos) instanceof ListedDevice;
            if (item == null || item == Items.AIR || node.laneCost() <= 0 && item != ModItems.WIRELESS_BRIDGE.get() && !listed) {
                continue;
            }
            String type = item == ModItems.DRIVE_BAY.get() ? "Drive Bay" : item == ModItems.TERMINAL_DESK.get() ? "Terminal"
                    : item == ModItems.DISK_DRIVE.get() ? "Disk" : item == ModItems.TAPE_DRIVE.get() ? "Tape"
                    : blockEntity(server, pos) instanceof MidrangeSystemBlockEntity ? "Controller" : "Device";
            Component name = item.getName(item.getDefaultInstance());
            // A Display Panel screen with its size: "Display Panel 3 x 2 (96 x 64)".
            if (blockEntity(server, pos) instanceof DisplayPanelBlockEntity display) {
                name = Component.translatable("gui.encodedlogistics.display.device", name, display.width(), display.height(), display.canvasWidth(),
                        display.canvasHeight());
            }
            // A Control Interface goes by the name scripts use for it.
            if (blockEntity(server, pos) instanceof ControlInterfaceBlockEntity ci && !ci.name().isEmpty()) {
                type = ControlInterfaceBlockEntity.TYPE;
                name = Component.literal(ci.name());
            }
            // A PLC is type PLC, by its program's name.
            if (blockEntity(server, pos) instanceof PlcBlockEntity plc) {
                type = PlcBlockEntity.TYPE;
                name = Component.translatable("gui.encodedlogistics.plc.device", name, plc.programName());
            }
            if (node.parts().isEmpty()) {
                boolean shown = online || item == ModItems.WIRELESS_BRIDGE.get() && networkOnline
                        || listed && blockEntity(server, pos) instanceof ListedDevice device && device.listedOnline();
                others.add(new DeviceRow(type, name, pos, node.laneCost(), shown, missing, null, 0));
            }
        }
        rows.addAll(others);
        return rows;
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

    // The block entities of a kind on a network (as last worked out), online ones only when asked.
    public static <T> List<T> onNetwork(MinecraftServer server, @Nullable NetworkRef ref, Class<T> kind, boolean onlineOnly) {
        Owner owner = owner(server, ref);
        List<T> found = new ArrayList<>();
        if (owner == null) {
            return found;
        }
        for (NodePos pos : owner.runtime.members) {
            if ((!onlineOnly || owner.runtime.online.contains(pos)) && kind.isInstance(blockEntity(server, pos))) {
                found.add(kind.cast(blockEntity(server, pos)));
            }
        }
        return found;
    }

    // Whether the device at pos has its lanes on a running network.
    public static boolean deviceOnline(ServerLevel level, BlockPos pos) {
        return onlineRuntime(level, pos) != null;
    }

    // The devices reached from a node through its cables alone, not its remote links (what a Wireless Bridge brings).
    public static int devicesBehind(MinecraftServer server, NodePos start) {
        Owner owner = owner(server, NetworkIndex.get(server).members.get(start));
        if (owner == null || owner.runtime.discovered == null) {
            return 0;
        }
        NetworkGraph graph = owner.runtime.discovered.graph();
        Set<NodePos> seen = new HashSet<>(Set.of(start));
        ArrayDeque<NodePos> queue = new ArrayDeque<>(seen);
        int devices = 0;
        while (!queue.isEmpty()) {
            NodePos at = queue.poll();
            for (Direction side : Direction.values()) {
                NetworkLink link = graph.link(at, side);
                if (link == null) {
                    continue;
                }
                NodePos next = link.other(at);
                if (seen.add(next)) {
                    NetworkNode node = graph.node(next);
                    if (node != null && node.isDevice()) {
                        devices++;
                    }
                    queue.add(next);
                }
            }
        }
        return devices;
    }

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

    // Lanes running through a cable as last solved, and how many it carries: the busiest of its links (the one toward
    // the controller carries everything beyond it). Null off a controlled network, or for a node that isn't a cable.
    public static int @Nullable [] cableLanes(MinecraftServer server, NodePos pos) {
        Owner owner = owner(server, NetworkIndex.get(server).members.get(pos));
        if (owner == null || owner.runtime.discovered == null || owner.runtime.lanes == null || owner.runtime.lanes.adHoc()) {
            return null;
        }
        NetworkNode node = owner.runtime.discovered.graph().node(pos);
        if (node == null || node.laneCapacity() == NetworkNode.UNLIMITED) {
            return null;
        }
        int used = 0;
        for (Direction side : Direction.values()) {
            NetworkLink link = owner.runtime.discovered.graph().link(pos, side);
            if (link != null) {
                used = Math.max(used, owner.runtime.lanes.usage(link));
            }
        }
        for (NetworkLink link : owner.runtime.discovered.graph().remoteLinks(pos)) {
            used = Math.max(used, owner.runtime.lanes.usage(link));
        }
        return new int[] { used, node.laneCapacity() };
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
        return drawEnergy(level.getServer(), networkOf(level, device), amount);
    }

    // The same, from a network found some other way (a rack device serving a segment).
    public static int drawEnergy(MinecraftServer server, @Nullable NetworkRef network, int amount) {
        Owner owner = owner(server, network);
        if (owner == null || owner.runtime.status != NetworkStatus.ONLINE || amount <= 0) {
            return 0;
        }
        return drain(cells(server, owner), buffers(owner), amount);
    }

    // The same, but never taking the network's stored energy below reserve (a share of its capacity): for energy given
    // away (a machine's power from network), so the network keeps running.
    public static int drawEnergyAbove(MinecraftServer server, @Nullable NetworkRef network, int amount, double reserve) {
        Owner owner = owner(server, network);
        if (owner == null || owner.runtime.status != NetworkStatus.ONLINE || amount <= 0) {
            return 0;
        }
        List<EnergyCell> banks = cells(server, owner);
        List<ControllerBuffer> buffers = buffers(owner);
        long stored = 0, capacity = 0;
        for (ControllerBuffer buffer : buffers) {
            stored += buffer.getEnergy();
            capacity += buffer.getCapacity();
        }
        for (EnergyCell bank : banks) {
            stored += bank.getStored();
            capacity += bank.getCapacity();
        }
        long spare = stored - (long) Math.ceil(capacity * reserve);
        return spare <= 0 ? 0 : drain(banks, buffers, (int) Math.min(amount, spare));
    }

    // --- Energy from outside ---

    // The network whose energy a Power Inlet or Capacitor Bank at pos is part of, or null when it isn't on a
    // controller's network.
    public @Nullable NetworkRef energyNetworkOf(ServerLevel level, BlockPos pos) {
        return networkOf(level, pos);
    }

    // Puts up to amount FE into a network's energy: its controllers first, then its banks and Energy Storage Drives.
    // Returns what went in.
    public static int fill(MinecraftServer server, NetworkRef network, int amount, TransactionContext transaction) {
        Owner owner = owner(server, network);
        if (owner == null || owner.runtime.status.isError()) {
            return 0;
        }
        int left = amount;
        for (ControllerBuffer controller : buffers(owner)) {
            if (left <= 0) {
                break;
            }
            left -= controller.fill(left, transaction);
        }
        for (EnergyCell bank : cells(server, owner)) {
            if (left <= 0) {
                break;
            }
            left -= bank.fill(left, transaction);
        }
        // A rack pair's standby keeps its own buffer topped up from what's left.
        for (NetworkControllerDevice standby : owner.runtime.standbys) {
            if (left <= 0) {
                break;
            }
            left -= standby.fill(left, transaction);
        }
        return amount - left;
    }

    // The buffers that are a network's working energy: its controller blocks (loaded), or its working rack controller.
    private static List<ControllerBuffer> buffers(Owner owner) {
        List<ControllerBuffer> buffers = new ArrayList<>();
        if (owner.structure.rack()) {
            if (owner.runtime.working != null) {
                buffers.add(owner.runtime.working);
            }
            return buffers;
        }
        if (owner.structure.midrange()) {
            BlockPos pos = owner.structure.members().getFirst();
            if (owner.home.isLoaded(pos) && owner.home.getBlockEntity(pos) instanceof MidrangeSystemBlockEntity midrange
                    && midrange.controllerStructure() == owner.ref.id()) {
                buffers.add(midrange);
            }
            return buffers;
        }
        for (BlockPos pos : owner.structure.members()) {
            if (owner.home.isLoaded(pos) && owner.home.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller
                    && controller.getStructureId() == owner.ref.id()) {
                buffers.add(controller);
            }
        }
        return buffers;
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
        boolean online = runtime.status == NetworkStatus.ONLINE || runtime.status == NetworkStatus.FAILOVER;
        // The structure's own blocks come first in the list (sorted with the rest by count).
        if (!structure.single()) {
            devices.add(new NetworkSnapshot.DeviceEntry(BuiltInRegistries.ITEM.getKey(ModItems.NETWORK_CONTROLLER.get()), structure.members().size(),
                    Config.CONTROLLER_DRAIN.getAsDouble() * structure.members().size(), 0, !online));
        }
        if (runtime.discovered != null && lanes != null) {
            Map<Item, int[]> counts = new LinkedHashMap<>();
            Map<Item, Double> drains = new HashMap<>();
            runtime.discovered.graph().entries().forEach((pos, node) -> {
                if (node.isController()) {
                    return;
                }
                boolean missingLane = online && node.isDevice() && (!lanes.hasLane(pos) || lanes.rack(pos).shed());
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
        return new NetworkSnapshot(runtime.status, runtime.stored, runtime.capacity, runtime.driveStored, runtime.driveCapacity, runtime.usage, runtime.generation,
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
                providers = List.of(), schedulers = List.of(), relays = List.of(), racks = List.of();
        // The devices online as of the last tick.
        Set<NodePos> online = Set.of();
        final int[] received = new int[GENERATION_WINDOW];
        int receivedIndex;
        double drainCarry;
        NetworkStatus status = NetworkStatus.NO_POWER;
        long stored, capacity;
        // The part of it in Energy Storage Drives; and each drive's charge as of the last tick, for its charging light.
        long driveStored, driveCapacity;
        Map<UUID, Long> driveCharge = new HashMap<>();
        double usage, generation;
        int comparator = -1;
        // Counted for Monitoring Servers: items moved in or out of storage, crafting jobs finished.
        long itemsMoved, jobsDone;
        // Rack controllers: the structure whose controllers run the network (its own, or its partner's), the pair as last
        // run, the active one, the working one (taking over, during a failover) and the standbys, and a failover in
        // progress.
        long lead;
        List<NetworkControllerDevice> pair = List.of(), standbys = List.of();
        @Nullable NetworkControllerDevice active, working, failoverTarget;
        int failoverTicks;
        boolean hadActive;
        int deviceCount;
        // Tape recalls waiting and running, archiving in progress.
        final TapeRecalls recalls = new TapeRecalls();
        // Crafting jobs' numbers as the Terminal Desk shows them (0001-9999, in the order it first saw them).
        final Map<UUID, Integer> jobNumbers = new HashMap<>();
        int nextJob = 1;
    }
}
