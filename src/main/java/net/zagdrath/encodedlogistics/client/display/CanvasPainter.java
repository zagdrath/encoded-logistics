/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.display;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.client.crt.TerminalFont;
import net.zagdrath.encodedlogistics.display.DisplayContent;
import net.zagdrath.encodedlogistics.display.DisplayFrame;

// Paints a screen's canvas (HANDOFF 2, 3; the reference is tools/dsp_canvas.py): Text mode's lines (colour, alignment,
// 1x / 2x); or the regions, each its background and widget, clipped to it: gauges (track step 1, a step-0 shadow line,
// a step-3 lip; the fill in the status colours by level, or the widget's colour; lit top row, shaded bottom), graphs
// (steel chrome, a dotted grid every 8 px, axes; a line in the light-blue ramp with lit points, or two-tone bars;
// label top left, value top right), list rows (alternating steps 1 / 2, a status dot, text, a value right), an item
// counter (its icon, its count, its name), a clock, text, an image.
final class CanvasPainter {
    // The steel ramp (TEXTURE_STYLE), the light-blue dye ramp, and the palette's text colours.
    static final int[] S = { 0xFF1F2228, 0xFF2B2F36, 0xFF373C44, 0xFF454B54, 0xFF555B65, 0xFF666D77, 0xFF79808A, 0xFF8D949D, 0xFFA3A9B1, 0xFFBBC0C6,
            0xFFD3D7DB };
    static final int[] LB = { 0xFF17577B, 0xFF2678A0, 0xFF399CC6, 0xFF50C2EC, 0xFF70E8FF, 0xFF89F9FF, 0xFFA3FFFA };
    static final int TEXT = 0xFFF0F0F0, MUTED = 0xFFB4B4B4, ACCENT = 0xFF00D992, WARNING = 0xFFE8C24A, FAULT = 0xFFE5483C;
    // The status lights: lit, base, shade; green, yellow, orange, red.
    private static final int[][] STATUS = { { 0xFFB5FFB0, 0xFF3CE05A, 0xFF22A03C }, { 0xFFFFF3A0, 0xFFF0D030, 0xFFB89A14 },
            { 0xFFFFC890, 0xFFF08A2A, 0xFFB05E14 }, { 0xFFFF9C90, 0xFFE5483C, 0xFF8E231C } };

    private final NativeImage image;
    private final NativeImage font;
    // The clip: the region being painted.
    private int cx0, cy0, cx1, cy1;

    CanvasPainter(NativeImage image, NativeImage font) {
        this.image = image;
        this.font = font;
        clip(0, 0, image.getWidth(), image.getHeight());
    }

    void clip(int x, int y, int w, int h) {
        cx0 = Math.max(0, x);
        cy0 = Math.max(0, y);
        cx1 = Math.min(image.getWidth(), x + w);
        cy1 = Math.min(image.getHeight(), y + h);
    }

    void set(int x, int y, int color) {
        if (x >= cx0 && x < cx1 && y >= cy0 && y < cy1) {
            image.setPixel(x, y, color);
        }
    }

    void fill(int x0, int y0, int w, int h, int color) {
        for (int y = Math.max(cy0, y0); y < Math.min(cy1, y0 + h); y++) {
            for (int x = Math.max(cx0, x0); x < Math.min(cx1, x0 + w); x++) {
                image.setPixel(x, y, color);
            }
        }
    }

    // --- Text ---

    void text(int x, int y, String text, int color, int scale) {
        for (int i = 0; i < text.length(); i++) {
            int glyph = TerminalFont.glyph(text.charAt(i));
            int gx = glyph % TerminalFont.COLUMNS * TerminalFont.CELL_W, gy = glyph / TerminalFont.COLUMNS * TerminalFont.CELL_H;
            for (int yy = 0; yy < TerminalFont.CELL_H; yy++) {
                for (int xx = 0; xx < TerminalFont.CELL_W; xx++) {
                    if ((font.getPixel(gx + xx, gy + yy) >>> 24) != 0) {
                        fill(x + (i * TerminalFont.CELL_W + xx) * scale, y + yy * scale, scale, scale, color);
                    }
                }
            }
        }
    }

