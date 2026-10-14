/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// Runs a job host's jobs (JobHost). Each job holds the items it took from storage from the moment it's accepted (and
// takes any it awaits from tape as they come back, once a second), waits in the queue for a thread, and then runs: every
// tick each of its steps with the inputs for another run is offered to the network's Fabricators and Gateways (and
// Fabrication Servers) holding its schematic. Whatever they make comes back to the job (SchedulerCoreBlockEntity.deliver);
// when every step is done the job puts all it holds into the network.
//
// Threads are shared out like a cluster's nodes: a thread keeps threadCrafts crafts in flight at once (one, by default:
// Fabricators and Fabrication Servers run every craft they're given side by side, so a job on 39 threads makes 39 crafts
// at a time), and a job wants ceil(crafts left / threadCrafts) of them (CraftingJob.threadsWanted). Every queued job gets one thread first (taking one
// back from the job with most, if none is free), then free threads go round the running jobs that can use more, one at
// a time; a job gives threads back as it nears its end. So a big job spreads over every Compute Server and Thread Unit
// the host has, and still leaves room for the next job to start.
public final class JobRunner {
    // Dispatches tried per job thread per tick, so a huge job can't stall the server.
    private static final int MAX_OFFERS = 64;
    private static final int AWAIT_INTERVAL = 20;

    private final List<CraftingJob> jobs = new ArrayList<>();
    // Jobs held one by one (a Midrange System's 3=Hold): a held job doesn't start, and a running one starts no more steps
    // (those running finish). Its host saves them.
    private final Set<UUID> heldJobs = new HashSet<>();

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

    public boolean isHeld(UUID id) {
        return heldJobs.contains(id);
    }

    public boolean setHeld(UUID id, boolean held) {
        if (job(id) == null) {
            return false;
        }
        return held ? heldJobs.add(id) : heldJobs.remove(id);
    }

    public Set<UUID> heldJobs() {
        return heldJobs;
    }

