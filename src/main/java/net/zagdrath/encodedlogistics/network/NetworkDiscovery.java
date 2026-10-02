/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;

// Builds a controller structure's network graph from the world: its controller blocks, then everything reachable
// from them through NetworkNodeBlocks (cables) and NetworkNodeHosts (devices) that connect toward each other.
// Controllers of another structure that turn up are added too (and not walked through), so the solver sees the
// conflict.
public final class NetworkDiscovery {
    // A network bigger than this is cut off where the walk stops.
    public static final int MAX_NODES = 16_384;

    public record Discovered(NetworkGraph graph, Map<BlockPos, Item> items) {}

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
        Map<BlockPos, Item> items = new HashMap<>();
        ArrayDeque<NetworkNode> queue = new ArrayDeque<>();
        for (BlockPos member : members) {
            NetworkNode node = new ControllerNode(member.immutable(), structureId, lanesPerFace);
            graph.addNode(node);
            queue.add(node);
        }
        while (!queue.isEmpty() && graph.size() < MAX_NODES) {
            NetworkNode node = queue.poll();
            for (Direction side : node.connections()) {
                BlockPos neighbourPos = node.pos().relative(side);
                if (!level.isLoaded(neighbourPos)) {
                    continue;
                }
                NetworkNode neighbour = graph.node(neighbourPos);
                boolean added = false;
                if (neighbour == null) {
                    neighbour = nodeAt(level, neighbourPos, lanesPerFace);
                    if (neighbour == null || !neighbour.connections().contains(side.getOpposite())) {
                        continue;
                    }
                    graph.addNode(neighbour);
                    added = true;
                } else if (!neighbour.connections().contains(side.getOpposite())) {
                    continue;
                }
                // Controllers of one structure are a single block as far as the network goes.
                if (node.isController() && neighbour.isController()) {
                    continue;
                }
                graph.connect(node.pos(), side);
                if (added && !neighbour.isController()) {
                    items.put(neighbourPos, level.getBlockState(neighbourPos).getBlock().asItem());
                    queue.add(neighbour);
                }
            }
        }
        return new Discovered(graph, items);
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
