/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.optical;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.network.NetworkGraph;
import net.zagdrath.encodedlogistics.network.NetworkLink;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NodePos;

// Fiber span loss (the optical handoff 1). Only Fiber Cable runs lose lanes: a continuous run longer than
// fiberSpanThreshold blocks loses one lane for every further fiberSpanLossStep, down to 1. A powered Optical Amplifier
// in the run ends the span there (the count starts again after it); an unpowered one passes the light through, and the
// run goes on through it. Network, Dense and Fiber Cable all still carry their tier's lanes otherwise; bridges and
// links are untouched.
//
// Worked out for each network's graph once it's discovered (NetworkDiscovery): each run's span is its longest path
// between its ends (where it meets anything that isn't Fiber Cable), and every link of the run, and into it, carries at
// most the lanes left after its span's loss. The spans are kept by position for the Fiber Cable popup.
public final class SpanLoss {
    // A run's span: its length in blocks (since the last amplifier, or the run's end) and the lanes it carries.
    public record Span(int length, int lanes, int lost) {}

    // The ends whose paths are measured, at most (a run with more is measured from its first ones).
    private static final int MAX_ENDS = 16;
    private static final int MAX_KEPT = 200_000;
    private static final Map<NodePos, Span> SPANS = new ConcurrentHashMap<>();

    private SpanLoss() {}

    // Lanes lost over an unamplified length.
    public static int lost(int length) {
        if (!Config.FIBER_SPAN_LOSS.getAsBoolean()) {
            return 0;
        }
        int over = length - Config.FIBER_SPAN_THRESHOLD.getAsInt();
        return over <= 0 ? 0 : over / Config.FIBER_SPAN_LOSS_STEP.getAsInt();
    }

    // The lanes left of full over an unamplified length (at least 1).
    public static int lanes(int length, int full) {
        return Math.max(1, full - lost(length));
    }

    // Signal quality: 100 % less 4 % a lane lost.
    public static int quality(int lost) {
        return Math.max(0, 100 - 4 * lost);
    }

    public static int fiberLanes() {
        return Config.LANES_PER_FIBER_CABLE.getAsInt();
    }

    // The span a Fiber Cable at pos was last found on, or null.
    public static @Nullable Span at(NodePos pos) {
        return SPANS.get(pos);
    }

    private static boolean inRun(@Nullable NetworkNode node) {
        return node != null && node.optical() == NetworkNode.Optical.FIBER;
    }

    // Limits the graph's Fiber Cable links to their spans' lanes.
    public static void apply(NetworkGraph graph) {
        if (SPANS.size() > MAX_KEPT) {
            SPANS.clear();
        }
        Set<NodePos> seen = new HashSet<>();
        int full = fiberLanes();
        for (Map.Entry<NodePos, NetworkNode> entry : graph.entries().entrySet()) {
            if (!inRun(entry.getValue()) || seen.contains(entry.getKey())) {
                continue;
            }
            // The run: Fiber Cables joined side by side.
            List<NodePos> run = new ArrayList<>();
            Set<NodePos> members = new HashSet<>();
            List<NodePos> ends = new ArrayList<>();
            ArrayDeque<NodePos> queue = new ArrayDeque<>();
            queue.add(entry.getKey());
            members.add(entry.getKey());
            while (!queue.isEmpty()) {
                NodePos at = queue.poll();
                run.add(at);
                boolean end = !graph.remoteLinks(at).isEmpty();
                for (Direction side : Direction.values()) {
                    NetworkLink link = graph.link(at, side);
                    if (link == null) {
                        continue;
                    }
                    NodePos next = link.other(at);
                    if (inRun(graph.node(next))) {
                        if (members.add(next)) {
                            queue.add(next);
                        }
                    } else {
                        end = true;
                    }
                }
                if (end) {
                    ends.add(at);
                }
            }
            seen.addAll(members);
            int length = length(graph, members, ends, run.size());
            int lanes = lanes(length, full);
            Span span = new Span(length, lanes, Math.max(0, full - lanes));
            for (NodePos at : run) {
                SPANS.put(at, span);
            }
            if (lanes >= full) {
                continue;
            }
            for (NodePos at : run) {
                for (Direction side : Direction.values()) {
                    NetworkLink link = graph.link(at, side);
                    if (link != null && link.capacity() > lanes) {
                        graph.limit(link, lanes);
                    }
                }
            }
        }
    }

    // The run's longest path between two of its ends, in blocks (one end: its farthest block; no ends: all of it).
    private static int length(NetworkGraph graph, Set<NodePos> members, List<NodePos> ends, int size) {
        if (ends.isEmpty()) {
            return size;
        }
        int longest = 0;
        for (NodePos from : ends.subList(0, Math.min(MAX_ENDS, ends.size()))) {
            Map<NodePos, Integer> distance = new HashMap<>();
            ArrayDeque<NodePos> queue = new ArrayDeque<>();
            distance.put(from, 0);
            queue.add(from);
            int farthest = 0, farthestEnd = 0;
            Set<NodePos> endSet = new HashSet<>(ends);
            while (!queue.isEmpty()) {
                NodePos at = queue.poll();
                int d = distance.get(at);
                farthest = Math.max(farthest, d);
                if (endSet.contains(at)) {
                    farthestEnd = Math.max(farthestEnd, d);
                }
                for (Direction side : Direction.values()) {
                    NetworkLink link = graph.link(at, side);
                    if (link == null) {
                        continue;
                    }
                    NodePos next = link.other(at);
                    if (members.contains(next) && !distance.containsKey(next)) {
                        distance.put(next, d + 1);
                        queue.add(next);
                    }
                }
            }
            longest = Math.max(longest, ends.size() > 1 ? farthestEnd : farthest);
        }
        return longest + 1;
    }
}
