/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// AE2-style lane assignment, as a pure function of the graph.
//
// Sources: every controller block that has a link to the rest of the network (each such face adds its lanes to the
// network's capacity), and every Server Rack holding a usable rack Network Controller (all of the rack's blocks; the
// controller's lanes are one budget shared by everything drawing from the rack sources, a redundant pair included). A
// multi-source BFS from them builds a shortest-path tree (ties broken by Direction order DOWN, UP, NORTH, SOUTH, WEST,
// EAST, then remote links in NetworkGraph.ORDER, sources in that order too, so assignment is stable between reloads).
// Devices are then visited nearest first (ties by position); each walks its tree path back to a source and gets a lane
// only if every link on the path (and the rack budget, ending at a rack source) has room for it, otherwise it is missing
// a lane (still connected, but inactive).
//
// A Server Rack is bonded: its demands (RackNode) go one by one, by priority (High first; within one, the highest unit
// first, so the lowest units are shed first), each whole on the first of the rack's uplinks that has room along its
// path. An uplink is a link from the rack to something outside it that reaches a source without coming back through
// the rack (its own shortest path, worked out with the rack left out); a link that only leads on to other devices (a
// cable on to the next rack) isn't one, and a link that leads to nothing at all (a cut cable's stub) is a down uplink.
// A rack source's own demands take lanes straight from the budget. Lanes passing through a rack to something beyond it
// go along the tree as before.
//
// Each rack's "rack left out" paths come from one search shared by the whole solve (RackSearch): the paths every rack
// would get if it weren't there are the shared ones, unless some node's path runs through the rack (a cable bus over the
// tops carrying lanes on through it). Only those racks get a search of their own, and it stops as soon as it has reached
// every one of the rack's uplinks. Either way the paths are exactly the ones a full search without the rack finds
// (LaneSolverEquivalenceTest checks the results against the solver as it was), at a fraction of the cost: a network of
// 50 racks no longer searches the whole graph 50 times.
//
// A block that's a controller on its own (SourceNode: a Midrange System) is a source too: its lanes are one budget for
// everything drawing on it.
//
// With no controller the network is ad hoc: up to adHocLimit lanes work with no routing limits; more and none do.
// Conflict (no device gets a lane): controller blocks from two or more structures (a Midrange System is one of its own);
// a controller structure and a rack controller; more than two rack controllers; or two of different sizes.
public final class LaneSolver {
    private LaneSolver() {}

