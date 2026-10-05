/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;
import net.zagdrath.encodedlogistics.menu.SchematicEncoderMenu;
import net.zagdrath.encodedlogistics.net.EncoderRecipePayload;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// JEI's "+" on any recipe in a Schematic Encoder: a crafting recipe fills the ghost grid in crafting mode (slot by slot);
// anything else becomes a processing schematic - its inputs (up to nine) and outputs (up to three), with their amounts.
// Only ghost items are set; nothing is taken from anywhere.
public class EncoderTransferHandler implements IUniversalRecipeTransferHandler<SchematicEncoderMenu> {
    @Override
    public Class<? extends SchematicEncoderMenu> getContainerClass() {
        return SchematicEncoderMenu.class;
    }

    @Override
    public Optional<MenuType<SchematicEncoderMenu>> getMenuType() {
        return Optional.of(ModMenuTypes.SCHEMATIC_ENCODER.get());
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(SchematicEncoderMenu container, Object recipe, IRecipeSlotsView recipeSlots, Player player,
            boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            boolean crafting = recipe instanceof RecipeHolder<?> holder && holder.value() instanceof CraftingRecipe;
            List<ItemStack> inputs = stacks(recipeSlots.getSlotViews(RecipeIngredientRole.INPUT), 9, crafting);
            List<ItemStack> outputs = stacks(recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT), 3, false);
            ClientPacketDistributor.sendToServer(new EncoderRecipePayload(container.containerId, crafting, inputs, outputs));
        }
        return null;
    }

    // The item each slot shows, up to limit; keepEmpty: empty slots keep their place (a crafting grid).
    private static List<ItemStack> stacks(List<IRecipeSlotView> views, int limit, boolean keepEmpty) {
        List<ItemStack> stacks = new ArrayList<>();
        for (IRecipeSlotView view : views) {
            if (stacks.size() >= limit) {
                break;
            }
            ItemStack stack = view.getDisplayedItemStack().or(() -> view.getItemStacks().findFirst()).map(ItemStack::copy).orElse(ItemStack.EMPTY);
            // A fluid (or a gas, which JEI shows as a fluid): a fluid or gas entry with the recipe's amount (Processing only).
            Optional<FluidStack> fluid = view.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK)
                    .or(() -> view.getIngredients(NeoForgeTypes.FLUID_STACK).findFirst());
            if (stack.isEmpty() && fluid.isPresent() && !fluid.get().isEmpty() && !keepEmpty) {
                stack = ResourceEntryItem.of(StorageKey.fluid(FluidResource.of(fluid.get())), Math.max(1, fluid.get().getAmount()));
            }
            if (!stack.isEmpty() || keepEmpty) {
                stacks.add(stack);
            }
        }
        return stacks;
    }
}
