/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network.routing;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingGraphTest {
    private static void link(RoutingGraph<String, String> graph, String a, String b, int cost, int throughput, String router) {
        graph.add(new RoutingGraph.Hop<>(a, b, cost, throughput, b, List.of(router)));
        graph.add(new RoutingGraph.Hop<>(b, a, cost, throughput, a, List.of(router)));
    }

    private static boolean anywhere(String network, String source) {
        return true;
    }

    @Test
    void costFollowsThroughput() {
        assertEquals(Math.round(100_000.0 / 17), RoutingGraph.cost(100, Integer.MAX_VALUE, 16));
        assertTrue(RoutingGraph.cost(100, Integer.MAX_VALUE, 64) < RoutingGraph.cost(100, Integer.MAX_VALUE, 16));
        // Lanes narrower than the Router's throughput set the cost.
        assertEquals(RoutingGraph.cost(100, 8, 8), RoutingGraph.cost(100, 8, 4096));
        assertEquals(1, RoutingGraph.cost(1, Integer.MAX_VALUE, 1_000_000));
    }

    @Test
    void cheapestPathWins() {
        RoutingGraph<String, String> graph = new RoutingGraph<>();
        link(graph, "A", "B", 10, 64, "r1");
        link(graph, "B", "C", 10, 64, "r2");
        link(graph, "A", "C", 50, 64, "r3");
        RoutingGraph.Path<String, String> path = graph.path("A", "C", RoutingGraphTest::anywhere, 16);
        assertNotNull(path);
        assertEquals(20, path.cost());
        assertEquals(2, path.hopCount());
        assertEquals("B", path.first().to());
    }

    @Test
    void transitIsRespected() {
        RoutingGraph<String, String> graph = new RoutingGraph<>();
        link(graph, "A", "B", 10, 64, "r1");
        link(graph, "B", "C", 10, 64, "r2");
        // B lets only A's traffic through.
        Set<String> allowed = Set.of("A");
        Map<String, RoutingGraph.Path<String, String>> fromA = graph.paths("A", (network, source) -> allowed.contains(source), 16);
        assertTrue(fromA.containsKey("C"));
        Map<String, RoutingGraph.Path<String, String>> fromC = graph.paths("C", (network, source) -> allowed.contains(source), 16);
        // B itself is reachable from C (a destination needs no transit), A beyond it isn't.
        assertTrue(fromC.containsKey("B"));
        assertFalse(fromC.containsKey("A"));
    }

    @Test
    void maxHopsBoundsPaths() {
        RoutingGraph<String, String> graph = new RoutingGraph<>();
        link(graph, "A", "B", 1, 64, "r1");
        link(graph, "B", "C", 1, 64, "r2");
        link(graph, "C", "D", 1, 64, "r3");
        assertNotNull(graph.path("A", "C", RoutingGraphTest::anywhere, 2));
        assertNull(graph.path("A", "D", RoutingGraphTest::anywhere, 2));
    }

    @Test
    void throughputIsTheNarrowestHop() {
        RoutingGraph<String, String> graph = new RoutingGraph<>();
        link(graph, "A", "B", 1, 64, "r1");
        link(graph, "B", "C", 1, 16, "r2");
        assertEquals(16, graph.path("A", "C", RoutingGraphTest::anywhere, 16).throughput());
    }

    @Test
    void fewerHopsBreakTies() {
        RoutingGraph<String, String> graph = new RoutingGraph<>();
        link(graph, "A", "B", 5, 64, "r1");
        link(graph, "B", "C", 5, 64, "r2");
        link(graph, "A", "C", 10, 64, "r3");
        assertEquals(1, graph.path("A", "C", RoutingGraphTest::anywhere, 16).hopCount());
    }

    @Test
    void directHopsEndPaths() {
        RoutingGraph<String, String> graph = new RoutingGraph<>();
        link(graph, "A", "B", 1, 64, "r1");
        // B and C peer without exchanging routes: only B and C use that hop, between themselves.
        graph.add(new RoutingGraph.Hop<>("B", "C", 1, 64, "IX", List.of("r2", "r3"), true));
        graph.add(new RoutingGraph.Hop<>("C", "B", 1, 64, "IX", List.of("r2", "r3"), true));
        link(graph, "C", "D", 1, 64, "r4");
        assertNotNull(graph.path("B", "C", RoutingGraphTest::anywhere, 16));
        assertNull(graph.path("A", "C", RoutingGraphTest::anywhere, 16));
        assertNull(graph.path("B", "D", RoutingGraphTest::anywhere, 16));
    }
}
