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
import net.zagdrath.encodedlogistics.blockentity.FabricatorBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Fabricator's screen (screens/fabricator.json): its 3x3 Crafting Schematic slots, two Throughput Module slots, the
// craft's progress and the player's inventory. data: progress, duration.
public class FabricatorMenu extends AbstractContainerMenu {
    public static final int SCHEMATICS_X = 45, SCHEMATICS_Y = 18, MODULE_X = 153, INVENTORY_Y = 84;
    public static final int[] MODULE_Y = { 27, 45 };
    private static final int INVENTORY = FabricatorBlockEntity.SLOTS;

    private final Container fabricator;
    private final ContainerData data;

    public FabricatorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(FabricatorBlockEntity.SLOTS), new SimpleContainerData(2));
    }

    public FabricatorMenu(int containerId, Inventory inventory, Container fabricator, ContainerData data) {
        super(ModMenuTypes.FABRICATOR.get(), containerId);
        checkContainerSize(fabricator, FabricatorBlockEntity.SLOTS);
        checkContainerDataCount(data, 2);
        this.fabricator = fabricator;
        this.data = data;
        fabricator.startOpen(inventory.player);
        for (int slot = 0; slot < FabricatorBlockEntity.SCHEMATIC_SLOTS; slot++) {
            addSlot(new FilteredSlot(fabricator, slot, SCHEMATICS_X + (slot % 3) * 18, SCHEMATICS_Y + (slot / 3) * 18));
        }
        for (int i = 0; i < FabricatorBlockEntity.MODULE_SLOTS; i++) {
            addSlot(new FilteredSlot(fabricator, FabricatorBlockEntity.SCHEMATIC_SLOTS + i, MODULE_X, MODULE_Y[i]));
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    public float progress() {
        int duration = data.get(1);
        return duration <= 0 ? 0 : (float) data.get(0) / duration;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INVENTORY) {
            if (!moveItemStackTo(stack, INVENTORY, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, INVENTORY, false)) {
            return ItemStack.EMPTY;
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
        return fabricator.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        fabricator.stopOpen(player);
    }

    // Takes only Crafting Schematics (schematic slots) or Throughput Modules (module slots), one per slot.
    private static class FilteredSlot extends Slot {
        FilteredSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return FabricatorBlockEntity.accepts(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
