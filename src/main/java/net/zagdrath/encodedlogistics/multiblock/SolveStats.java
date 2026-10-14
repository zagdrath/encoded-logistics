/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

// How often one network is worked out again (discovered and solved) and what it costs: counted over each minute of game
// time, with why it was asked to (TopologyCause), for /encodedlogistics debug solves. A network solving every second or
// so means something keeps invalidating it.
public final class SolveStats {
    private int solves, lastSolves;
    private long nanos, lastNanos, maxNanos, lastMaxNanos, latestNanos, latestDiscovery;
    private int nodes;
    // Every solve since the network was first worked out (game tests compare these).
    private long total;
    private final Map<TopologyCause, Integer> causes = new EnumMap<>(TopologyCause.class), lastCauses = new EnumMap<>(TopologyCause.class);

    // took: the whole solve; discovery: the part of it spent finding the network's blocks in the world.
    void solved(long took, long discovery, int graphSize, TopologyCause cause) {
        solves++;
        total++;
        nanos += took;
        maxNanos = Math.max(maxNanos, took);
        latestNanos = took;
        latestDiscovery = discovery;
        nodes = graphSize;
        causes.merge(cause, 1, Integer::sum);
    }

    // A minute is up: its counts become the last minute's.
    void roll() {
        lastSolves = solves;
        lastNanos = nanos;
        lastMaxNanos = maxNanos;
        lastCauses.clear();
        lastCauses.putAll(causes);
        solves = 0;
        nanos = 0;
        maxNanos = 0;
        causes.clear();
    }

    boolean any() {
        return solves > 0 || lastSolves > 0;
    }

    // "4 solves (1 so far), avg 3.1 ms, max 5.0 ms, last 2.9 ms, 1615 nodes; block placed, broken or reconnected 3, ...":
    // over the last full minute, and the solves of the one so far.
    public String describe() {
        StringJoiner why = new StringJoiner(", ");
        Map<TopologyCause, Integer> shown = new EnumMap<>(lastCauses);
        causes.forEach((cause, count) -> shown.merge(cause, count, Integer::sum));
        shown.forEach((cause, count) -> why.add(cause.label() + " " + count));
        return String.format(java.util.Locale.ROOT, "%d solves last minute (%d so far), avg %.1f ms, max %.1f ms, last %.1f ms (%.1f finding its blocks), %d nodes; %s",
                lastSolves, solves, lastSolves == 0 ? 0.0 : lastNanos / 1e6 / lastSolves, Math.max(lastMaxNanos, maxNanos) / 1e6, latestNanos / 1e6,
                latestDiscovery / 1e6, nodes, why.length() == 0 ? "no causes" : why.toString());
    }

    public long total() {
        return total;
    }

    // The latest solve's time, in milliseconds.
    public double latestMillis() {
        return latestNanos / 1e6;
    }

    public int lastMinute() {
        return lastSolves;
    }

    public int thisMinute() {
        return solves;
    }
}
