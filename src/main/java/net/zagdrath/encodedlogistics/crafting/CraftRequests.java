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
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Crafting requests from a terminal at a device position: what the network can craft, planning a request and starting
// it on a Scheduler - "Auto" (index -1) picks the first with a free thread and room for the job's memory, else the first
// with room (the job queues); an index picks that Scheduler, if it has room.
public final class CraftRequests {
    private CraftRequests() {}

    // Every schematic on the network, Fabricators and Gateways in position order.
    public static List<Schematic> schematics(ServerLevel level, BlockPos device) {
        List<Schematic> schematics = new ArrayList<>();
        for (CraftingProvider provider : ControllerStructures.get(level).providersAt(level, device)) {
            schematics.addAll(provider.schematics());
        }
        return schematics;
    }

    // What the network can make: every schematic's outputs.
    public static Set<ItemKey> craftables(ServerLevel level, BlockPos device) {
        Set<ItemKey> craftables = new LinkedHashSet<>();
        for (Schematic schematic : schematics(level, device)) {
            craftables.addAll(schematic.outputTotals().keySet());
        }
        return craftables;
    }

    public static CraftPlanner.@Nullable Plan plan(ServerLevel level, BlockPos device, ItemKey target, long amount) {
        NetworkStorage storage = ControllerStructures.get(level).storageAt(level, device);
        if (storage == null) {
            return null;
        }
        return CraftPlanner.plan(storage.list(), CraftPlanner.byOutput(schematics(level, device)), target, amount);
    }

    public static List<SchedulerCoreBlockEntity> schedulers(ServerLevel level, BlockPos device) {
        return ControllerStructures.get(level).schedulersAt(level, device);
    }

    public static @Nullable SchedulerCoreBlockEntity choose(List<SchedulerCoreBlockEntity> schedulers, long memory, int index) {
        if (index >= 0) {
            return index < schedulers.size() && schedulers.get(index).memoryFree() >= memory ? schedulers.get(index) : null;
        }
        for (SchedulerCoreBlockEntity scheduler : schedulers) {
            if (scheduler.memoryFree() >= memory && scheduler.threadsUsed() < scheduler.threads()) {
                return scheduler;
            }
        }
        for (SchedulerCoreBlockEntity scheduler : schedulers) {
            if (scheduler.memoryFree() >= memory) {
                return scheduler;
            }
        }
        return null;
    }

    // Takes the plan's items out of storage and gives the job to the scheduler; null (and nothing taken) when storage
    // no longer has them all.
    public static @Nullable CraftingJob start(ServerLevel level, BlockPos device, CraftPlanner.Plan plan, SchedulerCoreBlockEntity scheduler) {
        NetworkStorage storage = ControllerStructures.get(level).storageAt(level, device);
        if (storage == null || !plan.complete()) {
            return null;
        }
        for (Map.Entry<ItemKey, Long> entry : plan.take().entrySet()) {
            if (storage.extract(entry.getKey(), entry.getValue(), true) < entry.getValue()) {
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
        scheduler.addJob(job);
        return job;
    }
}
