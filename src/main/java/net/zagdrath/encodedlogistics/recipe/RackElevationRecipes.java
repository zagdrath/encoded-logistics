/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.item.RackElevationItem;
import net.zagdrath.encodedlogistics.rack.RackTemplate;
import net.zagdrath.encodedlogistics.registry.ModRecipeSerializers;

// The Rack Elevation's special crafting recipes (data/encodedlogistics/recipe/rack_elevation_{copy,clear}.json):
// - copy: a written one and a blank one make two written ones (the written one stays in the grid, as a book's original
//   does when it's copied);
// - clear: a written one alone goes back to blank.
public final class RackElevationRecipes {
    private RackElevationRecipes() {}

    private static boolean isElevation(ItemStack stack) {
        return stack.getItem() instanceof RackElevationItem;
    }

    public static final class Copy extends CustomRecipe {
        public static final Copy INSTANCE = new Copy();
        public static final RecipeSerializer<Copy> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE),
                StreamCodec.<RegistryFriendlyByteBuf, Copy>unit(INSTANCE));

        private Copy() {}

        // The written one and the blank one, or null when the grid is anything else.
        private static ItemStack written(CraftingInput input) {
            ItemStack written = ItemStack.EMPTY;
            int blank = 0;
            for (int slot = 0; slot < input.size(); slot++) {
                ItemStack stack = input.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!isElevation(stack)) {
                    return ItemStack.EMPTY;
                }
                if (RackTemplate.of(stack) != null) {
                    if (!written.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                    written = stack;
                } else {
                    blank++;
                }
            }
            return blank == 1 ? written : ItemStack.EMPTY;
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            return !written(input).isEmpty();
        }

        @Override
        public ItemStack assemble(CraftingInput input) {
            ItemStack written = written(input);
            return written.isEmpty() ? ItemStack.EMPTY : written.copyWithCount(1);
        }

        // The original stays.
        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < input.size(); slot++) {
                ItemStack stack = input.getItem(slot);
                ItemStackTemplate remainder = stack.getCraftingRemainder();
                if (remainder != null) {
                    remaining.set(slot, remainder.create());
                } else if (isElevation(stack) && RackTemplate.of(stack) != null) {
                    remaining.set(slot, stack.copyWithCount(1));
                }
            }
            return remaining;
        }

        @Override
        public RecipeSerializer<Copy> getSerializer() {
            return ModRecipeSerializers.RACK_ELEVATION_COPY.get();
        }
    }

    public static final class Clear extends CustomRecipe {
        public static final Clear INSTANCE = new Clear();
        public static final RecipeSerializer<Clear> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE),
                StreamCodec.<RegistryFriendlyByteBuf, Clear>unit(INSTANCE));

        private Clear() {}

        private static ItemStack written(CraftingInput input) {
            ItemStack written = ItemStack.EMPTY;
            for (int slot = 0; slot < input.size(); slot++) {
                ItemStack stack = input.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!written.isEmpty() || !isElevation(stack) || RackTemplate.of(stack) == null) {
                    return ItemStack.EMPTY;
                }
                written = stack;
            }
            return written;
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            return !written(input).isEmpty();
        }

        @Override
        public ItemStack assemble(CraftingInput input) {
            ItemStack written = written(input);
            if (written.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack blank = written.copyWithCount(1);
            RackTemplate.clear(blank);
            return blank;
        }

        @Override
        public RecipeSerializer<Clear> getSerializer() {
            return ModRecipeSerializers.RACK_ELEVATION_CLEAR.get();
        }
    }
}
