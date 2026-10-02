/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

// The nodes of one network, by dimension and position, and the links between them: neighbours, and remote links
// (Network Bridges, lanes Point-to-Point Links) that may reach into another dimension. What the LaneSolver routes over.
public final class NetworkGraph {
    // A stable order for positions across dimensions: by dimension id, then position.
    public static final Comparator<NodePos> ORDER = Comparator.<NodePos, String>comparing(pos -> pos.dimension().identifier().toString())
            .thenComparing(NodePos::pos);

    private final Map<NodePos, NetworkNode> nodes = new HashMap<>();
    private final Map<NodePos, Map<Direction, NetworkLink>> links = new HashMap<>();
    private final Map<NodePos, List<NetworkLink>> remote = new HashMap<>();
    private final List<NetworkLink> allLinks = new ArrayList<>();

    public static NodePos at(ResourceKey<Level> dimension, BlockPos pos) {
        return NodePos.of(dimension, pos.immutable());
    }

    public NodePos addNode(ResourceKey<Level> dimension, NetworkNode node) {
        NodePos pos = at(dimension, node.pos());
        if (nodes.putIfAbsent(pos, node) != null) {
            throw new IllegalArgumentException("Two nodes at " + pos);
        }
        return pos;
    }

    public boolean contains(NodePos pos) {
        return nodes.containsKey(pos);
    }

    public @Nullable NetworkNode node(NodePos pos) {
        return nodes.get(pos);
    }

    public Collection<NetworkNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    // Every node by where it is.
    public Map<NodePos, NetworkNode> entries() {
        return Collections.unmodifiableMap(nodes);
    }

    public int size() {
        return nodes.size();
    }

    // Links the node at pos to its neighbour on side, with the smaller lane capacity of the two. Both must be in
    // the graph; linking the same pair twice returns the existing link.
    public NetworkLink connect(NodePos pos, Direction side) {
        NodePos neighbour = NodePos.of(pos.dimension(), pos.pos().relative(side));
        NetworkNode a = nodes.get(pos);
        NetworkNode b = nodes.get(neighbour);
        if (a == null || b == null) {
            throw new IllegalArgumentException("No node at " + (a == null ? pos : neighbour));
        }
        NetworkLink existing = link(pos, side);
        if (existing != null) {
            return existing;
        }
        NetworkLink link = new NetworkLink(pos, neighbour, Math.min(a.laneCapacity(), b.laneCapacity()));
        links.computeIfAbsent(pos, p -> new EnumMap<>(Direction.class)).put(side, link);
        links.computeIfAbsent(neighbour, p -> new EnumMap<>(Direction.class)).put(side.getOpposite(), link);
        allLinks.add(link);
        return link;
    }

    // Links two nodes that aren't neighbours, carrying at most capacity lanes (and no more than either node carries).
    // Linking the same pair twice returns the existing link.
    public NetworkLink connectRemote(NodePos a, NodePos b, int capacity) {
        NetworkNode nodeA = nodes.get(a);
        NetworkNode nodeB = nodes.get(b);
        if (nodeA == null || nodeB == null) {
            throw new IllegalArgumentException("No node at " + (nodeA == null ? a : b));
        }
        NetworkLink existing = remoteLink(a, b);
        if (existing != null) {
            return existing;
        }
        NetworkLink link = new NetworkLink(a, b, Math.min(capacity, Math.min(nodeA.laneCapacity(), nodeB.laneCapacity())));
        remote.computeIfAbsent(a, p -> new ArrayList<>()).add(link);
        remote.computeIfAbsent(b, p -> new ArrayList<>()).add(link);
        allLinks.add(link);
        return link;
    }

    public @Nullable NetworkLink link(NodePos pos, Direction side) {
        Map<Direction, NetworkLink> sides = links.get(pos);
        return sides != null ? sides.get(side) : null;
    }

    public @Nullable NetworkLink remoteLink(NodePos a, NodePos b) {
        for (NetworkLink link : remoteLinks(a)) {
            if (link.other(a).equals(b)) {
                return link;
            }
        }
        return null;
    }

    // The remote links at a node, in a stable order.
    public List<NetworkLink> remoteLinks(NodePos pos) {
        List<NetworkLink> list = remote.get(pos);
        if (list == null) {
            return List.of();
        }
        List<NetworkLink> sorted = new ArrayList<>(list);
        sorted.sort((x, y) -> ORDER.compare(x.other(pos), y.other(pos)));
        return sorted;
    }

    public List<NetworkLink> links() {
        return Collections.unmodifiableList(allLinks);
    }
}
