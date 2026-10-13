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
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.blockentity.ExchangeSwitchBlockEntity;

// The Exchange Switch's panel (previews/gui_exchange.png), in tabs:
//  Members: network, owner, port, up; requests (no port yet) in amber, which the owner approves with a click;
//   right-click removes a member (the owner, or the member's own side); the ports used and route server under them.
//  Peers: from the viewer's member (the first whose network they may build on), every other member with the session
//   between them (via the route server or bilateral, up or down), the routes exchanged, its own side's policy and the
//   traffic: click a peer to accept (or stop accepting) bilateral peering with it, Rts to exchange routes with it or
//   not, Tr to let its traffic through (default, yes, no). A viewer with no member sees every session.
//  Setup: the name, membership, route server, ports (the owner's), the viewer's member's route-server opt-in, the
//   owner, whether it's online and keeps its chunk loaded.
final class ExchangePanel implements PanelBody {
    private static final int X = 12, RIGHT = 152, HEAD_Y = 36, ROWS_Y = 49, ROW = 11, ROWS = 7, FOOT_Y = 130, SETUP_ROW = 13;
    private static final int TAB_MEMBERS = 0, TAB_PEERS = 1, TAB_SETUP = 2;
    private static final int SETUP_NAME = 0, SETUP_MEMBERSHIP = 1, SETUP_SERVER = 2, SETUP_PORTS = 3, SETUP_OPT_IN = 4, SETUP_ROWS = 8;

    private final Context context;
    private final PanelTabs tabs = new PanelTabs("gui.encodedlogistics.exchange.tab.members", "gui.encodedlogistics.exchange.tab.peers",
            "gui.encodedlogistics.exchange.tab.setup");
    private int tab, scroll;
    private @Nullable EditBox rename;

    ExchangePanel(Context context) {
        this.context = context;
    }

    private record MemberRow(String network, String owner, int port, boolean up, boolean routeServer) {}

    private record PeerRow(int index, String network, String state, String mode, boolean weAccept, boolean theyAccept, int routes, long traffic,
            boolean exchangeRoutes, String transit) {}

    private List<MemberRow> members(ValueInput data) {
        List<MemberRow> rows = new ArrayList<>();
        for (ValueInput row : data.childrenListOrEmpty("members")) {
            rows.add(new MemberRow(row.getStringOr("network", ""), row.getStringOr("owner", ""), row.getIntOr("port", -1), row.getBooleanOr("up", false),
                    row.getBooleanOr("rs", true)));
        }
        return rows;
    }

    private List<PeerRow> peers(ValueInput data) {
        List<PeerRow> rows = new ArrayList<>();
        for (ValueInput row : data.childrenListOrEmpty("peers")) {
            rows.add(new PeerRow(row.getIntOr("index", -1), row.getStringOr("network", ""), row.getStringOr("state", "NONE"), row.getStringOr("mode", ""),
                    row.getBooleanOr("we_accept", false), row.getBooleanOr("they_accept", false), row.getIntOr("routes", 0), row.getLongOr("traffic", 0),
                    row.getBooleanOr("exchange_routes", true), row.getStringOr("transit", "DEFAULT")));
        }
        return rows;
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        tabs.extractBackground(graphics, context.font(), context.left(), context.top(), tab);
    }

