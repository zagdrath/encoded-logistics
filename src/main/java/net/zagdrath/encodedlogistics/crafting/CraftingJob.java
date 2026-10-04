/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// A crafting job on a Scheduler: the item and amount asked for, the job memory it takes, its steps (each a schematic
// and how many times to run it), and the items it holds - what it took from storage when it started and what its
// steps have made so far. A step runs whenever the job holds the inputs for one more run and a Fabricator or Gateway
// with its schematic is free. Once every step is done, everything the job holds goes into the network. Items the plan
// found on tape are awaited: recalled when the job starts, taken from storage as they come back (JobRunner).
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

    private record Held(ItemKey key, long count) {
        static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemKey.CODEC.fieldOf("item").forGetter(Held::key),
                Codec.LONG.fieldOf("count").forGetter(Held::count))
                .apply(i, Held::new));
    }

    public static final Codec<CraftingJob> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(job -> job.id),
            ItemKey.CODEC.fieldOf("target").forGetter(job -> job.target),
            Codec.LONG.fieldOf("amount").forGetter(job -> job.amount),
            Codec.LONG.fieldOf("memory").forGetter(job -> job.memory),
            Step.CODEC.listOf().fieldOf("steps").forGetter(job -> job.steps),
            Held.CODEC.listOf().fieldOf("held").forGetter(job -> job.held.entrySet().stream().map(e -> new Held(e.getKey(), e.getValue())).toList()),
            Codec.BOOL.fieldOf("running").forGetter(job -> job.running),
            Held.CODEC.listOf().optionalFieldOf("awaiting", List.of())
                    .forGetter(job -> job.awaiting.entrySet().stream().map(e -> new Held(e.getKey(), e.getValue())).toList()))
            .apply(i, (id, target, amount, memory, steps, held, running, awaiting) -> {
                CraftingJob job = new CraftingJob(id, target, amount, memory, steps);
                held.forEach(entry -> job.held.put(entry.key(), entry.count()));
                awaiting.forEach(entry -> job.awaiting.put(entry.key(), entry.count()));
                job.running = running;
                return job;
            }));

    public final UUID id;
    public final ItemKey target;
    public final long amount, memory;
    public final List<Step> steps;
    // The items the job holds, in the order they arrived.
    public final Map<ItemKey, Long> held = new LinkedHashMap<>();
    // Items it's still to take from storage, being recalled from tape.
    public final Map<ItemKey, Long> awaiting = new LinkedHashMap<>();
    // Running (has a thread) or waiting in the queue.
    public boolean running;

    public CraftingJob(UUID id, ItemKey target, long amount, long memory, List<Step> steps) {
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
            held.merge(ItemKey.of(stack), (long) stack.getCount(), Long::sum);
        }
    }

    // Whether it holds everything in amounts.
    public boolean holds(Map<ItemKey, Long> amounts) {
        for (Map.Entry<ItemKey, Long> entry : amounts.entrySet()) {
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
            ItemKey key = ItemKey.of(stack);
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
