/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// The nodes of one network and the links between neighbouring nodes: what the ChannelSolver routes over.
public final class NetworkGraph {
    private final Map<BlockPos, NetworkNode> nodes = new HashMap<>();
    private final Map<BlockPos, Map<Direction, NetworkLink>> links = new HashMap<>();
    private final List<NetworkLink> allLinks = new ArrayList<>();

    public void addNode(NetworkNode node) {
        BlockPos pos = node.pos().immutable();
        if (nodes.putIfAbsent(pos, node) != null) {
            throw new IllegalArgumentException("Two nodes at " + pos);
        }
    }

    public boolean contains(BlockPos pos) {
        return nodes.containsKey(pos);
    }

    public @Nullable NetworkNode node(BlockPos pos) {
        return nodes.get(pos);
    }

    public Collection<NetworkNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public int size() {
        return nodes.size();
    }

    // Links the node at pos to its neighbour on side, with the smaller channel capacity of the two. Both must be in
    // the graph; linking the same pair twice returns the existing link.
    public NetworkLink connect(BlockPos pos, Direction side) {
        BlockPos neighbour = pos.relative(side);
        NetworkNode a = nodes.get(pos);
        NetworkNode b = nodes.get(neighbour);
        if (a == null || b == null) {
            throw new IllegalArgumentException("No node at " + (a == null ? pos : neighbour));
        }
        NetworkLink existing = link(pos, side);
        if (existing != null) {
            return existing;
        }
        NetworkLink link = new NetworkLink(pos.immutable(), neighbour.immutable(), Math.min(a.channelCapacity(), b.channelCapacity()));
        links.computeIfAbsent(pos.immutable(), p -> new EnumMap<>(Direction.class)).put(side, link);
        links.computeIfAbsent(neighbour.immutable(), p -> new EnumMap<>(Direction.class)).put(side.getOpposite(), link);
        allLinks.add(link);
        return link;
    }

    public @Nullable NetworkLink link(BlockPos pos, Direction side) {
        Map<Direction, NetworkLink> sides = links.get(pos);
        return sides != null ? sides.get(side) : null;
    }

    public List<NetworkLink> links() {
        return Collections.unmodifiableList(allLinks);
    }
}
