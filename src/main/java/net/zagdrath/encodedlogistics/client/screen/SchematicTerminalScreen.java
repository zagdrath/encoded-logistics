/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.ResourceRender;
import net.zagdrath.encodedlogistics.client.SneakKey;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.menu.SchematicTerminalMenu;
import net.zagdrath.encodedlogistics.net.SchematicTerminalActionPayload;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;
import net.zagdrath.encodedlogistics.schematic.SchematicData;
import net.zagdrath.encodedlogistics.schematic.SchematicTerminalService;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// The Schematic Terminal (screens/schematic_terminal.json, on the terminal kit's frame: TerminalLayout), two tabs above
// the frame:
//  - Schematics: every schematic holder on the network, a header row each (its icon, name, location when it fits whole,
//    and badges: also made elsewhere, a recipe conflict, a Gateway whose machine is missing or rejects the inputs) and
//    its slots, nine a row. Click a header to select its holder (shift-clicking a schematic in the inventory - the Sneak
//    key, as the rack's screen - puts it into the selected holder's free slots); right-click it for Locate and Rename.
//    Click a slot to take its schematic, or with one carried to put it in. A Midrange System's are its diskettes'
//    recipes, read-only. The search matches outputs, inputs, holder names and fed machines; the toolbar sorts (name,
//    slots used, type), orders, filters (all or with free slots; Sneak-click cycles the holder type) and sets the height.
//  - Jobs: the network's crafting jobs, a row each (number, output, status; a progress bar, its scheduler and how long
//    it's run). Click one for its steps (a tree: state, item, done of total; a stalled step's reason, with Locate);
//    right-click it to cancel (confirmed), hold or release it. The toolbar filters by scheduler and requester, shows
//    active jobs or also those just ended, and sets the height.
// The summary strip under the rows counts outputs and slots used, or the scheduler's threads and job memory. Text is
// drawn only where it fits whole (nothing over the scrollbar column, x 172 on).
public class SchematicTerminalScreen extends AbstractContainerScreen<SchematicTerminalMenu> {
    public static final String LAYOUT = "schematic_terminal";
    // palette.json, and the Jobs tab's active blue.
    private static final int TEXT = 0xFFF0F0F0, MUTED = 0xFFB4B4B4, DISABLED = 0xFF7A7A7A, ACCENT = 0xFF00D992, WARNING = 0xFFE8C24A,
            ERROR = 0xFFFF6B6B, BLUE = 0xFF50C2EC, SELECTION = 0x5000A06B, HOVER = 0x2800D992, READ_ONLY = 0x8C2E2E2E;
    private static final int RIGHT = 169, CONTENT_RIGHT = 171, SCROLL_COLUMN = 172;
    private static final int SCHEMATICS = 0, JOBS = 1;
    private static final Identifier BADGE_PARALLEL = sprite("schematic/badge_parallel"), BADGE_CONFLICT = sprite("schematic/badge_conflict"),
            BADGE_GATEWAY = sprite("schematic/badge_gateway"), LOCATE = sprite("schematic/locate"), RENAME = sprite("schematic/rename"),
            PROGRESS_BG = sprite("jobs/progress_bg"), PROGRESS_FILL = sprite("jobs/progress_fill"), PROGRESS_STALLED = sprite("jobs/progress_stalled"),
            PROGRESS_FAILED = sprite("jobs/progress_failed"), TREE_BRANCH = sprite("jobs/tree_branch"), TREE_LAST = sprite("jobs/tree_last"),
            TREE_PIPE = sprite("jobs/tree_pipe");
    private static final Identifier[] STATES = { sprite("jobs/state_done"), sprite("jobs/state_active"), sprite("jobs/state_waiting"),
            sprite("jobs/state_stalled"), sprite("jobs/state_missing") };
    private static final String[] STATUS = { "queued", "active", "waiting", "stalled", "held", "done", "failed", "cancelled" };
    private static final int[] STATUS_COLOR = { MUTED, BLUE, MUTED, WARNING, MUTED, ACCENT, ERROR, ERROR };

    // The session's settings: the tab, the Schematics tab's sort (name, slots used, type), order and filters (free slots
    // only; a holder type, -1 all), the Jobs tab's filters (a scheduler by index, -1 all; the player's own; active only).
    private static int tab = SCHEMATICS, holderSort, kindFilter = -1, schedulerFilter = -1;
    private static boolean descending, freeOnly, mineOnly, activeOnly = true;
    private static String lastSearch = "";

    private final TerminalLayout layout;
    private final Identifier headerRow, listRow, tabActive, tabInactive;
    private final int tabWidth, tabHeight, tabLeft, tabStep, tabTop;
    private int rows;
    private @Nullable EditBox search, renameBox;
    private @Nullable String renaming;
    private int scroll;
    private boolean draggingThumb;
    private @Nullable String selected;
    private final Set<UUID> expanded = new HashSet<>();
    private @Nullable Popup popup;

    private record Line(int type, int height, SchematicData.@Nullable Holder holder, int row, SchematicData.@Nullable Job job, int step, boolean last) {}

    private static final int HEADER = 0, SLOTS = 1, JOB = 2, STEP = 3, REASON = 4;

    private record PopupItem(Component label, @Nullable Identifier icon, Runnable run) {}

    private record Popup(int x, int y, List<PopupItem> items) {}

