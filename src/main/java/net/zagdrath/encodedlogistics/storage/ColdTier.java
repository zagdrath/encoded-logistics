/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.Map;

// A network's cold storage: what's archived on the tapes in its Tape Libraries. Nothing comes out of it directly - a
// recall brings items back into hot storage (the drives), where they can be taken as usual.
public interface ColdTier {
    ColdTier NONE = new ColdTier() {
        @Override
        public void listInto(Map<StorageKey, Long> all) {}

        @Override
        public long count(StorageKey key) {
            return 0;
        }

        @Override
        public void recall(StorageKey key, long amount) {}

        @Override
        public int eta(StorageKey key) {
            return -1;
        }

        @Override
        public int progress(StorageKey key) {
            return -1;
        }
    };

    // Adds what's on tape to all.
    void listInto(Map<StorageKey, Long> all);

    long count(StorageKey key);

    // Asks for up to amount of an item to be brought back to hot storage (it merges with any recall already waiting).
    void recall(StorageKey key, long amount);

    // Ticks until a recall of the item would be done (the queue ahead, loading, reading), or -1 when none can be (no
    // library holding it has a drive).
    int eta(StorageKey key);

    // 0-100 while a recall of the item is running or waiting, -1 when there's none.
    int progress(StorageKey key);

    // Whether the item's last recall couldn't all come back (hot storage full).
    default boolean hotFull(StorageKey key) {
        return false;
    }
}
