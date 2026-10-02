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

import net.minecraft.core.Direction;

// AE2-style lane assignment, as a pure function of the graph.
//
// With one controller structure: a multi-source BFS from every controller block that has a link to the rest of the
// network builds a shortest-path tree (ties broken by Direction order DOWN, UP, NORTH, SOUTH, WEST, EAST, then remote
// links in NetworkGraph.ORDER, sources in that order too, so assignment is stable between reloads). Devices are then
// visited nearest first (ties by position); each walks its tree path back to the controller and gets a lane only if
// every link on the path has room for it, otherwise it is missing a lane (still connected, but inactive).
//
// With no controller the network is ad hoc: up to adHocLimit devices work with no routing limits; more and none do.
// With controllers from two or more structures the network is in conflict and no device gets a lane.
public final class LaneSolver {
    private LaneSolver() {}

    public static LaneResult solve(NetworkGraph graph, int adHocLimit) {
        List<NodePos> devices = new ArrayList<>();
        List<NodePos> controllers = new ArrayList<>();
        Set<Long> groups = new HashSet<>();
        graph.entries().forEach((pos, node) -> {
            if (node.isController()) {
                controllers.add(pos);
                groups.add(node.controllerGroup());
            } else if (node.laneCost() > 0) {
                devices.add(pos);
            }
        });
        devices.sort(NetworkGraph.ORDER);
        controllers.sort(NetworkGraph.ORDER);

        if (groups.size() > 1) {
            return new LaneResult(NetworkStatus.CONFLICT, false, 0, 0, allDevices(devices, false), Map.of());
        }
        if (groups.isEmpty()) {
            int needed = devices.stream().mapToInt(pos -> graph.node(pos).laneCost()).sum();
            boolean fits = needed <= adHocLimit;
            return new LaneResult(fits ? NetworkStatus.ONLINE : NetworkStatus.ADHOC_OVERLOAD, true, adHocLimit,
                    fits ? needed : 0, allDevices(devices, fits), Map.of());
        }

        // Sources: controller blocks with a link to something that isn't a controller. Each such link is a controller
        // face with a connection, and its capacity counts toward the network's.
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
        int used = 0;
        for (NodePos device : devices) {
            int cost = graph.node(device).laneCost();
            List<NetworkLink> path = pathToController(device, parent, distance);
            boolean fits = path != null;
            if (fits) {
                for (NetworkLink link : path) {
                    if (usage.getOrDefault(link, 0) + cost > link.capacity()) {
                        fits = false;
                        break;
                    }
                }
            }
            if (fits) {
                for (NetworkLink link : path) {
                    usage.merge(link, cost, Integer::sum);
                }
                used += cost;
            }
            lanes.put(device, fits);
        }
        return new LaneResult(NetworkStatus.ONLINE, false, capacity, used, lanes, usage);
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

    // The links from a device back to the controller along the BFS tree, or null when it can't reach one.
    private static List<NetworkLink> pathToController(NodePos device, Map<NodePos, NetworkLink> parent, Map<NodePos, Integer> distance) {
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
        return path;
    }

    private static Map<NodePos, Boolean> allDevices(List<NodePos> devices, boolean hasLane) {
        Map<NodePos, Boolean> lanes = new HashMap<>();
        devices.forEach(device -> lanes.put(device, hasLane));
        return lanes;
    }
}
