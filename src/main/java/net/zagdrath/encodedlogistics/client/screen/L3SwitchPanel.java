/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.device.L3SwitchDevice;

// The L3 Switch's panel (screens/rack/l3_switch.json): three tabs. Devices: the L2 list (lanes and segments). Routes:
// source segment -> destination segment and the filter (click a segment to step through them, the filter with an item to
// set it or empty-handed to clear it, right-click to remove; "+ Add route"). QoS: each rule's priority and the items it
// covers (click the priority to step through High / Normal / Low, the items with an item to set them; right-click to
// remove; "+ Add QoS rule").
public class L3SwitchPanel extends SwitchPanel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/l3_switch.png");
    private static final Identifier TAB_ACTIVE = EncodedLogistics.id("rack/switch/tab_active"), TAB_INACTIVE = EncodedLogistics.id("rack/switch/tab_inactive"),
            ARROW = EncodedLogistics.id("rack/router/arrow"), QOS = EncodedLogistics.id("rack/switch/qos_high");
    private static final int TABS_X = 8, TABS_Y = 17, TAB_STEP = 53, TAB_W = 52, LIST_X = 10, LIST_Y = 31, ROWS = 8, ROW_H = 14;
    private static final int SOURCE_X = 12, ARROW_X = 52, DEST_X = 70, FILTER_X = 130;
    private static final int KIND_X = 24, TARGET_X = 72, LEVEL_X = 128;
    private static final int ADD_X = 8, ADD_Y = 146, ADD_W = 160, ADD_H = 14;
    private static final String[] TABS = { "gui.encodedlogistics.l3.tab.devices", "gui.encodedlogistics.l3.tab.routes", "gui.encodedlogistics.l3.tab.qos" };
    private static final String[] LEVELS = { "high", "normal", "low" };

    private int tab;

    public L3SwitchPanel(RackScreen screen) {
        super(screen);
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
    }

    @Override
    protected int listY() {
        return LIST_Y;
    }

    @Override
    protected int rowHeight() {
        return ROW_H;
    }

    private @Nullable L3SwitchDevice l3() {
        return device(L3SwitchDevice.class);
    }

    private Component segment(int index) {
        List<String> segments = segments();
        return Component.literal(index < segments.size() ? segments.get(index) : "?");
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
        L3SwitchDevice l3 = l3();
        if (tab == 0) {
            extractDevices(graphics, mouseX, mouseY);
            return;
        }
        PartScreens.wideButton(graphics, font(), x + ADD_X, y + ADD_Y, ADD_W, ADD_H, Component.translatable(tab == 1 ? "gui.encodedlogistics.l3.add_route"
                : "gui.encodedlogistics.l3.add_qos"), l3 != null && (tab == 1 ? l3.routes().size() < L3SwitchDevice.MAX_ROUTES
                        : l3.qos().size() < L3SwitchDevice.MAX_QOS), mouseX, mouseY);
        if (l3 == null) {
            return;
        }
        if (tab == 1) {
            for (int i = 0; i < Math.min(ROWS, l3.routes().size()); i++) {
                int rowY = y + LIST_Y + i * ROW_H;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, x + ARROW_X, rowY + 1, 12, 12);
                item(graphics, l3.routes().get(i).filter(), x + FILTER_X, rowY + 1);
            }
        } else {
            for (int i = 0; i < Math.min(ROWS, l3.qos().size()); i++) {
                int rowY = y + LIST_Y + i * ROW_H;
                L3SwitchDevice.QosRule rule = l3.qos().get(i);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, QOS, x + LIST_X, rowY - 1, 16, 16,
                        rule.level() == ItemRouting.HIGH ? 0xFFFFFFFF : rule.level() == ItemRouting.NORMAL ? 0xFF8A8A8A : 0xFF4A4A4A);
                item(graphics, rule.filter(), x + TARGET_X, rowY + 1);
            }
        }
    }

    private static void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(0.75F, 0.75F);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < TABS.length; i++) {
            graphics.centeredText(font(), Component.translatable(TABS[i]), TABS_X + i * TAB_STEP + TAB_W / 2, TABS_Y + 3,
                    i == tab ? RackScreen.TEXT : RackScreen.TEXT_MUTED);
        }
        Component pool = poolText();
        graphics.text(font(), pool, 146 - font().width(pool), 5, RackScreen.TEXT_MUTED, false);
        L3SwitchDevice l3 = l3();
        if (tab == 0) {
            extractDeviceNames(graphics);
            return;
        }
        if (l3 == null) {
            return;
        }
        if (tab == 1) {
            for (int i = 0; i < Math.min(ROWS, l3.routes().size()); i++) {
                ItemRouting.Route route = l3.routes().get(i);
                int rowY = LIST_Y + i * ROW_H + 3;
                graphics.text(font(), font().plainSubstrByWidth(segment(route.source()).getString(), ARROW_X - SOURCE_X - 2), SOURCE_X, rowY,
                        RackScreen.TEXT, false);
                graphics.text(font(), font().plainSubstrByWidth(segment(route.dest()).getString(), FILTER_X - DEST_X - 2), DEST_X, rowY, RackScreen.TEXT,
                        false);
                if (route.filter().isEmpty()) {
                    graphics.text(font(), "*", FILTER_X + 4, rowY, RackScreen.TEXT_MUTED, false);
                }
            }
        } else {
            for (int i = 0; i < Math.min(ROWS, l3.qos().size()); i++) {
                L3SwitchDevice.QosRule rule = l3.qos().get(i);
                int rowY = LIST_Y + i * ROW_H + 3;
                graphics.text(font(), Component.translatable("gui.encodedlogistics.l3.qos.item_filter"), KIND_X, rowY, RackScreen.TEXT_MUTED, false);
                if (rule.filter().isEmpty()) {
                    graphics.text(font(), Component.translatable("gui.encodedlogistics.router.filter_any"), TARGET_X, rowY, RackScreen.TEXT, false);
                } else {
                    graphics.text(font(), font().plainSubstrByWidth(rule.filter().getHoverName().getString(), LEVEL_X - TARGET_X - 16), TARGET_X + 14, rowY,
                            RackScreen.TEXT, false);
                }
                graphics.text(font(), Component.translatable("gui.encodedlogistics.l3.qos." + LEVELS[rule.level()]), LEVEL_X, rowY,
                        rule.level() == ItemRouting.HIGH ? RackScreen.WARNING : RackScreen.TEXT, false);
            }
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (tab == 0) {
            super.extractTooltip(graphics, mouseX, mouseY);
            return;
        }
        int row = (mouseY - screen.top() - LIST_Y) / ROW_H;
        L3SwitchDevice l3 = l3();
        if (l3 == null || !screen.over(mouseX, mouseY, LIST_X, LIST_Y, 156, ROWS * ROW_H) || mouseY - screen.top() < LIST_Y) {
            return;
        }
        if (tab == 1 && row < l3.routes().size()) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.router.route_hint"), mouseX, mouseY);
        } else if (tab == 2 && row < l3.qos().size()) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.l3.qos_hint"), mouseX, mouseY);
        }
    }

    // --- Input ---

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        for (int i = 0; i < TABS.length; i++) {
            if (left && x >= TABS_X + i * TAB_STEP && x < TABS_X + i * TAB_STEP + TAB_W && y >= TABS_Y && y < TABS_Y + 13) {
                tab = i;
                return true;
            }
        }
        if (tab == 0) {
            return clickDevices(x, y, button);
        }
        if (left && x >= ADD_X && x < ADD_X + ADD_W && y >= ADD_Y && y < ADD_Y + ADD_H) {
            send(tab == 1 ? L3SwitchDevice.ACTION_ADD_ROUTE : L3SwitchDevice.ACTION_ADD_QOS, 0, "");
            return true;
        }
        L3SwitchDevice l3 = l3();
        int row = (int) Math.floor((y - LIST_Y) / ROW_H);
        if (l3 == null || y < LIST_Y || row >= ROWS || x < LIST_X || x >= LIST_X + 156) {
            return false;
        }
        if (tab == 1 && row < l3.routes().size()) {
            if (right) {
                send(L3SwitchDevice.ACTION_REMOVE_ROUTE, row, "");
            } else if (left && x < ARROW_X) {
                send(L3SwitchDevice.ACTION_CYCLE_SOURCE, row, "");
            } else if (left && x < FILTER_X - 2) {
                send(L3SwitchDevice.ACTION_CYCLE_DEST, row, "");
            } else if (left) {
                send(L3SwitchDevice.ACTION_SET_FILTER, row, "");
            }
            return true;
        }
        if (tab == 2 && row < l3.qos().size()) {
            if (right) {
                send(L3SwitchDevice.ACTION_REMOVE_QOS, row, "");
            } else if (left && x >= LEVEL_X) {
                send(L3SwitchDevice.ACTION_CYCLE_QOS_LEVEL, row, "");
            } else if (left) {
                send(L3SwitchDevice.ACTION_SET_QOS_FILTER, row, "");
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean mouseScrolled(double x, double y, double amount) {
        return tab == 0 && super.mouseScrolled(x, y, amount);
    }
}
