/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.ThreadUnitBlock;
import net.zagdrath.encodedlogistics.crafting.CraftTask;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.crafting.JobRunner;
import net.zagdrath.encodedlogistics.menu.SchedulerCoreMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.SchedulerStructures;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// A Scheduler Core: its structure (from SchedulerStructures), its jobs and running them (JobRunner). A formed structure
// has schedulerBaseThreads threads plus threadUnitThreads per Thread Unit, and schedulerBaseMemory job memory plus
// jobBufferMemory per Job Buffer. A job is accepted when its memory fits what's free. Cancelling a job puts what it holds
// into the network at once; runs still out finish into the network. Thread Units glow while any job runs. Nothing runs
// while the structure is unformed or offline.
public class SchedulerCoreBlockEntity extends BlockEntity implements NetworkDevice, JobHost {
    private List<BlockPos> members = List.of();
    private SchedulerStructures.Problem problem = SchedulerStructures.Problem.NONE;
    private int buffers, threadUnits;
    private boolean online, active;
    private final JobRunner runner = new JobRunner();

    public SchedulerCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SCHEDULER_CORE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            SchedulerStructures.get(serverLevel).queue(worldPosition);
        }
    }

    // --- Structure ---

    public void setStructure(List<BlockPos> members, SchedulerStructures.Problem problem, int buffers, int threadUnits) {
        this.members = members;
        this.problem = problem;
        this.buffers = buffers;
        this.threadUnits = threadUnits;
        updateActive(true);
    }

    public boolean formed() {
        return !members.isEmpty();
    }

    public SchedulerStructures.Problem problem() {
        return problem;
    }

    public int size() {
        return members.size();
    }

    // FE per tick the structure drains.
    public double drain() {
        return Config.SCHEDULER_DRAIN.getAsDouble() + Config.SCHEDULER_DRAIN_PER_BLOCK.getAsDouble() * Math.max(1, members.size());
    }

    @Override
    public BlockPos hostPos() {
        return worldPosition;
    }

    @Override
    public int threads() {
        return Config.SCHEDULER_BASE_THREADS.getAsInt() + threadUnits * Config.THREAD_UNIT_THREADS.getAsInt();
    }

    @Override
    public long memory() {
        return Config.SCHEDULER_BASE_MEMORY.getAsInt() + (long) buffers * Config.JOB_BUFFER_MEMORY.getAsInt();
    }

    @Override
    public int threadsUsed() {
        return runner.threadsUsed();
    }

    @Override
    public long memoryUsed() {
        return runner.memoryUsed();
    }

    public boolean isOnline() {
        return online;
    }

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
    }

    // --- Jobs ---

    @Override
    public List<CraftingJob> jobs() {
        return runner.jobs();
    }

    @Override
    public @Nullable CraftingJob job(UUID id) {
        return runner.job(id);
    }

    @Override
    public void addJob(CraftingJob job) {
        runner.add(job);
        setChanged();
    }

    @Override
    public void jobChanged() {
        setChanged();
    }

    // Cancels a job: everything it holds goes into the network (or drops at the Core when it doesn't fit).
    @Override
    public boolean cancel(UUID id) {
        if (!(level instanceof ServerLevel serverLevel)
                || !runner.cancel(serverLevel, worldPosition, id, ControllerStructures.get(serverLevel).storageAt(serverLevel, worldPosition))) {
            return false;
        }
        setChanged();
        updateActive(false);
        return true;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!formed() || !online) {
            updateActive(false);
            return;
        }
        ControllerStructures structures = ControllerStructures.get(serverLevel);
        if (runner.tick(serverLevel, worldPosition, threads(), () -> structures.providersAt(serverLevel, worldPosition),
                () -> structures.sharedStorageAt(serverLevel, worldPosition, true), () -> ControllerStructures.jobFinished(serverLevel, worldPosition))) {
            setChanged();
        }
        updateActive(false);
    }

    // Thread Units glow while any job runs; force: set them even if nothing changed (the structure changed).
    private void updateActive(boolean force) {
        boolean now = formed() && online && runner.anyRunning();
        if (now == active && !force || level == null) {
            return;
        }
        active = now;
        for (BlockPos pos : members) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ThreadUnitBlock && state.getValue(ThreadUnitBlock.ACTIVE) != now) {
                level.setBlock(pos, state.setValue(ThreadUnitBlock.ACTIVE, now), Block.UPDATE_CLIENTS);
            }
        }
    }

    // --- From providers ---

    // What a run made (or part of it, for a Gateway) goes back to its job, whichever host has it (a Scheduler or a
    // rack); runDone: the run is over. When the job is gone (cancelled, its host broken) it goes into the network at
    // from, or drops there.
    public static void deliver(ServerLevel level, CraftTask task, List<ItemStack> items, boolean runDone, BlockPos from) {
        JobHost host = JobHost.at(level, task.core());
        CraftingJob job = host != null ? host.job(task.job()) : null;
        if (job == null || task.step() >= job.steps.size()) {
            returnToNetwork(level, from, items);
            return;
        }
        items.forEach(job::add);
        if (runDone) {
            CraftingJob.Step step = job.steps.get(task.step());
            step.done++;
            step.running = Math.max(0, step.running - 1);
        }
        host.jobChanged();
    }

    // A run that won't happen (its Fabricator or Gateway was broken): its inputs go back to the job, to run again.
    public static void refund(ServerLevel level, CraftTask task, List<ItemStack> inputs, BlockPos from) {
        JobHost host = JobHost.at(level, task.core());
        CraftingJob job = host != null ? host.job(task.job()) : null;
        if (job == null || task.step() >= job.steps.size()) {
            returnToNetwork(level, from, inputs);
            return;
        }
        inputs.forEach(job::add);
        CraftingJob.Step step = job.steps.get(task.step());
        step.running = Math.max(0, step.running - 1);
        host.jobChanged();
    }

    // Puts items into the network the device at pos is on; what doesn't fit drops there.
    public static void returnToNetwork(ServerLevel level, BlockPos pos, List<ItemStack> items) {
        JobRunner.putBack(level, pos, items, ControllerStructures.get(level).storageAt(level, pos));
    }

    // --- Menu ---

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SchedulerCoreMenu(id, inventory, this),
                Component.translatable("block.encodedlogistics.scheduler_core")), buf -> buf.writeBlockPos(worldPosition));
    }

    // --- Saving ---

    // Breaking the Core drops what its jobs hold.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            runner.dropAll(serverLevel, pos);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        runner.load(input.read("jobs", CraftingJob.CODEC.listOf()).orElse(List.of()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("jobs", CraftingJob.CODEC.listOf(), runner.jobs());
    }
}
