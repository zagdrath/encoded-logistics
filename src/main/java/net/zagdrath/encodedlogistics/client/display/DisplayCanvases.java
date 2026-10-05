/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.display;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.crt.TerminalFont;
import net.zagdrath.encodedlogistics.display.DisplayContent;
import net.zagdrath.encodedlogistics.display.DisplayFrame;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlock;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlockEntity;
import net.zagdrath.encodedlogistics.net.DisplayFramePayload;

// The Display Panels' canvases on the client (HANDOFF 2): a dynamic texture per screen, CANVAS px a panel, painted
// again only when what it shows changes. Booting: the test pattern (textures/display/test_pattern.png) stretched over
// it; no signal: "NO SIGNAL" in the warning colour, centred (2x where it fits) over the darkest steel, a rule under it;
// online: its content over its background (steel step 1 by default), painted by CanvasPainter. Off: none (the glass
// shows). The reference is tools/dsp_canvas.py.
public final class DisplayCanvases {
    public static final int BACKGROUND = 0xFF2B2F36, DARK = 0xFF1F2228, RULE = 0xFF555B65, TEXT = 0xFFF0F0F0, WARNING = 0xFFE8C24A;
    private static final Identifier PATTERN = EncodedLogistics.id("textures/display/test_pattern.png");

    private record Canvas(Identifier id, DynamicTexture texture, int width, int height, int hash) {}

    private static final Map<BlockPos, Canvas> CANVASES = new HashMap<>();
    private static @Nullable NativeImage font, smallFont, pattern;
    private static final Identifier SMALL_FONT = EncodedLogistics.id("textures/font/terminal_small.png");

    private DisplayCanvases() {}

    // The texture showing a screen's master now, or null when it shows nothing (off).
    public static @Nullable Identifier texture(DisplayPanelBlockEntity master) {
        DisplayPanelBlock.Shown shown = master.shown();
        if (shown == DisplayPanelBlock.Shown.OFF) {
            return null;
        }
        int width = master.canvasWidth(), height = master.canvasHeight();
        int hash = Objects.hash(shown, width, height, shown == DisplayPanelBlock.Shown.ONLINE ? onlineHash(master) : 0);
        BlockPos pos = master.getBlockPos().immutable();
        Canvas canvas = CANVASES.get(pos);
        if (canvas != null && canvas.hash() == hash) {
            return canvas.id();
        }
        if (canvas == null || canvas.width() != width || canvas.height() != height) {
            release(pos);
            Identifier id = EncodedLogistics.id("display/" + Long.toHexString(pos.asLong()).toLowerCase(Locale.ROOT));
            DynamicTexture texture = new DynamicTexture(() -> "Display Panel " + pos.toShortString(), width, height, true);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            canvas = new Canvas(id, texture, width, height, hash);
        } else {
            canvas = new Canvas(canvas.id(), canvas.texture(), width, height, hash);
        }
        CANVASES.put(pos, canvas);
        NativeImage image = canvas.texture().getPixels();
        if (image != null) {
            switch (shown) {
                case BOOT -> testPattern(image);
                case NO_SIGNAL -> noSignal(image);
                default -> online(image, master);
            }
            canvas.texture().upload();
        }
        return canvas.id();
    }

    public static void release(BlockPos pos) {
        Canvas canvas = CANVASES.remove(pos);
        if (canvas != null) {
            Minecraft.getInstance().getTextureManager().release(canvas.id());
        }
    }

    // --- Painting ---

