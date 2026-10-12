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
import net.minecraft.core.UUIDUtil;
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
import net.zagdrath.encodedlogistics.rack.device.ServerDevice;

// A network's rack Scheduler, a cluster: every online Compute and Memory Server serving a network, in whichever of its
// racks, pools into one Scheduler with computeServerThreads threads per Compute Server and memoryServerMemory job
// memory per Memory Server. It runs crafting jobs like a Scheduler once it has at least one of each.
//
// Each rack has a RackScheduler, but a network's pool is headed by one rack: the first (in ControllerStructures.allRacks
// order) whose lowest online Compute Server serves that network (ownNetwork). Only the head's is active; it keeps the
// pool's jobs and appears among the network's Schedulers (the Craft Plan screen's "Rack x, y, z"). When the head moves
// (a rack lower in the order gains a Compute Server, or the head's go offline), the old head hands its jobs to the
// new one, and anything in flight finds them there (pool, through JobHost.at). A rack serving several networks heads
// at most one: its lowest online Compute Server's. Breaking the head rack fails its jobs, as breaking a Core does.
//
// It offers crafting steps to the pool's Fabrication Servers first, then the rest of the network's providers.
public final class RackScheduler implements JobHost {
    private final RackBlockEntity rack;
    private final JobRunner runner = new JobRunner();
    // As of the last refresh: the network this rack's lowest online Compute Server serves, whether this rack heads
    // that network's pool, and the pool's totals (only on the head).
    private @Nullable NetworkRef network;
    private boolean head;
    private int threads;
    private long memory;
    private static final int REFRESH_INTERVAL = 40;
    private long refreshedKey = -1;
    private int sinceRefresh;

    public RackScheduler(RackBlockEntity rack) {
        this.rack = rack;
    }

    public RackBlockEntity rack() {
        return rack;
    }

    // The network of the rack's lowest online Compute Server, from its devices as they are now; null without one.
    public @Nullable NetworkRef ownNetwork() {
        for (RackDevice device : rack.devices()) {
            if (device instanceof ComputeServerDevice && device.isOnline()) {
                return rack.network(device);
            }
        }
        return null;
    }

    // The Scheduler of a network's pool: its head rack's, or null while no loaded rack has an online Compute Server
    // serving it. (Worked out for every network at once, and cached: ControllerStructures.rackSchedulerOf.)
    public static @Nullable RackScheduler of(MinecraftServer server, @Nullable NetworkRef network) {
        return network != null ? ControllerStructures.rackSchedulerOf(server, network) : null;
    }

    // The Scheduler a device of this rack is part of: the pool of the network it serves.
    public @Nullable RackScheduler forDevice(RackDevice device) {
        return rack.getLevel() instanceof ServerLevel level ? of(level.getServer(), rack.network(device)) : null;
    }

    // The Scheduler that has (or takes) this rack's jobs: its pool's head, or itself when its network has no pool.
    public RackScheduler pool() {
        if (rack.getLevel() instanceof ServerLevel level) {
            RackScheduler head = of(level.getServer(), poolNetwork(level));
            if (head != null) {
                return head;
            }
        }
        return this;
    }

    // The network this rack's jobs belong to: its lowest Compute Server's (online or not), else the rack's own.
    private @Nullable NetworkRef poolNetwork(ServerLevel level) {
        NetworkRef own = ownNetwork();
        if (own != null) {
            return own;
        }
        for (RackDevice device : rack.devices()) {
            if (device instanceof ComputeServerDevice) {
                return rack.network(device);
            }
        }
        NetworkRef home = rack.segmentNetwork(0);
        return home != null ? home : ControllerStructures.networkOf(level, rack.getBlockPos());
    }

