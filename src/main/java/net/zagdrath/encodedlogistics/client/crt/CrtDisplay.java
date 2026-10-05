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
import net.minecraft.resources.Identifier;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// The green screen as drawn (HANDOFF 3): an 80 x 24 text grid on a CRT monitor drawn over the game, character by character
// from the terminal font sheet (6 x 10 cells) with tall pixels (1.15 times as tall as wide: the 520 x 260 virtual glass -
// the 480 x 240 text and its margin), in real screen pixels. Passes: the monitor's case, the phosphor's background, the
// glow (pre-blurred glyphs at 45%), the text, anything the screen adds in virtual pixels (the machines' slots), the
// vignette, the bezel. The Terminal Desk's screen (CrtScreen) and the Midrange machines' (CrtMachineScreen) both draw
// with it, so they look the same.
public final class CrtDisplay {
    private static final Identifier FONT = EncodedLogistics.id("textures/font/terminal.png"), GLOW = EncodedLogistics.id("textures/font/terminal_glow.png"),
            BEZEL_TEXTURE = EncodedLogistics.id("textures/gui/crt/bezel.png"), VIGNETTE = EncodedLogistics.id("textures/gui/crt/vignette.png");
    public static final int VW = 520, VH = 260, MARGIN_X = 20, MARGIN_Y = 10, CW = 6, CH = 10;
    // The font sheets: 16 x 7 cells of 6 x 10 (the glow sheet's of 10 x 14).
    private static final int FONT_W = 96, FONT_H = 70, GLOW_W = 160, GLOW_H = 98;
    // The monitor fills about three quarters of the window, its pixels 1.15 times as tall as wide (the glass a little
    // wider than 4:3), centred a little above the middle; the game shows round it.
    static final float PIXEL_ASPECT = 1.15F;
    // The housing round the glass, in glass widths: the bezel, the case's sides, top and chin.
    private static final float BEZEL = 0.025F, SIDE = 0.055F, TOP = 0.045F, CHIN = 0.10F;

    // A phosphor's colours (screens/crt/phosphor.json).
    public record Palette(int normal, int bright, int dim, int bg, int glow) {
        public static final Palette GREEN = new Palette(0xFF28D25A, 0xFFDAFFE4, 0xFF1F9E45, 0xFF020904, 0xFF33F06A);
    }

    private Palette palette = Palette.GREEN;
    // This frame's layout, in real pixels: the glass's corner and a virtual pixel's size.
    private int glassX, glassY;
    private float vx = 2, vy = 2.6F;

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

