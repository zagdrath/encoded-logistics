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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// The Router's panel (screens/rack/router.json), in tabs:
//  Routes: its networks (its own first; a green dot when it's reachable; click a name to rename it, right-click a linked
//   one to unlink it), its routes (click the source or the destination to step through the networks - in dynamic mode
//   the destinations go on to every network in its route table - the filter box to edit the route's filter over the
//   list - RouteFilterEditor, with "Done" in place of "+ Add route" - right-click to remove; a route with no path is
//   dimmed), "+ Add route", the three transceiver cages and the throughput.
//  Mode: routing mode and transit (click to change), neighbours (click: the Table tab's neighbour list), route count,
//   reconvergence, max hops, throughput.
//  Table: the route table (network, next hop, cost, hops; *STATIC entries say "static"); in Listed transit, click a
//   network to let its traffic through or not. Click the heading for its neighbours (state, cost: click +10,
//   right-click -10, shift-click back to its own).
//  ACLs: RouterAclTab.
public class RouterPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/router.png"),
            TABLE_BACKGROUND = EncodedLogistics.id("textures/gui/rack/l3_switch.png");
    private static final Identifier DOT = EncodedLogistics.id("rack/router/segment_dot"), ARROW = EncodedLogistics.id("rack/router/arrow"),
            GHOST = EncodedLogistics.id("relay/ghost_transceiver"), BAR = EncodedLogistics.id("common/bar_fill_mint");
    private static final int SEGMENTS_X = 10, LIST_Y = 31, ROWS = 6, ROW_H = 14, SEGMENTS_W = 52;
    private static final int ROUTES_X = 70, SOURCE_X = 72, ARROW_X = 98, DEST_X = 112, FILTER_X = 150, ROUTES_W = 98;
    private static final int ADD_X = 68, ADD_Y = 116, ADD_W = 100, ADD_H = 12, RATE_Y = 132, BAR_X = 69, BAR_Y = 144, BAR_W = 98;
    // The other tabs: the L3 Switch's inset (x 12-153, y 34-139), values right-aligned at 152.
    static final int INSET_X = 12, INSET_RIGHT = 152, HEAD_Y = 36, TABLE_Y = 49, TABLE_ROW = 11, TABLE_ROWS = 8, MODE_ROW = 13;
    private static final int TAB_ROUTES = 0, TAB_MODE = 1, TAB_TABLE = 2, TAB_ACLS = 3;
    private static final int MODE_MODE = 0, MODE_TRANSIT = 1, MODE_NEIGHBOURS = 2, MODE_ROWS = 7;
    static final int BLUE = 0xFF50C2EC;

    private final PanelTabs tabs = new PanelTabs("gui.encodedlogistics.router.tab.routes", "gui.encodedlogistics.router.tab.mode",
            "gui.encodedlogistics.router.tab.table", "gui.encodedlogistics.router.tab.acls");
    private final RouterAclTab acls = new RouterAclTab(this);
    private int tab, scroll;
    private boolean showNeighbours;
    private @Nullable EditBox rename;
    private int renaming = -1;
    private final RouteFilterEditor editor;

    public RouterPanel(RackScreen screen) {
        super(screen);
        editor = new RouteFilterEditor(this, ROUTES_X - 1, LIST_Y - 1, ROUTES_W + 1, ROWS * ROW_H + 1, RouterDevice.ACTION_SET_FILTER,
                RouterDevice.ACTION_FILTER_OPTION, RouterDevice.ACTION_SHARE_OPTION);
    }

    // The L3 Switch's height, for its other tabs' lists.
    @Override
    protected int height() {
        return 166;
    }

    @Override
    protected Identifier background() {
        return tab == TAB_ROUTES ? BACKGROUND : TABLE_BACKGROUND;
    }

    @Override
    protected void removed() {
        stopRenaming();
        acls.close();
        screen.getMenu().resetSlotWindow();
    }

    private void showTab(int tab) {
        this.tab = tab;
        scroll = 0;
        stopRenaming();
        editor.close();
        acls.close();
        // The transceiver cages are on the Routes tab only.
        screen.getMenu().setSlotWindow(tab == TAB_ROUTES, 0, Integer.MAX_VALUE);
    }

    private @Nullable RouterDevice router() {
        return device(RouterDevice.class);
    }

    private boolean reachable(int segment) {
        ValueInput data = data();
        return data != null && (data.getIntOr("reachable", 0) >> segment & 1) != 0;
    }

    private boolean paused(int route) {
        ValueInput data = data();
        return data != null && (data.getIntOr("paused", 0) >> route & 1) != 0;
    }

    // --- What the server sent ---

    record TableRow(String name, String next, int cost, int hops, String source, boolean transit, long age, @Nullable NetworkRef network) {}

    private record NeighbourRow(String name, String via, String state, int cost, int override) {}

    List<TableRow> table() {
        ValueInput data = data();
        List<TableRow> rows = new ArrayList<>();
        if (data != null) {
            for (ValueInput row : data.childrenListOrEmpty("table")) {
                rows.add(new TableRow(row.getStringOr("name", ""), row.getStringOr("next", ""), row.getIntOr("cost", 0), row.getIntOr("hops", 0),
                        row.getStringOr("source", ""), row.getBooleanOr("transit", false), row.getLongOr("age", 0),
                        row.read("network", NetworkRef.CODEC).orElse(null)));
            }
        }
        return rows;
    }

    private List<NeighbourRow> neighbours() {
        ValueInput data = data();
        List<NeighbourRow> rows = new ArrayList<>();
        if (data != null) {
            for (ValueInput row : data.childrenListOrEmpty("neighbour_rows")) {
                rows.add(new NeighbourRow(row.getStringOr("name", ""), row.getStringOr("via", ""), row.getStringOr("state", "DOWN"),
                        row.getIntOr("cost", 0), row.getIntOr("override", -1)));
            }
        }
        return rows;
    }

    // A route's destination: its endpoint, or a remote network by the route table's name for it.
    private Component destName(RouterDevice router, ItemRouting.Route route) {
        if (!route.remote()) {
            return router.endpointName(route.dest());
        }
        for (TableRow row : table()) {
            if (row.network() != null && row.network().equals(route.target())) {
                return Component.literal(row.name());
            }
        }
        return Component.translatable("gui.encodedlogistics.router.remote");
    }

    // --- Drawing ---

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        tabs.extractBackground(graphics, font(), x, y, tab);
        RouterDevice router = router();
        if (router != null && tab == TAB_ACLS) {
            acls.extractBackground(graphics, router, mouseX, mouseY);
        }
        if (router == null || tab != TAB_ROUTES) {
            return;
        }
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
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, x + ARROW_X, rowY + 1, 12, 12,
                        paused(i) ? 0xFF505050 : RouteFilterEditor.arrowColor(routes.get(i)));
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

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        tabs.extractLabels(graphics, font(), tab);
        RouterDevice router = router();
        if (router == null) {
            return;
        }
        switch (tab) {
            case TAB_MODE -> modeLabels(graphics, router);
            case TAB_ACLS -> {
                ValueInput data = data();
                acls.extractLabels(graphics, router, table(), data == null || data.getBooleanOr("acl_default", true));
            }
            case TAB_TABLE -> {
                if (showNeighbours) {
                    neighbourLabels(graphics);
                } else {
                    tableLabels(graphics);
                }
            }
            default -> routeLabels(graphics, router);
        }
    }

    private void routeLabels(GuiGraphicsExtractor graphics, RouterDevice router) {
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
            // An empty allow list moves nothing, nor does a route with no path: dimmed.
            int color = route.idle() || paused(i) ? RackScreen.TEXT_DISABLED : RackScreen.TEXT;
            text(graphics, router.endpointName(route.source()).getString(), SOURCE_X, rowY, ARROW_X - SOURCE_X - 1, color);
            text(graphics, destName(router, route).getString(), DEST_X, rowY, FILTER_X - DEST_X - 2, route.remote() && !paused(i) ? BLUE : color);
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

    private void modeLabels(GuiGraphicsExtractor graphics, RouterDevice router) {
        ValueInput data = data();
        if (data == null) {
            return;
        }
        List<NeighbourRow> neighbours = neighbours();
        int up = 0;
        for (NeighbourRow neighbour : neighbours) {
            if (neighbour.state().equals("FULL")) {
                up++;
            }
        }
        Component[] labels = {Component.translatable("gui.encodedlogistics.router.mode"), Component.translatable("gui.encodedlogistics.router.transit"),
                Component.translatable("gui.encodedlogistics.router.neighbours"), Component.translatable("gui.encodedlogistics.router.table_routes"),
                Component.translatable("gui.encodedlogistics.router.reconverge"), Component.translatable("gui.encodedlogistics.router.max_hops"),
                Component.translatable("gui.encodedlogistics.router.rate")};
        Component[] values = {Component.translatable("gui.encodedlogistics.router.mode." + router.mode().name().toLowerCase(Locale.ROOT)),
                Component.translatable("gui.encodedlogistics.router.transit." + router.transit().name().toLowerCase(Locale.ROOT)),
                Component.translatable("gui.encodedlogistics.router.neighbours_up", up, neighbours.size()),
                Component.literal(Integer.toString(table().size())),
                Component.translatable("gui.encodedlogistics.router.seconds", data.getIntOr("reconverge", 0)),
                Component.literal(Integer.toString(data.getIntOr("max_hops", 0))),
                Component.translatable("gui.encodedlogistics.router.per_second", String.format(Locale.ROOT, "%,d", data.getIntOr("rate", 0)))};
        for (int i = 0; i < MODE_ROWS; i++) {
            int rowY = HEAD_Y + i * MODE_ROW;
            boolean clickable = i <= MODE_NEIGHBOURS;
            graphics.text(font(), labels[i], INSET_X, rowY, RackScreen.TEXT_MUTED, false);
            graphics.text(font(), values[i], INSET_RIGHT - font().width(values[i]), rowY, clickable ? RackScreen.ACCENT : RackScreen.TEXT, false);
        }
    }

    // Columns: network at 12, next hop at 62, cost right at 132, hops right at 152.
    private void tableLabels(GuiGraphicsExtractor graphics) {
        graphics.text(font(), Component.translatable("gui.encodedlogistics.router.col.network"), INSET_X, HEAD_Y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), Component.translatable("gui.encodedlogistics.router.col.next"), 62, HEAD_Y, RackScreen.TEXT_MUTED, false);
        right(graphics, Component.translatable("gui.encodedlogistics.router.col.cost").getString(), 132, HEAD_Y, RackScreen.TEXT_MUTED);
        right(graphics, Component.translatable("gui.encodedlogistics.router.col.hops").getString(), INSET_RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
        List<TableRow> rows = table();
        for (int i = 0; i < TABLE_ROWS && scroll + i < rows.size(); i++) {
            TableRow row = rows.get(scroll + i);
            int rowY = TABLE_Y + i * TABLE_ROW;
            boolean isStatic = row.source().equals("STATIC");
            text(graphics, row.name(), INSET_X, rowY, 48, row.transit() ? RackScreen.ACCENT : RackScreen.TEXT);
            String next = isStatic ? Component.translatable("gui.encodedlogistics.router.static").getString() : row.next();
            text(graphics, next, 62, rowY, 48, isStatic ? BLUE : RackScreen.TEXT);
            right(graphics, Integer.toString(row.cost()), 132, rowY, RackScreen.TEXT);
            right(graphics, Integer.toString(row.hops()), INSET_RIGHT, rowY, RackScreen.TEXT);
        }
        if (rows.isEmpty()) {
            graphics.text(font(), Component.translatable("gui.encodedlogistics.router.no_routes"), INSET_X, TABLE_Y, RackScreen.TEXT_DISABLED, false);
        }
    }

    // Columns: neighbour at 12, via at 62, state right at 126, cost right at 152.
    private void neighbourLabels(GuiGraphicsExtractor graphics) {
        graphics.text(font(), Component.translatable("gui.encodedlogistics.router.col.neighbour"), INSET_X, HEAD_Y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), Component.translatable("gui.encodedlogistics.router.col.via"), 62, HEAD_Y, RackScreen.TEXT_MUTED, false);
        right(graphics, Component.translatable("gui.encodedlogistics.router.col.cost").getString(), INSET_RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
        List<NeighbourRow> rows = neighbours();
        for (int i = 0; i < TABLE_ROWS && scroll + i < rows.size(); i++) {
            NeighbourRow row = rows.get(scroll + i);
            int rowY = TABLE_Y + i * TABLE_ROW;
            int color = switch (row.state()) {
                case "FULL" -> RackScreen.TEXT;
                case "INIT" -> RackScreen.WARNING;
                default -> RackScreen.ERROR;
            };
            text(graphics, row.name(), INSET_X, rowY, 48, color);
            text(graphics, row.via(), 62, rowY, 50, RackScreen.TEXT_MUTED);
            right(graphics, Integer.toString(row.cost()), INSET_RIGHT, rowY, row.override() >= 0 ? BLUE : RackScreen.TEXT);
        }
        if (rows.isEmpty()) {
            graphics.text(font(), Component.translatable("gui.encodedlogistics.router.no_neighbours"), INSET_X, TABLE_Y, RackScreen.TEXT_DISABLED, false);
        }
    }

    private Component routeName(RouterDevice router, ItemRouting.Route route) {
        return Component.translatable("gui.encodedlogistics.router.route", router.endpointName(route.source()), destName(router, route));
    }

    private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        graphics.text(font(), font().plainSubstrByWidth(text, width), x, y, color, false);
    }

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(font(), text, right - font().width(text), y, color, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        RouterDevice router = router();
        if (router == null) {
            return;
        }
        double x = mouseX - screen.left(), y = mouseY - screen.top();
        if (tab == TAB_ACLS) {
            acls.extractTooltip(graphics, router, x, y, mouseX, mouseY);
            return;
        }
        if (tab == TAB_MODE) {
            int row = (int) Math.floor((y - HEAD_Y + 2) / MODE_ROW);
            if (x >= INSET_X && x < INSET_RIGHT && y >= HEAD_Y - 2 && row >= 0 && row <= MODE_NEIGHBOURS) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.router.hint." + switch (row) {
                    case MODE_MODE -> "mode";
                    case MODE_TRANSIT -> "transit";
                    default -> "neighbours";
                }), mouseX, mouseY);
            }
            return;
        }
        if (tab == TAB_TABLE) {
            if (y >= HEAD_Y && y < TABLE_Y && x >= INSET_X && x < INSET_RIGHT) {
                graphics.setTooltipForNextFrame(Component.translatable(showNeighbours ? "gui.encodedlogistics.router.hint.show_table"
                        : "gui.encodedlogistics.router.hint.show_neighbours"), mouseX, mouseY);
                return;
            }
            int row = tableRow(y);
            if (showNeighbours) {
                List<NeighbourRow> rows = neighbours();
                if (row >= 0 && row < rows.size()) {
                    NeighbourRow neighbour = rows.get(row);
                    graphics.setComponentTooltipForNextFrame(font(), List.of(Component.literal(neighbour.name()),
                            Component.translatable("gui.encodedlogistics.router.state." + neighbour.state().toLowerCase(Locale.ROOT)).withColor(RackScreen.TEXT_MUTED),
                            Component.translatable("gui.encodedlogistics.router.hint.cost").withColor(RackScreen.TEXT_DISABLED)), mouseX, mouseY);
                }
            } else {
                List<TableRow> rows = table();
                if (row >= 0 && row < rows.size()) {
                    TableRow entry = rows.get(row);
                    List<Component> lines = new ArrayList<>(List.of(Component.literal(entry.name()),
                            Component.translatable("gui.encodedlogistics.router.source." + entry.source().toLowerCase(Locale.ROOT)).withColor(RackScreen.TEXT_MUTED),
                            Component.translatable("gui.encodedlogistics.router.age", age(entry.age())).withColor(RackScreen.TEXT_MUTED)));
                    if (router.transit() == net.zagdrath.encodedlogistics.network.routing.RoutingDomain.Transit.LISTED) {
                        lines.add(Component.translatable(entry.transit() ? "gui.encodedlogistics.router.hint.transit_listed"
                                : "gui.encodedlogistics.router.hint.transit_unlisted").withColor(RackScreen.TEXT_DISABLED));
                    }
                    graphics.setComponentTooltipForNextFrame(font(), lines, mouseX, mouseY);
                }
            }
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
            List<Component> lines = new ArrayList<>(List.of(routeName(router, route),
                    RouteFilterEditor.modeLine(route).copy().withColor(route.shares() ? 0xFF8FB8F0 : RackScreen.TEXT_MUTED),
                    RouteFilterEditor.summary(route.filter()).copy().withColor(RackScreen.TEXT_MUTED)));
            if (paused(row)) {
                lines.add(Component.translatable("gui.encodedlogistics.router.paused").withColor(RackScreen.WARNING));
            } else if (route.remote()) {
                lines.add(Component.translatable("gui.encodedlogistics.router.via_routing").withColor(BLUE));
            }
            lines.add(Component.translatable("gui.encodedlogistics.router.route_hint").withColor(RackScreen.TEXT_DISABLED));
            graphics.setComponentTooltipForNextFrame(font(), lines, mouseX, mouseY);
        }
    }

    // "2:14:06" from seconds.
    static String age(long seconds) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    @Override
    protected List<RackScreen.GhostTarget> ghostTargets() {
        RouterDevice router = router();
        if (router == null || tab != TAB_ROUTES) {
            return List.of();
        }
        return editor.current(router.routes()) != null ? editor.ghostTargets(screen.left(), screen.top())
                : RouteFilterEditor.rowTargets(this, router.routes(), ROWS, screen.left() + FILTER_X - 1, screen.top() + LIST_Y, ROW_H,
                        RouterDevice.ACTION_SET_FILTER);
    }

    // --- Input ---

    private int tableRow(double y) {
        int row = (int) Math.floor((y - TABLE_Y) / TABLE_ROW);
        return y < TABLE_Y || row >= TABLE_ROWS ? -1 : scroll + row;
    }

    @Override
    protected boolean mouseScrolled(double x, double y, double amount) {
        RouterDevice router = router();
        if (tab == TAB_ACLS && router != null) {
            return acls.mouseScrolled(router, amount);
        }
        if (tab != TAB_TABLE) {
            return false;
        }
        int size = showNeighbours ? neighbours().size() : table().size();
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, size - TABLE_ROWS));
        return true;
    }

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        RouterDevice router = router();
        if (router == null) {
            return false;
        }
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        int clicked = tabs.at(font(), x, y);
        if (left && clicked >= 0) {
            showTab(clicked);
            return true;
        }
        return switch (tab) {
            case TAB_MODE -> left && modeClicked(x, y);
            case TAB_TABLE -> tableClicked(x, y, left, right, shift);
            case TAB_ACLS -> acls.mouseClicked(router, table(), x, y, button, shift);
            default -> routesClicked(router, x, y, left, right, button);
        };
    }

    private boolean modeClicked(double x, double y) {
        int row = (int) Math.floor((y - HEAD_Y + 2) / MODE_ROW);
        if (x < INSET_X || x >= INSET_RIGHT || y < HEAD_Y - 2) {
            return false;
        }
        switch (row) {
            case MODE_MODE -> send(RouterDevice.ACTION_MODE, 0, "");
            case MODE_TRANSIT -> send(RouterDevice.ACTION_TRANSIT, 0, "");
            case MODE_NEIGHBOURS -> {
                showTab(TAB_TABLE);
                showNeighbours = true;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean tableClicked(double x, double y, boolean left, boolean right, boolean shift) {
        if (x < INSET_X || x >= INSET_RIGHT) {
            return false;
        }
        if (left && y >= HEAD_Y && y < TABLE_Y) {
            showNeighbours = !showNeighbours;
            scroll = 0;
            return true;
        }
        int row = tableRow(y);
        if (row < 0) {
            return false;
        }
        if (showNeighbours) {
            if (row >= neighbours().size()) {
                return false;
            }
            send(RouterDevice.ACTION_NEIGHBOUR_COST, row, shift ? "clear" : left ? "+" : right ? "-" : "");
            return true;
        }
        if (left && row < table().size()) {
            send(RouterDevice.ACTION_TRANSIT_NETWORK, row, "");
            return true;
        }
        return false;
    }

    private boolean routesClicked(RouterDevice router, double x, double y, boolean left, boolean right, int button) {
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
        RouterDevice router = router();
        if (tab == TAB_ACLS && router != null) {
            return acls.keyPressed(router, event);
        }
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

    static boolean in(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
