/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// What a screen shows (HANDOFF 3), kept on its master:
// - its mode: TEXT (its text lines over the whole screen, each with a colour, alignment and size) or DASHBOARD (regions
//   with widgets, set up on its screen or by ELCL - CHGDSPRGN and the SNDDSP* commands - alike);
// - its background colour, and its text lines (SNDDSPTXT writes them; a *TEXT region shows them too);
// - its regions: named rectangles in canvas px (A, B, C ...), never overlapping, each with a widget and a background.
//   With none set up, region A is the whole screen.
public final class DisplayContent {
    public enum Mode {
        TEXT, DASHBOARD;

        // Saved screens from before the two were one: Script-controlled is a dashboard.
        public static Mode byName(String name) {
            if (name.equalsIgnoreCase("SCRIPT")) {
                return DASHBOARD;
            }
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return TEXT;
        }
    }

    public static final int LEFT = 0, CENTRE = 1, RIGHT = 2;
    // Text sizes (SNDDSPTXT SIZE): *SMALL (a 4 x 6 font), *NORMAL (the 6 x 10 terminal font), *LARGE (2x), *HUGE (3x).
    public static final int SMALL = 0, NORMAL = 1, LARGE = 2, HUGE = 3;
    // A write's size, colour or alignment left as the line had it (every int is a colour, so not -1).
    public static final int KEEP = Integer.MIN_VALUE;
    // Text stands this many canvas px in from the screen's edges.
    public static final int TEXT_PAD = 3;

    // A character cell at a size, in canvas px.
    public static int cellWidth(int size) {
        return size <= SMALL ? 4 : 6 * Math.min(size, HUGE);
    }

    public static int cellHeight(int size) {
        return size <= SMALL ? 6 : 10 * Math.min(size, HUGE);
    }