    static int width(String text, int scale) {
        return text.length() * TerminalFont.CELL_W * scale;
    }

    // Text lines in a box, LINES_PAD px in from its edges so none touches the bezel: each with its colour (0: the text
    // colour), alignment and scale.
    private static final int LINES_PAD = 3;

    void lines(int x, int y, int w, int h, List<DisplayContent.TextLine> lines) {
        int top = y + LINES_PAD;
        for (DisplayContent.TextLine line : lines) {
            int scale = Math.clamp(line.scale(), 1, 2);
            int lw = width(line.text(), scale);
            int lx = line.align() == DisplayContent.CENTRE ? x + (w - lw) / 2
                    : line.align() == DisplayContent.RIGHT ? x + w - lw - LINES_PAD : x + LINES_PAD;
            text(lx, top, line.text(), line.color() != 0 ? line.color() : TEXT, scale);
            top += TerminalFont.CELL_H * scale;
            if (top >= y + h) {
                break;
            }
        }
    }

    // --- Pieces ---

    // A widget frame: 1-px steel border (lit top / left step 5, shaded bottom / right step 3), field step 2 inside.
    void chrome(int x0, int y0, int w, int h) {
        fill(x0, y0, w, h, S[2]);
        for (int x = x0; x < x0 + w; x++) {
            set(x, y0, S[5]);
            set(x, y0 + h - 1, S[3]);
        }
        for (int y = y0; y < y0 + h; y++) {
            set(x0, y, S[5]);
            set(x0 + w - 1, y, S[3]);
        }
    }

    static int[] statusRamp(double fraction) {
        return STATUS[fraction < 0.5 ? 0 : fraction < 0.75 ? 1 : fraction < 0.99 ? 2 : 3];
    }

    // A ramp (lit, base, shade) from one colour.
    static int[] ramp(int color) {
        return new int[] { shade(color, 1.35F), color, shade(color, 0.7F) };
    }

