/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.registry.ModRecipeSerializers;
import net.zagdrath.encodedlogistics.registry.ModRecipeTypes;

// encodedlogistics:lithography - the Lithography Press etching a wafer with an additive through a photomask:
//   {"type": "encodedlogistics:lithography", "wafer": <ingredient>, "additive": <ingredient>, "photomask": <ingredient>,
//    "result": {"id": ..., "count": 1}, "energy": 4000, "time": 100}
// It consumes one wafer and one additive; the photomask is never used up. energy FE is spent evenly over time ticks.
public record LithographyRecipe(Ingredient wafer, Ingredient additive, Ingredient photomask, ItemStackTemplate result, int energy, int time)
        implements Recipe<LithographyInput> {
    public static final MapCodec<LithographyRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("wafer").forGetter(LithographyRecipe::wafer),
            Ingredient.CODEC.fieldOf("additive").forGetter(LithographyRecipe::additive),
            Ingredient.CODEC.fieldOf("photomask").forGetter(LithographyRecipe::photomask),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(LithographyRecipe::result),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("energy", 4000).forGetter(LithographyRecipe::energy),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("time", 100).forGetter(LithographyRecipe::time))
            .apply(i, LithographyRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LithographyRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, LithographyRecipe::wafer,
            Ingredient.CONTENTS_STREAM_CODEC, LithographyRecipe::additive,
            Ingredient.CONTENTS_STREAM_CODEC, LithographyRecipe::photomask,
            ItemStackTemplate.STREAM_CODEC, LithographyRecipe::result,
            ByteBufCodecs.VAR_INT, LithographyRecipe::energy,
            ByteBufCodecs.VAR_INT, LithographyRecipe::time,
            LithographyRecipe::new);

    public static final RecipeSerializer<LithographyRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public boolean matches(LithographyInput input, Level level) {
        return wafer.test(input.wafer()) && additive.test(input.additive()) && photomask.test(input.photomask());
    }

    @Override
    public ItemStack assemble(LithographyInput input) {
        return result.create();
    }

    // FE spent on the given tick of the recipe (0-based), so that the ticks add up to exactly energy.
    public int energyOnTick(int tick) {
        return (int) ((long) energy * (tick + 1) / time - (long) energy * tick / time);
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<LithographyRecipe> getSerializer() {
        return ModRecipeSerializers.LITHOGRAPHY.get();
    }

    @Override
    public RecipeType<LithographyRecipe> getType() {
        return ModRecipeTypes.LITHOGRAPHY.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
}
