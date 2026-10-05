/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.display.DisplayContent;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlockEntity;
import net.zagdrath.encodedlogistics.menu.DisplayPanelMenu;
import net.zagdrath.encodedlogistics.net.DisplayConfigPayload;

// A Display Panel screen's configuration (HANDOFF 4; previews/gui_display_*): the L3 Switch panel's frame
// (gui/display_panel.png: title band, tab row, list inset, bottom row) with three tabs.
// - Mode: Text / Dashboard / Script-controlled (the chosen one pressed, a dot and accent text), the device name, the
//   background (the default and the panel palette).
// - Layout: the screen's canvas (at up to 1.5x) with its regions. Drag an edge between regions to move it; drag an edge
//   on the screen's border inward to split a region there; right-click a region to remove it. Edges snap to SNAP px.
// - Widgets: a row per region (its letter, widget, data source, colour); "Change widget" opens the picker: the widget,
//   its source (an item, a device type; a graph's stat, range and type; an image's file, scaling and colours) and colour.
// Changes go to the server (DisplayConfigPayload), which checks the build permission and each change.
public class DisplayPanelScreen extends AbstractContainerScreen<DisplayPanelMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/display_panel.png"),
            TAB_ACTIVE = EncodedLogistics.id("rack/switch/tab_active"), TAB_INACTIVE = EncodedLogistics.id("rack/switch/tab_inactive"),
            HIGHLIGHT = EncodedLogistics.id("common/row_highlight"), DOT = EncodedLogistics.id("hud/dot_online");
    private static final String[] TABS = { "gui.encodedlogistics.display.tab.mode", "gui.encodedlogistics.display.tab.layout",
            "gui.encodedlogistics.display.tab.widgets" };
    private static final int TABS_X = 8, TABS_Y = 17, TAB_STEP = 53, TAB_W = 52;
    private static final int INSET_X = 9, INSET_Y = 31, INSET_W = 146, INSET_H = 110, ROW_H = 14, ROWS = 8, FOOTER_Y = 150;
    private static final int MODE_X = 10, MODE_W = 146, MODE_H = 14, FIELD_X = 74, NAME_Y = 92, SWATCH_Y = 108, SWATCH = 8;
    private static final int PREVIEW_H = 96, SNAP = 4, EDGE = 2;
    private static final int[] SWATCHES = { 0, 0xFF1F2228, 0xFF373C44, 0xFF555B65, 0xFF79808A, 0xFFA3A9B1, 0xFFD3D7DB, 0xFF2678A0, 0xFF50C2EC,
            0xFF89F9FF, 0xFFC65217, 0xFFFF9B44, 0xFFECC138, 0xFF3CE05A, 0xFF22A03C, 0xFFBA3B37, 0xFF6A4A2A };
    private static final List<String> STATS = List.of("*ENERGY", "*ITEMFLOW", "*LANES", "*STORAGE", "*CRAFTING", "*ITEM", "*MCHOPS", "*MCHFE"),
            RANGES = List.of("*1M", "*10M", "*1H", "*1D"), TYPES = List.of("*LINE", "*BAR"), SCALES = List.of("*DITHER", "*NEAREST"),
            COLORS = List.of("256", "64", "16", "*FULL");

    private int tab;
    // Widgets: the row chosen, and the picker.
    private int selected, scroll;
    private boolean picking;
    private String kind = "*NONE";
    private int stat, range = 1, type, scale, colors, color;
    private @Nullable EditBox name, item, devType, file;
    // Layout: the regions being dragged, which edge, and the split line.
    private @Nullable List<DisplayContent.Region> editing;
    private int dragRegion = -1, dragSide, dragFrom, dragTo;
    private boolean dragOuter;

    public DisplayPanelScreen(DisplayPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 168);
        inventoryLabelY = 1_000;
    }

    private @Nullable DisplayPanelBlockEntity display() {
        return menu.display();
    }

    private void send(String action, CompoundTag data) {
        ClientPacketDistributor.sendToServer(new DisplayConfigPayload(menu.containerId, action, data));
    }

    @Override
    protected void init() {
        super.init();
        DisplayPanelBlockEntity display = display();
        name = box(leftPos + FIELD_X + 3, topPos + NAME_Y + 2, 76, 10, display != null ? display.name() : "");
        item = box(leftPos + 44, topPos + 99, 108, 32, "");
        devType = box(leftPos + 44, topPos + 99, 108, 10, "*ALL");
        file = box(leftPos + 44, topPos + 99, 108, 64, "");
        updateWidgets();
    }

    private EditBox box(int x, int y, int w, int length, String value) {
        EditBox box = new EditBox(font, x, y, w, 9, Component.empty());
        box.setBordered(false);
        box.setMaxLength(length);
        box.setTextColor(PartScreens.TEXT);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    // Which fields show: the name on Mode; the picker's on Widgets.
    private void updateWidgets() {
        if (name == null || item == null || devType == null || file == null) {
            return;
        }
        name.setVisible(tab == 0);
        // *ITEM's item, or a machine stat's machine (its device name), in the same field.
        boolean graphItem = kind.equals("*GRAPH") && (STATS.get(stat).equals("*ITEM") || STATS.get(stat).startsWith("*MCH"));
        item.setVisible(tab == 2 && picking && (kind.equals("*ITEM") || graphItem));
        item.setY(topPos + (graphItem ? 111 : 99));
        devType.setVisible(tab == 2 && picking && kind.equals("*DEVICES"));
        file.setVisible(tab == 2 && picking && kind.equals("*IMAGE"));
    }

    private List<DisplayContent.Region> regions(DisplayPanelBlockEntity display) {
        return display.displayContent().regions(display.canvasWidth(), display.canvasHeight());
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String bare(String special) {
        return special.startsWith("*") ? special.substring(1).toLowerCase(Locale.ROOT) : special.toLowerCase(Locale.ROOT);
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        for (int i = 0; i < TABS.length; i++) {
            boolean active = i == tab;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, active ? TAB_ACTIVE : TAB_INACTIVE, x + TABS_X + i * TAB_STEP, y + TABS_Y, TAB_W, active ? 13 : 12);
        }
        DisplayPanelBlockEntity display = display();
        if (display == null) {
            return;
        }
        switch (tab) {
            case 0 -> modeBackground(graphics, display, mouseX, mouseY);
            case 1 -> layoutBackground(graphics, display, mouseX, mouseY);
            default -> widgetsBackground(graphics, display, mouseX, mouseY);
        }
    }

    private void modeBackground(GuiGraphicsExtractor graphics, DisplayPanelBlockEntity display, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        DisplayContent.Mode current = display.displayContent().mode;
        for (DisplayContent.Mode mode : DisplayContent.Mode.values()) {
            int by = y + INSET_Y + 2 + mode.ordinal() * (MODE_H + 4);
            String label = tr("gui.encodedlogistics.display.mode." + mode.name().toLowerCase(Locale.ROOT));
            if (mode == current) {
                PartScreens.wideButtonPressed(graphics, x + MODE_X, by, MODE_W, MODE_H);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DOT, x + MODE_X + 4, by + 4, 5, 5);
                graphics.text(font, label, x + MODE_X + 14, by + 3, PartScreens.ACCENT, false);
            } else {
                PartScreens.wideButton(graphics, font, x + MODE_X, by, MODE_W, MODE_H, Component.literal(label), true, mouseX, mouseY);
            }
        }
        graphics.fill(x + FIELD_X, y + NAME_Y, x + FIELD_X + 82, y + NAME_Y + 12, 0xFF1A1A1A);
        int background = display.displayContent().background;
        for (int i = 0; i < SWATCHES.length; i++) {
            int sx = x + FIELD_X + (i % 9) * (SWATCH + 1), sy = y + SWATCH_Y + (i / 9) * (SWATCH + 1);
            swatch(graphics, sx, sy, SWATCHES[i], SWATCHES[i] == background);
        }
    }

    private void swatch(GuiGraphicsExtractor graphics, int x, int y, int color, boolean chosen) {
        graphics.fill(x, y, x + SWATCH, y + SWATCH, chosen ? PartScreens.ACCENT : 0xFF101010);
        graphics.fill(x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, color != 0 ? color : 0xFF2B2F36);
        if (color == 0) {
            // The default: a diagonal.
            for (int i = 1; i < SWATCH - 1; i++) {
                graphics.fill(x + i, y + i, x + i + 1, y + i + 1, PartScreens.TEXT_MUTED);
            }
        }
    }

    // The preview: where the canvas sits and how big a canvas px is.
    private float previewScale(DisplayPanelBlockEntity display) {
        return Math.min(1.5F, Math.min((float) (INSET_W - 2) / display.canvasWidth(), (float) PREVIEW_H / display.canvasHeight()));
    }

    private int previewX(DisplayPanelBlockEntity display) {
        return leftPos + INSET_X + (INSET_W - Math.round(display.canvasWidth() * previewScale(display))) / 2;
    }

    private int previewY(DisplayPanelBlockEntity display) {
        return topPos + INSET_Y + 1 + (PREVIEW_H - Math.round(display.canvasHeight() * previewScale(display))) / 2;
    }

    private void layoutBackground(GuiGraphicsExtractor graphics, DisplayPanelBlockEntity display, int mouseX, int mouseY) {
        float s = previewScale(display);
        int px = previewX(display), py = previewY(display);
        graphics.fill(px, py, px + Math.round(display.canvasWidth() * s), py + Math.round(display.canvasHeight() * s), 0xFF1F2228);
        List<DisplayContent.Region> regions = editing != null ? editing : regions(display);
        int[] hovered = editing == null ? edgeAt(display, regions, mouseX, mouseY) : null;
        for (int i = 0; i < regions.size(); i++) {
            DisplayContent.Region region = regions.get(i);
            int x0 = px + Math.round(region.x() * s), y0 = py + Math.round(region.y() * s);
            int x1 = px + Math.round((region.x() + region.w()) * s), y1 = py + Math.round((region.y() + region.h()) * s);
            graphics.fill(x0, y0, x1, y1, 0xFF79808A);
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, region.bg() != 0 ? region.bg() : 0xFF373C44);
            if (hovered != null && hovered[0] == i) {
                int side = hovered[1];
                int lx0 = side == 1 ? x1 - 1 : x0, ly0 = side == 3 ? y1 - 1 : y0;
                int lx1 = side == 0 ? x0 + 1 : x1, ly1 = side == 2 ? y0 + 1 : y1;
                graphics.fill(lx0, ly0, lx1, ly1, PartScreens.ACCENT);
            }
        }
        // The split being dragged.
        if (editing != null && dragOuter && dragRegion >= 0) {
            DisplayContent.Region region = editing.get(dragRegion);
            if (dragSide <= 1) {
                int lx = px + Math.round(dragTo * s);
                graphics.fill(lx, py + Math.round(region.y() * s), lx + 1, py + Math.round((region.y() + region.h()) * s), PartScreens.ACCENT);
            } else {
                int ly = py + Math.round(dragTo * s);
                graphics.fill(px + Math.round(region.x() * s), ly, px + Math.round((region.x() + region.w()) * s), ly + 1, PartScreens.ACCENT);
            }
        }
    }

    private void widgetsBackground(GuiGraphicsExtractor graphics, DisplayPanelBlockEntity display, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        if (picking) {
            for (int i = 0; i < DisplayContent.Widget.KINDS.size(); i++) {
                String k = DisplayContent.Widget.KINDS.get(i);
                int bx = x + 10 + (i % 3) * 49, by = y + INSET_Y + 1 + (i / 3) * 12;
                if (k.equals(kind)) {
                    PartScreens.wideButtonPressed(graphics, bx, by, 48, 11);
                } else {
                    PartScreens.wideButton(graphics, font, bx, by, 48, 11, Component.empty(), true, mouseX, mouseY);
                }
            }
            for (EditBox box : new EditBox[] { item, devType, file }) {
                if (box != null && box.isVisible()) {
                    graphics.fill(box.getX() - 2, box.getY() - 2, box.getX() + box.getWidth() + 2, box.getY() + 10, 0xFF1A1A1A);
                }
            }
            if (kind.equals("*GRAPH")) {
                PartScreens.wideButton(graphics, font, x + 10, y + 97, 60, 12, Component.literal(tr("gui.encodedlogistics.display.stat." + bare(STATS.get(stat)))),
                        true, mouseX, mouseY);
                PartScreens.wideButton(graphics, font, x + 72, y + 97, 36, 12, Component.literal(bare(RANGES.get(range))), true, mouseX, mouseY);
                PartScreens.wideButton(graphics, font, x + 110, y + 97, 44, 12, Component.literal(tr("gui.encodedlogistics.display.graph." + bare(TYPES.get(type)))),
                        true, mouseX, mouseY);
            }
            if (kind.equals("*IMAGE")) {
                PartScreens.wideButton(graphics, font, x + 10, y + 111, 70, 12, Component.literal(tr("gui.encodedlogistics.display.scale." + bare(SCALES.get(scale)))),
                        true, mouseX, mouseY);
                PartScreens.wideButton(graphics, font, x + 82, y + 111, 72, 12, Component.literal(tr("gui.encodedlogistics.display.colors." + bare(COLORS.get(colors)))),
                        true, mouseX, mouseY);
            }
            if (!kind.equals("*IMAGE") && !kind.equals("*NONE")) {
                for (int i = 0; i < SWATCHES.length; i++) {
                    swatch(graphics, x + 12 + i * SWATCH, y + 127, SWATCHES[i], SWATCHES[i] == color);
                }
            }
            PartScreens.wideButton(graphics, font, x + 8, y + 146, 78, 14, Component.translatable("gui.encodedlogistics.display.apply"), true, mouseX, mouseY);
            PartScreens.wideButton(graphics, font, x + 90, y + 146, 78, 14, Component.translatable("gui.encodedlogistics.display.cancel"), true, mouseX, mouseY);
            return;
        }
        List<DisplayContent.Region> regions = regions(display);
        selected = Math.min(selected, regions.size() - 1);
        for (int i = 0; i < ROWS && scroll + i < regions.size(); i++) {
            int ry = y + INSET_Y + i * ROW_H;
            DisplayContent.Region region = regions.get(scroll + i);
            if (scroll + i == selected) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT, x + INSET_X, ry, INSET_W, ROW_H);
            }
            int c = region.widget().color();
            if (c != 0) {
                graphics.fill(x + 147, ry + 3, x + 154, ry + 10, c);
            }
        }
        PartScreens.wideButton(graphics, font, x + 8, y + 146, 160, 14, Component.translatable("gui.encodedlogistics.display.change_widget"),
                !regions.isEmpty(), mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        DisplayPanelBlockEntity display = display();
        String header = title.getString() + (display != null ? "  " + display.name() + "  " + tr("gui.encodedlogistics.display.size", display.width(),
                display.height()) : "");
        graphics.text(font, header, 8, 5, PartScreens.TEXT, false);
        for (int i = 0; i < TABS.length; i++) {
            graphics.centeredText(font, Component.translatable(TABS[i]), TABS_X + i * TAB_STEP + TAB_W / 2, TABS_Y + 3,
                    i == tab ? PartScreens.TEXT : PartScreens.TEXT_MUTED);
        }
        if (display == null) {
            return;
        }
        List<DisplayContent.Region> regions = regions(display);
        String mode = tr("gui.encodedlogistics.display.mode." + display.displayContent().mode.name().toLowerCase(Locale.ROOT));
        switch (tab) {
            case 0 -> {
                graphics.text(font, Component.translatable("gui.encodedlogistics.display.name"), MODE_X + 2, NAME_Y + 2, PartScreens.TEXT_MUTED, false);
                graphics.text(font, Component.translatable("gui.encodedlogistics.display.background"), MODE_X + 2, SWATCH_Y, PartScreens.TEXT_MUTED, false);
                graphics.text(font, tr("gui.encodedlogistics.display.footer.mode", mode, regions.size()), 10, FOOTER_Y, PartScreens.TEXT, false);
            }
            case 1 -> {
                float s = previewScale(display);
                int px = previewX(display) - leftPos, py = previewY(display) - topPos;
                for (DisplayContent.Region region : editing != null ? editing : regions) {
                    graphics.text(font, region.name(), px + Math.round(region.x() * s) + 3, py + Math.round(region.y() * s) + 3, PartScreens.ACCENT, false);
                }
                graphics.text(font, Component.translatable("gui.encodedlogistics.display.layout_hint"), INSET_X + 1, INSET_Y + PREVIEW_H + 3,
                        PartScreens.TEXT_MUTED, false);
                graphics.text(font, tr("gui.encodedlogistics.display.footer.layout", regions.size(), display.canvasWidth(), display.canvasHeight()), 10,
                        FOOTER_Y, PartScreens.TEXT, false);
            }
            default -> widgetLabels(graphics, display, regions);
        }
    }

    private void widgetLabels(GuiGraphicsExtractor graphics, DisplayPanelBlockEntity display, List<DisplayContent.Region> regions) {
        if (picking) {
            for (int i = 0; i < DisplayContent.Widget.KINDS.size(); i++) {
                String k = DisplayContent.Widget.KINDS.get(i);
                graphics.centeredText(font, Component.translatable("gui.encodedlogistics.display.pick." + bare(k)), 10 + (i % 3) * 49 + 24,
                        INSET_Y + 3 + (i / 3) * 12, k.equals(kind) ? PartScreens.ACCENT : PartScreens.TEXT);
            }
            String field = kind.equals("*GRAPH") && STATS.get(stat).startsWith("*MCH") ? "machine"
                    : kind.equals("*ITEM") || kind.equals("*GRAPH") && STATS.get(stat).equals("*ITEM") ? "item"
                    : kind.equals("*DEVICES") ? "type" : kind.equals("*IMAGE") ? "file" : null;
            if (field != null) {
                int fy = kind.equals("*GRAPH") ? 111 : 99;
                graphics.text(font, Component.translatable("gui.encodedlogistics.display.field." + field), 12, fy, PartScreens.TEXT_MUTED, false);
            }
            return;
        }
        for (int i = 0; i < ROWS && scroll + i < regions.size(); i++) {
            DisplayContent.Region region = regions.get(scroll + i);
            int ry = INSET_Y + i * ROW_H + 3;
            graphics.text(font, region.name(), INSET_X + 3, ry, PartScreens.ACCENT, false);
            graphics.text(font, Component.translatable("gui.encodedlogistics.display.widget." + bare(region.widget().kind())), INSET_X + 14, ry, PartScreens.TEXT,
                    false);
            String source = font.plainSubstrByWidth(source(display, region.widget()), 74);
            graphics.text(font, source, INSET_X + 62, ry, PartScreens.TEXT_MUTED, false);
        }
    }

    // A widget's data source, as the Widgets list shows it.
    private String source(DisplayPanelBlockEntity display, DisplayContent.Widget widget) {
        return switch (widget.kind()) {
            case "*ITEM" -> itemName(widget.item());
            case "*DEVICES" -> widget.devType().equals("*ALL") ? tr("gui.encodedlogistics.display.source.all_devices") : widget.devType();
            case "*GRAPH" -> (widget.stat().equals("*ITEM") ? itemName(widget.item()) : widget.stat().startsWith("*MCH")
                    ? widget.item() + " " + tr("gui.encodedlogistics.display.stat." + bare(widget.stat())) : tr("gui.encodedlogistics.display.stat." + bare(widget.stat()))) + "  "
                    + bare(widget.range());
            case "*IMAGE" -> widget.file();
            case "*TEXT" -> tr("gui.encodedlogistics.display.source.text", display.textLines().size());
            case "*NONE" -> "-";
            default -> tr("gui.encodedlogistics.display.source." + bare(widget.kind()));
        };
    }

    private static String itemName(String id) {
        Identifier key = Identifier.tryParse(id);
        Item item = key != null ? BuiltInRegistries.ITEM.getValue(key) : null;
        return item != null ? item.getName(item.getDefaultInstance()).getString() : id;
    }

    // --- Layout editing ---

    // The region edge under the mouse: {region, side (0 left, 1 right, 2 top, 3 bottom), outer (on the screen's border)}.
    private int @Nullable [] edgeAt(DisplayPanelBlockEntity display, List<DisplayContent.Region> regions, double mouseX, double mouseY) {
        float s = previewScale(display);
        int px = previewX(display), py = previewY(display);
        for (int i = 0; i < regions.size(); i++) {
            DisplayContent.Region r = regions.get(i);
            double x0 = px + r.x() * s, y0 = py + r.y() * s, x1 = px + (r.x() + r.w()) * s, y1 = py + (r.y() + r.h()) * s;
            boolean inY = mouseY >= y0 && mouseY < y1, inX = mouseX >= x0 && mouseX < x1;
            int side = -1;
            if (inY && Math.abs(mouseX - x0) <= EDGE && mouseX >= x0) {
                side = 0;
            } else if (inY && Math.abs(mouseX - x1) <= EDGE && mouseX < x1) {
                side = 1;
            } else if (inX && Math.abs(mouseY - y0) <= EDGE && mouseY >= y0) {
                side = 2;
            } else if (inX && Math.abs(mouseY - y1) <= EDGE && mouseY < y1) {
                side = 3;
            }
            if (side >= 0) {
                int line = edge(r, side);
                boolean outer = side == 0 && line == 0 || side == 1 && line == display.canvasWidth() || side == 2 && line == 0
                        || side == 3 && line == display.canvasHeight();
                return new int[] { i, side, outer ? 1 : 0 };
            }
        }
        return null;
    }

    private static int edge(DisplayContent.Region r, int side) {
        return switch (side) {
            case 0 -> r.x();
            case 1 -> r.x() + r.w();
            case 2 -> r.y();
            default -> r.y() + r.h();
        };
    }

    private static DisplayContent.Region moved(DisplayContent.Region r, int side, int to) {
        return switch (side) {
            case 0 -> r.at(to, r.y(), r.x() + r.w() - to, r.h(), r.bg());
            case 1 -> r.at(r.x(), r.y(), to - r.x(), r.h(), r.bg());
            case 2 -> r.at(r.x(), to, r.w(), r.y() + r.h() - to, r.bg());
            default -> r.at(r.x(), r.y(), r.w(), to - r.y(), r.bg());
        };
    }

    private int canvasAt(DisplayPanelBlockEntity display, double mouse, boolean horizontal) {
        float s = previewScale(display);
        double at = horizontal ? (mouse - previewX(display)) / s : (mouse - previewY(display)) / s;
        int max = horizontal ? display.canvasWidth() : display.canvasHeight();
        return Math.clamp(Math.round(at / SNAP) * SNAP, 0, max);
    }

    // Moves an inner edge: the region's side and every region's opposite side on the same line beside it.
    private @Nullable List<DisplayContent.Region> moveEdge(List<DisplayContent.Region> regions, int index, int side, int from, int to) {
        DisplayContent.Region region = regions.get(index);
        int opposite = side ^ 1;
        List<DisplayContent.Region> next = new ArrayList<>();
        for (int i = 0; i < regions.size(); i++) {
            DisplayContent.Region r = regions.get(i);
            boolean alongside = side <= 1 ? r.y() < region.y() + region.h() && region.y() < r.y() + r.h() : r.x() < region.x() + region.w() && region.x() < r.x() + r.w();
            if (i == index) {
                r = moved(r, side, to);
            } else if (alongside && edge(r, opposite) == from) {
                r = moved(r, opposite, to);
            } else if (alongside && edge(r, side) == from) {
                r = moved(r, side, to);
            }
            if (r.w() < SNAP || r.h() < SNAP) {
                return null;
            }
            next.add(r);
        }
        for (int i = 0; i < next.size(); i++) {
            for (int j = i + 1; j < next.size(); j++) {
                if (next.get(i).overlaps(next.get(j))) {
                    return null;
                }
            }
        }
        return next;
    }

    private void sendRegions(List<DisplayContent.Region> regions) {
        CompoundTag data = new CompoundTag();
        DisplayContent.Region.CODEC.listOf().encodeStart(NbtOps.INSTANCE, regions).result().ifPresent(tag -> data.put("regions", tag));
        send("regions", data);
    }

    private String nextName(List<DisplayContent.Region> regions) {
        for (char c = 'A'; c <= 'Z'; c++) {
            String candidate = String.valueOf(c);
            if (regions.stream().noneMatch(r -> r.name().equalsIgnoreCase(candidate))) {
                return candidate;
            }
        }
        return "R" + regions.size();
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int x = (int) mx - leftPos, y = (int) my - topPos;
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT, right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        for (int i = 0; i < TABS.length; i++) {
            if (left && x >= TABS_X + i * TAB_STEP && x < TABS_X + i * TAB_STEP + TAB_W && y >= TABS_Y && y < TABS_Y + 13) {
                tab = i;
                picking = false;
                editing = null;
                updateWidgets();
                return true;
            }
        }
        DisplayPanelBlockEntity display = display();
        if (display == null) {
            return super.mouseClicked(event, doubleClick);
        }
        switch (tab) {
            case 0 -> {
                for (DisplayContent.Mode mode : DisplayContent.Mode.values()) {
                    if (left && PartScreens.over(x, y, MODE_X, INSET_Y + 2 + mode.ordinal() * (MODE_H + 4), MODE_W, MODE_H)) {
                        CompoundTag data = new CompoundTag();
                        data.putString("mode", mode.name());
                        send("mode", data);
                        return true;
                    }
                }
                for (int i = 0; i < SWATCHES.length; i++) {
                    if (left && PartScreens.over(x, y, FIELD_X + (i % 9) * (SWATCH + 1), SWATCH_Y + (i / 9) * (SWATCH + 1), SWATCH, SWATCH)) {
                        CompoundTag data = new CompoundTag();
                        data.putInt("color", SWATCHES[i]);
                        send("background", data);
                        return true;
                    }
                }
            }
            case 1 -> {
                List<DisplayContent.Region> regions = regions(display);
                int[] edge = edgeAt(display, regions, mx, my);
                if (left && edge != null) {
                    editing = new ArrayList<>(regions);
                    dragRegion = edge[0];
                    dragSide = edge[1];
                    dragOuter = edge[2] == 1;
                    dragFrom = edge(regions.get(dragRegion), dragSide);
                    dragTo = dragFrom;
                    return true;
                }
                if (right && regions.size() > 1) {
                    float s = previewScale(display);
                    int cx = (int) ((mx - previewX(display)) / s), cy = (int) ((my - previewY(display)) / s);
                    for (DisplayContent.Region region : regions) {
                        if (region.contains(cx, cy)) {
                            List<DisplayContent.Region> kept = new ArrayList<>(regions);
                            kept.remove(region);
                            sendRegions(kept);
                            return true;
                        }
                    }
                }
            }
            default -> {
                if (widgetsClicked(display, x, y, left)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean widgetsClicked(DisplayPanelBlockEntity display, int x, int y, boolean left) {
        if (!left) {
            return false;
        }
        List<DisplayContent.Region> regions = regions(display);
        if (!picking) {
            for (int i = 0; i < ROWS && scroll + i < regions.size(); i++) {
                if (PartScreens.over(x, y, INSET_X, INSET_Y + i * ROW_H, INSET_W, ROW_H)) {
                    selected = scroll + i;
                    return true;
                }
            }
            if (PartScreens.over(x, y, 8, 146, 160, 14) && !regions.isEmpty()) {
                openPicker(regions.get(Math.min(selected, regions.size() - 1)).widget());
                return true;
            }
            return false;
        }
        for (int i = 0; i < DisplayContent.Widget.KINDS.size(); i++) {
            if (PartScreens.over(x, y, 10 + (i % 3) * 49, INSET_Y + 1 + (i / 3) * 12, 48, 11)) {
                kind = DisplayContent.Widget.KINDS.get(i);
                updateWidgets();
                return true;
            }
        }
        if (kind.equals("*GRAPH")) {
            if (PartScreens.over(x, y, 10, 97, 60, 12)) {
                stat = (stat + 1) % STATS.size();
                updateWidgets();
                return true;
            }
            if (PartScreens.over(x, y, 72, 97, 36, 12)) {
                range = (range + 1) % RANGES.size();
                return true;
            }
            if (PartScreens.over(x, y, 110, 97, 44, 12)) {
                type = (type + 1) % TYPES.size();
                return true;
            }
        }
        if (kind.equals("*IMAGE")) {
            if (PartScreens.over(x, y, 10, 111, 70, 12)) {
                scale = (scale + 1) % SCALES.size();
                return true;
            }
            if (PartScreens.over(x, y, 82, 111, 72, 12)) {
                colors = (colors + 1) % COLORS.size();
                return true;
            }
        }
        for (int i = 0; i < SWATCHES.length; i++) {
            if (PartScreens.over(x, y, 12 + i * SWATCH, 127, SWATCH, SWATCH)) {
                color = SWATCHES[i];
                return true;
            }
        }
        if (PartScreens.over(x, y, 8, 146, 78, 14)) {
            applyWidget(regions.get(Math.min(selected, regions.size() - 1)));
            return true;
        }
        if (PartScreens.over(x, y, 90, 146, 78, 14)) {
            picking = false;
            updateWidgets();
            return true;
        }
        return false;
    }

    private void openPicker(DisplayContent.Widget widget) {
        picking = true;
        kind = widget.kind();
        stat = Math.max(0, STATS.indexOf(widget.stat()));
        range = Math.max(0, RANGES.indexOf(widget.range()));
        type = Math.max(0, TYPES.indexOf(widget.graph()));
        scale = Math.max(0, SCALES.indexOf(widget.scale()));
        colors = Math.max(0, COLORS.indexOf(widget.colors()));
        color = widget.color();
        if (item != null && devType != null && file != null) {
            item.setValue(widget.item());
            devType.setValue(widget.devType());
            file.setValue(widget.file());
        }
        updateWidgets();
    }

    private void applyWidget(DisplayContent.Region region) {
        String itemValue = item != null ? item.getValue().trim() : "", devValue = devType != null ? devType.getValue().trim().toUpperCase(Locale.ROOT) : "*ALL";
        DisplayContent.Widget widget = new DisplayContent.Widget(kind, itemValue, devValue.isEmpty() ? "*ALL" : devValue, color,
                kind.equals("*GRAPH") ? STATS.get(stat) : "", RANGES.get(range), TYPES.get(type), file != null ? file.getValue().trim() : "", SCALES.get(scale),
                COLORS.get(colors));
        CompoundTag data = new CompoundTag();
        data.putString("region", region.name());
        DisplayContent.Widget.CODEC.encodeStart(NbtOps.INSTANCE, widget).result().ifPresent(tag -> data.put("widget", tag));
        send("widget", data);
        picking = false;
        updateWidgets();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        DisplayPanelBlockEntity display = display();
        if (tab == 1 && editing != null && dragRegion >= 0 && display != null) {
            int to = canvasAt(display, dragSide <= 1 ? event.x() : event.y(), dragSide <= 1);
            if (dragOuter) {
                DisplayContent.Region region = editing.get(dragRegion);
                int low = dragSide <= 1 ? region.x() : region.y(), high = dragSide <= 1 ? region.x() + region.w() : region.y() + region.h();
                dragTo = Math.clamp(to, low + SNAP, high - SNAP);
            } else {
                List<DisplayContent.Region> next = moveEdge(regionsBeforeDrag(display), dragRegion, dragSide, dragFrom, to);
                if (next != null) {
                    editing = next;
                    dragTo = to;
                }
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    private List<DisplayContent.Region> regionsBeforeDrag(DisplayPanelBlockEntity display) {
        return regions(display);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        DisplayPanelBlockEntity display = display();
        if (tab == 1 && editing != null && display != null) {
            List<DisplayContent.Region> result = editing;
            if (dragOuter && dragTo != dragFrom && dragRegion >= 0) {
                // Split: the region keeps the part away from the border; a new one takes the rest.
                DisplayContent.Region region = editing.get(dragRegion);
                String name = nextName(editing);
                DisplayContent.Region kept, added;
                if (dragSide == 0) {
                    kept = region.at(dragTo, region.y(), region.x() + region.w() - dragTo, region.h(), region.bg());
                    added = new DisplayContent.Region(name, region.x(), region.y(), dragTo - region.x(), region.h(), 0, DisplayContent.Widget.NONE);
                } else if (dragSide == 1) {
                    kept = region.at(region.x(), region.y(), dragTo - region.x(), region.h(), region.bg());
                    added = new DisplayContent.Region(name, dragTo, region.y(), region.x() + region.w() - dragTo, region.h(), 0, DisplayContent.Widget.NONE);
                } else if (dragSide == 2) {
                    kept = region.at(region.x(), dragTo, region.w(), region.y() + region.h() - dragTo, region.bg());
                    added = new DisplayContent.Region(name, region.x(), region.y(), region.w(), dragTo - region.y(), 0, DisplayContent.Widget.NONE);
                } else {
                    kept = region.at(region.x(), region.y(), region.w(), dragTo - region.y(), region.bg());
                    added = new DisplayContent.Region(name, region.x(), dragTo, region.w(), region.y() + region.h() - dragTo, 0, DisplayContent.Widget.NONE);
                }
                result = new ArrayList<>(editing);
                result.set(dragRegion, kept);
                if (result.size() < DisplayPanelMenu.MAX_REGIONS) {
                    result.add(added);
                }
            }
            if (!result.equals(regions(display))) {
                sendRegions(result);
            }
            editing = null;
            dragRegion = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        DisplayPanelBlockEntity display = display();
        if (tab == 2 && !picking && display != null) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, regions(display).size() - ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Typing goes to the field focused (the inventory key doesn't close the screen then); Enter saves the name.
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            return super.keyPressed(event);
        }
        if (name != null && name.isFocused() && event.isConfirmation()) {
            CompoundTag data = new CompoundTag();
            data.putString("name", name.getValue());
            send("name", data);
            name.setFocused(false);
            return true;
        }
        for (EditBox box : new EditBox[] { name, item, devType, file }) {
            if (box != null && box.isVisible() && box.isFocused()) {
                return box.keyPressed(event) || true;
            }
        }
        return super.keyPressed(event);
    }
}
