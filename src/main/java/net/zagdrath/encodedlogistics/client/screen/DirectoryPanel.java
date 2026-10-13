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

    private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        graphics.text(context.font(), context.font().plainSubstrByWidth(text, width), x, y, color, false);
    }

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(context.font(), text, right - context.font().width(text), y, color, false);
    }

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
        List<Row> rows = rows(data);
        switch (tab) {
            case TAB_REMOTE -> {
                text(graphics, tr("col.network"), X, HEAD_Y, 80, RackScreen.TEXT_MUTED);
                right(graphics, tr("col.hops"), 112, HEAD_Y, RackScreen.TEXT_MUTED);
                right(graphics, tr("col.control"), RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
            }
            case TAB_LOOKUPS -> {
                text(graphics, tr("col.name"), X, HEAD_Y, 80, RackScreen.TEXT_MUTED);
                right(graphics, tr("col.result"), RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
            }
            default -> {
                text(graphics, tr("col.device"), X, HEAD_Y, 70, RackScreen.TEXT_MUTED);
                text(graphics, tr("col.type"), 86, HEAD_Y, 40, RackScreen.TEXT_MUTED);
                right(graphics, tr("col.published"), RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
            }
        }
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            switch (tab) {
                case TAB_REMOTE -> {
                    text(graphics, row.a(), X, y, 80, RackScreen.TEXT);
                    right(graphics, row.b(), 112, y, RackScreen.TEXT);
                    right(graphics, tr(row.flag() ? "yes" : "no"), RIGHT, y, row.flag() ? RackScreen.ACCENT : RackScreen.ERROR);
                }
                case TAB_LOOKUPS -> {
                    text(graphics, row.a(), X, y, 82, RackScreen.TEXT);
                    right(graphics, row.b(), RIGHT, y, row.flag() ? RackScreen.TEXT : RackScreen.ERROR);
                }
                default -> {
                    text(graphics, row.a(), X, y, 72, RackScreen.TEXT);
                    text(graphics, row.b(), 86, y, 40, RackScreen.TEXT_MUTED);
                    right(graphics, tr(row.flag() ? "yes" : "no"), RIGHT, y, row.flag() ? RackScreen.ACCENT : RackScreen.TEXT_DISABLED);
                }
            }
        }
        if (rows.isEmpty()) {
            text(graphics, tr(tab == TAB_REMOTE ? "no_remote" : tab == TAB_LOOKUPS ? "no_lookups" : "no_devices"), X, ROWS_Y, 140, RackScreen.TEXT_DISABLED);
        }
    }

    @Override
    public void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        double x = mouseX - context.left(), y = mouseY - context.top();
        if (x >= X && x < RIGHT && y >= HEAD_Y && y < ROWS_Y + ROWS * ROW) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.directory.hint." + tab), mouseX, mouseY);
        }
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
