/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network.routing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// Dynamic routing on a server: the Routers in DYNAMIC mode, what each advertises (its LSA), and who is whose neighbour.
//
// Each such Router says hello once a second (hello): that refreshes its LSA (its network, the networks it links to with
// their costs, its exchange peerings, its transit policy; a new sequence number when any of it changes) and steps its
// neighbours. Two Routers are neighbours when one links to the other's network (a Link Card), or both peer at an
// Exchange Switch; a neighbour seen saying hello goes INIT, then FULL once it has seen this one too (their databases are
// in sync), and DOWN after missing three hellos (or fewer, to reconverge within reconvergeSeconds). A Router's database
// is every LSA it can reach through FULL neighbours, so a partition splits it; an LSA not refreshed for a minute ages out
// (an unloaded Router).
//
// Throughput: each Router can carry rate items a second, its own routes' and other Routers' transit alike (the ledger).
public final class RoutingDomain {
    public static final int HELLO_TICKS = 20, LSA_MAX_AGE_TICKS = 60 * 20;
    private static final Map<MinecraftServer, RoutingDomain> DOMAINS = new WeakHashMap<>();

    // A Router: the rack it's in (dimension and position) and its unit.
    public record RouterId(ResourceKey<Level> dimension, BlockPos rack, int u) {}

    public enum Transit {
        NONE, LISTED, ALL
    }

    public enum State {
        INIT, FULL, DOWN
    }

    // A network a Router advertises a hop to. peer: the Router on the far side when it's an exchange peering (both
    // carry it, and it counts only when the peer advertises it back); null for a network it links to itself. direct: a
    // peering that doesn't exchange routes (RoutingGraph.Hop#direct). label: the network's name, or IX:name.
    public record Link(NetworkRef network, int cost, int throughput, String label, @Nullable RouterId peer, boolean direct) {}

    // What a Router advertises. noTransit: networks never let through (exchange peers it refuses transit to).
    // overrides: costs it sets on hops from its network to these (per-neighbour cost overrides).
    public record Lsa(RouterId origin, NetworkRef home, String homeName, String device, List<Link> links, Transit transit, Set<NetworkRef> transitList,
            Set<NetworkRef> noTransit, Map<NetworkRef, Integer> overrides, int rate, long seq) {
        boolean sameAs(Lsa other) {
            return home.equals(other.home) && homeName.equals(other.homeName) && device.equals(other.device) && links.equals(other.links)
                    && transit == other.transit && transitList.equals(other.transitList) && noTransit.equals(other.noTransit)
                    && overrides.equals(other.overrides) && rate == other.rate;
        }

        Lsa withSeq(long seq) {
            return new Lsa(origin, home, homeName, device, links, transit, transitList, noTransit, overrides, rate, seq);
        }

        // Whether traffic from source may cross its network.
        boolean allowsTransit(NetworkRef source) {
            return !noTransit.contains(source) && (transit == Transit.ALL || transit == Transit.LISTED && transitList.contains(source));
        }
    }

    // A neighbour as one Router sees it: how it's reached (Link, IX:name), its network, since when it's in this state.
    public record Neighbour(RouterId id, NetworkRef home, String name, String via, State state, long since, long lastSeen) {
        Neighbour with(State state, long since, long lastSeen) {
            return new Neighbour(id, home, name, via, state, since, lastSeen);
        }
    }

    // What changed for a Router at its hello: a neighbour came up or went down.
    public record Change(Neighbour neighbour, boolean up) {}

    private final Map<RouterId, Lsa> lsas = new HashMap<>();
    private final Map<RouterId, Long> hellos = new HashMap<>();
    private final Map<RouterId, Map<RouterId, Neighbour>> neighbours = new HashMap<>();
    private int version;
    private long ledgerSecond = -1;
    private final Map<RouterId, Integer> used = new HashMap<>();

    public static synchronized RoutingDomain get(MinecraftServer server) {
        return DOMAINS.computeIfAbsent(server, key -> new RoutingDomain());
    }

    // Goes up with every change to any database or adjacency: a Router's paths are only as old as the version they're
    // worked out from.
    public int version() {
        return version;
    }

    // How long a neighbour may go without a hello before it's down: three hellos, or less to reconverge in time.
    public static int deadTicks() {
        int reconverge = Config.ROUTING_RECONVERGE_SECONDS.getAsInt() * 20 - HELLO_TICKS;
        return Math.max(HELLO_TICKS + HELLO_TICKS / 2, Math.min(3 * HELLO_TICKS, reconverge));
    }

    // --- Hellos ---

