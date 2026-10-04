/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Crafting requests from a terminal, by the network it reaches (or a device position on it): what the network can
// craft, planning a request and starting it on a Scheduler - "Auto" (index -1) picks the first with a free thread and
// room for the job's memory, else the first with room (the job queues); an index picks that Scheduler, if it has room.
//
// What's on tape counts as stored: a plan takes it as the hot items it would be once recalled (Plan#recall), and
// starting the job starts those recalls; the job takes the items as they arrive (CraftingJob#awaiting).
public final class CraftRequests {
    private CraftRequests() {}

    // The network a device reaches: its own while it's online.
    private static @Nullable NetworkRef network(ServerLevel level, BlockPos device) {
        return ControllerStructures.get(level).isDeviceOnline(level, device) ? ControllerStructures.networkOf(level, device) : null;
    }

    // Every schematic on the network, Fabricators and Gateways in position order.
    public static List<Schematic> schematics(MinecraftServer server, @Nullable NetworkRef network) {
        List<Schematic> schematics = new ArrayList<>();
        for (CraftingProvider provider : ControllerStructures.providersOf(server, network)) {
            schematics.addAll(provider.schematics());
        }
        return schematics;
    }

    public static List<Schematic> schematics(ServerLevel level, BlockPos device) {
        return schematics(level.getServer(), network(level, device));
    }

    // What the network can make: every schematic's outputs.
    public static Set<ItemKey> craftables(MinecraftServer server, @Nullable NetworkRef network) {
        Set<ItemKey> craftables = new LinkedHashSet<>();
        if (network == null) {
            return craftables;
        }
        for (Schematic schematic : schematics(server, network)) {
            craftables.addAll(schematic.outputTotals().keySet());
        }
        return craftables;
    }

    public static Set<ItemKey> craftables(ServerLevel level, BlockPos device) {
        return craftables(level.getServer(), network(level, device));
    }

    public static CraftPlanner.@Nullable Plan plan(MinecraftServer server, @Nullable NetworkRef network, ItemKey target, long amount) {
        NetworkStorage storage = network != null ? ControllerStructures.storageOf(server, network) : null;
        if (storage == null) {
            return null;
        }
        CraftPlanner.Plan plan = CraftPlanner.plan(storage.list(), storage.coldList(), CraftPlanner.byOutput(schematics(server, network)), target,
                amount);
        // The recall time: the longest of the items' (they run in parallel on the libraries' drives).
        int ticks = 0;
        for (ItemKey key : plan.recall().keySet()) {
            ticks = Math.max(ticks, Math.max(0, storage.cold().eta(key)));
        }
        return plan.withRecallTicks(ticks);
    }

    public static CraftPlanner.@Nullable Plan plan(ServerLevel level, BlockPos device, ItemKey target, long amount) {
        return plan(level.getServer(), network(level, device), target, amount);
    }

    public static List<JobHost> schedulers(MinecraftServer server, @Nullable NetworkRef network) {
        return ControllerStructures.schedulersOf(server, network);
    }

    public static List<JobHost> schedulers(ServerLevel level, BlockPos device) {
        return schedulers(level.getServer(), network(level, device));
    }

    public static @Nullable JobHost choose(List<JobHost> schedulers, long memory, int index) {
        if (index >= 0) {
            return index < schedulers.size() && schedulers.get(index).memoryFree() >= memory ? schedulers.get(index) : null;
        }
        for (JobHost scheduler : schedulers) {
            if (scheduler.memoryFree() >= memory && scheduler.threadsUsed() < scheduler.threads()) {
                return scheduler;
            }
        }
        for (JobHost scheduler : schedulers) {
            if (scheduler.memoryFree() >= memory) {
                return scheduler;
            }
        }
        return null;
    }

    // Takes the plan's hot items out of storage, starts the recalls of its cold ones, and gives the job to the
    // scheduler; null (and nothing taken) when storage no longer has them all.
    public static @Nullable CraftingJob start(MinecraftServer server, @Nullable NetworkRef network, CraftPlanner.Plan plan, JobHost scheduler) {
        NetworkStorage storage = network != null ? ControllerStructures.storageOf(server, network) : null;
        if (storage == null || !plan.complete()) {
            return null;
        }
        for (Map.Entry<ItemKey, Long> entry : plan.take().entrySet()) {
            if (storage.extract(entry.getKey(), entry.getValue(), true) < entry.getValue()) {
                return null;
            }
        }
        for (Map.Entry<ItemKey, Long> entry : plan.recall().entrySet()) {
            if (storage.cold().count(entry.getKey()) < entry.getValue()) {
                return null;
            }
        }
        List<CraftingJob.Step> steps = new ArrayList<>();
        plan.crafts().forEach((schematic, runs) -> steps.add(new CraftingJob.Step(schematic, (int) Math.min(Integer.MAX_VALUE, runs), 0, 0)));
        CraftingJob job = new CraftingJob(UUID.randomUUID(), plan.target(), plan.amount(), plan.memory(), steps);
        for (Map.Entry<ItemKey, Long> entry : plan.take().entrySet()) {
            long taken = storage.extract(entry.getKey(), entry.getValue(), false);
            if (taken > 0) {
                job.held.merge(entry.getKey(), taken, Long::sum);
            }
        }
        plan.recall().forEach((key, amount) -> {
            job.awaiting.merge(key, amount, Long::sum);
            storage.cold().recall(key, amount);
        });
        scheduler.addJob(job);
        return job;
    }

    public static @Nullable CraftingJob start(ServerLevel level, BlockPos device, CraftPlanner.Plan plan, JobHost scheduler) {
        return start(level.getServer(), network(level, device), plan, scheduler);
    }
}
