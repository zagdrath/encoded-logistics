/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

class LaneSolverTest {
    private static final int FACE = 32, CABLE = 8, DENSE = 32, AD_HOC = 8;
    private static final Set<Direction> ALL = EnumSet.allOf(Direction.class);

    private record Node(BlockPos pos, int laneCost, double passiveDrain, Set<Direction> connections, int laneCapacity,
            long controllerGroup) implements NetworkNode {}

    private static Node controller(int x, int y, int z, long group) {
        return new Node(new BlockPos(x, y, z), 0, 0, ALL, FACE, group);
    }

    private static Node cable(int x, int y, int z, int tier) {
        return new Node(new BlockPos(x, y, z), 0, 0, ALL, tier, NetworkNode.NO_CONTROLLER);
    }

    private static Node device(int x, int y, int z) {
        return new Node(new BlockPos(x, y, z), 1, 1, ALL, NetworkNode.UNLIMITED, NetworkNode.NO_CONTROLLER);
    }

    // Adds the nodes and links every pair of neighbours in the graph (except controller to controller).
    private static NetworkGraph graph(NetworkNode... nodes) {
        NetworkGraph graph = new NetworkGraph();
        for (NetworkNode node : nodes) {
            graph.addNode(node);
        }
        for (NetworkNode node : nodes) {
            for (Direction side : Direction.values()) {
                NetworkNode other = graph.node(node.pos().relative(side));
                if (other != null && !(node.isController() && other.isController())) {
                    graph.connect(node.pos(), side);
                }
            }
        }
        return graph;
    }

