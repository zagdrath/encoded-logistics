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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.SwitchDevice;

// An L2 Switch's panel (screens/rack/l2_switch_24.json, l2_switch_48.json): the rack's lane pool (bar and "N / M
// lanes"), the rack's other devices - icon, name, whether the pool carries them, and the segment each serves (click to
// step through the segments the rack knows, right-click back; only pooled devices can change; a scrollbar beside the
// list, as on the rack's own screen) - and the uplink.
public class SwitchPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/switch.png");
    protected static final Identifier LANE_TRACK = EncodedLogistics.id("rack/switch/lane_bar_track"),
            LANE_FILL = EncodedLogistics.id("rack/switch/lane_bar_fill"), POOL_FILL = EncodedLogistics.id("common/bar_fill_mint"),
            DOT_ONLINE = EncodedLogistics.id("hud/dot_online"), DOT_OFFLINE = EncodedLogistics.id("hud/dot_offline");
    private static final Identifier THUMB = EncodedLogistics.id("controller/scroll_thumb"), THUMB_HOVER = EncodedLogistics.id("controller/scroll_thumb_hover");
    protected static final int POOL_X = 9, POOL_W = 98;
    private static final int POOL_Y = 21, UPLINK_Y = 144;
    // Each row: the name (up to NAME_W), the lane bar, the segment button (inside the row, clear of its edges).
    private static final int NAME_W = 50, LANES_X = 66, SEGMENT_X = 104, SEGMENT_W = 38, SEGMENT_H = 10;
    // The scrollbar's track (in the background, beside the list) and its 6x15 thumb one in from the left.
    private static final int TRACK_X = 160, THUMB_W = 6, THUMB_H = 15;

    // A device in the list, as the server sent it.
    protected record Row(int u, RackDeviceType type, boolean pooled, int segment) {}

    protected int scroll;
    private boolean dragging;

    public SwitchPanel(RackScreen screen) {
        super(screen);
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
    }

    // The device list's place: left, top, rows, row height.
    protected int listX() {
        return 10;
    }

    protected int listY() {
        return 33;
    }

    protected int listRows() {
        return 8;
    }

    protected int rowHeight() {
        return 13;
    }

    protected List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        ValueInput data = data();
        if (data == null) {
            return rows;
        }
        for (ValueInput child : data.childrenListOrEmpty("devices")) {
            Identifier id = Identifier.tryParse(child.getStringOr("type", ""));
            RackDeviceType type = id != null ? RackDeviceType.byId(id) : null;
            if (type != null) {
                rows.add(new Row(child.getIntOr("u", 0), type, child.getBooleanOr("pooled", false), child.getIntOr("segment", 0)));
            }
        }
        return rows;
    }

    protected List<String> segments() {
        List<String> names = new ArrayList<>();
        ValueInput data = data();
        if (data != null) {
            for (ValueInput child : data.childrenListOrEmpty("segments")) {
                names.add(child.getStringOr("name", "-"));
            }
        }
        return names;
    }

    protected Component poolText() {
        ValueInput data = data();
        return Component.translatable("gui.encodedlogistics.switch.pool", data != null ? data.getIntOr("pool_used", 0) : 0,
                data != null ? data.getIntOr("pool_capacity", 0) : 0);
    }

    // --- Drawing ---

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        ValueInput data = data();
        if (data != null) {
            int capacity = data.getIntOr("pool_capacity", 0);
            int width = capacity <= 0 ? 0 : Math.round(POOL_W * Mth.clamp((float) data.getIntOr("pool_used", 0) / capacity, 0, 1));
            if (width > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, POOL_FILL, 200, 6, 0, 0, x + POOL_X, y + POOL_Y, width, 4);
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, data.getBooleanOr("uplink", false) ? DOT_ONLINE : DOT_OFFLINE, x + 12, y + UPLINK_Y + 1, 5, 5);
        }
        extractDevices(graphics, mouseX, mouseY);
    }

    // The device rows: icon, lane bar, segment button (names are labels).
    protected void extractDevices(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = screen.left(), y = screen.top();
        List<Row> rows = rows();
        List<String> segments = segments();
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - listRows()));
        for (int i = 0; i < listRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int rowY = y + listY() + i * rowHeight();
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + listX() + 1, rowY);
            graphics.pose().scale(0.75F, 0.75F);
            graphics.item(new ItemStack(row.type().item()), 0, 0);
            graphics.pose().popMatrix();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LANE_TRACK, x + listX() + LANES_X, rowY + 4, 32, 4);
            if (row.pooled()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LANE_FILL, x + listX() + LANES_X + 1, rowY + 5, 30, 2);
            }
            String segment = row.segment() < segments.size() ? segments.get(row.segment()) : "-";
            PartScreens.wideButton(graphics, font(), x + listX() + SEGMENT_X, rowY + 1, SEGMENT_W, SEGMENT_H, Component.literal(segment), row.pooled(),
                    mouseX, mouseY);
        }
        extractScrollbar(graphics, rows.size(), mouseX, mouseY);
    }

    // --- The list's scrollbar ---

    // The track's top and height (its border included), relative to the panel.
    private int trackY() {
        return listY() - 1;
    }

    private int trackH() {
        return listRows() * rowHeight();
    }

    private int maxScroll(int rows) {
        return Math.max(0, rows - listRows());
    }

    private int thumbY(int rows) {
        int max = maxScroll(rows);
        return trackY() + 1 + (max == 0 ? 0 : Math.round((trackH() - 2 - THUMB_H) * (float) scroll / max));
    }

    // The thumb, while there's more than the list shows.
    private void extractScrollbar(GuiGraphicsExtractor graphics, int rows, int mouseX, int mouseY) {
        if (maxScroll(rows) <= 0) {
            return;
        }
        int thumbY = thumbY(rows);
        boolean hover = dragging || screen.over(mouseX, mouseY, TRACK_X + 1, thumbY, THUMB_W, THUMB_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? THUMB_HOVER : THUMB, screen.left() + TRACK_X + 1, screen.top() + thumbY, THUMB_W, THUMB_H);
    }

    private void scrollTo(double y, int rows) {
        double fraction = (y - trackY() - 1 - THUMB_H / 2.0) / (trackH() - 2 - THUMB_H);
        scroll = Mth.clamp((int) Math.round(fraction * maxScroll(rows)), 0, maxScroll(rows));
    }

    // A click on the track: the thumb jumps there and follows the mouse.
    protected boolean clickScrollbar(double x, double y) {
        int rows = rows().size();
        if (maxScroll(rows) > 0 && x >= TRACK_X && x < TRACK_X + 8 && y >= trackY() && y < trackY() + trackH()) {
            dragging = true;
            scrollTo(y, rows);
            return true;
        }
        return false;
    }

    @Override
    protected boolean mouseDragged(double x, double y) {
        if (dragging) {
            scrollTo(y, rows().size());
            return true;
        }
        return false;
    }

    @Override
    protected void mouseReleased() {
        dragging = false;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Component pool = poolText();
        graphics.text(font(), pool, 168 - font().width(pool), 18, RackScreen.TEXT_MUTED, false);
        extractDeviceNames(graphics);
        ValueInput data = data();
        boolean linked = data != null && data.getBooleanOr("uplink", false);
        graphics.text(font(), Component.translatable("gui.encodedlogistics.switch.uplink",
                Component.translatable(linked ? "gui.encodedlogistics.switch.uplink.linked" : "gui.encodedlogistics.switch.uplink.none")), 20, UPLINK_Y,
                linked ? RackScreen.TEXT : RackScreen.TEXT_MUTED, false);
    }

    protected void extractDeviceNames(GuiGraphicsExtractor graphics) {
        List<Row> rows = rows();
        for (int i = 0; i < listRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            String name = font().plainSubstrByWidth(row.type().item().getName(row.type().item().getDefaultInstance()).getString(), NAME_W);
            graphics.text(font(), name, listX() + 15, listY() + i * rowHeight() + 3, row.pooled() ? RackScreen.TEXT : RackScreen.TEXT_MUTED, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Row> rows = rows();
        for (int i = 0; i < listRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int rowY = listY() + i * rowHeight();
            if (screen.over(mouseX, mouseY, listX() + LANES_X, rowY, 32, rowHeight())) {
                graphics.setTooltipForNextFrame(Component.translatable(row.pooled() ? "gui.encodedlogistics.switch.pooled"
                        : "gui.encodedlogistics.switch.unpooled"), mouseX, mouseY);
                return;
            }
            if (screen.over(mouseX, mouseY, listX() + SEGMENT_X, rowY + 1, SEGMENT_W, SEGMENT_H)) {
                graphics.setComponentTooltipForNextFrame(font(), List.of(Component.translatable("gui.encodedlogistics.switch.segment"),
                        Component.translatable(row.pooled() ? "gui.encodedlogistics.switch.segment_hint" : "gui.encodedlogistics.switch.segment_unpooled")
                                .withColor(RackScreen.TEXT_MUTED),
                        Component.translatable("gui.encodedlogistics.switch.link_hint").withColor(RackScreen.TEXT_DISABLED)), mouseX, mouseY);
                return;
            }
        }
    }

    // --- Input ---

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        return clickScrollbar(x, y) || clickDevices(x, y, button);
    }

    protected boolean clickDevices(double x, double y, int button) {
        List<Row> rows = rows();
        for (int i = 0; i < listRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int rowY = listY() + i * rowHeight();
            if (x >= listX() + SEGMENT_X && x < listX() + SEGMENT_X + SEGMENT_W && y >= rowY + 1 && y < rowY + 1 + SEGMENT_H && row.pooled()) {
                send(SwitchDevice.ACTION_CYCLE_SEGMENT, row.u(), button == InputConstants.MOUSE_BUTTON_RIGHT ? "back" : "");
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean mouseScrolled(double x, double y, double amount) {
        if (x >= listX() && x < TRACK_X + 8 && y >= listY() && y < listY() + listRows() * rowHeight()) {
            scroll = Mth.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, rows().size() - listRows()));
            return true;
        }
        return false;
    }
}
