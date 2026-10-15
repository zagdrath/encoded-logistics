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
import net.zagdrath.encodedlogistics.optical.Multiplexer;

// The Optical Multiplexer's panel (previews/gui_optical_mux.png), block or rack form, in tabs:
//  Channels: each channel's client network, its lanes and whether it's up. Click a channel to pick it for the next
//   Link Card used on the multiplexer (click again to unpick); right-click to free it.
//  Line: the far end, the line's distance, longest span, loss, lanes and signal quality, its amplifiers, and the
//   pairing (Unpair).
final class OpticalMuxPanel implements PanelBody {
    private static final int X = 12, RIGHT = 152, HEAD_Y = 36, ROWS_Y = 49, ROW = 11, ROWS = 8;
    private static final int TAB_CHANNELS = 0, TAB_LINE = 1;
    // Channels: number at 12, network at NETWORK_X, lanes right at LANES_RIGHT, up right at 152 (GAP between columns).
    // Line: Unpair under the rows, inside the inset.
    private static final int NETWORK_X = 30, LANES_RIGHT = 128, GAP = 4, UNPAIR_Y = 124, UNPAIR_W = 60, UNPAIR_H = 14;

    private final Context context;
    private final PanelTabs tabs = new PanelTabs("gui.encodedlogistics.mux.tab.channels", "gui.encodedlogistics.mux.tab.line");
    private int tab, scroll;

    OpticalMuxPanel(Context context) {
        this.context = context;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("gui.encodedlogistics.mux." + key, args).getString();
    }

