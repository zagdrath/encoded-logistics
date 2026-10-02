/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

// What a Lithography Press has loaded: wafer (0), additive (1) and photomask (2).
public record LithographyInput(ItemStack wafer, ItemStack additive, ItemStack photomask) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return switch (index) {
            case 0 -> wafer;
            case 1 -> additive;
            case 2 -> photomask;
            default -> throw new IllegalArgumentException("No item " + index);
        };
    }

    @Override
    public int size() {
        return 3;
    }
}
