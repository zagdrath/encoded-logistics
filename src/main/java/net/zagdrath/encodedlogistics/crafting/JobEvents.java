/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.net.JobToastPayload;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// A crafting job ended: completed, failed (its Scheduler or rack was broken while it ran) or cancelled. Whatever ends it
// (JobRunner's hosts) reports it here, so it doesn't matter how its outputs came back. The requester, when online,
// gets a toast (JobToastPayload, theirs); everyone else online who may view the network gets one too, marked not
// theirs, for players who want every job (each client filters by its own settings). An offline requester gets a
// message in their Terminal OS queue instead. allowJobToasts off sends no toasts (the queue message still goes).
public final class JobEvents {
    public enum Outcome {
        COMPLETED, FAILED, CANCELLED;

        private static final Outcome[] VALUES = values();

        public static Outcome byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : COMPLETED;
        }
    }

    // Reasons a job failed (lang keys under gui.encodedlogistics.job.reason).
    public static final String SCHEDULER_REMOVED = "scheduler_removed";

    // Who was told about an end (for game tests): each toast's player and whether it was theirs (whether or not their
    // client has the mod to show it), and a queue message.
    public record Notice(@Nullable ServerPlayer player, boolean mine, @Nullable String queuedFor, JobToastPayload toast) {}

    private static final List<Consumer<Notice>> LISTENERS = new ArrayList<>();

    private JobEvents() {}

    public static void listen(Consumer<Notice> listener) {
        LISTENERS.add(listener);
    }

    public static void unlisten(Consumer<Notice> listener) {
        LISTENERS.remove(listener);
    }

    public static void ended(MinecraftServer server, @Nullable NetworkRef network, CraftingJob job, Outcome outcome, String reason) {
        CraftHistory.record(server, job, outcome);
        if (network != null) {
            net.zagdrath.encodedlogistics.elcl.exec.ElclEvents.craftEnded(server, network, job, outcome);
        }
        long duration = job.started >= 0 ? Math.max(0, server.overworld().getGameTime() - job.started) : 0;
        JobToastPayload theirs = new JobToastPayload(job.target, job.amount, outcome.ordinal(), job.processing(), reason, duration, true);
        JobToastPayload others = new JobToastPayload(job.target, job.amount, outcome.ordinal(), job.processing(), reason, duration, false);
        ServerPlayer requester = job.requester.map(id -> server.getPlayerList().getPlayer(id)).orElse(null);
        if (requester == null && !job.notifyUser().isEmpty() && network != null) {
            queue(server, network, job, outcome, reason);
            notify(new Notice(null, true, job.notifyUser(), theirs));
        }
        if (!Config.ALLOW_JOB_TOASTS.getAsBoolean()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean mine = player == requester;
            if (!mine && (network == null || !NetworkAccess.allowed(server, network, player, RackPermission.VIEW))) {
                continue;
            }
            JobToastPayload toast = mine ? theirs : others;
            // Only to clients that have the mod (they've agreed to its channel).
            if (player.connection.hasChannel(JobToastPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, toast);
            }
            notify(new Notice(player, mine, null, toast));
        }
    }

    private static void notify(Notice notice) {
        List.copyOf(LISTENERS).forEach(listener -> listener.accept(notice));
    }

    // "Crafting complete: Iron Ingot x32" in the requester's (or the job's user's) Terminal OS message queue.
    private static void queue(MinecraftServer server, NetworkRef network, CraftingJob job, Outcome outcome, String reason) {
        String item = job.target.stack().getHoverName().getString() + " x" + job.amount;
        String text = Component.translatable(title(outcome, job.processing())).getString() + ": " + item;
        if (outcome == Outcome.FAILED && !reason.isEmpty()) {
            text += " (" + Component.translatable("gui.encodedlogistics.job.reason." + reason).getString() + ")";
        }
        ElclServices.messages().send(new ElclSystem(server, network), "QSYSOPR", job.notifyUser(), "", outcome == Outcome.FAILED ? 30 : 0, text);
    }

    // The heading's lang key: crafting or processing, complete / failed / cancelled.
    public static String title(Outcome outcome, boolean processing) {
        return "gui.encodedlogistics.job." + (processing ? "processing" : "crafting") + "." + outcome.name().toLowerCase(Locale.ROOT);
    }
}