    public static LaneResult solve(NetworkGraph graph, int adHocLimit) {
        List<NodePos> devices = new ArrayList<>();
        List<NodePos> controllers = new ArrayList<>(), ownSources = new ArrayList<>();
        Map<NodePos, RackNode> racks = new HashMap<>();
        Set<Long> groups = new HashSet<>();
        Set<Integer> rackSizes = new HashSet<>();
        int rackControllers = 0;
        for (Map.Entry<NodePos, NetworkNode> entry : graph.entries().entrySet()) {
            NodePos pos = entry.getKey();
            NetworkNode node = entry.getValue();
            if (node.isController()) {
                controllers.add(pos);
                groups.add(node.controllerGroup());
            } else if (node.sourceStructure() != NetworkNode.NO_CONTROLLER) {
                ownSources.add(pos);
                groups.add(node.sourceStructure());
            } else if (node.isDevice()) {
                devices.add(pos);
            }
            if (node instanceof RackNode rack) {
                racks.put(pos, rack);
                rackControllers += rack.controllers().size();
                rack.controllers().forEach(controller -> rackSizes.add(controller.size()));
            }
        }
        devices.sort(NetworkGraph.ORDER);
        controllers.sort(NetworkGraph.ORDER);
        ownSources.sort(NetworkGraph.ORDER);

        if (groups.size() > 1 || !groups.isEmpty() && rackControllers > 0 || rackControllers > 2 || rackSizes.size() > 1) {
            return new LaneResult(NetworkStatus.CONFLICT, false, 0, 0, allDevices(devices, false), Map.of());
        }
        if (groups.isEmpty() && rackControllers == 0) {
            int needed = devices.stream().mapToInt(pos -> graph.node(pos).laneCost()).sum();
            boolean fits = needed <= adHocLimit;
            Map<NodePos, RackLanes> rackLanes = new HashMap<>();
            racks.forEach((pos, rack) -> rackLanes.put(pos, allOrNone(rack, fits)));
            return new LaneResult(fits ? NetworkStatus.ONLINE : NetworkStatus.ADHOC_OVERLOAD, true, adHocLimit, fits ? needed : 0,
                    allDevices(devices, fits), Map.of(), rackLanes);
        }

        // Which rack each rack block belongs to.
        Map<NodePos, NodePos> rackOf = new HashMap<>();
        graph.entries().forEach((pos, node) -> {
            BlockPos master = node.rackMaster();
            if (master != null) {
                rackOf.put(pos, NodePos.of(pos.dimension(), master));
            }
        });

        Map<NodePos, NetworkLink> parent = new HashMap<>();
        Map<NodePos, Integer> distance = new HashMap<>();
        ArrayDeque<NodePos> queue = new ArrayDeque<>();
        int capacity = 0;
        for (NodePos controller : controllers) {
            boolean source = false;
            NetworkNode node = graph.node(controller);
            for (Direction side : Direction.values()) {
                NetworkLink link = graph.link(controller, side);
                NetworkNode other = link != null ? graph.node(link.other(controller)) : null;
                if (other != null && !other.isController()) {
                    capacity += node.laneCapacity();
                    source = true;
                }
            }
            if (source) {
                distance.put(controller, 0);
                queue.add(controller);
            }
        }
        // Blocks that are controllers on their own: each its own budget {lanes, used}.
        Map<NodePos, int[]> budgets = new HashMap<>();
        for (NodePos pos : ownSources) {
            int lanes = graph.node(pos).sourceLanes();
            budgets.put(pos, new int[] { lanes, 0 });
            capacity += lanes;
            distance.put(pos, 0);
            queue.add(pos);
        }
        // Rack sources: every block of a rack with a usable controller.
        Set<NodePos> rackSources = new HashSet<>();
        int budget = 0;
        for (Map.Entry<NodePos, RackNode> entry : racks.entrySet()) {
            for (RackNode.RackController controller : entry.getValue().controllers()) {
                if (controller.usable()) {
                    rackSources.add(entry.getKey());
                    budget = Math.max(budget, controller.lanes());
                }
            }
        }
        if (!rackSources.isEmpty()) {
            capacity += budget;
            List<NodePos> sourceBlocks = new ArrayList<>();
            rackOf.forEach((pos, rack) -> {
                if (rackSources.contains(rack)) {
                    sourceBlocks.add(pos);
                }
            });
            sourceBlocks.sort(NetworkGraph.ORDER);
            for (NodePos pos : sourceBlocks) {
                distance.put(pos, 0);
                queue.add(pos);
            }
        }
        while (!queue.isEmpty()) {
            NodePos at = queue.poll();
            int next = distance.get(at) + 1;
            for (NetworkLink link : links(graph, at)) {
                NodePos neighbour = link.other(at);
                NetworkNode other = graph.node(neighbour);
                if (other == null || other.isController() || distance.containsKey(neighbour)) {
                    continue;
                }
                distance.put(neighbour, next);
                parent.put(neighbour, link);
                queue.add(neighbour);
            }
        }

        RackSearch search = new RackSearch(graph, rackOf, distance);
        devices.sort(Comparator.<NodePos>comparingInt(d -> distance.getOrDefault(d, Integer.MAX_VALUE)).thenComparing(NetworkGraph.ORDER));
        Map<NodePos, Boolean> lanes = new HashMap<>();
        Map<NetworkLink, Integer> usage = new HashMap<>();
        Map<NodePos, RackLanes> rackLanes = new HashMap<>();
        int[] budgetUsed = { 0 };
        int used = 0;
        for (NodePos device : devices) {
            NetworkNode node = graph.node(device);
            if (node instanceof RackNode rack) {
                RackLanes result = rackSources.contains(device) ? sourceRack(rack, budget, budgetUsed)
                        : bondedRack(search, device, rack, rackOf, rackSources, usage, budget, budgetUsed, budgets);
                rackLanes.put(device, result);
                lanes.put(device, result.source() || result.activeUplinks() > 0);
                used += result.used();
                continue;
            }
            int cost = node.laneCost();
            Path path = pathToSource(device, parent, distance);
            boolean fits = path != null && fits(path, cost, usage, rackOf, rackSources, budget, budgetUsed, budgets);
            if (fits) {
                take(path, cost, usage, rackOf, rackSources, budgetUsed, budgets);
                used += cost;
            }
            lanes.put(device, fits);
        }
        // A source rack's uplinks, once everything has drawn through them.
        for (NodePos source : rackSources) {
            RackLanes result = rackLanes.get(source);
            if (result == null) {
                continue;
            }
            List<RackLanes.Uplink> uplinks = new ArrayList<>();
            for (RackLink link : search.rackLinks(source)) {
                uplinks.add(new RackLanes.Uplink(link.outside(), shown(link.link()), usage.getOrDefault(link.link(), 0), true));
            }
            rackLanes.put(source, new RackLanes(true, result.granted(), result.used(), result.available(), result.total(), uplinks, result.shed()));
        }
        return new LaneResult(NetworkStatus.ONLINE, false, capacity, used, lanes, usage, rackLanes);
    }

