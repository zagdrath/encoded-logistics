/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.DeferredItem;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.registry.ModItems;

// The metal dusts: each is in #c:dusts and #c:dusts/<metal>, and smelts and blasts into its ingot. Their Arc Crusher
// recipes only load with Arcforge installed. The storage blocks: nine ingots (or Ferrite) craft one, which is in
// #c:storage_blocks/<name> and crafts back into nine.
final class MaterialGameTests {
    private record Metal(String name, DeferredItem<Item> dust, DeferredItem<Item> ingot) {}

    private static final List<Metal> METALS = List.of(new Metal("neodymium", ModItems.NEODYMIUM_DUST, ModItems.NEODYMIUM_INGOT),
            new Metal("tantalum", ModItems.TANTALUM_DUST, ModItems.TANTALUM_INGOT), new Metal("gallium", ModItems.GALLIUM_DUST, ModItems.GALLIUM_INGOT));

    private record Storage(String name, ItemLike unit, ItemLike block) {}

    private static final List<Storage> STORAGE = List.of(new Storage("neodymium", ModItems.NEODYMIUM_INGOT, ModItems.NEODYMIUM_BLOCK),
            new Storage("tantalum", ModItems.TANTALUM_INGOT, ModItems.TANTALUM_BLOCK), new Storage("gallium", ModItems.GALLIUM_INGOT, ModItems.GALLIUM_BLOCK),
            new Storage("ferrite", ModItems.FERRITE, ModItems.FERRITE_BLOCK));

    private MaterialGameTests() {}

    static void dusts(GameTestHelper helper) {
        RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
        boolean arcforge = ModList.get().isLoaded("arcforge");
        for (Metal metal : METALS) {
            ItemStack dust = new ItemStack(metal.dust().get());
            helper.assertTrue(dust.is(Tags.Items.DUSTS), metal.name() + " dust isn't in #c:dusts");
            helper.assertTrue(dust.is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "dusts/" + metal.name()))),
                    metal.name() + " dust isn't in #c:dusts/" + metal.name());
            SingleRecipeInput input = new SingleRecipeInput(dust);
            for (RecipeType<? extends AbstractCookingRecipe> type : List.of(RecipeType.SMELTING, RecipeType.BLASTING)) {
                Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> recipe = recipes.getRecipeFor(type, input, helper.getLevel());
                helper.assertTrue(recipe.isPresent(), "No " + type + " recipe for " + metal.name() + " dust");
                helper.assertTrue(recipe.get().value().assemble(input).is(metal.ingot().get()), metal.name() + " dust doesn't " + type + " into its ingot");
            }
            ResourceKey<Recipe<?>> crushing = ResourceKey.create(Registries.RECIPE,
                    EncodedLogistics.id("crushing/" + metal.name() + "_dust_from_" + metal.name() + "_ore"));
            helper.assertTrue(recipes.byKey(crushing).isPresent() == arcforge,
                    "Crushing recipe for " + metal.name() + (arcforge ? " missing with" : " loaded without") + " Arcforge");
        }
        helper.succeed();
    }

    static void storageBlocks(GameTestHelper helper) {
        RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
        for (Storage storage : STORAGE) {
            ItemStack block = new ItemStack(storage.block());
            helper.assertTrue(block.is(Tags.Items.STORAGE_BLOCKS), storage.name() + " block isn't in #c:storage_blocks");
            helper.assertTrue(block.is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "storage_blocks/" + storage.name()))),
                    storage.name() + " block isn't in #c:storage_blocks/" + storage.name());
            CraftingInput nine = CraftingInput.of(3, 3, Collections.nCopies(9, new ItemStack(storage.unit())));
            ItemStack packed = recipes.getRecipeFor(RecipeType.CRAFTING, nine, helper.getLevel()).map(recipe -> recipe.value().assemble(nine))
                    .orElse(ItemStack.EMPTY);
            helper.assertTrue(packed.is(storage.block().asItem()) && packed.getCount() == 1, "Nine " + storage.name() + " made " + packed);
            CraftingInput one = CraftingInput.of(1, 1, List.of(block));
            ItemStack unpacked = recipes.getRecipeFor(RecipeType.CRAFTING, one, helper.getLevel()).map(recipe -> recipe.value().assemble(one))
                    .orElse(ItemStack.EMPTY);
            helper.assertTrue(unpacked.is(storage.unit().asItem()) && unpacked.getCount() == 9, storage.name() + " block made " + unpacked);
        }
        helper.succeed();
    }
}