    static int shade(int color, float by) {
        int r = Math.min(255, Math.round((color >> 16 & 0xFF) * by)), g = Math.min(255, Math.round((color >> 8 & 0xFF) * by)),
                b = Math.min(255, Math.round((color & 0xFF) * by));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // A bar gauge: the label above (muted), the value right; the track, the fill.
    void gauge(int x0, int y0, int w, int h, double fraction, @Nullable String label, @Nullable String value, int @Nullable [] ramp) {
        int ty = y0;
        if (label != null) {
            text(x0, y0, label, MUTED, 1);
            ty = y0 + 10;
        }
        if (value != null) {
            text(x0 + w - width(value, 1), y0, value, TEXT, 1);
        }
        fill(x0, ty, w, h, S[1]);
        for (int x = x0; x < x0 + w; x++) {
            set(x, ty, S[0]);
            set(x, ty + h - 1, S[3]);
        }
        double f = Math.clamp(fraction, 0, 1);
        int fw = (int) ((w - 2) * f);
        int[] r = ramp != null ? ramp : statusRamp(f);
        for (int yy = 1; yy < h - 1; yy++) {
            fill(x0 + 1, ty + yy, fw, 1, yy == 1 ? r[0] : yy == h - 2 ? r[2] : r[1]);
        }
    }

    // A line or bar graph of a series in a frame.
    void graph(int x0, int y0, int w, int h, List<Float> series, int[] col, boolean bars, @Nullable String label, @Nullable String value) {
        chrome(x0, y0, w, h);
        for (int gy = y0 + h - 2; gy > y0; gy -= 8) {
            for (int x = x0 + 2; x < x0 + w - 1; x += 2) {
                set(x, gy, S[3]);
            }
        }
        for (int x = x0 + 1; x < x0 + w - 1; x++) {
            set(x, y0 + h - 2, S[6]);
        }
        for (int y = y0 + 1; y < y0 + h - 1; y++) {
            set(x0 + 1, y, S[6]);
        }
        int n = series.size();
        if (n >= 2) {
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (float v : series) {
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
            float span = hi - lo == 0 ? 1 : hi - lo;
            int top = label != null ? 12 : 3;
            int[] px = new int[n], py = new int[n];
            for (int i = 0; i < n; i++) {
                px[i] = x0 + 2 + i * (w - 4) / (n - 1);
                py[i] = y0 + h - 3 - (int) ((series.get(i) - lo) / span * (h - 3 - top));
            }
            if (bars) {
                int bw = Math.max(1, (w - 4) / n - 1);
                for (int i = 0; i < n; i++) {
                    for (int yy = py[i]; yy < y0 + h - 2; yy++) {
                        for (int xx = 0; xx < bw; xx++) {
                            set(px[i] + xx, yy, yy == py[i] ? col[4] : xx < bw - 1 ? col[3] : col[2]);
                        }
                    }
                }
            } else {
                for (int i = 0; i + 1 < n; i++) {
                    int steps = Math.max(1, Math.max(Math.abs(px[i + 1] - px[i]), Math.abs(py[i + 1] - py[i])));
                    for (int k = 0; k <= steps; k++) {
                        set(Math.round(px[i] + (px[i + 1] - px[i]) * (float) k / steps), Math.round(py[i] + (py[i + 1] - py[i]) * (float) k / steps), col[3]);
                    }
                }
                for (int i = 0; i < n; i += Math.max(1, n / 8)) {
                    set(px[i], py[i], col[6]);
                }
            }
        }
        if (label != null) {
            text(x0 + 3, y0 + 2, label, MUTED, 1);
        }
        if (value != null) {
            text(x0 + w - 3 - width(value, 1), y0 + 2, value, TEXT, 1);
        }
    }

    // List rows: "dot|text|value", alternating backgrounds, a status dot.
    void rows(int x0, int y0, int w, int h, List<String> rows) {
        int rowH = 11;
        for (int i = 0; i < rows.size() && y0 + (i + 1) * rowH <= y0 + h + 1; i++) {
            String[] parts = rows.get(i).split("\\|", -1);
            String dot = parts[0], label = parts.length > 1 ? parts[1] : "", value = parts.length > 2 ? parts[2] : "";
            int y = y0 + i * rowH;
            fill(x0, y, w, rowH, i % 2 == 1 ? S[2] : S[1]);
            int col = switch (dot) {
                case "on" -> ACCENT;
                case "warn" -> WARNING;
                case "fault" -> FAULT;
                default -> S[5];
            };
            fill(x0 + 2, y + 4, 3, 3, col);
            set(x0 + 2, y + 4, shade(col, 1.3F));
            int room = (w - 10 - width(value, 1) - 4) / TerminalFont.CELL_W;
            text(x0 + 8, y + 1, label.length() > room ? label.substring(0, Math.max(0, room)) : label, dot.equals("off") ? MUTED : TEXT, 1);
            if (!value.isEmpty()) {
                text(x0 + w - width(value, 1) - 2, y + 1, value, MUTED, 1);
            }
        }
    }

    // An item's icon (its particle sprite), scaled.
    void icon(int x, int y, String id, int scale) {
        NativeImage sprite = sprite(id);
        if (sprite == null) {
            return;
        }
        int size = Math.min(sprite.getWidth(), sprite.getHeight());
        for (int yy = 0; yy < 16; yy++) {
            for (int xx = 0; xx < 16; xx++) {
                int argb = sprite.getPixel(xx * size / 16, yy * size / 16);
                if ((argb >>> 24) > 128) {
                    fill(x + xx * scale, y + yy * scale, scale, scale, argb | 0xFF000000);
                }
            }
        }
    }

    private static @Nullable NativeImage sprite(String id) {
        Identifier key = Identifier.tryParse(id);
        Item item = key != null ? BuiltInRegistries.ITEM.getValue(key) : null;
        Minecraft minecraft = Minecraft.getInstance();
        if (item == null || minecraft.level == null) {
            return null;
        }
        ItemStackRenderState state = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, new ItemStack(item), ItemDisplayContext.GUI, minecraft.level, null, 0);
        Material.Baked material = state.pickParticleMaterial(RandomSource.create(0));
        return material != null ? material.sprite().contents().getOriginalImage() : null;
    }

    void image(int x0, int y0, int w, int h, int @Nullable [] pixels) {
        if (pixels == null || pixels.length < w * h) {
            return;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                set(x0 + x, y0 + y, pixels[y * w + x] | 0xFF000000);
            }
        }
    }

