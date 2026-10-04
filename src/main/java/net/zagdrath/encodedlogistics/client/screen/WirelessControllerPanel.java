/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;

// The Wireless Controller's panel (screens/rack/wireless_controller.json; gui/rack/wireless_controller.png, the L3
// Switch's layout): two tabs over a list of eight rows. APs: each Access Point on its network - a dot lit while it's
// online, its name, and the clients it carries ("6 / 8 clients", "8 / 8 - full", or "no uplink"), then the totals.
// Linked: its Wireless Bridges and Ports, then the Handheld Terminals linked to it - a dot lit while connected, the
// name, the kind (bridge, ingress, egress, handheld) and an X to unlink it - then how many are linked and connected. The
// wheel scrolls a longer list.
public class WirelessControllerPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/wireless_controller.png");
    private static final Identifier TAB_ACTIVE = EncodedLogistics.id("rack/switch/tab_active"), TAB_INACTIVE = EncodedLogistics.id("rack/switch/tab_inactive"),
            DOT_ON = EncodedLogistics.id("hud/dot_online"), DOT_OFF = EncodedLogistics.id("hud/dot_offline"),
            UNLINK = EncodedLogistics.id("common/cancel_small");
    private static final int TABS_X = 8, TABS_Y = 17, TAB_STEP = 53, TAB_W = 52, LIST_X = 10, LIST_Y = 31, ROWS = 8, ROW_H = 14, WHERE_RIGHT = 130,
            UNLINK_RIGHT = 152, SUMMARY_X = 12, SUMMARY_Y = 148;
    private static final String[] TABS = { "gui.encodedlogistics.wireless.tab.aps", "gui.encodedlogistics.wireless.tab.linked" };

    // A row of either tab: its dot, name (dimmed when off), what's on its right, and how it's unlinked (action, index;
    // -1 for an AP row).
    private record Row(boolean lit, String name, Component right, int action, int index) {}

    private int tab, scroll;

    public WirelessControllerPanel(RackScreen screen) {
        super(screen);
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        ValueInput data = data();
        if (data == null) {
            return rows;
        }
        if (tab == 0) {
            for (ValueInput ap : data.childrenListOrEmpty("aps")) {
                boolean online = ap.getBooleanOr("online", false);
                int clients = ap.getIntOr("clients", 0), capacity = ap.getIntOr("capacity", 8);
                Component right = !online ? Component.translatable("gui.encodedlogistics.wireless.ap.no_uplink")
                        : clients >= capacity ? Component.translatable("gui.encodedlogistics.wireless.ap.full", clients, capacity)
                                : Component.translatable("gui.encodedlogistics.wireless.ap.clients", clients, capacity);
                rows.add(new Row(online, ap.getStringOr("name", "?"), right, -1, -1));
            }
            return rows;
        }
        int i = 0;
        for (ValueInput client : data.childrenListOrEmpty("clients")) {
            rows.add(new Row(client.getBooleanOr("connected", false), client.getStringOr("name", "?"),
                    Component.translatable("gui.encodedlogistics.wireless.kind." + client.getStringOr("kind", "bridge")), WirelessControllerDevice.ACTION_UNLINK_CLIENT,
                    i++));
        }
        i = 0;
        for (ValueInput entry : data.childrenListOrEmpty("entries")) {
            rows.add(new Row(entry.getBooleanOr("connected", false), entry.getStringOr("name", "?"), Component.translatable("gui.encodedlogistics.wireless.kind.handheld"),
                    WirelessControllerDevice.ACTION_UNLINK, i++));
        }
        return rows;
    }

    private Component summary(List<Row> rows) {
        ValueInput data = data();
        if (data == null) {
            return Component.empty();
        }
        if (tab == 0) {
            long online = rows.stream().filter(Row::lit).count();
            return Component.translatable("gui.encodedlogistics.wireless.summary.aps", rows.size(), online, data.getIntOr("admitted", 0),
                    data.getIntOr("ap_slots", 0));
        }
        long connected = rows.stream().filter(Row::lit).count();
        return Component.translatable("gui.encodedlogistics.wireless.summary.linked", rows.size(), connected);
    }

    // --- Drawing ---

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        for (int i = 0; i < TABS.length; i++) {
            boolean active = i == tab;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, active ? TAB_ACTIVE : TAB_INACTIVE, x + TABS_X + i * TAB_STEP, y + TABS_Y, TAB_W,
                    active ? 13 : 12);
        }
        List<Row> rows = rows();
        scroll = Math.max(0, Math.min(scroll, rows.size() - ROWS));
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int rowY = y + LIST_Y + i * ROW_H;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, row.lit() ? DOT_ON : DOT_OFF, x + LIST_X + 2, rowY + 4, 5, 5);
            if (row.action() >= 0) {
                boolean over = PartScreens.over(mouseX, mouseY, x + UNLINK_RIGHT - 9, rowY + 2, 9, 9);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, UNLINK, x + UNLINK_RIGHT - 9, rowY + 2, 9, 9, over ? 0xFFFFFFFF : 0xFFB0B0B0);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < TABS.length; i++) {
            graphics.centeredText(font(), Component.translatable(TABS[i]), TABS_X + i * TAB_STEP + TAB_W / 2, TABS_Y + 3, i == tab ? RackScreen.TEXT : RackScreen.TEXT_MUTED);
        }
        List<Row> rows = rows();
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int rowY = LIST_Y + i * ROW_H + 3;
            int rightWidth = Math.min(font().width(row.right()), 80);
            graphics.text(font(), font().plainSubstrByWidth(row.right().getString(), 80), WHERE_RIGHT - rightWidth, rowY, RackScreen.TEXT_MUTED, false);
            int nameRoom = WHERE_RIGHT - rightWidth - 4 - (LIST_X + 10);
            graphics.text(font(), font().plainSubstrByWidth(row.name(), nameRoom), LIST_X + 10, rowY, row.lit() ? RackScreen.TEXT : RackScreen.TEXT_DISABLED, false);
        }
        if (rows.isEmpty()) {
            graphics.text(font(), Component.translatable(tab == 0 ? "gui.encodedlogistics.wireless.no_aps" : "gui.encodedlogistics.wireless.none_linked"),
                    LIST_X + 4, LIST_Y + 3, RackScreen.TEXT_DISABLED, false);
        }
        graphics.text(font(), summary(rows), SUMMARY_X, SUMMARY_Y, RackScreen.TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Row> rows = rows();
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            int rowY = screen.top() + LIST_Y + i * ROW_H;
            Row row = rows.get(scroll + i);
            if (row.action() >= 0 && PartScreens.over(mouseX, mouseY, screen.left() + UNLINK_RIGHT - 9, rowY + 2, 9, 9)) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.wireless.unlink"), mouseX, mouseY);
            } else if (PartScreens.over(mouseX, mouseY, screen.left() + LIST_X + 1, rowY + 3, 7, 7)) {
                graphics.setTooltipForNextFrame(Component.translatable(tab == 0 ? row.lit() ? "gui.encodedlogistics.wireless.ap.online"
                        : "gui.encodedlogistics.wireless.ap.offline" : row.lit() ? "gui.encodedlogistics.wireless.connected" : "gui.encodedlogistics.wireless.away"),
                        mouseX, mouseY);
            }
        }
    }

    // --- Input ---

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (x >= TABS_X + i * TAB_STEP && x < TABS_X + i * TAB_STEP + TAB_W && y >= TABS_Y && y < TABS_Y + 13) {
                tab = i;
                scroll = 0;
                return true;
            }
        }
        List<Row> rows = rows();
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int rowY = LIST_Y + i * ROW_H;
            if (row.action() >= 0 && x >= UNLINK_RIGHT - 9 && x < UNLINK_RIGHT && y >= rowY + 2 && y < rowY + 11) {
                send(row.action(), row.index(), "");
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean mouseScrolled(double x, double y, double amount) {
        if (x >= LIST_X && x < UNLINK_RIGHT + 8 && y >= LIST_Y && y < LIST_Y + ROWS * ROW_H) {
            scroll = Math.max(0, scroll - (int) Math.signum(amount));
            return true;
        }
        return false;
    }
}
