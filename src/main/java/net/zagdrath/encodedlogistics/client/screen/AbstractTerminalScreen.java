/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.CraftingClient;
import net.zagdrath.encodedlogistics.client.ExternalSearch;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.net.TerminalClickPayload;
import net.zagdrath.encodedlogistics.net.TerminalItemsPayload;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// The modular terminal screen, built from a TerminalLayout: a title bar with the search field, a grid of the network's
// items (as many rows as fit the window, between the layout's min and max), a scrollbar, the player's inventory, and
// a toolbar on a tab at the left (sort mode: name / amount / mod; sort direction; craftables shown always or only when
// searching; grid height: small / medium / tall / fill the window, which re-lays the open screen out; search mode:
// standard, or synced with JEI's search bar both ways, as AE2's is). The toolbar settings are saved (TerminalSettings).
// Counts are drawn at half size and abbreviated (1.2K, 34M, 5.1B); what the network can craft but doesn't have shows with
// "Craft" instead. Clicks work as in AE2: left click takes a stack, right click half of one, shift-click moves a stack
// into the inventory, shift-right-click takes one onto the cursor; clicking with an item held puts it in (right click:
// just one); Shift+wheel puts one in (up) or takes one (down); double-clicking an inventory stack puts every stack like
// it in. Middle-click or Ctrl-click on a craftable item (or any click on one
// the network has none of) asks how many to craft. Search matches names; "@" searches mod ids. Each terminal adds
// little more than its layout and title (AccessTerminalScreen).
public abstract class AbstractTerminalScreen<M extends AccessTerminalMenu> extends AbstractContainerScreen<M> {
    // palette.json
    private static final int TEXT = 0xFFF0F0F0, TEXT_MUTED = 0xFFB4B4B4, ERROR = 0xFFFF6B6B, ACCENT = 0xFF00D992;

    // The search lasts the session, across terminals; the toolbar settings are saved (TerminalSettings).
    private static String lastSearch = "";

    private final TerminalLayout layout;
    // The grid's rows: the menu's when it opened, until the height button changes them.
    private int rows;
    private @Nullable EditBox search;
    private List<Map.Entry<ItemKey, Long>> view = List.of();
    private int viewVersion = -1;
    private String viewSearch = "";
    // The view was last built in the order it already had (the mouse was over the grid), not sorted afresh.
    private boolean viewHeld;
    // Where the mouse was last drawn at, for the view's hold.
    private double lastMouseX = -1, lastMouseY = -1;
    private int scrollRow;
    private boolean draggingThumb;
    // The release of a click this screen handled itself, which vanilla mustn't act on as well.
    private boolean swallowRelease;

    protected AbstractTerminalScreen(M menu, Inventory inventory, Component title, TerminalLayout layout) {
        super(menu, inventory, title, layout.width, layout.height(menu.rows()));
        this.layout = layout;
        this.rows = menu.rows();
        this.titleLabelX = layout.titleLeft;
        this.titleLabelY = layout.titleTop;
        this.inventoryLabelX = layout.inventoryLeft;
        this.inventoryLabelY = layout.topHeight + rows * layout.rowHeight + layout.sectionHeight + layout.inventoryTopInBottom;
    }

    // The screen's height at the current rows (imageHeight stays what it was when the screen opened).
    private int screenHeight() {
        return layout.height(rows);
    }

    @Override
    public int getImageHeight() {
        return screenHeight();
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        boolean onTab = mouseX >= left + layout.toolbarLeft - 3 && mouseX < left && mouseY >= top + layout.toolbarTop - 3
                && mouseY < top + layout.toolbarTop + 1 + layout.buttonIcons.size() * layout.toolbarSpacing;
        return !onTab && (mouseX < left || mouseY < top || mouseX >= left + imageWidth || mouseY >= top + screenHeight());
    }

    // The search mode button: synced with JEI's search bar or not. Turning it on hands the terminal's search to JEI.
    // Without JEI it stays standard.
    private void toggleSearchSync() {
        if (ExternalSearch.field() == null) {
            return;
        }
        TerminalSettings.searchSynced(!TerminalSettings.searchSynced());
        ExternalSearch.Field jei = syncedField();
        if (jei != null && search != null) {
            jei.setText(search.getValue());
        }
    }