    // The monitor with the grid on it; cursor: the blinking block's cell (row, column), or null; overlay: drawn in
    // virtual pixels after the text (the machines' slots and items).
    public void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, CrtGrid grid, int @Nullable [] cursor, @Nullable Consumer<GuiGraphicsExtractor> overlay) {
        float scale = (float) minecraft.getWindow().getGuiScale();
        layout(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        graphics.pose().pushMatrix();
        // Real pixels.
        graphics.pose().scale(1 / scale, 1 / scale);
        housing(graphics);
        int glassW = Math.round(VW * vx), glassH = Math.round(VH * vy);
        graphics.fill(glassX, glassY, glassX + glassW, glassY + glassH, palette.bg());
        // Virtual pixels: the text and its glow.
        graphics.pose().pushMatrix();
        graphics.pose().translate(glassX, glassY);
        graphics.pose().scale(vx, vy);
        int glowColor = (0x73 << 24) | (palette.glow() & 0xFFFFFF);
        for (int row = 0; row < CrtGrid.ROWS; row++) {
            for (int col = 0; col < CrtGrid.COLS; col++) {
                char c = grid.chars[row][col];
                if (c != ' ' && !grid.reverse[row][col]) {
                    int i = CrtGrid.glyph(c);
                    graphics.blit(RenderPipelines.GUI_TEXTURED, GLOW, MARGIN_X + col * CW - 2, MARGIN_Y + row * CH - 2, (i % 16) * 10, (i / 16) * 14, 10, 14,
                            10, 14, GLOW_W, GLOW_H, glowColor);
                }
            }
        }
        for (int row = 0; row < CrtGrid.ROWS; row++) {
            for (int col = 0; col < CrtGrid.COLS; col++) {
                int x = MARGIN_X + col * CW, y = MARGIN_Y + row * CH;
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
            int x = MARGIN_X + cursor[1] * CW, y = MARGIN_Y + cursor[0] * CH;
            graphics.fill(x, y + 1, x + 5, y + 8, palette.bright());
        }
        if (overlay != null) {
            overlay.accept(graphics);
        }
        graphics.pose().popMatrix();
        graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, glassX, glassY, 0, 0, glassW, glassH, 256, 192, 256, 192);
        int bezel = bezelPx();
        bezel(graphics, glassX - bezel, glassY - bezel, glassW + 2 * bezel, glassH + 2 * bezel, bezel);
        graphics.pose().popMatrix();
    }

    private void layout(int width, int height) {
        float caseW = VW * (1 + 2 * BEZEL + 2 * SIDE), caseH = VH * PIXEL_ASPECT + VW * (2 * BEZEL + TOP + CHIN);
        vx = Math.max(0.5F, Math.min(0.74F * width / caseW, 0.78F * height / caseH));
        vy = vx * PIXEL_ASPECT;
        int glassW = Math.round(VW * vx), outerH = Math.round(caseH * vx);
        glassX = (width - glassW) / 2;
        int caseTop = (height - outerH) / 2;
        glassY = caseTop + Math.round(VW * vx * (TOP + BEZEL));
    }

    private int bezelPx() {
        return Math.max(4, Math.round(VW * vx * BEZEL));
    }

    // The CRT's case: a soft shadow, the beige shell with its lit top edge and dark rim, a recess round the bezel, and
    // the chin with its badge strip, two knobs and the power light.
    private void housing(GuiGraphicsExtractor graphics) {
        int glassW = Math.round(VW * vx), glassH = Math.round(VH * vy);
        float g = VW * vx;
        int bezel = bezelPx(), side = Math.round(g * SIDE), top = Math.round(g * TOP), chin = Math.round(g * CHIN);
        int x0 = glassX - bezel - side, y0 = glassY - bezel - top, x1 = glassX + glassW + bezel + side, y1 = glassY + glassH + bezel + chin;
        int shadow = Math.max(4, Math.round(g * 0.012F));
        graphics.fill(x0 + shadow, y0 + shadow * 2, x1 + shadow, y1 + shadow * 2, 0x55000000);
        int rim = Math.max(2, Math.round(g * 0.003F));
        graphics.fill(x0, y0, x1, y1, 0xFF7F786B);
        graphics.fill(x0 + rim, y0 + rim, x1 - rim, y1 - rim, 0xFFCFC8B6);
        graphics.fill(x0 + rim, y0 + rim, x1 - rim, y0 + rim * 2, 0xFFE6E0D0);
        graphics.fill(x0 + rim, y1 - rim * 3, x1 - rim, y1 - rim, 0xFFB3AC9B);
        // The recess round the bezel.
        int recess = Math.max(2, Math.round(g * 0.006F));
        graphics.fill(glassX - bezel - recess, glassY - bezel - recess, glassX + glassW + bezel + recess, glassY + glassH + bezel + recess, 0xFFA59E8E);
        // The chin: a badge strip left, two knobs and the power light right.
        int chinTop = glassY + glassH + bezel + recess, chinMid = (chinTop + y1 - rim * 3) / 2;
        int unit = Math.max(2, Math.round(g * 0.008F));
        graphics.fill(glassX, chinMid - unit / 2, glassX + Math.round(g * 0.14F), chinMid + unit / 2 + 1, 0xFFB7B09F);
        int knob = unit * 3, right = glassX + glassW;
        for (int i = 0; i < 2; i++) {
            int kx = right - knob * (5 + i * 2);
            graphics.fill(kx, chinMid - knob / 2, kx + knob, chinMid + knob / 2, 0xFF9C9584);
            graphics.fill(kx + 1, chinMid - knob / 2 + 1, kx + knob - 1, chinMid + knob / 2 - 1, 0xFFC3BCAA);
        }
        int led = unit * 2;
        graphics.fill(right - led * 2 - 2, chinMid - led / 2 - 2, right - led + 2, chinMid + led / 2 + 2, 0x4033F06A);
        graphics.fill(right - led * 2, chinMid - led / 2, right - led, chinMid + led / 2, 0xFF33F06A);
    }

    // The 9-slice bezel (64 x 64, 16-texel borders) round the glass, its borders b pixels.
    private static void bezel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int b) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x, y, 0, 0, b, b, 16, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x + w - b, y, 48, 0, b, b, 16, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x, y + h - b, 0, 48, b, b, 16, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x + w - b, y + h - b, 48, 48, b, b, 16, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x + b, y, 16, 0, w - 2 * b, b, 32, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x + b, y + h - b, 16, 48, w - 2 * b, b, 32, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x, y + b, 0, 16, b, h - 2 * b, 16, 32, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x + w - b, y + b, 48, 16, b, h - 2 * b, 16, 32, 64, 64);
    }

    // --- Where the mouse is ---

    // A point on the GUI in virtual pixels on the glass (the text from (MARGIN_X, MARGIN_Y)), as of the last frame.
    public double[] virtual(Minecraft minecraft, double mouseX, double mouseY) {
        double scale = minecraft.getWindow().getGuiScale();
        return new double[] { (mouseX * scale - glassX) / vx, (mouseY * scale - glassY) / vy };
    }

    // The cell under a point on the GUI, or null outside the text.
    public int @Nullable [] cell(Minecraft minecraft, double mouseX, double mouseY) {
        double[] v = virtual(minecraft, mouseX, mouseY);
        int col = (int) Math.floor((v[0] - MARGIN_X) / CW), row = (int) Math.floor((v[1] - MARGIN_Y) / CH);
        return col >= 0 && col < CrtGrid.COLS && row >= 0 && row < CrtGrid.ROWS ? new int[] { row, col } : null;
    }
}