    // A link from one of a rack's blocks to something outside it.
    private record RackLink(NetworkLink link, NodePos outside) {}

    // What every rack's uplink search shares within one solve: each rack's blocks, its links to the outside, and the
    // reference search: breadth-first from the sources (in order, as a rack's own search goes), every node's link toward
    // a source, its distance, its place in the search order and which of its parent's links reached it.
    //
    // A rack's own search (the sources' paths with the rack left out) differs from the reference only for the rack's
    // subtree: the nodes whose reference path runs through the rack. Everything else keeps its distance and its link:
    // leaving nodes out never shortens a path, and in a breadth-first search the nodes that survive keep their order
    // among themselves, so a node outside the subtree is still reached first by the node that reached it before. So a
    // rack nobody's path runs through takes the reference as it is, as does one whose uplinks all lead outside its
    // subtree; otherwise only the subtree is searched again, in the order the full search would reach it (each node by
    // the earliest node of the level before, the order decided as the full search decides it: by its parents' order,
    // then the link).
    private static final class RackSearch {
        private final NetworkGraph graph;
        private final Map<NodePos, NodePos> rackOf;
        private final Map<NodePos, List<NodePos>> blocks = new HashMap<>();
        private final Map<NodePos, List<RackLink>> links = new HashMap<>();
        private final List<NodePos> sources = new ArrayList<>();
        // The reference search, made the first time a bonded rack needs it.
        private @Nullable Paths reference;
        private final Map<NodePos, Integer> rank = new HashMap<>(), linkIndex = new HashMap<>();
        private final Map<NodePos, List<NodePos>> children = new HashMap<>();

        RackSearch(NetworkGraph graph, Map<NodePos, NodePos> rackOf, Map<NodePos, Integer> distance) {
            this.graph = graph;
            this.rackOf = rackOf;
            // Each rack's blocks, in the order a rack's links were always gathered in.
            rackOf.forEach((block, rack) -> blocks.computeIfAbsent(rack, key -> new ArrayList<>()).add(block));
            distance.forEach((pos, d) -> {
                if (d == 0) {
                    sources.add(pos);
                }
            });
            sources.sort(NetworkGraph.ORDER);
        }

        // A rack's links to the outside, in a stable order.
        List<RackLink> rackLinks(NodePos master) {
            return links.computeIfAbsent(master, key -> {
                List<RackLink> result = new ArrayList<>();
                for (NodePos block : blocks.getOrDefault(master, List.of())) {
                    for (NetworkLink link : links(graph, block)) {
                        NodePos outside = link.other(block);
                        if (!master.equals(rackOf.get(outside))) {
                            result.add(new RackLink(link, outside));
                        }
                    }
                }
                result.sort(Comparator.comparing(RackLink::outside, NetworkGraph.ORDER));
                return result;
            });
        }

        // The paths to the sources with a (non-source) rack left out, for following back from each of targets.
        Paths without(NodePos master, List<NodePos> targets) {
            Paths reference = reference();
            Set<NodePos> rackBlocks = new HashSet<>(blocks.getOrDefault(master, List.of()));
            // The rack's subtree.
            Set<NodePos> subtree = new HashSet<>();
            ArrayDeque<NodePos> queue = new ArrayDeque<>();
            for (NodePos block : rackBlocks) {
                queue.addAll(children.getOrDefault(block, List.of()));
            }
            while (!queue.isEmpty()) {
                NodePos at = queue.poll();
                // (The rack's own blocks reach each other: they're left out, not part of it.)
                if (!rackBlocks.contains(at) && subtree.add(at)) {
                    queue.addAll(children.getOrDefault(at, List.of()));
                }
            }
            boolean needed = false;
            for (NodePos target : targets) {
                needed |= subtree.contains(target);
            }
            if (!needed) {
                // Every uplink's path back (outside the subtree, so its whole path is) is the reference's.
                return reference;
            }
            return reroute(reference, rackBlocks, subtree);
        }

