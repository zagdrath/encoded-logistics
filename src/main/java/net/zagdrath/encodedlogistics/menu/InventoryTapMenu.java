/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.part.InventoryTapPart;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// An Inventory Tap's screen: priority (typed in, or stepped), access mode, the 3x3 ghost filter and the player's inventory.
// Buttons: 0 access mode, 1 priority up, 2 priority down. Value 0: priority. data: 0-1 priority, 2 access.
public class InventoryTapMenu extends AbstractContainerMenu implements ValueMenu {
    public static final int FILTER_X = 98, FILTER_Y = 19, INVENTORY_Y = 94;
    public static final int BUTTON_ACCESS = 0, BUTTON_UP = 1, BUTTON_DOWN = 2, VALUE_PRIORITY = 0;

    private final @Nullable InventoryTapPart tap;
    private final Container filter;
    private final ContainerData data;

    public static void open(ServerPlayer player, InventoryTapPart tap) {
        PartMenus.open(player, tap, Component.translatable(tap.type().item().getDescriptionId()),
                (id, inventory, part) -> new InventoryTapMenu(id, inventory, (InventoryTapPart) part));
    }

    // Client constructor.
    public InventoryTapMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, (InventoryTapPart) null);
    }

    private InventoryTapMenu(int containerId, Inventory inventory, @Nullable InventoryTapPart tap) {
        super(ModMenuTypes.INVENTORY_TAP.get(), containerId);
        this.tap = tap;
        if (tap != null) {
            filter = new ListContainer(tap.filter().entries(), tap::settingsChanged);
            data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case 0 -> PartMenus.low(tap.priority());
                        case 1 -> PartMenus.high(tap.priority());
                        default -> tap.access();
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 3;
                }
            };
        } else {
            filter = new SimpleContainer(PartFilter.SIZE);
            data = new SimpleContainerData(3);
        }
        for (int i = 0; i < PartFilter.SIZE; i++) {
            addSlot(new GhostSlot(filter, i, FILTER_X + (i % 3) * 18, FILTER_Y + (i / 3) * 18));
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    public int priority() {
        return PartMenus.join(data.get(0), data.get(1));
    }

    public int access() {
        return data.get(2);
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex >= 0 && slotIndex < PartFilter.SIZE) {
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                filter.setItem(slotIndex, PartMenus.ghost(this));
            }
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (tap == null) {
            return false;
        }
        switch (id) {
            case BUTTON_ACCESS -> tap.cycleAccess();
            case BUTTON_UP -> tap.setPriority(tap.priority() + 1);
            case BUTTON_DOWN -> tap.setPriority(tap.priority() - 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setValue(ServerPlayer player, int key, int value) {
        if (tap != null && key == VALUE_PRIORITY) {
            tap.setPriority(value);
        }
    }

    // Shift-clicking an item in the inventory sets it in the first empty filter slot.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= PartFilter.SIZE && slots.get(index).hasItem()) {
            for (int i = 0; i < PartFilter.SIZE; i++) {
                if (filter.getItem(i).isEmpty()) {
                    filter.setItem(i, slots.get(index).getItem().copyWithCount(1));
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return tap == null || tap.stillValid(player);
    }
}
