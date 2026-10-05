/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.storage.DriveCapacity;
import net.zagdrath.encodedlogistics.storage.DriveStats;

// A 7-track Tape Reel (HANDOFF 5): cold storage on a Tape Drive, tapeReelItems items of up to tapeReelTypes types. As an
// LTO tape does, it keeps its contents in DriveStorage under its drive_id (given when it's first mounted) and carries
// that id and a cache of its fill (drive_stats). Its tooltip (ItemInfoTooltips): "18,420 / 65,536 items".
public class TapeReelItem extends Item {
    // A reel's capacity: 8 items a byte, no bytes reserved for types (a reel's limit on them is its own).
    public static final DriveCapacity CAPACITY = new DriveCapacity() {
        @Override
        public long bytes() {
            return Math.max(1, Config.TAPE_REEL_ITEMS.getAsInt() / 8);
        }

        @Override
        public long bytesPerType() {
            return 0;
        }

        @Override
        public int typeLimit() {
            return Config.TAPE_REEL_TYPES.getAsInt();
        }
    };

    public TapeReelItem(Item.Properties properties) {
        super(properties);
    }

    public static @Nullable UUID id(ItemStack stack) {
        return stack.get(ModDataComponents.DRIVE_ID.get());
    }

    public static DriveStats stats(ItemStack stack) {
        DriveStats stats = stack.get(ModDataComponents.DRIVE_STATS.get());
        return stats != null ? stats : DriveStats.empty(CAPACITY);
    }

    // Its volume serial (VOL001-VOL999), from its id; VOL000 before it has one.
    public static String volume(ItemStack stack) {
        UUID id = id(stack);
        return id == null ? "VOL000" : String.format(Locale.ROOT, "VOL%03d", 1 + Math.floorMod(id.getLeastSignificantBits(), 999));
    }
}
