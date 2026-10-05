/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;

// Something that takes crafting jobs and runs them: a Scheduler (its Core), a Server Rack with Compute and Memory
// Servers (RackScheduler) or a Midrange System. Known by its position: the Core's, or the rack's master. What providers make comes back to
// the job through here (SchedulerCoreBlockEntity.deliver).
public interface JobHost {
    BlockPos hostPos();

    List<CraftingJob> jobs();

    @Nullable CraftingJob job(UUID id);

    int threads();

    int threadsUsed();

    long memory();

    long memoryUsed();

    default long memoryFree() {
        return memory() - memoryUsed();
    }

    // A job the request already took its items for (CraftRequests.start).
    void addJob(CraftingJob job);

    // Cancels a job: everything it holds goes into the network.
    boolean cancel(UUID id);

    // A job's progress changed (saves it).
    void jobChanged();

    // The job host at pos, if it's loaded.
    static @Nullable JobHost at(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SchedulerCoreBlockEntity core) {
            return core;
        }
        if (blockEntity instanceof MidrangeSystemBlockEntity midrange) {
            return midrange;
        }
        return blockEntity instanceof RackBlockEntity rack ? rack.scheduler() : null;
    }
}
