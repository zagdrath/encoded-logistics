/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

// The LaneSolver gives exactly what it gave before its rack uplink search was reworked (LaneSolverReference, the old
// solver kept verbatim): on hundreds of generated networks shaped like a data hall - a cable floor, rows of Server Racks
// with up to seven uplinks each (floor, rear cable columns, a cable bus over the tops that carries lanes through racks),
// cut cables and stubs, devices, Network Bridges, and every kind of source (controller blocks, a source rack, a
// redundant pair, a Midrange System) - every device's lane, every rack's lanes and uplinks and every link's usage are
// the same. Also times both on the profiled server's shape (50 racks, 7 uplinks each).
class LaneSolverEquivalenceTest {
    private static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final Set<Direction> ALL = EnumSet.allOf(Direction.class);
    private static final int AD_HOC = 8;

    private record Cable(BlockPos pos, Set<Direction> connections, int laneCapacity) implements NetworkNode {
        @Override
        public int laneCost() {
            return 0;
        }

        @Override
        public double passiveDrain() {
            return 0;
        }
    }

    private record Device(BlockPos pos, int laneCost) implements NetworkNode {
        @Override
        public double passiveDrain() {
            return 1;
        }

        @Override
        public Set<Direction> connections() {
            return ALL;
        }
    }

    private record Controller(BlockPos pos, long controllerGroup, int laneCapacity) implements NetworkNode {
        @Override
        public int laneCost() {
            return 0;
        }

        @Override
        public double passiveDrain() {
            return 0;
        }

        @Override
        public Set<Direction> connections() {
            return ALL;
        }
    }

    private record Bridge(BlockPos pos, List<RemoteLink> remoteLinks) implements NetworkNode {
        @Override
        public int laneCost() {
            return 0;
        }

        @Override
        public double passiveDrain() {
            return 0;
        }

        @Override
        public Set<Direction> connections() {
            return ALL;
        }
    }

    enum Source {
        CONTROLLER, CONTROLLERS, SOURCE_RACK, RACK_PAIR, MIDRANGE
    }

