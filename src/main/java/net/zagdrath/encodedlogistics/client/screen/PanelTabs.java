/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// A rack panel's row of tabs under its title (the L3 Switch's tab sprites, each widened to its label with its edges
// kept): the Router's Routes | Mode | Table | ACLs, the Exchange Switch's, the Directory Server's.
public final class PanelTabs {
    private static final Identifier ACTIVE = EncodedLogistics.id("rack/switch/tab_active"), INACTIVE = EncodedLogistics.id("rack/switch/tab_inactive");
    private static final int SPRITE_W = 52, EDGE = 3, MIN_W = 36, PAD = 10, GAP = 1;
    public static final int X = 8, Y = 17, HEIGHT = 13;

    private final String[] keys;

    public PanelTabs(String... keys) {
        this.keys = keys;
    }

    public int count() {
        return keys.length;
    }

    private int width(Font font, int tab) {
        return Math.max(MIN_W, font.width(Component.translatable(keys[tab])) + PAD);
    }

    private int left(Font font, int tab) {
        int x = X;
        for (int i = 0; i < tab; i++) {
            x += width(font, i) + GAP;
        }
        return x;
    }

    // In screen coordinates (the panel's top-left at left, top).
    public void extractBackground(GuiGraphicsExtractor graphics, Font font, int left, int top, int active) {
        for (int i = 0; i < keys.length; i++) {
            boolean on = i == active;
            int height = on ? HEIGHT : HEIGHT - 1, width = width(font, i), x = left + left(font, i), y = top + Y;
            Identifier sprite = on ? ACTIVE : INACTIVE;
            // The left edge and the middle, the middle column repeated where it's wider than the sprite, the right edge.
            int body = width - EDGE;
            int first = Math.min(body, SPRITE_W - EDGE);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, SPRITE_W, height, 0, 0, x, y, first, height);
            for (int filled = first; filled < body; filled++) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, SPRITE_W, height, SPRITE_W / 2, 0, x + filled, y, 1, height);
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, SPRITE_W, height, SPRITE_W - EDGE, 0, x + body, y, EDGE, height);
        }
    }

    // In the panel's own coordinates.
    public void extractLabels(GuiGraphicsExtractor graphics, Font font, int active) {
        for (int i = 0; i < keys.length; i++) {
            graphics.centeredText(font, Component.translatable(keys[i]), left(font, i) + width(font, i) / 2, Y + 3,
                    i == active ? RackScreen.TEXT : RackScreen.TEXT_MUTED);
        }
    }

    // The tab at x, y (panel coordinates), or -1.
    public int at(Font font, double x, double y) {
        if (y < Y || y >= Y + HEIGHT) {
            return -1;
        }
        for (int i = 0; i < keys.length; i++) {
            int left = left(font, i);
            if (x >= left && x < left + width(font, i)) {
                return i;
            }
        }
        return -1;
    }
}
