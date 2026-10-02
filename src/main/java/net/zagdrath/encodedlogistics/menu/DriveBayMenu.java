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
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Drive Bay screen: its ten drive slots laid out like the front (two columns of five) and the player's inventory.
// data[0] is 1 while the bay is online.
public class DriveBayMenu extends AbstractContainerMenu {
    // Item positions, as screens/drive_bay.json lays them out (slot frames at 56 / 96 from 20, 18 down).
    public static final int[] COLUMN_X = { 57, 97 };
    public static final int TOP = 21, ROW = 18, INVENTORY_Y = 126, HOTBAR_Y = 184;

    private final Container bay;
    private final ContainerData data;

    // Client constructor.
    public DriveBayMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(DriveBayBlockEntity.SLOTS), new SimpleContainerData(1));
    }

    public DriveBayMenu(int containerId, Inventory inventory, Container bay, ContainerData data) {
        super(ModMenuTypes.DRIVE_BAY.get(), containerId);
        checkContainerSize(bay, DriveBayBlockEntity.SLOTS);
        this.bay = bay;
        this.data = data;
        bay.startOpen(inventory.player);
        for (int slot = 0; slot < DriveBayBlockEntity.SLOTS; slot++) {
            addSlot(new Slot(bay, slot, COLUMN_X[slot / 5], TOP + (slot % 5) * ROW) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof StorageDriveItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    public boolean isOnline() {
        return data.get(0) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int bays = DriveBayBlockEntity.SLOTS;
        if (index < bays) {
            if (!moveItemStackTo(stack, bays, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!(stack.getItem() instanceof StorageDriveItem) || !moveItemStackTo(stack, 0, bays, false)) {
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
        return bay.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        bay.stopOpen(player);
    }
}
