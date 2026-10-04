/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.item.LtoTapeItem;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackSlot;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;
import net.zagdrath.encodedlogistics.storage.DriveStats;

// A Tape Library's panel (screens/rack/tape_library_4u.json, _6u.json), two tabs:
//  Magazine (gui/rack/tape_library.png): the tape slots, eight a row, three rows at a time (scroll through 3 or 6), a
//    fill bar under each; the drive bays (2 or 4) with what each drive is doing; the cold capacity bar; what it's doing.
//  Policy (gui/rack/tape_library_policy.png): archive after <n> <minutes | hours | days> untouched; only while hot storage
//    is above <x>% full [On / Off]; Keep hot - nine filter entries (right-click one for its fuzzy match); Pinned
//    (always hot) - nine items; how full hot storage is.
// Click an entry with an item to set it, empty-handed to clear it; items dragged from JEI land too. Typed numbers are
// set with Enter (or by clicking away).
public class TapeLibraryPanel extends RackScreen.Panel {
    private static final Identifier MAGAZINE = EncodedLogistics.id("textures/gui/rack/tape_library.png"),
            POLICY = EncodedLogistics.id("textures/gui/rack/tape_library_policy.png");
    private static final Identifier TAB_ACTIVE = EncodedLogistics.id("rack/switch/tab_active"), TAB_INACTIVE = EncodedLogistics.id("rack/switch/tab_inactive"),
            GHOST_TAPE = EncodedLogistics.id("rack/tape/ghost_tape"), GHOST_DRIVE = EncodedLogistics.id("rack/tape/ghost_drive"),
            FILL_TRACK = EncodedLogistics.id("rack/tape/slot_fill_track"), BAR = EncodedLogistics.id("common/bar_fill_mint"),
            THUMB = EncodedLogistics.id("controller/scroll_thumb"), THUMB_HOVER = EncodedLogistics.id("controller/scroll_thumb_hover"),
            THUMB_DISABLED = EncodedLogistics.id("controller/scroll_thumb_disabled"), FIELD = EncodedLogistics.id("inventory_tap/number_field"),
            FIELD_FOCUSED = EncodedLogistics.id("inventory_tap/number_field_focused");
    private static final Identifier[] FILLS = { EncodedLogistics.id("rack/tape/slot_fill_low"), EncodedLogistics.id("rack/tape/slot_fill_mid"),
            EncodedLogistics.id("rack/tape/slot_fill_high"), EncodedLogistics.id("rack/tape/slot_fill_full") };
    private static final Identifier[] CHIPS = { EncodedLogistics.id("rack/tape/drive_empty"), EncodedLogistics.id("rack/tape/drive_idle"),
            EncodedLogistics.id("rack/tape/drive_read"), EncodedLogistics.id("rack/tape/drive_write") };
    private static final String[] CHIP_KEYS = { "empty", "idle", "reading", "writing" }, UNITS = { "min", "h", "d" };
    private static final String[] TABS = { "gui.encodedlogistics.tape.tab.magazine", "gui.encodedlogistics.tape.tab.policy" };
    private static final int TABS_X = 8, TABS_Y = 17, TAB_STEP = 53, TAB_W = 52;
    private static final int COLUMNS = 8, ROWS_SHOWN = 3, ROW_PITCH = 20, SCROLL_X = 156, SCROLL_Y = 32, SCROLL_H = 58, THUMB_W = 6, THUMB_H = 15;
    private static final int BAY_Y = 99, BAR_X = 9, BAR_Y = 125, BAR_W = 158, ACTIVITY_X = 12, ACTIVITY_Y = 142;
    private static final int FIELD_X = 80, AGE_Y = 35, PERCENT_Y = 49, FIELD_W = 36, FIELD_H = 12, BUTTON_X = 120, BUTTON_W = 40, BUTTON_H = 14;
    private static final int KEEP_Y = 81, PINNED_Y = 117, ENTRY_X = 9, NOTE_Y = 140;
    private static final int PANEL_BG = 0xFF4B4B4B;

