/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.crafting.JobEvents;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.crafting.JobRunner;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.device.ComputeServerDevice;
import net.zagdrath.encodedlogistics.rack.device.FabricationServerDevice;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;

// A rack's Scheduler: a rack with at least one online Compute Server and one online Memory Server runs crafting jobs
// like a Scheduler, with computeServerThreads threads per Compute Server and memoryServerMemory job memory per Memory
// Server. It serves the network of its lowest Compute Server (the rack's own, or the segment that server is on); only
// servers serving that network count. It appears among that network's Schedulers (the Craft Plan screen's "Rack x, y,
// z"), and offers crafting steps to its own rack's Fabrication Servers first. Its jobs are kept with the rack.
public final class RackScheduler implements JobHost {
    private final RackBlockEntity rack;
    private final JobRunner runner = new JobRunner();
    private @Nullable NetworkRef network;
    private int threads;
    private long memory;

    public RackScheduler(RackBlockEntity rack) {
        this.rack = rack;
    }

    // Works out which servers count, from the rack's devices as they are now.
    public void refresh() {
        network = null;
        threads = 0;
        memory = 0;
        for (RackDevice device : rack.devices()) {
            if (device instanceof ComputeServerDevice && device.isOnline()) {
                network = rack.network(device);
                break;
            }
        }
        if (network == null) {
            return;
        }
        for (RackDevice device : rack.devices()) {
            if (!device.isOnline() || !network.equals(rack.network(device))) {
                continue;
            }
            if (device instanceof ComputeServerDevice) {
                threads += Config.COMPUTE_SERVER_THREADS.getAsInt();
            } else if (device instanceof MemoryServerDevice) {
                memory += Config.MEMORY_SERVER_MEMORY.getAsInt();
            }
        }
    }

    // Has threads and memory, so it takes jobs.
    public boolean active() {
        return network != null && threads > 0 && memory > 0;
    }

    public @Nullable NetworkRef network() {
        return network;
    }

    // Whether a device in its rack shows the Scheduler badge: a server that's part of it, while it's active.
    public boolean badges(RackDevice device) {
        return active() && (device instanceof ComputeServerDevice || device instanceof MemoryServerDevice || device instanceof FabricationServerDevice)
                && device.isOnline() && network.equals(rack.network(device));
    }

    public void tick(ServerLevel level) {
        refresh();
        if (!active()) {
            return;
        }
        MinecraftServer server = level.getServer();
        NetworkRef served = network;
        if (runner.tick(level, rack.getBlockPos(), threads, () -> providers(server), () -> ControllerStructures.sharedStorageOf(server, served, true),
                job -> {
                    ControllerStructures.jobFinished(server, served);
                    JobEvents.ended(server, served, this, job, JobEvents.Outcome.COMPLETED, "");
                })) {
            rack.setChanged();
        }
    }

    // Its own rack's Fabrication Servers first, then the rest of its network's providers.
    private List<CraftingProvider> providers(MinecraftServer server) {
        List<CraftingProvider> providers = new ArrayList<>();
        for (RackDevice device : rack.devices()) {
            if (device instanceof FabricationServerDevice fabrication && device.isOnline() && network.equals(rack.network(device))) {
                providers.add(fabrication);
            }
        }
        for (CraftingProvider provider : ControllerStructures.providersOf(server, network)) {
            if (!providers.contains(provider)) {
                providers.add(provider);
            }
        }
        return providers;
    }

    // --- JobHost ---

    @Override
    public BlockPos hostPos() {
        return rack.getBlockPos();
    }

    @Override
    public List<CraftingJob> jobs() {
        return runner.jobs();
    }

    @Override
    public @Nullable CraftingJob job(UUID id) {
        return runner.job(id);
    }

    @Override
    public int threads() {
        return threads;
    }

    @Override
    public int threadsUsed() {
        return runner.threadsUsed();
    }

    @Override
    public long memory() {
        return memory;
    }

    @Override
    public long memoryUsed() {
        return runner.memoryUsed();
    }

    @Override
    public void addJob(CraftingJob job) {
        runner.add(job);
        rack.setChanged();
    }

    @Override
    public boolean cancel(UUID id) {
        CraftingJob job = rack.getLevel() instanceof ServerLevel level
                ? runner.cancel(level, rack.getBlockPos(), id, ControllerStructures.storageOf(level.getServer(), network)) : null;
        if (job == null) {
            return false;
        }
        rack.setChanged();
        JobEvents.ended(rack.getLevel().getServer(), network, this, job, JobEvents.Outcome.CANCELLED, "");
        return true;
    }

    @Override
    public void jobChanged() {
        rack.setChanged();
    }

    // The rack broken: what its jobs hold drops, and they've failed.
    public void dropAll(ServerLevel level) {
        NetworkRef served = network;
        for (CraftingJob job : runner.dropAll(level, rack.getBlockPos())) {
            JobEvents.ended(level.getServer(), served, this, job, JobEvents.Outcome.FAILED, JobEvents.SCHEDULER_REMOVED);
        }
    }

    public void save(ValueOutput output) {
        if (!runner.jobs().isEmpty()) {
            output.store("jobs", CraftingJob.CODEC.listOf(), runner.jobs());
        }
    }

    public void load(ValueInput input) {
        runner.load(input.read("jobs", CraftingJob.CODEC.listOf()).orElse(List.of()));
    }
}
