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
import net.minecraft.util.FormattedCharSequence;

// Tooltips wrapped to a compact width: each line split at word breaks to at most WIDTH pixels, so a long hint becomes a
// few short lines instead of one running off the screen. Lines keep their own colours.
public final class Tooltips {
    public static final int WIDTH = 200;

    private Tooltips() {}

    public static void show(GuiGraphicsExtractor graphics, Font font, Component text, int mouseX, int mouseY) {
        show(graphics, font, List.of(text), mouseX, mouseY);
    }

    public static void show(GuiGraphicsExtractor graphics, Font font, List<Component> lines, int mouseX, int mouseY) {
        graphics.setTooltipForNextFrame(font, wrap(font, lines), mouseX, mouseY);
    }

    public static List<FormattedCharSequence> wrap(Font font, List<Component> lines) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            List<FormattedCharSequence> split = font.split(line, WIDTH);
            if (split.isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            } else {
                wrapped.addAll(split);
            }
        }
        return wrapped;
    }
}
