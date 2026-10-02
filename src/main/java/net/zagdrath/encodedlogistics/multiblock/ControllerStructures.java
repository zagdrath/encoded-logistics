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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.ControllerState;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.network.ChannelResult;
import net.zagdrath.encodedlogistics.network.ChannelSolver;
import net.zagdrath.encodedlogistics.network.NetworkDiscovery;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.network.NetworkStatus;

// Every Network Controller structure in a level, by id, saved with the level. Each controller block entity holds its
// structure's id.
//
// Placing or breaking a controller queues the positions around it; once per tick the queued positions are flood-filled
// into groups, each group is validated (ControllerFrame) and keeps the id most of it had, or gets a new one. Then every
// structure ticks: its energy (buffer, drain, generation over the last 20 ticks), its network (rediscovered and
// re-solved only when the topology changed) and the FORMED / STATE of its blocks.
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
        for (Structure structure : List.copyOf(structures.values())) {
            Runtime runtime = runtimes.computeIfAbsent(structure.id(), id -> new Runtime());
            if (topology) {
                runtime.dirty = true;
            }
            tickStructure(level, structure, runtime);
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

    private void tickStructure(ServerLevel level, Structure structure, Runtime runtime) {
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
            runtime.channels = null;
            runtime.dirty = true;
        } else {
            if (runtime.dirty || runtime.discovered == null || runtime.channels == null) {
                runtime.discovered = NetworkDiscovery.discover(level, structure.id(), structure.members(),
                        Config.CHANNELS_PER_CONTROLLER_FACE.getAsInt());
                runtime.channels = ChannelSolver.solve(runtime.discovered.graph(), Config.ADHOC_MAX_DEVICES.getAsInt());
                runtime.dirty = false;
            }
            if (runtime.channels.status() == NetworkStatus.CONFLICT) {
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
        ChannelResult channels = runtime.channels;
        List<NetworkSnapshot.DeviceEntry> devices = new ArrayList<>();
        if (runtime.discovered != null && channels != null) {
            boolean online = runtime.status == NetworkStatus.ONLINE;
            Map<Item, int[]> counts = new LinkedHashMap<>();
            Map<Item, Double> drains = new HashMap<>();
            for (NetworkNode node : runtime.discovered.graph().nodes()) {
                if (node.isController()) {
                    continue;
                }
                Item item = runtime.discovered.items().get(node.pos());
                if (item == null) {
                    continue;
                }
                int[] count = counts.computeIfAbsent(item, key -> new int[2]);
                count[0]++;
                if (online && node.channelCost() > 0 && !channels.hasChannel(node.pos())) {
                    count[1]++;
                }
                drains.merge(item, node.passiveDrain(), Double::sum);
            }
            counts.forEach((item, count) -> {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                devices.add(new NetworkSnapshot.DeviceEntry(itemId, count[0], drains.getOrDefault(item, 0.0), count[1], !online));
            });
            devices.sort(Comparator.comparingInt(NetworkSnapshot.DeviceEntry::count).reversed()
                    .thenComparing(entry -> entry.item().toString()));
        }
        return new NetworkSnapshot(runtime.status, runtime.stored, runtime.capacity, runtime.usage, runtime.generation,
                channels != null ? channels.used() : 0, channels != null ? channels.capacity() : 0,
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
        @Nullable ChannelResult channels;
        final int[] received = new int[GENERATION_WINDOW];
        int receivedIndex;
        double drainCarry;
        NetworkStatus status = NetworkStatus.NO_POWER;
        long stored, capacity;
        double usage, generation;
        int comparator = -1;
    }
}
