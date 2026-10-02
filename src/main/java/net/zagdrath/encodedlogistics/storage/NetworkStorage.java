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

// A network's storage as its parts see it: every drive in its online Drive Bays and every inventory its online
// Inventory Taps face. Items go in by priority (highest first; drives before taps on a tie), and within a priority to the
// places already holding that item first; they come out lowest priority first (taps before drives on a tie).
public final class NetworkStorage {
    private final List<StorageView> fillOrder, emptyOrder;

    public NetworkStorage(List<StorageView> views) {
        fillOrder = new ArrayList<>(views);
        fillOrder.sort(Comparator.comparingInt(StorageView::priority).reversed().thenComparing(StorageView::isTap));
        emptyOrder = new ArrayList<>(views);
        emptyOrder.sort(Comparator.comparingInt(StorageView::priority).thenComparing(view -> !view.isTap()));
    }

    // Everything stored, added up.
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
        return amount - left;
    }

    // Takes up to amount of an item out of the network; returns how many came out.
    public long extract(ItemKey key, long amount, boolean simulate) {
        long left = amount;
        for (StorageView view : emptyOrder) {
            if (left <= 0) {
                break;
            }
            left -= view.extract(key, left, simulate);
        }
        return amount - left;
    }
}
