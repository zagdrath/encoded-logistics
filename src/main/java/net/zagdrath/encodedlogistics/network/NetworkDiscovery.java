/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;

// Builds a controller structure's network graph from the world: its controller blocks, then everything reachable
// from them through NetworkNodeBlocks (cables) and NetworkNodeHosts (devices) that connect toward each other, and
// through remote links (Network Bridges, lanes Point-to-Point Links) whose two ends are both loaded and list each other -
// into another dimension too, for Bridges, when bridgeCrossDimension allows. A node that doesn't pass through (a
// Segment Isolator) is on the network but the walk stops there.
// Controllers of another structure that turn up are added too (and not walked through), so the solver sees the
// conflict.
public final class NetworkDiscovery {
    // A network bigger than this is cut off where the walk stops.
    public static final int MAX_NODES = 16_384;

    public record Discovered(NetworkGraph graph, Map<NodePos, Item> items) {}

    // A controller block on the graph: links on all six sides, 32 (config) lanes per linked face.
    public record ControllerNode(BlockPos pos, long controllerGroup, int laneCapacity) implements NetworkNode {
        private static final Set<Direction> ALL = EnumSet.allOf(Direction.class);

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

    private NetworkDiscovery() {}

    public static Discovered discover(ServerLevel level, long structureId, Collection<BlockPos> members, int lanesPerFace) {
        NetworkGraph graph = new NetworkGraph();
        Map<NodePos, Item> items = new HashMap<>();
        ArrayDeque<NodePos> queue = new ArrayDeque<>();
        for (BlockPos member : members) {
            queue.add(graph.addNode(level.dimension(), new ControllerNode(member.immutable(), structureId, lanesPerFace)));
        }
        return walk(level, graph, items, queue, lanesPerFace);
    }

    // A rack Network Controller's network: everything reachable from its Server Rack (whose master is at rack).
    public static Discovered discoverRack(ServerLevel level, BlockPos rack, int lanesPerFace) {
        NetworkGraph graph = new NetworkGraph();
        Map<NodePos, Item> items = new HashMap<>();
        ArrayDeque<NodePos> queue = new ArrayDeque<>();
        NetworkNode start = nodeAt(level, rack, lanesPerFace);
        if (start != null) {
            NodePos pos = graph.addNode(level.dimension(), start);
            items.put(pos, level.getBlockState(rack).getBlock().asItem());
            queue.add(pos);
        }
        return walk(level, graph, items, queue, lanesPerFace);
    }

    private static Discovered walk(ServerLevel level, NetworkGraph graph, Map<NodePos, Item> items, ArrayDeque<NodePos> queue, int lanesPerFace) {
        MinecraftServer server = level.getServer();
        while (!queue.isEmpty() && graph.size() < MAX_NODES) {
            NodePos at = queue.poll();
            NetworkNode node = graph.node(at);
            ServerLevel here = server.getLevel(at.dimension());
            for (Direction side : node.connections()) {
                NodePos neighbourPos = NodePos.of(at.dimension(), at.pos().relative(side));
                if (here == null || !here.isLoaded(neighbourPos.pos())) {
                    continue;
                }
                NetworkNode neighbour = graph.node(neighbourPos);
                boolean added = false;
                if (neighbour == null) {
                    neighbour = nodeAt(here, neighbourPos.pos(), lanesPerFace);
                    if (neighbour == null || !neighbour.connections().contains(side.getOpposite())) {
                        continue;
                    }
                    graph.addNode(at.dimension(), neighbour);
                    added = true;
                } else if (!neighbour.connections().contains(side.getOpposite())) {
                    continue;
                }
                // Controllers of one structure are a single block as far as the network goes.
                if (node.isController() && neighbour.isController()) {
                    continue;
                }
                graph.connect(at, side);
                if (added && !neighbour.isController()) {
                    items.put(neighbourPos, here.getBlockState(neighbourPos.pos()).getBlock().asItem());
                    if (neighbour.passesThrough()) {
                        queue.add(neighbourPos);
                    }
                }
            }
            for (RemoteLink remote : node.remoteLinks()) {
                NodePos target = remote.target();
                if (target.equals(at) || !target.dimension().equals(at.dimension()) && !Config.BRIDGE_CROSS_DIMENSION.getAsBoolean()) {
                    continue;
                }
                ServerLevel there = server.getLevel(target.dimension());
                if (there == null || !there.isLoaded(target.pos())) {
                    continue;
                }
                NetworkNode other = graph.node(target);
                boolean added = false;
                if (other == null) {
                    other = nodeAt(there, target.pos(), lanesPerFace);
                    if (other == null) {
                        continue;
                    }
                    added = true;
                }
                RemoteLink back = linkTo(other, at);
                if (back == null || other.isController()) {
                    continue;
                }
                if (added) {
                    graph.addNode(target.dimension(), other);
                    items.put(target, there.getBlockState(target.pos()).getBlock().asItem());
                    if (other.passesThrough()) {
                        queue.add(target);
                    }
                }
                graph.connectRemote(at, target, Math.min(remote.capacity(), back.capacity()));
            }
        }
        return new Discovered(graph, items);
    }

    // The controller structure reachable from a node (start, linked toward the side it was reached from), without passing
    // the excluded positions: a controller block's structure, or a rack's whose rack holds controllers; null for none.
    // What a Server Rack checks its cables against (RackBlockEntity#isMismatched).
    public static @Nullable Long controllerBeyond(ServerLevel level, BlockPos start, Direction from, Set<BlockPos> excluded) {
        NetworkNode first = level.isLoaded(start) ? nodeAt(level, start, 0) : null;
        if (first == null || !first.connections().contains(from.getOpposite())) {
            return null;
        }
        ArrayDeque<NetworkNode> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>(excluded);
        seen.add(start);
        queue.add(first);
        while (!queue.isEmpty() && seen.size() < MAX_NODES) {
            NetworkNode node = queue.poll();
            if (node.isController()) {
                return node.controllerGroup();
            }
            if (node instanceof RackNode rack && !rack.controllers().isEmpty() && rack.structure() > 0) {
                return rack.structure();
            }
            if (!node.passesThrough() && node != first) {
                continue;
            }
            for (Direction side : node.connections()) {
                BlockPos next = node.pos().relative(side);
                if (!seen.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                NetworkNode neighbour = nodeAt(level, next, 0);
                if (neighbour != null && neighbour.connections().contains(side.getOpposite())) {
                    queue.add(neighbour);
                }
            }
        }
        return null;
    }

    private static @Nullable RemoteLink linkTo(NetworkNode node, NodePos target) {
        for (RemoteLink link : node.remoteLinks()) {
            if (link.target().equals(target)) {
                return link;
            }
        }
        return null;
    }

    private static @Nullable NetworkNode nodeAt(ServerLevel level, BlockPos pos, int lanesPerFace) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof NetworkNodeBlock block) {
            return block.getNetworkNode(level, pos, state);
        }
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof NetworkControllerBlockEntity controller) {
            return new ControllerNode(pos.immutable(), controller.getStructureId(), lanesPerFace);
        }
        return blockEntity instanceof NetworkNodeHost host ? host.getNetworkNode() : null;
    }
}