    @Test
    void singleCableOverCapacity() {
        // Controller at x=0, an 8-lane cable at x=1, then a dense cable spine along x=2 with nine devices on it (y=1).
        // Every device's path runs through the one cable, so only eight get a lane.
        NetworkNode[] nodes = new NetworkNode[3 + 9 + 9];
        int n = 0;
        nodes[n++] = controller(0, 0, 0, 1);
        nodes[n++] = cable(1, 0, 0, CABLE);
        nodes[n++] = cable(2, 0, 0, DENSE);
        for (int z = 1; z <= 9; z++) {
            nodes[n++] = cable(2, 0, z, DENSE);
        }
        for (int z = 0; z <= 8; z++) {
            nodes[n++] = device(2, 1, z);
        }
        LaneResult result = LaneSolver.solve(graph(nodes), AD_HOC);

        assertEquals(NetworkStatus.ONLINE, result.status());
        assertEquals(FACE, result.capacity());
        assertEquals(8, result.used());
        assertEquals(1, result.missing());
        // Nearest first: the device furthest down the spine misses out.
        assertFalse(result.hasLane(new BlockPos(2, 1, 8)));
        NetworkLink throughCable = new NetworkLink(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), CABLE);
        assertEquals(8, result.usage(throughCable));
    }

    @Test
    void twoBranchesEachLimitedByTheirCable() {
        // Controller at the origin with an 8-lane cable on its east and west faces, ten devices beyond each.
        NetworkNode[] nodes = new NetworkNode[1 + 2 * (1 + 10 + 10)];
        int n = 0;
        nodes[n++] = controller(0, 0, 0, 1);
        for (int sign : new int[] { 1, -1 }) {
            nodes[n++] = cable(sign, 0, 0, CABLE);
            for (int i = 0; i < 10; i++) {
                nodes[n++] = cable(sign * 2, 0, i, DENSE);
                nodes[n++] = device(sign * 2, 1, i);
            }
        }
        LaneResult result = LaneSolver.solve(graph(nodes), AD_HOC);

        assertEquals(2 * FACE, result.capacity());
        assertEquals(16, result.used());
        assertEquals(4, result.missing());
        long east = result.lanes().entrySet().stream().filter(e -> e.getKey().getX() > 0 && e.getValue()).count();
        long west = result.lanes().entrySet().stream().filter(e -> e.getKey().getX() < 0 && e.getValue()).count();
        assertEquals(8, east);
        assertEquals(8, west);
    }

    @Test
    void tiesGoToTheLowerBlockPos() {
        // A 1-lane bottleneck with two devices at the same distance: the lower position wins, every time.
        Node bottleneck = new Node(new BlockPos(1, 0, 0), 0, 0, ALL, 1, NetworkNode.NO_CONTROLLER);
        NetworkNode[] nodes = { controller(0, 0, 0, 1), bottleneck, device(1, 0, -1), device(1, 0, 1) };
        for (int run = 0; run < 5; run++) {
            LaneResult result = LaneSolver.solve(graph(nodes), AD_HOC);
            assertTrue(result.hasLane(new BlockPos(1, 0, -1)));
            assertFalse(result.hasLane(new BlockPos(1, 0, 1)));
        }
    }

    @Test
    void nearerDevicesWinOverLowerPositions() {
        // The far device has the lower position but is further away, so the near one gets the only lane.
        Node bottleneck = new Node(new BlockPos(1, 0, 0), 0, 0, ALL, 1, NetworkNode.NO_CONTROLLER);
        NetworkNode[] nodes = { controller(0, 0, 0, 1), bottleneck, device(2, 0, 0), cable(1, 0, -1, CABLE), device(1, 0, -2) };
        LaneResult result = LaneSolver.solve(graph(nodes), AD_HOC);
        assertTrue(result.hasLane(new BlockPos(2, 0, 0)));
        assertFalse(result.hasLane(new BlockPos(1, 0, -2)));
    }

    @Test
    void capacityIsThirtyTwoPerConnectedControllerFace() {
        // A 2-block controller structure with something on three of its faces.
        NetworkNode[] nodes = { controller(0, 0, 0, 1), controller(1, 0, 0, 1), cable(-1, 0, 0, CABLE), cable(2, 0, 0, CABLE),
                device(0, 1, 0) };
        LaneResult result = LaneSolver.solve(graph(nodes), AD_HOC);
        assertEquals(3 * FACE, result.capacity());
        assertEquals(1, result.used());
    }

    @Test
    void adHocLimit() {
        NetworkNode[] eight = new NetworkNode[16];
        NetworkNode[] nine = new NetworkNode[18];
        for (int i = 0; i < 9; i++) {
            if (i < 8) {
                eight[2 * i] = cable(i, 0, 0, CABLE);
                eight[2 * i + 1] = device(i, 1, 0);
            }
            nine[2 * i] = cable(i, 0, 0, CABLE);
            nine[2 * i + 1] = device(i, 1, 0);
        }
        LaneResult fits = LaneSolver.solve(graph(eight), AD_HOC);
        assertTrue(fits.adHoc());
        assertEquals(NetworkStatus.ONLINE, fits.status());
        assertEquals(8, fits.used());
        assertEquals(0, fits.missing());

        LaneResult over = LaneSolver.solve(graph(nine), AD_HOC);
        assertEquals(NetworkStatus.ADHOC_OVERLOAD, over.status());
        assertEquals(0, over.used());
        assertEquals(9, over.missing());
    }

    @Test
    void conflictBetweenTwoControllerStructures() {
        NetworkNode[] nodes = { controller(0, 0, 0, 1), cable(1, 0, 0, CABLE), device(1, 1, 0), cable(2, 0, 0, CABLE),
                controller(3, 0, 0, 2) };
        LaneResult result = LaneSolver.solve(graph(nodes), AD_HOC);
        assertEquals(NetworkStatus.CONFLICT, result.status());
        assertEquals(0, result.used());
        assertFalse(result.hasLane(new BlockPos(1, 1, 0)));
    }

    @Test
    void oneStructureIsNotAConflict() {
        NetworkNode[] nodes = { controller(0, 0, 0, 7), controller(0, 1, 0, 7), cable(1, 0, 0, CABLE), device(1, 1, 0) };
        assertEquals(NetworkStatus.ONLINE, LaneSolver.solve(graph(nodes), AD_HOC).status());
    }

    @Test
    void unreachableDeviceHasNoLane() {
        // Linked to the network only through a node that doesn't connect back.
        Node oneWay = new Node(new BlockPos(1, 0, 0), 0, 0, EnumSet.of(Direction.EAST), CABLE, NetworkNode.NO_CONTROLLER);
        NetworkGraph graph = new NetworkGraph();
        graph.addNode(controller(0, 0, 0, 1));
        graph.addNode(device(0, 1, 0));
        graph.addNode(oneWay);
        graph.addNode(device(5, 5, 5));
        graph.connect(new BlockPos(0, 0, 0), Direction.UP);
        LaneResult result = LaneSolver.solve(graph, AD_HOC);
        assertTrue(result.hasLane(new BlockPos(0, 1, 0)));
        assertFalse(result.hasLane(new BlockPos(5, 5, 5)));
    }
}
