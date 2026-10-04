/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;

// How crafting jobs ended (JobEvents.ended), so scripts can still ask after a job has left its Scheduler (RTVCRFSTS,
// STRCRAFT WAIT(*YES), the *CRAFTEND trigger): the last few hundred per server, while it runs.
public final class CraftHistory {
    public record Ended(UUID job, JobEvents.Outcome outcome, String target, long amount, int done, int total) {}

    private static final int KEPT = 512;
    private static final Map<MinecraftServer, Map<UUID, Ended>> ENDED = new WeakHashMap<>();

    private CraftHistory() {}

    static synchronized void record(MinecraftServer server, CraftingJob job, JobEvents.Outcome outcome) {
        Map<UUID, Ended> ended = ENDED.computeIfAbsent(server, s -> new LinkedHashMap<>());
        ended.put(job.id, new Ended(job.id, outcome, net.zagdrath.encodedlogistics.elcl.exec.ElclItems.id(job.target.stack().getItem()), job.amount,
                job.done(), job.total()));
        while (ended.size() > KEPT) {
            ended.remove(ended.keySet().iterator().next());
        }
    }

    public static synchronized @Nullable Ended ended(MinecraftServer server, UUID job) {
        Map<UUID, Ended> ended = ENDED.get(server);
        return ended != null ? ended.get(job) : null;
    }
}