    // A Router's hello: its LSA refreshed, its neighbours stepped. Returns the neighbours that came up or went down.
    public List<Change> hello(Lsa lsa, long now) {
        return hello(lsa, now, deadTicks());
    }

    // The same with the dead interval given (tests).
    public List<Change> hello(Lsa lsa, long now, int deadTicks) {
        expire(now);
        RouterId self = lsa.origin();
        hellos.put(self, now);
        Lsa old = lsas.get(self);
        if (old == null || !old.sameAs(lsa)) {
            lsas.put(self, lsa.withSeq(old == null ? 1 : old.seq() + 1));
            version++;
        }
        List<Change> changes = new ArrayList<>();
        Map<RouterId, Neighbour> table = neighbours.computeIfAbsent(self, key -> new LinkedHashMap<>());
        Map<RouterId, Neighbour> found = candidates(self);
        for (Neighbour candidate : found.values()) {
            Neighbour known = table.get(candidate.id());
            Long heard = hellos.get(candidate.id());
            boolean alive = heard != null && now - heard <= deadTicks;
            if (alive) {
                if (known == null || known.state() == State.DOWN) {
                    table.put(candidate.id(), candidate.with(State.INIT, now, heard));
                } else if (known.state() == State.INIT && seesUs(candidate.id(), self)) {
                    Neighbour up = candidate.with(State.FULL, now, heard);
                    table.put(candidate.id(), up);
                    changes.add(new Change(up, true));
                    version++;
                } else {
                    table.put(candidate.id(), candidate.with(known.state(), known.since(), heard));
                }
            } else if (known != null && known.state() != State.DOWN) {
                Neighbour down = candidate.with(State.DOWN, now, known.lastSeen());
                table.put(candidate.id(), down);
                if (known.state() == State.FULL) {
                    changes.add(new Change(down, false));
                    version++;
                }
            } else if (known == null) {
                table.put(candidate.id(), candidate.with(State.DOWN, now, heard != null ? heard : now));
            }
        }
        // No longer neighbours at all (unlinked, left the exchange).
        for (Neighbour gone : List.copyOf(table.values())) {
            if (!found.containsKey(gone.id())) {
                table.remove(gone.id());
                if (gone.state() == State.FULL) {
                    changes.add(new Change(gone.with(State.DOWN, now, gone.lastSeen()), false));
                    version++;
                }
            }
        }
        return changes;
    }

    // A Router that left dynamic routing (static mode, taken out, offline): it says no more hellos, and its LSA goes.
    public void remove(RouterId id) {
        hellos.remove(id);
        if (lsas.remove(id) != null) {
            version++;
        }
        Map<RouterId, Neighbour> table = neighbours.remove(id);
        if (table != null && !table.isEmpty()) {
            version++;
        }
    }

    public boolean participates(RouterId id) {
        return lsas.containsKey(id);
    }

    private void expire(long now) {
        for (RouterId id : List.copyOf(lsas.keySet())) {
            Long heard = hellos.get(id);
            if (heard == null || now - heard > LSA_MAX_AGE_TICKS) {
                remove(id);
            }
        }
    }

    // Whether other has us as a neighbour, not down.
    private boolean seesUs(RouterId other, RouterId self) {
        Map<RouterId, Neighbour> table = neighbours.get(other);
        Neighbour us = table != null ? table.get(self) : null;
        return us != null && us.state() != State.DOWN;
    }

    // The Routers that are self's neighbours by their LSAs: one links to the other's network, or they peer.
    private Map<RouterId, Neighbour> candidates(RouterId self) {
        Map<RouterId, Neighbour> found = new LinkedHashMap<>();
        Lsa own = lsas.get(self);
        if (own == null) {
            return found;
        }
        for (Lsa other : lsas.values()) {
            if (other.origin().equals(self) || other.home().equals(own.home())) {
                continue;
            }
            String via = null;
            for (Link link : own.links()) {
                if (link.peer() != null ? link.peer().equals(other.origin()) : link.network().equals(other.home())) {
                    via = link.peer() != null ? link.label() : "Link";
                    break;
                }
            }
            if (via == null) {
                for (Link link : other.links()) {
                    if (link.peer() != null ? link.peer().equals(self) : link.network().equals(own.home())) {
                        via = link.peer() != null ? link.label() : "Link";
                        break;
                    }
                }
            }
            if (via != null) {
                found.put(other.origin(), new Neighbour(other.origin(), other.home(), other.homeName(), via, State.INIT, 0, 0));
            }
        }
        return found;
    }

    // A Router's neighbours as it last saw them.
    public List<Neighbour> neighbours(RouterId id) {
        Map<RouterId, Neighbour> table = neighbours.get(id);
        return table != null ? List.copyOf(table.values()) : List.of();
    }

