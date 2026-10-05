/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.io.Reader;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// The green screen as drawn (docs/crt HANDOFF: the green-screen GUI frame): an 80 x 24 text grid in a GUI-kit panel like
// the mod's other machine GUIs - the panel (514 x 282 GUI px, a 9-slice), its title in the GUI font, a recessed well and
// the grid in it (480 x 240, at (17, 25), with PAD px of phosphor round it so no text touches the well's lip), 1 screen px a GUI px, drawn character by character from the terminal font
// sheet (6 x 10 cells). Passes: the panel, the title, the well, the phosphor's background, the text, the cursor, anything
// the screen adds in grid px. No glow, scanlines, CRT bezel, vignette, curvature or tall pixels. The panel goes at the
// largest whole scale that keeps it within MAX_WIDTH / MAX_HEIGHT of the window (at least 1), centred, so it sits in the
// window like the mod's other GUIs rather than filling it.
// Every text screen draws with it - the Terminal Desk's and the Integrated console's (CrtScreen), the machines'
// (CrtMachineScreen) and a PLC's editor - so they look the same.
public final class CrtDisplay {
    private static final Identifier FONT = EncodedLogistics.id("textures/font/terminal.png"), PANEL = EncodedLogistics.id("textures/gui/crt/machine_panel.png"), WELL = EncodedLogistics.id("textures/gui/crt/screen_well.png");
    public static final int CW = 6, CH = 10, GRID_W = CrtGrid.COLS * CW, GRID_H = CrtGrid.ROWS * CH;
    // The frame, in GUI px: the panel, the title, the well (a 3 px lip), the phosphor's margin and the grid inside it.
    public static final int PAD = 6, PANEL_W = 514, PANEL_H = 282, TITLE_X = 8, TITLE_Y = 5, WELL_X = 8, WELL_Y = 16, WELL_B = 3,
            GRID_X = WELL_X + WELL_B + PAD, GRID_Y = WELL_Y + WELL_B + PAD;
    private static final int TITLE_COLOR = 0xFFF0F0F0;
    // The font sheet: 16 x 7 cells of 6 x 10.
    private static final int FONT_W = 96, FONT_H = 70;
    // The most of the window the panel takes, across and down.
    private static final double MAX_WIDTH = 0.6, MAX_HEIGHT = 0.7;

    // A phosphor's colours (screens/crt/phosphor.json).
    public record Palette(int normal, int bright, int dim, int bg, int glow) {
        public static final Palette GREEN = new Palette(0xFF28D25A, 0xFFDAFFE4, 0xFF1F9E45, 0xFF020904, 0xFF33F06A);
    }

    private Palette palette = Palette.GREEN;
    // This frame's layout, in real pixels: the panel's corner and how many real pixels a GUI px of it is.
    private int panelX, panelY, scale = 1;

    public Palette palette() {
        return palette;
    }

