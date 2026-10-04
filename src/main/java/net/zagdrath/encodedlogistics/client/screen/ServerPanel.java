/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.net.JobCancelPayload;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.device.ComputeServerDevice;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;

// A Compute or Memory Server's panel (screens/rack/compute_server.json, memory_server.json; gui/rack/server.png): what it
// provides, what the rack's Scheduler uses of it, whether the Scheduler is active, a usage bar, and the Scheduler's jobs,
// each with a cancel X (what it holds goes back into the network), and under them the last few it finished
// (RecentJobs, with a hint to Work with Jobs' history).
public class ServerPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/server.png"),
            BAR = EncodedLogistics.id("common/bar_fill_gold"), CANCEL = EncodedLogistics.id("common/cancel_small");
    private static final int LABEL_X = 12, VALUE_RIGHT = 164, READOUT_Y = 25, LINE_H = 11, BAR_X = 9, BAR_Y = 64, BAR_W = 158, JOBS_Y = 79;
    private static final int JOB_ROWS = 3, JOB_H = 10, CANCEL_SIZE = 9, CANCEL_X = VALUE_RIGHT - CANCEL_SIZE;
    // Under the divider (texture row 107): the heading, then the finished jobs, 9 apart.
    private static final int RECENT_LABEL_Y = 110, RECENT_Y = 120, RECENT_H = 9;

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
        int rows = Math.min(JOB_ROWS, data.childrenListOrEmpty("jobs").stream().toList().size());
        for (int i = 0; i < rows; i++) {
            boolean hover = screen.over(mouseX, mouseY, CANCEL_X, JOBS_Y + i * JOB_H - 1, CANCEL_SIZE, CANCEL_SIZE);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CANCEL, screen.left() + CANCEL_X, screen.top() + JOBS_Y + i * JOB_H - 1, CANCEL_SIZE,
                    CANCEL_SIZE, hover ? 0xFFFFFFFF : 0xFFC8C8C8);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ValueInput data = data();
        if (data == null) {
            return;
        }
        int rows = Math.min(JOB_ROWS, data.childrenListOrEmpty("jobs").stream().toList().size());
        for (int i = 0; i < rows; i++) {
            if (screen.over(mouseX, mouseY, CANCEL_X, JOBS_Y + i * JOB_H - 1, CANCEL_SIZE, CANCEL_SIZE)) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.scheduler.cancel"), mouseX, mouseY);
            }
        }
        RecentJobs.Row finished = RecentJobs.at(recent(data), mouseX - screen.left(), mouseY - screen.top(), LABEL_X, RECENT_Y, VALUE_RIGHT - LABEL_X,
                RECENT_H);
        if (finished != null) {
            graphics.setComponentTooltipForNextFrame(font(), RecentJobs.tooltip(finished), mouseX, mouseY);
        }
    }

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        ValueInput data = data();
        RackDevice device = screen.pickedDevice();
        if (button != InputConstants.MOUSE_BUTTON_LEFT || data == null || device == null || device.rack() == null) {
            return false;
        }
        List<ValueInput> jobs = data.childrenListOrEmpty("jobs").stream().toList();
        for (int i = 0; i < Math.min(JOB_ROWS, jobs.size()); i++) {
            int top = JOBS_Y + i * JOB_H - 1;
            if (x >= CANCEL_X && x < CANCEL_X + CANCEL_SIZE && y >= top && y < top + CANCEL_SIZE) {
                UUID id = parse(jobs.get(i).getStringOr("id", ""));
                if (id != null) {
                    ClientPacketDistributor.sendToServer(new JobCancelPayload(device.rack().getBlockPos(), id));
                }
                return true;
            }
        }
        return false;
    }

    private static @Nullable UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
            // No Scheduler, no jobs: why, where they'd be.
            graphics.text(font(), font().plainSubstrByWidth(Component.translatable("gui.encodedlogistics.server.scheduler.inactive").getString(),
                    VALUE_RIGHT - LABEL_X), LABEL_X, JOBS_Y, RackScreen.TEXT_DISABLED, false);
            return;
        }
        List<String> jobs = new ArrayList<>();
        for (ValueInput job : data.childrenListOrEmpty("jobs")) {
            jobs.add(Component.translatable(job.getBooleanOr("running", false) ? "gui.encodedlogistics.server.job.running"
                    : "gui.encodedlogistics.server.job.queued", job.getStringOr("target", "?"), job.getLongOr("amount", 0)).getString());
        }
        if (jobs.isEmpty()) {
            graphics.text(font(), Component.translatable("gui.encodedlogistics.server.no_jobs"), LABEL_X, JOBS_Y, RackScreen.TEXT_DISABLED, false);
        }
        for (int i = 0; i < Math.min(JOB_ROWS, jobs.size()); i++) {
            graphics.text(font(), font().plainSubstrByWidth(jobs.get(i), CANCEL_X - 4 - LABEL_X), LABEL_X, JOBS_Y + i * JOB_H, i == 0 ? RackScreen.TEXT : RackScreen.TEXT_MUTED,
                    false);
        }
        RecentJobs.heading(graphics, font(), LABEL_X, RECENT_LABEL_Y, VALUE_RIGHT);
        RecentJobs.rows(graphics, font(), recent(data), LABEL_X, RECENT_Y, VALUE_RIGHT - LABEL_X, RECENT_H);
    }

    private static List<RecentJobs.Row> recent(ValueInput data) {
        List<RecentJobs.Row> rows = new ArrayList<>();
        for (ValueInput job : data.childrenListOrEmpty("recent")) {
            rows.add(new RecentJobs.Row(Component.literal(job.getStringOr("target", "?")), job.getLongOr("requested", 0), job.getLongOr("produced", 0),
                    job.getIntOr("status", 0), job.getStringOr("reason", ""), job.getStringOr("ended", ""), job.getLongOr("duration", 0)));
        }
        return rows;
    }

    private void row(GuiGraphicsExtractor graphics, int line, String key, Component value, int color) {
        int y = READOUT_Y + line * LINE_H;
        graphics.text(font(), Component.translatable("gui.encodedlogistics.server." + key), LABEL_X, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), value, VALUE_RIGHT - font().width(value), y, color, false);
    }
}
