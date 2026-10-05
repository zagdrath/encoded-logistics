/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.Map;
import java.util.UUID;

// A Storage Drive in a Drive Bay slot (or a Disk Drive), as network storage (priority 0): an item, fluid or pressurized
// drive, which holds only its own type. Changes refresh the drive's stats in its holder.
public record DriveView(DriveStorage data, DriveHolder bay, int slot, UUID id, StorageTier tier, ResourceType type) implements StorageView {
    public DriveView(DriveStorage data, DriveHolder bay, int slot, UUID id, StorageTier tier) {
        this(data, bay, slot, id, tier, ResourceType.ITEM);
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean isTap() {
        return false;
    }

    @Override
    public void listInto(Map<StorageKey, Long> all) {
        data.contents(id).forEach((key, count) -> all.merge(key, count, Long::sum));
    }

    @Override
    public long count(StorageKey key) {
        return data.count(id, key);
    }

    @Override
    public long insert(StorageKey key, long amount, boolean simulate) {
        long accepted = data.insert(id, tier, type, key, amount, simulate);
        if (accepted > 0 && !simulate) {
            bay.driveChanged(slot);
        }
        return accepted;
    }

    @Override
    public ResourceType driveType() {
        return type;
    }

    @Override
    public UUID driveId() {
        return id;
    }

    @Override
    public long lastAccess(StorageKey key) {
        return data.lastAccess(id, key);
    }

    @Override
    public DriveStats stats() {
        return data.stats(id, tier, type);
    }

    @Override
    public long extract(StorageKey key, long amount, boolean simulate) {
        long taken = data.extract(id, key, amount, simulate);
        if (taken > 0 && !simulate) {
            bay.driveChanged(slot);
        }
        return taken;
    }
}
