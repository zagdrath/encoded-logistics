/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

// One piece of a network's storage: a Storage Drive (in a Drive Bay, NAS or SAN), or the inventory an Inventory Tap
// faces. Higher priority is filled first and emptied last; on a tie drives fill before taps. Drives are the hot tier
// archiving to tape works on (driveId, lastAccess, stats).
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

    // A Storage Drive's id, or null for anything else.
    default @Nullable UUID driveId() {
        return null;
    }

    // A drive's: the tick a type in it was last put in or taken out, -1 when it doesn't hold it.
    default long lastAccess(ItemKey key) {
        return -1;
    }

    // A drive's fill, or null.
    default @Nullable DriveStats stats() {
        return null;
    }
}
