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
// first, so the lowest units are shed first), each whole on the first of the rack's uplinks that leads toward a source
// without coming back through the rack and has room along its path. A rack source's own demands take lanes straight
// from the budget. Lanes passing through a rack to something beyond it go along the tree as before.
//
// With no controller the network is ad hoc: up to adHocLimit lanes work with no routing limits; more and none do.
// Conflict (no device gets a lane): controller blocks from two or more structures; a controller structure and a rack
// controller; more than two rack controllers; or two of different sizes.
public final class LaneSolver {
    private LaneSolver() {}

    public static LaneResult solve(NetworkGraph graph, int adHocLimit) {
        List<NodePos> devices = new ArrayList<>();
        List<NodePos> controllers = new ArrayList<>();
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
                        : bondedRack(graph, device, rack, rackOf, rackSources, parent, distance, usage, budget, budgetUsed);
                rackLanes.put(device, result);
                lanes.put(device, result.source() || result.activeUplinks() > 0);
                used += result.used();
                continue;
            }
            int cost = node.laneCost();
            Path path = pathToSource(device, parent, distance);
            boolean fits = path != null && fits(path, cost, usage, rackOf, rackSources, budget, budgetUsed);
            if (fits) {
                take(path, cost, usage, rackOf, rackSources, budgetUsed);
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
            for (RackLink link : rackLinks(graph, source, rackOf)) {
                uplinks.add(new RackLanes.Uplink(link.outside(), shown(link.link()), usage.getOrDefault(link.link(), 0), true));
            }
            rackLanes.put(source, new RackLanes(true, result.granted(), result.used(), result.available(), result.total(), uplinks, result.shed()));
        }
        return new LaneResult(NetworkStatus.ONLINE, false, capacity, used, lanes, usage, rackLanes);
    }

    // A link from one of a rack's blocks to something outside it.
    private record RackLink(NetworkLink link, NodePos outside) {}

    // A rack's links to the outside, in a stable order.
    private static List<RackLink> rackLinks(NetworkGraph graph, NodePos master, Map<NodePos, NodePos> rackOf) {
        List<RackLink> result = new ArrayList<>();
        rackOf.forEach((block, rack) -> {
            if (rack.equals(master)) {
                for (NetworkLink link : links(graph, block)) {
                    NodePos outside = link.other(block);
                    if (!master.equals(rackOf.get(outside))) {
                        result.add(new RackLink(link, outside));
                    }
                }
            }
        });
        result.sort(Comparator.comparing(RackLink::outside, NetworkGraph.ORDER));
        return result;
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

    private static RackLanes bondedRack(NetworkGraph graph, NodePos master, RackNode rack, Map<NodePos, NodePos> rackOf, Set<NodePos> rackSources,
            Map<NodePos, NetworkLink> parent, Map<NodePos, Integer> distance, Map<NetworkLink, Integer> usage, int budget, int[] budgetUsed) {
        // The rack's uplinks, each with its way to a source if it has one.
        record Candidate(NetworkLink link, NodePos outside, @Nullable Path path) {}
        List<Candidate> candidates = new ArrayList<>();
        for (RackLink link : rackLinks(graph, master, rackOf)) {
            candidates.add(new Candidate(link.link(), link.outside(), towardSource(link.outside(), master, rackOf, parent, distance)));
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
                    if (fits(path, demand.cost(), usage, rackOf, rackSources, budget, budgetUsed)) {
                        take(path, demand.cost(), usage, rackOf, rackSources, budgetUsed);
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

    // The tree path from a node outside a rack back to a source, unless it runs back through that rack.
    private static @Nullable Path towardSource(NodePos outside, NodePos rack, Map<NodePos, NodePos> rackOf, Map<NodePos, NetworkLink> parent,
            Map<NodePos, Integer> distance) {
        Path path = pathToSource(outside, parent, distance);
        if (path == null) {
            return null;
        }
        NodePos at = outside;
        for (NetworkLink link : path.links()) {
            at = link.other(at);
            if (rack.equals(rackOf.get(at))) {
                return null;
            }
        }
        return path;
    }

    private static boolean fits(Path path, int cost, Map<NetworkLink, Integer> usage, Map<NodePos, NodePos> rackOf, Set<NodePos> rackSources, int budget,
            int[] budgetUsed) {
        for (NetworkLink link : path.links()) {
            if (usage.getOrDefault(link, 0) + cost > link.capacity()) {
                return false;
            }
        }
        return !endsAtRack(path, rackOf, rackSources) || budgetUsed[0] + cost <= budget;
    }

    private static void take(Path path, int cost, Map<NetworkLink, Integer> usage, Map<NodePos, NodePos> rackOf, Set<NodePos> rackSources,
            int[] budgetUsed) {
        for (NetworkLink link : path.links()) {
            usage.merge(link, cost, Integer::sum);
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