        // The subtree's nodes searched again without the rack, in the full search's order.
        private Paths reroute(Paths reference, Set<NodePos> rackBlocks, Set<NodePos> subtree) {
            Map<NodePos, NetworkLink> parent = new HashMap<>();
            Map<NodePos, Integer> distance = new HashMap<>(), index = new HashMap<>();
            record Offer(NodePos node, NodePos from, NetworkLink link, int level, int index) {}
            PriorityQueue<Offer> offers = new PriorityQueue<>((a, b) -> {
                if (a.level() != b.level()) {
                    return Integer.compare(a.level(), b.level());
                }
                int byFrom = order(a.from(), b.from(), reference, parent, index);
                return byFrom != 0 ? byFrom : Integer.compare(a.index(), b.index());
            });
            // Into the subtree from anything the search reaches outside it (and outside the rack).
            for (NodePos node : subtree) {
                for (NetworkLink link : links(graph, node)) {
                    NodePos from = link.other(node);
                    Integer d = reference.distance(from);
                    if (d != null && !subtree.contains(from) && !rackBlocks.contains(from)) {
                        offers.add(new Offer(node, from, link, d + 1, links(graph, from).indexOf(link)));
                    }
                }
            }
            while (!offers.isEmpty()) {
                Offer offer = offers.poll();
                if (distance.containsKey(offer.node())) {
                    continue;
                }
                distance.put(offer.node(), offer.level());
                parent.put(offer.node(), offer.link());
                index.put(offer.node(), offer.index());
                List<NetworkLink> out = links(graph, offer.node());
                for (int i = 0; i < out.size(); i++) {
                    NodePos next = out.get(i).other(offer.node());
                    if (subtree.contains(next) && !distance.containsKey(next)) {
                        offers.add(new Offer(next, offer.node(), out.get(i), offer.level() + 1, i));
                    }
                }
            }
            return new Paths() {
                @Override
                public @Nullable NetworkLink parent(NodePos node) {
                    return subtree.contains(node) || rackBlocks.contains(node) ? parent.get(node) : reference.parent(node);
                }

                @Override
                public @Nullable Integer distance(NodePos node) {
                    return subtree.contains(node) || rackBlocks.contains(node) ? distance.get(node) : reference.distance(node);
                }
            };
        }

        // The search order of two nodes at the same distance: by their parents' order, then by which of the parent's
        // links reached each. Nodes outside the subtree keep the reference's order among themselves.
        private int order(NodePos a, NodePos b, Paths reference, Map<NodePos, NetworkLink> parent, Map<NodePos, Integer> index) {
            while (!a.equals(b)) {
                NetworkLink linkA = parent.get(a), linkB = parent.get(b);
                if (linkA == null && linkB == null) {
                    return Integer.compare(rank.get(a), rank.get(b));
                }
                int indexA = linkA != null ? index.get(a) : linkIndex.get(a), indexB = linkB != null ? index.get(b) : linkIndex.get(b);
                NodePos parentA = (linkA != null ? linkA : reference.parent(a)).other(a);
                NodePos parentB = (linkB != null ? linkB : reference.parent(b)).other(b);
                if (parentA.equals(parentB)) {
                    return Integer.compare(indexA, indexB);
                }
                a = parentA;
                b = parentB;
            }
            return 0;
        }

        private Paths reference() {
            if (reference != null) {
                return reference;
            }
            Map<NodePos, NetworkLink> parent = new HashMap<>();
            Map<NodePos, Integer> distance = new HashMap<>();
            ArrayDeque<NodePos> queue = new ArrayDeque<>();
            for (NodePos source : sources) {
                distance.put(source, 0);
                queue.add(source);
            }
            int order = 0;
            while (!queue.isEmpty()) {
                NodePos at = queue.poll();
                rank.put(at, order++);
                List<NetworkLink> out = links(graph, at);
                for (int i = 0; i < out.size(); i++) {
                    NodePos next = out.get(i).other(at);
                    NetworkNode node = graph.node(next);
                    if (node == null || node.isController() || distance.containsKey(next)) {
                        continue;
                    }
                    distance.put(next, distance.get(at) + 1);
                    parent.put(next, out.get(i));
                    linkIndex.put(next, i);
                    children.computeIfAbsent(at, key -> new ArrayList<>()).add(next);
                    queue.add(next);
                }
            }
            reference = new Paths() {
                @Override
                public @Nullable NetworkLink parent(NodePos node) {
                    return parent.get(node);
                }

                @Override
                public @Nullable Integer distance(NodePos node) {
                    return distance.get(node);
                }
            };
            return reference;
        }
    }

