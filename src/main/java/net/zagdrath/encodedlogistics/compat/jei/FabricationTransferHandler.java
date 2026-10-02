/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.menu.FabricationTerminalMenu;
import net.zagdrath.encodedlogistics.net.TerminalRecipePayload;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// JEI's "+" on a crafting recipe in a Fabrication Terminal: sends each grid slot's options to the server, which fills the
// grid from the network first, then the player's inventory (FabricationTerminalMenu.fillGrid).
public class FabricationTransferHandler implements IRecipeTransferHandler<FabricationTerminalMenu, RecipeHolder<CraftingRecipe>> {
    @Override
    public Class<? extends FabricationTerminalMenu> getContainerClass() {
        return FabricationTerminalMenu.class;
    }

    @Override
    public Optional<MenuType<FabricationTerminalMenu>> getMenuType() {
        return Optional.of(ModMenuTypes.FABRICATION_TERMINAL.get());
    }

    @Override
    public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(FabricationTerminalMenu container, RecipeHolder<CraftingRecipe> recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            List<List<ItemStack>> inputs = new ArrayList<>(9);
            for (IRecipeSlotView slot : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
                if (inputs.size() < 9) {
                    inputs.add(slot.getItemStacks().map(ItemStack::copy).toList());
                }
            }
            while (inputs.size() < 9) {
                inputs.add(List.of());
            }
            ClientPacketDistributor.sendToServer(new TerminalRecipePayload(container.containerId, inputs));
        }
        return null;
    }
}
