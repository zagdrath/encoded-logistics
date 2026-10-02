/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.part.PartFilter;

// A filter entry's context menu while a Fuzzy Match Module is installed (right-click the entry): match it exactly, by
// damage (any, or a quarter of its durability worn) when it can be damaged, or by any of its tags. Shows the current
// choice in the accent colour; scrolls when the item has many tags. Picking sends the code (PartFilter.FUZZY_*).
final class FuzzyPopup {
    private static final int ROW = 10, PAD = 3, MAX_ROWS = 10, MIN_WIDTH = 90;

    final int entry;
    private final List<Component> labels = new ArrayList<>();
    private final List<Integer> codes = new ArrayList<>();
    private final int current;
    private int x, y, width, scroll;

    FuzzyPopup(Font font, int entry, ItemStack stack, int current, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        this.entry = entry;
        this.current = current;
        add(Component.translatable("gui.encodedlogistics.fuzzy.exact"), PartFilter.FUZZY_NONE);
        if (stack.isDamageableItem()) {
            add(Component.translatable("gui.encodedlogistics.fuzzy.any_damage"), PartFilter.FUZZY_ANY_DAMAGE);
            for (int quarter = 0; quarter < 4; quarter++) {
                add(Component.translatable("gui.encodedlogistics.fuzzy.damage", quarter * 25, quarter * 25 + 25), PartFilter.FUZZY_QUARTER + quarter);
            }
        }
        List<TagKey<Item>> tags = PartFilter.tags(stack);
        for (int i = 0; i < tags.size(); i++) {
            add(Component.literal("#" + tags.get(i).location()), PartFilter.FUZZY_TAG + i);
        }
        width = MIN_WIDTH;
        for (Component label : labels) {
            width = Math.max(width, font.width(label) + 2 * PAD + 6);
        }
        int height = Math.min(MAX_ROWS, labels.size()) * ROW + 2 * PAD;
        x = Math.min(mouseX, screenWidth - width - 2);
        y = Math.min(mouseY, screenHeight - height - 2);
    }

    private void add(Component label, int code) {
        labels.add(label);
        codes.add(code);
    }

    private int rows() {
        return Math.min(MAX_ROWS, labels.size());
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!PartScreens.over(mouseX, mouseY, x, y + PAD, width, rows() * ROW)) {
            return -1;
        }
        int row = scroll + (int) (mouseY - y - PAD) / ROW;
        return row < labels.size() ? row : -1;
    }

    void extract(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int height = rows() * ROW + 2 * PAD;
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF0E0E0E);
        graphics.fill(x, y, x + width, y + height, 0xF0262626);
        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < rows(); row++) {
            int index = scroll + row;
            int ry = y + PAD + row * ROW;
            if (index == hovered) {
                graphics.fill(x + 1, ry, x + width - 1, ry + ROW, 0xFF3A3A3A);
            }
            int color = codes.get(index) == current ? PartScreens.ACCENT : PartScreens.TEXT;
            graphics.text(font, labels.get(index), x + PAD + 3, ry + 1, color, false);
        }
        if (labels.size() > MAX_ROWS) {
            int track = rows() * ROW, thumb = Math.max(6, track * MAX_ROWS / labels.size());
            int thumbY = y + PAD + (track - thumb) * scroll / (labels.size() - MAX_ROWS);
            graphics.fill(x + width - 3, thumbY, x + width - 1, thumbY + thumb, 0xFF8A8A8A);
        }
    }

    // The code picked, or -1 (and the popup closes either way, unless the click was on its border).
    int click(double mouseX, double mouseY) {
        int row = rowAt(mouseX, mouseY);
        return row >= 0 ? codes.get(row) : -1;
    }

    boolean over(double mouseX, double mouseY) {
        return PartScreens.over(mouseX, mouseY, x - 1, y - 1, width + 2, rows() * ROW + 2 * PAD + 2);
    }

    void scroll(double amount) {
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, labels.size() - MAX_ROWS));
    }
}
