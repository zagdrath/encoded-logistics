/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.Iterator;
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
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.menu.SchedulerCoreMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.SchedulerStructures;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// A Scheduler Core: its structure (from SchedulerStructures), its jobs and running them. A formed structure has
// schedulerBaseThreads threads plus threadUnitThreads per Thread Unit, and schedulerBaseMemory job memory plus
// jobBufferMemory per Job Buffer. A job is accepted when its memory fits what's free; it holds the items it took from
// storage from then on, waits in the queue for a thread, and then runs: every tick each of its steps with the inputs for
// another run is offered to the network's Fabricators and Gateways holding its schematic. Whatever they make comes back
// to the job (deliver); when every step is done the job puts all it holds into the network. Cancelling a job does the
// same at once; runs still out finish into the network. Thread Units glow while any job runs. Nothing runs while the
// structure is unformed or offline.
public class SchedulerCoreBlockEntity extends BlockEntity implements NetworkDevice {
    // Dispatches tried per job per tick, so a huge job can't stall the server.
    private static final int MAX_OFFERS = 64;

    private List<BlockPos> members = List.of();
    private SchedulerStructures.Problem problem = SchedulerStructures.Problem.NONE;
    private int buffers, threadUnits;
    private boolean online, active;
    private final List<CraftingJob> jobs = new ArrayList<>();

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

    public int threads() {
        return Config.SCHEDULER_BASE_THREADS.getAsInt() + threadUnits * Config.THREAD_UNIT_THREADS.getAsInt();
    }

    public long memory() {
        return Config.SCHEDULER_BASE_MEMORY.getAsInt() + (long) buffers * Config.JOB_BUFFER_MEMORY.getAsInt();
    }

    public int threadsUsed() {
        return (int) jobs.stream().filter(job -> job.running).count();
    }

    public long memoryUsed() {
        return jobs.stream().mapToLong(job -> job.memory).sum();
    }

    public long memoryFree() {
        return memory() - memoryUsed();
    }

    public boolean isOnline() {
        return online;
    }

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
    }

    // --- Jobs ---

    public List<CraftingJob> jobs() {
        return jobs;
    }

    public @Nullable CraftingJob job(UUID id) {
        for (CraftingJob job : jobs) {
            if (job.id.equals(id)) {
                return job;
            }
        }
        return null;
    }

    // A job the request already took its items for (CraftRequests.start).
    public void addJob(CraftingJob job) {
        jobs.add(job);
        setChanged();
    }

    // Cancels a job: everything it holds goes into the network (or drops at the Core when it doesn't fit).
    public boolean cancel(UUID id) {
        CraftingJob job = job(id);
        if (job == null || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        jobs.remove(job);
        returnToNetwork(serverLevel, worldPosition, job.heldStacks());
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
        int free = threads() - threadsUsed();
        for (CraftingJob job : jobs) {
            if (free <= 0) {
                break;
            }
            if (!job.running) {
                job.running = true;
                free--;
                setChanged();
            }
        }
        List<CraftingProvider> providers = null;
        Iterator<CraftingJob> iterator = jobs.iterator();
        while (iterator.hasNext()) {
            CraftingJob job = iterator.next();
            if (!job.running) {
                continue;
            }
            if (job.finished()) {
                if (finish(serverLevel, job)) {
                    iterator.remove();
                    setChanged();
                }
                continue;
            }
            if (providers == null) {
                providers = ControllerStructures.get(serverLevel).providersAt(serverLevel, worldPosition);
            }
            dispatch(serverLevel, job, providers);
        }
        updateActive(false);
    }

    // Offers each step's next runs to the providers holding its schematic, while the job holds the inputs.
    private void dispatch(ServerLevel level, CraftingJob job, List<CraftingProvider> providers) {
        int offers = 0;
        for (int index = 0; index < job.steps.size(); index++) {
            CraftingJob.Step step = job.steps.get(index);
            while (step.left() > 0 && offers < MAX_OFFERS && job.holds(step.schematic.inputTotals())) {
                CraftTask task = new CraftTask(worldPosition, job.id, index, step.schematic, step.schematic.inputs().stream()
                        .map(input -> input.item().create()).toList());
                boolean taken = false;
                for (CraftingProvider provider : providers) {
                    offers++;
                    if (provider.schematics().contains(step.schematic) && provider.offer(level, task)) {
                        taken = true;
                        break;
                    }
                }
                if (!taken) {
                    break;
                }
                job.takeInputs(step.schematic);
                step.running++;
                setChanged();
            }
        }
    }

    // A finished job puts everything it holds into the network; true once it all went in.
    private boolean finish(ServerLevel level, CraftingJob job) {
        NetworkStorage storage = ControllerStructures.get(level).storageAt(level, worldPosition);
        if (storage == null) {
            return false;
        }
        for (ItemKey key : List.copyOf(job.held.keySet())) {
            long count = job.held.get(key);
            long stored = storage.insert(key, count, false);
            if (stored >= count) {
                job.held.remove(key);
            } else {
                job.held.put(key, count - stored);
            }
        }
        return job.held.isEmpty();
    }

    // Thread Units glow while any job runs; force: set them even if nothing changed (the structure changed).
    private void updateActive(boolean force) {
        boolean now = formed() && online && jobs.stream().anyMatch(job -> job.running);
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

    // What a run made (or part of it, for a Gateway) goes back to its job; runDone: the run is over. When the job is
    // gone (cancelled, its Core broken) it goes into the network at from, or drops there.
    public static void deliver(ServerLevel level, CraftTask task, List<ItemStack> items, boolean runDone, BlockPos from) {
        CraftingJob job = level.isLoaded(task.core()) && level.getBlockEntity(task.core()) instanceof SchedulerCoreBlockEntity core
                ? core.job(task.job()) : null;
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
        level.getBlockEntity(task.core()).setChanged();
    }

    // A run that won't happen (its Fabricator or Gateway was broken): its inputs go back to the job, to run again.
    public static void refund(ServerLevel level, CraftTask task, List<ItemStack> inputs, BlockPos from) {
        CraftingJob job = level.isLoaded(task.core()) && level.getBlockEntity(task.core()) instanceof SchedulerCoreBlockEntity core
                ? core.job(task.job()) : null;
        if (job == null || task.step() >= job.steps.size()) {
            returnToNetwork(level, from, inputs);
            return;
        }
        inputs.forEach(job::add);
        CraftingJob.Step step = job.steps.get(task.step());
        step.running = Math.max(0, step.running - 1);
        level.getBlockEntity(task.core()).setChanged();
    }

    // Puts items into the network the device at pos is on; what doesn't fit drops there.
    public static void returnToNetwork(ServerLevel level, BlockPos pos, List<ItemStack> items) {
        NetworkStorage storage = ControllerStructures.get(level).storageAt(level, pos);
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack left = stack.copy();
            if (storage != null) {
                left.shrink((int) storage.insert(ItemKey.of(left), left.getCount(), false));
            }
            if (!left.isEmpty()) {
                Block.popResource(level, pos, left);
            }
        }
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
        if (level != null) {
            for (CraftingJob job : jobs) {
                for (ItemStack stack : job.heldStacks()) {
                    Block.popResource(level, pos, stack);
                }
            }
            jobs.clear();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        jobs.clear();
        input.read("jobs", CraftingJob.CODEC.listOf()).ifPresent(jobs::addAll);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("jobs", CraftingJob.CODEC.listOf(), jobs);
    }
}
