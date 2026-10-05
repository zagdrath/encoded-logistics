/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.zagdrath.encodedlogistics.display.DisplayContent;
import net.zagdrath.encodedlogistics.display.DisplayImages;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.elcl.device.DisplayDevice;
import net.zagdrath.encodedlogistics.elcl.device.Displays;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;

// The Display Panel commands (display handoff 6): CLRDSP clears a region or the whole screen, CHGDSPRGN defines or
// changes a region (canvas px), SNDDSPWDG places a dashboard widget, SNDDSPGPH a graph, SNDDSPIMG an image from the
// system's images folder, RTVDSPSIZ returns the size. ELC1301 (no such device), ELC1303 (not a display), ELC1314 (a
// region outside the screen, overlapping another, or not there), ELC1316 (a data source the widget can't use), and
// for images ELC1312 / ELC1313 / ELC1315, and ELC1317 (a diagnostic) when the colour mode is above the server's limit.
// A screen in Text mode that a script sets up is Script-controlled after.
final class DisplayCommands {
    private DisplayCommands() {}

    record Target(ElclSystem system, DisplayPanelBlockEntity display) {}

    private static Target display(Invocation call) throws ElclException {
        ElclContext context = OsCommands.context(call);
        if (context.network() == null) {
            throw new ElclException("ELC1302", "*NETWORK");
        }
        ElclSystem system = new ElclSystem(context.server(), context.network());
        String name = call.text("DEV").toUpperCase(Locale.ROOT);
        DisplayDevice found = Displays.find(system, name);
        if (found instanceof DisplayPanelBlockEntity display) {
            return new Target(system, display);
        }
        ElclDevices.Device device = ElclDevices.find(context.server(), context.network(), name);
        throw device != null || found != null ? new ElclException("ELC1303", name, device != null ? device.type() : "DSP")
                : new ElclException("ELC1301", name);
    }

    private static DisplayContent.Region region(DisplayPanelBlockEntity display, String name) throws ElclException {
        DisplayContent.Region region = display.displayContent().region(name, display.canvasWidth(), display.canvasHeight());
        if (region == null) {
            throw new ElclException("ELC1314", name.toUpperCase(Locale.ROOT));
        }
        return region;
    }

    private static int color(Invocation call, String keyword) throws ElclException {
        String value = call.text(keyword);
        int color = DisplayContent.color(value);
        if (color < 0) {
            throw new ElclException("ELC0003", value, "COLOUR");
        }
        return color;
    }

    // Set up by a script: a Text screen becomes Script-controlled.
    private static void scripted(DisplayPanelBlockEntity display) {
        if (display.displayContent().mode == DisplayContent.Mode.TEXT) {
            display.displayContent().mode = DisplayContent.Mode.SCRIPT;
        }
    }