    // The height button: the next height setting, and the open screen laid out again at its rows. The menu's slots (all
    // below the grid) move with it on the client.
    private void cycleHeight() {
        TerminalLayout.Height[] heights = TerminalLayout.Height.values();
        TerminalSettings.height(heights[(TerminalSettings.height().ordinal() + 1) % heights.length]);
        int wanted = layout.rowsFor(height);
        if (wanted == rows) {
            return;
        }
        rows = wanted;
        int dy = (rows - menu.rows()) * layout.rowHeight;
        for (int i = 0; i < menu.slots.size(); i++) {
            menu.slots.set(i, MovedSlot.of(menu.slots.get(i), dy));
        }
        inventoryLabelY = layout.topHeight + rows * layout.rowHeight + layout.sectionHeight + layout.inventoryTopInBottom;
        scrollRow = Math.min(scrollRow, maxScroll());
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        topPos = (height - screenHeight()) / 2;
        search = new EditBox(font, leftPos + layout.searchLeft + layout.searchTextLeft, topPos + layout.searchTop + layout.searchTextTop,
                layout.searchWidth - layout.searchTextLeft - 2, 9, search, Component.translatable("gui.encodedlogistics.terminal.search"));
        search.setBordered(false);
        search.setMaxLength(layout.searchMaxLength);
        search.setTextColor(TEXT);
        search.setHint(Component.translatable("gui.encodedlogistics.terminal.search").withColor(0xFF7A7A7A));
        ExternalSearch.Field jei = syncedField();
        if (jei != null) {
            search.setValue(jei.text());
        } else if (search.getValue().isEmpty()) {
            search.setValue(lastSearch);
        }
        search.setResponder(text -> {
            lastSearch = text;
            scrollRow = 0;
            ExternalSearch.Field field = syncedField();
            if (field != null && !field.text().equals(text)) {
                field.setText(text);
            }
        });
        addRenderableWidget(search);
    }

    // JEI's search bar while the search mode is synced (and JEI is there), else null.
    private static ExternalSearch.@Nullable Field syncedField() {
        return TerminalSettings.searchSynced() ? ExternalSearch.field() : null;
    }

    // Synced: what's typed into JEI's search bar shows up here too.
    @Override
    protected void containerTick() {
        super.containerTick();
        ExternalSearch.Field jei = syncedField();
        if (jei != null && search != null && !search.isFocused() && !jei.text().equals(search.getValue())) {
            search.setValue(jei.text());
        }
    }

    // --- The list ---

    // What the grid shows. While the mouse is over the grid the order holds, as in AE2: counts change in place, an item
    // that runs out leaves a gap (a negative count) and new ones go on the end, so nothing moves under the cursor. The
    // list is sorted afresh once the mouse leaves the grid, or the search or a toolbar setting changes.
    private List<Map.Entry<ItemKey, Long>> view() {
        String text = search != null ? search.getValue() : "";
        boolean hold = inInsertArea(lastMouseX, lastMouseY);
        boolean resort = viewVersion == -1 || !text.equals(viewSearch) || viewHeld && !hold;
        if (resort || viewVersion != menu.version()) {
            List<Map.Entry<ItemKey, Long>> fresh = filtered(text);
            view = resort || !hold ? fresh : keepOrder(view, fresh);
            viewHeld = !resort && hold;
            viewVersion = menu.version();
            viewSearch = text;
        }
        return view;
    }

    // The fresh list in the old one's order: what's still there where it was, a gap for what's gone, new items after.
    private static List<Map.Entry<ItemKey, Long>> keepOrder(List<Map.Entry<ItemKey, Long>> old, List<Map.Entry<ItemKey, Long>> fresh) {
        Map<ItemKey, Long> counts = new LinkedHashMap<>();
        fresh.forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        List<Map.Entry<ItemKey, Long>> kept = new ArrayList<>(Math.max(old.size(), fresh.size()));
        for (Map.Entry<ItemKey, Long> entry : old) {
            Long count = counts.remove(entry.getKey());
            kept.add(Map.entry(entry.getKey(), count != null ? count : -1L));
        }
        counts.forEach((key, count) -> kept.add(Map.entry(key, count)));
        while (!kept.isEmpty() && kept.getLast().getValue() < 0) {
            kept.removeLast();
        }
        return kept;
    }

