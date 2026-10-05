/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.menu.FabricationTerminalMenu;
import net.zagdrath.encodedlogistics.net.TerminalRecipePayload;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// JEI's "+" on a crafting recipe in a Fabrication Terminal: sends each grid slot's options to the server, which fills the
// grid from the network first, then the player's inventory (FabricationTerminalMenu.fillGrid). Before that, as AE2 does,
// the recipe shows which ingredients the network, the inventory and the grid don't have between them: red for missing,
// blue for missing but craftable on the network. The "+" still works then, moving what there is.
public class FabricationTransferHandler implements IRecipeTransferHandler<FabricationTerminalMenu, RecipeHolder<CraftingRecipe>> {
    private static final int MISSING = 0x66FF0000, CRAFTABLE = 0x660060FF;

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
        List<IRecipeSlotView> slots = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        if (!doTransfer) {
            return availability(container, slots);
        }
        List<List<ItemStack>> inputs = new ArrayList<>(9);
        for (IRecipeSlotView slot : slots) {
            if (inputs.size() < 9) {
                inputs.add(slot.getItemStacks().map(ItemStack::copy).toList());
            }
        }
        while (inputs.size() < 9) {
            inputs.add(List.of());
        }
        ClientPacketDistributor.sendToServer(new TerminalRecipePayload(container.containerId, inputs));
        return null;
    }

    // What the recipe's slots can be filled from: the network's items (as the client last heard), the player's inventory
    // and what's already in the grid (it goes back to the network first). Each slot takes one of the first option there
    // is; null when every slot is covered.
    private static @Nullable IRecipeTransferError availability(FabricationTerminalMenu menu, List<IRecipeSlotView> slots) {
        Map<StorageKey, Long> pool = new HashMap<>(menu.items());
        for (int i = 0; i < AccessTerminalMenu.INVENTORY_SLOTS + 9; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                pool.merge(StorageKey.of(stack), (long) stack.getCount(), Long::sum);
            }
        }
        List<IRecipeSlotView> missing = new ArrayList<>(), craftable = new ArrayList<>();
        for (IRecipeSlotView slot : slots) {
            List<StorageKey> options = slot.getItemStacks().filter(stack -> !stack.isEmpty()).map(StorageKey::of).toList();
            if (options.isEmpty()) {
                continue;
            }
            Optional<StorageKey> have = options.stream().filter(key -> pool.getOrDefault(key, 0L) > 0).findFirst();
            if (have.isPresent()) {
                pool.merge(have.get(), -1L, Long::sum);
            } else if (options.stream().anyMatch(menu.craftables()::contains)) {
                craftable.add(slot);
            } else {
                missing.add(slot);
            }
        }
        return missing.isEmpty() && craftable.isEmpty() ? null : new Unavailable(missing, craftable);
    }

    // Cosmetic: highlights the slots and explains in the "+" tooltip, without stopping the transfer.
    private record Unavailable(List<IRecipeSlotView> missing, List<IRecipeSlotView> craftable) implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            return missing.isEmpty() ? CRAFTABLE : MISSING;
        }

        @Override
        public void showError(GuiGraphicsExtractor graphics, int mouseX, int mouseY, IRecipeSlotsView recipeSlotsView, int recipeX, int recipeY) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(recipeX, recipeY);
            missing.forEach(slot -> slot.drawHighlight(graphics, MISSING));
            craftable.forEach(slot -> slot.drawHighlight(graphics, CRAFTABLE));
            graphics.pose().popMatrix();
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            if (!missing.isEmpty()) {
                tooltip.add(Component.translatable("jei.encodedlogistics.transfer.missing", missing.size()).withStyle(ChatFormatting.RED));
            }
            if (!craftable.isEmpty()) {
                tooltip.add(Component.translatable("jei.encodedlogistics.transfer.craftable", craftable.size()).withStyle(ChatFormatting.BLUE));
            }
            tooltip.add(Component.translatable("jei.encodedlogistics.transfer.partial").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public int getMissingCountHint() {
            return missing.size();
        }
    }
}
