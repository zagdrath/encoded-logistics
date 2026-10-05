/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Gateway's screen (screens/gateway.json): its Processing Schematic row, the stock row (ghost items, each with the
// amount to keep: click with an item to set it, scroll or right-click to change the amount), the buffer it keeps
// (read-only) and the player's inventory.
public class GatewayMenu extends AbstractContainerMenu implements ValueMenu {
    public static final int ROW_X = 8, SCHEMATICS_Y = 18, STOCK_Y = 50, BUFFER_X = 9, BUFFER_Y = 84, INVENTORY_Y = 124;
    public static final int SCHEMATICS = 0, STOCK = GatewayBlockEntity.SCHEMATIC_SLOTS, BUFFER = STOCK + GatewayBlockEntity.STOCK_SLOTS,
            INVENTORY = BUFFER + GatewayBlockEntity.BUFFER;

    private final @Nullable GatewayBlockEntity gateway;
    private final Container stock;

    public GatewayMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public GatewayMenu(int containerId, Inventory inventory, @Nullable GatewayBlockEntity gateway) {
        super(ModMenuTypes.GATEWAY.get(), containerId);
        this.gateway = gateway;
        Container schematics = gateway != null ? new ListContainer(gateway.schematicSlots(), gateway::settingsChanged)
                : new SimpleContainer(GatewayBlockEntity.SCHEMATIC_SLOTS);
        stock = gateway != null ? new ListContainer(gateway.stock(), gateway::settingsChanged) : new SimpleContainer(GatewayBlockEntity.STOCK_SLOTS);
        Container buffer = gateway != null ? gateway.bufferView() : new SimpleContainer(GatewayBlockEntity.BUFFER);
        for (int i = 0; i < GatewayBlockEntity.SCHEMATIC_SLOTS; i++) {
            addSlot(new Slot(schematics, i, ROW_X + i * 18, SCHEMATICS_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return GatewayBlockEntity.acceptsSchematic(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int i = 0; i < GatewayBlockEntity.STOCK_SLOTS; i++) {
            addSlot(new GhostSlot(stock, i, ROW_X + i * 18, STOCK_Y) {
                @Override
                public int getMaxStackSize() {
                    return 99;
                }
            });
        }
        for (int i = 0; i < GatewayBlockEntity.BUFFER; i++) {
            addSlot(new Slot(buffer, i, BUFFER_X + i * 18, BUFFER_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
    }

    // Stock row: an item in hand sets it (left: the whole stack's count, right: one), an empty hand clears it.
    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex >= STOCK && slotIndex < BUFFER) {
            // A right click with an empty hand is the screen's amount step, not a clear.
            if ((input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) && (!getCarried().isEmpty() || buttonNum == 0)) {
                stock.setItem(slotIndex - STOCK, ghost(getCarried(), buttonNum == 1));
            }
            return;
        }
        if (slotIndex >= BUFFER && slotIndex < INVENTORY) {
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    // An item to stock (or encode); the stock row keeps items only, so a fluid or gas entry sets nothing.
    static ItemStack ghost(ItemStack carried, boolean one) {
        return carried.isEmpty() || ResourceEntryItem.entry(carried) != null ? ItemStack.EMPTY : carried.copyWithCount(one ? 1 : Math.min(carried.getCount(), carried.getMaxStackSize()));
    }

    // A stock amount typed, scrolled or right-clicked on the screen: key is the stock slot.
    @Override
    public void setValue(ServerPlayer player, int key, int value) {
        if (key < 0 || key >= GatewayBlockEntity.STOCK_SLOTS) {
            return;
        }
        ItemStack stack = stock.getItem(key);
        if (!stack.isEmpty()) {
            stock.setItem(key, stack.copyWithCount(Math.clamp(value, 1, stack.getMaxStackSize())));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index >= STOCK && index < INVENTORY) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < STOCK) {
            if (!moveItemStackTo(stack, INVENTORY, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!GatewayBlockEntity.acceptsSchematic(stack) || !moveItemStackTo(stack, SCHEMATICS, STOCK, false)) {
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
        return gateway == null || gateway.stillValid(player);
    }
}