    public @Nullable Lsa lsa(RouterId id) {
        return lsas.get(id);
    }

    // --- Databases and paths ---

    // The LSAs a Router holds: its own and every one it reaches through FULL neighbours.
    public List<Lsa> database(RouterId id) {
        List<Lsa> held = new ArrayList<>();
        Set<RouterId> seen = new HashSet<>();
        Deque<RouterId> queue = new ArrayDeque<>();
        queue.add(id);
        seen.add(id);
        while (!queue.isEmpty()) {
            RouterId next = queue.poll();
            Lsa lsa = lsas.get(next);
            if (lsa != null) {
                held.add(lsa);
            }
            Map<RouterId, Neighbour> table = neighbours.get(next);
            if (table == null) {
                continue;
            }
            for (Neighbour neighbour : table.values()) {
                if (neighbour.state() == State.FULL && seen.add(neighbour.id())) {
                    queue.add(neighbour.id());
                }
            }
        }
        return held;
    }

    // A database as a graph to find paths over, and who may cross which network.
    public record View(RoutingGraph<NetworkRef, RouterId> graph, Map<NetworkRef, List<Lsa>> byHome, Map<NetworkRef, String> names) {
        public boolean crossable(NetworkRef network, NetworkRef source) {
            List<Lsa> routers = byHome.get(network);
            if (routers == null || routers.isEmpty()) {
                return false;
            }
            for (Lsa lsa : routers) {
                if (!lsa.allowsTransit(source)) {
                    return false;
                }
            }
            return true;
        }

        public String name(NetworkRef network) {
            return names.getOrDefault(network, "");
        }
    }

    public static View view(List<Lsa> database) {
        RoutingGraph<NetworkRef, RouterId> graph = new RoutingGraph<>();
        Map<NetworkRef, List<Lsa>> byHome = new HashMap<>();
        Map<NetworkRef, String> names = new HashMap<>();
        Map<RouterId, Lsa> held = new HashMap<>();
        for (Lsa lsa : database) {
            held.put(lsa.origin(), lsa);
            byHome.computeIfAbsent(lsa.home(), key -> new ArrayList<>()).add(lsa);
            names.put(lsa.home(), lsa.homeName());
        }
        for (Lsa lsa : database) {
            for (Link link : lsa.links()) {
                if (link.peer() == null) {
                    names.putIfAbsent(link.network(), link.label());
                    graph.add(hop(byHome, lsa.home(), link.network(), link, List.of(lsa.origin())));
                    graph.add(hop(byHome, link.network(), lsa.home(), link, List.of(lsa.origin())));
                } else {
                    // A peering counts once both sides advertise it; each side adds its own direction.
                    Lsa peer = held.get(link.peer());
                    if (peer != null && advertises(peer, lsa.origin())) {
                        graph.add(hop(byHome, lsa.home(), peer.home(), link, List.of(lsa.origin(), peer.origin())));
                    }
                }
            }
        }
        return new View(graph, byHome, names);
    }

    private static boolean advertises(Lsa lsa, RouterId peer) {
        for (Link link : lsa.links()) {
            if (peer.equals(link.peer())) {
                return true;
            }
        }
        return false;
    }

    // A hop from one network to another, its cost overridden by a Router on the from side when it sets one.
    private static RoutingGraph.Hop<NetworkRef, RouterId> hop(Map<NetworkRef, List<Lsa>> byHome, NetworkRef from, NetworkRef to, Link link,
            List<RouterId> carriers) {
        Integer overridden = null;
        for (Lsa lsa : byHome.getOrDefault(from, List.of())) {
            Integer override = lsa.overrides().get(to);
            if (override != null) {
                overridden = overridden == null ? override : Math.min(overridden, override);
            }
        }
        int cost = overridden != null ? overridden : link.cost();
        String label = link.peer() != null ? link.label() : "";
        return new RoutingGraph.Hop<>(from, to, cost, link.throughput(), label, carriers, link.direct());
    }

    // --- Throughput ---

    // What a Router has carried this second (its own routes and transit).
    public int used(RouterId id, long now) {
        roll(now);
        return used.getOrDefault(id, 0);
    }

    public void charge(RouterId id, int amount, long now) {
        roll(now);
        used.merge(id, amount, Integer::sum);
    }

    // What a Router can still carry this second.
    public int remaining(RouterId id, long now) {
        Lsa lsa = lsas.get(id);
        return lsa == null ? 0 : Math.max(0, lsa.rate() - used(id, now));
    }

    private void roll(long now) {
        long second = now / HELLO_TICKS;
        if (second != ledgerSecond) {
            ledgerSecond = second;
            used.clear();
        }
    }
}
