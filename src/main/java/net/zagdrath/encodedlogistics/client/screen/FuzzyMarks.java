/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;
import java.util.function.IntUnaryOperator;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.part.PartFilter;

// How a 3x3 filter shows its entries' fuzzy settings (with a Fuzzy Match Module in): a "~" on each fuzzy entry, and its
// setting in the entry's tooltip.
final class FuzzyMarks {
    private FuzzyMarks() {}

    // Over the filter's slots (the menu's first nine, the first at left, top on screen), above the items.
    static void extract(GuiGraphicsExtractor graphics, Font font, AbstractContainerMenu menu, int left, int top, IntUnaryOperator fuzzy) {
        for (int entry = 0; entry < PartFilter.SIZE; entry++) {
            if (fuzzy.applyAsInt(entry) != PartFilter.FUZZY_NONE && menu.getSlot(entry).hasItem()) {
                int x = left + (entry % 3) * 18, y = top + (entry / 3) * 18;
                graphics.text(font, "~", x + 11, y - 1, PartScreens.ACCENT, true);
            }
        }
    }

    static void tooltip(ItemStack stack, int code, List<Component> lines) {
        lines.add(stack.getHoverName());
        Component setting;
        if (code >= PartFilter.FUZZY_TAG) {
            List<TagKey<Item>> tags = PartFilter.tags(stack);
            int index = code - PartFilter.FUZZY_TAG;
            setting = index < tags.size() ? Component.literal("#" + tags.get(index).location()) : Component.translatable("gui.encodedlogistics.fuzzy.exact");
        } else if (code == PartFilter.FUZZY_ANY_DAMAGE) {
            setting = Component.translatable("gui.encodedlogistics.fuzzy.any_damage");
        } else if (code >= PartFilter.FUZZY_QUARTER) {
            int quarter = code - PartFilter.FUZZY_QUARTER;
            setting = Component.translatable("gui.encodedlogistics.fuzzy.damage", quarter * 25, quarter * 25 + 25);
        } else {
            setting = Component.translatable("gui.encodedlogistics.fuzzy.exact");
        }
        lines.add(Component.translatable("gui.encodedlogistics.fuzzy.matches", setting).withColor(PartScreens.ACCENT));
        lines.add(Component.translatable("gui.encodedlogistics.fuzzy.hint").withColor(PartScreens.TEXT_MUTED));
    }
}
