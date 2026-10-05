/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import net.minecraft.resources.Identifier;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// The terminal font sheet (textures/font/terminal.png: 16 x 7 cells of 6 x 10, ASCII from the space, then the not sign
// and box pieces) for what draws text glyph by glyph outside the green screen (the Display Panels' canvas).
public final class TerminalFont {
    public static final Identifier SHEET = EncodedLogistics.id("textures/font/terminal.png");
    public static final int CELL_W = 6, CELL_H = 10, COLUMNS = 16;

    private TerminalFont() {}

    // A character's cell on the sheet ('?' for one it hasn't got).
    public static int glyph(char c) {
        return CrtGrid.drawable(c) ? CrtGrid.glyph(c) : CrtGrid.glyph('?');
    }
}
