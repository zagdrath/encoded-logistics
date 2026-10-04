/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.storage.ItemKey;

// A network's tape traffic (kept with its live state, not saved): the recalls waiting for a Tape Library drive, oldest
// first - an item asked for again while it waits takes the larger amount - the ones running (with their progress, which
// their library keeps up to date), and the items being archived, so two libraries never pick the same one. A library
// takes the oldest waiting recall it holds the item for (claim), runs it, and reports how it went (finish).
public final class TapeRecalls {
    // A recall on a drive: how many, and how far along (ticks done of total).
    public static final class Running {
        public final long amount;
        int done, total;

        Running(long amount, int total) {
            this.amount = amount;
            this.total = Math.max(1, total);
        }

        public int progress() {
            return Math.min(100, done * 100 / total);
        }

        public int ticksLeft() {
            return Math.max(0, total - done);
        }
    }

    private final Map<ItemKey, Long> waiting = new LinkedHashMap<>();
    private final Map<ItemKey, Running> running = new HashMap<>();
    private final Set<ItemKey> archiving = new HashSet<>();
    // Items whose last recall couldn't all come back (hot storage full).
    private final Set<ItemKey> hotFull = new HashSet<>();

    // Asks for amount of an item back; merges with a recall already waiting, or adds to one running for less.
    public void request(ItemKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        Running run = running.get(key);
        long more = run != null ? amount - run.amount : amount;
        if (more > 0) {
            waiting.merge(key, more, Math::max);
        }
    }

    public List<ItemKey> waiting() {
        return new ArrayList<>(waiting.keySet());
    }

    public long waitingAmount(ItemKey key) {
        return waiting.getOrDefault(key, 0L);
    }

    // Where an item is in the queue (0 first), or -1 when it isn't waiting.
    public int position(ItemKey key) {
        int at = 0;
        for (ItemKey waited : waiting.keySet()) {
            if (waited.equals(key)) {
                return at;
            }
            at++;
        }
        return -1;
    }

    // A library starts a recall of up to amount of an item, taking it off the queue; total: the ticks it'll take.
    public Running claim(ItemKey key, long amount, int total) {
        waiting.remove(key);
        hotFull.remove(key);
        Running run = new Running(amount, total);
        running.put(key, run);
        return run;
    }

    public @Nullable Running running(ItemKey key) {
        return running.get(key);
    }

    public void progress(ItemKey key, int done, int total) {
        Running run = running.get(key);
        if (run != null) {
            run.done = done;
            run.total = Math.max(1, total);
        }
    }

    // A recall finished (or was dropped): short means hot storage couldn't take it all.
    public void finish(ItemKey key, boolean short_) {
        running.remove(key);
        if (short_) {
            hotFull.add(key);
        }
    }

    public boolean hotFull(ItemKey key) {
        return hotFull.contains(key);
    }

    public int runningCount() {
        return running.size();
    }

    // --- Archiving ---

    // Claims an item for archiving; false when another library already is.
    public boolean startArchiving(ItemKey key) {
        return archiving.add(key);
    }

    public void stopArchiving(ItemKey key) {
        archiving.remove(key);
    }

    public boolean busy(ItemKey key) {
        return archiving.contains(key) || running.containsKey(key);
    }
}
