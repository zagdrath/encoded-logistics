/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network.routing;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingDomainTest {
    private static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final NetworkRef A = new NetworkRef(DIM, 1), B = new NetworkRef(DIM, 2), C = new NetworkRef(DIM, 3), D = new NetworkRef(DIM, 4);
    private static final RoutingDomain.RouterId R1 = id(1), R2 = id(2), R3 = id(3);
    private static final int DEAD = 60;

    private static RoutingDomain.RouterId id(int x) {
        return new RoutingDomain.RouterId(DIM, new BlockPos(x, 0, 0), 1);
    }

    private static RoutingDomain.Link link(NetworkRef to) {
        return new RoutingDomain.Link(to, 10, 64, "N" + to.id(), null, false);
    }

    private static RoutingDomain.Link peer(NetworkRef to, RoutingDomain.RouterId peer) {
        return new RoutingDomain.Link(to, 10, 64, "IX:MAIN", peer, false);
    }

    private static RoutingDomain.Lsa lsa(RoutingDomain.RouterId id, NetworkRef home, RoutingDomain.Transit transit, RoutingDomain.Link... links) {
        return new RoutingDomain.Lsa(id, home, "N" + home.id(), "RTR", List.of(links), transit, Set.of(), Set.of(), Map.of(), 64, 0);
    }

    private static Map<NetworkRef, RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId>> paths(RoutingDomain domain, RoutingDomain.RouterId from,
            NetworkRef source) {
        RoutingDomain.View view = RoutingDomain.view(domain.database(from));
        return view.graph().paths(source, view::crossable, 16);
    }

    @Test
    void neighboursComeUpAndSync() {
        RoutingDomain domain = new RoutingDomain();
        // R1 on A links B; R2 on B links C, letting anything through.
        RoutingDomain.Lsa r1 = lsa(R1, A, RoutingDomain.Transit.NONE, link(B)), r2 = lsa(R2, B, RoutingDomain.Transit.ALL, link(C));
        int ups = 0;
        for (long now = 0; now <= 40; now += 20) {
            ups += domain.hello(r1, now, DEAD).size();
            ups += domain.hello(r2, now, DEAD).size();
        }
        assertEquals(2, ups);
        assertEquals(RoutingDomain.State.FULL, domain.neighbours(R1).getFirst().state());
        assertEquals(2, domain.database(R1).size());
        Map<NetworkRef, RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId>> fromA = paths(domain, R1, A);
        assertTrue(fromA.containsKey(C));
        assertEquals(2, fromA.get(C).hopCount());
        // Both Routers carry A to C.
        assertTrue(fromA.get(C).hops().get(1).carriers().contains(R2));
    }

    @Test
    void transitIsTheCrossedRoutersCall() {
        RoutingDomain domain = new RoutingDomain();
        RoutingDomain.Lsa r1 = lsa(R1, A, RoutingDomain.Transit.NONE, link(B)), r2 = lsa(R2, B, RoutingDomain.Transit.NONE, link(C));
        for (long now = 0; now <= 40; now += 20) {
            domain.hello(r1, now, DEAD);
            domain.hello(r2, now, DEAD);
        }
        Map<NetworkRef, RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId>> fromA = paths(domain, R1, A);
        assertTrue(fromA.containsKey(B));
        assertFalse(fromA.containsKey(C));
        // Listed: only the networks named.
        RoutingDomain.Lsa listed = new RoutingDomain.Lsa(R2, B, "N2", "RTR", List.of(link(C)), RoutingDomain.Transit.LISTED, Set.of(A), Set.of(), Map.of(),
                64, 0);
        domain.hello(listed, 60, DEAD);
        assertTrue(paths(domain, R1, A).containsKey(C));
    }

    @Test
    void silentNeighbourGoesDown() {
        RoutingDomain domain = new RoutingDomain();
        RoutingDomain.Lsa r1 = lsa(R1, A, RoutingDomain.Transit.NONE, link(B)), r2 = lsa(R2, B, RoutingDomain.Transit.ALL, link(C));
        for (long now = 0; now <= 40; now += 20) {
            domain.hello(r1, now, DEAD);
            domain.hello(r2, now, DEAD);
        }
        // R2 says no more hellos: three missed and it's down, its networks beyond gone from R1's database.
        List<RoutingDomain.Change> changes = List.of();
        for (long now = 60; now <= 120; now += 20) {
            changes = domain.hello(r1, now, DEAD);
            if (!changes.isEmpty()) {
                break;
            }
        }
        assertEquals(1, changes.size());
        assertFalse(changes.getFirst().up());
        assertEquals(RoutingDomain.State.DOWN, domain.neighbours(R1).getFirst().state());
        assertFalse(paths(domain, R1, A).containsKey(C));
        // Its own link still reaches B.
        assertTrue(paths(domain, R1, A).containsKey(B));
    }

    @Test
    void staticModeLeavesAtOnce() {
        RoutingDomain domain = new RoutingDomain();
        RoutingDomain.Lsa r1 = lsa(R1, A, RoutingDomain.Transit.NONE, link(B)), r2 = lsa(R2, B, RoutingDomain.Transit.ALL, link(C));
        for (long now = 0; now <= 40; now += 20) {
            domain.hello(r1, now, DEAD);
            domain.hello(r2, now, DEAD);
        }
        int version = domain.version();
        domain.remove(R2);
        assertTrue(domain.version() > version);
        List<RoutingDomain.Change> changes = domain.hello(r1, 60, DEAD);
        assertEquals(1, changes.size());
        assertTrue(domain.neighbours(R1).isEmpty());
    }

    @Test
    void peersAtAnExchange() {
        RoutingDomain domain = new RoutingDomain();
        // R1 (A) and R3 (D) peer; a peering counts once both advertise it.
        RoutingDomain.Lsa r1 = lsa(R1, A, RoutingDomain.Transit.NONE, peer(D, R3)), r3 = lsa(R3, D, RoutingDomain.Transit.NONE);
        for (long now = 0; now <= 40; now += 20) {
            domain.hello(r1, now, DEAD);
            domain.hello(r3, now, DEAD);
        }
        assertFalse(paths(domain, R1, A).containsKey(D));
        r3 = lsa(R3, D, RoutingDomain.Transit.NONE, peer(A, R1));
        domain.hello(r3, 60, DEAD);
        RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId> path = paths(domain, R1, A).get(D);
        assertEquals("IX:MAIN", path.first().label());
        assertEquals(List.of(R1, R3), path.first().carriers());
        assertEquals("IX:MAIN", domain.neighbours(R1).getFirst().via());
    }

    @Test
    void ledgerSharesThroughput() {
        RoutingDomain domain = new RoutingDomain();
        domain.hello(lsa(R1, A, RoutingDomain.Transit.NONE), 0, DEAD);
        domain.charge(R1, 40, 0);
        assertEquals(24, domain.remaining(R1, 0));
        // A new second, a fresh budget.
        assertEquals(64, domain.remaining(R1, 20));
    }
}
