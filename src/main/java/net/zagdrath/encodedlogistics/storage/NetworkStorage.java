/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;

// A network's storage as a terminal sees it: every drive in its online Drive Bays (bays in position order, drives in
// slot order). Items go to drives that already hold that item first, then to the first drive with room; they come out
// of the last drives first.
public final class NetworkStorage {
    private record Drive(DriveBayBlockEntity bay, int slot, UUID id, StorageTier tier) {}

    private final DriveStorage data;
    private final List<Drive> drives = new ArrayList<>();

    public NetworkStorage(MinecraftServer server, List<DriveBayBlockEntity> bays) {
        this.data = DriveStorage.get(server);
        for (DriveBayBlockEntity bay : bays) {
            for (int slot = 0; slot < DriveBayBlockEntity.SLOTS; slot++) {
                ItemStack stack = bay.drive(slot);
                if (stack != null && stack.getItem() instanceof StorageDriveItem item) {
                    drives.add(new Drive(bay, slot, StorageDriveItem.id(stack), item.getTier()));
                }
            }
        }
    }

    // Everything stored, added up across the drives.
    public Map<ItemKey, Long> list() {
        Map<ItemKey, Long> all = new LinkedHashMap<>();
        for (Drive drive : drives) {
            data.contents(drive.id()).forEach((key, count) -> all.merge(key, count, Long::sum));
        }
        return all;
    }

    public long count(ItemKey key) {
        long count = 0;
        for (Drive drive : drives) {
            count += data.count(drive.id(), key);
        }
        return count;
    }

    // Puts up to amount of an item into the network; returns how many went in.
    public long insert(ItemKey key, long amount, boolean simulate) {
        long left = amount;
        // Drives that already hold it, then any drive.
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (Drive drive : drives) {
                if (left <= 0) {
                    break;
                }
                if (pass == 0 && data.count(drive.id(), key) == 0) {
                    continue;
                }
                long accepted = data.insert(drive.id(), drive.tier(), key, left, simulate);
                if (accepted > 0) {
                    left -= accepted;
                    if (!simulate) {
                        drive.bay().driveChanged(drive.slot());
                    }
                }
            }
        }
        return amount - left;
    }

    // Takes up to amount of an item out of the network; returns how many came out.
    public long extract(ItemKey key, long amount, boolean simulate) {
        long left = amount;
        for (int i = drives.size() - 1; i >= 0 && left > 0; i--) {
            Drive drive = drives.get(i);
            long taken = data.extract(drive.id(), key, left, simulate);
            if (taken > 0) {
                left -= taken;
                if (!simulate) {
                    drive.bay().driveChanged(drive.slot());
                }
            }
        }
        return amount - left;
    }
}
