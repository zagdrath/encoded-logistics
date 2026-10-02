/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// A Storage Drive of one tier. Its contents are kept in DriveStorage under its drive_id; the item carries only that id
// and a cache of its fill (drive_stats). Works once it's in a Drive Bay on a network.
public class StorageDriveItem extends Item {
    private final StorageTier tier;

    public StorageDriveItem(Item.Properties properties, StorageTier tier) {
        super(properties);
        this.tier = tier;
    }

    public StorageTier getTier() {
        return tier;
    }

    public static @Nullable UUID id(ItemStack stack) {
        return stack.get(ModDataComponents.DRIVE_ID.get());
    }

    public static DriveStats stats(ItemStack stack) {
        DriveStats stats = stack.get(ModDataComponents.DRIVE_STATS.get());
        return stats != null ? stats : stack.getItem() instanceof StorageDriveItem drive ? DriveStats.empty(drive.tier) : DriveStats.empty(StorageTier.K8);
    }
}
