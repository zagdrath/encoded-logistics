/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;
import net.zagdrath.encodedlogistics.part.CablePart;
import net.zagdrath.encodedlogistics.storage.ResourceContainers;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;

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

    // A click on a ghost slot: an item in hand sets a copy of it, an empty hand clears it. A filled bucket, tank or gas
    // container sets the fluid or gas in it instead (a Resource Entry), and a Resource Entry (from the fluid and gas picker,
    // or JEI) sets itself.
    public static ItemStack ghost(AbstractContainerMenu menu) {
        return ghost(menu.getCarried(), null);
    }

    // The same for a device working on one type: an item for ITEM (a bucket is just a bucket), the contents for FLUID or
    // PRESSURIZED; null: the contents when there are any, else the item.
    public static ItemStack ghost(ItemStack carried, @Nullable ResourceType type) {
        if (carried.isEmpty()) {
            return ItemStack.EMPTY;
        }
        StorageKey entry = ResourceEntryItem.key(carried);
        if (entry != null) {
            return ResourceEntryItem.of(entry, 0);
        }
        StorageKey contents = type == ResourceType.ITEM ? null : ResourceContainers.contents(carried, type);
        if (contents != null) {
            return ResourceEntryItem.of(contents, 0);
        }
        return carried.copyWithCount(1);
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
