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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackSlot;
import net.zagdrath.encodedlogistics.rack.chassis.Chassis;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisLayout;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPart;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;
import net.zagdrath.encodedlogistics.rack.device.OpticalTransportShelfDevice;
import net.zagdrath.encodedlogistics.registry.ModItems;

// A modular chassis's panel (the optical handoff 4-6; previews/gui_*.png), in tabs, on a plain panel (chassis.png: no
// list well or scroll track; each tab draws its own wells):
//  Slots (Front, and Rear for the Blade Enclosure): the chassis's face at 1:1 (its ears left off) with its parts in their
//   bays, lit while they work, in a well, and its figures in a column beside it. Click an empty bay holding a part that
//   fits to put it in; click a part to pick it (its state and settings show under the face: a transponder's channel, a
//   Protection Card's revertive switch); sneak-click to take it out.
//  Transport Shelf: Channels (each transponder's channel, network here and there, lanes, up) and Spans (the span, then
//   the working and protect paths side by side: length, loss, lanes, quality, bridged; unpair).
//  Chassis Switch: Cages (each line slot's four Optical Transceiver cages, used by an Uplink Card there) and Switch -
//   the L3 Switch's own panel (ports, routes and QoS), with a Cards tab back.
//  Blade Enclosure: Drives (two Storage Drive bays behind each Storage Blade) and Fab (the Fabrication Blades'
//   schematics).
// Every text is fitted to the room it has: long hints wrap (LINE apart, up to a set number of lines), values are cut to
// their column.
public class ChassisPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/chassis.png");
    // The face: drawn from FACE_U on its sheet (the ears left off), FACE_W wide, in a well two pixels round it.
    private static final int FACE_X = 12, FACE_Y = 35, FACE_U = 10, FACE_W = 84;
    // The figures column beside it (label over value, PAIR apart), the picked part or hint under both, the text's right edge.
    private static final int COLUMN_X = 104, COLUMN_Y = 34, PAIR = 18, DETAIL_Y = 125, RIGHT = 168, ROW = 11, LINE = 10;
    private static final int OUTLINE = 0xFF5CF0B8, SELECTED = 0xFFF5B23A, SLOT_BORDER = 0xFF5A5A5A, SEPARATOR = 0xFF383838;
    // Channels: number, network, far end, up (right edge RIGHT).
    private static final int CH_X = 12, NETWORK_X = 28, FAR_X = 80;
    // Spans: the rows' labels at 12, the working and protect columns' right edges; the protection line, the pair line.
    private static final int WORKING_RIGHT = 110, PROTECT_RIGHT = RIGHT, SPAN_TABLE_Y = 50, PROTECTION_Y = 120, PAIR_Y = 142;
    private static final String[] SPAN_ROWS = { "length", "loss", "lanes", "quality", "bridged" };

    private enum Tab {
        FRONT, REAR, CHANNELS, SPANS, CAGES, SWITCH, DRIVES, FAB
    }

    private final RackDeviceType type;
    private final ChassisLayout layout;
    private final List<Tab> tabs = new ArrayList<>();
    private final PanelTabs tabStrip;
    private final @Nullable L3SwitchPanel switchPanel;
    private Tab tab = Tab.FRONT;
    private int selected = -1;

    public ChassisPanel(RackScreen screen) {
        super(screen);
        RackDevice device = screen.pickedDevice();
        this.type = device != null ? device.type() : RackDeviceType.CHASSIS_SWITCH;
        this.layout = type == RackDeviceType.CHASSIS_SWITCH ? ChassisLayout.SWITCH : type == RackDeviceType.BLADE_ENCLOSURE ? ChassisLayout.BLADE
                : ChassisLayout.SHELF;
        tabs.add(Tab.FRONT);
        if (type == RackDeviceType.BLADE_ENCLOSURE) {
            tabs.add(Tab.REAR);
            tabs.add(Tab.DRIVES);
            tabs.add(Tab.FAB);
        } else if (type == RackDeviceType.CHASSIS_SWITCH) {
            tabs.add(Tab.CAGES);
            tabs.add(Tab.SWITCH);
        } else {
            tabs.add(Tab.CHANNELS);
            tabs.add(Tab.SPANS);
        }
        this.tabStrip = new PanelTabs(tabs.stream().map(t -> "gui.encodedlogistics.chassis.tab." + (t == Tab.FRONT && type != RackDeviceType.BLADE_ENCLOSURE
                ? "slots" : t.name().toLowerCase(Locale.ROOT))).toArray(String[]::new));
        this.switchPanel = type == RackDeviceType.CHASSIS_SWITCH ? new L3SwitchPanel(screen, Component.translatable("gui.encodedlogistics.chassis.cards"))
                : null;
    }

    @Override
    protected int height() {
        return 166;
    }

    // The Switch view keeps the L3 Switch's own (its list well and scroll track).
    @Override
    protected Identifier background() {
        return switchView() ? switchPanel.background() : BACKGROUND;
    }

    private boolean switchView() {
        return tab == Tab.SWITCH && switchPanel != null;
    }

    // Which bank of real slots shows: the page's, none elsewhere.
    private void applyWindow() {
        switch (tab) {
            case CAGES -> screen.getMenu().setSlotWindow(true, ChassisLayout.CAGE_PAGE, 1);
            case DRIVES -> screen.getMenu().setSlotWindow(true, ChassisLayout.BAY_PAGE, 1);
            case FAB -> screen.getMenu().setSlotWindow(true, ChassisLayout.SCHEMATIC_PAGE, 1);
            default -> screen.getMenu().setSlotWindow(false, 0, 0);
        }
    }

    @Override
    protected void init() {
        applyWindow();
        if (switchPanel != null) {
            switchPanel.init();
        }
    }

    @Override
    protected void removed() {
        screen.getMenu().resetSlotWindow();
        if (switchPanel != null) {
            switchPanel.removed();
        }
    }

    @Override
    protected void tick() {
        if (switchPanel != null) {
            switchPanel.tick();
        }
    }

    private void select(Tab next) {
        tab = next;
        selected = -1;
        applyWindow();
    }

    // --- Data ---

    private record BayView(ChassisLayout.Bay bay, @Nullable ChassisPart part, String code, RackDeviceInfo.Status status, String detail, boolean lit) {}

    private List<BayView> bays(@Nullable ValueInput data) {
        List<BayView> views = new ArrayList<>();
        List<ValueInput> rows = new ArrayList<>();
        if (data != null) {
            data.childrenListOrEmpty("bays").forEach(rows::add);
        }
        for (int i = 0; i < layout.bays().size(); i++) {
            ChassisLayout.Bay bay = layout.bays().get(i);
            if (i < rows.size()) {
                ValueInput row = rows.get(i);
                views.add(new BayView(bay, ChassisPart.byOrdinal(row.getIntOr("part", -1)), row.getStringOr("code", "*EMPTY"),
                        RackDeviceInfo.Status.byId(row.getIntOr("status", 1)), row.getStringOr("detail", ""), row.getBooleanOr("lit", false)));
            } else {
                views.add(new BayView(bay, null, "*EMPTY", RackDeviceInfo.Status.OFFLINE, "", false));
            }
        }
        return views;
    }

    private static Component partName(@Nullable ChassisPart part) {
        return part == null ? Component.translatable("hud.encodedlogistics.chassis.empty_bay") : new ItemStack(ModItems.chassisPart(part).get()).getHoverName();
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("gui.encodedlogistics.chassis." + key, args).getString();
    }

    private static String code(String code) {
        return Component.translatable("hud.encodedlogistics.chassis.code." + code.substring(1).toLowerCase(Locale.ROOT)).getString();
    }

    // --- Drawing ---

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (switchView()) {
            switchPanel.extractBackground(graphics, mouseX, mouseY, partialTick);
            return;
        }
        int left = screen.left(), top = screen.top();
        tabStrip.extractBackground(graphics, font(), left, top, tabs.indexOf(tab));
        ValueInput data = data();
        switch (tab) {
            case FRONT, REAR -> face(graphics, data, left, top, tab == Tab.REAR, mouseX, mouseY);
            case CAGES, DRIVES, FAB -> slotFrames(graphics, data, left, top);
            case CHANNELS -> graphics.fill(left + CH_X, top + FACE_Y + 10, left + RIGHT, top + FACE_Y + 11, SEPARATOR);
            case SPANS -> graphics.fill(left + 12, top + SPAN_TABLE_Y + 10, left + RIGHT, top + SPAN_TABLE_Y + 11, SEPARATOR);
            default -> {
            }
        }
    }

    // A sunken well (the rack panels' list well: dark top and left rim, light bottom and right), screen coordinates.
    private static void well(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF707070);
        graphics.fill(x, y, x + w - 1, y + h - 1, 0xFF161616);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF1F1F1F);
        graphics.fill(x + 2, y + 2, x + w - 1, y + h - 1, 0xFF2A2A2A);
    }

    // The chassis's face at 1:1 in its well: its sheet's face, each part's faceplate (lit), a blank in each empty rear
    // bay (the back sheet has none drawn), the hovered and picked bays outlined.
    private void face(GuiGraphicsExtractor graphics, @Nullable ValueInput data, int left, int top, boolean rear, int mouseX, int mouseY) {
        int height = 8 * type.size(), sheet = type.sheetHeight();
        int x0 = left + FACE_X, y0 = top + FACE_Y;
        well(graphics, x0 - 2, y0 - 2, FACE_W + 4, height + 4);
        graphics.blit(RenderPipelines.GUI_TEXTURED, type.texture(""), x0, y0, FACE_U, rear ? type.backOrigin() : 0.0F, FACE_W, height, 128, sheet);
        for (BayView view : bays(data)) {
            ChassisLayout.Bay bay = view.bay();
            if (bay.rear() != rear) {
                continue;
            }
            int x = x0 + bay.x() - FACE_U, y = y0 + bay.y();
            if (view.part() != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, view.part().texture(), x, y, 0.0F, 0.0F, bay.w(), bay.h(), 128, 64);
                if (view.lit()) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, EncodedLogistics.id("textures/block/rack_card/" + view.part().id() + "_on.png"), x, y, 0.0F,
                            0.0F, bay.w(), bay.h(), 128, 256);
                }
            }
            if (bay.slot() == selected) {
                RackElevationView.outline(graphics, x - 1, y - 1, bay.w() + 2, bay.h() + 2, SELECTED, false);
            } else if (PartScreens.over(mouseX, mouseY, x, y, bay.w(), bay.h())) {
                RackElevationView.outline(graphics, x - 1, y - 1, bay.w() + 2, bay.h() + 2, OUTLINE, false);
            }
        }
    }

    // The real slots' frames on a page (the slots draw their items over them), dimmed where they aren't in use.
    private void slotFrames(GuiGraphicsExtractor graphics, @Nullable ValueInput data, int left, int top) {
        List<BayView> views = bays(data);
        List<RackSlot> specs = type.slots();
        int page = tab == Tab.CAGES ? ChassisLayout.CAGE_PAGE : tab == Tab.DRIVES ? ChassisLayout.BAY_PAGE : ChassisLayout.SCHEMATIC_PAGE;
        for (int i = 0; i < specs.size(); i++) {
            RackSlot spec = specs.get(i);
            if (spec.scrollRow() != page) {
                continue;
            }
            boolean used = used(views, i);
            RackScreen.filterBox(graphics, left + spec.x() - 1, top + spec.y() - 1, 18, used ? SLOT_BORDER : 0xFF2E2E2E);
        }
    }

    // Whether a page slot is in use: a cage under an Uplink Card, a drive bay behind a Storage Blade, a schematic with a
    // Fabrication Blade in.
    private boolean used(List<BayView> views, int slot) {
        if (tab == Tab.CAGES) {
            int line = (slot - ChassisLayout.SWITCH_CAGES_FROM) / ChassisLayout.CAGES_PER_CARD;
            BayView view = line + 2 < views.size() ? views.get(line + 2) : null;
            return view != null && view.part() == ChassisPart.UPLINK_CARD;
        }
        if (tab == Tab.DRIVES) {
            int blade = slot / ChassisLayout.DRIVES_PER_BLADE;
            return blade < views.size() && views.get(blade).part() == ChassisPart.STORAGE_BLADE;
        }
        return views.stream().anyMatch(view -> view.part() == ChassisPart.FABRICATION_BLADE);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (switchView()) {
            switchPanel.extractLabels(graphics, mouseX, mouseY);
            return;
        }
        tabStrip.extractLabels(graphics, font(), tabs.indexOf(tab));
        ValueInput data = data();
        if (data == null) {
            return;
        }
        switch (tab) {
            case FRONT, REAR -> {
                column(graphics, data);
                detail(graphics, data);
            }
            case CHANNELS -> channels(graphics, data);
            case SPANS -> spans(graphics, data);
            case CAGES -> cages(graphics, data);
            case DRIVES -> drives(graphics, data);
            case FAB -> fab(graphics, data);
            default -> {
            }
        }
    }

    // A label over its value in the figures column, both cut to the column.
    private void pair(GuiGraphicsExtractor graphics, int y, String label, String value, int color) {
        graphics.text(font(), font().plainSubstrByWidth(label, RIGHT - COLUMN_X), COLUMN_X, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), font().plainSubstrByWidth(value, RIGHT - COLUMN_X), COLUMN_X, y + 9, color, false);
    }

    // Text wrapped to a width, LINE apart, at most max lines; the lines drawn.
    private int wrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int max, int color) {
        List<FormattedCharSequence> lines = font().split(Component.literal(text), width);
        for (int i = 0; i < lines.size() && i < max; i++) {
            graphics.text(font(), lines.get(i), x, y + i * LINE, color, false);
        }
        return Math.min(lines.size(), max);
    }

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(font(), text, right - font().width(text), y, color, false);
    }

    // The figures beside the face (as the previews have them), PAIR apart from COLUMN_Y.
    private void column(GuiGraphicsExtractor graphics, ValueInput data) {
        int y = COLUMN_Y;
        if (type == RackDeviceType.CHASSIS_SWITCH) {
            List<BayView> views = bays(data);
            for (int i = 0; i < 2; i++) {
                BayView sup = views.get(i);
                String state = sup.part() == null && !sup.code().equals("*FAILED") ? "-" : code(sup.code()).toLowerCase(Locale.ROOT);
                pair(graphics, y, tr("sup", sup.bay().label()), state, sup.code().equals("*FAILED") ? RackScreen.ERROR
                        : sup.code().equals("*ACTIVE") ? RackScreen.ACCENT : RackScreen.TEXT);
                y += PAIR;
            }
            pair(graphics, y, tr("lanes"), Integer.toString(data.getIntOr("lanes", 0)), RackScreen.TEXT);
            y += PAIR;
            int uplinks = data.getIntOr("uplinks", 0), total = data.getIntOr("uplinks_total", 0);
            pair(graphics, y, tr("uplinks"), uplinks + "/" + total, uplinks == 0 && total > 0 ? RackScreen.ERROR : RackScreen.TEXT);
            y += PAIR;
            int psus = data.getIntOr("psus", 0);
            pair(graphics, y, tr("psu"), psus + "/4", psus == 0 ? RackScreen.ERROR : psus < 2 ? RackScreen.AMBER : RackScreen.TEXT);
        } else if (type == RackDeviceType.BLADE_ENCLOSURE) {
            pair(graphics, y, tr("threads"), Integer.toString(data.getIntOr("threads", 0)), RackScreen.TEXT);
            y += PAIR;
            pair(graphics, y, tr("buffer"), MemoryServerDevice.memory(data.getLongOr("buffer", 0)), RackScreen.TEXT);
            y += PAIR;
            pair(graphics, y, tr("storage"), tr("bays", data.getIntOr("storage_bays", 0)), RackScreen.TEXT);
            y += PAIR;
            pair(graphics, y, tr("lanes"), Integer.toString(data.getIntOr("lanes", 0)), data.getIntOr("lanes", 0) == 0 ? RackScreen.ERROR : RackScreen.TEXT);
            y += PAIR;
            pair(graphics, y, tr("blades"), data.getIntOr("blades", 0) + "/16", RackScreen.TEXT);
        } else {
            boolean protect = data.getStringOr("active", "working").equals("protect") && data.getBooleanOr("protection", false);
            pair(graphics, y, tr("path"), tr(data.getBooleanOr("paired", false) ? protect ? "protect" : "working" : "unpaired"),
                    protect ? RackScreen.AMBER : RackScreen.TEXT);
            y += PAIR;
            pair(graphics, y, tr("channels"), data.getIntOr("channels_up", 0) + "/" + data.getIntOr("max_channels", 0), RackScreen.TEXT);
            y += PAIR;
            pair(graphics, y, tr("quality"), data.getIntOr("quality", 0) + "%", RackScreen.TEXT);
            y += PAIR;
            int controllers = data.getIntOr("controllers", 0);
            pair(graphics, y, tr("ctrl"), controllers >= 2 ? "1 + 1" : Integer.toString(controllers), controllers == 0 ? RackScreen.ERROR
                    : controllers < 2 ? RackScreen.AMBER : RackScreen.TEXT);
            // The last switch, under the (6U) face's well, as wide as the face.
            String switched = data.getStringOr("switched", "");
            if (!switched.isEmpty()) {
                int under = FACE_Y + 8 * type.size() + 5;
                graphics.text(font(), font().plainSubstrByWidth(tr("switched", switched), FACE_W), FACE_X, under, RackScreen.AMBER, false);
                graphics.text(font(), font().plainSubstrByWidth(data.getStringOr("reason", ""), FACE_W), FACE_X, under + LINE, RackScreen.TEXT_MUTED, false);
            }
        }
    }

    // Under the face and the column: the picked part's name and bay, state and detail, and its setting (wrapped,
    // clickable); or, with none picked, how to fit and pick parts.
    private void detail(GuiGraphicsExtractor graphics, ValueInput data) {
        BayView view = bays(data).stream().filter(v -> v.bay().slot() == selected).findFirst().orElse(null);
        int y = DETAIL_Y, width = RIGHT - FACE_X;
        if (view == null) {
            wrapped(graphics, tr("hint"), FACE_X, y, width, 4, RackScreen.TEXT_DISABLED);
            return;
        }
        graphics.text(font(), font().plainSubstrByWidth(view.bay().label() + "  " + partName(view.part()).getString(), width), FACE_X, y, RackScreen.TEXT,
                false);
        String state = code(view.code());
        graphics.text(font(), state, FACE_X, y + LINE, RackScreen.statusColor(view.status()), false);
        int detailX = FACE_X + font().width(state) + 6;
        graphics.text(font(), font().plainSubstrByWidth(view.detail(), RIGHT - detailX), detailX, y + LINE, RackScreen.TEXT_MUTED, false);
        String setting = setting(view, data);
        if (setting != null) {
            wrapped(graphics, setting, FACE_X, y + 2 * LINE, width, 2, RackScreen.ACCENT);
        }
    }

    // The picked part's setting line (a transponder's channel buttons, a Protection Card's revertive switch), or null.
    private static @Nullable String setting(BayView view, ValueInput data) {
        if (view.part() == ChassisPart.TRANSPONDER_CARD) {
            return tr("channel_buttons");
        }
        if (view.part() == ChassisPart.PROTECTION_CARD) {
            return tr(data.getBooleanOr("revertive", true) ? "revertive_on" : "revertive_off");
        }
        return null;
    }

    // Number, network, far end (the two names sharing the room left of the up column), up.
    private void channels(GuiGraphicsExtractor graphics, ValueInput data) {
        int y = FACE_Y;
        int up = RIGHT - Math.max(Math.max(font().width(tr("col.up")), font().width(tr("no_mux"))), font().width(tr("no"))) - 4;
        graphics.text(font(), tr("col.ch"), CH_X, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), font().plainSubstrByWidth(tr("col.network"), FAR_X - NETWORK_X - 4), NETWORK_X, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), font().plainSubstrByWidth(tr("col.far"), up - FAR_X), FAR_X, y, RackScreen.TEXT_MUTED, false);
        right(graphics, tr("col.up"), RIGHT, y, RackScreen.TEXT_MUTED);
        List<ValueInput> rows = new ArrayList<>();
        data.childrenListOrEmpty("channel_list").forEach(rows::add);
        for (int i = 0; i < rows.size() && i < 10; i++) {
            ValueInput row = rows.get(i);
            int rowY = y + 14 + i * ROW;
            boolean on = row.getBooleanOr("up", false);
            graphics.text(font(), Integer.toString(row.getIntOr("number", 0)), CH_X, rowY, RackScreen.TEXT, false);
            graphics.text(font(), font().plainSubstrByWidth(row.getStringOr("network", "-"), FAR_X - NETWORK_X - 4), NETWORK_X, rowY, RackScreen.TEXT, false);
            graphics.text(font(), font().plainSubstrByWidth(row.getStringOr("far", "-"), up - FAR_X), FAR_X, rowY, RackScreen.TEXT, false);
            String state = !row.getBooleanOr("carried", true) ? tr("no_mux") : on ? row.getIntOr("lanes", 0) + "" : tr("no");
            right(graphics, state, RIGHT, rowY, on ? RackScreen.ACCENT : RackScreen.ERROR);
        }
        if (rows.isEmpty()) {
            graphics.text(font(), tr("no_channels"), CH_X, y + 14, RackScreen.TEXT_DISABLED, false);
        }
    }

    // The span, then the working and protect paths side by side (a row for each figure), whether it's protected, and
    // pairing.
    private void spans(GuiGraphicsExtractor graphics, ValueInput data) {
        boolean paired = data.getBooleanOr("paired", false);
        String label = tr("span");
        graphics.text(font(), label, 12, FACE_Y, RackScreen.TEXT_MUTED, false);
        int valueX = 12 + font().width(label) + 6;
        graphics.text(font(), font().plainSubstrByWidth(paired ? data.getStringOr("span", "-") : tr("unpaired"), RIGHT - valueX), valueX, FACE_Y,
                paired ? RackScreen.TEXT : RackScreen.TEXT_DISABLED, false);
        List<ValueInput> paths = new ArrayList<>();
        data.childrenListOrEmpty("paths").forEach(paths::add);
        int[] rights = { WORKING_RIGHT, PROTECT_RIGHT };
        // The rows' labels: up to the working column's widest value.
        int labelW = WORKING_RIGHT - 12 - font().width("100%") - 6;
        for (int r = 0; r < SPAN_ROWS.length; r++) {
            graphics.text(font(), font().plainSubstrByWidth(tr("col." + SPAN_ROWS[r]), labelW), 12, SPAN_TABLE_Y + 14 + r * ROW, RackScreen.TEXT_MUTED, false);
        }
        for (int p = 0; p < paths.size() && p < rights.length; p++) {
            ValueInput path = paths.get(p);
            boolean up = path.getBooleanOr("up", false), active = path.getBooleanOr("active", false);
            int column = rights[p];
            right(graphics, tr("col." + path.getStringOr("name", "working")) + (active ? " *" : ""), column, SPAN_TABLE_Y, active ? RackScreen.ACCENT
                    : RackScreen.TEXT_MUTED);
            String[] values = up ? new String[] { Integer.toString(path.getIntOr("length", 0)), Integer.toString(path.getIntOr("loss", 0)),
                    Integer.toString(path.getIntOr("lanes", 0)), path.getIntOr("quality", 0) + "%", path.getBooleanOr("bridged", false) ? tr("yes") : "-" }
                    : new String[] { tr("down"), "-", "-", "-", "-" };
            for (int r = 0; r < values.length; r++) {
                right(graphics, values[r], column, SPAN_TABLE_Y + 14 + r * ROW, !up ? r == 0 ? RackScreen.ERROR : RackScreen.TEXT_DISABLED : RackScreen.TEXT);
            }
        }
        wrapped(graphics, tr(data.getBooleanOr("protection", false) ? data.getBooleanOr("revertive", true) ? "revertive_yes" : "revertive_no"
                : "no_protection"), 12, PROTECTION_Y, RIGHT - 12, 2, RackScreen.TEXT_MUTED);
        if (paired) {
            graphics.text(font(), tr("unpair"), 12, PAIR_Y, RackScreen.ERROR, false);
        } else {
            wrapped(graphics, tr("pair_hint"), 12, PAIR_Y, RIGHT - 12, 2, RackScreen.TEXT_DISABLED);
        }
    }

    // Each line slot's group of cages under its label; the hint under both rows.
    private void cages(GuiGraphicsExtractor graphics, ValueInput data) {
        List<BayView> views = bays(data);
        for (int line = 0; line < 8; line++) {
            BayView view = views.get(line + 2);
            boolean uplink = view.part() == ChassisPart.UPLINK_CARD;
            graphics.text(font(), "L" + (line + 1), ChassisLayout.cageX(line), ChassisLayout.cageY(line) - 10, uplink ? RackScreen.TEXT : RackScreen.TEXT_DISABLED,
                    false);
        }
        wrapped(graphics, tr("cages_hint"), 12, ChassisLayout.cageY(4) + 2 * 18 + 6, RIGHT - 12, 2, RackScreen.TEXT_DISABLED);
    }

    // How many drive bays are open over the grid, whose bays are which under it.
    private void drives(GuiGraphicsExtractor graphics, ValueInput data) {
        graphics.text(font(), tr("drives_hint", data.getIntOr("storage_bays", 0)), 12, FACE_Y - 1, RackScreen.TEXT_MUTED, false);
        wrapped(graphics, tr("drives_note"), 12, ChassisLayout.driveY(8, 1) + 18 + 6, RIGHT - 12, 3, RackScreen.TEXT_DISABLED);
    }

    // The crafts running over the grid, what the schematics are for under it.
    private void fab(GuiGraphicsExtractor graphics, ValueInput data) {
        boolean any = bays(data).stream().anyMatch(view -> view.part() == ChassisPart.FABRICATION_BLADE);
        graphics.text(font(), tr("crafts", data.getIntOr("crafts", 0)), 12, FACE_Y - 1, RackScreen.TEXT_MUTED, false);
        wrapped(graphics, tr(any ? "fab_hint" : "fab_none"), 12, 46 + 3 * 18 + 8, RIGHT - 12, 3, any ? RackScreen.TEXT_MUTED : RackScreen.TEXT_DISABLED);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (switchView()) {
            switchPanel.extractTooltip(graphics, mouseX, mouseY);
            return;
        }
        if (tab != Tab.FRONT && tab != Tab.REAR) {
            return;
        }
        BayView view = hovered(mouseX - screen.left(), mouseY - screen.top());
        if (view == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(view.bay().label() + "  ").append(partName(view.part())));
        lines.add(Component.literal(code(view.code())).withColor(RackScreen.statusColor(view.status())));
        if (!view.detail().isEmpty()) {
            lines.add(Component.literal(view.detail()).withColor(RackScreen.TEXT_MUTED));
        }
        lines.add(Component.translatable(view.part() == null ? "gui.encodedlogistics.chassis.empty_hint" : "gui.encodedlogistics.chassis.part_hint")
                .withColor(RackScreen.TEXT_DISABLED));
        Tooltips.show(graphics, font(), lines, mouseX, mouseY);
    }

    private @Nullable BayView hovered(double x, double y) {
        boolean rear = tab == Tab.REAR;
        ChassisLayout.Bay bay = layout.at(rear, x - FACE_X + FACE_U, y - FACE_Y);
        if (bay == null) {
            return null;
        }
        for (BayView view : bays(data())) {
            if (view.bay() == bay) {
                return view;
            }
        }
        return null;
    }

    // --- Input ---

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        if (switchView()) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT && switchPanel.overBackTab(x, y)) {
                select(Tab.FRONT);
                return true;
            }
            return switchPanel.mouseClicked(x, y, button, shift);
        }
        int clicked = tabStrip.at(font(), x, y);
        if (button == InputConstants.MOUSE_BUTTON_LEFT && clicked >= 0) {
            select(tabs.get(clicked));
            return true;
        }
        ValueInput data = data();
        if (tab == Tab.FRONT || tab == Tab.REAR) {
            BayView view = hovered(x, y);
            if (view != null) {
                ItemStack carried = screen.carried();
                if (view.part() == null && !carried.isEmpty() && view.bay().accepts(carried)) {
                    send(Chassis.ACTION_INSTALL, view.bay().slot(), "");
                } else if (view.part() != null && shift) {
                    send(Chassis.ACTION_REMOVE, view.bay().slot(), "");
                    selected = -1;
                } else {
                    selected = selected == view.bay().slot() ? -1 : view.bay().slot();
                }
                return true;
            }
            return data != null && clickDetail(data, x, y, button);
        }
        if (tab == Tab.SPANS && data != null && data.getBooleanOr("paired", false)) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT && y >= PAIR_Y && y < PAIR_Y + 9 && x >= 12 && x < 12 + font().width(tr("unpair"))) {
                send(OpticalTransportShelfDevice.ACTION_UNPAIR, 0, "");
                return true;
            }
        }
        return false;
    }

    // The picked part's settings (its setting line, all its wrapped lines): a transponder's channel (left: up, right:
    // down, middle: no network), a Protection Card's revertive switch.
    private boolean clickDetail(ValueInput data, double x, double y, int button) {
        BayView view = bays(data).stream().filter(v -> v.bay().slot() == selected).findFirst().orElse(null);
        String setting = view != null ? setting(view, data) : null;
        if (setting == null) {
            return false;
        }
        int settingY = DETAIL_Y + 2 * LINE, lines = Math.min(2, font().split(Component.literal(setting), RIGHT - FACE_X).size());
        if (y < settingY || y >= settingY + lines * LINE || x < FACE_X || x >= RIGHT) {
            return false;
        }
        if (view.part() == ChassisPart.TRANSPONDER_CARD) {
            if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                send(OpticalTransportShelfDevice.ACTION_CLEAR_NETWORK, selected, "");
            } else {
                send(button == InputConstants.MOUSE_BUTTON_RIGHT ? OpticalTransportShelfDevice.ACTION_CHANNEL_DOWN : OpticalTransportShelfDevice.ACTION_CHANNEL_UP,
                        selected, "");
            }
            return true;
        }
        if (view.part() == ChassisPart.PROTECTION_CARD) {
            send(OpticalTransportShelfDevice.ACTION_REVERTIVE, selected, "");
            return true;
        }
        return false;
    }

    @Override
    protected boolean mouseScrolled(double x, double y, double amount) {
        return switchView() && switchPanel.mouseScrolled(x, y, amount);
    }

    @Override
    protected boolean mouseDragged(double x, double y) {
        return switchView() && switchPanel.mouseDragged(x, y);
    }

    @Override
    protected void mouseReleased() {
        if (switchView()) {
            switchPanel.mouseReleased();
        }
    }

    @Override
    protected boolean keyPressed(KeyEvent event) {
        return switchView() && switchPanel.keyPressed(event);
    }

    @Override
    protected List<RackScreen.GhostTarget> ghostTargets() {
        return switchView() ? switchPanel.ghostTargets() : List.of();
    }
}
