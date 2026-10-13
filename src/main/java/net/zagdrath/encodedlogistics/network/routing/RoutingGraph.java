/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network.routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.BiPredicate;

import org.jspecify.annotations.Nullable;

// The link-state graph dynamic routing works out paths over: networks (N) joined by hops, each hop carried by one or
// more Routers (C) and costed by how much they can carry (cost). Kept free of the game so the path rules can be tested
// on their own.
//
// A path from a source crosses other networks only where they allow transit for that source (crossable), is at most
// maxHops hops long, and is the cheapest such (fewer hops breaking ties). Its throughput is its narrowest hop's.
public final class RoutingGraph<N, C> {
    // A hop from one network to another (one way; a two-way link is two hops). label is what route tables show as the
    // next hop when it's the first (a network's name, or IX:name for an exchange); carriers the Routers it passes
    // through, which each spend their throughput on what crosses it. A direct hop (an exchange peering that doesn't
    // exchange routes) only carries traffic between its two ends: it's never part of a longer path.
    public record Hop<N, C>(N from, N to, int cost, int throughput, String label, List<C> carriers, boolean direct) {
        public Hop(N from, N to, int cost, int throughput, String label, List<C> carriers) {
            this(from, to, cost, throughput, label, carriers, false);
        }
    }

    // The best path to dest: its hops in order.
    public record Path<N, C>(N dest, int cost, List<Hop<N, C>> hops) {
        public int hopCount() {
            return hops.size();
        }

        public Hop<N, C> first() {
            return hops.getFirst();
        }

        public int throughput() {
            int narrowest = Integer.MAX_VALUE;
            for (Hop<N, C> hop : hops) {
                narrowest = Math.min(narrowest, hop.throughput());
            }
            return narrowest;
        }
    }

    private final Map<N, List<Hop<N, C>>> out = new LinkedHashMap<>();

    public void add(Hop<N, C> hop) {
        if (hop.from().equals(hop.to())) {
            return;
        }
        out.computeIfAbsent(hop.from(), key -> new ArrayList<>()).add(hop);
        out.computeIfAbsent(hop.to(), key -> new ArrayList<>());
    }

    public void addAll(Collection<Hop<N, C>> hops) {
        hops.forEach(this::add);
    }

    public Collection<N> networks() {
        return out.keySet();
    }

    public List<Hop<N, C>> hopsFrom(N network) {
        return out.getOrDefault(network, List.of());
    }

    // A hop's cost: base x 1000 / (what it carries + 1), rounded, at least 1. More throughput (Optical Transceivers) or
    // lanes makes a hop cheaper.
    public static int cost(int base, int lanes, int throughput) {
        long carried = Math.max(0, Math.min(lanes, throughput));
        return (int) Math.max(1, Math.round(base * 1000.0 / (carried + 1)));
    }

    // The best paths from source to every network it can reach. crossable(network, source): whether a path may pass
    // through network (not end there) for traffic from source.
    public Map<N, Path<N, C>> paths(N source, BiPredicate<N, N> crossable, int maxHops) {
        Map<N, Path<N, C>> best = new HashMap<>();
        // A path found but not yet settled: ordered by cost, then hops.
        PriorityQueue<Path<N, C>> queue = new PriorityQueue<>(Comparator.<Path<N, C>>comparingLong(Path::cost).thenComparingInt(Path::hopCount));
        queue.add(new Path<>(source, 0, List.of()));
        Map<N, Path<N, C>> result = new LinkedHashMap<>();
        while (!queue.isEmpty()) {
            Path<N, C> path = queue.poll();
            if (result.containsKey(path.dest())) {
                continue;
            }
            result.put(path.dest(), path);
            boolean expand = path.dest().equals(source) || crossable.test(path.dest(), source) && !path.hops().getLast().direct();
            if (!expand || path.hopCount() >= maxHops) {
                continue;
            }
            for (Hop<N, C> hop : hopsFrom(path.dest())) {
                if (result.containsKey(hop.to()) || hop.direct() && !path.hops().isEmpty()) {
                    continue;
                }
                List<Hop<N, C>> hops = new ArrayList<>(path.hops());
                hops.add(hop);
                Path<N, C> next = new Path<>(hop.to(), (int) Math.min(Integer.MAX_VALUE, (long) path.cost() + hop.cost()), List.copyOf(hops));
                Path<N, C> known = best.get(hop.to());
                if (known == null || next.cost() < known.cost() || next.cost() == known.cost() && next.hopCount() < known.hopCount()) {
                    best.put(hop.to(), next);
                    queue.add(next);
                }
            }
        }
        result.remove(source);
        return result;
    }

    // The best path from source to dest, or null when there's none.
    public @Nullable Path<N, C> path(N source, N dest, BiPredicate<N, N> crossable, int maxHops) {
        return paths(source, crossable, maxHops).get(dest);
    }
}