    static void bind() {
        CommandRegistry.bind("CLRDSP", call -> {
            DisplayPanelBlockEntity display = display(call).display();
            DisplayContent content = display.displayContent();
            String name = call.text("RGN");
            if (name.equalsIgnoreCase("*ALL")) {
                content.lines.clear();
                content.next = 0;
                content.regions.clear();
                display.images().clear();
            } else {
                DisplayContent.Region region = region(display, name);
                content.put(region.with(DisplayContent.Widget.NONE), display.canvasWidth(), display.canvasHeight());
                display.forgetImage(region.name());
            }
            display.changed();
        });

        CommandRegistry.bind("CHGDSPRGN", call -> {
            DisplayPanelBlockEntity display = display(call).display();
            DisplayContent content = display.displayContent();
            int cw = display.canvasWidth(), ch = display.canvasHeight();
            String name = call.text("RGN").toUpperCase(Locale.ROOT);
            if (!name.matches("[A-Z0-9]{1,8}")) {
                throw new ElclException("ELC0003", name, "*NAME");
            }
            DisplayContent.Region old = content.region(name, cw, ch);
            int x = same(call, "X", old != null ? old.x() : 0), y = same(call, "Y", old != null ? old.y() : 0);
            int w = same(call, "W", old != null ? old.w() : cw - x), h = same(call, "H", old != null ? old.h() : ch - y);
            int bg = call.text("BG").equalsIgnoreCase("*SAME") ? old != null ? old.bg() : 0 : color(call, "BG");
            DisplayContent.Region region = new DisplayContent.Region(name, x, y, w, h, bg, old != null ? old.widget() : DisplayContent.Widget.NONE);
            if (w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > cw || y + h > ch) {
                throw new ElclException("ELC1314", name);
            }
            for (DisplayContent.Region other : content.regions(cw, ch)) {
                if (!other.name().equals(name) && other.overlaps(region)) {
                    throw new ElclException("ELC1314", name);
                }
            }
            content.put(region, cw, ch);
            if (old == null || old.w() != w || old.h() != h) {
                display.forgetImage(name);
            }
            scripted(display);
            display.changed();
        });

        CommandRegistry.bind("SNDDSPWDG", call -> {
            DisplayPanelBlockEntity display = display(call).display();
            DisplayContent.Region region = region(display, call.text("RGN"));
            String kind = call.text("WDG").toUpperCase(Locale.ROOT);
            String item = call.text("ITEM");
            if (kind.equals("*ITEM")) {
                item = item(item, kind);
            } else if (!item.equalsIgnoreCase("*NONE")) {
                throw new ElclException("ELC1316", item, kind);
            }
            String devType = call.text("DEVTYPE").toUpperCase(Locale.ROOT);
            if (!devType.equals("*ALL") && !kind.equals("*DEVICES")) {
                throw new ElclException("ELC1316", devType, kind);
            }
            DisplayContent.Widget widget = new DisplayContent.Widget(kind, kind.equals("*ITEM") ? item : "", devType, color(call, "COLOR"), "", "*10M",
                    "*LINE", "", "*DITHER", "*DFT");
            put(display, region.with(widget));
        });

        CommandRegistry.bind("SNDDSPGPH", call -> {
            DisplayPanelBlockEntity display = display(call).display();
            DisplayContent.Region region = region(display, call.text("RGN"));
            String stat = call.text("STAT").toUpperCase(Locale.ROOT);
            String item = call.text("ITEM");
            if (stat.equals("*ITEM")) {
                item = item(item, stat);
            } else if (!item.equalsIgnoreCase("*NONE")) {
                throw new ElclException("ELC1316", item, stat);
            }
            DisplayContent.Widget widget = new DisplayContent.Widget("*GRAPH", stat.equals("*ITEM") ? item : "", "*ALL", color(call, "COLOR"), stat,
                    call.text("RANGE").toUpperCase(Locale.ROOT), call.text("TYPE").toUpperCase(Locale.ROOT), "", "*DITHER", "*DFT");
            put(display, region.with(widget));
        });

        CommandRegistry.bind("SNDDSPIMG", call -> {
            Target target = display(call);
            DisplayPanelBlockEntity display = target.display();
            DisplayContent.Region region = region(display, call.text("RGN"));
            String colors = call.text("COLORS").toUpperCase(Locale.ROOT);
            if (!List.of("*DFT", "16", "64", "256", "*FULL").contains(colors)) {
                throw new ElclException("ELC0003", colors, "COLORS");
            }
            Path path = DisplayImages.check(target.system().server(), target.system().name(), call.text("FILE"));
            DisplayImages.Colors wanted = colors.equals("*DFT") ? DisplayImages.Colors.C256 : DisplayImages.Colors.of(colors);
            DisplayImages.Colors used = DisplayImages.capped(wanted);
            if (used != wanted) {
                call.send(ElclMessage.of("ELC1317", wanted.label(), used.label()));
            }
            DisplayContent.Widget widget = new DisplayContent.Widget("*IMAGE", "", "*ALL", 0, "", "*10M", "*LINE", path.getFileName().toString(),
                    call.text("SCALE").toUpperCase(Locale.ROOT), used.label());
            DisplayContent.Region placed = region.with(widget);
            put(display, placed);
            display.forgetImage(placed.name());
            display.renderImage(target.system().server(), placed, path);
        });

        CommandRegistry.bind("RTVDSPSIZ", call -> {
            DisplayPanelBlockEntity display = display(call).display();
            call.returns("RTNW", (long) display.width());
            call.returns("RTNH", (long) display.height());
            call.returns("RTNPXW", (long) display.canvasWidth());
            call.returns("RTNPXH", (long) display.canvasHeight());
        });
    }

    private static int same(Invocation call, String keyword, int otherwise) throws ElclException {
        return call.text(keyword).equalsIgnoreCase("*SAME") ? otherwise : (int) call.integer(keyword);
    }

    // An item for a widget or graph that needs one (ELC1316 without one, or one that isn't an item).
    private static String item(String spec, String kind) throws ElclException {
        if (spec.isBlank() || spec.equalsIgnoreCase("*NONE")) {
            throw new ElclException("ELC1316", "*NONE", kind);
        }
        Item item = ElclItems.resolve(spec);
        // Its registry id: the client finds its icon by it.
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static void put(DisplayPanelBlockEntity display, DisplayContent.Region region) {
        display.displayContent().put(region, display.canvasWidth(), display.canvasHeight());
        if (!region.widget().kind().equals("*IMAGE")) {
            display.forgetImage(region.name());
        }
        scripted(display);
        display.changed();
    }
}