    public int threadsUsed() {
        return jobs.stream().filter(job -> job.running).mapToInt(job -> Math.max(1, job.threads)).sum();
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

    // Takes over another runner's jobs (and which are held), as they are: a rack's pool moving to another rack.
    public void adopt(JobRunner from) {
        jobs.addAll(from.jobs);
        heldJobs.addAll(from.heldJobs);
        from.jobs.clear();
        from.heldJobs.clear();
    }

    // One tick: queued jobs take free threads, running ones dispatch their steps, finished ones empty into storage.
    // host: the job host's position (on its tasks); finished: called for each job done. Returns whether anything changed.
    public boolean tick(ServerLevel level, BlockPos host, int threads, Supplier<List<CraftingProvider>> providers,
            Supplier<@Nullable NetworkStorage> storage, Consumer<CraftingJob> finished) {
        return tick(level, host, threads, providers, storage, finished, job -> {});
    }

    // The same; stalled: called once when a job stalls (no progress for jobStallSeconds), with its reason worked out.
    public boolean tick(ServerLevel level, BlockPos host, int threads, Supplier<List<CraftingProvider>> providers,
            Supplier<@Nullable NetworkStorage> storage, Consumer<CraftingJob> finished, Consumer<CraftingJob> stalled) {
        return tick(level, host, threads, providers, storage, storage, finished, stalled);
    }

    // The same; output: where finished jobs empty into (the network's storage with every Read/Write Share route, as a
    // terminal puts items in), where storage is what jobs draw from (only shares that allow crafting).
    public boolean tick(ServerLevel level, BlockPos host, int threads, Supplier<List<CraftingProvider>> providers,
            Supplier<@Nullable NetworkStorage> storage, Supplier<@Nullable NetworkStorage> output, Consumer<CraftingJob> finished,
            Consumer<CraftingJob> stalled) {
        boolean changed = allocate(threads);
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
                if (finish(job, output.get())) {
                    iterator.remove();
                    heldJobs.remove(job.id);
                    finished.accept(job);
                    changed = true;
                }
                continue;
            }
            if (heldJobs.contains(job.id)) {
                continue;
            }
            if (found == null) {
                found = providers.get();
            }
            changed |= dispatch(level, host, job, found, runsPerThread());
        }
        if (level.getGameTime() % STALL_INTERVAL == 0) {
            checkStalls(level, providers, stalled);
        }
        return changed;
    }

    // Stall detection: a running job (not held) whose done count, crafts in flight and awaited items haven't changed for
    // jobStallSeconds is stalled, with its reason (JobStalls); any progress clears it. Looked at once a second.
    private static final int STALL_INTERVAL = 20;

    private void checkStalls(ServerLevel level, Supplier<List<CraftingProvider>> providers, Consumer<CraftingJob> stalled) {
        long now = level.getGameTime();
        long limit = Config.JOB_STALL_SECONDS.getAsInt() * 20L;
        List<CraftingProvider> found = null;
        for (CraftingJob job : jobs) {
            if (!job.running || heldJobs.contains(job.id)) {
                job.progressAt = now;
                job.stalled = false;
                continue;
            }
            int mark = job.done() * 1_000_003 + job.inFlight() * 1_009 + job.awaiting.size();
            // Not looked at yet (progressMark -1: marks are never negative), or it moved on.
            if (job.progressMark < 0 || mark != job.progressMark) {
                job.progressMark = mark;
                job.progressAt = now;
                job.stalled = false;
                job.stallReason = null;
                job.stallStep = -1;
                continue;
            }
            if (now - job.progressAt < limit) {
                continue;
            }
            if (found == null) {
                found = providers.get();
            }
            JobStalls.Stall stall = JobStalls.find(level, job, found);
            job.stallReason = stall.reason();
            job.stallStep = stall.step();
            if (!job.stalled) {
                job.stalled = true;
                stalled.accept(job);
            }
        }
    }

    // Crafts a thread keeps in flight (threadCrafts); 0 for no limit.
    private static int runsPerThread() {
        return Config.THREAD_RUNS.getAsInt();
    }

    // The running job with most threads beyond its first, which can give one up; null when every job has just one.
    private @Nullable CraftingJob donor() {
        CraftingJob donor = null;
        for (CraftingJob job : jobs) {
            if (job.running && job.threads > 1 && (donor == null || job.threads > donor.threads)) {
                donor = job;
            }
        }
        return donor;
    }

    // Shares the host's threads out among the jobs (see the class comment); returns whether anything changed.
    private boolean allocate(int threads) {
        boolean changed = false;
        int runs = runsPerThread();
        // Running jobs keep what they still need: one nearing its end, or held, gives the rest back.
        for (CraftingJob job : jobs) {
            if (!job.running) {
                continue;
            }
            int want = heldJobs.contains(job.id) ? 1 : job.threadsWanted(runs);
            if (job.threads > want || job.threads < 1) {
                job.threads = Math.max(1, Math.min(job.threads, want));
                changed = true;
            }
        }
        int free = threads - threadsUsed();
        // Fewer threads than the jobs hold (a Compute Server went): the jobs with most give them back, down to one each.
        while (free < 0) {
            CraftingJob donor = donor();
            if (donor == null) {
                break;
            }
            donor.threads--;
            free++;
            changed = true;
        }
        // Queued jobs start, a thread each, in order; when none is free, the job with most gives one up.
        for (CraftingJob job : jobs) {
            if (job.running || heldJobs.contains(job.id)) {
                continue;
            }
            if (free <= 0) {
                CraftingJob donor = free < 0 ? null : donor();
                if (donor == null) {
                    break;
                }
                donor.threads--;
                free++;
            }
            job.running = true;
            job.threads = 1;
            free--;
            changed = true;
        }
        // Free threads go round the running jobs that can use more, one at a time, so they share them fairly.
        boolean given = true;
        while (free > 0 && given) {
            given = false;
            for (CraftingJob job : jobs) {
                if (free <= 0) {
                    break;
                }
                if (job.running && !heldJobs.contains(job.id) && job.threads < job.threadsWanted(runs)) {
                    job.threads++;
                    free--;
                    given = true;
                    changed = true;
                }
            }
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
            for (StorageKey key : List.copyOf(job.awaiting.keySet())) {
                long want = job.awaiting.get(key);
                long taken = storage.extract(key, want, false);
                if (taken > 0) {
                    job.held.merge(key, taken, Long::sum);
                    job.taken.merge(key, taken, Long::sum);
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

    // Offers each step's next runs to the providers holding its schematic, while the job holds the inputs and its
    // threads have room for another craft in flight (runsPerThread each; 0: any number). Each run goes to the least busy
    // of them that takes it (CraftingProvider.load; ties in network order), so a second job's runs go to idle machines
    // rather than queueing behind the first job's on the same ones.
    private static boolean dispatch(ServerLevel level, BlockPos host, CraftingJob job, List<CraftingProvider> providers, int runsPerThread) {
        boolean changed = false;
        int offers = 0, maxOffers = MAX_OFFERS * Math.max(1, job.threads);
        int inFlight = job.inFlight(), capacity = runsPerThread <= 0 ? Integer.MAX_VALUE : Math.max(1, job.threads) * runsPerThread;
        for (int index = 0; index < job.steps.size(); index++) {
            CraftingJob.Step step = job.steps.get(index);
            List<CraftingProvider> able = null;
            while (step.left() > 0 && offers < maxOffers && inFlight < capacity && job.holds(step.schematic.inputTotals())) {
                CraftTask task = new CraftTask(host, job.id, index, step.schematic, step.schematic.inputs().stream()
                        .map(input -> input.item().create()).toList());
                if (able == null) {
                    able = new ArrayList<>();
                    for (CraftingProvider provider : providers) {
                        if (provider.accepts(step.schematic)) {
                            able.add(provider);
                        }
                    }
                }
                // Stable: equally busy providers keep their network order.
                able.sort(Comparator.comparingInt(CraftingProvider::load));
                boolean taken = false;
                for (CraftingProvider provider : able) {
                    offers++;
                    if (provider.offer(level, task)) {
                        taken = true;
                        break;
                    }
                }
                if (!taken) {
                    break;
                }
                job.takeInputs(step.schematic);
                step.running++;
                inFlight++;
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
        if (job.returned == null) {
            job.returned = new java.util.LinkedHashMap<>(job.held);
        }
        for (StorageKey key : List.copyOf(job.held.keySet())) {
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
                // Fluids and gases can't drop: they're lost with the host.
                if (ResourceEntryItem.entry(stack) == null) {
                    Block.popResource(level, pos, stack);
                }
            }
        }
        List<CraftingJob> dropped = List.copyOf(jobs);
        jobs.clear();
        return dropped;
    }

    // Items into storage (not claimed by waiting jobs: they're coming back, not arriving); what doesn't fit drops at pos.
    // Fluids and gases (Resource Entries) go in as far as they fit; the rest can't drop and is lost.
    public static void putBack(ServerLevel level, BlockPos pos, List<ItemStack> items, @Nullable NetworkStorage storage) {
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            StorageKey entry = ResourceEntryItem.key(stack);
            if (entry != null) {
                if (storage != null) {
                    storage.store(entry, ResourceEntryItem.amount(stack), false);
                }
                continue;
            }
            ItemStack left = stack.copy();
            if (storage != null) {
                left.shrink((int) storage.store(StorageKey.of(left), left.getCount(), false));
            }
            if (!left.isEmpty()) {
                Block.popResource(level, pos, left);
            }
        }
    }

    public void load(List<CraftingJob> saved) {
        jobs.clear();
        jobs.addAll(saved);
        heldJobs.removeIf(id -> job(id) == null);
    }
}
