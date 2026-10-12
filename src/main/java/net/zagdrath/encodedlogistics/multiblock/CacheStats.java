/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import java.util.StringJoiner;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// How often the network caches (ControllerStructures, "Caches") are rebuilt: per controller structure, and server-wide
// for the racks in order and the rack Scheduler pools' heads. Counted over each minute of game time; /encodedlogistics
// caches shows the last full minute and the one so far, and with logging on, each minute that rebuilt anything is
// logged. A cache rebuilt about every tick (some 1200 a minute) means something invalidates it every tick.
public final class CacheStats {
    public static final int MINUTE = 1200;

    public enum Kind {
        STORAGE("storage"),
        DRIVE_CELLS("drive cells"),
        SERVING("rack devices serving"),
        RACKS("racks"),
        SCHEDULER_HEADS("scheduler heads");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private static boolean logging;

    private final int[] current = new int[Kind.values().length], last = new int[Kind.values().length];

    public static boolean logging() {
        return logging;
    }

    public static void setLogging(boolean on) {
        logging = on;
    }

    void rebuilt(Kind kind) {
        current[kind.ordinal()]++;
    }

    // A minute is up: its counts become the last minute's (and are logged, when asked for and there were any).
    void roll(@Nullable NetworkRef network) {
        boolean any = false;
        for (int i = 0; i < current.length; i++) {
            last[i] = current[i];
            current[i] = 0;
            any |= last[i] > 0;
        }
        if (logging && any) {
            EncodedLogistics.LOGGER.info("[caches] {}: {} rebuilds in the last minute", network == null ? "server"
                    : network.dimension().identifier() + " #" + network.id(), describe(true));
        }
    }

    // "storage 3, drive cells 3, rack devices serving 1": the kinds rebuilt, over the last full minute or the one so far.
    public String describe(boolean lastMinute) {
        int[] counts = lastMinute ? last : current;
        StringJoiner joined = new StringJoiner(", ");
        for (Kind kind : Kind.values()) {
            if (counts[kind.ordinal()] > 0) {
                joined.add(kind.label + " " + counts[kind.ordinal()]);
            }
        }
        return joined.length() == 0 ? "none" : joined.toString();
    }
}
