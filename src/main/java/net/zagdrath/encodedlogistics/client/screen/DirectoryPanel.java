/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.network.routing.DirectoryPanels;

// The Directory Server's panel (previews/gui_directory.png), block or rack form, in tabs:
//  Published: its network's devices (name, type, published or not: click to publish it or stop).
//  Remote: the networks its network's Routers reach that have a Directory Server (hops, whether a CONTROL policy goes
//   there - without one their devices can't be reached).
//  Lookups: the latest SYSNAME/DEVICE lookups made from its network, and what came of them.
final class DirectoryPanel implements PanelBody {
    private static final int X = 12, RIGHT = 152, HEAD_Y = 36, ROWS_Y = 49, ROW = 11, ROWS = 8;
    // Column positions, and the room kept between a column and the next.
    private static final int TYPE_X = 84, HOPS_RIGHT = 118, GAP = 4;
    private static final int TAB_PUBLISHED = 0, TAB_REMOTE = 1, TAB_LOOKUPS = 2;

    private final Context context;
    private final PanelTabs tabs = new PanelTabs("gui.encodedlogistics.directory.tab.published", "gui.encodedlogistics.directory.tab.remote",
            "gui.encodedlogistics.directory.tab.lookups");
    private int tab, scroll;

    DirectoryPanel(Context context) {
        this.context = context;
    }

    private record Row(String a, String b, String c, boolean flag) {}

    private List<Row> rows(ValueInput data) {
        List<Row> rows = new ArrayList<>();
        switch (tab) {
            case TAB_REMOTE -> data.childrenListOrEmpty("remotes").forEach(row -> rows.add(new Row(row.getStringOr("name", ""),
                    Integer.toString(row.getIntOr("hops", 0)), "", row.getBooleanOr("control", false))));
            case TAB_LOOKUPS -> data.childrenListOrEmpty("lookups").forEach(row -> {
                String refused = row.getStringOr("refused", "");
                String result = refused.isEmpty() ? Component.translatable("gui.encodedlogistics.directory.ok", row.getIntOr("hops", 0)).getString()
                        : tr(refused.equals("ELC2604") ? "denied" : "no_route");
                rows.add(new Row(row.getStringOr("name", ""), result, "", refused.isEmpty()));
            });
            default -> data.childrenListOrEmpty("devices").forEach(row -> rows.add(new Row(row.getStringOr("name", ""), row.getStringOr("type", ""),
                    Long.toString(row.getLongOr("lookups", 0)), row.getBooleanOr("published", false))));
        }
        return rows;
    }

    private static String tr(String key) {
        return Component.translatable("gui.encodedlogistics.directory." + key).getString();
    }