    public SchematicTerminalScreen(SchematicTerminalMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, TerminalLayout.load(LAYOUT));
    }

    private SchematicTerminalScreen(SchematicTerminalMenu menu, Inventory inventory, Component title, TerminalLayout layout) {
        super(menu, inventory, title, layout.width, layout.height(menu.rows()));
        this.layout = layout;
        this.rows = menu.rows();
        JsonObject pieces = object(layout.json, "pieces"), tabs = object(layout.json, "tabs");
        headerRow = EncodedLogistics.id("textures/" + string(object(pieces, "header_row"), "texture", "gui/terminal/row_header.png"));
        listRow = EncodedLogistics.id("textures/" + string(object(pieces, "list_row"), "texture", "gui/terminal/row_list.png"));
        tabActive = sprite(string(tabs, "sprite", "schematic/tab_active"));
        tabInactive = sprite(string(tabs, "sprite_inactive", "schematic/tab_inactive"));
        tabWidth = tabs.has("size") ? tabs.getAsJsonArray("size").get(0).getAsInt() : 64;
        tabHeight = tabs.has("size") ? tabs.getAsJsonArray("size").get(1).getAsInt() : 13;
        tabLeft = tabs.has("left") ? tabs.get("left").getAsInt() : 8;
        tabStep = tabs.has("step") ? tabs.get("step").getAsInt() : 65;
        tabTop = tabs.has("top") ? tabs.get("top").getAsInt() : -12;
        titleLabelX = layout.titleLeft;
        titleLabelY = layout.titleTop;
        inventoryLabelX = layout.inventoryLeft;
        inventoryLabelY = labelY();
    }

    private static Identifier sprite(String path) {
        return EncodedLogistics.id(path);
    }

    private static JsonObject object(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private int labelY() {
        return layout.topHeight + rows * layout.rowHeight + layout.sectionHeight + layout.inventoryTopInBottom;
    }

    private int screenHeight() {
        return layout.height(rows);
    }

    @Override
    public int getImageHeight() {
        return screenHeight();
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
        search.setHint(Component.translatable("gui.encodedlogistics.terminal.search").withColor(DISABLED));
        if (search.getValue().isEmpty()) {
            search.setValue(lastSearch);
        }
        search.setResponder(text -> {
            lastSearch = text;
            scroll = 0;
        });
        addRenderableWidget(search);
        renaming = null;
        renameBox = null;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        boolean onTab = mouseX >= left + layout.toolbarLeft - 3 && mouseX < left && mouseY >= top + layout.toolbarTop - 3
                && mouseY < top + layout.toolbarTop + 1 + 4 * layout.toolbarSpacing;
        boolean onTabs = mouseY >= top + tabTop && mouseY < top && mouseX >= left + tabLeft && mouseX < left + tabLeft + tabStep + tabWidth;
        return !onTab && !onTabs && (mouseX < left || mouseY < top || mouseX >= left + imageWidth || mouseY >= top + screenHeight());
    }

    // --- What it shows ---

    private SchematicData.Snapshot data() {
        return menu.snapshot();
    }

    private String query() {
        return search != null ? search.getValue().trim().toLowerCase(Locale.ROOT) : "";
    }

    private List<SchematicData.Holder> holders() {
        String query = query();
        List<SchematicData.Holder> shown = new ArrayList<>();
        for (SchematicData.Holder holder : data().holders()) {
            if (freeOnly && (holder.readOnly() || holder.used() >= holder.slots().size())) {
                continue;
            }
            if (kindFilter >= 0 && holder.kind() != kindFilter) {
                continue;
            }
            if (!query.isEmpty() && !matches(holder, query)) {
                continue;
            }
            shown.add(holder);
        }
        Comparator<SchematicData.Holder> byName = Comparator.comparing(SchematicData.Holder::name, String.CASE_INSENSITIVE_ORDER);
        Comparator<SchematicData.Holder> order = switch (holderSort) {
            case 1 -> Comparator.comparingInt(SchematicData.Holder::used).thenComparing(byName);
            case 2 -> Comparator.comparingInt(SchematicData.Holder::kind).thenComparing(byName);
            default -> byName;
        };
        // Unsorted by name keeps the network's order (the planner's); a sort reorders.
        if (holderSort != 0 || descending) {
            shown.sort(descending ? order.reversed() : order);
        }
        return shown;
    }

    // A holder matches by its name (a Gateway's machine too), or a schematic's outputs or inputs.
    private static boolean matches(SchematicData.Holder holder, String query) {
        if (holder.name().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (ItemStack stack : holder.slots()) {
            Schematic schematic = SchematicItem.schematic(stack);
            if (schematic == null) {
                continue;
            }
            for (StorageKey key : schematic.outputTotals().keySet()) {
                if (key.displayName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
            }
            for (StorageKey key : schematic.inputTotals().keySet()) {
                if (key.displayName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
            }
        }
        return false;
    }

    private @Nullable String schedulerName() {
        List<SchematicData.Scheduler> schedulers = data().schedulers();
        return schedulerFilter >= 0 && schedulerFilter < schedulers.size() ? schedulers.get(schedulerFilter).name() : null;
    }

    private List<SchematicData.Job> jobs() {
        String query = query();
        String scheduler = schedulerName();
        List<SchematicData.Job> shown = new ArrayList<>();
        for (SchematicData.Job job : data().jobs()) {
            if (activeOnly && job.ended() || mineOnly && !job.mine() || scheduler != null && !job.scheduler().equals(scheduler)) {
                continue;
            }
            if (!query.isEmpty() && !job.target().displayName().getString().toLowerCase(Locale.ROOT).contains(query)
                    && !job.scheduler().toLowerCase(Locale.ROOT).contains(query) && !job.requester().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            shown.add(job);
        }
        return shown;
    }

    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        if (tab == SCHEMATICS) {
            for (SchematicData.Holder holder : holders()) {
                lines.add(new Line(HEADER, 18, holder, 0, null, -1, false));
                int slotRows = Math.max(1, (holder.slots().size() + layout.columns - 1) / layout.columns);
                for (int row = 0; row < slotRows; row++) {
                    lines.add(new Line(SLOTS, 18, holder, row, null, -1, false));
                }
            }
            return lines;
        }
        for (SchematicData.Job job : jobs()) {
            lines.add(new Line(JOB, 18, null, 0, job, -1, false));
            if (!expanded.contains(job.id())) {
                continue;
            }
            for (int step = 0; step < job.steps().size(); step++) {
                boolean last = step == job.steps().size() - 1;
                lines.add(new Line(STEP, 9, null, 0, job, step, last));
                if (!job.steps().get(step).reason().isEmpty()) {
                    lines.add(new Line(REASON, 9, null, 0, job, step, last));
                }
            }
        }
        return lines;
    }

    // Scrolling goes by rows on the Schematics tab, by half rows on the Jobs tab (the step tree's lines).
    private int unit() {
        return tab == SCHEMATICS ? layout.rowHeight : layout.rowHeight / 2;
    }

    private int maxScroll(List<Line> lines) {
        int content = lines.stream().mapToInt(Line::height).sum();
        return Math.max(0, (content - rows * layout.rowHeight + unit() - 1) / unit());
    }

    private record Placed(Line line, int y) {}

    // The lines on screen, each with its top (screen coordinates).
    private List<Placed> placed(List<Line> lines) {
        List<Placed> placed = new ArrayList<>();
        int y = topPos + layout.topHeight - scroll * unit();
        int top = topPos + layout.topHeight, bottom = top + rows * layout.rowHeight;
        for (Line line : lines) {
            if (y + line.height() > top && y < bottom) {
                placed.add(new Placed(line, y));
            }
            y += line.height();
        }
        return placed;
    }

    private @Nullable Placed lineAt(List<Line> lines, double mouseX, double mouseY) {
        int top = topPos + layout.topHeight;
        if (mouseX < leftPos + 8 || mouseX >= leftPos + SCROLL_COLUMN || mouseY < top || mouseY >= top + rows * layout.rowHeight) {
            return null;
        }
        for (Placed placed : placed(lines)) {
            if (mouseY >= placed.y() && mouseY < placed.y() + placed.line().height()) {
                return placed;
            }
        }
        return null;
    }

    // The slot of a holder's slot row under the mouse, or -1.
    private int slotAt(Placed placed, double mouseX, double mouseY) {
        double x = mouseX - leftPos - layout.gridLeft, y = mouseY - placed.y() - layout.gridTopInRow;
        if (x < 0 || y < 0 || y >= 16 || x >= layout.columns * layout.cell) {
            return -1;
        }
        int column = (int) x / layout.cell;
        if (x - column * layout.cell >= 16) {
            return -1;
        }
        int slot = placed.line().row() * layout.columns + column;
        return slot < placed.line().holder().slots().size() ? slot : -1;
    }

    private SchematicData.@Nullable Holder selectedHolder() {
        if (selected == null) {
            return null;
        }
        for (SchematicData.Holder holder : data().holders()) {
            if (holder.key().equals(selected)) {
                return holder;
            }
        }
        return null;
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        // The tabs above the frame (the active one overlaps it by a pixel).
        for (int i = 0; i < 2; i++) {
            boolean active = i == tab;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, active ? tabActive : tabInactive, x + tabLeft + i * tabStep, y + tabTop + (active ? 0 : 1),
                    tabWidth, active ? tabHeight : tabHeight - 1);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, layout.top, x, y, 0.0F, 0.0F, layout.width, layout.topHeight, 256, 32);
        List<Line> lines = lines();
        scroll = Math.min(scroll, maxScroll(lines));
        List<Placed> placed = placed(lines);
        int rowsTop = y + layout.topHeight;
        if (tab == SCHEMATICS) {
            // Each row its piece: a header bar, or slots; past the content, plain list rows (no slots where there are none).
            int drawn = 0;
            for (Placed line : placed) {
                Identifier piece = line.line().type() == HEADER ? headerRow : layout.row;
                int texWidth = piece == headerRow ? layout.width : 256, texHeight = piece == headerRow ? layout.rowHeight : 32;
                graphics.blit(RenderPipelines.GUI_TEXTURED, piece, x, line.y(), 0.0F, 0.0F, layout.width, layout.rowHeight, texWidth, texHeight);
                if (line.line().type() == SLOTS) {
                    // A holder's last row short of a full one: its unused cells plain, not slots.
                    int used = Math.max(0, line.line().holder().slots().size() - line.line().row() * layout.columns);
                    if (used < layout.columns) {
                        int from = layout.gridLeft - 1 + used * layout.cell, to = layout.gridLeft - 1 + layout.columns * layout.cell;
                        graphics.blit(RenderPipelines.GUI_TEXTURED, listRow, x + from, line.y(), from, 0.0F, to - from, layout.rowHeight, layout.width,
                                layout.rowHeight);
                    }
                }
                drawn++;
            }
            for (int row = drawn; row < rows; row++) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, listRow, x, rowsTop + row * layout.rowHeight, 0.0F, 0.0F, layout.width, layout.rowHeight,
                        layout.width, layout.rowHeight);
            }
        } else {
            for (int row = 0; row < rows; row++) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, listRow, x, rowsTop + row * layout.rowHeight, 0.0F, 0.0F, layout.width, layout.rowHeight,
                        layout.width, layout.rowHeight);
            }
        }
        int sectionTop = rowsTop + rows * layout.rowHeight;
        if (layout.section != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, layout.section, x, sectionTop, 0.0F, 0.0F, layout.width, layout.sectionHeight, 256, 128);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, layout.bottom, x, sectionTop + layout.sectionHeight, 0.0F, 0.0F, layout.width, layout.bottomHeight,
                256, 128);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, search != null && search.isFocused() ? layout.searchSpriteFocused : layout.searchSprite,
                x + layout.searchLeft, y + layout.searchTop, layout.searchWidth, layout.searchHeight);
        toolbar(graphics, mouseX, mouseY);

        if (data().online()) {
            graphics.enableScissor(x + 8, rowsTop, x + SCROLL_COLUMN, sectionTop);
            for (Placed line : placed) {
                switch (line.line().type()) {
                    case HEADER -> header(graphics, line, mouseX, mouseY);
                    case SLOTS -> slots(graphics, line, mouseX, mouseY);
                    case JOB -> job(graphics, line, mouseX, mouseY);
                    case STEP -> step(graphics, line);
                    default -> reason(graphics, line, mouseX, mouseY);
                }
            }
            graphics.disableScissor();
        }

        int max = maxScroll(lines);
        boolean overThumb = mouseX >= x + layout.scrollLeft && mouseX < x + layout.scrollLeft + layout.thumbWidth && mouseY >= y + thumbY(max)
                && mouseY < y + thumbY(max) + layout.thumbHeight;
        Identifier thumb = max == 0 ? layout.thumbDisabled : draggingThumb || overThumb ? layout.thumbHover : layout.thumb;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, thumb, x + layout.scrollLeft, y + thumbY(max), layout.thumbWidth, layout.thumbHeight);
        if (renameBox != null) {
            renameBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        if (popup != null) {
            drawPopup(graphics, mouseX, mouseY);
        }
    }

    private int thumbY(int max) {
        int travel = rows * layout.rowHeight - layout.thumbHeight;
        return layout.scrollTop + (max == 0 ? 0 : Math.round((float) Math.min(scroll, max) * travel / max));
    }

    // The toolbar's four buttons: the tab's.
    private void toolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int tx = leftPos + layout.toolbarLeft - 3, ty = topPos + layout.toolbarTop - 3, th = Math.min(layout.tabHeight, 4 * layout.toolbarSpacing + 4);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, layout.tab, layout.tabWidth, layout.tabHeight, 0, 0, tx, ty, layout.tabWidth, th - 3);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, layout.tab, layout.tabWidth, layout.tabHeight, 0, layout.tabHeight - 3, tx, ty + th - 3, layout.tabWidth,
                3);
        for (int button = 0; button < 4; button++) {
            int bx = leftPos + layout.toolbarLeft, by = topPos + layout.toolbarTop + button * layout.toolbarSpacing;
            boolean hover = mouseX >= bx && mouseX < bx + 18 && mouseY >= by && mouseY < by + 18;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? layout.buttonHover : layout.button, bx, by, 18, 18);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon(button), bx + 1, by + 1, 16, 16);
        }
    }

    private Identifier icon(int button) {
        if (button == 3) {
            return iconOf(3, TerminalSettings.height().ordinal(), "terminal/icon_height_medium");
        }
        if (tab == SCHEMATICS) {
            return switch (button) {
                case 0 -> iconOf(0, holderSort, "terminal/icon_sort_name");
                case 1 -> iconOf(1, descending ? 1 : 0, "terminal/icon_dir_asc");
                default -> iconOf(2, freeOnly ? 1 : 0, "terminal/icon_type_all");
            };
        }
        return switch (button) {
            case 0 -> sprite(schedulerFilter < 0 ? "terminal/icon_type_all" : "terminal/icon_sort_mod");
            case 1 -> sprite(mineOnly ? "terminal/icon_sort_name" : "terminal/icon_type_items");
            default -> sprite(activeOnly ? "terminal/icon_craftable_on" : "terminal/icon_craftable_off");
        };
    }

    private Identifier iconOf(int button, int state, String fallback) {
        if (button < layout.buttonIcons.size() && !layout.buttonIcons.get(button).isEmpty()) {
            List<Identifier> icons = layout.buttonIcons.get(button);
            return icons.get(Math.min(state, icons.size() - 1));
        }
        return sprite(fallback);
    }

    // A holder's header: icon, name, location (only when it fits whole before the badges), badges; lit when selected.
    private void header(GuiGraphicsExtractor graphics, Placed placed, int mouseX, int mouseY) {
        SchematicData.Holder holder = placed.line().holder();
        int x = leftPos, y = placed.y();
        if (holder.key().equals(selected)) {
            graphics.fill(x + 8, y, x + CONTENT_RIGHT + 1, y + 18, SELECTION);
        } else if (mouseX >= x + 8 && mouseX < x + SCROLL_COLUMN && mouseY >= y && mouseY < y + 18 && popup == null) {
            graphics.fill(x + 8, y, x + CONTENT_RIGHT + 1, y + 18, HOVER);
        }
        graphics.item(holder.icon(), x + 10, y + 1);
        int badgeX = x + CONTENT_RIGHT - 1;
        for (Identifier badge : badges(holder).reversed()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, badge, badgeX - 7, y + 5, 7, 7);
            badgeX -= 9;
        }
        if (holder.key().equals(renaming)) {
            return;
        }
        int nameRight = badgeX - 2;
        String name = fit(holder.name(), nameRight - (x + 28));
        graphics.text(font, name, x + 28, y + 5, holder.readOnly() ? DISABLED : TEXT, false);
        int locationX = x + 28 + font.width(name) + 5;
        if (name.equals(holder.name()) && locationX + font.width(holder.location()) <= nameRight) {
            graphics.text(font, holder.location(), locationX, y + 5, DISABLED, false);
        }
    }

    private static List<Identifier> badges(SchematicData.Holder holder) {
        List<Identifier> badges = new ArrayList<>();
        if ((holder.badges() & SchematicData.PARALLEL) != 0) {
            badges.add(BADGE_PARALLEL);
        }
        if ((holder.badges() & SchematicData.CONFLICT) != 0) {
            badges.add(BADGE_CONFLICT);
        }
        if ((holder.badges() & SchematicData.GATEWAY_PROBLEM) != 0) {
            badges.add(BADGE_GATEWAY);
        }
        return badges;
    }

    private void slots(GuiGraphicsExtractor graphics, Placed placed, int mouseX, int mouseY) {
        SchematicData.Holder holder = placed.line().holder();
        int hovered = popup == null ? slotAt(placed, mouseX, mouseY) : -1;
        for (int column = 0; column < layout.columns; column++) {
            int slot = placed.line().row() * layout.columns + column;
            if (slot >= holder.slots().size()) {
                break;
            }
            int sx = leftPos + layout.gridLeft + column * layout.cell, sy = placed.y() + layout.gridTopInRow;
            ItemStack stack = holder.slots().get(slot);
            if (!stack.isEmpty()) {
                graphics.item(stack, sx, sy);
            }
            if (holder.readOnly()) {
                // Read-only (a Midrange System's diskettes): faded.
                graphics.fill(sx, sy, sx + 16, sy + 16, READ_ONLY);
            }
            if (slot == hovered) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, layout.slotHighlight, sx, sy, 16, 16);
            }
        }
    }

    private int numberX() {
        return leftPos + 10 + font.width("0000") + 4;
    }

    // A job's row: number, output and quantity, status; its progress bar, scheduler and time.
    private void job(GuiGraphicsExtractor graphics, Placed placed, int mouseX, int mouseY) {
        SchematicData.Job job = placed.line().job();
        int x = leftPos, y = placed.y(), nx = numberX();
        if (mouseX >= x + 8 && mouseX < x + SCROLL_COLUMN && mouseY >= y && mouseY < y + 18 && popup == null) {
            graphics.fill(x + 8, y, x + CONTENT_RIGHT + 1, y + 18, HOVER);
        }
        graphics.text(font, String.format(Locale.ROOT, "%04d", job.number() % 10_000), x + 10, y + 1, DISABLED, false);
        Component status = Component.translatable("gui.encodedlogistics.jobs.status." + STATUS[Math.clamp(job.status(), 0, STATUS.length - 1)]);
        int statusX = x + RIGHT - font.width(status);
        graphics.text(font, status, statusX, y + 1, STATUS_COLOR[Math.clamp(job.status(), 0, STATUS_COLOR.length - 1)], false);
        String output = job.target().displayName().getString() + " x" + amount(job.target(), job.amount());
        graphics.text(font, fit(output, statusX - 3 - nx), nx, y + 1, TEXT, false);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS_BG, nx, y + 12, 40, 5);
        int fill = job.status() == SchematicData.DONE ? 40 : job.total() <= 0 ? 0 : Math.round(40.0F * Math.min(job.done(), job.total()) / job.total());
        if (fill > 0) {
            Identifier bar = job.status() == SchematicData.STALLED ? PROGRESS_STALLED
                    : job.status() == SchematicData.FAILED || job.status() == SchematicData.CANCELLED ? PROGRESS_FAILED : PROGRESS_FILL;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bar, 40, 5, 0, 0, nx, y + 12, fill, 5);
        }
        String meta = job.scheduler() + " " + time(job.elapsed());
        graphics.text(font, fit(meta, x + CONTENT_RIGHT - (nx + 44)), nx + 44, y + 10, DISABLED, false);
    }

    // A step of the tree: its branch, state, item and quantity, done of total.
    private void step(GuiGraphicsExtractor graphics, Placed placed) {
        SchematicData.Step step = placed.line().job().steps().get(placed.line().step());
        int y = placed.y(), nx = numberX();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, placed.line().last() ? TREE_LAST : TREE_BRANCH, nx, y - 1, 9, 11);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STATES[Math.clamp(step.state(), 0, STATES.length - 1)], nx + 9, y + 1, 7, 7);
        String count = step.done() + "/" + step.total();
        int countX = leftPos + RIGHT - font.width(count);
        graphics.text(font, count, countX, y + 1, MUTED, false);
        String name = step.item().getHoverName().getString() + " x" + step.amount();
        graphics.text(font, fit(name, countX - 3 - (nx + 18)), nx + 18, y + 1, step.state() == SchematicData.STEP_STALLED ? WARNING : TEXT, false);
    }

    // A stalled step's reason, and Locate for its holder.
    private void reason(GuiGraphicsExtractor graphics, Placed placed, int mouseX, int mouseY) {
        SchematicData.Step step = placed.line().job().steps().get(placed.line().step());
        int y = placed.y(), nx = numberX();
        if (!placed.line().last()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TREE_PIPE, nx, y - 1, 9, 11);
        }
        String reason = Component.translatable("gui.encodedlogistics.jobs.reason." + step.reason()).getString();
        graphics.text(font, fit(reason, leftPos + 159 - (nx + 18)), nx + 18, y + 1, WARNING, false);
        if (step.locatable()) {
            int lx = leftPos + 161;
            boolean hover = mouseX >= lx && mouseX < lx + 9 && mouseY >= y && mouseY < y + 9;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCATE, lx, y, 9, 9);
            if (hover) {
                graphics.fill(lx, y, lx + 9, y + 9, HOVER);
            }
        }
    }

    private void drawPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Popup shown = popup;
        int width = popupWidth(shown), height = shown.items().size() * 12 + 4;
        graphics.nextStratum();
        graphics.fill(shown.x() - 1, shown.y() - 1, shown.x() + width + 1, shown.y() + height + 1, 0xFF000000);
        graphics.fill(shown.x(), shown.y(), shown.x() + width, shown.y() + height, 0xFF2E2E2E);
        for (int i = 0; i < shown.items().size(); i++) {
            PopupItem item = shown.items().get(i);
            int iy = shown.y() + 2 + i * 12;
            if (mouseX >= shown.x() && mouseX < shown.x() + width && mouseY >= iy && mouseY < iy + 12) {
                graphics.fill(shown.x(), iy, shown.x() + width, iy + 12, HOVER);
            }
            int tx = shown.x() + 3;
            if (item.icon() != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, item.icon(), tx, iy + 1, 9, 9);
                tx += 12;
            }
            graphics.text(font, item.label(), tx, iy + 2, TEXT, false);
        }
    }

    private int popupWidth(Popup shown) {
        int width = 0;
        for (PopupItem item : shown.items()) {
            width = Math.max(width, font.width(item.label()) + (item.icon() != null ? 12 : 0) + 6);
        }
        return width;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Tabs' labels, centred.
        for (int i = 0; i < 2; i++) {
            Component label = Component.translatable(i == SCHEMATICS ? "gui.encodedlogistics.schematic.tab.schematics" : "gui.encodedlogistics.schematic.tab.jobs");
            int tx = tabLeft + i * tabStep;
            graphics.text(font, label, tx + (tabWidth - font.width(label)) / 2, tabTop + 3, i == tab ? TEXT : MUTED, false);
        }
        Component heading = tab == SCHEMATICS ? title
                : Component.translatable("gui.encodedlogistics.jobs.title", schedulerName() != null ? schedulerName()
                        : Component.translatable("gui.encodedlogistics.jobs.all").getString());
        graphics.text(font, fit(heading.getString(), layout.titleMaxRight - titleLabelX), titleLabelX, titleLabelY, TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        int summaryTop = layout.topHeight + rows * layout.rowHeight + 2;
        if (!data().online()) {
            Component offline = Component.translatable("gui.encodedlogistics.terminal.offline");
            int gridWidth = layout.columns * layout.cell;
            graphics.text(font, offline, layout.gridLeft + (gridWidth - font.width(offline)) / 2, layout.topHeight + (rows * layout.rowHeight - 8) / 2, ERROR,
                    false);
            return;
        }
        List<Line> lines = lines();
        if (lines.isEmpty()) {
            Component empty = Component.translatable(tab == SCHEMATICS ? "gui.encodedlogistics.schematic.empty" : "gui.encodedlogistics.jobs.empty");
            graphics.text(font, empty, layout.gridLeft + (layout.columns * layout.cell - font.width(empty)) / 2, layout.topHeight + 5, DISABLED, false);
        }
        graphics.text(font, fit(summary(), CONTENT_RIGHT - 9), 9, summaryTop, MUTED, false);
    }

    private String summary() {
        if (tab == SCHEMATICS) {
            Set<StorageKey> outputs = new LinkedHashSet<>();
            int used = 0, total = 0;
            for (SchematicData.Holder holder : data().holders()) {
                used += holder.used();
                total += holder.slots().size();
                for (ItemStack stack : holder.slots()) {
                    Schematic schematic = SchematicItem.schematic(stack);
                    if (schematic != null) {
                        outputs.addAll(schematic.outputTotals().keySet());
                    }
                }
            }
            return Component.translatable("gui.encodedlogistics.schematic.summary", outputs.size(), used, total).getString();
        }
        int threadsUsed = 0, threads = 0;
        long memoryUsed = 0, memory = 0;
        String scheduler = schedulerName();
        for (SchematicData.Scheduler each : data().schedulers()) {
            if (scheduler == null || each.name().equals(scheduler)) {
                threadsUsed += each.threadsUsed();
                threads += each.threads();
                memoryUsed += each.memoryUsed();
                memory += each.memory();
            }
        }
        return Component.translatable("gui.encodedlogistics.jobs.summary", threadsUsed, threads, MemoryServerDevice.memory(memoryUsed),
                MemoryServerDevice.memory(memory)).getString();
    }

    // The string, cut short with "..." if it doesn't fit (the layout leaves room, so this is for names longer than any
    // the mockups had).
    private String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return width <= font.width("...") ? "" : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    private static String amount(StorageKey key, long amount) {
        return key.isItem() ? Long.toString(amount) : key.format(amount);
    }

    // "4:12", "1:02:40".
    private static String time(long ticks) {
        long seconds = ticks / 20;
        return seconds >= 3_600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    // --- Tooltips ---

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (popup != null || !menu.getCarried().isEmpty()) {
            return;
        }
        List<Component> lines = tooltip(mouseX, mouseY);
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private List<Component> tooltip(int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        for (int button = 0; button < 4; button++) {
            int bx = leftPos + layout.toolbarLeft, by = topPos + layout.toolbarTop + button * layout.toolbarSpacing;
            if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by && mouseY < by + 18) {
                buttonTooltip(button, lines);
                return lines;
            }
        }
        int summaryTop = topPos + layout.topHeight + rows * layout.rowHeight;
        if (tab == SCHEMATICS && data().online() && mouseX >= leftPos + 8 && mouseX < leftPos + SCROLL_COLUMN && mouseY >= summaryTop
                && mouseY < summaryTop + layout.sectionHeight) {
            lines.add(Component.translatable("gui.encodedlogistics.schematic.summary.tooltip", data().holders().size()));
            return lines;
        }
        if (!data().online()) {
            return lines;
        }
        Placed placed = lineAt(lines(), mouseX, mouseY);
        if (placed == null) {
            return lines;
        }
        Line line = placed.line();
        switch (line.type()) {
            case HEADER -> {
                SchematicData.Holder holder = line.holder();
                int badgeX = leftPos + CONTENT_RIGHT - 1;
                List<Identifier> badges = badges(holder);
                for (int i = badges.size() - 1; i >= 0; i--) {
                    if (mouseX >= badgeX - 7 && mouseX < badgeX && mouseY >= placed.y() + 5 && mouseY < placed.y() + 12) {
                        badgeTooltip(holder, badges.get(i), lines);
                        return lines;
                    }
                    badgeX -= 9;
                }
                lines.add(Component.literal(holder.name()));
                lines.add(Component.literal(holder.location()).withColor(MUTED));
                lines.add(Component.translatable("gui.encodedlogistics.schematic.slots", holder.used(), holder.slots().size()).withColor(MUTED));
                if (holder.readOnly()) {
                    lines.add(Component.translatable("gui.encodedlogistics.schematic.readonly").withColor(DISABLED));
                }
                lines.add(Component.translatable("gui.encodedlogistics.schematic.select").withColor(DISABLED));
            }
            case SLOTS -> {
                int slot = slotAt(placed, mouseX, mouseY);
                if (slot >= 0 && !line.holder().slots().get(slot).isEmpty()) {
                    lines.addAll(getTooltipFromContainerItem(line.holder().slots().get(slot)));
                    if (line.holder().readOnly()) {
                        lines.add(Component.translatable("gui.encodedlogistics.schematic.readonly").withColor(DISABLED));
                    }
                }
            }
            case JOB -> {
                SchematicData.Job job = line.job();
                lines.add(Component.literal(job.target().displayName().getString() + " x" + amount(job.target(), job.amount())));
                lines.add(Component.translatable("gui.encodedlogistics.jobs.tooltip.scheduler", job.scheduler()).withColor(MUTED));
                if (!job.requester().isEmpty()) {
                    lines.add(Component.translatable("gui.encodedlogistics.jobs.tooltip.requester", job.requester()).withColor(MUTED));
                }
                lines.add(Component.translatable("gui.encodedlogistics.jobs.tooltip.progress", job.done(), job.total()).withColor(MUTED));
                if (job.eta() >= 0) {
                    lines.add(Component.translatable("gui.encodedlogistics.jobs.tooltip.eta", time(job.eta())).withColor(MUTED));
                }
                if (!job.reason().isEmpty()) {
                    String key = job.ended() ? "gui.encodedlogistics.job.reason." : "gui.encodedlogistics.jobs.reason.";
                    lines.add(Component.translatable("gui.encodedlogistics.jobs.stalled", Component.translatable(key + job.reason())).withColor(WARNING));
                }
                if (job.ended()) {
                    lines.add(Component.translatable("gui.encodedlogistics.jobs.tooltip.record", job.record()).withColor(DISABLED));
                } else {
                    if (!job.steps().isEmpty()) {
                        lines.add(Component.translatable("gui.encodedlogistics.jobs.tooltip.expand").withColor(DISABLED));
                    }
                    if (data().craft() || job.mine()) {
                        lines.add(Component.translatable("gui.encodedlogistics.jobs.tooltip.menu").withColor(DISABLED));
                    }
                }
            }
            case REASON -> {
                if (line.job().steps().get(line.step()).locatable() && mouseX >= leftPos + 161 && mouseX < leftPos + 170) {
                    lines.add(Component.translatable("gui.encodedlogistics.schematic.locate"));
                }
            }
            default -> {}
        }
        return lines;
    }

    private void badgeTooltip(SchematicData.Holder holder, Identifier badge, List<Component> lines) {
        if (badge == BADGE_PARALLEL) {
            lines.add(Component.translatable("gui.encodedlogistics.schematic.badge.parallel", holder.parallel()));
        } else if (badge == BADGE_CONFLICT) {
            lines.add(Component.translatable("gui.encodedlogistics.schematic.badge.conflict", holder.conflictItem(), holder.conflictPreferred()));
        } else {
            lines.add(Component.translatable("gui.encodedlogistics.schematic.badge.gateway"));
        }
    }

    private void buttonTooltip(int button, List<Component> lines) {
        if (button == 3) {
            lines.add(Component.translatable("gui.encodedlogistics.terminal.height"));
            lines.add(Component.translatable("gui.encodedlogistics.terminal.height." + TerminalSettings.height().name().toLowerCase(Locale.ROOT)).withColor(MUTED));
            return;
        }
        if (tab == SCHEMATICS) {
            switch (button) {
                case 0 -> lines.add(Component.translatable("gui.encodedlogistics.schematic.sort." + new String[] { "name", "count", "type" }[holderSort]));
                case 1 -> lines.add(Component.translatable(descending ? "gui.encodedlogistics.terminal.dir.desc" : "gui.encodedlogistics.terminal.dir.asc"));
                default -> {
                    lines.add(Component.translatable("gui.encodedlogistics.schematic.filter"));
                    lines.add(Component.translatable(freeOnly ? "gui.encodedlogistics.schematic.filter.free" : "gui.encodedlogistics.schematic.filter.all")
                            .withColor(MUTED));
                    lines.add(Component.translatable("gui.encodedlogistics.schematic.kind." + (kindFilter < 0 ? "all" : Integer.toString(kindFilter)))
                            .withColor(MUTED));
                    lines.add(Component.translatable("gui.encodedlogistics.schematic.filter.hint", SneakKey.name()).withColor(DISABLED));
                }
            }
            return;
        }
        switch (button) {
            case 0 -> {
                lines.add(Component.translatable("gui.encodedlogistics.jobs.filter.scheduler"));
                lines.add((schedulerName() != null ? Component.literal(schedulerName()) : Component.translatable("gui.encodedlogistics.jobs.filter.scheduler.all"))
                        .withColor(MUTED));
            }
            case 1 -> {
                lines.add(Component.translatable("gui.encodedlogistics.jobs.filter.requester"));
                lines.add(Component.translatable(mineOnly ? "gui.encodedlogistics.jobs.filter.requester.mine" : "gui.encodedlogistics.jobs.filter.requester.all")
                        .withColor(MUTED));
            }
            default -> {
                lines.add(Component.translatable("gui.encodedlogistics.jobs.filter.show"));
                lines.add(Component.translatable(activeOnly ? "gui.encodedlogistics.jobs.filter.show.active" : "gui.encodedlogistics.jobs.filter.show.all")
                        .withColor(MUTED));
            }
        }
    }

    // --- Input ---

    private void send(int action, String holder, int slot, @Nullable UUID job, String text) {
        ClientPacketDistributor.sendToServer(new SchematicTerminalActionPayload(menu.containerId, action, holder, slot, Optional.ofNullable(job), text));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (popup != null) {
            Popup shown = popup;
            popup = null;
            int width = popupWidth(shown);
            for (int i = 0; i < shown.items().size(); i++) {
                int iy = shown.y() + 2 + i * 12;
                if (mx >= shown.x() && mx < shown.x() + width && my >= iy && my < iy + 12) {
                    shown.items().get(i).run().run();
                    break;
                }
            }
            return true;
        }
        if (renameBox != null) {
            if (renameBox.isMouseOver(mx, my)) {
                return renameBox.mouseClicked(event, doubleClick);
            }
            cancelRename();
        }
        // The tabs.
        for (int i = 0; i < 2; i++) {
            int tx = leftPos + tabLeft + i * tabStep;
            if (mx >= tx && mx < tx + tabWidth && my >= topPos + tabTop && my < topPos + tabTop + tabHeight) {
                if (tab != i) {
                    tab = i;
                    scroll = 0;
                }
                return true;
            }
        }
        // The toolbar.
        for (int b = 0; b < 4; b++) {
            int bx = leftPos + layout.toolbarLeft, by = topPos + layout.toolbarTop + b * layout.toolbarSpacing;
            if (mx >= bx && mx < bx + 18 && my >= by && my < by + 18) {
                pressButton(b, SneakKey.held(event.hasShiftDown()));
                return true;
            }
        }
        List<Line> lines = lines();
        int max = maxScroll(lines);
        int sx = leftPos + layout.scrollLeft, sy = topPos + layout.scrollTop;
        if (button == InputConstants.MOUSE_BUTTON_LEFT && max > 0 && mx >= sx && mx < sx + layout.thumbWidth && my >= sy && my < sy + rows * layout.rowHeight) {
            draggingThumb = true;
            scrollTo(my, max);
            return true;
        }
        if (data().online()) {
            Placed placed = lineAt(lines, mx, my);
            if (placed != null) {
                clickLine(placed, event);
                return true;
            }
            // Sneak-click a schematic in the inventory: into the selected holder.
            SchematicData.Holder holder = selectedHolder();
            if (tab == SCHEMATICS && hoveredSlot != null && holder != null && !holder.readOnly() && data().edit() && hoveredSlot.hasItem()
                    && SneakKey.held(event.hasShiftDown()) && SchematicItem.schematic(hoveredSlot.getItem()) != null) {
                send(SchematicTerminalService.QUICK_PUT, holder.key(), hoveredSlot.index, null, "");
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void pressButton(int button, boolean sneak) {
        if (button == 3) {
            cycleHeight();
            return;
        }
        scroll = 0;
        if (tab == SCHEMATICS) {
            switch (button) {
                case 0 -> holderSort = (holderSort + 1) % 3;
                case 1 -> descending = !descending;
                default -> {
                    if (sneak) {
                        kindFilter = kindFilter + 1 >= SchematicData.KINDS ? -1 : kindFilter + 1;
                    } else {
                        freeOnly = !freeOnly;
                    }
                }
            }
            return;
        }
        switch (button) {
            case 0 -> schedulerFilter = schedulerFilter + 1 >= data().schedulers().size() ? -1 : schedulerFilter + 1;
            case 1 -> mineOnly = !mineOnly;
            default -> activeOnly = !activeOnly;
        }
    }

    private void clickLine(Placed placed, MouseButtonEvent event) {
        Line line = placed.line();
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        switch (line.type()) {
            case HEADER -> {
                SchematicData.Holder holder = line.holder();
                selected = holder.key();
                if (right) {
                    List<PopupItem> items = new ArrayList<>();
                    items.add(new PopupItem(Component.translatable("gui.encodedlogistics.schematic.locate"), LOCATE,
                            () -> send(SchematicTerminalService.LOCATE, holder.key(), 0, null, "")));
                    if (holder.renamable() && data().edit()) {
                        items.add(new PopupItem(Component.translatable("gui.encodedlogistics.schematic.rename"), RENAME, () -> startRename(holder, placed.y())));
                    }
                    popup = new Popup((int) event.x(), (int) event.y(), items);
                }
            }
            case SLOTS -> {
                int slot = slotAt(placed, event.x(), event.y());
                SchematicData.Holder holder = line.holder();
                if (slot < 0 || holder.readOnly() || !data().edit()) {
                    return;
                }
                selected = holder.key();
                boolean carrying = !menu.getCarried().isEmpty();
                if (carrying) {
                    send(SchematicTerminalService.PUT, holder.key(), slot, null, "");
                } else if (!holder.slots().get(slot).isEmpty()) {
                    send(SchematicTerminalService.TAKE, holder.key(), slot, null, "");
                }
            }
            case JOB -> {
                SchematicData.Job job = line.job();
                if (right) {
                    jobPopup(job, event);
                } else if (!job.steps().isEmpty() && !expanded.remove(job.id())) {
                    expanded.add(job.id());
                }
            }
            case REASON -> {
                SchematicData.Step step = line.job().steps().get(line.step());
                if (step.locatable() && event.x() >= leftPos + 161 && event.x() < leftPos + 170) {
                    send(SchematicTerminalService.LOCATE_STEP, "", line.step(), line.job().id(), "");
                }
            }
            default -> {}
        }
    }

    // Right-click on a job: cancel (confirmed), hold or release - for a player with CRAFT, or the job's own requester.
    private void jobPopup(SchematicData.Job job, MouseButtonEvent event) {
        if (job.ended() || !data().craft() && !job.mine()) {
            return;
        }
        List<PopupItem> items = new ArrayList<>();
        int x = (int) event.x(), y = (int) event.y();
        items.add(new PopupItem(Component.translatable("gui.encodedlogistics.jobs.cancel"), null, () -> popup = new Popup(x, y, List.of(
                new PopupItem(Component.translatable("gui.encodedlogistics.jobs.confirm_cancel", String.format(Locale.ROOT, "%04d", job.number() % 10_000))
                        .withColor(ERROR), null, () -> send(SchematicTerminalService.CANCEL, "", 0, job.id(), "")),
                new PopupItem(Component.translatable("gui.encodedlogistics.jobs.keep"), null, () -> {})))));
        boolean held = job.status() == SchematicData.HELD;
        items.add(new PopupItem(Component.translatable(held ? "gui.encodedlogistics.jobs.release" : "gui.encodedlogistics.jobs.hold"), null,
                () -> send(held ? SchematicTerminalService.RELEASE : SchematicTerminalService.HOLD, "", 0, job.id(), "")));
        popup = new Popup(x, y, items);
    }

    // Rename: a field over the header's name; Enter sends it (RNMDEV's rules), Escape or a click elsewhere drops it.
    private void startRename(SchematicData.Holder holder, int y) {
        renaming = holder.key();
        renameBox = new EditBox(font, leftPos + 28, y + 5, 100, 9, Component.translatable("gui.encodedlogistics.schematic.rename"));
        renameBox.setBordered(false);
        renameBox.setMaxLength(10);
        renameBox.setTextColor(TEXT);
        String name = holder.name().split(" ")[0];
        renameBox.setValue(name);
        renameBox.setFocused(true);
        setFocused(renameBox);
    }

    private void cancelRename() {
        renaming = null;
        renameBox = null;
    }

    private void cycleHeight() {
        TerminalLayout.Height[] heights = TerminalLayout.Height.values();
        TerminalSettings.height(heights[(TerminalSettings.height().ordinal() + 1) % heights.length]);
        int wanted = layout.rowsFor(height - layout.sectionHeight);
        if (wanted == rows) {
            return;
        }
        rows = wanted;
        int dy = (rows - menu.rows()) * layout.rowHeight;
        for (int i = 0; i < menu.slots.size(); i++) {
            menu.slots.set(i, MovedSlot.of(menu.slots.get(i), dy));
        }
        inventoryLabelY = labelY();
        scroll = 0;
        rebuildWidgets();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingThumb) {
            scrollTo(event.y(), maxScroll(lines()));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingThumb = false;
        return super.mouseReleased(event);
    }

    private void scrollTo(double mouseY, int max) {
        if (max <= 0) {
            return;
        }
        float position = (float) (mouseY - topPos - layout.scrollTop - layout.thumbHeight / 2.0) / (rows * layout.rowHeight - layout.thumbHeight);
        scroll = Mth.clamp(Math.round(position * max), 0, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double y = mouseY - topPos - layout.topHeight;
        if (y >= 0 && y < rows * layout.rowHeight && mouseX >= leftPos && mouseX < leftPos + layout.width) {
            popup = null;
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * (tab == SCHEMATICS ? 1 : 2), 0, maxScroll(lines()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popup != null && event.isEscape()) {
            popup = null;
            return true;
        }
        if (renameBox != null) {
            if (event.isEscape()) {
                cancelRename();
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                if (renaming != null && !renameBox.getValue().isBlank()) {
                    send(SchematicTerminalService.RENAME, renaming, 0, null, renameBox.getValue().trim());
                }
                cancelRename();
                return true;
            }
            return renameBox.keyPressed(event) || renameBox.canConsumeInput() || super.keyPressed(event);
        }
        if (search != null && search.isFocused() && !event.isEscape()) {
            return search.keyPressed(event) || search.canConsumeInput() || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (renameBox != null) {
            return renameBox.charTyped(event);
        }
        return super.charTyped(event);
    }
}
