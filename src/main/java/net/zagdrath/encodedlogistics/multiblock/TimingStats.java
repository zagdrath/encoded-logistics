/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import java.util.Locale;

// How long something that runs every tick takes (a network's tick, a level's networks): counted over each minute of
// game time, for /encodedlogistics debug ticks.
public final class TimingStats {
    private int count, lastCount;
    private long nanos, lastNanos, max, lastMax;

    void add(long took) {
        count++;
        nanos += took;
        max = Math.max(max, took);
    }

    // A minute is up: its timings become the last minute's.
    void roll() {
        lastCount = count;
        lastNanos = nanos;
        lastMax = max;
        count = 0;
        nanos = 0;
        max = 0;
    }

    // Average per run over the last full minute (the one so far, before there's been one), in milliseconds.
    public double averageMillis() {
        return lastCount > 0 ? lastNanos / 1e6 / lastCount : count > 0 ? nanos / 1e6 / count : 0;
    }

    public double maxMillis() {
        return Math.max(lastMax, max) / 1e6;
    }

    // "avg 0.12 ms, max 1.80 ms".
    public String describe() {
        return String.format(Locale.ROOT, "avg %.2f ms, max %.2f ms", averageMillis(), maxMillis());
    }
}
