/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Runs a job host's jobs (JobHost). Each job holds the items it took from storage from the moment it's accepted (and
// takes any it awaits from tape as they come back, once a second), waits in the queue for a thread, and then runs: every tick each of its steps with the inputs for another run is offered to
// the network's Fabricators and Gateways (and Fabrication Servers) holding its schematic. Whatever they make comes back
// to the job (SchedulerCoreBlockEntity.deliver); when every step is done the job puts all it holds into the network.
public final class JobRunner {
    // Dispatches tried per job per tick, so a huge job can't stall the server.
    private static final int MAX_OFFERS = 64;
    private static final int AWAIT_INTERVAL = 20;

    private final List<CraftingJob> jobs = new ArrayList<>();

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

    public int threadsUsed() {
        return (int) jobs.stream().filter(job -> job.running).count();
    }

    public long memoryUsed() {
        return jobs.stream().mapToLong(job -> job.memory).sum();
    }

    public boolean anyRunning() {
        return jobs.stream().anyMatch(job -> job.running);
    }

    public void add(CraftingJob job) {
        jobs.add(job);
    }

    // One tick: queued jobs take free threads, running ones dispatch their steps, finished ones empty into storage.
    // host: the job host's position (on its tasks); finished: called for each job done. Returns whether anything changed.
    public boolean tick(ServerLevel level, BlockPos host, int threads, Supplier<List<CraftingProvider>> providers,
            Supplier<@Nullable NetworkStorage> storage, Consumer<CraftingJob> finished) {
        boolean changed = false;
        int free = threads - threadsUsed();
        for (CraftingJob job : jobs) {
            if (free <= 0) {
                break;
            }
            if (!job.running) {
                job.running = true;
                free--;
                changed = true;
            }
        }
        if (level.getGameTime() % AWAIT_INTERVAL == 0 && jobs.stream().anyMatch(job -> !job.awaiting.isEmpty())) {
            changed |= takeAwaited(storage.get());
        }
        List<CraftingProvider> found = null;
        Iterator<CraftingJob> iterator = jobs.iterator();
        while (iterator.hasNext()) {
            CraftingJob job = iterator.next();
            if (!job.running) {
                continue;
            }
            if (job.finished()) {
                if (finish(job, storage.get())) {
                    iterator.remove();
                    finished.accept(job);
                    changed = true;
                }
                continue;
            }
            if (found == null) {
                found = providers.get();
            }
            changed |= dispatch(level, host, job, found);
        }
        return changed;
    }

    // Jobs take what they await that's back from tape (taking more than is hot keeps the recall going).
    private boolean takeAwaited(@Nullable NetworkStorage storage) {
        if (storage == null) {
            return false;
        }
        boolean changed = false;
        for (CraftingJob job : jobs) {
            for (ItemKey key : List.copyOf(job.awaiting.keySet())) {
                long want = job.awaiting.get(key);
                long taken = storage.extract(key, want, false);
                if (taken > 0) {
                    job.held.merge(key, taken, Long::sum);
                    changed = true;
                }
                if (taken >= want) {
                    job.awaiting.remove(key);
                } else {
                    job.awaiting.put(key, want - taken);
                }
            }
        }
        return changed;
    }

    // Offers each step's next runs to the providers holding its schematic, while the job holds the inputs.
    private static boolean dispatch(ServerLevel level, BlockPos host, CraftingJob job, List<CraftingProvider> providers) {
        boolean changed = false;
        int offers = 0;
        for (int index = 0; index < job.steps.size(); index++) {
            CraftingJob.Step step = job.steps.get(index);
            while (step.left() > 0 && offers < MAX_OFFERS && job.holds(step.schematic.inputTotals())) {
                CraftTask task = new CraftTask(host, job.id, index, step.schematic, step.schematic.inputs().stream()
                        .map(input -> input.item().create()).toList());
                boolean taken = false;
                for (CraftingProvider provider : providers) {
                    offers++;
                    if (provider.accepts(step.schematic) && provider.offer(level, task)) {
                        taken = true;
                        break;
                    }
                }
                if (!taken) {
                    break;
                }
                job.takeInputs(step.schematic);
                step.running++;
                changed = true;
            }
        }
        return changed;
    }

    // A finished job puts everything it holds into the network; true once it all went in.
    private static boolean finish(CraftingJob job, @Nullable NetworkStorage storage) {
        if (storage == null) {
            return false;
        }
        for (ItemKey key : List.copyOf(job.held.keySet())) {
            long count = job.held.get(key);
            long stored = storage.store(key, count, false);
            if (stored >= count) {
                job.held.remove(key);
            } else {
                job.held.put(key, count - stored);
            }
        }
        return job.held.isEmpty();
    }

    // Cancels a job: what it holds goes into storage, and drops at host what doesn't fit.
    // Returns the job cancelled, or null.
    public @Nullable CraftingJob cancel(ServerLevel level, BlockPos host, UUID id, @Nullable NetworkStorage storage) {
        CraftingJob job = job(id);
        if (job == null) {
            return null;
        }
        jobs.remove(job);
        putBack(level, host, job.heldStacks(), storage);
        return job;
    }

    // Every job's items dropped at pos (the host broken); returns the jobs, which have failed.
    public List<CraftingJob> dropAll(ServerLevel level, BlockPos pos) {
        for (CraftingJob job : jobs) {
            for (ItemStack stack : job.heldStacks()) {
                Block.popResource(level, pos, stack);
            }
        }
        List<CraftingJob> dropped = List.copyOf(jobs);
        jobs.clear();
        return dropped;
    }

    // Items into storage (not claimed by waiting jobs: they're coming back, not arriving); what doesn't fit drops at pos.
    public static void putBack(ServerLevel level, BlockPos pos, List<ItemStack> items, @Nullable NetworkStorage storage) {
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack left = stack.copy();
            if (storage != null) {
                left.shrink((int) storage.store(ItemKey.of(left), left.getCount(), false));
            }
            if (!left.isEmpty()) {
                Block.popResource(level, pos, left);
            }
        }
    }

    public void load(List<CraftingJob> saved) {
        jobs.clear();
        jobs.addAll(saved);
    }
}
