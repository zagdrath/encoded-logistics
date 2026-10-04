/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

import java.util.function.LongSupplier;

// The real time schedule entries' INTERVAL() and the triggers' debounce go by (epoch ms). The game tests, whose server
// runs ticks as fast as it can, set it to the server's ticks at 50 ms each.
public final class RealTime {
    private static LongSupplier clock = System::currentTimeMillis;

    private RealTime() {}

    public static long millis() {
        return clock.getAsLong();
    }

    public static void set(LongSupplier source) {
        clock = source;
    }
}
