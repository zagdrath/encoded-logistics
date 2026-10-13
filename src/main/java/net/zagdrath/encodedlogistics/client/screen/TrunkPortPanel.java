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
import net.zagdrath.encodedlogistics.part.TrunkPortPart;

// The Trunk Port's panel: its mode (click: Access / Trunk), native segment, the lanes its segments use, and the segments
// it has (each linked with a Link Card used on it): ID, network, whether it carries it. Click a segment's network to make
// it the native one (again: none), its Carry to carry it or not (a trunk; all while none are picked), right-click to
// unlink it.
final class TrunkPortPanel implements PanelBody {
    private static final int X = 12, RIGHT = 152, MODE_Y = 36, NATIVE_Y = 49, LANES_Y = 62, HEAD_Y = 75, ROWS_Y = 88, ROW = 11, ROWS = 5, CARRY_X = 120;

    private final Context context;
    private int scroll;

    TrunkPortPanel(Context context) {
        this.context = context;
    }

    private record SegmentRow(int id, String network, boolean allowed) {}

    private List<SegmentRow> segments(ValueInput data) {
        List<SegmentRow> rows = new ArrayList<>();
        for (ValueInput row : data.childrenListOrEmpty("segments")) {
            rows.add(new SegmentRow(row.getIntOr("id", 0), row.getStringOr("network", ""), row.getBooleanOr("allowed", true)));
        }
        return rows;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("gui.encodedlogistics.trunk." + key, args).getString();
    }

    private void right(GuiGraphicsExtractor graphics, String text, int y, int color) {
        graphics.text(context.font(), text, RIGHT - context.font().width(text), y, color, false);
    }

    @Override
    public void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ValueInput data = context.data();
        if (data == null) {
            return;
        }
        boolean trunk = data.getStringOr("mode", "TRUNK").equals("TRUNK");
        int nativeId = data.getIntOr("native", 0);
        graphics.text(context.font(), tr("mode"), X, MODE_Y, RackScreen.TEXT_MUTED, false);
        right(graphics, tr(trunk ? "trunk" : "access"), MODE_Y, RackScreen.ACCENT);
        graphics.text(context.font(), tr("native"), X, NATIVE_Y, RackScreen.TEXT_MUTED, false);
        right(graphics, nativeId > 0 ? Integer.toString(nativeId) : "-", NATIVE_Y, RackScreen.TEXT);
        graphics.text(context.font(), tr("lanes"), X, LANES_Y, RackScreen.TEXT_MUTED, false);
        right(graphics, data.getIntOr("lanes_used", 0) + " / " + data.getIntOr("lanes", 0), LANES_Y, RackScreen.TEXT);
        graphics.text(context.font(), tr("col.id"), X, HEAD_Y, RackScreen.TEXT_MUTED, false);
        graphics.text(context.font(), tr("col.network"), 40, HEAD_Y, RackScreen.TEXT_MUTED, false);
        right(graphics, tr("col.carry"), HEAD_Y, RackScreen.TEXT_MUTED);
        List<SegmentRow> rows = segments(data);
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            SegmentRow row = rows.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            boolean carried = trunk ? row.allowed() || row.id() == nativeId : row.id() == nativeId;
            graphics.text(context.font(), Integer.toString(row.id()), X, y, row.id() == nativeId ? SwitchPanel.TRUNK_BLUE : RackScreen.TEXT, false);
            graphics.text(context.font(), context.font().plainSubstrByWidth(row.network().isEmpty() ? tr("unreachable") : row.network(), 76), 40, y,
                    row.network().isEmpty() ? RackScreen.TEXT_DISABLED : RackScreen.TEXT, false);
            right(graphics, tr(carried ? "yes" : "no"), y, carried ? RackScreen.ACCENT : RackScreen.TEXT_DISABLED);
        }
        if (rows.isEmpty()) {
            graphics.text(context.font(), tr("none"), X, ROWS_Y, RackScreen.TEXT_DISABLED, false);
        }
    }

    @Override
    public void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        double x = mouseX - context.left(), y = mouseY - context.top();
        if (x < X || x >= RIGHT) {
            return;
        }
        if (y >= MODE_Y - 2 && y < MODE_Y + 9) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.trunk.hint.mode"), mouseX, mouseY);
        } else if (y >= HEAD_Y - 2) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.trunk.hint.segments"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double amount) {
        ValueInput data = context.data();
        int size = data == null ? 0 : segments(data).size();
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, size - ROWS));
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button, boolean shift) {
        if (x < X || x >= RIGHT) {
            return false;
        }
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (left && y >= MODE_Y - 2 && y < MODE_Y + 9) {
            context.send(TrunkPortPart.ACTION_MODE, 0, "");
            return true;
        }
        if (y < ROWS_Y || y >= ROWS_Y + ROWS * ROW) {
            return false;
        }
        int row = scroll + (int) ((y - ROWS_Y) / ROW);
        if (right) {
            context.send(TrunkPortPart.ACTION_UNLINK, row, "");
        } else if (left) {
            context.send(x >= CARRY_X ? TrunkPortPart.ACTION_ALLOW : TrunkPortPart.ACTION_NATIVE, row, "");
        }
        return true;
    }
}
