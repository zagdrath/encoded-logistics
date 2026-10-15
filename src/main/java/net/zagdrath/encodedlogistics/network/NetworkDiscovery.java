/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import net.zagdrath.encodedlogistics.machine.MachineBridges;
import net.zagdrath.encodedlogistics.optical.OpticalChannels;
import net.zagdrath.encodedlogistics.optical.SpanLoss;

// Builds a controller structure's network graph from the world: its controller blocks, then everything reachable
// from them through NetworkNodeBlocks (cables) and NetworkNodeHosts (devices) that connect toward each other, and
// through remote links (Network Bridges, lanes Point-to-Point Links, a Wireless Controller's Wireless Bridges and Ports)
// whose two ends are both loaded and list each other - into another dimension too when bridgeCrossDimension allows
// (wirelessCrossDimension, for wireless links). A node that doesn't pass through (a
// Segment Isolator) is on the network but the walk stops there.
// Controllers of another structure that turn up are added too (and not walked through), so the solver sees the
// conflict. Where the walk met an unloaded block (a neighbour, or a remote link's far end) is kept as its frontier: the
// network is worked out again when that chunk loads, so it doesn't stay cut short at a chunk border.
// A graph's Fiber Cable spans (SpanLoss) are kept with it, for the Fiber Cable and Optical Amplifier popups.
public final class NetworkDiscovery {
    // A network bigger than this is cut off where the walk stops.
    public static final int MAX_NODES = 16_384;

    public record Discovered(NetworkGraph graph, Map<NodePos, Item> items, Map<NodePos, SpanLoss.Span> spans, Set<NodePos> frontier) {}

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
        Set<NodePos> frontier = new HashSet<>();
        while (!queue.isEmpty() && graph.size() < MAX_NODES) {
            NodePos at = queue.poll();
            NetworkNode node = graph.node(at);
            ServerLevel here = server.getLevel(at.dimension());
            for (Direction side : node.connections()) {
                NodePos neighbourPos = NodePos.of(at.dimension(), at.pos().relative(side));
                if (here == null) {
                    continue;
                }
                if (!here.isLoaded(neighbourPos.pos())) {
                    frontier.add(neighbourPos);
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
            List<RemoteLink> remotes = node.remoteLinks();
            List<RemoteLink> optical = OpticalChannels.links(server, at);
            if (!optical.isEmpty()) {
                remotes = new ArrayList<>(remotes);
                remotes.addAll(optical);
            }
            for (RemoteLink remote : remotes) {
                NodePos target = remote.target();
                boolean crossDimension = remote.wireless() ? Config.WIRELESS_CROSS_DIMENSION.getAsBoolean() : Config.BRIDGE_CROSS_DIMENSION.getAsBoolean();
                if (target.equals(at) || !target.dimension().equals(at.dimension()) && !crossDimension) {
                    continue;
                }
                ServerLevel there = server.getLevel(target.dimension());
                if (there == null) {
                    continue;
                }
                if (!there.isLoaded(target.pos())) {
                    frontier.add(target);
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
                if (back == null) {
                    back = OpticalChannels.link(server, target, at);
                }
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
        Map<NodePos, SpanLoss.Span> spans = SpanLoss.apply(graph);
        return new Discovered(graph, items, spans, frontier);
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

    // The network node at pos as discovery sees it, or null.
    public static @Nullable NetworkNode nodeAt(ServerLevel level, BlockPos pos, int lanesPerFace) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof NetworkNodeBlock block) {
            return block.getNetworkNode(level, pos, state);
        }
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof NetworkControllerBlockEntity controller) {
            return new ControllerNode(pos.immutable(), controller.getStructureId(), lanesPerFace);
        }
        if (blockEntity instanceof NetworkNodeHost host) {
            return host.getNetworkNode();
        }
        // Another mod's machine with a linked Small Wireless Bridge on it.
        return MachineBridges.node(level, pos);
    }
}
