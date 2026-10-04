/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

// A network's storage as its parts see it: every drive in its online Drive Bays (and NAS / SAN devices) and every
// inventory its online Inventory Taps face - the hot tier. Items go in by priority (highest first; drives before taps
// on a tie), and within a priority to the places already holding that item first; they come out lowest priority first
// (taps before drives on a tie).
//
// Behind it, the cold tier (ColdTier: tapes in Tape Libraries). list() and count() are hot only; taking more of an item
// than is hot asks for the rest of it (what's on tape) to be recalled, and it can be taken once it's back.
public final class NetworkStorage {
    private final List<StorageView> fillOrder, emptyOrder;
    // Told how many items really went in or came out (not simulations): the network's item flow.
    private final LongConsumer moved;
    private final ColdTier cold;

    public NetworkStorage(List<StorageView> views) {
        this(views, count -> {}, ColdTier.NONE);
    }

    public NetworkStorage(List<StorageView> views, LongConsumer moved) {
        this(views, moved, ColdTier.NONE);
    }

    public NetworkStorage(List<StorageView> views, LongConsumer moved, ColdTier cold) {
        this.moved = moved;
        this.cold = cold;
        fillOrder = new ArrayList<>(views);
        fillOrder.sort(Comparator.comparingInt(StorageView::priority).reversed().thenComparing(StorageView::isTap));
        emptyOrder = new ArrayList<>(views);
        emptyOrder.sort(Comparator.comparingInt(StorageView::priority).thenComparing(view -> !view.isTap()));
    }

    // Everything stored hot, added up.
    public Map<ItemKey, Long> list() {
        Map<ItemKey, Long> all = new LinkedHashMap<>();
        for (StorageView view : fillOrder) {
            view.listInto(all);
        }
        return all;
    }

    public long count(ItemKey key) {
        long count = 0;
        for (StorageView view : fillOrder) {
            count += view.count(key);
        }
        return count;
    }

    // --- The cold tier ---

    public ColdTier cold() {
        return cold;
    }

    // Everything on tape, added up.
    public Map<ItemKey, Long> coldList() {
        Map<ItemKey, Long> all = new LinkedHashMap<>();
        cold.listInto(all);
        return all;
    }

    // Hot and cold, added up (what terminals show).
    public Map<ItemKey, Long> listAll() {
        Map<ItemKey, Long> all = list();
        cold.listInto(all);
        return all;
    }

    // --- Moving items ---

    // Puts up to amount of an item into the network; returns how many went in.
    public long insert(ItemKey key, long amount, boolean simulate) {
        long left = amount;
        int start = 0;
        while (start < fillOrder.size() && left > 0) {
            int priority = fillOrder.get(start).priority(), end = start;
            while (end < fillOrder.size() && fillOrder.get(end).priority() == priority) {
                end++;
            }
            // Within a priority: where it already is, then anywhere else (each place once, so a simulation adds up).
            boolean[] holds = new boolean[end - start];
            for (int i = start; i < end; i++) {
                holds[i - start] = fillOrder.get(i).count(key) > 0;
            }
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int i = start; i < end && left > 0; i++) {
                    if (holds[i - start] == (pass == 0)) {
                        left -= fillOrder.get(i).insert(key, left, simulate);
                    }
                }
            }
            start = end;
        }
        if (!simulate && amount - left > 0) {
            moved.accept(amount - left);
        }
        return amount - left;
    }

    // Takes up to amount of an item out of the network; returns how many came out. What hot storage is short of is
    // recalled from tape, if any is there (not for a simulation).
    public long extract(ItemKey key, long amount, boolean simulate) {
        long left = amount;
        for (StorageView view : emptyOrder) {
            if (left <= 0) {
                break;
            }
            left -= view.extract(key, left, simulate);
        }
        if (!simulate && amount - left > 0) {
            moved.accept(amount - left);
        }
        if (!simulate && left > 0) {
            long onTape = cold.count(key);
            if (onTape > 0) {
                cold.recall(key, Math.min(left, onTape));
            }
        }
        return amount - left;
    }

    // --- The drives (what archiving works on) ---

    // Everything the drives hold (not the taps' inventories).
    public Map<ItemKey, Long> driveContents() {
        Map<ItemKey, Long> all = new LinkedHashMap<>();
        for (StorageView view : fillOrder) {
            if (view.driveId() != null) {
                view.listInto(all);
            }
        }
        return all;
    }

    // The tick an item was last put into or taken out of any drive holding it, or -1 when none does.
    public long lastAccess(ItemKey key) {
        long last = -1;
        for (StorageView view : fillOrder) {
            if (view.driveId() != null) {
                last = Math.max(last, view.lastAccess(key));
            }
        }
        return last;
    }

    // Takes up to amount of an item out of the drives only (archiving; no recall); returns how many came out.
    public long extractFromDrives(ItemKey key, long amount, boolean simulate) {
        long left = amount;
        for (StorageView view : emptyOrder) {
            if (left <= 0) {
                break;
            }
            if (view.driveId() != null) {
                left -= view.extract(key, left, simulate);
            }
        }
        return amount - left;
    }

    // How full the drives are, 0-1, by bytes (0 with no drives).
    public double hotFill() {
        long[] bytes = hotBytes();
        return bytes[1] <= 0 ? 0 : (double) bytes[0] / bytes[1];
    }

    // The drives' bytes used and in all.
    public long[] hotBytes() {
        long used = 0, total = 0;
        for (StorageView view : fillOrder) {
            DriveStats stats = view.stats();
            if (stats != null) {
                used += stats.bytesUsed();
                total += stats.bytesTotal();
            }
        }
        return new long[] { used, total };
    }
}
