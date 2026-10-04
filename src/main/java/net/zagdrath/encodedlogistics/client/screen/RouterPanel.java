/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// The Router's panel (screens/rack/router.json): its networks (its own first; a green dot when it's reachable; click a
// name to rename it, right-click a linked one to unlink it), its routes (click the source or the destination to step
// through the networks, the filter box to edit the route's filter over the list - RouteFilterEditor, with "Done" in place
// of "+ Add route" - right-click to remove), "+ Add route", the three transceiver cages and the throughput.
public class RouterPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/router.png");
    private static final Identifier DOT = EncodedLogistics.id("rack/router/segment_dot"), ARROW = EncodedLogistics.id("rack/router/arrow"),
            GHOST = EncodedLogistics.id("relay/ghost_transceiver"), BAR = EncodedLogistics.id("common/bar_fill_mint");
    private static final int SEGMENTS_X = 10, LIST_Y = 31, ROWS = 6, ROW_H = 14, SEGMENTS_W = 52;
    private static final int ROUTES_X = 70, SOURCE_X = 72, ARROW_X = 98, DEST_X = 112, FILTER_X = 150, ROUTES_W = 98;
    private static final int ADD_X = 68, ADD_Y = 116, ADD_W = 100, ADD_H = 12, RATE_Y = 132, BAR_X = 69, BAR_Y = 144, BAR_W = 98;

    private @Nullable EditBox rename;
    private int renaming = -1;
    private final RouteFilterEditor editor;

    public RouterPanel(RackScreen screen) {
        super(screen);
        editor = new RouteFilterEditor(this, ROUTES_X - 1, LIST_Y - 1, ROUTES_W + 1, ROWS * ROW_H + 1, RouterDevice.ACTION_SET_FILTER,
                RouterDevice.ACTION_FILTER_OPTION);
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
    }

    @Override
    protected void removed() {
        stopRenaming();
    }

    private @Nullable RouterDevice router() {
        return device(RouterDevice.class);
    }

    private boolean reachable(int segment) {
        ValueInput data = data();
        return data != null && (data.getIntOr("reachable", 0) >> segment & 1) != 0;
    }

    // --- Drawing ---

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        RouterDevice router = router();
        if (router != null) {
            for (int i = 0; i < Math.min(ROWS, router.endpointCount()); i++) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DOT, x + SEGMENTS_X + 2, y + LIST_Y + i * ROW_H + 4, 5, 5,
                        reachable(i) ? 0xFFFFFFFF : 0xFF505050);
            }
            List<ItemRouting.Route> routes = router.routes();
            ItemRouting.Route editing = editor.current(routes);
            if (editing != null) {
                editor.extractBackground(graphics, editing, x, y, mouseX, mouseY);
            } else {
                for (int i = 0; i < Math.min(ROWS, routes.size()); i++) {
                    int rowY = y + LIST_Y + i * ROW_H;
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, x + ARROW_X, rowY + 1, 12, 12);
                    RackScreen.routeBox(graphics, routes.get(i).filter(), x + FILTER_X - 1, rowY, mouseX >= x + FILTER_X - 2
                            && mouseX < x + ROUTES_X + ROUTES_W && mouseY >= rowY && mouseY < rowY + ROW_H);
                }
            }
            boolean full = routes.size() >= RouterDevice.MAX_ROUTES;
            PartScreens.wideButton(graphics, font(), x + ADD_X, y + ADD_Y, ADD_W, ADD_H, Component.translatable(editing != null
                    ? "gui.encodedlogistics.route.done" : "gui.encodedlogistics.router.add_rule"), editing != null || !full, mouseX, mouseY);
            for (int i = 0; i < RouterDevice.CAGES; i++) {
                if (screen.getMenu().deviceSlot(i) == null || screen.getMenu().deviceSlot(i).getItem().isEmpty()) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOST, x + RackDeviceType.ROUTER.slots().get(i).x(), y + RackDeviceType.ROUTER.slots().get(i).y(), 16, 16);
                }
            }
            ValueInput data = data();
            if (data != null) {
                PartScreens.bar(graphics, BAR, x + BAR_X, y + BAR_Y, BAR_W,
                        (float) (data.getDoubleOr("throughput", 0) / Math.max(1, data.getIntOr("rate", 1))));
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font(), Component.translatable("gui.encodedlogistics.router.networks"), 8, 20, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), Component.translatable("gui.encodedlogistics.router.wan_rules"), 68, 20, RackScreen.TEXT_MUTED, false);
        RouterDevice router = router();
        if (router == null) {
            return;
        }
        for (int i = 0; i < Math.min(ROWS, router.endpointCount()); i++) {
            if (i != renaming) {
                text(graphics, router.endpointName(i).getString(), SEGMENTS_X + 10, LIST_Y + i * ROW_H + 3, SEGMENTS_W - 10,
                        reachable(i) ? RackScreen.TEXT : RackScreen.TEXT_DISABLED);
            }
        }
        List<ItemRouting.Route> routes = router.routes();
        ItemRouting.Route editing = editor.current(routes);
        if (editing != null) {
            editor.extractLabels(graphics, routeName(router, editing));
        }
        for (int i = 0; editing == null && i < Math.min(ROWS, routes.size()); i++) {
            ItemRouting.Route route = routes.get(i);
            int rowY = LIST_Y + i * ROW_H + 3;
            // An empty allow list moves nothing: dimmed.
            int color = route.idle() ? RackScreen.TEXT_DISABLED : RackScreen.TEXT;
            text(graphics, router.endpointName(route.source()).getString(), SOURCE_X, rowY, ARROW_X - SOURCE_X - 1, color);
            text(graphics, router.endpointName(route.dest()).getString(), DEST_X, rowY, FILTER_X - DEST_X - 2, color);
            if (route.filter().isEmpty() && route.filter().deny()) {
                graphics.text(font(), "*", FILTER_X + 4, rowY, RackScreen.TEXT_MUTED, false);
            }
        }
        ValueInput data = data();
        if (data != null) {
            graphics.text(font(), Component.translatable("gui.encodedlogistics.router.throughput",
                    String.format(Locale.ROOT, "%.1f", data.getDoubleOr("throughput", 0))).append(" / " + data.getIntOr("rate", 0)), 68, RATE_Y,
                    RackScreen.TEXT_MUTED, false);
        }
    }

    private static Component routeName(RouterDevice router, ItemRouting.Route route) {
        return Component.translatable("gui.encodedlogistics.router.route", router.endpointName(route.source()), router.endpointName(route.dest()));
    }

    private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        graphics.text(font(), font().plainSubstrByWidth(text, width), x, y, color, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        RouterDevice router = router();
        if (router == null) {
            return;
        }
        if (screen.over(mouseX, mouseY, SEGMENTS_X, LIST_Y, SEGMENTS_W, ROWS * ROW_H)) {
            int row = (mouseY - screen.top() - LIST_Y) / ROW_H;
            if (row < router.endpointCount()) {
                graphics.setComponentTooltipForNextFrame(font(), List.of(router.endpointName(row),
                        Component.translatable(reachable(row) ? "gui.encodedlogistics.router.reachable" : "gui.encodedlogistics.router.unreachable")
                                .withColor(RackScreen.TEXT_MUTED),
                        Component.translatable(row == 0 ? "gui.encodedlogistics.router.rename_hint" : "gui.encodedlogistics.router.network_hint")
                                .withColor(RackScreen.TEXT_DISABLED)), mouseX, mouseY);
            } else {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.router.link_hint"), mouseX, mouseY);
            }
            return;
        }
        ItemRouting.Route editing = editor.current(router.routes());
        if (editing != null) {
            editor.extractTooltip(graphics, editing, screen.left(), screen.top(), mouseX, mouseY);
            return;
        }
        int row = (mouseY - screen.top() - LIST_Y) / ROW_H;
        if (screen.over(mouseX, mouseY, ROUTES_X, LIST_Y, ROUTES_W, ROWS * ROW_H) && row < router.routes().size()) {
            ItemRouting.Route route = router.routes().get(row);
            graphics.setComponentTooltipForNextFrame(font(), List.of(routeName(router, route),
                    RouteFilterEditor.summary(route.filter()).copy().withColor(RackScreen.TEXT_MUTED),
                    Component.translatable("gui.encodedlogistics.router.route_hint").withColor(RackScreen.TEXT_DISABLED)), mouseX, mouseY);
        }
    }

    @Override
    protected List<RackScreen.GhostTarget> ghostTargets() {
        RouterDevice router = router();
        if (router == null) {
            return List.of();
        }
        return editor.current(router.routes()) != null ? editor.ghostTargets(screen.left(), screen.top())
                : RouteFilterEditor.rowTargets(this, router.routes(), ROWS, screen.left() + FILTER_X - 1, screen.top() + LIST_Y, ROW_H,
                        RouterDevice.ACTION_SET_FILTER);
    }

    // --- Input ---

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        RouterDevice router = router();
        if (router == null) {
            return false;
        }
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (rename != null && !in(x, y, SEGMENTS_X, LIST_Y + renaming * ROW_H, SEGMENTS_W, ROW_H)) {
            submitRename();
        }
        boolean editing = editor.current(router.routes()) != null;
        if (left && in(x, y, ADD_X, ADD_Y, ADD_W, ADD_H)) {
            if (editing) {
                editor.close();
            } else {
                send(RouterDevice.ACTION_ADD_ROUTE, 0, "");
            }
            return true;
        }
        if (editing && editor.mouseClicked(x, y, button)) {
            return true;
        }
        int row = (int) Math.floor((y - LIST_Y) / ROW_H);
        if (in(x, y, SEGMENTS_X, LIST_Y, SEGMENTS_W, ROWS * ROW_H) && row < router.endpointCount()) {
            if (right && row > 0) {
                send(RouterDevice.ACTION_UNLINK, row, "");
            } else if (left) {
                startRenaming(router, row);
            }
            return true;
        }
        if (in(x, y, ROUTES_X, LIST_Y, ROUTES_W, ROWS * ROW_H) && row < router.routes().size()) {
            if (right) {
                send(RouterDevice.ACTION_REMOVE_ROUTE, row, "");
            } else if (left && x < ARROW_X) {
                send(RouterDevice.ACTION_CYCLE_SOURCE, row, "");
            } else if (left && x < FILTER_X - 2) {
                send(RouterDevice.ACTION_CYCLE_DEST, row, "");
            } else if (left) {
                editor.open(row);
            }
            return true;
        }
        return false;
    }

    // --- Renaming a segment ---

    private void startRenaming(RouterDevice router, int row) {
        stopRenaming();
        renaming = row;
        rename = new EditBox(font(), screen.left() + SEGMENTS_X + 10, screen.top() + LIST_Y + row * ROW_H + 3, SEGMENTS_W - 10, 9,
                Component.translatable("gui.encodedlogistics.router.rename"));
        rename.setBordered(false);
        rename.setMaxLength(24);
        rename.setTextColor(RackScreen.TEXT);
        rename.setValue(router.endpointName(row).getString());
        screen.addPanelWidget(rename);
        rename.setFocused(true);
        screen.setFocused(rename);
    }

    private void submitRename() {
        if (rename != null && renaming >= 0) {
            send(RouterDevice.ACTION_RENAME, renaming, rename.getValue());
        }
        stopRenaming();
    }

    private void stopRenaming() {
        if (rename != null) {
            screen.removePanelWidget(rename);
            rename = null;
        }
        renaming = -1;
    }

    @Override
    protected boolean keyPressed(KeyEvent event) {
        if (rename == null) {
            return false;
        }
        if (event.isConfirmation()) {
            submitRename();
            return true;
        }
        if (event.isEscape()) {
            stopRenaming();
            return true;
        }
        return rename.keyPressed(event) || rename.canConsumeInput();
    }

    private static boolean in(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
