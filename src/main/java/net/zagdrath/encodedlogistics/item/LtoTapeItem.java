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
import net.zagdrath.encodedlogistics.storage.TapeGeneration;

// An LTO tape of one generation: cold storage in a Tape Library. As a Storage Drive does, it keeps its contents in
// DriveStorage under its drive_id (given when it first goes into a library) and carries only that id and a cache of its
// fill (drive_stats) - so a tape taken out, or in a broken library, keeps what's on it until it goes back into a library.
public class LtoTapeItem extends Item {
    private final TapeGeneration generation;

    public LtoTapeItem(Item.Properties properties, TapeGeneration generation) {
        super(properties);
        this.generation = generation;
    }

    public TapeGeneration generation() {
        return generation;
    }

    public static @Nullable UUID id(ItemStack stack) {
        return stack.get(ModDataComponents.DRIVE_ID.get());
    }

    public static DriveStats stats(ItemStack stack) {
        DriveStats stats = stack.get(ModDataComponents.DRIVE_STATS.get());
        return stats != null ? stats : DriveStats.empty(stack.getItem() instanceof LtoTapeItem tape ? tape.generation : TapeGeneration.LTO_6);
    }
}
