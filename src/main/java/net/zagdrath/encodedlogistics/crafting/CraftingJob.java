/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// A crafting job on a Scheduler: the item and amount asked for, the job memory it takes, its steps (each a schematic
// and how many times to run it), and the items it holds - what it took from storage when it started and what its
// steps have made so far. A step runs whenever the job holds the inputs for one more run and a Fabricator or Gateway
// with its schematic is free. Once every step is done, everything the job holds goes into the network. Items the plan
// found on tape are awaited: recalled when the job starts, taken from storage as they come back (JobRunner).
//
// It also knows who asked for it (the player, by id, and their Terminal OS user name), who it runs as (a script's
// user, once scripts can start jobs; else the requester), the ELCL job that started it (and the schedule entry or
// trigger behind that), when it started (game time, and the clock the screens show) and what it took from storage:
// what its end is told and recorded with (JobEvents, CraftLog).
public final class CraftingJob {
    public static final class Step {
        static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                Schematic.CODEC.fieldOf("schematic").forGetter(step -> step.schematic),
                Codec.INT.fieldOf("total").forGetter(step -> step.total),
                Codec.INT.fieldOf("done").forGetter(step -> step.done),
                Codec.INT.fieldOf("running").forGetter(step -> step.running))
                .apply(i, Step::new));

        public final Schematic schematic;
        public final int total;
        public int done, running;

        public Step(Schematic schematic, int total, int done, int running) {
            this.schematic = schematic;
            this.total = total;
            this.done = done;
            this.running = running;
        }

        public boolean finished() {
            return done >= total && running <= 0;
        }

        public int left() {
            return total - done - running;
        }
    }

    private record Held(StorageKey key, long count) {
        static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
                StorageKey.CODEC.fieldOf("item").forGetter(Held::key),
                Codec.LONG.fieldOf("count").forGetter(Held::count))
                .apply(i, Held::new));
    }

    public static final Codec<CraftingJob> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(job -> job.id),
            StorageKey.CODEC.fieldOf("target").forGetter(job -> job.target),
            Codec.LONG.fieldOf("amount").forGetter(job -> job.amount),
            Codec.LONG.fieldOf("memory").forGetter(job -> job.memory),
            Step.CODEC.listOf().fieldOf("steps").forGetter(job -> job.steps),
            Held.CODEC.listOf().fieldOf("held").forGetter(job -> job.held.entrySet().stream().map(e -> new Held(e.getKey(), e.getValue())).toList()),
            Codec.BOOL.fieldOf("running").forGetter(job -> job.running),
            Held.CODEC.listOf().optionalFieldOf("awaiting", List.of())
                    .forGetter(job -> job.awaiting.entrySet().stream().map(e -> new Held(e.getKey(), e.getValue())).toList()),
            UUIDUtil.CODEC.optionalFieldOf("requester").forGetter(job -> job.requester),
            Codec.STRING.optionalFieldOf("user", "").forGetter(job -> job.user),
            Codec.STRING.optionalFieldOf("run_as", "").forGetter(job -> job.runAs),
            Codec.LONG.optionalFieldOf("started", -1L).forGetter(job -> job.started),
            Held.CODEC.listOf().optionalFieldOf("taken", List.of())
                    .forGetter(job -> job.taken.entrySet().stream().map(e -> new Held(e.getKey(), e.getValue())).toList()),
            Codec.STRING.optionalFieldOf("origin", "").forGetter(job -> job.origin),
            Codec.LONG.optionalFieldOf("started_clock", -1L).forGetter(job -> job.startedClock))
            .apply(i, (id, target, amount, memory, steps, held, running, awaiting, requester, user, runAs, started, taken, origin, startedClock) -> {
                CraftingJob job = new CraftingJob(id, target, amount, memory, steps);
                held.forEach(entry -> job.held.put(entry.key(), entry.count()));
                awaiting.forEach(entry -> job.awaiting.put(entry.key(), entry.count()));
                job.running = running;
                job.requester = requester;
                job.user = user;
                job.runAs = runAs;
                job.started = started;
                taken.forEach(entry -> job.taken.put(entry.key(), entry.count()));
                job.origin = origin;
                job.startedClock = startedClock;
                return job;
            }));

    public final UUID id;
    public final StorageKey target;
    public final long amount, memory;
    public final List<Step> steps;
    // The items the job holds, in the order they arrived.
    public final Map<StorageKey, Long> held = new LinkedHashMap<>();
    // Items it's still to take from storage, being recalled from tape.
    public final Map<StorageKey, Long> awaiting = new LinkedHashMap<>();
    // Running (has a thread) or waiting in the queue.
    public boolean running;
    // Who asked for it (none for jobs saved before this was kept), their Terminal OS user, the user it runs as (empty:
    // the requester's), and the game time it started (-1 unknown).
    public Optional<UUID> requester = Optional.empty();
    public String user = "", runAs = "";
    public long started = -1;
    // What it took from storage (at the start, and back from tape): what it consumed, less what it gives back.
    public final Map<StorageKey, Long> taken = new LinkedHashMap<>();
    // The ELCL job that started it ("000123/USER/NAME", and " *SCDE NAME" or " *TRGEVT NAME" when a schedule entry or
    // trigger submitted that); empty for a player's request. The overworld clock when it started (-1 unknown).
    public String origin = "";
    public long startedClock = -1;
    // What it held when it finished, before that went into the network (not saved: a job finishing across a restart
    // reports what it still held).
    public @Nullable Map<StorageKey, Long> returned;

    // What it holds at its end: what went back to the network, or what it holds still.
    public Map<StorageKey, Long> atEnd() {
        return returned != null ? returned : held;
    }

    // The Terminal OS user its end is told to.
    public String notifyUser() {
        return runAs.isEmpty() ? user : runAs;
    }

    // Whether what it makes in the end comes from a Processing Schematic (a machine) rather than crafting.
    public boolean processing() {
        for (Step step : steps) {
            if (step.schematic.output().is(target.stack().getItem())) {
                return step.schematic.kind() == Schematic.Kind.PROCESSING;
            }
        }
        return false;
    }

    public CraftingJob(UUID id, StorageKey target, long amount, long memory, List<Step> steps) {
        this.id = id;
        this.target = target;
        this.amount = amount;
        this.memory = memory;
        this.steps = new ArrayList<>(steps);
    }

    public int done() {
        return steps.stream().mapToInt(step -> Math.min(step.done, step.total)).sum();
    }

    public int total() {
        return steps.stream().mapToInt(step -> step.total).sum();
    }

    public boolean finished() {
        return steps.stream().allMatch(Step::finished);
    }

    public void add(ItemStack stack) {
        if (!stack.isEmpty()) {
            held.merge(StorageKey.of(stack), (long) stack.getCount(), Long::sum);
        }
    }

    // Whether it holds everything in amounts.
    public boolean holds(Map<StorageKey, Long> amounts) {
        for (Map.Entry<StorageKey, Long> entry : amounts.entrySet()) {
            if (held.getOrDefault(entry.getKey(), 0L) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    // Takes one run's inputs for a schematic out of what it holds, as the stacks the schematic lists.
    public List<ItemStack> takeInputs(Schematic schematic) {
        List<ItemStack> taken = new ArrayList<>();
        for (Schematic.Input input : schematic.inputs()) {
            ItemStack stack = input.item().create();
            StorageKey key = StorageKey.of(stack);
            long left = held.getOrDefault(key, 0L) - stack.getCount();
            if (left > 0) {
                held.put(key, left);
            } else {
                held.remove(key);
            }
            taken.add(stack);
        }
        return taken;
    }

    // Everything it holds, as stacks.
    public List<ItemStack> heldStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        held.forEach((key, count) -> {
            long left = count;
            while (left > 0) {
                int size = (int) Math.min(left, key.maxStackSize());
                stacks.add(key.toStack(size));
                left -= size;
            }
        });
        return stacks;
    }
}
