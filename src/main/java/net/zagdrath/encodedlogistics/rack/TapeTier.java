/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.storage.ColdTier;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// A network's cold tier: the tapes in its online Tape Libraries and the reels on its Tape Drives (TapeSource; each tape
// once, as with copied drives), and its recall queue. A recall's time is guessed from where it is in the queue, the
// drives there are to work it, and how long one tape takes to load and read.
public final class TapeTier implements ColdTier {
    // A typical load + read + unload, for the queue ahead.
    private static final int OP_TICKS = 160;

    private final List<TapeSource> libraries;
    private final DriveStorage data;
    private final TapeRecalls recalls;

    public TapeTier(List<TapeSource> libraries, DriveStorage data, TapeRecalls recalls) {
        this.libraries = libraries;
        this.data = data;
        this.recalls = recalls;
    }

    private void forEachTape(Consumer<UUID> action) {
        Set<UUID> seen = new HashSet<>();
        for (TapeSource library : libraries) {
            for (UUID tape : library.tapeIds()) {
                if (seen.add(tape)) {
                    action.accept(tape);
                }
            }
        }
    }

    @Override
    public void listInto(Map<StorageKey, Long> all) {
        forEachTape(tape -> data.contents(tape).forEach((key, count) -> all.merge(key, count, Long::sum)));
    }

    @Override
    public long count(StorageKey key) {
        long[] count = { 0 };
        forEachTape(tape -> count[0] += data.count(tape, key));
        return count[0];
    }

    @Override
    public void recall(StorageKey key, long amount) {
        recalls.request(key, amount);
    }

    @Override
    public int eta(StorageKey key) {
        TapeRecalls.Running running = recalls.running(key);
        if (running != null) {
            return running.ticksLeft();
        }
        int position = recalls.position(key);
        int ahead = (position < 0 ? recalls.waiting().size() : position) + recalls.runningCount();
        long amount = Math.max(recalls.waitingAmount(key), Math.min(count(key), key.maxStackSize()));
        int drives = 0, first = -1;
        for (TapeSource library : libraries) {
            if (library.driveCount() <= 0) {
                continue;
            }
            int ticks = library.recallTicks(data, key, amount);
            if (ticks >= 0) {
                drives += library.driveCount();
                if (first < 0) {
                    first = ticks;
                }
            }
        }
        if (first < 0) {
            return -1;
        }
        return ahead * OP_TICKS / Math.max(1, drives) + first;
    }

    // The library or drive holding the most of an item, or null.
    public @Nullable TapeSource holder(StorageKey key) {
        TapeSource best = null;
        long most = 0;
        for (TapeSource library : libraries) {
            long count = 0;
            for (UUID tape : library.tapeIds()) {
                count += data.count(tape, key);
            }
            if (count > most) {
                best = library;
                most = count;
            }
        }
        return best;
    }

    @Override
    public int progress(StorageKey key) {
        TapeRecalls.Running running = recalls.running(key);
        if (running != null) {
            return running.progress();
        }
        return recalls.position(key) >= 0 ? 0 : -1;
    }

    @Override
    public boolean hotFull(StorageKey key) {
        return recalls.hotFull(key);
    }
}