    // --- A region's widget ---

    void region(DisplayContent.Region region, @Nullable DisplayFrame frame, List<DisplayContent.TextLine> lines, Map<String, int[]> images, long dayTime) {
        int x = region.x(), y = region.y(), w = region.w(), h = region.h();
        clip(x, y, w, h);
        if (region.bg() != 0) {
            fill(x, y, w, h, region.bg());
        }
        DisplayContent.Widget widget = region.widget();
        int[] colorRamp = widget.color() != 0 ? ramp(widget.color()) : null;
        switch (widget.kind()) {
            case "*TEXT" -> lines(x, y, w, h, lines);
            case "*CLOCK" -> clock(x, y, w, h, dayTime, widget.color() != 0 ? widget.color() : ACCENT);
            case "*IMAGE" -> image(x, y, w, h, images.get(region.name().toUpperCase(Locale.ROOT)));
            case "*STORAGE", "*COLD", "*ENERGY", "*LANES", "*UPS" -> {
                if (frame == null) {
                    break;
                }
                long total = frame.number(1);
                gauge(x + 1, y + 1, w - 2, Math.min(7, Math.max(4, h - 12)), total <= 0 ? 0 : (double) frame.number(0) / total, frame.label(), frame.value(),
                        colorRamp);
                if (widget.kind().equals("*UPS") && h > 30) {
                    rows(x, y + 20, w, h - 20, frame.rows());
                }
            }
            case "*JOBS", "*DEVICES" -> {
                if (frame == null) {
                    break;
                }
                text(x + 2, y + 1, frame.label(), MUTED, 1);
                text(x + w - 2 - width(frame.value(), 1), y + 1, frame.value(), TEXT, 1);
                rows(x, y + 11, w, h - 11, frame.rows());
            }
            case "*ITEM" -> {
                if (frame != null) {
                    item(x, y, w, h, widget.item(), frame.value(), widget.color() != 0 ? widget.color() : TEXT);
                }
            }
            case "*TABLE" -> {
                if (frame != null) {
                    table(x, y, w, h, frame, widget.color() != 0 ? widget.color() : ACCENT);
                }
            }
            case "*GRAPH" -> {
                if (frame != null) {
                    int[] col = widget.color() != 0 ? new int[] { shade(widget.color(), 0.5F), shade(widget.color(), 0.7F), shade(widget.color(), 0.85F),
                            widget.color(), shade(widget.color(), 1.2F), shade(widget.color(), 1.35F), shade(widget.color(), 1.5F) } : LB;
                    graph(x, y, w, h, frame.series(), col, widget.graph().equals("*BAR"), frame.label(), frame.value());
                }
            }
            default -> {}
        }
        clip(0, 0, image.getWidth(), image.getHeight());
    }

    // A file's records (DisplayData's table): the file and how many were selected on top, the headings on a bar in the
    // widget's colour, then a row a record (alternating backgrounds), each column as wide as its widest value (up to 24
    // characters), numbers right-aligned; columns that don't fit are left off. A file gone or a selection that fails:
    // its message, in the fault colour.
    private static final int TABLE_ROW = 11, TABLE_GAP = 6, TABLE_COLUMN = 24;

