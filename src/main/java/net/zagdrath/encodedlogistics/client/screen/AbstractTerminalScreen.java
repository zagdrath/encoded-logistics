/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
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
import net.zagdrath.encodedlogistics.client.CraftingClient;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.net.TerminalClickPayload;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// The modular terminal screen, built from a TerminalLayout: a title bar with the search field, a grid of the network's
// items (as many rows as fit the window, between the layout's min and max), a scrollbar, the player's inventory, and
// a toolbar on a tab at the left (sort mode: name / amount / mod; sort direction; craftables shown always or only when
// searching; grid height: small / medium / tall / fill the window, which re-lays the open screen out).
// Counts are drawn at half size and abbreviated (1.2K, 34M, 5.1B); what the network can craft but doesn't have shows with
// "Craft" instead. Clicks work as in AE2: left click takes a stack, right click half of one, shift-click moves a stack
// into the inventory, shift-right-click takes one onto the cursor; clicking with an item held puts it in (right click:
// just one); Shift+wheel puts one in (up) or takes one (down). Middle-click or Ctrl-click on a craftable item (or any click on one
// the network has none of) asks how many to craft. Search matches names; "@" searches mod ids. Each terminal adds
// little more than its layout and title (AccessTerminalScreen).
public abstract class AbstractTerminalScreen<M extends AccessTerminalMenu> extends AbstractContainerScreen<M> {
    // palette.json
    private static final int TEXT = 0xFFF0F0F0, TEXT_MUTED = 0xFFB4B4B4, ERROR = 0xFFFF6B6B, ACCENT = 0xFF00D992;

    private enum SortMode {
        NAME, COUNT, MOD
    }

    // Sort settings last the session, across terminals.
    private static SortMode sortMode = SortMode.NAME;
    private static boolean descending;
    private static boolean craftablesAlways = true;
    private static String lastSearch = "";

    private final TerminalLayout layout;
    // The grid's rows: the menu's when it opened, until the height button changes them.
    private int rows;
    private @Nullable EditBox search;
    private List<Map.Entry<ItemKey, Long>> view = List.of();
    private int viewVersion = -1;
    private String viewSearch = "";
    private int scrollRow;
    private boolean draggingThumb;

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

    // The height button: the next height setting, and the open screen laid out again at its rows. The menu's slots (all
    // below the grid) move with it on the client.
    private void cycleHeight() {
        TerminalLayout.Height[] heights = TerminalLayout.Height.values();
        TerminalLayout.height = heights[(TerminalLayout.height.ordinal() + 1) % heights.length];
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
        if (search.getValue().isEmpty()) {
            search.setValue(lastSearch);
        }
        search.setResponder(text -> {
            lastSearch = text;
            scrollRow = 0;
        });
        addRenderableWidget(search);
    }

    // --- The list ---

    private List<Map.Entry<ItemKey, Long>> view() {
        String text = search != null ? search.getValue() : "";
        if (viewVersion != menu.version() || !text.equals(viewSearch)) {
            viewVersion = menu.version();
            viewSearch = text;
            view = filtered(text);
        }
        return view;
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
        if (craftablesAlways || !needle.isEmpty()) {
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
        Comparator<Map.Entry<ItemKey, Long>> order = switch (sortMode) {
            case NAME -> byName;
            case COUNT -> Comparator.<Map.Entry<ItemKey, Long>>comparingLong(Map.Entry::getValue).thenComparing(byName);
            case MOD -> Comparator.<Map.Entry<ItemKey, Long>, String>comparing(
                    entry -> BuiltInRegistries.ITEM.getKey(entry.getKey().stack().getItem()).getNamespace()).thenComparing(byName);
        };
        entries.sort(descending ? order.reversed() : order);
        return entries;
    }

    private int maxScroll() {
        return Math.max(0, (view().size() + layout.columns - 1) / layout.columns - rows);
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
        return index < view().size() ? index : -2;
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
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
            if (index < entries.size()) {
                graphics.item(entries.get(index).getKey().stack(), cx, cy);
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

    private static int buttonState(String id) {
        return switch (id) {
            case "sort_mode" -> sortMode.ordinal();
            case "craftables" -> craftablesAlways ? 0 : 1;
            case "height" -> TerminalLayout.height.ordinal();
            default -> descending ? 1 : 0;
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
            if (count == 1) {
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
                            Component.translatable("gui.encodedlogistics.terminal.height." + TerminalLayout.height.name().toLowerCase(Locale.ROOT))
                                    .withColor(TEXT_MUTED)), mouseX, mouseY);
                    continue;
                }
                if (id.equals("craftables")) {
                    graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.encodedlogistics.terminal.craftable"),
                            Component.translatable(craftablesAlways ? "gui.encodedlogistics.terminal.craftable.always"
                                    : "gui.encodedlogistics.terminal.craftable.search").withColor(TEXT_MUTED)), mouseX, mouseY);
                    continue;
                }
                String key = id.equals("sort_mode") ? "gui.encodedlogistics.terminal.sort." + sortMode.name().toLowerCase(Locale.ROOT)
                        : descending ? "gui.encodedlogistics.terminal.dir.desc" : "gui.encodedlogistics.terminal.dir.asc";
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
                    case "sort_mode" -> sortMode = SortMode.values()[(sortMode.ordinal() + 1) % SortMode.values().length];
                    case "craftables" -> craftablesAlways = !craftablesAlways;
                    case "height" -> cycleHeight();
                    default -> descending = !descending;
                }
                viewVersion = -1;
                return true;
            }
        }
        int sx = leftPos + layout.scrollLeft, sy = topPos + layout.scrollTop;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && maxScroll() > 0 && mx >= sx && mx < sx + layout.thumbWidth && my >= sy && my < sy + rows * layout.rowHeight) {
            draggingThumb = true;
            scrollTo(my);
            return true;
        }
        int index = hoveredIndex(mx, my);
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
