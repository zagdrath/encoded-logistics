/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.recipe.LithographyRecipe;

// Every item the mod registers comes out of some loaded recipe, except the ones that come from elsewhere: ores and raw
// metals (worldgen and ore loot), dusts (crushing, only with Arcforge installed), encoded schematics (the Schematic
// Encoder writes them) and printouts (the Line Printer prints them).
final class RecipeGameTests {
    private RecipeGameTests() {}

    private static boolean obtainedElsewhere(String path) {
        return path.endsWith("_ore") || path.startsWith("raw_") || path.endsWith("_dust") || path.startsWith("encoded_schematic_") || path.equals("printout")
                || path.equals("resource_entry");
    }

    static void everyItemCraftable(GameTestHelper helper) {
        Set<Identifier> made = new HashSet<>();
        for (RecipeHolder<?> holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe instanceof LithographyRecipe lithography) {
                made.add(BuiltInRegistries.ITEM.getKey(lithography.result().item().value()));
            }
            for (RecipeDisplay display : recipe.display()) {
                if (display.result() instanceof SlotDisplay.ItemStackSlotDisplay stack) {
                    made.add(BuiltInRegistries.ITEM.getKey(stack.stack().item().value()));
                } else if (display.result() instanceof SlotDisplay.ItemSlotDisplay item) {
                    made.add(BuiltInRegistries.ITEM.getKey(item.item().value()));
                }
            }
        }
        List<Identifier> missing = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals(EncodedLogistics.MODID) && !obtainedElsewhere(id.getPath()) && !made.contains(id))
                .sorted()
                .toList();
        helper.assertTrue(missing.isEmpty(), "No recipe makes " + missing);
        helper.succeed();
    }
}
