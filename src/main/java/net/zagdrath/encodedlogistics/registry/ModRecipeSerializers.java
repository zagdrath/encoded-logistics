/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.recipe.LithographyRecipe;
import net.zagdrath.encodedlogistics.recipe.RackElevationRecipes;
import net.zagdrath.encodedlogistics.recipe.TapeUpgradeRecipe;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER,
            EncodedLogistics.MODID);

    public static final Supplier<RecipeSerializer<LithographyRecipe>> LITHOGRAPHY = RECIPE_SERIALIZERS.register("lithography",
            () -> LithographyRecipe.SERIALIZER);

    public static final Supplier<RecipeSerializer<TapeUpgradeRecipe>> TAPE_UPGRADE = RECIPE_SERIALIZERS.register("tape_upgrade",
            () -> TapeUpgradeRecipe.SERIALIZER);

    // The Rack Elevation's special recipes: copy (written + blank) and clear (written alone).
    public static final Supplier<RecipeSerializer<RackElevationRecipes.Copy>> RACK_ELEVATION_COPY = RECIPE_SERIALIZERS.register("rack_elevation_copy",
            () -> RackElevationRecipes.Copy.SERIALIZER);

    public static final Supplier<RecipeSerializer<RackElevationRecipes.Clear>> RACK_ELEVATION_CLEAR = RECIPE_SERIALIZERS.register(
            "rack_elevation_clear", () -> RackElevationRecipes.Clear.SERIALIZER);

    private ModRecipeSerializers() {}
}
