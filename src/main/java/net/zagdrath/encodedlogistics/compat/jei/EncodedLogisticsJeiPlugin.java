/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.recipe.LithographyRecipes;
import net.zagdrath.encodedlogistics.registry.ModItems;

// JEI support (only loaded when JEI is installed). Crafting recipes show up on their own; this adds the Lithography
// category (with the press as its station), the Fabricator as a crafting station, recipe transfer into the Fabrication
// Terminal's grid and the Schematic Encoder's ghost slots (any recipe), and an info page on how controllers form
// structures and what they provide.
@JeiPlugin
public class EncodedLogisticsJeiPlugin implements IModPlugin {
    private static final Identifier UID = EncodedLogistics.id("jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new LithographyCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // The recipes the server sent (EncodedLogisticsClient keeps them).
        registration.addRecipes(LithographyCategory.TYPE, LithographyRecipes.clientRecipes());
        registration.addItemStackInfo(new ItemStack(ModItems.NETWORK_CONTROLLER.get()),
                Component.translatable("jei.encodedlogistics.network_controller.info"));
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new FabricationTransferHandler(), RecipeTypes.CRAFTING);
        registration.addUniversalRecipeTransferHandler(new EncoderTransferHandler());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(LithographyCategory.TYPE, ModItems.LITHOGRAPHY_PRESS.get());
        registration.addCraftingStation(RecipeTypes.CRAFTING, ModItems.FABRICATOR.get());
    }
}