    private List<Map.Entry<ItemKey, Long>> filtered(String text) {
        String query = text.trim().toLowerCase(Locale.ROOT);
        boolean byMod = query.startsWith("@");
        String needle = byMod ? query.substring(1) : query;
        List<Map.Entry<ItemKey, Long>> entries = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> entry : menu.items().entrySet()) {
            ItemStack stack = entry.getKey().stack();
            String haystack = byMod ? BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()
                    : stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || haystack.contains(needle)) {
                entries.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        // What the network can make but has none of, with a count of 0.
        if (TerminalSettings.craftablesAlways() || !needle.isEmpty()) {
            for (ItemKey key : menu.craftables()) {
                if (menu.items().containsKey(key)) {
                    continue;
                }
                ItemStack stack = key.stack();
                String haystack = byMod ? BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()
                        : stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (needle.isEmpty() || haystack.contains(needle)) {
                    entries.add(Map.entry(key, 0L));
                }
            }
        }
        Comparator<Map.Entry<ItemKey, Long>> byName = Comparator.comparing(entry -> entry.getKey().stack().getHoverName().getString(),
                String.CASE_INSENSITIVE_ORDER);
        Comparator<Map.Entry<ItemKey, Long>> order = switch (TerminalSettings.sortMode()) {
            case NAME -> byName;
            case COUNT -> Comparator.<Map.Entry<ItemKey, Long>>comparingLong(Map.Entry::getValue).thenComparing(byName);
            case MOD -> Comparator.<Map.Entry<ItemKey, Long>, String>comparing(
                    entry -> BuiltInRegistries.ITEM.getKey(entry.getKey().stack().getItem()).getNamespace()).thenComparing(byName);
        };
        entries.sort(TerminalSettings.descending() ? order.reversed() : order);
        return entries;
    }

    private int maxScroll() {
        return Math.max(0, (view().size() + layout.columns - 1) / layout.columns - rows);
    }

    // The grid with the gaps between its squares.
    private boolean inInsertArea(double mouseX, double mouseY) {
        double x = mouseX - leftPos - layout.gridLeft, y = mouseY - topPos - layout.topHeight - layout.gridTopInRow;
        return x >= 0 && y >= 0 && x < layout.columns * layout.cell && y < rows * layout.cell;
    }

