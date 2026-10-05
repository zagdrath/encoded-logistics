/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.menu.KeypunchMenu;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModSounds;

// The Keypunch (HANDOFF 5): a crafting recipe typed into its 3 x 3 grid (ghost items: nothing is used up) is punched
// onto a blank Punch Card - one card per Punch (F6), into the punched-card slot (cards of the same recipe stack there).
// It works while a Midrange System on its network is online. Its items: the blank cards and the punched ones.
public class KeypunchBlockEntity extends PeripheralBlockEntity {
    public static final String TYPE = "KEYPUNCH";
    public static final int BLANK = 0, PUNCHED = 1;
    private static final int ACTIVE_TICKS = 16;

    // Its recipe (a change saves it and works out the output again) and what it crafts.
    private final SimpleContainer grid = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            KeypunchBlockEntity.this.setChanged();
            updateResult();
        }
    };
    private final SimpleContainer result = new SimpleContainer(1);

    public KeypunchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.KEYPUNCH.get(), pos, state, 2);
    }

    @Override
    public String deviceType() {
        return TYPE;
    }

    public SimpleContainer grid() {
        return grid;
    }

    public SimpleContainer result() {
        return result;
    }

    public static boolean blank(ItemStack stack) {
        return stack.is(ModItems.PUNCH_CARD.get()) && !stack.has(ModDataComponents.PUNCHED_RECIPE.get());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == BLANK && blank(stack);
    }

    // What the grid crafts (empty for nothing).
    public void updateResult() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = grid.getItem(i);
            items.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            any |= !stack.isEmpty();
        }
        ItemStack output = ItemStack.EMPTY;
        if (any) {
            CraftingInput input = CraftingInput.of(3, 3, items);
            Optional<RecipeHolder<CraftingRecipe>> recipe = serverLevel.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, serverLevel);
            if (recipe.isPresent()) {
                output = recipe.get().value().assemble(input);
            }
        }
        result.setItem(0, output);
    }

    // The recipe on the grid, or null when it crafts nothing.
    public @Nullable Schematic schematic() {
        ItemStack output = result.getItem(0);
        if (output.isEmpty()) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            inputs.add(grid.getItem(i).copy());
        }
        return Schematic.of(Schematic.Kind.CRAFTING, inputs, List.of(output.copy()));
    }

    // Punches one card; the message line says how it went.
    public Component punch() {
        if (!isOnline()) {
            return Component.translatable("crt.encodedlogistics.machine.no_host");
        }
        Schematic schematic = schematic();
        if (schematic == null) {
            return Component.translatable("crt.encodedlogistics.keypunch.no_recipe");
        }
        ItemStack blank = getItem(BLANK);
        if (!blank(blank)) {
            return Component.translatable("crt.encodedlogistics.keypunch.no_blank");
        }
        ItemStack card = new ItemStack(ModItems.PUNCH_CARD.get());
        card.set(ModDataComponents.PUNCHED_RECIPE.get(), schematic);
        ItemStack punched = getItem(PUNCHED);
        if (punched.isEmpty()) {
            setItem(PUNCHED, card);
        } else if (ItemStack.isSameItemSameComponents(punched, card) && punched.getCount() < punched.getMaxStackSize()) {
            punched.grow(1);
        } else {
            return Component.translatable("crt.encodedlogistics.keypunch.full");
        }
        blank.shrink(1);
        setChanged();
        activate(ACTIVE_TICKS, ModSounds.KEYPUNCH_CLATTER);
        ItemStack output = schematic.output();
        return Component.translatable("crt.encodedlogistics.keypunch.punched", output.getHoverName(), output.getCount());
    }

    public void clearGrid() {
        grid.clearContent();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        updateResult();
        return new KeypunchMenu(containerId, inventory, this, grid, result, this, PeripheralMenu.Opening.SERVER);
    }

    // --- Saving: the grid too ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        List<ItemStack> saved = input.read("grid", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < 9; i++) {
            grid.setItem(i, i < saved.size() ? saved.get(i) : ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("grid", ItemStack.OPTIONAL_CODEC.listOf(), grid.getItems());
    }
}
