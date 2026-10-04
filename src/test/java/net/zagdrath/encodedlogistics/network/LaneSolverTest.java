/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaneSolverTest {
    private static final int FACE = 32, CABLE = 8, DENSE = 32, AD_HOC = 8;
    private static final Set<Direction> ALL = EnumSet.allOf(Direction.class);
    private static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld")),
            OTHER = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));

    private static NodePos at(BlockPos pos) {
        return NodePos.of(DIM, pos);
    }

    private record Node(BlockPos pos, int laneCost, double passiveDrain, Set<Direction> connections, int laneCapacity,
            long controllerGroup) implements NetworkNode {}

    // A node with remote links and nothing else (a Bridge).
    private record Remote(BlockPos pos, List<RemoteLink> remoteLinks) implements NetworkNode {
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
            graph.addNode(DIM, node);
        }
        for (NetworkNode node : nodes) {
            for (Direction side : Direction.values()) {
                NetworkNode other = graph.node(at(node.pos().relative(side)));
                if (other != null && !(node.isController() && other.isController())) {
                    graph.connect(at(node.pos()), side);
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
        assertFalse(result.hasLane(at(new BlockPos(2, 1, 8))));
        NetworkLink throughCable = new NetworkLink(at(new BlockPos(0, 0, 0)), at(new BlockPos(1, 0, 0)), CABLE);
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
        long east = result.lanes().entrySet().stream().filter(e -> e.getKey().pos().getX() > 0 && e.getValue()).count();
        long west = result.lanes().entrySet().stream().filter(e -> e.getKey().pos().getX() < 0 && e.getValue()).count();
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
            assertTrue(result.hasLane(at(new BlockPos(1, 0, -1))));
            assertFalse(result.hasLane(at(new BlockPos(1, 0, 1))));
        }
    }

    @Test
    void nearerDevicesWinOverLowerPositions() {
        // The far device has the lower position but is further away, so the near one gets the only lane.
        Node bottleneck = new Node(new BlockPos(1, 0, 0), 0, 0, ALL, 1, NetworkNode.NO_CONTROLLER);
        NetworkNode[] nodes = { controller(0, 0, 0, 1), bottleneck, device(2, 0, 0), cable(1, 0, -1, CABLE), device(1, 0, -2) };
        LaneResult result = LaneSolver.solve(graph(nodes), AD_HOC);
        assertTrue(result.hasLane(at(new BlockPos(2, 0, 0))));
        assertFalse(result.hasLane(at(new BlockPos(1, 0, -2))));
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
        assertFalse(result.hasLane(at(new BlockPos(1, 1, 0))));
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
        graph.addNode(DIM, controller(0, 0, 0, 1));
        graph.addNode(DIM, device(0, 1, 0));
        graph.addNode(DIM, oneWay);
        graph.addNode(DIM, device(5, 5, 5));
        graph.connect(at(new BlockPos(0, 0, 0)), Direction.UP);
        LaneResult result = LaneSolver.solve(graph, AD_HOC);
        assertTrue(result.hasLane(at(new BlockPos(0, 1, 0))));
        assertFalse(result.hasLane(at(new BlockPos(5, 5, 5))));
    }

    @Test
    void remoteLinkCarriesLanesIntoAnotherDimension() {
        // Controller - 8-lane cable - bridge here; its partner in another dimension at the same coordinates, with two
        // devices beyond it. The remote link carries 1 lane: one device gets it, and it shows on the link.
        NetworkGraph graph = new NetworkGraph();
        NodePos here = at(new BlockPos(2, 0, 0)), there = NodePos.of(OTHER, new BlockPos(2, 0, 0));
        graph.addNode(DIM, controller(0, 0, 0, 1));
        graph.addNode(DIM, cable(1, 0, 0, CABLE));
        graph.addNode(DIM, new Remote(new BlockPos(2, 0, 0), List.of(new RemoteLink(there, 1))));
        graph.addNode(OTHER, new Remote(new BlockPos(2, 0, 0), List.of(new RemoteLink(here, 1))));
        graph.addNode(OTHER, device(3, 0, 0));
        graph.addNode(OTHER, device(2, 1, 0));
        graph.connect(at(new BlockPos(0, 0, 0)), Direction.EAST);
        graph.connect(at(new BlockPos(1, 0, 0)), Direction.EAST);
        NetworkLink bridge = graph.connectRemote(here, there, 1);
        graph.connect(there, Direction.EAST);
        graph.connect(there, Direction.UP);
        LaneResult result = LaneSolver.solve(graph, AD_HOC);
        assertEquals(1, result.used());
        assertEquals(1, result.missing());
        assertEquals(1, result.usage(bridge));
        // Same distance: the lower position (y=0 before y=1) wins.
        assertTrue(result.hasLane(NodePos.of(OTHER, new BlockPos(2, 1, 0))) != result.hasLane(NodePos.of(OTHER, new BlockPos(3, 0, 0))));
    }

    // --- Server Racks ---

    private static RackNode rack(int x, int y, int z, List<RackNode.LaneDemand> demands, RackNode.RackController... controllers) {
        return new RackNode(new BlockPos(x, y, z), ALL, 0, List.of(), demands, List.of(controllers), controllers.length > 0 ? 1 : 0);
    }

    private static RackPartNode part(int x, int y, int z, BlockPos master) {
        return new RackPartNode(new BlockPos(x, y, z), ALL, master);
    }

    private static List<RackNode.LaneDemand> demands(int count, int cost, int priority) {
        List<RackNode.LaneDemand> list = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new RackNode.LaneDemand(1 + i, cost, priority));
        }
        return list;
    }

    @Test
    void rackControllerIsASourceForItsOwnRackAndCables() {
        // A rack with a 192-lane controller and three devices; an 8-lane cable to a device outside.
        RackNode source = rack(0, 0, 0, demands(3, 1, 1), new RackNode.RackController(20, 2, 192, true));
        NetworkNode[] nodes = { source, part(0, 1, 0, source.pos()), cable(0, 2, 0, CABLE), device(0, 3, 0) };
        LaneResult result = LaneSolver.solve(graph(nodes), AD_HOC);
        assertEquals(NetworkStatus.ONLINE, result.status());
        assertEquals(192, result.capacity());
        assertEquals(4, result.used());
        assertTrue(result.rack(at(source.pos())).source());
        assertEquals(Set.of(1, 2, 3), result.rack(at(source.pos())).granted());
        assertTrue(result.hasLane(at(new BlockPos(0, 3, 0))));
    }

    @Test
    void rackBudgetLimitsEverythingDrawingFromIt() {
        RackNode source = rack(0, 0, 0, demands(3, 1, 1), new RackNode.RackController(20, 2, 3, true));
        NetworkNode[] nodes = { source, part(0, 1, 0, source.pos()), cable(0, 2, 0, CABLE), device(0, 3, 0) };
        LaneResult result = LaneSolver.solve(graph(nodes), AD_HOC);
        assertEquals(3, result.used());
        assertFalse(result.hasLane(at(new BlockPos(0, 3, 0))));
    }

    @Test
    void bondedUplinksAddUpAndShedLowPriorityFirst() {
        // Controller block at x=0; two 8-lane cables, each into its own block of a rack at x=2 (master y=0, part y=1).
        RackNode bonded = rack(2, 0, 0, List.of(new RackNode.LaneDemand(1, 8, 2), new RackNode.LaneDemand(2, 8, 1), new RackNode.LaneDemand(3, 8, 0)));
        NetworkNode[] both = { controller(0, 0, 0, 1), controller(0, 1, 0, 1), cable(1, 0, 0, CABLE), cable(1, 1, 0, CABLE), bonded,
                part(2, 1, 0, bonded.pos()) };
        LaneResult result = LaneSolver.solve(graph(both), AD_HOC);
        RackLanes lanes = result.rack(at(bonded.pos()));
        assertEquals(16, lanes.available());
        assertEquals(2, lanes.activeUplinks());
        // Two of three fit; the Low one is shed.
        assertEquals(Set.of(2, 3), lanes.granted());
        assertTrue(lanes.degraded());

        // One cable gone: only the High one is left.
        NetworkNode[] one = { controller(0, 0, 0, 1), controller(0, 1, 0, 1), cable(1, 0, 0, CABLE), bonded, part(2, 1, 0, bonded.pos()) };
        assertEquals(Set.of(3), LaneSolver.solve(graph(one), AD_HOC).rack(at(bonded.pos())).granted());
    }

    @Test
    void sameKindShedsTheLowestUnitFirst() {
        RackNode bonded = rack(2, 0, 0, demands(3, 4, 1));
        NetworkNode[] nodes = { controller(0, 0, 0, 1), cable(1, 0, 0, CABLE), bonded };
        assertEquals(Set.of(2, 3), LaneSolver.solve(graph(nodes), AD_HOC).rack(at(bonded.pos())).granted());
    }

    @Test
    void cutCableStubIsADownUplink() {
        RackNode bonded = rack(2, 0, 0, demands(1, 1, 1));
        // The cable at (1,1,0) leads nowhere (its other end was cut).
        NetworkNode[] nodes = { controller(0, 0, 0, 1), cable(1, 0, 0, CABLE), bonded, part(2, 1, 0, bonded.pos()), cable(3, 1, 0, CABLE) };
        RackLanes lanes = LaneSolver.solve(graph(nodes), AD_HOC).rack(at(bonded.pos()));
        assertEquals(2, lanes.uplinks().size());
        assertEquals(1, lanes.activeUplinks());
        assertTrue(lanes.degraded());
        assertEquals(Set.of(1), lanes.granted());
    }

    @Test
    void rackControllerConflicts() {
        RackNode.RackController two = new RackNode.RackController(20, 2, 192, true), four = new RackNode.RackController(20, 4, 384, true);
        // With a controller block.
        RackNode a = rack(2, 0, 0, List.of(), two);
        assertEquals(NetworkStatus.CONFLICT, LaneSolver.solve(graph(controller(0, 0, 0, 1), cable(1, 0, 0, CABLE), a), AD_HOC).status());
        // A 2U with a 4U.
        RackNode b = rack(4, 0, 0, List.of(), four);
        assertEquals(NetworkStatus.CONFLICT, LaneSolver.solve(graph(a, cable(3, 0, 0, CABLE), b), AD_HOC).status());
        // Two 2Us pair up; a third is a conflict.
        RackNode c = rack(4, 0, 0, List.of(), two);
        assertEquals(NetworkStatus.ONLINE, LaneSolver.solve(graph(a, cable(3, 0, 0, CABLE), c), AD_HOC).status());
        RackNode d = rack(6, 0, 0, List.of(), two);
        assertEquals(NetworkStatus.CONFLICT, LaneSolver.solve(graph(a, cable(3, 0, 0, CABLE), c, cable(5, 0, 0, CABLE), d), AD_HOC).status());
    }

    @Test
    void pairSharesOneBudget() {
        RackNode.RackController two = new RackNode.RackController(20, 2, 3, true);
        RackNode a = rack(0, 0, 0, demands(2, 1, 1), two), b = rack(2, 0, 0, demands(2, 1, 1), two);
        LaneResult result = LaneSolver.solve(graph(a, cable(1, 0, 0, CABLE), b), AD_HOC);
        assertEquals(3, result.capacity());
        assertEquals(3, result.used());
    }

    @Test
    void adHocRackIsAllOrNone() {
        RackNode fits = rack(0, 0, 0, demands(8, 1, 1)), over = rack(0, 0, 0, demands(9, 1, 1));
        assertEquals(8, LaneSolver.solve(graph(fits), AD_HOC).rack(at(fits.pos())).granted().size());
        assertTrue(LaneSolver.solve(graph(over), AD_HOC).rack(at(over.pos())).granted().isEmpty());
    }

    @Test
    void cableOnToTheNextDeviceIsNotAnUplink() {
        // Controller - cable - rack (master y=0, part y=1) - cable from its top - a device beyond: the rack isn't degraded.
        RackNode bonded = rack(2, 0, 0, demands(1, 1, 1));
        NetworkNode[] nodes = { controller(0, 0, 0, 1), cable(1, 0, 0, CABLE), bonded, part(2, 1, 0, bonded.pos()), cable(2, 2, 0, CABLE),
                device(2, 3, 0) };
        LaneResult result = LaneSolver.solve(graph(nodes), AD_HOC);
        RackLanes lanes = result.rack(at(bonded.pos()));
        assertEquals(1, lanes.uplinks().size());
        assertFalse(lanes.degraded());
        assertTrue(result.hasLane(at(new BlockPos(2, 3, 0))), "Device beyond the rack lost its lane");
    }

    @Test
    void secondCableCountsEvenWhenItsShortestWayIsThroughTheRack() {
        // A second cable from the rack's top runs the long way round to the controller: still an uplink, bonded.
        RackNode bonded = rack(2, 0, 0, demands(3, 8, 1));
        NetworkNode[] nodes = { controller(0, 0, 0, 1), cable(1, 0, 0, CABLE), bonded, part(2, 1, 0, bonded.pos()), cable(2, 2, 0, CABLE),
                cable(1, 2, 0, CABLE), cable(0, 2, 0, CABLE), cable(0, 1, 0, CABLE) };
        RackLanes lanes = LaneSolver.solve(graph(nodes), AD_HOC).rack(at(bonded.pos()));
        assertEquals(2, lanes.activeUplinks());
        assertEquals(2, lanes.granted().size());
    }
}