    private void table(int x, int y, int w, int h, DisplayFrame frame, int color) {
        text(x + 2, y + 1, frame.label(), MUTED, 1);
        text(x + w - 2 - width(frame.value(), 1), y + 1, frame.value(), TEXT, 1);
        List<String> rows = frame.rows();
        if (rows.isEmpty()) {
            return;
        }
        if (rows.getFirst().startsWith("!")) {
            String message = rows.getFirst().substring(1);
            int room = Math.max(1, (w - 4) / TerminalFont.CELL_W);
            for (int line = 0, at = 0; at < message.length() && y + TABLE_ROW * (line + 2) <= y + h; line++, at += room) {
                text(x + 2, y + TABLE_ROW * (line + 1) + 1, message.substring(at, Math.min(message.length(), at + room)), FAULT, 1);
            }
            return;
        }
        List<String[]> cells = new java.util.ArrayList<>();
        for (String row : rows) {
            cells.add(row.split("\t", -1));
        }
        int columns = cells.getFirst().length;
        int[] chars = new int[columns];
        for (String[] row : cells) {
            for (int c = 0; c < Math.min(columns, row.length); c++) {
                chars[c] = Math.min(TABLE_COLUMN, Math.max(chars[c], row[c].length()));
            }
        }
        for (int r = 0; r < cells.size() && y + TABLE_ROW * (r + 2) <= y + h; r++) {
            int ry = y + TABLE_ROW * (r + 1);
            fill(x, ry, w, TABLE_ROW, r == 0 ? shade(color, 0.45F) : r % 2 == 1 ? S[1] : S[2]);
            int cx = x + 2;
            for (int c = 0; c < columns && c < cells.get(r).length; c++) {
                int cw = chars[c] * TerminalFont.CELL_W;
                if (cx + cw > x + w - 1) {
                    break;
                }
                String value = cells.get(r)[c];
                if (value.length() > chars[c]) {
                    value = value.substring(0, chars[c]);
                }
                boolean right = r > 0 && frame.number(c) == 1;
                text(right ? cx + cw - width(value, 1) : cx, ry + 1, value, r == 0 ? color : TEXT, 1);
                cx += cw + TABLE_GAP;
            }
        }
    }

    // The count large beside the icon (2x where it fits), the item's name under it.
    private void item(int x, int y, int w, int h, String id, String value, int color) {
        int scale = h >= 44 && w >= 96 ? 2 : 1, iconSize = 16 * scale;
        icon(x + 2, y + 2, id, scale);
        int countScale = w - iconSize - 8 >= width(value, 2) && h >= 22 ? 2 : 1;
        text(x + iconSize + 6, y + 2, value, color, countScale);
        Item item = Identifier.tryParse(id) != null ? BuiltInRegistries.ITEM.getValue(Identifier.parse(id)) : null;
        String name = item != null ? item.getName(item.getDefaultInstance()).getString().toUpperCase(Locale.ROOT) : id;
        int nameY = Math.max(y + 2 + TerminalFont.CELL_H * countScale + 2, y + iconSize + 4);
        if (nameY + TerminalFont.CELL_H <= y + h) {
            int room = Math.max(0, (w - 4) / TerminalFont.CELL_W);
            text(x + 2, nameY, name.length() > room ? name.substring(0, room) : name, MUTED, 1);
        }
    }

    // HH:MM (2x where it fits) and the day.
    private void clock(int x, int y, int w, int h, long dayTime, int color) {
        long day = dayTime / 24_000 + 1, tick = dayTime % 24_000;
        long seconds = (tick * 86_400 / 24_000 + 6 * 3_600) % 86_400;
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 3_600, seconds / 60 % 60), days = "DAY " + day;
        int scale = w >= width(time, 2) + 4 && h >= 32 ? 2 : 1;
        int tx = x + (w - width(time, scale)) / 2;
        text(tx, y + 2, time, color, scale);
        int dy = y + 2 + TerminalFont.CELL_H * scale + 2;
        if (dy + TerminalFont.CELL_H <= y + h) {
            text(x + (w - width(days, 1)) / 2, dy, days, MUTED, 1);
        }
    }
}
