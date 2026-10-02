/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.Map;

// One piece of a network's storage: a Storage Drive in a Drive Bay, or the inventory an Inventory Tap faces. Higher
// priority is filled first and emptied last; on a tie drives fill before taps.
public interface StorageView {
    int priority();

    boolean isTap();

    // Adds what it holds to all.
    void listInto(Map<ItemKey, Long> all);

    long count(ItemKey key);

    // Puts up to amount in; returns how many fit.
    long insert(ItemKey key, long amount, boolean simulate);

    // Takes up to amount out; returns how many it had.
    long extract(ItemKey key, long amount, boolean simulate);
}
