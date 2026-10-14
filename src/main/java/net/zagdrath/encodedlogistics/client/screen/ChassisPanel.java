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

// A modular chassis's panel (the optical handoff 4-6; previews/gui_*.png), in tabs:
//  Slots (Front, and Rear for the Blade Enclosure): the chassis's face at 1:1 with its parts in their bays, lit while
//   they work, and its figures in a column beside it. Click an empty bay holding a part that fits to put it in; click a
//   part to pick it (its state and settings show under the face: a transponder's channel, a Protection Card's revertive
//   switch); sneak-click to take it out.
//  Transport Shelf: Channels (each transponder's channel, network here and there, lanes, up) and Spans (the working
//   and protect paths' length, loss, lanes, quality; unpair).
//  Chassis Switch: Cages (each line slot's four Optical Transceiver cages, used by an Uplink Card there) and Switch -
//   the L3 Switch's own panel (ports, routes and QoS), with a Cards button back.
//  Blade Enclosure: Drives (two Storage Drive bays behind each Storage Blade) and Fab (the Fabrication Blades'
//   schematics).
public class ChassisPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/l3_switch.png");
    private static final int FACE_X = 12, FACE_Y = 36, COLUMN_X = 120, COLUMN_RIGHT = 168, DETAIL_Y = 122, ROW = 11;
    private static final int OUTLINE = 0xFF5CF0B8, SELECTED = 0xFFF5B23A, SLOT_BORDER = 0xFF5A5A5A;
    // The Switch view's way back to the cards (in the title row, left of the priority button).
    private static final int CARDS_X = 96, CARDS_Y = 3, CARDS_W = 34, CARDS_H = 12;

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
        this.switchPanel = type == RackDeviceType.CHASSIS_SWITCH ? new L3SwitchPanel(screen) : null;
    }

    @Override
    protected int height() {
        return 166;
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
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
            PartScreens.wideButton(graphics, font(), screen.left() + CARDS_X, screen.top() + CARDS_Y, CARDS_W, CARDS_H,
                    Component.translatable("gui.encodedlogistics.chassis.cards"), true, mouseX, mouseY);
            return;
        }
        int left = screen.left(), top = screen.top();
        tabStrip.extractBackground(graphics, font(), left, top, tabs.indexOf(tab));
        ValueInput data = data();
        switch (tab) {
            case FRONT, REAR -> face(graphics, data, left, top, tab == Tab.REAR, mouseX, mouseY);
            case CAGES, DRIVES, FAB -> slotFrames(graphics, data, left, top);
            default -> {
            }
        }
    }

    // The chassis's face at 1:1: its sheet's face, each part's faceplate (lit), the hovered and picked bays outlined.
    private void face(GuiGraphicsExtractor graphics, @Nullable ValueInput data, int left, int top, boolean rear, int mouseX, int mouseY) {
        int height = 8 * type.size(), sheet = type.sheetHeight();
        int x0 = left + FACE_X, y0 = top + FACE_Y;
        graphics.blit(RenderPipelines.GUI_TEXTURED, type.texture(""), x0, y0, 0.0F, rear ? type.backOrigin() : 0.0F, 104, height, 128, sheet);
        for (BayView view : bays(data)) {
            ChassisLayout.Bay bay = view.bay();
            if (bay.rear() != rear) {
                continue;
            }
            int x = x0 + bay.x(), y = y0 + bay.y();
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

    private void pair(GuiGraphicsExtractor graphics, int y, String label, String value, int color) {
        graphics.text(font(), label, COLUMN_X, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), font().plainSubstrByWidth(value, COLUMN_RIGHT - COLUMN_X), COLUMN_X, y + 9, color, false);
    }

    // The figures beside the face (as the previews have them).
    private void column(GuiGraphicsExtractor graphics, ValueInput data) {
        int y = FACE_Y - 2;
        if (type == RackDeviceType.CHASSIS_SWITCH) {
            List<BayView> views = bays(data);
            for (int i = 0; i < 2; i++) {
                BayView sup = views.get(i);
                String state = sup.part() == null && !sup.code().equals("*FAILED") ? "-" : code(sup.code()).toLowerCase(Locale.ROOT);
                pair(graphics, y, tr("sup", sup.bay().label()), state, sup.code().equals("*FAILED") ? RackScreen.ERROR
                        : sup.code().equals("*ACTIVE") ? RackScreen.ACCENT : RackScreen.TEXT);
                y += 2 * ROW;
            }
            pair(graphics, y, tr("lanes"), Integer.toString(data.getIntOr("lanes", 0)), RackScreen.TEXT);
            y += 2 * ROW;
            int uplinks = data.getIntOr("uplinks", 0), total = data.getIntOr("uplinks_total", 0);
            pair(graphics, y, tr("uplinks"), uplinks + "/" + total, uplinks == 0 && total > 0 ? RackScreen.ERROR : RackScreen.TEXT);
            y += 2 * ROW;
            int psus = data.getIntOr("psus", 0);
            pair(graphics, y, tr("psu"), psus + "/4", psus == 0 ? RackScreen.ERROR : psus < 2 ? RackScreen.AMBER : RackScreen.TEXT);
        } else if (type == RackDeviceType.BLADE_ENCLOSURE) {
            pair(graphics, y, tr("threads"), Integer.toString(data.getIntOr("threads", 0)), RackScreen.TEXT);
            y += 2 * ROW;
            pair(graphics, y, tr("buffer"), MemoryServerDevice.memory(data.getLongOr("buffer", 0)), RackScreen.TEXT);
            y += 2 * ROW;
            pair(graphics, y, tr("storage"), tr("bays", data.getIntOr("storage_bays", 0)), RackScreen.TEXT);
            y += 2 * ROW;
            pair(graphics, y, tr("lanes"), Integer.toString(data.getIntOr("lanes", 0)), data.getIntOr("lanes", 0) == 0 ? RackScreen.ERROR : RackScreen.TEXT);
            y += 2 * ROW;
            pair(graphics, y, tr("blades"), data.getIntOr("blades", 0) + "/16", RackScreen.TEXT);
        } else {
            boolean protect = data.getStringOr("active", "working").equals("protect") && data.getBooleanOr("protection", false);
            pair(graphics, y, tr("path"), tr(data.getBooleanOr("paired", false) ? protect ? "protect" : "working" : "unpaired"),
                    protect ? RackScreen.AMBER : RackScreen.TEXT);
            y += 2 * ROW;
            pair(graphics, y, tr("channels"), data.getIntOr("channels_up", 0) + "/" + data.getIntOr("max_channels", 0), RackScreen.TEXT);
            y += 2 * ROW;
            pair(graphics, y, tr("quality"), data.getIntOr("quality", 0) + "%", RackScreen.TEXT);
            y += 2 * ROW;
            int controllers = data.getIntOr("controllers", 0);
            pair(graphics, y, tr("ctrl"), controllers >= 2 ? "1 + 1" : Integer.toString(controllers), controllers == 0 ? RackScreen.ERROR
                    : controllers < 2 ? RackScreen.AMBER : RackScreen.TEXT);
            // The last switch, under the face.
            String switched = data.getStringOr("switched", "");
            if (!switched.isEmpty() && selected < 0) {
                graphics.text(font(), tr("switched", switched), FACE_X, FACE_Y + 8 * type.size() + 4, RackScreen.AMBER, false);
                graphics.text(font(), font().plainSubstrByWidth(data.getStringOr("reason", ""), 104), FACE_X, FACE_Y + 8 * type.size() + 15,
                        RackScreen.TEXT_MUTED, false);
            }
        }
    }

    // The picked part under the face: its name and bay, state and detail, and its settings.
    private void detail(GuiGraphicsExtractor graphics, ValueInput data) {
        BayView view = bays(data).stream().filter(v -> v.bay().slot() == selected).findFirst().orElse(null);
        if (view == null) {
            if (tab == Tab.FRONT && type != RackDeviceType.OPTICAL_TRANSPORT_SHELF || tab == Tab.REAR) {
                graphics.text(font(), tr("hint"), FACE_X, detailY(), RackScreen.TEXT_DISABLED, false);
            }
            return;
        }
        int y = detailY();
        graphics.text(font(), font().plainSubstrByWidth(view.bay().label() + "  " + partName(view.part()).getString(), 150), FACE_X, y, RackScreen.TEXT,
                false);
        graphics.text(font(), code(view.code()), FACE_X, y + ROW, RackScreen.statusColor(view.status()), false);
        graphics.text(font(), font().plainSubstrByWidth(view.detail(), 100), FACE_X + 48, y + ROW, RackScreen.TEXT_MUTED, false);
        if (view.part() == ChassisPart.TRANSPONDER_CARD) {
            graphics.text(font(), tr("channel_buttons"), FACE_X, y + 2 * ROW, RackScreen.ACCENT, false);
        } else if (view.part() == ChassisPart.PROTECTION_CARD) {
            graphics.text(font(), tr(data.getBooleanOr("revertive", true) ? "revertive_on" : "revertive_off"), FACE_X, y + 2 * ROW, RackScreen.ACCENT, false);
        }
    }

    private int detailY() {
        return type.size() >= 10 ? DETAIL_Y : FACE_Y + 8 * type.size() + 4;
    }

    private void channels(GuiGraphicsExtractor graphics, ValueInput data) {
        int y = FACE_Y;
        graphics.text(font(), tr("col.ch"), 12, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), tr("col.network"), 30, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), tr("col.far"), 84, y, RackScreen.TEXT_MUTED, false);
        right(graphics, tr("col.up"), 164, y, RackScreen.TEXT_MUTED);
        List<ValueInput> rows = new ArrayList<>();
        data.childrenListOrEmpty("channel_list").forEach(rows::add);
        for (int i = 0; i < rows.size() && i < 9; i++) {
            ValueInput row = rows.get(i);
            int rowY = y + 13 + i * ROW;
            boolean up = row.getBooleanOr("up", false);
            graphics.text(font(), Integer.toString(row.getIntOr("number", 0)), 12, rowY, RackScreen.TEXT, false);
            graphics.text(font(), font().plainSubstrByWidth(row.getStringOr("network", "-"), 52), 30, rowY, RackScreen.TEXT, false);
            graphics.text(font(), font().plainSubstrByWidth(row.getStringOr("far", "-"), 50), 84, rowY, RackScreen.TEXT, false);
            String state = !row.getBooleanOr("carried", true) ? tr("no_mux") : up ? row.getIntOr("lanes", 0) + "" : tr("no");
            right(graphics, state, 164, rowY, up ? RackScreen.ACCENT : RackScreen.ERROR);
        }
        if (rows.isEmpty()) {
            graphics.text(font(), tr("no_channels"), 12, y + 13, RackScreen.TEXT_DISABLED, false);
        }
    }

    private void spans(GuiGraphicsExtractor graphics, ValueInput data) {
        int y = FACE_Y;
        graphics.text(font(), font().plainSubstrByWidth(tr("span", data.getStringOr("span", "-")), 150), 12, y, RackScreen.TEXT, false);
        y += 14;
        graphics.text(font(), tr("col.path"), 12, y, RackScreen.TEXT_MUTED, false);
        right(graphics, tr("col.length"), 84, y, RackScreen.TEXT_MUTED);
        right(graphics, tr("col.loss"), 108, y, RackScreen.TEXT_MUTED);
        right(graphics, tr("col.lanes"), 134, y, RackScreen.TEXT_MUTED);
        right(graphics, tr("col.quality"), 164, y, RackScreen.TEXT_MUTED);
        int i = 0;
        for (ValueInput path : data.childrenListOrEmpty("paths")) {
            int rowY = y + 13 + i++ * ROW;
            boolean up = path.getBooleanOr("up", false), active = path.getBooleanOr("active", false);
            String name = tr(path.getStringOr("name", "working")) + (active ? " *" : "") + (path.getBooleanOr("bridged", false) ? " (B)" : "");
            graphics.text(font(), name, 12, rowY, active ? RackScreen.ACCENT : RackScreen.TEXT, false);
            if (!up) {
                right(graphics, tr("down"), 164, rowY, RackScreen.ERROR);
                continue;
            }
            right(graphics, Integer.toString(path.getIntOr("length", 0)), 84, rowY, RackScreen.TEXT);
            right(graphics, Integer.toString(path.getIntOr("loss", 0)), 108, rowY, RackScreen.TEXT);
            right(graphics, Integer.toString(path.getIntOr("lanes", 0)), 134, rowY, RackScreen.TEXT);
            right(graphics, path.getIntOr("quality", 0) + "%", 164, rowY, RackScreen.TEXT);
        }
        y += 13 + 3 * ROW;
        graphics.text(font(), tr(data.getBooleanOr("protection", false) ? data.getBooleanOr("revertive", true) ? "revertive_yes" : "revertive_no"
                : "no_protection"), 12, y, RackScreen.TEXT_MUTED, false);
        if (data.getBooleanOr("paired", false)) {
            graphics.text(font(), tr("unpair"), 12, y + 2 * ROW, RackScreen.ERROR, false);
        } else {
            graphics.text(font(), tr("pair_hint"), 12, y + 2 * ROW, RackScreen.TEXT_DISABLED, false);
        }
    }

    private void cages(GuiGraphicsExtractor graphics, ValueInput data) {
        List<BayView> views = bays(data);
        for (int line = 0; line < 8; line++) {
            BayView view = views.get(line + 2);
            boolean uplink = view.part() == ChassisPart.UPLINK_CARD;
            graphics.text(font(), "L" + (line + 1), ChassisLayout.cageX(line), ChassisLayout.cageY(line) - 10, uplink ? RackScreen.TEXT : RackScreen.TEXT_DISABLED,
                    false);
        }
        graphics.text(font(), tr("cages_hint"), 12, 140, RackScreen.TEXT_DISABLED, false);
    }

    private void drives(GuiGraphicsExtractor graphics, ValueInput data) {
        graphics.text(font(), tr("drives_hint", data.getIntOr("storage_bays", 0)), 12, FACE_Y - 2, RackScreen.TEXT_MUTED, false);
    }

    private void fab(GuiGraphicsExtractor graphics, ValueInput data) {
        boolean any = bays(data).stream().anyMatch(view -> view.part() == ChassisPart.FABRICATION_BLADE);
        graphics.text(font(), tr(any ? "fab_hint" : "fab_none"), 12, FACE_Y - 2, any ? RackScreen.TEXT_MUTED : RackScreen.TEXT_DISABLED, false);
        graphics.text(font(), tr("crafts", data.getIntOr("crafts", 0)), 12, 110, RackScreen.TEXT_MUTED, false);
    }

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(font(), text, right - font().width(text), y, color, false);
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
        graphics.setComponentTooltipForNextFrame(font(), lines, mouseX, mouseY);
    }

    private @Nullable BayView hovered(double x, double y) {
        boolean rear = tab == Tab.REAR;
        ChassisLayout.Bay bay = layout.at(rear, x - FACE_X, y - FACE_Y);
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
            if (button == InputConstants.MOUSE_BUTTON_LEFT && PartScreens.over(x, y, CARDS_X, CARDS_Y, CARDS_W, CARDS_H)) {
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
            int unpairY = FACE_Y + 14 + 13 + 3 * ROW + 2 * ROW;
            if (button == InputConstants.MOUSE_BUTTON_LEFT && y >= unpairY && y < unpairY + 9 && x >= 12 && x < 12 + font().width(tr("unpair"))) {
                send(OpticalTransportShelfDevice.ACTION_UNPAIR, 0, "");
                return true;
            }
        }
        return false;
    }

    // The picked part's settings: a transponder's channel (left: up, right: down), a Protection Card's revertive switch.
    private boolean clickDetail(ValueInput data, double x, double y, int button) {
        BayView view = bays(data).stream().filter(v -> v.bay().slot() == selected).findFirst().orElse(null);
        int settingY = detailY() + 2 * ROW;
        if (view == null || y < settingY || y >= settingY + 9 || x < FACE_X || x > FACE_X + 150) {
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
