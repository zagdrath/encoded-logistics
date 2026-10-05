/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

// Images on Display Panels: the 16-colour mode uses only the panel palette; 64 / 256 at most that many colours (median
// cut); full colour keeps every colour; the dither is the same each time; the size is the region's.
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
}
