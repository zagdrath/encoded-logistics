/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.storage.DriveCopies;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.TapeGeneration;

// An LTO tape of one generation: cold storage in a Tape Library. As a Storage Drive does, it keeps its contents in
// DriveStorage under its drive_id (given when it first goes into a library) and carries only that id and a cache of its
// fill (drive_stats) - so a tape taken out, or in a broken library, keeps what's on it until it goes back into a library.
//
// Also as a Storage Drive does: new tapes stack to 64 (and copy freely), a tape with an id is one of a kind and stacks
// to 1, and one that's empty again goes back to blank in a player's inventory, so it stacks with new ones.
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

    // In a player's inventory: back to blank once nothing's on it (no id, no cached fill, its own stack size again);
    // otherwise one of a kind (a tape given its id before tapes stacked).
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        UUID id = id(stack);
        if (id == null) {
            return;
        }
        if (!DriveStorage.get(level.getServer()).contents(id).isEmpty()) {
            DriveCopies.unique(stack);
            return;
        }
        stack.remove(ModDataComponents.DRIVE_ID.get());
        stack.remove(ModDataComponents.DRIVE_STATS.get());
        // Back to the item's own stack size (removing it would leave a removal on the stack, which a new tape hasn't).
        stack.set(DataComponents.MAX_STACK_SIZE, stack.getPrototype().get(DataComponents.MAX_STACK_SIZE));
    }
}
