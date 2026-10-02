/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.blockentity.LithographyPressBlockEntity;
import net.zagdrath.encodedlogistics.recipe.LithographyRecipes;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Lithography Press screen: photomask, wafer, additive and output slots (positions from
// screens/lithography_press.json) and the player's inventory. data: progress, time, energy and capacity (16-bit halves).
public class LithographyPressMenu extends AbstractContainerMenu {
    private final Container press;
    private final ContainerData data;
    private final Level level;

    // Client constructor.
    public LithographyPressMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(4), new SimpleContainerData(6));
    }

    public LithographyPressMenu(int containerId, Inventory inventory, Container press, ContainerData data) {
        super(ModMenuTypes.LITHOGRAPHY_PRESS.get(), containerId);
        checkContainerSize(press, 4);
        checkContainerDataCount(data, 6);
        this.press = press;
        this.data = data;
        this.level = inventory.player.level();
        press.startOpen(inventory.player);
        addSlot(new InputSlot(press, LithographyPressBlockEntity.PHOTOMASK, 80, 17));
        addSlot(new InputSlot(press, LithographyPressBlockEntity.WAFER, 44, 36));
        addSlot(new InputSlot(press, LithographyPressBlockEntity.ADDITIVE, 80, 59));
        addSlot(new Slot(press, LithographyPressBlockEntity.OUTPUT, 113, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    public float progress() {
        int time = data.get(1);
        return time <= 0 ? 0 : (float) data.get(0) / time;
    }

    public int energy() {
        return data.get(2) | data.get(3) << 16;
    }

    public int capacity() {
        return data.get(4) | data.get(5) << 16;
    }

    private boolean fits(int slot, ItemStack stack) {
        return switch (slot) {
            case LithographyPressBlockEntity.PHOTOMASK -> LithographyRecipes.isPhotomask(level, stack);
            case LithographyPressBlockEntity.WAFER -> LithographyRecipes.isWafer(level, stack);
            case LithographyPressBlockEntity.ADDITIVE -> LithographyRecipes.isAdditive(level, stack);
            default -> false;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 4) {
            if (!moveItemStackTo(stack, 4, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int target = 0; target < 3 && !moved; target++) {
                if (fits(target, stack)) {
                    moved = moveItemStackTo(stack, target, target + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return press.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        press.stopOpen(player);
    }

    private final class InputSlot extends Slot {
        InputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return fits(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return getContainerSlot() == LithographyPressBlockEntity.PHOTOMASK ? 1 : super.getMaxStackSize();
        }
    }
}
