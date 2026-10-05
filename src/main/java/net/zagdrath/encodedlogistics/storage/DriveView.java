/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.Map;
import java.util.UUID;

// A Storage Drive in a Drive Bay slot (or a Disk Drive), as network storage (priority 0). Changes refresh the drive's
// stats in its holder.
public record DriveView(DriveStorage data, DriveHolder bay, int slot, UUID id, StorageTier tier) implements StorageView {
    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean isTap() {
        return false;
    }

    @Override
    public void listInto(Map<ItemKey, Long> all) {
        data.contents(id).forEach((key, count) -> all.merge(key, count, Long::sum));
    }

    @Override
    public long count(ItemKey key) {
        return data.count(id, key);
    }

    @Override
    public long insert(ItemKey key, long amount, boolean simulate) {
        long accepted = data.insert(id, tier, key, amount, simulate);
        if (accepted > 0 && !simulate) {
            bay.driveChanged(slot);
        }
        return accepted;
    }

    @Override
    public UUID driveId() {
        return id;
    }

    @Override
    public long lastAccess(ItemKey key) {
        return data.lastAccess(id, key);
    }

    @Override
    public DriveStats stats() {
        return data.stats(id, tier);
    }

    @Override
    public long extract(ItemKey key, long amount, boolean simulate) {
        long taken = data.extract(id, key, amount, simulate);
        if (taken > 0 && !simulate) {
            bay.driveChanged(slot);
        }
        return taken;
    }
}
