/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;

// One of a rack device's item slots, as its settings panel shows it: where the item sits (screen-relative, in the top
// half), what it takes and how many. A device type lists its slots (RackDeviceType#slots); the device holds the items
// (RackDevice#items) and the rack's menu shows them while it's picked.
public record RackSlot(int x, int y, Predicate<ItemStack> accepts, int maxStack) {
    // A grid of slots, columns by rows, 18 px apart, from (x, y).
    public static List<RackSlot> grid(int x, int y, int columns, int rows, Predicate<ItemStack> accepts, int maxStack) {
        List<RackSlot> slots = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                slots.add(new RackSlot(x + column * 18, y + row * 18, accepts, maxStack));
            }
        }
        return slots;
    }
}