    private final boolean six;
    private int tab, scroll;
    private boolean draggingThumb;
    private @Nullable EditBox age, percent;
    private int shownAge = -1, shownPercent = -1;
    private @Nullable FuzzyPopup popup;

    public TapeLibraryPanel(RackScreen screen) {
        super(screen);
        six = screen.pickedDevice() != null && screen.pickedDevice().type() == RackDeviceType.TAPE_LIBRARY_6U;
    }

    @Override
    protected Identifier background() {
        return tab == 0 ? MAGAZINE : POLICY;
    }

    private RackDeviceType type() {
        return six ? RackDeviceType.TAPE_LIBRARY_6U : RackDeviceType.TAPE_LIBRARY_4U;
    }

    private int tapes() {
        return six ? 48 : 24;
    }

    private int bays() {
        return six ? 4 : 2;
    }

    private int maxScroll() {
        return tapes() / COLUMNS - ROWS_SHOWN;
    }

    private @Nullable TapeLibraryDevice library() {
        return device(TapeLibraryDevice.class);
    }

    // --- Slots: scrolled, and hidden on the policy page ---

    private void applyWindow() {
        screen.getMenu().setSlotWindow(tab == 0, scroll, ROWS_SHOWN);
        for (int i = 0; i < tapes(); i++) {
            Slot slot = screen.getMenu().deviceSlot(i);
            if (slot != null) {
                screen.getMenu().slots.set(slot.index, MovedSlot.of(slot, -ROW_PITCH * scroll));
            }
        }
    }

    @Override
    protected void init() {
        age = field(AGE_Y, 4);
        percent = field(PERCENT_Y, 3);
        applyWindow();
        showFields();
    }

    private EditBox field(int y, int length) {
        EditBox box = new EditBox(font(), screen.left() + FIELD_X + 4, screen.top() + y + 2, FIELD_W - 6, 9, Component.empty());
        box.setBordered(false);
        box.setMaxLength(length);
        box.setTextColor(RackScreen.TEXT);
        PartScreens.numeric(box, false);
        screen.addPanelWidget(box);
        return box;
    }

    private void showFields() {
        if (age != null && percent != null) {
            age.visible = percent.visible = tab == 1;
        }
    }

    @Override
    protected void removed() {
        for (int i = 0; i < tapes(); i++) {
            Slot slot = screen.getMenu().deviceSlot(i);
            if (slot != null) {
                screen.getMenu().slots.set(slot.index, MovedSlot.of(slot, 0));
            }
        }
        screen.getMenu().resetSlotWindow();
        if (age != null) {
            screen.removePanelWidget(age);
        }
        if (percent != null) {
            screen.removePanelWidget(percent);
        }
    }

    // The fields follow the server's values unless they're being typed in.
    @Override
    protected void tick() {
        TapeLibraryDevice library = library();
        if (library == null) {
            return;
        }
        if (age != null && !age.isFocused() && library.age() != shownAge) {
            shownAge = library.age();
            age.setValue(Integer.toString(shownAge));
        }
        if (percent != null && !percent.isFocused() && library.percent() != shownPercent) {
            shownPercent = library.percent();
            percent.setValue(Integer.toString(shownPercent));
        }
    }

    private void submit(@Nullable EditBox box, int action, int min, int max, int shown) {
        if (box == null) {
            return;
        }
        try {
            send(action, Mth.clamp(Integer.parseInt(box.getValue()), min, max), "");
        } catch (NumberFormatException e) {
            box.setValue(Integer.toString(shown));
        }
        box.setFocused(false);
    }

