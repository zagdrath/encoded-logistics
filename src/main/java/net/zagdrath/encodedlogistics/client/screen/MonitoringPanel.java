/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.device.MonitoringServerDevice;

// The Monitoring Server's panel (screens/rack/monitoring_server.json): pick a stat (item flow, energy use, lane usage,
// crafting throughput) and a range (1m, 10m, 1h, 1d); the graph plots it in the stat's colour, scaled to its highest
// value (shown top left), with the latest value under it and the time and value under the mouse.
public class MonitoringPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/monitoring_server.png");
    private static final String[] STATS = { "items", "energy", "lanes", "jobs" };
    private static final String[] RANGES = { "1m", "10m", "1h", "1d" };
    private static final int STAT_X = 8, STAT_Y = 18, STAT_STEP = 20, RANGE_X = 92, RANGE_Y = 21, RANGE_STEP = 19, RANGE_W = 18, RANGE_H = 12;
    private static final int PLOT_X = 10, PLOT_Y = 46, PLOT_W = 156, PLOT_H = 100, NOW_Y = 152;

    public MonitoringPanel(RackScreen screen) {
        super(screen);
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
    }

    private int stat() {
        ValueInput data = data();
        return data != null ? Math.clamp(data.getIntOr("stat", 0), 0, 3) : 0;
    }

    private int range() {
        ValueInput data = data();
        return data != null ? Math.clamp(data.getIntOr("range", 0), 0, 3) : 0;
    }

    private float[] series() {
        ValueInput data = data();
        return data != null ? MonitoringServerDevice.floats(data.getIntArray("series").orElse(new int[0])) : new float[0];
    }

    private static float max(float[] series) {
        float max = 0;
        for (float value : series) {
            max = Math.max(max, value);
        }
        return max <= 0 ? 1 : max;
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        int stat = stat(), range = range();
        for (int i = 0; i < STATS.length; i++) {
            int bx = x + STAT_X + i * STAT_STEP, by = y + STAT_Y;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, i == stat || PartScreens.over(mouseX, mouseY, bx, by, 18, 18) ? PartScreens.BUTTON_HOVER
                    : PartScreens.BUTTON, bx, by, 18, 18);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EncodedLogistics.id("rack/monitor/stat_" + STATS[i]), bx + 1, by + 1, 16, 16,
                    i == stat ? 0xFFFFFFFF : 0xFF9A9A9A);
            if (i == stat) {
                graphics.fill(bx, by + 17, bx + 18, by + 18, RackScreen.ACCENT);
            }
        }
        for (int i = 0; i < RANGES.length; i++) {
            PartScreens.wideButton(graphics, font(), x + RANGE_X + i * RANGE_STEP, y + RANGE_Y, RANGE_W, RANGE_H, Component.literal(RANGES[i]), i != range,
                    mouseX, mouseY);
        }
        // The plot: one 2x2 dot per pixel column, joined down to the next one so the line has no gaps.
        float[] series = series();
        if (series.length == 0) {
            return;
        }
        Identifier line = EncodedLogistics.id("rack/monitor/line_" + STATS[stat]);
        float max = max(series);
        int lastY = Integer.MIN_VALUE;
        for (int px = 0; px < PLOT_W - 1; px++) {
            int index = Math.min(series.length - 1, (int) ((long) px * series.length / (PLOT_W - 1)));
            int py = PLOT_Y + PLOT_H - 2 - Math.round(series[index] / max * (PLOT_H - 2));
            int from = lastY == Integer.MIN_VALUE ? py : Math.min(py, lastY), to = lastY == Integer.MIN_VALUE ? py : Math.max(py, lastY);
            for (int dy = from; dy <= to; dy += 2) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, line, x + PLOT_X + px, y + dy, 2, 2);
            }
            lastY = py;
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int stat = stat();
        float[] series = series();
        if (series.length > 0) {
            graphics.text(font(), MonitoringServerDevice.format(stat, max(series)), PLOT_X + 2, PLOT_Y + 2, RackScreen.TEXT_MUTED, false);
        }
        ValueInput data = data();
        float now = data != null ? MonitoringServerDevice.floats(data.getIntArray("now").orElse(new int[4]))[stat] : 0;
        graphics.text(font(), Component.translatable("gui.encodedlogistics.monitor.now", MonitoringServerDevice.format(stat, now)), 8, NOW_Y,
                RackScreen.TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < STATS.length; i++) {
            if (screen.over(mouseX, mouseY, STAT_X + i * STAT_STEP, STAT_Y, 18, 18)) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.monitor." + STATS[i]), mouseX, mouseY);
                return;
            }
        }
        float[] series = series();
        if (series.length == 0 || !screen.over(mouseX, mouseY, PLOT_X, PLOT_Y, PLOT_W, PLOT_H)) {
            return;
        }
        int px = mouseX - screen.left() - PLOT_X;
        int index = Math.min(series.length - 1, (int) ((long) px * series.length / (PLOT_W - 1)));
        long secondsAgo = (long) (series.length - 1 - index) * MonitoringServerDevice.step(range());
        graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.monitor.point", ago(secondsAgo),
                MonitoringServerDevice.format(stat(), series[index])), mouseX, mouseY);
    }

    private static String ago(long seconds) {
        return seconds < 60 ? seconds + "s" : seconds < 3600 ? seconds / 60 + "m " + seconds % 60 + "s" : seconds / 3600 + "h " + seconds / 60 % 60 + "m";
    }

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int i = 0; i < STATS.length; i++) {
            if (x >= STAT_X + i * STAT_STEP && x < STAT_X + i * STAT_STEP + 18 && y >= STAT_Y && y < STAT_Y + 18) {
                send(MonitoringServerDevice.ACTION_STAT, i, "");
                return true;
            }
        }
        for (int i = 0; i < RANGES.length; i++) {
            if (x >= RANGE_X + i * RANGE_STEP && x < RANGE_X + i * RANGE_STEP + RANGE_W && y >= RANGE_Y && y < RANGE_Y + RANGE_H) {
                send(MonitoringServerDevice.ACTION_RANGE, i, "");
                return true;
            }
        }
        return false;
    }
}
