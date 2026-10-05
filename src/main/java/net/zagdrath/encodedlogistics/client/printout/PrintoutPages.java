/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.printout;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.display.DisplayCanvases;
import net.zagdrath.encodedlogistics.midrange.Printout;

// A Printout's pages as textures (HANDOFF 9; tools/printout.py the reference): 384 x 512, the paper
// (textures/printout/paper.png: green bars, tractor strips, perforations) with the text in the terminal font glyph by
// glyph at x 32 + col * 6, y 16 + row * 10 - the header (rows 0-2), the body from row 4, the footer at row 47 - in its
// inks. Kept for the last MAX pages drawn (in hand, in frames, in the reader).
public final class PrintoutPages {
    public static final int WIDTH = 384, HEIGHT = 512;
    private static final int TEXT_X = 32, TEXT_Y = 16, ROW = 10, MAX = 32;
    private static final int[] INKS = { 0xFF2C3036, 0xFF5A606A, 0xFF9E2A22 };
    private static final Identifier PAPER = EncodedLogistics.id("textures/printout/paper.png");

    private record Key(Printout printout, int page) {}

    private static final Map<Key, Identifier> TEXTURES = new LinkedHashMap<>(16, 0.75F, true);
    private static @Nullable NativeImage paper;
    private static int next;

    private PrintoutPages() {}

    // The texture of a printout's page (made the first time it's asked for).
    public static Identifier texture(Printout printout, int page) {
        Key key = new Key(printout, Math.clamp(page, 0, Math.max(0, printout.pages().size() - 1)));
        Identifier id = TEXTURES.get(key);
        if (id != null) {
            return id;
        }
        id = EncodedLogistics.id("printout/page_" + next++);
        DynamicTexture texture = new DynamicTexture(() -> "Printout " + printout.title(), WIDTH, HEIGHT, true);
        NativeImage image = texture.getPixels();
        if (image != null) {
            draw(image, printout, key.page());
            texture.upload();
        }
        Minecraft.getInstance().getTextureManager().register(id, texture);
        TEXTURES.put(key, id);
        if (TEXTURES.size() > MAX) {
            Map.Entry<Key, Identifier> eldest = TEXTURES.entrySet().iterator().next();
            Minecraft.getInstance().getTextureManager().release(eldest.getValue());
            TEXTURES.remove(eldest.getKey());
        }
        return id;
    }

    private static void draw(NativeImage image, Printout printout, int page) {
        if (paper == null) {
            paper = DisplayCanvases.load(PAPER);
        }
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image.setPixel(x, y, paper != null && x < paper.getWidth() && y < paper.getHeight() ? paper.getPixel(x, y) | 0xFF000000 : 0xFFF4F2E8);
            }
        }
        text(image, 0, printout.header(page), Printout.NORMAL);
        text(image, 1, printout.subheader(page), Printout.LIGHT);
        text(image, 2, "─".repeat(Printout.COLUMNS), Printout.LIGHT);
        if (page < printout.pages().size()) {
            int row = 4;
            for (Printout.Line line : printout.pages().get(page).lines()) {
                text(image, row++, line.text(), line.ink());
            }
        }
        text(image, Printout.ROWS - 1, printout.footer(page), Printout.LIGHT);
    }

    private static void text(NativeImage image, int row, String text, byte ink) {
        String cut = text.length() > Printout.COLUMNS ? text.substring(0, Printout.COLUMNS) : text;
        DisplayCanvases.text(image, TEXT_X, TEXT_Y + row * ROW, cut, INKS[Math.clamp(ink, 0, INKS.length - 1)], 1);
    }
}