    // A search's result: each reached node's link toward a source and its distance from one (null: not reached).
    private interface Paths {
        @Nullable NetworkLink parent(NodePos node);

        @Nullable Integer distance(NodePos node);
    }

    // A link's lanes as shown: 0 for one with no limit (straight into a device).
    private static int shown(NetworkLink link) {
        return link.capacity() == NetworkNode.UNLIMITED ? 0 : link.capacity();
    }

    // A rack with a controller: its demands take lanes from the budget, by priority. Its uplinks are what the others
    // draw through; each is up while there's anything beyond it.
    private static RackLanes sourceRack(RackNode rack, int budget, int[] budgetUsed) {
        Set<Integer> granted = new HashSet<>();
        int used = 0;
        boolean shed = false;
        for (RackNode.LaneDemand demand : ordered(rack.demands())) {
            if (budgetUsed[0] + demand.cost() <= budget) {
                budgetUsed[0] += demand.cost();
                used += demand.cost();
                granted.add(demand.u());
            } else {
                shed = true;
            }
        }
        return new RackLanes(true, granted, used, budget, budget, List.of(), shed);
    }

    private static RackLanes bondedRack(RackSearch search, NodePos master, RackNode rack, Map<NodePos, NodePos> rackOf, Set<NodePos> rackSources,
            Map<NetworkLink, Integer> usage, int budget, int[] budgetUsed, Map<NodePos, int[]> budgets) {
        NetworkGraph graph = search.graph;
        // Shortest paths to the sources with this rack left out.
        List<RackLink> rackLinks = search.rackLinks(master);
        List<NodePos> outsides = new ArrayList<>(rackLinks.size());
        rackLinks.forEach(link -> outsides.add(link.outside()));
        Paths without = search.without(master, outsides);
        // The rack's uplinks, each with its way to a source (null: down); links leading only on to other devices left out.
        record Candidate(NetworkLink link, NodePos outside, @Nullable Path path) {}
        List<Candidate> candidates = new ArrayList<>();
        for (RackLink link : rackLinks) {
            Path path = pathToSource(link.outside(), without);
            if (path != null || !leadsToDevices(graph, link.outside(), master, rackOf)) {
                candidates.add(new Candidate(link.link(), link.outside(), path));
            }
        }

        Map<NetworkLink, Integer> own = new HashMap<>();
        Set<Integer> granted = new HashSet<>();
        int used = 0;
        boolean shed = false;
        boolean reachable = candidates.stream().anyMatch(candidate -> candidate.path() != null);
        if (reachable) {
            for (RackNode.LaneDemand demand : ordered(rack.demands())) {
                boolean placed = demand.cost() == 0;
                for (int i = 0; i < candidates.size() && !placed; i++) {
                    Candidate candidate = candidates.get(i);
                    if (candidate.path() == null) {
                        continue;
                    }
                    Path path = candidate.path().prepend(candidate.link());
                    if (fits(path, demand.cost(), usage, rackOf, rackSources, budget, budgetUsed, budgets)) {
                        take(path, demand.cost(), usage, rackOf, rackSources, budgetUsed, budgets);
                        own.merge(candidate.link(), demand.cost(), Integer::sum);
                        placed = true;
                    }
                }
                if (placed) {
                    granted.add(demand.u());
                    used += demand.cost();
                } else {
                    shed = true;
                }
            }
        }
        List<RackLanes.Uplink> uplinks = new ArrayList<>();
        int available = 0, total = 0;
        for (Candidate candidate : candidates) {
            boolean active = candidate.path() != null;
            int linkCapacity = shown(candidate.link());
            uplinks.add(new RackLanes.Uplink(candidate.outside(), linkCapacity, own.getOrDefault(candidate.link(), 0), active));
            total += linkCapacity;
            if (active) {
                available += linkCapacity;
            }
        }
        return new RackLanes(false, granted, used, available, total, uplinks, shed);
    }

    // High priority first, then the highest unit (the lowest units are shed first).
    private static List<RackNode.LaneDemand> ordered(List<RackNode.LaneDemand> demands) {
        List<RackNode.LaneDemand> ordered = new ArrayList<>(demands);
        ordered.sort(Comparator.comparingInt(RackNode.LaneDemand::priority).thenComparing(Comparator.comparingInt(RackNode.LaneDemand::u).reversed()));
        return ordered;
    }