    // The phosphor by name (*GREEN, *AMBER, *WHITE), from screens/crt/phosphor.json.
    public void loadPalette(Minecraft minecraft, String name) {
        try (Reader reader = minecraft.getResourceManager().openAsReader(EncodedLogistics.id("screens/crt/phosphor.json"))) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject colours = json.has(name) ? json.getAsJsonObject(name) : json.getAsJsonObject(json.get("default").getAsString());
            palette = new Palette(hex(colours, "normal"), hex(colours, "bright"), hex(colours, "dim"), hex(colours, "bg"), hex(colours, "glow"));
        } catch (Exception e) {
            palette = Palette.GREEN;
        }
    }

    private static int hex(JsonObject json, String key) {
        return 0xFF000000 | Integer.parseInt(json.get(key).getAsString().substring(1), 16);
    }

    public int color(byte attr) {
        return attr == CrtGrid.BRIGHT ? palette.bright() : attr == CrtGrid.DIM ? palette.dim() : palette.normal();
    }

    // --- Drawing ---

    // The panel titled title with the grid in it; cursor: the blinking block's cell (row, column), or null; overlay: drawn
    // in grid px after the text.
    public void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, CrtGrid grid, int @Nullable [] cursor, @Nullable Consumer<GuiGraphicsExtractor> overlay,
            Component title) {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        layout(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        graphics.pose().pushMatrix();
        // Real pixels, then the frame's GUI px.
        graphics.pose().scale(1 / guiScale, 1 / guiScale);
        graphics.pose().translate(panelX, panelY);
        graphics.pose().scale(scale, scale);
        nine(graphics, PANEL, 0, 0, PANEL_W, PANEL_H, 4, 32);
        graphics.text(minecraft.font, title, TITLE_X, TITLE_Y, TITLE_COLOR, false);
        nine(graphics, WELL, WELL_X, WELL_Y, GRID_W + 2 * (WELL_B + PAD), GRID_H + 2 * (WELL_B + PAD), WELL_B, 16);
        graphics.pose().pushMatrix();
        graphics.pose().translate(GRID_X, GRID_Y);
        graphics.fill(-PAD, -PAD, GRID_W + PAD, GRID_H + PAD, palette.bg());
        for (int row = 0; row < CrtGrid.ROWS; row++) {
            for (int col = 0; col < CrtGrid.COLS; col++) {
                int x = col * CW, y = row * CH;
                int color = color(grid.attrs[row][col]);
                char c = grid.chars[row][col];
                if (grid.reverse[row][col]) {
                    graphics.fill(x, y, x + CW, y + CH, color);
                    color = palette.bg();
                }
                if (c != ' ') {
                    int i = CrtGrid.glyph(c);
                    graphics.blit(RenderPipelines.GUI_TEXTURED, FONT, x, y, (i % 16) * CW, (i / 16) * CH, CW, CH, CW, CH, FONT_W, FONT_H, color);
                }
                if (grid.underline[row][col]) {
                    graphics.fill(x, y + CH - 1, x + CW, y + CH, color);
                }
            }
        }
        // The cursor: a block, blinking (the caller decides when it shows).
        if (cursor != null) {
            int x = cursor[1] * CW, y = cursor[0] * CH;
            graphics.fill(x, y + 1, x + 5, y + 8, palette.bright());
        }
        if (overlay != null) {
            overlay.accept(graphics);
        }
        graphics.pose().popMatrix();
        graphics.pose().popMatrix();
    }

    // The largest whole scale that keeps the panel within MAX_WIDTH x MAX_HEIGHT of the window (at least 1), centred.
    private void layout(int width, int height) {
        scale = Math.max(1, Math.min((int) (width * MAX_WIDTH / PANEL_W), (int) (height * MAX_HEIGHT / PANEL_H)));
        panelX = (width - PANEL_W * scale) / 2;
        panelY = (height - PANEL_H * scale) / 2;
    }

    // A size x size 9-slice texture (b-texel borders, the same in GUI px) stretched over w x h at (x, y).
    private static void nine(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int w, int h, int b, int size) {
        int mid = size - 2 * b;
        int[][] cols = { { x, 0, b, b }, { x + b, b, w - 2 * b, mid }, { x + w - b, size - b, b, b } };
        int[][] rows = { { y, 0, b, b }, { y + b, b, h - 2 * b, mid }, { y + h - b, size - b, b, b } };
        for (int[] col : cols) {
            for (int[] row : rows) {
                // Destination x / y, texture u / v, destination size, the texture region it's stretched from.
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, col[0], row[0], col[1], row[1], col[2], row[2], col[3], row[3], size, size);
            }
        }
    }

    // --- Where the mouse is ---

    // A point on the GUI in grid px (the text from (0, 0)), as of the last frame.
    public double[] virtual(Minecraft minecraft, double mouseX, double mouseY) {
        double guiScale = minecraft.getWindow().getGuiScale();
        return new double[] { (mouseX * guiScale - panelX) / scale - GRID_X, (mouseY * guiScale - panelY) / scale - GRID_Y };
    }

    // The cell under a point on the GUI, or null outside the text.
    public int @Nullable [] cell(Minecraft minecraft, double mouseX, double mouseY) {
        double[] v = virtual(minecraft, mouseX, mouseY);
        int col = (int) Math.floor(v[0] / CW), row = (int) Math.floor(v[1] / CH);
        return col >= 0 && col < CrtGrid.COLS && row >= 0 && row < CrtGrid.ROWS ? new int[] { row, col } : null;
    }
}
