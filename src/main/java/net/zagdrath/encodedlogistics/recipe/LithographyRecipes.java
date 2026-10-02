/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.recipe;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.registry.ModRecipeTypes;

// Looking up lithography recipes on either side: the server's recipe manager, or on the client the recipes the server
// sent (EncodedLogistics sends them on datapack sync; the client keeps them for the press's slots and JEI).
public final class LithographyRecipes {
    private static volatile List<RecipeHolder<LithographyRecipe>> client = List.of();

    private LithographyRecipes() {}

    public static Collection<RecipeHolder<LithographyRecipe>> all(Level level) {
        return level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess().recipeMap().byType(ModRecipeTypes.LITHOGRAPHY.get()) : client;
    }

    // Client side: the recipes the server sent.
    public static void setClientRecipes(Collection<RecipeHolder<LithographyRecipe>> recipes) {
        client = List.copyOf(recipes);
    }

    public static List<RecipeHolder<LithographyRecipe>> clientRecipes() {
        return client;
    }

    public static Optional<RecipeHolder<LithographyRecipe>> find(Level level, LithographyInput input) {
        for (RecipeHolder<LithographyRecipe> recipe : all(level)) {
            if (recipe.value().matches(input, level)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static boolean isWafer(Level level, ItemStack stack) {
        return !stack.isEmpty() && all(level).stream().anyMatch(recipe -> recipe.value().wafer().test(stack));
    }

    public static boolean isAdditive(Level level, ItemStack stack) {
        return !stack.isEmpty() && all(level).stream().anyMatch(recipe -> recipe.value().additive().test(stack));
    }

    public static boolean isPhotomask(Level level, ItemStack stack) {
        return !stack.isEmpty() && all(level).stream().anyMatch(recipe -> recipe.value().photomask().test(stack));
    }
}