    // Whether anything that uses the network lies beyond a node, the rack left out: then a link to it carries the network
    // on (to the next rack, a device), rather than being a stub that leads nowhere.
    private static boolean leadsToDevices(NetworkGraph graph, NodePos start, NodePos rack, Map<NodePos, NodePos> rackOf) {
        Set<NodePos> seen = new HashSet<>();
        ArrayDeque<NodePos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            NodePos at = queue.poll();
            NetworkNode node = graph.node(at);
            if (node != null && (node.isDevice() || !node.parts().isEmpty())) {
                return true;
            }
            for (NetworkLink link : links(graph, at)) {
                NodePos next = link.other(at);
                if (!rack.equals(rackOf.get(next)) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private static boolean fits(Path path, int cost, Map<NetworkLink, Integer> usage, Map<NodePos, NodePos> rackOf, Set<NodePos> rackSources, int budget,
            int[] budgetUsed, Map<NodePos, int[]> budgets) {
        for (NetworkLink link : path.links()) {
            if (usage.getOrDefault(link, 0) + cost > link.capacity()) {
                return false;
            }
        }
        int[] own = budgets.get(path.end());
        if (own != null && own[1] + cost > own[0]) {
            return false;
        }
        return !endsAtRack(path, rackOf, rackSources) || budgetUsed[0] + cost <= budget;
    }

    private static void take(Path path, int cost, Map<NetworkLink, Integer> usage, Map<NodePos, NodePos> rackOf, Set<NodePos> rackSources,
            int[] budgetUsed, Map<NodePos, int[]> budgets) {
        for (NetworkLink link : path.links()) {
            usage.merge(link, cost, Integer::sum);
        }
        int[] own = budgets.get(path.end());
        if (own != null) {
            own[1] += cost;
        }
        if (endsAtRack(path, rackOf, rackSources)) {
            budgetUsed[0] += cost;
        }
    }

    private static boolean endsAtRack(Path path, Map<NodePos, NodePos> rackOf, Set<NodePos> rackSources) {
        NodePos rack = rackOf.get(path.end());
        return rack != null && rackSources.contains(rack);
    }

    // A node's links in search order: its neighbours by Direction, then its remote links.
    private static List<NetworkLink> links(NetworkGraph graph, NodePos at) {
        List<NetworkLink> links = new ArrayList<>(8);
        for (Direction side : Direction.values()) {
            NetworkLink link = graph.link(at, side);
            if (link != null) {
                links.add(link);
            }
        }
        links.addAll(graph.remoteLinks(at));
        return links;
    }

    // Links from a node back to a source along the BFS tree, and the source it ends at.
    private record Path(List<NetworkLink> links, NodePos end) {
        Path prepend(NetworkLink link) {
            List<NetworkLink> longer = new ArrayList<>(links.size() + 1);
            longer.add(link);
            longer.addAll(links);
            return new Path(longer, end);
        }
    }

    // The same along a search's paths (null when it can't reach one).
    private static @Nullable Path pathToSource(NodePos device, Paths paths) {
        Integer d = paths.distance(device);
        if (d == null) {
            return null;
        }
        List<NetworkLink> path = new ArrayList<>(d);
        NodePos at = device;
        while (paths.distance(at) > 0) {
            NetworkLink link = paths.parent(at);
            path.add(link);
            at = link.other(at);
        }
        return new Path(path, at);
    }

    // Null when it can't reach one.
    private static @Nullable Path pathToSource(NodePos device, Map<NodePos, NetworkLink> parent, Map<NodePos, Integer> distance) {
        if (!distance.containsKey(device)) {
            return null;
        }
        List<NetworkLink> path = new ArrayList<>();
        NodePos at = device;
        while (distance.get(at) > 0) {
            NetworkLink link = parent.get(at);
            path.add(link);
            at = link.other(at);
        }
        return new Path(path, at);
    }

    private static RackLanes allOrNone(RackNode rack, boolean fits) {
        Set<Integer> granted = new HashSet<>();
        if (fits) {
            rack.demands().forEach(demand -> granted.add(demand.u()));
        }
        return new RackLanes(false, granted, fits ? rack.laneCost() : 0, 0, 0, List.of(), !fits && !rack.demands().isEmpty());
    }

    private static Map<NodePos, Boolean> allDevices(List<NodePos> devices, boolean hasLane) {
        Map<NodePos, Boolean> lanes = new HashMap<>();
        devices.forEach(device -> lanes.put(device, hasLane));
        return lanes;
    }
}
