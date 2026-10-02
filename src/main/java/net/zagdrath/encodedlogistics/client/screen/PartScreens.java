/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// What the part screens (ports, tap, sensor) share: the palette and the 18x18 kit buttons with a 16x16 icon.
final class PartScreens {
    // palette.json
    static final int TEXT = 0xFFF0F0F0, TEXT_MUTED = 0xFFB4B4B4, TEXT_DISABLED = 0xFF7A7A7A, ACCENT = 0xFF00D992;
    static final Identifier BUTTON = EncodedLogistics.id("terminal/button"), BUTTON_HOVER = EncodedLogistics.id("terminal/button_hover");
    static final int BUTTON_SIZE = 18;

    private PartScreens() {}

    // Keeps a number field to digits (and a leading minus when negative is allowed).
    static void numeric(EditBox box, boolean negative) {
        box.setResponder(text -> {
            StringBuilder clean = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isDigit(c) || negative && c == '-' && clean.isEmpty()) {
                    clean.append(c);
                }
            }
            if (!clean.toString().equals(text)) {
                box.setValue(clean.toString());
            }
        });
    }

    static boolean over(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // A kit button at (x, y) on screen, with its icon.
    static void button(GuiGraphicsExtractor graphics, int x, int y, Identifier icon, int mouseX, int mouseY) {
        boolean hover = over(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? BUTTON_HOVER : BUTTON, x, y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + 1, y + 1, 16, 16);
    }
}