    // A generated data hall. racks in rows of cols; floor cables at y = 0, racks on y 1-3 (x, z..z+1), rear columns at
    // z + 2, a bus over the tops at y = 4.
    static NetworkGraph hall(long seed, int rackCount, Source source) {
        Random random = new Random(seed);
        Map<BlockPos, NetworkNode> nodes = new HashMap<>();
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(rackCount)));
        int rows = (rackCount + cols - 1) / cols;
        int width = 3 * cols + 4, depth = 4 * rows + 4;
        // The floor, with holes and mixed tiers.
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (random.nextInt(100) < 6 && x + z > 0) {
                    continue;
                }
                nodes.put(new BlockPos(x, 0, z), new Cable(new BlockPos(x, 0, z), ALL, random.nextInt(10) < 7 ? 32 : 8));
            }
        }
        List<BlockPos> masters = new ArrayList<>();
        for (int i = 0; i < rackCount; i++) {
            int x = 2 + 3 * (i % cols), z = 2 + 4 * (i / cols);
            BlockPos master = new BlockPos(x, 1, z);
            masters.add(master);
            boolean sourceRack = source == Source.SOURCE_RACK && i == 0 || source == Source.RACK_PAIR && i < 2;
            // Which of its seven connection points it uses: two to the floor, two to the bus, three to the rear column.
            Set<BlockPos> blocks = new java.util.HashSet<>();
            for (int y = 1; y <= 3; y++) {
                blocks.add(new BlockPos(x, y, z));
                blocks.add(new BlockPos(x, y, z + 1));
            }
            for (BlockPos block : blocks) {
                Set<Direction> sides = EnumSet.noneOf(Direction.class);
                for (Direction side : Direction.values()) {
                    BlockPos next = block.relative(side);
                    boolean inside = blocks.contains(next);
                    boolean point = side == Direction.DOWN && block.getY() == 1 || side == Direction.UP && block.getY() == 3
                            || side == Direction.SOUTH && block.getZ() == z + 1;
                    if (inside || point && random.nextInt(100) < 85) {
                        sides.add(side);
                    }
                }
                if (block.equals(master)) {
                    List<RackNode.LaneDemand> demands = new ArrayList<>();
                    int count = 4 + random.nextInt(10);
                    for (int d = 0; d < count; d++) {
                        demands.add(new RackNode.LaneDemand(1 + random.nextInt(42), random.nextInt(8) == 0 ? 2 : random.nextInt(12) == 0 ? 0 : 1,
                                random.nextInt(3)));
                    }
                    List<RackNode.RackController> controllers = sourceRack
                            ? List.of(new RackNode.RackController(40, 2, source == Source.RACK_PAIR ? 96 : 128 + random.nextInt(128), random.nextInt(10) > 0))
                            : List.of();
                    nodes.put(block, new RackNode(block, sides, 1, List.of(), demands, controllers, sourceRack ? 100 + i : 0));
                } else {
                    nodes.put(block, new RackPartNode(block, sides, master));
                }
            }
            // The rear column (sometimes cut, sometimes a stub that leads nowhere), and the bus over the top.
            int column = random.nextInt(10);
            for (int y = 1; y <= 3; y++) {
                BlockPos pos = new BlockPos(x, y, z + 2);
                Set<Direction> sides = EnumSet.of(Direction.NORTH, Direction.UP, Direction.DOWN);
                if (column == 0 && y == 1) {
                    sides.remove(Direction.DOWN);
                }
                if (column == 1) {
                    sides = EnumSet.of(Direction.NORTH);
                }
                nodes.put(pos, new Cable(pos, sides, random.nextInt(4) == 0 ? 8 : 32));
            }
            if (random.nextInt(10) < 8) {
                for (int dx = 0; dx < 3; dx++) {
                    BlockPos pos = new BlockPos(x + dx, 4, z);
                    nodes.put(pos, new Cable(pos, ALL, random.nextInt(3) == 0 ? 8 : 32));
                }
            }
            if (random.nextInt(3) == 0) {
                // A drop from the bus down to the floor beside the rack.
                for (int y = 1; y <= 3; y++) {
                    BlockPos pos = new BlockPos(x + 1, y, z + 1);
                    nodes.putIfAbsent(pos, new Cable(pos, ALL, 32));
                }
            }
        }
        // Devices on the floor and the bus wherever there's room.
        int devices = rackCount * 3 + random.nextInt(rackCount + 4);
        for (int d = 0; d < devices; d++) {
            BlockPos pos = new BlockPos(random.nextInt(width), random.nextBoolean() ? 1 : 5, random.nextInt(depth));
            nodes.putIfAbsent(pos, new Device(pos, 1 + (random.nextInt(6) == 0 ? 1 : 0)));
        }
        // The sources.
        switch (source) {
            case CONTROLLER -> nodes.put(new BlockPos(-1, 0, 0), new Controller(new BlockPos(-1, 0, 0), 1, 32));
            case CONTROLLERS -> {
                nodes.put(new BlockPos(-1, 0, 0), new Controller(new BlockPos(-1, 0, 0), 1, 32));
                nodes.put(new BlockPos(-1, 0, 1), new Controller(new BlockPos(-1, 0, 1), 1, 32));
                nodes.put(new BlockPos(-1, 0, depth / 2), new Controller(new BlockPos(-1, 0, depth / 2), 1, 32));
            }
            case MIDRANGE -> nodes.put(new BlockPos(-1, 0, 0), new SourceNode(new BlockPos(-1, 0, 0), ALL, 2, 9, 96));
            default -> {
            }
        }
        // A pair of Network Bridges joining two far corners.
        BlockPos a = new BlockPos(0, -1, 0), b = new BlockPos(width - 1, -1, depth - 1);
        if (random.nextBoolean()) {
            nodes.put(a, new Bridge(a, List.of(new RemoteLink(NodePos.of(DIM, b), 32))));
            nodes.put(b, new Bridge(b, List.of(new RemoteLink(NodePos.of(DIM, a), 32))));
        }
        return graph(nodes, masters, random);
    }

    // Adds the nodes in a shuffled order and links neighbours that connect toward each other (controllers to each
    // other excepted, as discovery does), then the bridges.
    private static NetworkGraph graph(Map<BlockPos, NetworkNode> nodes, List<BlockPos> masters, Random random) {
        NetworkGraph graph = new NetworkGraph();
        List<NetworkNode> order = new ArrayList<>(nodes.values());
        java.util.Collections.shuffle(order, random);
        for (NetworkNode node : order) {
            graph.addNode(DIM, node);
        }
        for (NetworkNode node : order) {
            for (Direction side : node.connections()) {
                NetworkNode other = nodes.get(node.pos().relative(side));
                if (other != null && other.connections().contains(side.getOpposite()) && !(node.isController() && other.isController())) {
                    graph.connect(NodePos.of(DIM, node.pos()), side);
                }
            }
            for (RemoteLink link : node.remoteLinks()) {
                if (graph.contains(link.target())) {
                    graph.connectRemote(NodePos.of(DIM, node.pos()), link.target(), link.capacity());
                }
            }
        }
        return graph;
    }

    @Test
    void sameResultsAsBefore() {
        int compared = 0;
        for (Source source : Source.values()) {
            for (long seed = 0; seed < 200; seed++) {
                int racks = 1 + (int) (seed % 31);
                NetworkGraph graph = hall(seed * 31 + source.ordinal(), racks, source);
                LaneResult before = LaneSolverReference.solve(graph, AD_HOC);
                LaneResult after = LaneSolver.solve(graph, AD_HOC);
                assertEquals(before, after, source + " seed " + seed + " (" + racks + " racks)");
                compared++;
            }
        }
        assertEquals(Source.values().length * 200, compared);
    }

    @Test
    void sameResultsOnTheProfiledShape() {
        for (Source source : Source.values()) {
            NetworkGraph graph = hall(4242 + source.ordinal(), 50, source);
            assertEquals(LaneSolverReference.solve(graph, AD_HOC), LaneSolver.solve(graph, AD_HOC), source.toString());
        }
    }

    // 50 racks with seven uplinks each on one network, as on the profiled server: both solvers, best of several runs.
    @Test
    void solveTimes() {
        NetworkGraph graph = hall(7, 50, Source.CONTROLLERS);
        long before = best(() -> LaneSolverReference.solve(graph, AD_HOC));
        long after = best(() -> LaneSolver.solve(graph, AD_HOC));
        System.out.printf("LaneSolver on %d nodes, 50 racks: before %.2f ms, after %.2f ms%n", graph.size(), before / 1e6, after / 1e6);
        assertTrue(after <= before * 2 + 1_000_000, "the new solver isn't slower");
    }

    private static long best(Runnable solve) {
        for (int i = 0; i < 5; i++) {
            solve.run();
        }
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 15; i++) {
            long start = System.nanoTime();
            solve.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }
}