    // --- Drawing ---

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        for (int i = 0; i < TABS.length; i++) {
            boolean active = i == tab;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, active ? TAB_ACTIVE : TAB_INACTIVE, x + TABS_X + i * TAB_STEP, y + TABS_Y, TAB_W, active ? 13 : 12);
        }
        TapeLibraryDevice library = library();
        if (tab == 0) {
            extractMagazine(graphics, library, x, y, mouseX, mouseY);
        } else if (library != null) {
            extractPolicy(graphics, library, x, y, mouseX, mouseY);
        }
    }

    private void extractMagazine(GuiGraphicsExtractor graphics, @Nullable TapeLibraryDevice library, int x, int y, int mouseX, int mouseY) {
        List<RackSlot> specs = type().slots();
        for (int row = 0; row < ROWS_SHOWN; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int i = (scroll + row) * COLUMNS + column;
                Slot slot = screen.getMenu().deviceSlot(i);
                int sx = x + specs.get(i).x(), sy = y + 33 + row * ROW_PITCH;
                ItemStack stack = slot != null ? slot.getItem() : ItemStack.EMPTY;
                if (stack.isEmpty()) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOST_TAPE, sx, sy, 16, 16);
                    continue;
                }
                // Its fill under it.
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FILL_TRACK, sx, sy + 17, 16, 2);
                DriveStats stats = LtoTapeItem.stats(stack);
                double fill = stats.fill();
                int width = (int) Math.ceil(16 * fill);
                if (width > 0) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FILLS[Math.min(3, stats.light())], 16, 1, 0, 0, sx, sy + 17, width, 1);
                }
            }
        }
        // Scrollbar.
        int max = maxScroll();
        int thumbY = y + SCROLL_Y + 1 + (max == 0 ? 0 : Math.round((float) scroll * (SCROLL_H - 2 - THUMB_H) / max));
        boolean overThumb = PartScreens.over(mouseX, mouseY, x + SCROLL_X + 2, thumbY, THUMB_W, THUMB_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, max == 0 ? THUMB_DISABLED : draggingThumb || overThumb ? THUMB_HOVER : THUMB, x + SCROLL_X + 2,
                thumbY, THUMB_W, THUMB_H);
        // The drive bays: a 4U library's last two aren't there.
        if (!six) {
            graphics.fill(x + 86, y + 96, x + 168, y + 118, PANEL_BG);
        }
        ValueInput data = data();
        int[] drives = data != null ? data.getIntArray("drives").orElse(new int[0]) : new int[0];
        for (int bay = 0; bay < bays(); bay++) {
            RackSlot spec = specs.get(tapes() + bay);
            Slot slot = screen.getMenu().deviceSlot(tapes() + bay);
            if (slot == null || slot.getItem().isEmpty()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOST_DRIVE, x + spec.x(), y + spec.y(), 16, 16);
            }
            int state = bay < drives.length ? drives[bay] : TapeLibraryDevice.DRIVE_NONE;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CHIPS[Mth.clamp(state + 1, 0, 3)], x + spec.x() + 23, y + BAY_Y + 6, 8, 5);
        }
        if (data != null) {
            long total = data.getLongOr("cold_total", 0);
            PartScreens.bar(graphics, BAR, x + BAR_X, y + BAR_Y, BAR_W, total <= 0 ? 0 : (float) data.getLongOr("cold_used", 0) / total);
        }
    }

    private void extractPolicy(GuiGraphicsExtractor graphics, TapeLibraryDevice library, int x, int y, int mouseX, int mouseY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, age != null && age.isFocused() ? FIELD_FOCUSED : FIELD, x + FIELD_X, y + AGE_Y, FIELD_W, FIELD_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, percent != null && percent.isFocused() ? FIELD_FOCUSED : FIELD, x + FIELD_X, y + PERCENT_Y, FIELD_W,
                FIELD_H);
        PartScreens.wideButton(graphics, font(), x + BUTTON_X, y + AGE_Y - 2, BUTTON_W, BUTTON_H,
                Component.translatable("gui.encodedlogistics.tape.unit." + UNITS[Mth.clamp(library.unit(), 0, 2)]), true, mouseX, mouseY);
        PartScreens.wideButton(graphics, font(), x + BUTTON_X, y + PERCENT_Y - 2, BUTTON_W, BUTTON_H,
                Component.translatable(library.trigger() ? "gui.encodedlogistics.tape.policy.on" : "gui.encodedlogistics.tape.policy.off"), true, mouseX,
                mouseY);
        for (int i = 0; i < TapeLibraryDevice.KEEP; i++) {
            ItemStack entry = library.keepHot().entries().get(i);
            if (!entry.isEmpty()) {
                graphics.item(entry, x + ENTRY_X + i * 18, y + KEEP_Y);
                FuzzyMarks.mark(graphics, font(), library.keepHot().fuzzy(i), x + ENTRY_X + i * 18, y + KEEP_Y);
            }
        }
        for (int i = 0; i < TapeLibraryDevice.PINNED; i++) {
            ItemStack entry = library.pinned().get(i);
            if (!entry.isEmpty()) {
                graphics.item(entry, x + ENTRY_X + i * 18, y + PINNED_Y);
            }
        }
        // The entry under the mouse.
        int keep = entryAt(mouseX - x, mouseY - y, KEEP_Y), pin = entryAt(mouseX - x, mouseY - y, PINNED_Y);
        if (keep >= 0 || pin >= 0) {
            graphics.fill(x + ENTRY_X + Math.max(keep, pin) * 18, y + (keep >= 0 ? KEEP_Y : PINNED_Y), x + ENTRY_X + Math.max(keep, pin) * 18 + 16,
                    y + (keep >= 0 ? KEEP_Y : PINNED_Y) + 16, 0x80FFFFFF);
        }
    }

    // A row of nine entries at y: the one at (px, py), panel-relative, or -1.
    private static int entryAt(double px, double py, int rowY) {
        if (py < rowY || py >= rowY + 16 || px < ENTRY_X) {
            return -1;
        }
        int i = (int) (px - ENTRY_X) / 18;
        return i < 9 && px - ENTRY_X - i * 18 < 16 ? i : -1;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < TABS.length; i++) {
            graphics.centeredText(font(), Component.translatable(TABS[i]), TABS_X + i * TAB_STEP + TAB_W / 2, TABS_Y + 3,
                    i == tab ? RackScreen.TEXT : RackScreen.TEXT_MUTED);
        }
        ValueInput data = data();
        if (tab == 0) {
            if (data != null) {
                List<Component> lines = data.read("activity", ComponentSerialization.CODEC.listOf()).orElse(List.of());
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    Component line = lines.get(i);
                    boolean recalling = line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                            && contents.getKey().endsWith("recalling");
                    graphics.text(font(), font().plainSubstrByWidth(line.getString(), 152), ACTIVITY_X, ACTIVITY_Y + i * 10,
                            recalling ? CraftPlanScreen.TAPE_BLUE : RackScreen.TEXT, false);
                }
            }
        } else {
            graphics.text(font(), Component.translatable("gui.encodedlogistics.tape.policy.age"), 12, AGE_Y + 2, RackScreen.TEXT, false);
            graphics.text(font(), Component.translatable("gui.encodedlogistics.tape.policy.free"), 12, PERCENT_Y + 2, RackScreen.TEXT, false);
            graphics.text(font(), "%", FIELD_X + FIELD_W - 7, PERCENT_Y + 2, RackScreen.TEXT_MUTED, false);
            graphics.text(font(), Component.translatable("gui.encodedlogistics.tape.policy.keep_hot"), 8, KEEP_Y - 11, RackScreen.TEXT_MUTED, false);
            graphics.text(font(), Component.translatable("gui.encodedlogistics.tape.policy.pinned"), 8, PINNED_Y - 11, RackScreen.TEXT_MUTED, false);
            if (data != null) {
                graphics.text(font(), Component.translatable("gui.encodedlogistics.tape.policy.hot_full", data.getIntOr("hot_percent", 0)), 8, NOTE_Y,
                        RackScreen.TEXT_MUTED, false);
            }
            if (popup != null) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(-screen.left(), -screen.top());
                popup.extract(graphics, font(), mouseX, mouseY);
                graphics.pose().popMatrix();
            }
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (popup != null) {
            return;
        }
        int px = mouseX - screen.left(), py = mouseY - screen.top();
        TapeLibraryDevice library = library();
        ValueInput data = data();
        if (tab == 0) {
            int[] drives = data != null ? data.getIntArray("drives").orElse(new int[0]) : new int[0];
            for (int bay = 0; bay < bays(); bay++) {
                int bx = type().slots().get(tapes() + bay).x() + 18;
                if (px >= bx && px < bx + 18 && py >= BAY_Y - 1 && py < BAY_Y + 17) {
                    int state = bay < drives.length ? drives[bay] : TapeLibraryDevice.DRIVE_NONE;
                    graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.tape.drive." + CHIP_KEYS[Mth.clamp(state + 1, 0, 3)]),
                            mouseX, mouseY);
                }
            }
            if (data != null && px >= BAR_X && px < BAR_X + BAR_W && py >= BAR_Y - 1 && py < BAR_Y + 7) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.storage.capacity",
                        StorageDevice.bytes(data.getLongOr("cold_used", 0)), StorageDevice.bytes(data.getLongOr("cold_total", 0))), mouseX, mouseY);
            }
            return;
        }
        if (library == null) {
            return;
        }
        int keep = entryAt(px, py, KEEP_Y), pin = entryAt(px, py, PINNED_Y);
        ItemStack entry = keep >= 0 ? library.keepHot().entries().get(keep) : pin >= 0 ? library.pinned().get(pin) : ItemStack.EMPTY;
        if (keep >= 0 || pin >= 0) {
            List<Component> lines = new ArrayList<>();
            if (!entry.isEmpty() && keep >= 0) {
                FuzzyMarks.tooltip(entry, library.keepHot().fuzzy(keep), lines);
            } else if (!entry.isEmpty()) {
                lines.add(entry.getHoverName());
            }
            lines.add(Component.translatable(keep >= 0 ? "gui.encodedlogistics.tape.policy.keep_hint" : "gui.encodedlogistics.tape.policy.pinned_hint")
                    .withColor(RackScreen.TEXT_DISABLED));
            graphics.setComponentTooltipForNextFrame(font(), lines, mouseX, mouseY);
        } else if (py >= AGE_Y - 2 && py < AGE_Y + 12 && px >= BUTTON_X && px < BUTTON_X + BUTTON_W) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.tape.policy.age_hint"), mouseX, mouseY);
        } else if (py >= PERCENT_Y - 2 && py < PERCENT_Y + 12 && px >= BUTTON_X && px < BUTTON_X + BUTTON_W) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.tape.policy.free_hint"), mouseX, mouseY);
        }
    }

    // --- Input ---

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (popup != null) {
            int code = popup.click(x + screen.left(), y + screen.top());
            if (code >= 0) {
                send(TapeLibraryDevice.ACTION_SET_FUZZY, popup.entry << 8 | code, "");
            }
            popup = null;
            return true;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (left && x >= TABS_X + i * TAB_STEP && x < TABS_X + i * TAB_STEP + TAB_W && y >= TABS_Y && y < TABS_Y + 13) {
                tab = i;
                applyWindow();
                showFields();
                return true;
            }
        }
        if (tab == 0) {
            if (left && maxScroll() > 0 && x >= SCROLL_X && x < SCROLL_X + 10 && y >= SCROLL_Y && y < SCROLL_Y + SCROLL_H) {
                draggingThumb = true;
                scrollTo(y);
                return true;
            }
            return false;
        }
        TapeLibraryDevice library = library();
        // Fields lose focus (and send) when clicked away from.
        if (age != null && age.isFocused() && !(x >= FIELD_X && x < FIELD_X + FIELD_W && y >= AGE_Y && y < AGE_Y + FIELD_H)) {
            submit(age, TapeLibraryDevice.ACTION_SET_AGE, 1, 9_999, shownAge);
        }
        if (percent != null && percent.isFocused() && !(x >= FIELD_X && x < FIELD_X + FIELD_W && y >= PERCENT_Y && y < PERCENT_Y + FIELD_H)) {
            submit(percent, TapeLibraryDevice.ACTION_SET_PERCENT, 0, 100, shownPercent);
        }
        if (left && x >= BUTTON_X && x < BUTTON_X + BUTTON_W) {
            if (y >= AGE_Y - 2 && y < AGE_Y - 2 + BUTTON_H) {
                send(TapeLibraryDevice.ACTION_CYCLE_UNIT, 0, "");
                return true;
            }
            if (y >= PERCENT_Y - 2 && y < PERCENT_Y - 2 + BUTTON_H) {
                send(TapeLibraryDevice.ACTION_TOGGLE_TRIGGER, 0, "");
                return true;
            }
        }
        int keep = entryAt(x, y, KEEP_Y), pin = entryAt(x, y, PINNED_Y);
        if (keep >= 0 && right && library != null && !library.keepHot().entries().get(keep).isEmpty()) {
            ItemStack entry = library.keepHot().entries().get(keep);
            popup = new FuzzyPopup(font(), keep, entry, library.keepHot().fuzzy(keep), (int) x + screen.left(), (int) y + screen.top(), screen.width,
                    screen.height);
            return true;
        }
        if (keep >= 0 || pin >= 0) {
            int action = keep >= 0 ? TapeLibraryDevice.ACTION_SET_KEEP : TapeLibraryDevice.ACTION_SET_PINNED;
            int entry = Math.max(keep, pin);
            if (left) {
                send(action, entry, "");
            } else if (right) {
                sendItem(action, entry, ItemStack.EMPTY);
            }
            return true;
        }
        return false;
    }

    private void scrollTo(double mouseY) {
        float f = (float) (mouseY - SCROLL_Y - 1 - THUMB_H / 2.0) / (SCROLL_H - 2 - THUMB_H);
        setScroll(Math.round(f * maxScroll()));
    }

    private void setScroll(int value) {
        int clamped = Mth.clamp(value, 0, maxScroll());
        if (clamped != scroll) {
            scroll = clamped;
            applyWindow();
        }
    }

    @Override
    protected boolean mouseScrolled(double x, double y, double amount) {
        if (popup != null) {
            popup.scroll(amount);
            return true;
        }
        if (tab == 0 && y < BAY_Y - 4) {
            setScroll(scroll - (int) Math.signum(amount));
            return true;
        }
        return false;
    }

    @Override
    protected boolean mouseDragged(double x, double y) {
        if (draggingThumb) {
            scrollTo(y);
            return true;
        }
        return false;
    }

    @Override
    protected void mouseReleased() {
        draggingThumb = false;
    }

    @Override
    protected boolean keyPressed(KeyEvent event) {
        if (popup != null && event.isEscape()) {
            popup = null;
            return true;
        }
        EditBox focused = age != null && age.isFocused() ? age : percent != null && percent.isFocused() ? percent : null;
        if (focused == null) {
            return false;
        }
        if (event.isConfirmation()) {
            if (focused == age) {
                submit(age, TapeLibraryDevice.ACTION_SET_AGE, 1, 9_999, shownAge);
            } else {
                submit(percent, TapeLibraryDevice.ACTION_SET_PERCENT, 0, 100, shownPercent);
            }
            return true;
        }
        if (event.isEscape()) {
            return false;
        }
        return focused.keyPressed(event) || focused.canConsumeInput();
    }

    // JEI drags onto the policy page's entries.
    @Override
    protected List<RackScreen.GhostTarget> ghostTargets() {
        List<RackScreen.GhostTarget> targets = new ArrayList<>();
        if (tab != 1) {
            return targets;
        }
        for (int i = 0; i < PartFilter.SIZE; i++) {
            int entry = i;
            targets.add(new RackScreen.GhostTarget(screen.left() + ENTRY_X + i * 18, screen.top() + KEEP_Y, 16, 16,
                    stack -> sendItem(TapeLibraryDevice.ACTION_SET_KEEP, entry, stack)));
            targets.add(new RackScreen.GhostTarget(screen.left() + ENTRY_X + i * 18, screen.top() + PINNED_Y, 16, 16,
                    stack -> sendItem(TapeLibraryDevice.ACTION_SET_PINNED, entry, stack)));
        }
        return targets;
    }
}
