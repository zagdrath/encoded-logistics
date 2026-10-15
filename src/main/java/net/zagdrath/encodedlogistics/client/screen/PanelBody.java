/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// What a panel draws and does, whichever screen hosts it: a block's own (BlockPanelScreen: the Exchange Switch, the
// Directory Server) or a rack unit's (BodyPanel: the Directory Server's rack form). Drawn on BACKGROUND (the L3 Switch's
// frame with a plain inset, x 8-155, y 30-141, text in x 12-152; the scroll track beside it, x 160-167), 176 by 166.
public interface PanelBody {
    Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/panel_body.png");
    Identifier THUMB = EncodedLogistics.id("controller/scroll_thumb"), THUMB_DISABLED = EncodedLogistics.id("controller/scroll_thumb_disabled");
    // The scroll track's inside (the thumb's travel), panel coordinates.
    int TRACK_X = 161, TRACK_Y = 31, TRACK_H = 110, THUMB_W = 6, THUMB_H = 15;
    String ELLIPSIS = "...";

    // text cut to width with "..." when it doesn't fit.
    static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ELLIPSIS))) + ELLIPSIS;
    }

    // The scroll thumb in the track (screen coordinates: the panel's corner at left, top), dimmed when it all fits.
    static void scrollbar(GuiGraphicsExtractor graphics, int left, int top, int scroll, int size, int rows) {
        int max = Math.max(0, size - rows);
        int y = top + TRACK_Y + (max == 0 ? 0 : Math.round((float) Math.min(scroll, max) * (TRACK_H - THUMB_H) / max));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, max == 0 ? THUMB_DISABLED : THUMB, left + TRACK_X, y, THUMB_W, THUMB_H);
    }

    // A line under a list's column headings (the heading at headY), panel coordinates.
    static void divider(GuiGraphicsExtractor graphics, int headY) {
        graphics.fill(10, headY + 10, 154, headY + 11, 0xFF232323);
    }

    // A message wrapped to the list's width, one line per row from y; for an empty list.
    static void wrapped(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int width, int row, int color) {
        for (FormattedCharSequence line : font.split(Component.literal(text), width)) {
            graphics.text(font, line, x, y, color, false);
            y += row;
        }
    }

    // Where it is and what it can use.
    interface Context {
        @Nullable ValueInput data();

        void send(int action, int value, String text);

        Font font();

        // The panel's top-left on screen.
        int left();

        int top();

        void addWidget(EditBox box);

        void removeWidget(EditBox box);
    }

    // Screen coordinates (context.left(), top() is the panel's corner).
    default void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

    // The panel's own coordinates.
    void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

    default void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

    // x, y in the panel's coordinates.
    default boolean mouseClicked(double x, double y, int button, boolean shift) {
        return false;
    }

    default boolean mouseScrolled(double amount) {
        return false;
    }

    default boolean keyPressed(KeyEvent event) {
        return false;
    }

    default void removed() {}
}
