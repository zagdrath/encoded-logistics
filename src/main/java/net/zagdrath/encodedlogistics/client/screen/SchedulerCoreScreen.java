/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.JobInfo;
import net.zagdrath.encodedlogistics.menu.SchedulerCoreMenu;
import net.zagdrath.encodedlogistics.multiblock.SchedulerStructures;
import net.zagdrath.encodedlogistics.net.JobCancelPayload;
import net.zagdrath.encodedlogistics.net.SchedulerStatusPayload;

// The Scheduler Core's screen (screens/scheduler_core.json): the running jobs (icon, name x amount, progress, cancel X),
// the queue, and meters for threads (gold) and job memory (mint). Click a job to see its status. While the structure
// isn't formed it says why instead.
public class SchedulerCoreScreen extends AbstractContainerScreen<SchedulerCoreMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/scheduler_core.png");
    private static final Identifier MINT = EncodedLogistics.id("common/bar_fill_mint"), GOLD = EncodedLogistics.id("common/bar_fill_gold"),
            CANCEL = EncodedLogistics.id("common/cancel_small"), HIGHLIGHT = EncodedLogistics.id("common/row_highlight");
    private static final int LIST_X = 10, LIST_W = 188, ROW = 20, ACTIVE_Y = 30, ACTIVE_ROWS = 3, QUEUE_Y = 110, QUEUE_ROWS = 2;
    private static final int PROGRESS_W = 120, CANCEL_SIZE = 9, METER_Y = 163, METER_W = 90, THREADS_X = 9, BUFFER_X = 109;

    private int activeScroll, queueScroll;

    public SchedulerCoreScreen(SchedulerCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 208, 186);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
    }

    private List<JobInfo> active() {
        return menu.status().jobs().stream().filter(JobInfo::running).toList();
    }

    private List<JobInfo> queued() {
        return menu.status().jobs().stream().filter(job -> !job.running()).toList();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        SchedulerStatusPayload status = menu.status();
        List<JobInfo> active = active(), queued = queued();
        activeScroll = Mth.clamp(activeScroll, 0, Math.max(0, active.size() - ACTIVE_ROWS));
        queueScroll = Mth.clamp(queueScroll, 0, Math.max(0, queued.size() - QUEUE_ROWS));
        for (int row = 0; row < ACTIVE_ROWS && activeScroll + row < active.size(); row++) {
            JobInfo job = active.get(activeScroll + row);
            int ry = y + ACTIVE_Y + row * ROW;
            if (PartScreens.over(mouseX, mouseY, x + LIST_X, ry, LIST_W, ROW)) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT, 200, 18, 0, 0, x + LIST_X, ry + 1, LIST_W, ROW - 2);
            }
            graphics.item(job.item().stack(), x + LIST_X + 2, ry + 2);
            graphics.text(font, name(job, LIST_W - 22 - 20), x + LIST_X + 22, ry + 2, PartScreens.TEXT, false);
            PartScreens.bar(graphics, MINT, x + LIST_X + 22, ry + 12, PROGRESS_W, job.progress());
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CANCEL, cancelX(), ry + 5, CANCEL_SIZE, CANCEL_SIZE);
        }
        for (int row = 0; row < QUEUE_ROWS && queueScroll + row < queued.size(); row++) {
            JobInfo job = queued.get(queueScroll + row);
            int ry = y + QUEUE_Y + row * ROW;
            graphics.item(job.item().stack(), x + LIST_X + 2, ry + 2);
            graphics.text(font, name(job, LIST_W - 22 - 20), x + LIST_X + 22, ry + 6, PartScreens.TEXT_MUTED, false);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CANCEL, cancelX(), ry + 5, CANCEL_SIZE, CANCEL_SIZE);
        }
        PartScreens.bar(graphics, GOLD, x + THREADS_X, y + METER_Y, METER_W, status.threads() <= 0 ? 0 : (float) status.threadsUsed() / status.threads());
        PartScreens.bar(graphics, MINT, x + BUFFER_X, y + METER_Y, METER_W, status.memory() <= 0 ? 0 : (float) status.memoryUsed() / status.memory());
    }

    private int cancelX() {
        return leftPos + LIST_X + LIST_W - 6 - CANCEL_SIZE;
    }

    private String name(JobInfo job, int width) {
        return font.plainSubstrByWidth(job.amount() + " x " + job.item().stack().getHoverName().getString(), width);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SchedulerStatusPayload status = menu.status();
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.scheduler.active"), 8, 18, PartScreens.TEXT_MUTED, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.scheduler.queue"), 8, 98, PartScreens.TEXT_MUTED, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.scheduler.threads", status.threadsUsed(), status.threads()), 8, 172,
                PartScreens.TEXT_MUTED, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.scheduler.buffer", AbstractTerminalScreen.abbreviate(status.memoryUsed()),
                AbstractTerminalScreen.abbreviate(status.memory())), 108, 172, PartScreens.TEXT_MUTED, false);
        if (!status.formed() && status.containerId() >= 0) {
            SchedulerStructures.Problem problem = SchedulerStructures.Problem.values()[Mth.clamp(status.problem(), 0,
                    SchedulerStructures.Problem.values().length - 1)];
            Component text = problem == SchedulerStructures.Problem.NONE ? Component.translatable("gui.encodedlogistics.scheduler.unformed")
                    : Component.translatable(problem.key());
            graphics.centeredText(font, text, imageWidth / 2, ACTIVE_Y + ACTIVE_ROWS * ROW / 2 - 4, PartScreens.ERROR);
        } else if (menu.status().jobs().isEmpty()) {
            graphics.centeredText(font, Component.translatable("gui.encodedlogistics.scheduler.idle"), imageWidth / 2, ACTIVE_Y + ACTIVE_ROWS * ROW / 2 - 4,
                    PartScreens.TEXT_MUTED);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        JobInfo job = jobAt(mouseX, mouseY);
        if (job != null) {
            if (mouseX >= cancelX() && mouseX < cancelX() + CANCEL_SIZE) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.scheduler.cancel"), mouseX, mouseY);
            } else {
                graphics.setComponentTooltipForNextFrame(font, List.of(job.item().stack().getHoverName(),
                        Component.translatable("gui.encodedlogistics.craft.progress", job.done(), job.total()).withColor(PartScreens.TEXT_MUTED)),
                        mouseX, mouseY);
            }
        }
    }

    private JobInfo jobAt(double mouseX, double mouseY) {
        if (mouseX < leftPos + LIST_X || mouseX >= leftPos + LIST_X + LIST_W) {
            return null;
        }
        int activeRow = Mth.floor((mouseY - topPos - ACTIVE_Y) / ROW), queueRow = Mth.floor((mouseY - topPos - QUEUE_Y) / ROW);
        List<JobInfo> active = active(), queued = queued();
        if (activeRow >= 0 && activeRow < ACTIVE_ROWS && activeScroll + activeRow < active.size()) {
            return active.get(activeScroll + activeRow);
        }
        if (queueRow >= 0 && queueRow < QUEUE_ROWS && queueScroll + queueRow < queued.size()) {
            return queued.get(queueScroll + queueRow);
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        JobInfo job = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? jobAt(event.x(), event.y()) : null;
        if (job != null) {
            if (event.x() >= cancelX() && event.x() < cancelX() + CANCEL_SIZE) {
                ClientPacketDistributor.sendToServer(new JobCancelPayload(menu.pos(), job.id()));
            } else {
                minecraft.gui.setScreen(new JobStatusScreen(this, menu.pos(), job.id()));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = -(int) Math.signum(scrollY);
        if (mouseY < topPos + QUEUE_Y - 12) {
            activeScroll = Math.max(0, activeScroll + step);
        } else {
            queueScroll = Math.max(0, queueScroll + step);
        }
        return true;
    }
}
