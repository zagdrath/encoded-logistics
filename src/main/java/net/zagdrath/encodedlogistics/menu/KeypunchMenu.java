/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.midrange.KeypunchBlockEntity;
import net.zagdrath.encodedlogistics.midrange.PeripheralBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Keypunch's screen: the 3 x 3 recipe grid (ghost items: an item in hand sets a cell, an empty hand clears it), the
// output it crafts, the blank and punched card slots, then the player's inventory. Buttons: Punch (F6), Clear grid (F13).
public class KeypunchMenu extends PeripheralMenu {
    public static final int GRID = 0, RESULT = 9, BLANK = 10, PUNCHED = 11;
    public static final int BUTTON_PUNCH = 0, BUTTON_CLEAR = 1;
    public static final int GRID_X = 36, GRID_Y = 60, GRID_PITCH_X = 24, GRID_PITCH_Y = 20, RESULT_X = 29 * 6, RESULT_Y = 80, BLANK_X = 46 * 6,
            PUNCHED_X = 57 * 6, CARDS_Y = 70;

    private final @Nullable KeypunchBlockEntity keypunch;

    // Client constructor.
    public KeypunchMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(2), new SimpleContainer(9), new SimpleContainer(1), null, Opening.read(buf));
    }

    public KeypunchMenu(int containerId, Inventory inventory, Container cards, Container grid, Container result, @Nullable PeripheralBlockEntity peripheral,
            Opening opening) {
        super(ModMenuTypes.KEYPUNCH.get(), containerId, inventory, cards, peripheral, opening);
        this.keypunch = peripheral instanceof KeypunchBlockEntity k ? k : null;
        for (int i = 0; i < 9; i++) {
            addSlot(new GhostSlot(grid, i, GRID_X + (i % 3) * GRID_PITCH_X, GRID_Y + (i / 3) * GRID_PITCH_Y));
        }
        addSlot(new GhostSlot(result, 0, RESULT_X, RESULT_Y));
        addSlot(new MachineSlot(cards, KeypunchBlockEntity.BLANK, BLANK_X, CARDS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return KeypunchBlockEntity.blank(stack);
            }
        });
        addSlot(new MachineSlot(cards, KeypunchBlockEntity.PUNCHED, PUNCHED_X, CARDS_Y));
        addPlayerSlots(inventory);
    }

    // Grid cells: an item in hand sets one, an empty hand (left click) clears it. The output can't be taken.
    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex >= GRID && slotIndex <= RESULT) {
            if (slotIndex < RESULT && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
                Slot slot = slots.get(slotIndex);
                ItemStack carried = getCarried();
                if (!carried.isEmpty() || buttonNum == 0) {
                    slot.container.setItem(slot.getContainerSlot(), carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
                }
            }
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (keypunch == null) {
            return false;
        }
        switch (id) {
            case BUTTON_PUNCH -> send(keypunch.punch());
            case BUTTON_CLEAR -> keypunch.clearGrid();
            default -> {
                return false;
            }
        }
        return true;
    }
}