    // A text line: its colour (ARGB; 0: the default text colour), alignment and size (SMALL - HUGE).
    public record TextLine(String text, int color, int align, int scale) {
        public static final Codec<TextLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(TextLine::text),
                Codec.INT.optionalFieldOf("color", 0).forGetter(TextLine::color),
                Codec.INT.optionalFieldOf("align", LEFT).forGetter(TextLine::align),
                Codec.INT.optionalFieldOf("scale", 1).forGetter(TextLine::scale))
                .apply(i, TextLine::new));

        public static TextLine of(String text) {
            return new TextLine(text, 0, LEFT, 1);
        }

        public TextLine withText(String text) {
            return new TextLine(text, color, align, scale);
        }

        // New text, and a new size, colour and alignment where they're given (KEEP: as it was).
        public TextLine with(String text, int size, int color, int align) {
            return new TextLine(text, color != KEEP ? color : this.color, align != KEEP ? align : this.align, size != KEEP ? size : scale);
        }
    }

    // A region's widget: its kind (*NONE, *TEXT, *STORAGE, *COLD, *ENERGY, *LANES, *JOBS, *ITEM, *CLOCK, *DEVICES, *UPS,
    // *GRAPH, *IMAGE, *TABLE) and what it needs: an item, a device type, a colour (0: its own), a graph's stat, range and
    // type, an image's file, scaling and colour mode; a table's file (LIB/NAME, in file), record selection (select, a
    // RUNQRY QRYSLT; blank: all) and sort (blank-joined SORT fields; blank: the file's order).
    public record Widget(String kind, String item, String devType, int color, String stat, String range, String graph, String file, String scale,
            String colors, String select, String sort) {
        public static final Widget NONE = new Widget("*NONE", "", "*ALL", 0, "", "*10M", "*LINE", "", "*DITHER", "*DFT");
        public static final List<String> KINDS = List.of("*NONE", "*TEXT", "*STORAGE", "*COLD", "*ENERGY", "*LANES", "*JOBS", "*ITEM", "*CLOCK",
                "*DEVICES", "*UPS", "*GRAPH", "*IMAGE", "*TABLE");

        public Widget(String kind, String item, String devType, int color, String stat, String range, String graph, String file, String scale,
                String colors) {
            this(kind, item, devType, color, stat, range, graph, file, scale, colors, "", "");
        }

        public static final Codec<Widget> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("kind", "*NONE").forGetter(Widget::kind),
                Codec.STRING.optionalFieldOf("item", "").forGetter(Widget::item),
                Codec.STRING.optionalFieldOf("dev_type", "*ALL").forGetter(Widget::devType),
                Codec.INT.optionalFieldOf("color", 0).forGetter(Widget::color),
                Codec.STRING.optionalFieldOf("stat", "").forGetter(Widget::stat),
                Codec.STRING.optionalFieldOf("range", "*10M").forGetter(Widget::range),
                Codec.STRING.optionalFieldOf("graph", "*LINE").forGetter(Widget::graph),
                Codec.STRING.optionalFieldOf("file", "").forGetter(Widget::file),
                Codec.STRING.optionalFieldOf("scale", "*DITHER").forGetter(Widget::scale),
                Codec.STRING.optionalFieldOf("colors", "*DFT").forGetter(Widget::colors),
                Codec.STRING.optionalFieldOf("select", "").forGetter(Widget::select),
                Codec.STRING.optionalFieldOf("sort", "").forGetter(Widget::sort))
                .apply(i, Widget::new));

        public static Widget of(String kind) {
            return new Widget(kind, "", "*ALL", 0, "", "*10M", "*LINE", "", "*DITHER", "*DFT");
        }

        public Widget withColor(int color) {
            return new Widget(kind, item, devType, color, stat, range, graph, file, scale, colors, select, sort);
        }

        // Whether it shows values from the network (refreshed every second).
        public boolean live() {
            return !kind.equals("*NONE") && !kind.equals("*TEXT") && !kind.equals("*IMAGE") && !kind.equals("*CLOCK");
        }
    }

    public record Region(String name, int x, int y, int w, int h, int bg, Widget widget) {
        public static final Codec<Region> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Region::name),
                Codec.INT.fieldOf("x").forGetter(Region::x),
                Codec.INT.fieldOf("y").forGetter(Region::y),
                Codec.INT.fieldOf("w").forGetter(Region::w),
                Codec.INT.fieldOf("h").forGetter(Region::h),
                Codec.INT.optionalFieldOf("bg", 0).forGetter(Region::bg),
                Widget.CODEC.optionalFieldOf("widget", Widget.NONE).forGetter(Region::widget))
                .apply(i, Region::new));

        public boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        public boolean overlaps(Region other) {
            return x < other.x + other.w && other.x < x + w && y < other.y + other.h && other.y < y + h;
        }

        public Region with(Widget widget) {
            return new Region(name, x, y, w, h, bg, widget);
        }

        public Region at(int x, int y, int w, int h, int bg) {
            return new Region(name, x, y, w, h, bg, widget);
        }
    }

    public static final Codec<DisplayContent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("mode", "TEXT").forGetter(c -> c.mode.name()),
            Codec.INT.optionalFieldOf("background", 0).forGetter(c -> c.background),
            TextLine.CODEC.listOf().optionalFieldOf("lines", List.of()).forGetter(c -> c.lines),
            Codec.INT.optionalFieldOf("next", 0).forGetter(c -> c.next),
            Region.CODEC.listOf().optionalFieldOf("regions", List.of()).forGetter(c -> c.regions))
            .apply(i, (mode, background, lines, next, regions) -> new DisplayContent(Mode.byName(mode), background, lines, next, regions)));

    public Mode mode;
    public int background;
    public final List<TextLine> lines;
    public int next;
    public final List<Region> regions;

    public DisplayContent(Mode mode, int background, List<TextLine> lines, int next, List<Region> regions) {
        this.mode = mode;
        this.background = background;
        this.lines = new ArrayList<>(lines);
        this.next = next;
        this.regions = new ArrayList<>(regions);
    }

    public DisplayContent() {
        this(Mode.TEXT, 0, List.of(), 0, List.of());
    }

    public DisplayContent copy() {
        return new DisplayContent(mode, background, lines, next, regions);
    }

    public List<String> texts() {
        return lines.stream().map(TextLine::text).toList();
    }

    // The regions as they are on a screen of this size: the default A over all of it when none are set up.
    public List<Region> regions(int canvasW, int canvasH) {
        return regions.isEmpty() ? List.of(new Region("A", 0, 0, canvasW, canvasH, 0, Widget.NONE)) : regions;
    }

    public @Nullable Region region(String name, int canvasW, int canvasH) {
        for (Region region : regions(canvasW, canvasH)) {
            if (region.name().equalsIgnoreCase(name)) {
                return region;
            }
        }
        return null;
    }

    // Sets a region (by name), the default A made real first.
    public void put(Region region, int canvasW, int canvasH) {
        if (regions.isEmpty()) {
            regions.addAll(regions(canvasW, canvasH));
        }
        for (int i = 0; i < regions.size(); i++) {
            if (regions.get(i).name().equalsIgnoreCase(region.name())) {
                regions.set(i, region);
                return;
            }
        }
        regions.add(region);
    }

    // A smaller screen: regions clamped to it (dropped when nothing's left of them); text lines past it dropped.
    public void clamp(int canvasW, int canvasH, int maxLines) {
        List<Region> kept = new ArrayList<>();
        for (Region region : regions) {
            int x = Math.min(region.x(), canvasW - 1), y = Math.min(region.y(), canvasH - 1);
            int w = Math.min(region.w(), canvasW - x), h = Math.min(region.h(), canvasH - y);
            if (w > 0 && h > 0 && x >= 0 && y >= 0) {
                kept.add(region.at(x, y, w, h, region.bg()));
            }
        }
        regions.clear();
        regions.addAll(kept);
        while (lines.size() > maxLines) {
            lines.removeLast();
        }
        next = Math.min(next, maxLines);
    }

    // --- Colours: the panel palette (textures/display/palette.json) by name, or #RRGGBB ---

    public static final Map<String, Integer> COLORS = Map.ofEntries(Map.entry("*DARK", 0xFF1F2228), Map.entry("*STEEL", 0xFF373C44),
            Map.entry("*GREY", 0xFF555B65), Map.entry("*SILVER", 0xFF79808A), Map.entry("*LIGHT", 0xFFA3A9B1), Map.entry("*WHITE", 0xFFD3D7DB),
            Map.entry("*NAVY", 0xFF2678A0), Map.entry("*BLUE", 0xFF50C2EC), Map.entry("*CYAN", 0xFF89F9FF), Map.entry("*RUST", 0xFFC65217),
            Map.entry("*ORANGE", 0xFFFF9B44), Map.entry("*YELLOW", 0xFFECC138), Map.entry("*GREEN", 0xFF3CE05A), Map.entry("*FOREST", 0xFF22A03C),
            Map.entry("*RED", 0xFFBA3B37), Map.entry("*BROWN", 0xFF6A4A2A), Map.entry("*MINT", 0xFF00D992));

    // A colour parameter: *DFT (0), a name, or #RRGGBB; -1 when it's none of them.
    public static int color(String value) {
        String text = value.trim().toUpperCase(Locale.ROOT);
        if (text.isEmpty() || text.equals("*DFT")) {
            return 0;
        }
        Integer named = COLORS.get(text);
        if (named != null) {
            return named;
        }
        if (text.matches("#[0-9A-F]{6}")) {
            return 0xFF000000 | Integer.parseInt(text.substring(1), 16);
        }
        return -1;
    }
}