    // text in width, cut with "..." (the row's tooltip has it whole).
    private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        graphics.text(context.font(), PanelBody.fit(context.font(), text, width), x, y, color, false);
    }

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(context.font(), text, right - context.font().width(text), y, color, false);
    }

    private static List<ValueInput> channels(ValueInput data) {
        List<ValueInput> rows = new ArrayList<>();
        data.childrenListOrEmpty("channels").forEach(rows::add);
        return rows;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        tabs.extractBackground(graphics, context.font(), context.left(), context.top(), tab);
        ValueInput data = context.data();
        if (data != null && tab == TAB_CHANNELS) {
            int selected = data.getIntOr("selected", -1) - scroll;
            if (selected >= 0 && selected < ROWS) {
                int y = context.top() + ROWS_Y + selected * ROW - 1;
                graphics.fill(context.left() + X - 2, y, context.left() + RIGHT + 1, y + ROW, 0x4000D992);
            }
        }
        if (tab == TAB_CHANNELS) {
            PanelBody.scrollbar(graphics, context.left(), context.top(), scroll, data == null ? 0 : channels(data).size(), ROWS);
        }
        if (data != null && tab == TAB_LINE && !data.getStringOr("line", "unpaired").equals("unpaired")) {
            PartScreens.wideButton(graphics, context.font(), context.left() + X, context.top() + UNPAIR_Y, UNPAIR_W, UNPAIR_H,
                    Component.translatable("gui.encodedlogistics.mux.unpair"), true, mouseX, mouseY);
        }
    }

    @Override
    public void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        tabs.extractLabels(graphics, context.font(), tab);
        ValueInput data = context.data();
        if (data == null) {
            return;
        }
        if (tab == TAB_LINE) {
            line(graphics, data);
            return;
        }
        PanelBody.divider(graphics, HEAD_Y);
        int networkW = LANES_RIGHT - context.font().width(tr("col.lanes")) - GAP - NETWORK_X;
        text(graphics, tr("col.ch"), X, HEAD_Y, NETWORK_X - GAP - X, RackScreen.TEXT_MUTED);
        text(graphics, tr("col.network"), NETWORK_X, HEAD_Y, networkW, RackScreen.TEXT_MUTED);
        right(graphics, tr("col.lanes"), LANES_RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
        right(graphics, tr("col.up"), RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
        List<ValueInput> rows = channels(data);
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            ValueInput row = rows.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            boolean assigned = row.getBooleanOr("assigned", false), up = row.getBooleanOr("up", false);
            text(graphics, Integer.toString(scroll + i + 1), X, y, NETWORK_X - GAP - X, RackScreen.TEXT);
            text(graphics, assigned ? row.getStringOr("network", "") : "-", NETWORK_X, y, networkW, assigned ? RackScreen.TEXT : RackScreen.TEXT_DISABLED);
            right(graphics, up ? Integer.toString(row.getIntOr("lanes", 0)) : "-", LANES_RIGHT, y, RackScreen.TEXT);
            right(graphics, !assigned ? "-" : tr(up ? "yes" : "no"), RIGHT, y, !assigned ? RackScreen.TEXT_DISABLED : up ? RackScreen.TEXT : RackScreen.ERROR);
        }
    }

    private void line(GuiGraphicsExtractor graphics, ValueInput data) {
        String state = data.getStringOr("line", "unpaired");
        int y = HEAD_Y;
        row(graphics, y, tr("far_end"), state.equals("unpaired") ? tr("none") : data.getStringOr("partner", "?"), RackScreen.TEXT);
        row(graphics, y += ROW, tr("state"), tr("line." + state), state.equals("up") ? RackScreen.ACCENT : state.equals("unpaired") ? RackScreen.TEXT_MUTED
                : RackScreen.ERROR);
        if (state.equals("up")) {
            row(graphics, y += ROW, tr("distance"), Multiplexer.length(data.getIntOr("length", 0)), RackScreen.TEXT);
            row(graphics, y += ROW, tr("span"), Multiplexer.length(data.getIntOr("span", 0)), RackScreen.TEXT);
            int loss = data.getIntOr("loss", 0);
            row(graphics, y += ROW, tr("loss"), tr("loss_value", loss), loss > 0 ? RackScreen.AMBER : RackScreen.TEXT);
            row(graphics, y += ROW, tr("lanes"), Integer.toString(data.getIntOr("lanes", 0)), RackScreen.TEXT);
            row(graphics, y += ROW, tr("quality"), data.getIntOr("quality", 0) + "%", RackScreen.TEXT);
            row(graphics, y += ROW, tr("amplifiers"), Integer.toString(data.getIntOr("amplifiers", 0)), RackScreen.TEXT);
        } else if (state.equals("unpaired")) {
            PanelBody.wrapped(graphics, context.font(), tr("pair_hint"), X, y + 2 * ROW, RIGHT - X, ROW, RackScreen.TEXT_DISABLED);
        }
    }

    // A label at 12 and its value right at 152, the value cut short of the label.
    private void row(GuiGraphicsExtractor graphics, int y, String label, String value, int color) {
        int labelW = context.font().width(label);
        graphics.text(context.font(), label, X, y, RackScreen.TEXT_MUTED, false);
        right(graphics, PanelBody.fit(context.font(), value, RIGHT - X - labelW - GAP * 2), RIGHT, y, color);
    }

    @Override
    public void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        double x = mouseX - context.left(), y = mouseY - context.top();
        ValueInput data = context.data();
        if (data == null || x < X || x >= RIGHT) {
            return;
        }
        if (tab == TAB_LINE) {
            // The far end in full (it's cut to fit beside its label).
            if (y >= HEAD_Y - 1 && y < HEAD_Y + ROW - 1 && !data.getStringOr("line", "unpaired").equals("unpaired")) {
                Tooltips.show(graphics, context.font(), Component.literal(data.getStringOr("partner", "?")), mouseX, mouseY);
            }
            return;
        }
        if (y >= ROWS_Y && y < ROWS_Y + ROWS * ROW) {
            List<ValueInput> rows = channels(data);
            int channel = scroll + (int) ((y - ROWS_Y) / ROW);
            List<Component> lines = new ArrayList<>();
            if (channel < rows.size() && rows.get(channel).getBooleanOr("assigned", false)) {
                lines.add(Component.literal(rows.get(channel).getStringOr("network", "")));
            }
            lines.add(Component.translatable("gui.encodedlogistics.mux.hint").withColor(lines.isEmpty() ? RackScreen.TEXT : RackScreen.TEXT_DISABLED));
            Tooltips.show(graphics, context.font(), lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double amount) {
        ValueInput data = context.data();
        int size = data == null ? 0 : channels(data).size();
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
        if (tab == TAB_LINE) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT && PartScreens.over(x, y, X, UNPAIR_Y, UNPAIR_W, UNPAIR_H)) {
                context.send(Multiplexer.ACTION_UNPAIR, 0, "");
                return true;
            }
            return false;
        }
        if (x < X || x >= RIGHT || y < ROWS_Y || y >= ROWS_Y + ROWS * ROW) {
            return false;
        }
        int channel = scroll + (int) ((y - ROWS_Y) / ROW);
        context.send(button == InputConstants.MOUSE_BUTTON_RIGHT ? Multiplexer.ACTION_CLEAR : Multiplexer.ACTION_SELECT, channel, "");
        return true;
    }
}
