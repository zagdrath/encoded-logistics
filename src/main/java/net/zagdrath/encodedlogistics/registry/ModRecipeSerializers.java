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
import net.zagdrath.encodedlogistics.recipe.FacadeRecipe;
import net.zagdrath.encodedlogistics.recipe.LithographyRecipe;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER,
            EncodedLogistics.MODID);

    public static final Supplier<RecipeSerializer<FacadeRecipe>> FACADE = RECIPE_SERIALIZERS.register("facade", () -> FacadeRecipe.SERIALIZER);

    public static final Supplier<RecipeSerializer<LithographyRecipe>> LITHOGRAPHY = RECIPE_SERIALIZERS.register("lithography",
            () -> LithographyRecipe.SERIALIZER);

    private ModRecipeSerializers() {}
}
