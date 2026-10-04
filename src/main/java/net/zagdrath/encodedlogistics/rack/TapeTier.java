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
import java.util.function.BiConsumer;

import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;
import net.zagdrath.encodedlogistics.storage.ColdTier;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// A network's cold tier: the tapes in its online Tape Libraries (each tape once, as with copied drives), and its recall
// queue. A recall's time is guessed from where it is in the queue, the drives there are to work it, and how long one
// tape takes to load and read.
public final class TapeTier implements ColdTier {
    // A typical load + read + unload, for the queue ahead.
    private static final int OP_TICKS = 160;

    private final List<TapeLibraryDevice> libraries;
    private final DriveStorage data;
    private final TapeRecalls recalls;

    public TapeTier(List<TapeLibraryDevice> libraries, DriveStorage data, TapeRecalls recalls) {
        this.libraries = libraries;
        this.data = data;
        this.recalls = recalls;
    }

    private void forEachTape(BiConsumer<TapeLibraryDevice, TapeLibraryDevice.Tape> action) {
        Set<UUID> seen = new HashSet<>();
        for (TapeLibraryDevice library : libraries) {
            for (TapeLibraryDevice.Tape tape : library.tapes()) {
                if (seen.add(tape.id())) {
                    action.accept(library, tape);
                }
            }
        }
    }

    @Override
    public void listInto(Map<ItemKey, Long> all) {
        forEachTape((library, tape) -> data.contents(tape.id()).forEach((key, count) -> all.merge(key, count, Long::sum)));
    }

    @Override
    public long count(ItemKey key) {
        long[] count = { 0 };
        forEachTape((library, tape) -> count[0] += data.count(tape.id(), key));
        return count[0];
    }

    @Override
    public void recall(ItemKey key, long amount) {
        recalls.request(key, amount);
    }

    @Override
    public int eta(ItemKey key) {
        TapeRecalls.Running running = recalls.running(key);
        if (running != null) {
            return running.ticksLeft();
        }
        int drives = 0;
        TapeLibraryDevice.Tape holding = null;
        TapeLibraryDevice holder = null;
        for (TapeLibraryDevice library : libraries) {
            for (TapeLibraryDevice.Tape tape : library.tapes()) {
                if (data.count(tape.id(), key) > 0 && library.driveCount() > 0) {
                    drives += library.driveCount();
                    if (holding == null) {
                        holding = tape;
                        holder = library;
                    }
                    break;
                }
            }
        }
        if (holding == null) {
            return -1;
        }
        int position = recalls.position(key);
        int ahead = (position < 0 ? recalls.waiting().size() : position) + recalls.runningCount();
        long amount = Math.max(recalls.waitingAmount(key), Math.min(count(key), key.maxStackSize()));
        return ahead * OP_TICKS / Math.max(1, drives) + holder.loadTicks(holding.slot())
                + TapeLibraryDevice.workTicks(holding.generation(), amount);
    }

    @Override
    public int progress(ItemKey key) {
        TapeRecalls.Running running = recalls.running(key);
        if (running != null) {
            return running.progress();
        }
        return recalls.position(key) >= 0 ? 0 : -1;
    }

    @Override
    public boolean hotFull(ItemKey key) {
        return recalls.hotFull(key);
    }
}
