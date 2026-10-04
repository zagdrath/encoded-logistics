/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.recipe;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.zagdrath.encodedlogistics.item.LtoTapeItem;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModRecipeSerializers;

// A shapeless recipe that upgrades an LTO tape: the new tape takes the old one's id, so it keeps what's on it (its fill
// is worked out again once it's in a library). Written as a shapeless recipe, type encodedlogistics:tape_upgrade.
public class TapeUpgradeRecipe extends ShapelessRecipe {
    public static final MapCodec<TapeUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.common),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.book),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.output),
            Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(recipe -> recipe.inputs))
            .apply(i, TapeUpgradeRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TapeUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.common,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.book,
            ItemStackTemplate.STREAM_CODEC, recipe -> recipe.output,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), recipe -> recipe.inputs,
            TapeUpgradeRecipe::new);
    public static final RecipeSerializer<TapeUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Recipe.CommonInfo common;
    private final CraftingRecipe.CraftingBookInfo book;
    private final ItemStackTemplate output;
    private final List<Ingredient> inputs;

    public TapeUpgradeRecipe(Recipe.CommonInfo common, CraftingRecipe.CraftingBookInfo book, ItemStackTemplate output, List<Ingredient> inputs) {
        super(common, book, output, inputs);
        this.common = common;
        this.book = book;
        this.output = output;
        this.inputs = inputs;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = super.assemble(input);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.getItem() instanceof LtoTapeItem && LtoTapeItem.id(stack) != null) {
                result.set(ModDataComponents.DRIVE_ID.get(), LtoTapeItem.id(stack));
                break;
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<ShapelessRecipe> getSerializer() {
        return (RecipeSerializer<ShapelessRecipe>) (RecipeSerializer<?>) ModRecipeSerializers.TAPE_UPGRADE.get();
    }
}
