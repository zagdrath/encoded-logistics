/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.device.ComputeServerDevice;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;

// A Compute or Memory Server's panel (screens/rack/compute_server.json, memory_server.json; gui/rack/server.png): what it
// provides, what the rack's Scheduler uses of it, whether the Scheduler is active, a usage bar, and the Scheduler's jobs.
public class ServerPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/server.png"),
            BAR = EncodedLogistics.id("common/bar_fill_gold");
    private static final int LABEL_X = 12, VALUE_RIGHT = 164, READOUT_Y = 26, LINE_H = 12, BAR_X = 9, BAR_Y = 101, BAR_W = 158, JOBS_Y = 122;

    public ServerPanel(RackScreen screen) {
        super(screen);
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
    }

    private boolean compute() {
        RackDevice device = screen.pickedDevice();
        return device instanceof ComputeServerDevice;
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ValueInput data = data();
        if (data == null || !data.getBooleanOr("active", false)) {
            return;
        }
        float used = compute() ? (float) data.getIntOr("threads_used", 0) / Math.max(1, data.getIntOr("threads", 1))
                : (float) data.getLongOr("memory_used", 0) / Math.max(1, data.getLongOr("memory", 1));
        PartScreens.bar(graphics, BAR, screen.left() + BAR_X, screen.top() + BAR_Y, BAR_W, used);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ValueInput data = data();
        if (data == null) {
            return;
        }
        boolean active = data.getBooleanOr("active", false);
        if (compute()) {
            row(graphics, 0, "threads_provided", Component.literal(Integer.toString(Config.COMPUTE_SERVER_THREADS.getAsInt())), RackScreen.TEXT);
            row(graphics, 1, "threads_in_use", Component.literal(active ? data.getIntOr("threads_used", 0) + " / " + data.getIntOr("threads", 0) : "—"),
                    RackScreen.TEXT);
        } else {
            row(graphics, 0, "buffer_capacity", Component.literal(MemoryServerDevice.memory(Config.MEMORY_SERVER_MEMORY.getAsInt())), RackScreen.TEXT);
            row(graphics, 1, "buffer_in_use", Component.literal(active ? MemoryServerDevice.memory(data.getLongOr("memory_used", 0)) + " / "
                    + MemoryServerDevice.memory(data.getLongOr("memory", 0)) : "—"), RackScreen.TEXT);
        }
        row(graphics, 2, "scheduler", Component.translatable(active ? "gui.encodedlogistics.server.scheduler.active" : "gui.encodedlogistics.server.scheduler.inactive_short"),
                active ? RackScreen.ACCENT : RackScreen.TEXT_MUTED);
        if (!active) {
            graphics.text(font(), Component.translatable("gui.encodedlogistics.server.scheduler.inactive"), LABEL_X, READOUT_Y + 3 * LINE_H + 4,
                    RackScreen.TEXT_DISABLED, false);
        }
        List<String> jobs = new ArrayList<>();
        for (ValueInput job : data.childrenListOrEmpty("jobs")) {
            jobs.add(Component.translatable(job.getBooleanOr("running", false) ? "gui.encodedlogistics.server.job.running"
                    : "gui.encodedlogistics.server.job.queued", job.getStringOr("target", "?"), job.getLongOr("amount", 0)).getString());
        }
        if (jobs.isEmpty()) {
            graphics.text(font(), Component.translatable("gui.encodedlogistics.server.no_jobs"), LABEL_X, JOBS_Y, RackScreen.TEXT_DISABLED, false);
        }
        for (int i = 0; i < Math.min(3, jobs.size()); i++) {
            graphics.text(font(), font().plainSubstrByWidth(jobs.get(i), 152), LABEL_X, JOBS_Y + i * 12, i == 0 ? RackScreen.TEXT : RackScreen.TEXT_MUTED,
                    false);
        }
    }

    private void row(GuiGraphicsExtractor graphics, int line, String key, Component value, int color) {
        int y = READOUT_Y + line * LINE_H;
        graphics.text(font(), Component.translatable("gui.encodedlogistics.server." + key), LABEL_X, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), value, VALUE_RIGHT - font().width(value), y, color, false);
    }
}
