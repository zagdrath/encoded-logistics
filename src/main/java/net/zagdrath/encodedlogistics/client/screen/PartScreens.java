/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// What the kit's screens (parts, autocrafting) share: the palette, the 18x18 kit buttons with a 16x16 icon, the wide text
// buttons (common/button_wide*, any size: the sprite's corners kept, the rest cut from it), progress bars and amounts
// drawn at half size like the terminal's counts.
final class PartScreens {
    // palette.json
    static final int TEXT = 0xFFF0F0F0, TEXT_MUTED = 0xFFB4B4B4, TEXT_DISABLED = 0xFF7A7A7A, ACCENT = 0xFF00D992, WARNING = 0xFFE8C24A,
            ERROR = 0xFFFF6B6B;
    // The controller's status lights, with the colour of the text beside them.
    enum Status {
        ONLINE("led_online", ACCENT), IDLE("led_idle", TEXT_MUTED), WARNING("led_warning", PartScreens.WARNING), ERROR("led_error",
                PartScreens.ERROR);

        final Identifier led;
        final int color;

        Status(String led, int color) {
            this.led = EncodedLogistics.id("controller/" + led);
            this.color = color;
        }
    }

    // Where a status sits: right-aligned in the title strip (the title's line), like the Drive Bay's and the Capacitor
    // Bank's.
    static final int STATUS_RIGHT = 168, STATUS_Y = 5;
    static final Identifier BUTTON = EncodedLogistics.id("terminal/button"), BUTTON_HOVER = EncodedLogistics.id("terminal/button_hover");
    static final int BUTTON_SIZE = 18;
    private static final Identifier WIDE = EncodedLogistics.id("common/button_wide"), WIDE_HOVER = EncodedLogistics.id("common/button_wide_hover"),
            WIDE_DISABLED = EncodedLogistics.id("common/button_wide_disabled"), WIDE_PRESSED = EncodedLogistics.id("common/button_wide_pressed");
    private static final int WIDE_WIDTH = 200, WIDE_HEIGHT = 18, CORNER = 3, BAR_WIDTH = 200, BAR_HEIGHT = 6;

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

    // A wide button at (x, y) on screen, width by height, with its text centred; dimmed and not hovered when disabled.
    static void wideButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, Component text, boolean enabled,
            int mouseX, int mouseY) {
        Identifier sprite = !enabled ? WIDE_DISABLED : over(mouseX, mouseY, x, y, width, height) ? WIDE_HOVER : WIDE;
        int w = width - CORNER, h = height - CORNER;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, WIDE_WIDTH, WIDE_HEIGHT, 0, 0, x, y, w, h);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, WIDE_WIDTH, WIDE_HEIGHT, WIDE_WIDTH - CORNER, 0, x + w, y, CORNER, h);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, WIDE_WIDTH, WIDE_HEIGHT, 0, WIDE_HEIGHT - CORNER, x, y + h, w, CORNER);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, WIDE_WIDTH, WIDE_HEIGHT, WIDE_WIDTH - CORNER, WIDE_HEIGHT - CORNER, x + w, y + h,
                CORNER, CORNER);
        graphics.centeredText(font, text, x + width / 2, y + (height - 8) / 2, enabled ? TEXT : TEXT_DISABLED);
    }

    // A wide button held down (a chosen option), without its text: the caller draws that.
    static void wideButtonPressed(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int w = width - CORNER, h = height - CORNER;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WIDE_PRESSED, WIDE_WIDTH, WIDE_HEIGHT, 0, 0, x, y, w, h);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WIDE_PRESSED, WIDE_WIDTH, WIDE_HEIGHT, WIDE_WIDTH - CORNER, 0, x + w, y, CORNER, h);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WIDE_PRESSED, WIDE_WIDTH, WIDE_HEIGHT, 0, WIDE_HEIGHT - CORNER, x, y + h, w, CORNER);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WIDE_PRESSED, WIDE_WIDTH, WIDE_HEIGHT, WIDE_WIDTH - CORNER, WIDE_HEIGHT - CORNER, x + w, y + h,
                CORNER, CORNER);
    }

    // A 16x16 icon (whose glyph, as the kit draws them, sits in its top rows: y 1 to 8) centred in a box: the button
    // under it, at (x, y), width by height.
    static void centeredIcon(GuiGraphicsExtractor graphics, Identifier icon, int x, int y, int width, int height, int color) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + width / 2 - 8, y + (height - 7) / 2 - 1, 16, 16, color);
    }

    // A progress bar's fill (common/bar_fill_*), width wide when full.
    static void bar(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, float progress) {
        int filled = Math.round(width * Math.clamp(progress, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, BAR_WIDTH, BAR_HEIGHT, 0, 0, x, y, filled, BAR_HEIGHT);
        }
    }

    // An item with its amount at half size in the bottom right corner (nothing for 1).
    static void itemWithAmount(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
        graphics.item(stack, x, y);
        if (stack.getCount() > 1) {
            String text = Integer.toString(stack.getCount());
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 16 - font.width(text) * 0.5F, y + 16 - 4.5F);
            graphics.pose().scale(0.5F, 0.5F);
            graphics.text(font, text, 0, 0, TEXT, true);
            graphics.pose().popMatrix();
        }
    }

    // A ghost amount after a scroll or right click: up or down by 1, or 10 with Shift, within 1..max.
    static int stepAmount(int amount, boolean up, boolean shift, int max) {
        return Math.clamp(amount + (up ? 1 : -1) * (shift ? 10 : 1), 1, max);
    }

    // A status: the controller's light, then the text in the light's colour, ending at right (screen-relative to the
    // pose, as labels are); y is the text's top.
    static void status(GuiGraphicsExtractor graphics, Font font, int right, int y, Component text, Status status) {
        int textX = right - font.width(text);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, status.led, textX - 9, y + 1, 6, 6);
        graphics.text(font, text, textX, y, status.color, false);
    }

    // A kit button at (x, y) on screen, with its icon.
    static void button(GuiGraphicsExtractor graphics, int x, int y, Identifier icon, int mouseX, int mouseY) {
        boolean hover = over(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? BUTTON_HOVER : BUTTON, x, y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + 1, y + 1, 16, 16);
    }
}
