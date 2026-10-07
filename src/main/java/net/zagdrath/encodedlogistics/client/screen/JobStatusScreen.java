/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.ResourceRender;
import net.zagdrath.encodedlogistics.crafting.JobInfo;
import net.zagdrath.encodedlogistics.net.JobCancelPayload;
import net.zagdrath.encodedlogistics.net.JobStatusPayload;

// A job's progress (screens/job_status.json): what it makes and its overall progress, then its steps two to a row, each
// with its own progress (scroll for more), and Cancel job. It asks the server every POLL ticks; once the job is gone it
// says it finished. Opened when a job starts and from the Scheduler Core's screen; closing it goes back there.
public class JobStatusScreen extends Screen {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/job_status.png");
    private static final Identifier MINT = EncodedLogistics.id("common/bar_fill_mint"), GOLD = EncodedLogistics.id("common/bar_fill_gold");
    private static final int WIDTH = 220, HEIGHT = 150, POLL = 10;
    private static final int ITEM_X = 9, ITEM_Y = 21, NAME_X = 32, NAME_Y = 20, BAR_X = 33, BAR_Y = 32, BAR_W = 178;
    private static final int LIST_X = 10, LIST_Y = 50, ROWS = 3, COLUMNS = 2, CELL_W = 100, CELL_H = 22, CELL_BAR_W = 76;
    private static final int CANCEL_X = 166, CANCEL_Y = 124, CANCEL_W = 46, CANCEL_H = 18;

    private final Screen parent;
    private final BlockPos core;
    private final UUID id;
    private @Nullable JobInfo job;
    private boolean gone;
    private int ticks, scroll, left, top;

    public JobStatusScreen(Screen parent, BlockPos core, UUID id) {
        super(Component.translatable("gui.encodedlogistics.craft.status"));
        this.parent = parent;
        this.core = core;
        this.id = id;
    }

    public void update(JobStatusPayload payload) {
        if (payload.core().equals(core) && payload.id().equals(id)) {
            job = payload.job().orElse(null);
            gone = payload.job().isEmpty();
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        ask();
    }

    private void ask() {
        ClientPacketDistributor.sendToServer(new JobStatusPayload(core, id, Optional.empty()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!gone && ++ticks % POLL == 0) {
            ask();
        }
    }

    private int maxScroll() {
        int steps = job != null ? job.steps().size() : 0;
        return Math.max(0, (steps + COLUMNS - 1) / COLUMNS - ROWS);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0.0F, 0.0F, WIDTH, HEIGHT, 256, 256);
        graphics.text(font, title, left + 8, top + 5, PartScreens.TEXT, false);
        if (job != null) {
            ResourceRender.icon(graphics, job.item(), left + ITEM_X, top + ITEM_Y);
            String amount = job.item().isItem() ? Long.toString(job.amount()) : job.item().format(job.amount());
            Component name = Component.literal(amount + " x ").append(job.item().displayName());
            graphics.text(font, font.plainSubstrByWidth(name.getString(), WIDTH - NAME_X - 70), left + NAME_X, top + NAME_Y, PartScreens.TEXT, false);
            // Waiting on tape: how many items are still to come back, else the runs done.
            Component progress = job.awaiting() > 0 ? Component.translatable("gui.encodedlogistics.craft.awaiting", String.format(Locale.ROOT, "%,d", job.awaiting()))
                    : Component.translatable("gui.encodedlogistics.craft.progress", job.done(), job.total());
            graphics.text(font, progress, left + 211 - font.width(progress), top + NAME_Y, job.awaiting() > 0 ? CraftPlanScreen.TAPE_BLUE : PartScreens.TEXT_MUTED,
                    false);
            PartScreens.bar(graphics, MINT, left + BAR_X, top + BAR_Y, BAR_W, job.progress());
            List<JobInfo.StepInfo> steps = job.steps();
            for (int cell = 0; cell < ROWS * COLUMNS; cell++) {
                int index = scroll * COLUMNS + cell;
                if (index >= steps.size()) {
                    break;
                }
                JobInfo.StepInfo step = steps.get(index);
                int x = left + LIST_X + (cell % COLUMNS) * CELL_W, y = top + LIST_Y + (cell / COLUMNS) * CELL_H;
                ResourceRender.icon(graphics, step.output(), x + 2, y + 3);
                graphics.text(font, step.done() + " / " + step.total(), x + 21, y + 4, step.done() >= step.total() ? PartScreens.ACCENT : PartScreens.TEXT,
                        false);
                PartScreens.bar(graphics, GOLD, x + 21, y + 14, CELL_BAR_W, step.total() <= 0 ? 0 : (float) step.done() / step.total());
            }
        } else if (gone) {
            graphics.centeredText(font, Component.translatable("gui.encodedlogistics.craft.finished"), left + WIDTH / 2, top + 80, PartScreens.ACCENT);
        }
        PartScreens.wideButton(graphics, font, left + CANCEL_X, top + CANCEL_Y, CANCEL_W, CANCEL_H,
                Component.translatable("gui.encodedlogistics.scheduler.cancel"), job != null, mouseX, mouseY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (job == null) {
            return;
        }
        if (PartScreens.over(mouseX, mouseY, left + ITEM_X, top + ITEM_Y, 16, 16)) {
            graphics.setTooltipForNextFrame(font, job.item().stack(), mouseX, mouseY);
            return;
        }
        for (int cell = 0; cell < ROWS * COLUMNS; cell++) {
            int index = scroll * COLUMNS + cell;
            int x = left + LIST_X + (cell % COLUMNS) * CELL_W, y = top + LIST_Y + (cell / COLUMNS) * CELL_H;
            if (index < job.steps().size() && PartScreens.over(mouseX, mouseY, x + 2, y + 3, 16, 16)) {
                graphics.setTooltipForNextFrame(font, job.steps().get(index).output().stack(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && job != null && PartScreens.over(event.x(), event.y(), left + CANCEL_X, top + CANCEL_Y, CANCEL_W, CANCEL_H)) {
            ClientPacketDistributor.sendToServer(new JobCancelPayload(core, id));
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
