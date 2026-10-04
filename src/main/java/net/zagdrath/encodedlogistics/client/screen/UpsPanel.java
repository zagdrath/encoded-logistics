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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;

// The UPS's panel (screens/rack/ups.json): the battery gauge (the controller's energy bar), Charge / Load / Runtime /
// Source, the mode button (Online or Standby), the load against the rated output, and the last three switchovers.
public class UpsPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/ups.png");
    private static final Identifier ENERGY_BAR = EncodedLogistics.id("controller/energy_bar"), LOAD_BAR = EncodedLogistics.id("common/bar_fill_gold");
    private static final Identifier MODE_ONLINE = EncodedLogistics.id("rack/ups/mode_online"), MODE_STANDBY = EncodedLogistics.id("rack/ups/mode_standby");
    private static final int GAUGE_X = 9, GAUGE_Y = 23, GAUGE_W = 10, GAUGE_H = 50;
    private static final int LABEL_X = 30, VALUE_RIGHT = 165, READOUT_Y = 25, LINE_H = 12;
    private static final int MODE_X = 8, MODE_Y = 80, MODE_SIZE = 14, LOAD_X = 27, LOAD_Y = 85, LOAD_W = 140;
    private static final int LOG_TITLE_Y = 95, LOG_X = 10, LOG_Y = 106, LOG_H = 12;
    private static final int ALARM_X = 152, ALARM_Y = 93, ALARM_SIZE = 14;
    private static final Identifier ALARM_ON = EncodedLogistics.id("rack/ups/alarm_on"), ALARM_MUTED = EncodedLogistics.id("rack/ups/alarm_muted");

    public UpsPanel(RackScreen screen) {
        super(screen);
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
    }

    private @Nullable UpsDevice ups() {
        return device(UpsDevice.class);
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        UpsDevice ups = ups();
        ValueInput data = data();
        if (ups == null || data == null) {
            return;
        }
        long capacity = Math.max(1, data.getLongOr("capacity", UpsDevice.capacity()));
        int height = (int) Math.min(GAUGE_H, Math.round((double) ups.stored() * GAUGE_H / capacity));
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_BAR, GAUGE_W, GAUGE_H, 0, GAUGE_H - height, x + GAUGE_X,
                    y + GAUGE_Y + GAUGE_H - height, GAUGE_W, height);
        }
        boolean hover = screen.over(mouseX, mouseY, MODE_X, MODE_Y, MODE_SIZE, MODE_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? PartScreens.BUTTON_HOVER : PartScreens.BUTTON, x + MODE_X, y + MODE_Y, MODE_SIZE,
                MODE_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ups.mode() == UpsDevice.Mode.ONLINE ? MODE_ONLINE : MODE_STANDBY, x + MODE_X + 1,
                y + MODE_Y + 1, 12, 12);
        boolean alarmHover = ups.onBattery() && screen.over(mouseX, mouseY, ALARM_X, ALARM_Y, ALARM_SIZE, ALARM_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, alarmHover ? PartScreens.BUTTON_HOVER : PartScreens.BUTTON, x + ALARM_X, y + ALARM_Y, ALARM_SIZE,
                ALARM_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ups.muted() ? ALARM_MUTED : ALARM_ON, x + ALARM_X + 1, y + ALARM_Y + 1, 12, 12,
                ups.onBattery() ? 0xFFFFFFFF : 0xFF808080);
        PartScreens.bar(graphics, LOAD_BAR, x + LOAD_X, y + LOAD_Y, LOAD_W,
                (float) (data.getDoubleOr("load", 0) / Math.max(1, data.getIntOr("max_output", UpsDevice.maxOutput()))));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UpsDevice ups = ups();
        ValueInput data = data();
        if (ups == null || data == null) {
            return;
        }
        long capacity = Math.max(1, data.getLongOr("capacity", UpsDevice.capacity()));
        int percent = (int) Math.min(100, ups.stored() * 100 / capacity);
        row(graphics, 0, "charge", Component.literal(percent + "%"), percent > 50 ? RackScreen.ACCENT : percent >= 20 ? RackScreen.WARNING
                : RackScreen.ERROR);
        row(graphics, 1, "load", Component.literal(UpsDevice.rate(data.getDoubleOr("load", 0))), RackScreen.TEXT);
        row(graphics, 2, "runtime", UpsDevice.runtime(data.getLongOr("runtime", -1)), RackScreen.TEXT);
        row(graphics, 3, "source", Component.translatable(ups.onBattery() ? "gui.encodedlogistics.ups.source.battery"
                : "gui.encodedlogistics.ups.source.mains"), ups.onBattery() ? RackScreen.WARNING : RackScreen.TEXT);
        row(graphics, 4, "status", Component.literal(data.getStringOr("status", "")), ups.onBattery() ? RackScreen.AMBER : RackScreen.TEXT);

        graphics.text(font(), Component.translatable("gui.encodedlogistics.ups.log"), 8, LOG_TITLE_Y, RackScreen.TEXT_MUTED, false);
        long now = data.getLongOr("now", 0);
        List<UpsDevice.Event> log = ups.log();
        if (log.isEmpty()) {
            graphics.text(font(), Component.translatable("gui.encodedlogistics.ups.log.empty"), LOG_X, LOG_Y, RackScreen.TEXT_DISABLED, false);
        }
        for (int i = 0; i < log.size(); i++) {
            UpsDevice.Event event = log.get(i);
            Component text = Component.translatable(event.toBattery() ? "gui.encodedlogistics.ups.event.battery" : "gui.encodedlogistics.ups.event.mains",
                    ago(now - event.time()));
            graphics.text(font(), font().plainSubstrByWidth(text.getString(), 156), LOG_X, LOG_Y + i * LOG_H, i == 0 ? RackScreen.TEXT
                    : RackScreen.TEXT_MUTED, false);
        }
    }

    private void row(GuiGraphicsExtractor graphics, int line, String key, Component value, int color) {
        int y = READOUT_Y + line * LINE_H;
        graphics.text(font(), Component.translatable("gui.encodedlogistics.ups." + key), LABEL_X, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), value, VALUE_RIGHT - font().width(value), y, color, false);
    }

    // "42s", "5m", "3h" ago.
    private static String ago(long ticks) {
        long seconds = Math.max(0, ticks / 20);
        return seconds < 60 ? seconds + "s" : seconds < 3600 ? seconds / 60 + "m" : seconds / 3600 + "h";
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UpsDevice ups = ups();
        if (ups != null && screen.over(mouseX, mouseY, ALARM_X, ALARM_Y, ALARM_SIZE, ALARM_SIZE)) {
            graphics.setComponentTooltipForNextFrame(font(), List.of(Component.translatable("gui.encodedlogistics.ups.alarm",
                    Component.translatable(ups.muted() ? "gui.encodedlogistics.ups.alarm.muted" : "gui.encodedlogistics.ups.alarm.on")),
                    Component.translatable(ups.onBattery() ? "gui.encodedlogistics.ups.alarm.hint" : "gui.encodedlogistics.ups.alarm.idle")
                            .withColor(RackScreen.TEXT_MUTED)), mouseX, mouseY);
        } else if (ups != null && screen.over(mouseX, mouseY, MODE_X, MODE_Y, MODE_SIZE, MODE_SIZE)) {
            graphics.setComponentTooltipForNextFrame(font(), List.of(ups.mode().label(),
                    Component.translatable("gui.encodedlogistics.ups.mode.hint").withColor(RackScreen.TEXT_MUTED)), mouseX, mouseY);
        } else if (ups != null && screen.over(mouseX, mouseY, GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H)) {
            graphics.setTooltipForNextFrame(Component.literal(String.format(Locale.ROOT, "%,d / %,d FE", ups.stored(), UpsDevice.capacity())),
                    mouseX, mouseY);
        }
    }

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && x >= MODE_X && x < MODE_X + MODE_SIZE && y >= MODE_Y && y < MODE_Y + MODE_SIZE) {
            send(UpsDevice.ACTION_TOGGLE_MODE, 0, "");
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && x >= ALARM_X && x < ALARM_X + ALARM_SIZE && y >= ALARM_Y && y < ALARM_Y + ALARM_SIZE) {
            send(UpsDevice.ACTION_TOGGLE_ALARM, 0, "");
            return true;
        }
        return false;
    }
}
