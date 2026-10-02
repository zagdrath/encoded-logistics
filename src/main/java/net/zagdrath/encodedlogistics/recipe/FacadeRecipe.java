/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.item.CableFacadeItem;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModRecipeSerializers;

// Shapeless: a blank Cable Facade and one block a facade can copy (CableFacadeItem.canCopy) make a facade of that block.
// The block is used up, AE2-style.
public class FacadeRecipe extends CustomRecipe {
    public static final FacadeRecipe INSTANCE = new FacadeRecipe();
    public static final MapCodec<FacadeRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, FacadeRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<FacadeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return target(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        BlockState target = target(input);
        return target != null ? CableFacadeItem.of(ModItems.CABLE_FACADE.get(), target) : ItemStack.EMPTY;
    }

    // The block to copy when the grid holds exactly a blank facade and one block it can copy; otherwise null.
    private static BlockState target(CraftingInput input) {
        boolean blank = false;
        BlockState target = null;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.CABLE_FACADE.get()) && CableFacadeItem.target(stack) == null && !blank) {
                blank = true;
            } else if (target == null) {
                target = CableFacadeItem.targetFor(stack);
                if (target == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return blank ? target : null;
    }

    @Override
    public RecipeSerializer<FacadeRecipe> getSerializer() {
        return ModRecipeSerializers.FACADE.get();
    }
}
