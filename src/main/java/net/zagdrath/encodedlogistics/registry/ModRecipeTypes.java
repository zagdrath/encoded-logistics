/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.recipe.LithographyRecipe;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, EncodedLogistics.MODID);

    public static final Supplier<RecipeType<LithographyRecipe>> LITHOGRAPHY = RECIPE_TYPES.register("lithography",
            () -> RecipeType.simple(EncodedLogistics.id("lithography")));

    private ModRecipeTypes() {}
}
