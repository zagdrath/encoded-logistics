/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.part.CablePart;

// Opening a part's menu (menus are per side: the menu data carries the host's position and the part's side), ghost
// slot clicks, and ints split over two data slots (they only carry 16 bits each).
public final class PartMenus {
    private PartMenus() {}

    public static void open(ServerPlayer player, CablePart part, Component title, MenuFactory factory) {
        BlockPos pos = part.host().getBlockPos();
        Direction side = part.side();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> factory.create(id, inventory, part), title), buf -> {
            buf.writeBlockPos(pos);
            buf.writeEnum(side);
        });
    }

    @FunctionalInterface
    public interface MenuFactory {
        AbstractContainerMenu create(int containerId, Inventory inventory, CablePart part);
    }

    // A click on a ghost slot: an item in hand sets a copy of it, an empty hand clears it.
    public static ItemStack ghost(AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        return carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
    }

    public static int low(int value) {
        return value & 0xFFFF;
    }

    public static int high(int value) {
        return value >>> 16;
    }

    public static int join(int low, int high) {
        return (low & 0xFFFF) | (high & 0xFFFF) << 16;
    }
}