    // Works out whether this rack heads its network's pool, and the pool's threads and memory, from the racks' devices
    // as they are now.
    public void refresh() {
        network = ownNetwork();
        head = false;
        threads = 0;
        memory = 0;
        if (network == null || !(rack.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        head = of(server, network) == this;
        if (!head) {
            return;
        }
        for (RackDevice device : ControllerStructures.rackDevicesServing(server, network)) {
            if (!device.isOnline()) {
                continue;
            }
            if (device instanceof ComputeServerDevice) {
                threads += Config.COMPUTE_SERVER_THREADS.getAsInt();
            } else if (device instanceof MemoryServerDevice) {
                memory += Config.MEMORY_SERVER_MEMORY.getAsInt();
            }
        }
    }

    // Its servers' lights, every tick: Compute Servers blink while jobs run, Memory Servers while jobs hold memory.
    private void markBusy(ServerLevel level) {
        boolean computing = active() && runner.threadsUsed() > 0, holding = active() && runner.memoryUsed() > 0;
        if (computing || holding) {
            long now = level.getGameTime();
            for (RackDevice device : ControllerStructures.rackDevicesServing(level.getServer(), network)) {
                if (device.isOnline() && (computing && device instanceof ComputeServerDevice || holding && device instanceof MemoryServerDevice)) {
                    ((ServerDevice) device).markBusy(now);
                }
            }
        }
    }

    // Heads its pool and has threads and memory, so it takes jobs.
    public boolean active() {
        return head && threads > 0 && memory > 0;
    }

    public @Nullable NetworkRef network() {
        return network;
    }

    // Whether a device in its rack shows the Scheduler badge: an online server whose network's pool is active.
    public boolean badges(RackDevice device) {
        if (!(device instanceof ComputeServerDevice || device instanceof MemoryServerDevice || device instanceof FabricationServerDevice)
                || !device.isOnline()) {
            return false;
        }
        RackScheduler pool = forDevice(device);
        return pool != null && pool.active();
    }

    public void tick(ServerLevel level) {
        // Worked out again only when the networks' caches changed (devices, racks, topology: ControllerStructures
        // cacheKey), and every REFRESH_INTERVAL ticks anyway (settings such as threads per server).
        long key = ControllerStructures.cacheKey(level.getServer());
        if (key != refreshedKey || ++sinceRefresh >= REFRESH_INTERVAL) {
            refresh();
            refreshedKey = key;
            sinceRefresh = 0;
        }
        markBusy(level);
        if (!head) {
            handOver(level);
            return;
        }
        if (!active()) {
            return;
        }
        MinecraftServer server = level.getServer();
        NetworkRef served = network;
        if (runner.tick(level, rack.getBlockPos(), threads, () -> providers(server), () -> ControllerStructures.sharedStorageOf(server, served, true),
                () -> ControllerStructures.sharedStorageOf(server, served, false), job -> {
                    ControllerStructures.jobFinished(server, served);
                    JobEvents.ended(server, served, this, job, JobEvents.Outcome.COMPLETED, "");
                }, job -> JobEvents.stalled(server, served, job))) {
            rack.setChanged();
        }
    }

    // A rack that no longer heads its pool gives the head its jobs (they carry on there; what's in flight finds them
    // through pool). Without a head they wait here, as they did before pooling.
    private void handOver(ServerLevel level) {
        if (runner.jobs().isEmpty()) {
            return;
        }
        RackScheduler head = of(level.getServer(), poolNetwork(level));
        if (head == null || head == this) {
            return;
        }
        head.runner.adopt(runner);
        rack.setChanged();
        head.rack.setChanged();
    }

    // The pool's Fabrication Servers first, then the rest of its network's providers.
    private List<CraftingProvider> providers(MinecraftServer server) {
        List<CraftingProvider> providers = new ArrayList<>();
        for (RackDevice device : ControllerStructures.rackDevicesServing(server, network)) {
            if (device instanceof FabricationServerDevice fabrication && device.isOnline()) {
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
                ? runner.cancel(level, rack.getBlockPos(), id, ControllerStructures.sharedStorageOf(level.getServer(), network, false)) : null;
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

    @Override
    public boolean holdJob(UUID id, boolean hold) {
        if (!runner.setHeld(id, hold)) {
            return false;
        }
        rack.setChanged();
        return true;
    }

    @Override
    public boolean isHeld(UUID id) {
        return runner.isHeld(id);
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
        if (!runner.heldJobs().isEmpty()) {
            output.store("held_jobs", UUIDUtil.CODEC.listOf(), List.copyOf(runner.heldJobs()));
        }
    }

    public void load(ValueInput input) {
        runner.load(input.read("jobs", CraftingJob.CODEC.listOf()).orElse(List.of()));
        runner.heldJobs().clear();
        runner.heldJobs().addAll(input.read("held_jobs", UUIDUtil.CODEC.listOf()).orElse(List.of()));
        runner.heldJobs().removeIf(id -> runner.job(id) == null);
    }
}