    // The entry under the mouse, or -1.
    private int hoveredIndex(double mouseX, double mouseY) {
        double x = mouseX - leftPos - layout.gridLeft, y = mouseY - topPos - layout.topHeight - layout.gridTopInRow;
        if (x < 0 || y < 0 || x >= layout.columns * layout.cell || y >= rows * layout.cell) {
            return -1;
        }
        int column = (int) x / layout.cell, row = (int) y / layout.cell;
        if (x - column * layout.cell >= 16 || y - row * layout.cell >= 16) {
            return -1;
        }
        int index = (scrollRow + row) * layout.columns + column;
        return index < view().size() && view().get(index).getValue() >= 0 ? index : -2;
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, layout.top, x, y, 0.0F, 0.0F, layout.width, layout.topHeight, 256, 32);
        for (int row = 0; row < rows; row++) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, layout.row, x, y + layout.topHeight + row * layout.rowHeight, 0.0F, 0.0F, layout.width,
                    layout.rowHeight, 256, 32);
        }
        int sectionTop = y + layout.topHeight + rows * layout.rowHeight;
        Identifier section = sectionTexture();
        if (section != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, section, x, sectionTop, 0.0F, 0.0F, layout.width, layout.sectionHeight, 256, 128);
            extractSection(graphics, x, sectionTop, mouseX, mouseY);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, layout.bottom, x, sectionTop + layout.sectionHeight, 0.0F, 0.0F, layout.width,
                layout.bottomHeight, 256, 128);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, search != null && search.isFocused() ? layout.searchSpriteFocused : layout.searchSprite,
                x + layout.searchLeft, y + layout.searchTop, layout.searchWidth, layout.searchHeight);

        // Toolbar: its tab, then the buttons on it.
        int buttons = layout.buttonIcons.size();
        if (buttons > 0) {
            int tx = x + layout.toolbarLeft - 3, ty = y + layout.toolbarTop - 3, th = Math.min(layout.tabHeight, buttons * layout.toolbarSpacing + 4);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, layout.tab, layout.tabWidth, layout.tabHeight, 0, 0, tx, ty, layout.tabWidth, th - 3);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, layout.tab, layout.tabWidth, layout.tabHeight, 0, layout.tabHeight - 3, tx, ty + th - 3,
                    layout.tabWidth, 3);
        }
        for (int button = 0; button < layout.buttonIcons.size(); button++) {
            int bx = x + layout.toolbarLeft, by = y + layout.toolbarTop + button * layout.toolbarSpacing;
            boolean hover = mouseX >= bx && mouseX < bx + 18 && mouseY >= by && mouseY < by + 18;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? layout.buttonHover : layout.button, bx, by, 18, 18);
            List<Identifier> icons = layout.buttonIcons.get(button);
            int state = buttonState(layout.buttonIds.get(button));
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icons.get(Math.min(state, icons.size() - 1)), bx + 1, by + 1, 16, 16);
        }

        // Grid.
        scrollRow = Math.min(scrollRow, maxScroll());
        List<Map.Entry<ItemKey, Long>> entries = view();
        int hovered = hoveredIndex(mouseX, mouseY);
        for (int cell = 0; cell < rows * layout.columns; cell++) {
            int index = scrollRow * layout.columns + cell;
            int cx = x + layout.gridLeft + (cell % layout.columns) * layout.cell;
            int cy = y + layout.topHeight + layout.gridTopInRow + (cell / layout.columns) * layout.cell;
            if (index < entries.size() && entries.get(index).getValue() >= 0) {
                ItemKey key = entries.get(index).getKey();
                graphics.item(key.stack(), cx, cy);
                coldMarks(graphics, key, cx, cy);
            }
            if (index == hovered || hovered == -2 && cell == cellUnder(mouseX, mouseY)) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, layout.slotHighlight, cx, cy, 16, 16);
            }
        }

        // Scrollbar.
        int max = maxScroll();
        boolean overThumb = mouseX >= x + layout.scrollLeft && mouseX < x + layout.scrollLeft + layout.thumbWidth && mouseY >= y + thumbY()
                && mouseY < y + thumbY() + layout.thumbHeight;
        Identifier thumb = max == 0 ? layout.thumbDisabled : draggingThumb || overThumb ? layout.thumbHover : layout.thumb;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, thumb, x + layout.scrollLeft, y + thumbY(), layout.thumbWidth, layout.thumbHeight);
    }

    // terminal/cold_items.json: an item on tape has the tape badge at its bottom left; one the player is waiting on from
    // tape, a progress bar along its bottom and the spinner top right.
    private static final Identifier TAPE_BADGE = EncodedLogistics.id("terminal/tape_badge"), RECALL_TRACK = EncodedLogistics.id("terminal/recall_track"),
            RECALL_FILL = EncodedLogistics.id("terminal/recall_fill"), RECALL_SPINNER = EncodedLogistics.id("terminal/recall_spinner");

    private void coldMarks(GuiGraphicsExtractor graphics, ItemKey key, int cx, int cy) {
        if (menu.cold(key) != null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TAPE_BADGE, cx, cy + 11, 7, 5);
        }
        int recall = menu.recall(key);
        if (recall >= 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, RECALL_TRACK, cx, cy + 14, 16, 2);
            int fill = Math.round(16 * Math.min(100, recall) / 100.0F);
            if (fill > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, RECALL_FILL, 16, 1, 0, 0, cx, cy + 14, fill, 1);
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, RECALL_SPINNER, cx + 8, cy, 8, 8);
        }
    }

    // The tooltip's tape lines: on tape, and how long a recall would take (or how far along the player's is).
    private void coldLines(ItemKey key, List<Component> lines) {
        TerminalItemsPayload.Entry cold = menu.cold(key);
        int recall = menu.recall(key);
        if (cold == null && recall < 0) {
            return;
        }
        if (cold != null) {
            lines.add(Component.translatable("tooltip.encodedlogistics.tape.on_tape").withColor(CraftPlanScreen.TAPE_BLUE)
                    .append(Component.literal(String.format(Locale.ROOT, " (%,d)", cold.cold())).withColor(TEXT_MUTED)));
        }
        if (recall >= 0) {
            lines.add(Component.translatable("tooltip.encodedlogistics.tape.recalling", recall).withColor(ACCENT));
        } else if (cold.hotFull()) {
            lines.add(Component.translatable("tooltip.encodedlogistics.tape.hot_full").withColor(ERROR));
        } else if (cold.eta() < 0) {
            lines.add(Component.translatable("tooltip.encodedlogistics.tape.no_drive").withColor(ERROR));
        } else {
            lines.add(Component.translatable("tooltip.encodedlogistics.tape.recall_eta", CraftPlanScreen.seconds(cold.eta(), true)).withColor(TEXT_MUTED));
        }
    }

    private static int buttonState(String id) {
        return switch (id) {
            case "sort_mode" -> TerminalSettings.sortMode().ordinal();
            case "craftables" -> TerminalSettings.craftablesAlways() ? 0 : 1;
            case "height" -> TerminalSettings.height().ordinal();
            case "search_mode" -> syncedField() != null ? 1 : 0;
            default -> TerminalSettings.descending() ? 1 : 0;
        };
    }

    // What the grid says while the terminal can't reach its network.
    protected Component offlineMessage() {
        return Component.translatable("gui.encodedlogistics.terminal.offline");
    }

    // The section shown between the grid and the inventory, if any (the Schematic Encoder switches by mode).
    protected @Nullable Identifier sectionTexture() {
        return layout.section;
    }

    // Draws over a terminal's section (the Fabrication Terminal's clear button); top: the section's top on screen.
    protected void extractSection(GuiGraphicsExtractor graphics, int left, int top, int mouseX, int mouseY) {}

    protected TerminalLayout layout() {
        return layout;
    }

    protected int rows() {
        return rows;
    }

    private int cellUnder(double mouseX, double mouseY) {
        double gx = mouseX - leftPos - layout.gridLeft, gy = mouseY - topPos - layout.topHeight - layout.gridTopInRow;
        return (int) gy / layout.cell * layout.columns + (int) gx / layout.cell;
    }

    // The thumb runs the scroll track's inside, like the controller's.
    private int thumbY() {
        int travel = rows * layout.rowHeight - layout.thumbHeight;
        int max = maxScroll();
        return layout.scrollTop + (max == 0 ? 0 : Math.round((float) Math.min(scrollRow, max) * travel / max));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // The title stops short of the search field.
        int room = layout.titleMaxRight - titleLabelX;
        Component shown = title;
        if (font.width(title) > room) {
            shown = Component.literal(font.plainSubstrByWidth(title.getString(), room - font.width("...")) + "...");
        }
        graphics.text(font, shown, titleLabelX, titleLabelY, TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_MUTED, false);
        if (!menu.isOnline()) {
            Component offline = offlineMessage();
            int gridWidth = layout.columns * layout.cell;
            graphics.text(font, offline, layout.gridLeft + (gridWidth - font.width(offline)) / 2,
                    layout.topHeight + (rows * layout.rowHeight - 8) / 2, ERROR, false);
            return;
        }
        // Counts at half size, bottom right, shadowed.
        List<Map.Entry<ItemKey, Long>> entries = view();
        for (int cell = 0; cell < rows * layout.columns; cell++) {
            int index = scrollRow * layout.columns + cell;
            if (index >= entries.size()) {
                break;
            }
            long count = entries.get(index).getValue();
            if (count == 1 || count < 0) {
                continue;
            }
            String text = count == 0 ? Component.translatable("gui.encodedlogistics.terminal.craft").getString() : abbreviate(count);
            int cx = layout.gridLeft + (cell % layout.columns) * layout.cell;
            int cy = layout.topHeight + layout.gridTopInRow + (cell / layout.columns) * layout.cell;
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx + 16 - font.width(text) * 0.5F, cy + 16 - 4.5F);
            graphics.pose().scale(0.5F, 0.5F);
            graphics.text(font, text, 0, 0, count == 0 ? ACCENT : TEXT, true);
            graphics.pose().popMatrix();
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int index = hoveredIndex(mouseX, mouseY);
        if (index >= 0 && menu.getCarried().isEmpty()) {
            Map.Entry<ItemKey, Long> entry = view().get(index);
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(entry.getKey().stack()));
            if (entry.getValue() > 0) {
                lines.add(Component.literal(String.format(Locale.ROOT, "%,d", entry.getValue())).withColor(TEXT_MUTED));
            }
            coldLines(entry.getKey(), lines);
            if (menu.craftables().contains(entry.getKey())) {
                lines.add(Component.translatable("gui.encodedlogistics.terminal.craft_hint").withColor(ACCENT));
            }
            graphics.setTooltipForNextFrame(font, lines, Optional.empty(), entry.getKey().stack(), mouseX, mouseY);
            return;
        }
        for (int button = 0; button < layout.buttonIcons.size(); button++) {
            int bx = leftPos + layout.toolbarLeft, by = topPos + layout.toolbarTop + button * layout.toolbarSpacing;
            if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by && mouseY < by + 18) {
                String id = layout.buttonIds.get(button);
                if (id.equals("height")) {
                    graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.encodedlogistics.terminal.height"),
                            Component.translatable("gui.encodedlogistics.terminal.height." + TerminalSettings.height().name().toLowerCase(Locale.ROOT))
                                    .withColor(TEXT_MUTED)), mouseX, mouseY);
                    continue;
                }
                if (id.equals("search_mode")) {
                    String mode = syncedField() != null ? "gui.encodedlogistics.terminal.search_mode.jei" : "gui.encodedlogistics.terminal.search_mode.standard";
                    List<Component> lines = new ArrayList<>(List.of(Component.translatable("gui.encodedlogistics.terminal.search_mode"),
                            Component.translatable(mode).withColor(TEXT_MUTED)));
                    if (ExternalSearch.field() == null) {
                        lines.add(Component.translatable("gui.encodedlogistics.terminal.search_mode.no_jei").withColor(ERROR));
                    }
                    graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                    continue;
                }
                if (id.equals("craftables")) {
                    graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.encodedlogistics.terminal.craftable"),
                            Component.translatable(TerminalSettings.craftablesAlways() ? "gui.encodedlogistics.terminal.craftable.always"
                                    : "gui.encodedlogistics.terminal.craftable.search").withColor(TEXT_MUTED)), mouseX, mouseY);
                    continue;
                }
                String key = id.equals("sort_mode") ? "gui.encodedlogistics.terminal.sort." + TerminalSettings.sortMode().name().toLowerCase(Locale.ROOT)
                        : TerminalSettings.descending() ? "gui.encodedlogistics.terminal.dir.desc" : "gui.encodedlogistics.terminal.dir.asc";
                graphics.setTooltipForNextFrame(Component.translatable(key), mouseX, mouseY);
            }
        }
    }

    // 950, 1.2K, 34M, 5.1B.
    static String abbreviate(long count) {
        if (count < 1000) {
            return Long.toString(count);
        }
        String[] units = { "K", "M", "B", "T" };
        double value = count;
        int unit = -1;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        return (value < 10 ? String.format(Locale.ROOT, "%.1f", Math.floor(value * 10) / 10).replace(".0", "") : Long.toString((long) value))
                + units[unit];
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (int button = 0; button < layout.buttonIcons.size(); button++) {
            int bx = leftPos + layout.toolbarLeft, by = topPos + layout.toolbarTop + button * layout.toolbarSpacing;
            if (mx >= bx && mx < bx + 18 && my >= by && my < by + 18) {
                switch (layout.buttonIds.get(button)) {
                    case "sort_mode" -> {
                        TerminalSettings.SortMode[] modes = TerminalSettings.SortMode.values();
                        TerminalSettings.sortMode(modes[(TerminalSettings.sortMode().ordinal() + 1) % modes.length]);
                    }
                    case "craftables" -> TerminalSettings.craftablesAlways(!TerminalSettings.craftablesAlways());
                    case "height" -> cycleHeight();
                    case "search_mode" -> toggleSearchSync();
                    default -> TerminalSettings.descending(!TerminalSettings.descending());
                }
                viewVersion = -1;
                return true;
            }
        }
        // Double-clicking an inventory stack: the first click picked it up; this one puts it, and every stack like it in
        // the inventory, into the network (instead of vanilla gathering them onto the cursor).
        if (doubleClick && event.button() == InputConstants.MOUSE_BUTTON_LEFT && !event.hasShiftDown() && menu.isOnline()
                && !menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index < AccessTerminalMenu.INVENTORY_SLOTS
                && (!hoveredSlot.hasItem() || ItemStack.isSameItemSameComponents(hoveredSlot.getItem(), menu.getCarried()))) {
            ClientPacketDistributor.sendToServer(new TerminalClickPayload(menu.containerId, Optional.empty(),
                    AccessTerminalMenu.INSERT_ALL_LIKE_CARRIED));
            swallowRelease = true;
            return true;
        }
        int sx = leftPos + layout.scrollLeft, sy = topPos + layout.scrollTop;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && maxScroll() > 0 && mx >= sx && mx < sx + layout.thumbWidth && my >= sy && my < sy + rows * layout.rowHeight) {
            draggingThumb = true;
            scrollTo(my);
            return true;
        }
        int index = hoveredIndex(mx, my);
        // With an item held, a gap between the grid's squares puts it in too.
        if (index == -1 && menu.isOnline() && !menu.getCarried().isEmpty() && inInsertArea(mx, my)
                && (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
            int action = event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? AccessTerminalMenu.INSERT_ONE : AccessTerminalMenu.INSERT_CARRIED;
            ClientPacketDistributor.sendToServer(new TerminalClickPayload(menu.containerId, Optional.empty(), action));
            return true;
        }
        if (index != -1 && menu.isOnline()) {
            boolean carrying = !menu.getCarried().isEmpty();
            ItemKey key = index >= 0 ? view().get(index).getKey() : null;
            // Crafting: middle-click or Ctrl-click a craftable, or click one the network has none of.
            if (key != null && !carrying && menu.craftables().contains(key)
                    && (event.button() == InputConstants.MOUSE_BUTTON_MIDDLE || event.hasControlDown() || view().get(index).getValue() == 0)) {
                CraftingClient.openAmount(this, menu, key);
                return true;
            }
            // Taking and putting in are left and right clicks only; any other button does nothing here.
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
                return true;
            }
            int action;
            if (event.hasShiftDown() && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                if (key == null) {
                    return true;
                }
                action = AccessTerminalMenu.TAKE_ONE;
            } else if (carrying) {
                action = event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? AccessTerminalMenu.INSERT_ONE : AccessTerminalMenu.INSERT_CARRIED;
            } else if (key == null) {
                return true;
            } else if (event.hasShiftDown()) {
                action = AccessTerminalMenu.TAKE_TO_INVENTORY;
            } else {
                action = event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? AccessTerminalMenu.TAKE_HALF : AccessTerminalMenu.TAKE_STACK;
            }
            ClientPacketDistributor.sendToServer(new TerminalClickPayload(menu.containerId, Optional.ofNullable(key), action));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingThumb) {
            scrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingThumb = false;
        if (swallowRelease) {
            // The stack is still on the cursor until the server answers; vanilla would put it back in the slot.
            swallowRelease = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void scrollTo(double mouseY) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        float position = (float) (mouseY - topPos - layout.scrollTop - layout.thumbHeight / 2.0) / (rows * layout.rowHeight - layout.thumbHeight);
        scrollRow = Mth.clamp(Math.round(position * max), 0, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Shift+wheel over the grid, as in AE2: up puts one of the carried item in, down takes one of the item under the
        // mouse onto the cursor.
        int index = hoveredIndex(mouseX, mouseY);
        if (index != -1 && scrollY != 0 && menu.isOnline() && minecraft.hasShiftDown()) {
            ItemKey key = index >= 0 ? view().get(index).getKey() : null;
            int action = scrollY > 0 ? AccessTerminalMenu.INSERT_ONE : AccessTerminalMenu.TAKE_ONE;
            if (action == AccessTerminalMenu.INSERT_ONE ? !menu.getCarried().isEmpty() : key != null) {
                for (int i = 0; i < Math.max(1, (int) Math.abs(scrollY)); i++) {
                    ClientPacketDistributor.sendToServer(new TerminalClickPayload(menu.containerId, Optional.ofNullable(key), action));
                }
            }
            return true;
        }
        double y = mouseY - topPos - layout.topHeight;
        if (y >= 0 && y < rows * layout.rowHeight && mouseX >= leftPos && mouseX < leftPos + layout.width) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // While the search field has focus it gets every key but Escape (so typing "e" doesn't close the screen).
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search != null && search.isFocused() && !event.isEscape()) {
            return search.keyPressed(event) || search.canConsumeInput() || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }
}
