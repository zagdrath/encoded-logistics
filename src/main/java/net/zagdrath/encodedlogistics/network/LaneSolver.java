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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// AE2-style lane assignment, as a pure function of the graph.
//
// With one controller structure: a multi-source BFS from every controller block that has a link to the rest of the
// network builds a shortest-path tree (ties broken by Direction order DOWN, UP, NORTH, SOUTH, WEST, EAST, sources in
// BlockPos order, so assignment is stable between reloads). Devices are then visited nearest first (ties by BlockPos);
// each walks its tree path back to the controller and gets a lane only if every link on the path has room for it,
// otherwise it is missing a lane (still connected, but inactive).
//
// With no controller the network is ad hoc: up to adHocLimit devices work with no routing limits; more and none do.
// With controllers from two or more structures the network is in conflict and no device gets a lane.
public final class LaneSolver {
    private static final Comparator<NetworkNode> BY_POS = Comparator.comparing(NetworkNode::pos);

    private LaneSolver() {}

    public static LaneResult solve(NetworkGraph graph, int adHocLimit) {
        List<NetworkNode> devices = new ArrayList<>();
        List<NetworkNode> controllers = new ArrayList<>();
        Set<Long> groups = new HashSet<>();
        for (NetworkNode node : graph.nodes()) {
            if (node.isController()) {
                controllers.add(node);
                groups.add(node.controllerGroup());
            } else if (node.laneCost() > 0) {
                devices.add(node);
            }
        }
        devices.sort(BY_POS);
        controllers.sort(BY_POS);

        if (groups.size() > 1) {
            return new LaneResult(NetworkStatus.CONFLICT, false, 0, 0, allDevices(devices, false), Map.of());
        }
        if (groups.isEmpty()) {
            int needed = devices.stream().mapToInt(NetworkNode::laneCost).sum();
            boolean fits = needed <= adHocLimit;
            return new LaneResult(fits ? NetworkStatus.ONLINE : NetworkStatus.ADHOC_OVERLOAD, true, adHocLimit,
                    fits ? needed : 0, allDevices(devices, fits), Map.of());
        }

        // Sources: controller blocks with a link to something that isn't a controller. Each such link is a controller
        // face with a connection, and its capacity counts toward the network's.
        Map<BlockPos, NetworkLink> parent = new HashMap<>();
        Map<BlockPos, Integer> distance = new HashMap<>();
        ArrayDeque<NetworkNode> queue = new ArrayDeque<>();
        int capacity = 0;
        for (NetworkNode controller : controllers) {
            boolean source = false;
            for (Direction side : Direction.values()) {
                NetworkLink link = graph.link(controller.pos(), side);
                NetworkNode other = link != null ? graph.node(link.other(controller.pos())) : null;
                if (other != null && !other.isController()) {
                    capacity += controller.laneCapacity();
                    source = true;
                }
            }
            if (source) {
                distance.put(controller.pos(), 0);
                queue.add(controller);
            }
        }
        while (!queue.isEmpty()) {
            NetworkNode node = queue.poll();
            int next = distance.get(node.pos()) + 1;
            for (Direction side : Direction.values()) {
                NetworkLink link = graph.link(node.pos(), side);
                if (link == null) {
                    continue;
                }
                BlockPos neighbour = link.other(node.pos());
                NetworkNode other = graph.node(neighbour);
                if (other == null || other.isController() || distance.containsKey(neighbour)) {
                    continue;
                }
                distance.put(neighbour, next);
                parent.put(neighbour, link);
                queue.add(other);
            }
        }

        devices.sort(Comparator.<NetworkNode>comparingInt(d -> distance.getOrDefault(d.pos(), Integer.MAX_VALUE)).thenComparing(BY_POS));
        Map<BlockPos, Boolean> lanes = new HashMap<>();
        Map<NetworkLink, Integer> usage = new HashMap<>();
        int used = 0;
        for (NetworkNode device : devices) {
            List<NetworkLink> path = pathToController(device.pos(), parent, distance);
            boolean fits = path != null;
            if (fits) {
                for (NetworkLink link : path) {
                    if (usage.getOrDefault(link, 0) + device.laneCost() > link.capacity()) {
                        fits = false;
                        break;
                    }
                }
            }
            if (fits) {
                for (NetworkLink link : path) {
                    usage.merge(link, device.laneCost(), Integer::sum);
                }
                used += device.laneCost();
            }
            lanes.put(device.pos(), fits);
        }
        return new LaneResult(NetworkStatus.ONLINE, false, capacity, used, lanes, usage);
    }

    // The links from a device back to the controller along the BFS tree, or null when it can't reach one.
    private static List<NetworkLink> pathToController(BlockPos device, Map<BlockPos, NetworkLink> parent, Map<BlockPos, Integer> distance) {
        if (!distance.containsKey(device)) {
            return null;
        }
        List<NetworkLink> path = new ArrayList<>();
        BlockPos at = device;
        while (distance.get(at) > 0) {
            NetworkLink link = parent.get(at);
            path.add(link);
            at = link.other(at);
        }
        return path;
    }

    private static Map<BlockPos, Boolean> allDevices(List<NetworkNode> devices, boolean hasLane) {
        Map<BlockPos, Boolean> lanes = new HashMap<>();
        devices.forEach(device -> lanes.put(device.pos(), hasLane));
        return lanes;
    }
}