    // text in width, cut with "..." (the row's tooltip has it whole).
    private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        graphics.text(context.font(), PanelBody.fit(context.font(), text, width), x, y, color, false);
    }

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(context.font(), text, right - context.font().width(text), y, color, false);
    }

    private int width(String text) {
        return context.font().width(text);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        tabs.extractBackground(graphics, context.font(), context.left(), context.top(), tab);
        ValueInput data = context.data();
        PanelBody.scrollbar(graphics, context.left(), context.top(), scroll, data == null ? 0 : rows(data).size(), ROWS);
    }

    // Columns, each value kept clear of the next column's heading and values:
    //  Published: device at 12, type at TYPE_X, published right at 152.
    //  Remote: network at 12, hops right at HOPS_RIGHT, control right at 152.
    //  Lookups: name at 12, result right at 152 (the name up to the result).
    @Override
    public void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        tabs.extractLabels(graphics, context.font(), tab);
        ValueInput data = context.data();
        if (data == null) {
            return;
        }
        List<Row> rows = rows(data);
        String yes = tr("yes"), no = tr("no");
        int flagW = Math.max(width(yes), width(no));
        PanelBody.divider(graphics, HEAD_Y);
        switch (tab) {
            case TAB_REMOTE -> {
                text(graphics, tr("col.network"), X, HEAD_Y, HOPS_RIGHT - width(tr("col.hops")) - GAP - X, RackScreen.TEXT_MUTED);
                right(graphics, tr("col.hops"), HOPS_RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
                right(graphics, tr("col.control"), RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
            }
            case TAB_LOOKUPS -> {
                text(graphics, tr("col.name"), X, HEAD_Y, RIGHT - width(tr("col.result")) - GAP - X, RackScreen.TEXT_MUTED);
                right(graphics, tr("col.result"), RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
            }
            default -> {
                text(graphics, tr("col.device"), X, HEAD_Y, TYPE_X - GAP - X, RackScreen.TEXT_MUTED);
                text(graphics, tr("col.type"), TYPE_X, HEAD_Y, typeWidth(flagW), RackScreen.TEXT_MUTED);
                right(graphics, tr("col.published"), RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
            }
        }
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            switch (tab) {
                case TAB_REMOTE -> {
                    int hopsLeft = HOPS_RIGHT - Math.max(width(tr("col.hops")), width(row.b()));
                    text(graphics, row.a(), X, y, hopsLeft - GAP - X, RackScreen.TEXT);
                    right(graphics, row.b(), HOPS_RIGHT, y, RackScreen.TEXT);
                    right(graphics, row.flag() ? yes : no, RIGHT, y, row.flag() ? RackScreen.ACCENT : RackScreen.ERROR);
                }
                case TAB_LOOKUPS -> {
                    text(graphics, row.a(), X, y, RIGHT - width(row.b()) - GAP - X, RackScreen.TEXT);
                    right(graphics, row.b(), RIGHT, y, row.flag() ? RackScreen.TEXT : RackScreen.ERROR);
                }
                default -> {
                    text(graphics, row.a(), X, y, TYPE_X - GAP - X, RackScreen.TEXT);
                    text(graphics, row.b(), TYPE_X, y, typeWidth(flagW), RackScreen.TEXT_MUTED);
                    right(graphics, row.flag() ? yes : no, RIGHT, y, row.flag() ? RackScreen.ACCENT : RackScreen.TEXT_DISABLED);
                }
            }
        }
        if (rows.isEmpty()) {
            PanelBody.wrapped(graphics, context.font(), tr(tab == TAB_REMOTE ? "no_remote" : tab == TAB_LOOKUPS ? "no_lookups" : "no_devices"), X, ROWS_Y,
                    RIGHT - X, ROW, RackScreen.TEXT_DISABLED);
        }
    }

    // The type column: up to the published column (its heading or its widest value).
    private int typeWidth(int flagW) {
        return RIGHT - Math.max(width(tr("col.published")), flagW) - GAP - TYPE_X;
    }

    // Over the headings, the tab's hint; over a row, its values in full and the hint.
    @Override
    public void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ValueInput data = context.data();
        double x = mouseX - context.left(), y = mouseY - context.top();
        if (data == null || x < X || x >= RIGHT || y < HEAD_Y - 2 || y >= ROWS_Y + ROWS * ROW) {
            return;
        }
        Component hint = Component.translatable("gui.encodedlogistics.directory.hint." + tab).withColor(RackScreen.TEXT_DISABLED);
        List<Row> rows = rows(data);
        int index = y >= ROWS_Y ? scroll + (int) ((y - ROWS_Y) / ROW) : -1;
        if (index < 0 || index >= rows.size()) {
            Tooltips.show(graphics, context.font(), Component.translatable("gui.encodedlogistics.directory.hint." + tab), mouseX, mouseY);
            return;
        }
        Row row = rows.get(index);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(row.a()));
        switch (tab) {
            case TAB_REMOTE -> lines.add(Component.translatable("gui.encodedlogistics.directory.hops_line", row.b()).withColor(RackScreen.TEXT_MUTED));
            case TAB_LOOKUPS -> lines.add(Component.literal(row.b()).withColor(row.flag() ? RackScreen.TEXT_MUTED : RackScreen.ERROR));
            default -> {
                lines.add(Component.literal(row.b()).withColor(RackScreen.TEXT_MUTED));
                lines.add(Component.translatable("gui.encodedlogistics.directory.lookups_line", row.c()).withColor(RackScreen.TEXT_MUTED));
            }
        }
        lines.add(hint);
        Tooltips.show(graphics, context.font(), lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double amount) {
        ValueInput data = context.data();
        int size = data == null ? 0 : rows(data).size();
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, size - ROWS));
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button, boolean shift) {
        int clicked = tabs.at(context.font(), x, y);
        if (button == InputConstants.MOUSE_BUTTON_LEFT && clicked >= 0) {
            tab = clicked;
            scroll = 0;
            return true;
        }
        if (tab != TAB_PUBLISHED || button != InputConstants.MOUSE_BUTTON_LEFT || x < X || x >= RIGHT || y < ROWS_Y || y >= ROWS_Y + ROWS * ROW) {
            return false;
        }
        context.send(DirectoryPanels.ACTION_PUBLISH, scroll + (int) ((y - ROWS_Y) / ROW), "");
        return true;
    }
}