    static void fill(NativeImage image, int x0, int y0, int w, int h, int color) {
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y0 + h); y++) {
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x0 + w); x++) {
                image.setPixel(x, y, color);
            }
        }
    }

    // Text in the terminal font from (x, y), 1x or 2x, cut at the canvas.
    public static void text(NativeImage image, int x, int y, String text, int color, int scale) {
        NativeImage sheet = font();
        if (sheet == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            int glyph = TerminalFont.glyph(text.charAt(i));
            int gx = glyph % TerminalFont.COLUMNS * TerminalFont.CELL_W, gy = glyph / TerminalFont.COLUMNS * TerminalFont.CELL_H;
            for (int yy = 0; yy < TerminalFont.CELL_H; yy++) {
                for (int xx = 0; xx < TerminalFont.CELL_W; xx++) {
                    if ((sheet.getPixel(gx + xx, gy + yy) >>> 24) != 0) {
                        fill(image, x + (i * TerminalFont.CELL_W + xx) * scale, y + yy * scale, scale, scale, color);
                    }
                }
            }
        }
    }

    static int textWidth(String text, int scale) {
        return text.length() * TerminalFont.CELL_W * scale;
    }

    // What changes an online canvas: the content, the live frames, the images, and the minute for a clock.
    private static int onlineHash(DisplayPanelBlockEntity master) {
        DisplayContent content = master.displayContent();
        int images = 0;
        for (Map.Entry<String, int[]> image : master.images().entrySet()) {
            images = 31 * images + image.getKey().hashCode() * 17 + System.identityHashCode(image.getValue());
        }
        boolean clock = content.mode != DisplayContent.Mode.TEXT
                && content.regions(master.canvasWidth(), master.canvasHeight()).stream().anyMatch(r -> r.widget().kind().equals("*CLOCK"));
        long minute = clock && Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getOverworldClockTime() / 16 : 0;
        return Objects.hash(content.mode, content.background, content.lines, content.regions, DisplayFramePayload.frames(master.getBlockPos()), images,
                minute);
    }

    // Text mode: the lines over the background; Dashboard: the regions and their widgets.
    private static void online(NativeImage image, DisplayPanelBlockEntity master) {
        DisplayContent content = master.displayContent();
        fill(image, 0, 0, image.getWidth(), image.getHeight(), content.background != 0 ? content.background : BACKGROUND);
        NativeImage sheet = font();
        if (sheet == null) {
            return;
        }
        CanvasPainter painter = new CanvasPainter(image, sheet, smallFont());
        if (content.mode == DisplayContent.Mode.TEXT) {
            painter.lines(0, 0, image.getWidth(), image.getHeight(), content.lines);
            return;
        }
        Map<String, DisplayFrame> frames = new HashMap<>();
        for (DisplayFrame frame : DisplayFramePayload.frames(master.getBlockPos())) {
            frames.put(frame.region(), frame);
        }
        long dayTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getOverworldClockTime() : 0;
        for (DisplayContent.Region region : content.regions(master.canvasWidth(), master.canvasHeight())) {
            painter.region(region, frames.get(region.name()), content.lines, master.images(), dayTime);
        }
    }

    private static void noSignal(NativeImage image) {
        int w = image.getWidth(), h = image.getHeight();
        fill(image, 0, 0, w, h, DARK);
        String message = net.minecraft.network.chat.Component.translatable("display.encodedlogistics.no_signal").getString();
        int scale = w >= textWidth(message, 2) + 8 ? 2 : 1;
        int x = (w - textWidth(message, scale)) / 2, y = (h - TerminalFont.CELL_H * scale) / 2;
        text(image, x, y, message, WARNING, scale);
        fill(image, x, (h + TerminalFont.CELL_H * scale) / 2 + 2, textWidth(message, scale), 1, RULE);
    }

    private static void testPattern(NativeImage image) {
        NativeImage source = pattern();
        if (source == null) {
            fill(image, 0, 0, image.getWidth(), image.getHeight(), BACKGROUND);
            return;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setPixel(x, y, source.getPixel(x * source.getWidth() / image.getWidth(), y * source.getHeight() / image.getHeight()) | 0xFF000000);
            }
        }
    }

    private static @Nullable NativeImage font() {
        if (font == null) {
            font = load(TerminalFont.SHEET);
        }
        return font;
    }

    private static @Nullable NativeImage smallFont() {
        if (smallFont == null) {
            smallFont = load(SMALL_FONT);
        }
        return smallFont;
    }

    private static @Nullable NativeImage pattern() {
        if (pattern == null) {
            pattern = load(PATTERN);
        }
        return pattern;
    }

    public static @Nullable NativeImage load(Identifier id) {
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(id)) {
            return NativeImage.read(in);
        } catch (IOException e) {
            return null;
        }
    }
}
