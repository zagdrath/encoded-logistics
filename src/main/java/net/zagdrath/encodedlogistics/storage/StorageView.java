/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;

// One piece of a network's storage: a Storage Drive (in a Drive Bay, NAS or SAN), or the inventory, tanks and gas tanks
// an Inventory Tap faces. Each holds whatever resource types it can (StorageKey.type()) and takes none of the others.
// Higher priority is filled first and emptied last; on a tie drives fill before taps. Item drives are the hot tier
// archiving to tape works on (driveId, lastAccess, stats).
public interface StorageView {
    int priority();

    boolean isTap();

    // Adds what it holds to all.
    void listInto(Map<StorageKey, Long> all);

    long count(StorageKey key);

    // Puts up to amount in; returns how many fit.
    long insert(StorageKey key, long amount, boolean simulate);

    // Takes up to amount out; returns how many it had.
    long extract(StorageKey key, long amount, boolean simulate);

    // A drive's resource type: what its stats() count (an item, fluid or pressurized drive).
    default ResourceType driveType() {
        return ResourceType.ITEM;
    }

    // A Storage Drive's id, or null for anything else.
    default @Nullable UUID driveId() {
        return null;
    }

    // A drive's: the tick a type in it was last put in or taken out, -1 when it doesn't hold it.
    default long lastAccess(StorageKey key) {
        return -1;
    }

    // A drive's fill, or null.
    default @Nullable DriveStats stats() {
        return null;
    }

    // Another segment's storage seen through a Share route (SharedView): after the network's own, and named for where
    // it's from.
    default boolean isShared() {
        return false;
    }

    default @Nullable Component sharedFrom() {
        return null;
    }
}
