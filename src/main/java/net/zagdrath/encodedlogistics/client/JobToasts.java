/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.screen.TerminalSettings;
import net.zagdrath.encodedlogistics.crafting.JobEvents;
import net.zagdrath.encodedlogistics.net.JobToastPayload;

// Job toasts: a crafting job's end (JobToastPayload) shown top right, if the player's settings want it (TerminalSettings:
// on, whose jobs, which ends, the minimum duration). Ends of the same kind within JobToastBatch.WINDOW share one toast
// ("3 jobs complete"). Drawn in the mod's light grey HUD panel with a coloured accent (mint complete, red failed, grey
// cancelled), with a quiet chime (a lower bell for failures) unless that's off.
public final class JobToasts {
    private JobToasts() {}

    public static void receive(JobToastPayload payload) {
        JobEvents.Outcome outcome = JobEvents.Outcome.byId(payload.outcome());
        if (!JobToastBatch.wanted(TerminalSettings.toasts(), TerminalSettings.toastJobs() == TerminalSettings.ToastJobs.ALL,
                TerminalSettings.toastCompleted(), TerminalSettings.toastFailed(), TerminalSettings.toastCancelled(), TerminalSettings.toastMinimumSeconds(),
                payload.mine(), outcome, payload.duration())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ToastManager toasts = minecraft.getToastManager();
        long now = System.currentTimeMillis();
        JobToastBatch.Event event = new JobToastBatch.Event(payload.item(), payload.amount(), outcome, payload.processing(), payload.reason());
        JobToast showing = toasts.getToast(JobToast.class, outcome);
        if (showing != null && showing.batch.accepts(event, now)) {
            showing.batch.add(event, now);
            showing.restart = true;
        } else {
            toasts.addToast(new JobToast(new JobToastBatch(event, now)));
        }
        if (TerminalSettings.toastSound()) {
            boolean failed = outcome == JobEvents.Outcome.FAILED;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(failed ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_CHIME.value(),
                    failed ? 0.6F : 1.4F, 0.25F));
        }
    }

    // One toast: its batch, shown for DISPLAY_MS after the last job joined it.
    static final class JobToast implements Toast {
        private static final Identifier PANEL = EncodedLogistics.id("hud/panel");
        private static final int PANEL_COLOR = ARGB.color(Math.round(0.92F * 255), 0xFFFFFF);
        private static final int WIDTH = 160, HEIGHT = 32, DISPLAY_MS = 5_000;
        private static final int TEXT = 0xFFF0F0F0, MUTED = 0xFFB4B4B4, MINT = 0xFF00D992, RED = 0xFFFF6B6B, GREY = 0xFF8A8A8A;

        final JobToastBatch batch;
        boolean restart;
        private long shownFrom;
        private Toast.Visibility visibility = Toast.Visibility.SHOW;

        JobToast(JobToastBatch batch) {
            this.batch = batch;
        }

        @Override
        public Object getToken() {
            return batch.outcome();
        }

        @Override
        public int width() {
            return WIDTH;
        }

        @Override
        public int height() {
            return HEIGHT;
        }

        @Override
        public Toast.Visibility getWantedVisibility() {
            return visibility;
        }

        @Override
        public void update(ToastManager manager, long visibleTime) {
            if (restart) {
                restart = false;
                shownFrom = visibleTime;
            }
            visibility = visibleTime - shownFrom < DISPLAY_MS * manager.getNotificationDisplayTimeMultiplier() ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long visibleTime) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, 0, 0, WIDTH, HEIGHT, PANEL_COLOR);
            int accent = switch (batch.outcome()) {
                case COMPLETED -> MINT;
                case FAILED -> RED;
                case CANCELLED -> GREY;
            };
            graphics.fill(1, 2, 3, HEIGHT - 2, accent);
            JobToastBatch.Event first = batch.first();
            graphics.item(first.item().stack(), 8, 8);
            Component title, detail;
            if (batch.count() > 1) {
                title = Component.translatable("gui.encodedlogistics.job.batch." + batch.outcome().name().toLowerCase(java.util.Locale.ROOT), batch.count());
                detail = Component.translatable("gui.encodedlogistics.job.batch.first", first.item().stack().getHoverName(), first.amount());
            } else {
                title = Component.translatable(JobEvents.title(batch.outcome(), first.processing()));
                detail = Component.translatable("gui.encodedlogistics.job.item", first.item().stack().getHoverName(), first.amount());
            }
            graphics.text(font, font.plainSubstrByWidth(title.getString(), WIDTH - 34), 30, 7, batch.outcome() == JobEvents.Outcome.FAILED ? RED : TEXT, false);
            String second = detail.getString();
            if (batch.outcome() == JobEvents.Outcome.FAILED && !first.reason().isEmpty() && batch.count() == 1) {
                second += " - " + Component.translatable("gui.encodedlogistics.job.reason." + first.reason()).getString();
            }
            List<String> lines = List.of(font.plainSubstrByWidth(second, WIDTH - 34));
            graphics.text(font, lines.getFirst(), 30, 18, MUTED, false);
        }
    }
}
