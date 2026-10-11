/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Images on Display Panels: the 16-colour mode uses only the panel palette; 64 / 256 at most that many colours (median
// cut); full colour keeps every colour; the dither is the same each time; the size is the region's. A source image's
// size is read from its header and checked (sides, pixels, file size) before it's decoded.
class DisplayImagesTest {
    private static BufferedImage gradient() {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 80; x++) {
            for (int y = 0; y < 60; y++) {
                image.setRGB(x, y, (x * 3) << 16 | (y * 4) << 8 | (x + y));
            }
        }
        return image;
    }

    private static Set<Integer> colours(int[] pixels) {
        Set<Integer> set = new HashSet<>();
        for (int pixel : pixels) {
            set.add(pixel & 0xFFFFFF);
        }
        return set;
    }

    @Test
    void sixteenUsesThePalette() {
        int[] pixels = DisplayImages.convert(gradient(), 40, 30, false, DisplayImages.Colors.C16);
        assertEquals(40 * 30, pixels.length);
        Set<Integer> palette = new HashSet<>();
        for (int colour : DisplayImages.PALETTE_16) {
            palette.add(colour);
        }
        assertTrue(palette.containsAll(colours(pixels)), "a colour off the panel palette");
    }

    @Test
    void adaptiveCapsTheColours() {
        assertTrue(colours(DisplayImages.convert(gradient(), 40, 30, false, DisplayImages.Colors.C64)).size() <= 64);
        assertTrue(colours(DisplayImages.convert(gradient(), 40, 30, false, DisplayImages.Colors.C256)).size() <= 256);
        assertTrue(colours(DisplayImages.convert(gradient(), 40, 30, false, DisplayImages.Colors.FULL)).size() > 256, "full colour lost colours");
    }

    @Test
    void ditherIsStable() {
        assertEquals(java.util.Arrays.hashCode(DisplayImages.convert(gradient(), 40, 30, false, DisplayImages.Colors.C64)),
                java.util.Arrays.hashCode(DisplayImages.convert(gradient(), 40, 30, false, DisplayImages.Colors.C64)));
    }

    private static Path png(Path dir, int width, int height) throws IOException {
        Path file = dir.resolve(width + "x" + height + ".png");
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        return file;
    }

    @Test
    void readsTheSizeFromTheHeader(@TempDir Path dir) throws IOException {
        assertArrayEquals(new int[] { 120, 40 }, DisplayImages.dimensions(png(dir, 120, 40)));
        Path text = Files.writeString(dir.resolve("fake.png"), "not an image");
        assertThrows(IOException.class, () -> DisplayImages.dimensions(text));
    }

    @Test
    void refusesTooManyPixelsBeforeDecoding(@TempDir Path dir) throws IOException {
        Path image = png(dir, 120, 40);
        DisplayImages.Limits roomy = new DisplayImages.Limits(1024 * 1024, 4096, 120 * 40);
        assertEquals(120, DisplayImages.decode(image, roomy).getWidth());
        // Each side is within the side limit, but width x height isn't within the pixel limit.
        IOException refused = assertThrows(IOException.class, () -> DisplayImages.decode(image, new DisplayImages.Limits(1024 * 1024, 4096, 120 * 40 - 1)));
        assertEquals("too large", refused.getMessage());
        assertThrows(IOException.class, () -> DisplayImages.decode(image, new DisplayImages.Limits(1024 * 1024, 100, 1_000_000)));
        assertThrows(IOException.class, () -> DisplayImages.decode(image, new DisplayImages.Limits(16, 4096, 1_000_000)));
        assertFalse(new DisplayImages.Limits(1024, 16_384, 16_777_216).fits(16_384, 16_384));
        assertEquals("4096 x 4096 px, 4 MB", new DisplayImages.Limits(4L * 1024 * 1024, 4096, 16_777_216).text());
    }
}