    @Override
    public void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        tabs.extractLabels(graphics, context.font(), tab);
        ValueInput data = context.data();
        if (data == null) {
            return;
        }
        switch (tab) {
            case TAB_PEERS -> peerLabels(graphics, data);
            case TAB_SETUP -> setupLabels(graphics, data);
            default -> memberLabels(graphics, data);
        }
    }

    private void memberLabels(GuiGraphicsExtractor graphics, ValueInput data) {
        text(graphics, tr("col.network"), X, HEAD_Y, RackScreen.TEXT_MUTED);
        text(graphics, tr("col.owner"), 66, HEAD_Y, RackScreen.TEXT_MUTED);
        right(graphics, tr("col.port"), 128, HEAD_Y, RackScreen.TEXT_MUTED);
        right(graphics, tr("col.up"), RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
        List<MemberRow> rows = members(data);
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            MemberRow row = rows.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            boolean request = row.port() < 0;
            int color = request ? RackScreen.WARNING : RackScreen.TEXT;
            clip(graphics, row.network(), X, y, 52, color);
            clip(graphics, row.owner(), 66, y, 46, color);
            right(graphics, request ? "-" : Integer.toString(row.port()), 128, y, color);
            right(graphics, request ? tr("request") : tr(row.up() ? "yes" : "no"), RIGHT, y, request ? RackScreen.WARNING
                    : row.up() ? RackScreen.TEXT : RackScreen.ERROR);
        }
        if (rows.isEmpty()) {
            text(graphics, tr("no_members"), X, ROWS_Y, RackScreen.TEXT_DISABLED);
        }
        text(graphics, Component.translatable("gui.encodedlogistics.exchange.ports_line", data.getIntOr("used", 0), data.getIntOr("ports", 0),
                tr(data.getBooleanOr("route_server", true) ? "rs_on" : "rs_off")).getString(), X, FOOT_Y, RackScreen.TEXT_MUTED);
    }

    // Columns: peer 12, session 62, routes right at 112, transit 118, traffic right at 152.
    private void peerLabels(GuiGraphicsExtractor graphics, ValueInput data) {
        boolean member = data.getIntOr("mine", -1) >= 0;
        text(graphics, tr(member ? "col.peer" : "col.session"), X, HEAD_Y, RackScreen.TEXT_MUTED);
        text(graphics, tr("col.state"), 62, HEAD_Y, RackScreen.TEXT_MUTED);
        if (member) {
            right(graphics, tr("col.routes"), 112, HEAD_Y, RackScreen.TEXT_MUTED);
            text(graphics, tr("col.transit"), 118, HEAD_Y, RackScreen.TEXT_MUTED);
        }
        right(graphics, tr("col.traffic"), RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
        List<PeerRow> rows = peers(data);
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            PeerRow row = rows.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            clip(graphics, row.network(), X, y, member ? 48 : 48, member && row.weAccept() ? RouterPanel.BLUE : RackScreen.TEXT);
            String state = row.state().equals("NONE") ? "-" : (row.mode().equals("RS") ? "RS " : "Bi ") + tr(row.state().toLowerCase(Locale.ROOT));
            text(graphics, state, 62, y, row.state().equals("UP") ? RackScreen.ACCENT : row.state().equals("DOWN") ? RackScreen.ERROR : RackScreen.TEXT_DISABLED);
            if (member) {
                right(graphics, Integer.toString(row.routes()), 112, y, row.exchangeRoutes() ? RackScreen.TEXT : RackScreen.TEXT_DISABLED);
                text(graphics, tr("transit." + row.transit().toLowerCase(Locale.ROOT)), 118, y, row.transit().equals("NO") ? RackScreen.ERROR
                        : row.transit().equals("YES") ? RackScreen.ACCENT : RackScreen.TEXT_MUTED);
            }
            right(graphics, RouterAclTab.compact(row.traffic()), RIGHT, y, RackScreen.TEXT);
        }
        if (rows.isEmpty()) {
            text(graphics, tr(member ? "no_peers" : "no_sessions"), X, ROWS_Y, RackScreen.TEXT_DISABLED);
        }
    }

    private void setupLabels(GuiGraphicsExtractor graphics, ValueInput data) {
        boolean owner = data.getBooleanOr("is_owner", false), member = data.getIntOr("mine", -1) >= 0;
        List<MemberRow> rows = members(data);
        int mine = data.getIntOr("mine", -1);
        String[] labels = {"name", "membership", "route_server", "ports", "opt_in", "owner", "online", "chunk"};
        String[] values = {data.getStringOr("name", ""), tr("membership." + data.getStringOr("membership", "APPROVAL").toLowerCase(Locale.ROOT)),
                tr(data.getBooleanOr("route_server", true) ? "on" : "off"), data.getIntOr("ports", 0) + " / " + data.getIntOr("max_ports", 0),
                member && mine < rows.size() ? tr(rows.get(mine).routeServer() ? "yes" : "no") : "-", data.getStringOr("owner", ""),
                tr(data.getBooleanOr("online", false) ? "yes" : "no"), tr(data.getBooleanOr("chunk", false) ? "yes" : "no")};
        for (int i = 0; i < SETUP_ROWS; i++) {
            int y = HEAD_Y + i * SETUP_ROW;
            boolean clickable = i <= SETUP_PORTS ? owner : i == SETUP_OPT_IN && member;
            text(graphics, tr("setup." + labels[i]), X, y, RackScreen.TEXT_MUTED);
            if (!(i == SETUP_NAME && rename != null)) {
                right(graphics, values[i], RIGHT, y, clickable ? RackScreen.ACCENT : RackScreen.TEXT);
            }
        }
    }

    private static String tr(String key) {
        return Component.translatable("gui.encodedlogistics.exchange." + key).getString();
    }

    private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(context.font(), text, x, y, color, false);
    }

    private void clip(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        graphics.text(context.font(), context.font().plainSubstrByWidth(text, width), x, y, color, false);
    }

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(context.font(), text, right - context.font().width(text), y, color, false);
    }

    @Override
    public void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ValueInput data = context.data();
        double x = mouseX - context.left(), y = mouseY - context.top();
        if (data == null || x < X || x >= RIGHT) {
            return;
        }
        if (tab == TAB_SETUP) {
            int row = (int) Math.floor((y - HEAD_Y + 2) / SETUP_ROW);
            if (y >= HEAD_Y - 2 && row >= 0 && row <= SETUP_OPT_IN) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.exchange.hint.setup." + row), mouseX, mouseY);
            }
            return;
        }
        int row = y >= ROWS_Y ? scroll + (int) ((y - ROWS_Y) / ROW) : -1;
        if (tab == TAB_MEMBERS && row >= 0 && row < members(data).size()) {
            MemberRow member = members(data).get(row);
            graphics.setTooltipForNextFrame(Component.translatable(member.port() < 0 ? "gui.encodedlogistics.exchange.hint.request"
                    : "gui.encodedlogistics.exchange.hint.member", member.network(), member.owner()), mouseX, mouseY);
        } else if (tab == TAB_PEERS && row >= 0 && row < peers(data).size() && data.getIntOr("mine", -1) >= 0) {
            PeerRow peer = peers(data).get(row);
            graphics.setComponentTooltipForNextFrame(context.font(), List.of(Component.literal(peer.network()),
                    Component.translatable("gui.encodedlogistics.exchange.accepts", tr(peer.weAccept() ? "yes" : "no"), tr(peer.theyAccept() ? "yes" : "no"))
                            .withColor(RackScreen.TEXT_MUTED),
                    Component.translatable("gui.encodedlogistics.exchange.hint.peer").withColor(RackScreen.TEXT_DISABLED)), mouseX, mouseY);
        }
    }

    // --- Input ---

    @Override
    public boolean mouseScrolled(double amount) {
        ValueInput data = context.data();
        int size = data == null ? 0 : tab == TAB_PEERS ? peers(data).size() : members(data).size();
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, size - ROWS));
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button, boolean shift) {
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (rename != null) {
            submitRename();
        }
        int clicked = tabs.at(context.font(), x, y);
        if (left && clicked >= 0) {
            tab = clicked;
            scroll = 0;
            return true;
        }
        ValueInput data = context.data();
        if (data == null || x < X || x >= RIGHT) {
            return false;
        }
        if (tab == TAB_SETUP) {
            int row = (int) Math.floor((y - HEAD_Y + 2) / SETUP_ROW);
            switch (y >= HEAD_Y - 2 ? row : -1) {
                case SETUP_NAME -> startRename(data.getStringOr("name", ""));
                case SETUP_MEMBERSHIP -> context.send(ExchangeSwitchBlockEntity.ACTION_MEMBERSHIP, 0, "");
                case SETUP_SERVER -> context.send(ExchangeSwitchBlockEntity.ACTION_ROUTE_SERVER, 0, "");
                case SETUP_PORTS -> context.send(ExchangeSwitchBlockEntity.ACTION_PORTS, right ? -1 : 1, "");
                case SETUP_OPT_IN -> context.send(ExchangeSwitchBlockEntity.ACTION_OPT_IN, 0, "");
                default -> {
                    return false;
                }
            }
            return true;
        }
        int row = y >= ROWS_Y && y < ROWS_Y + ROWS * ROW ? scroll + (int) ((y - ROWS_Y) / ROW) : -1;
        if (row < 0) {
            return false;
        }
        if (tab == TAB_MEMBERS) {
            List<MemberRow> members = members(data);
            if (row >= members.size()) {
                return false;
            }
            if (right) {
                context.send(ExchangeSwitchBlockEntity.ACTION_REMOVE, row, "");
            } else if (left && members.get(row).port() < 0) {
                context.send(ExchangeSwitchBlockEntity.ACTION_APPROVE, row, "");
            }
            return true;
        }
        List<PeerRow> peers = peers(data);
        if (row >= peers.size() || peers.get(row).index() < 0 || !left) {
            return false;
        }
        int index = peers.get(row).index();
        int action = x >= 118 ? ExchangeSwitchBlockEntity.ACTION_PEER_TRANSIT : x >= 90 ? ExchangeSwitchBlockEntity.ACTION_PEER_ROUTES
                : ExchangeSwitchBlockEntity.ACTION_ACCEPT;
        context.send(action, index, "");
        return true;
    }

    private void startRename(String name) {
        rename = new EditBox(context.font(), context.left() + 70, context.top() + HEAD_Y, RIGHT - 70, 9, Component.translatable("gui.encodedlogistics.exchange.setup.name"));
        rename.setBordered(false);
        rename.setMaxLength(ExchangeSwitchBlockEntity.MAX_NAME);
        rename.setTextColor(RackScreen.TEXT);
        rename.setValue(name);
        context.addWidget(rename);
    }

    private void submitRename() {
        if (rename != null) {
            context.send(ExchangeSwitchBlockEntity.ACTION_RENAME, 0, rename.getValue());
            context.removeWidget(rename);
            rename = null;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (rename == null) {
            return false;
        }
        if (event.isConfirmation()) {
            submitRename();
            return true;
        }
        if (event.isEscape()) {
            context.removeWidget(rename);
            rename = null;
            return true;
        }
        return rename.keyPressed(event) || rename.canConsumeInput();
    }

    @Override
    public void removed() {
        if (rename != null) {
            context.removeWidget(rename);
            rename = null;
        }
    }
}
