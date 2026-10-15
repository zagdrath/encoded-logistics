/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// The crafts a Fabricator or Fabrication Server is working on, side by side: each with its own progress, all advancing
// together. A machine takes every craft its Schedulers offer it, so how many run at once is set by the jobs' threads
// (JobRunner: threadCrafts a thread), not by the machine.
public final class CraftQueue {
    private static final class Craft {
        private static final Codec<Craft> CODEC = RecordCodecBuilder.create(i -> i.group(
                CraftTask.CODEC.fieldOf("task").forGetter(craft -> craft.task),
                Codec.INT.fieldOf("progress").forGetter(craft -> craft.progress),
                Codec.INT.fieldOf("duration").forGetter(craft -> craft.duration)).apply(i, Craft::new));

        private final CraftTask task;
        private int progress;
        private final int duration;

        private Craft(CraftTask task, int progress, int duration) {
            this.task = task;
            this.progress = progress;
            this.duration = Math.max(1, duration);
        }
    }

    // Oldest first, so the first is the one nearest done.
    private final List<Craft> crafts = new ArrayList<>();

    public boolean isEmpty() {
        return crafts.isEmpty();
    }

    public int size() {
        return crafts.size();
    }

    // How many of its crafts are extra runs (CraftTask.extra).
    public int extraRuns() {
        return (int) crafts.stream().filter(craft -> craft.task.extra()).count();
    }

    public void add(CraftTask task, int duration) {
        crafts.add(new Craft(task, 0, duration));
    }

    // A tick of work on every craft; returns those done, oldest first (no longer held here).
    public List<CraftTask> tick() {
        List<CraftTask> done = new ArrayList<>();
        crafts.removeIf(craft -> {
            if (++craft.progress < craft.duration) {
                return false;
            }
            done.add(craft.task);
            return true;
        });
        return done;
    }

    // Every craft, dropped (the machine broken or taken out: their inputs go back to the jobs).
    public List<CraftTask> clear() {
        List<CraftTask> all = crafts.stream().map(craft -> craft.task).toList();
        crafts.clear();
        return all;
    }

    // The craft nearest done, for the machine's screen and panel; null while idle.
    public @Nullable CraftTask lead() {
        return crafts.isEmpty() ? null : crafts.getFirst().task;
    }

    public int leadProgress() {
        return crafts.isEmpty() ? 0 : crafts.getFirst().progress;
    }

    public int leadDuration() {
        return crafts.isEmpty() ? 0 : crafts.getFirst().duration;
    }

    // --- Saving ---

    public void save(ValueOutput output) {
        if (!crafts.isEmpty()) {
            output.store("crafts", Craft.CODEC.listOf(), crafts);
        }
    }

    // Also reads the single craft ("task", "progress", "duration") saved before machines ran crafts side by side.
    public void load(ValueInput input) {
        crafts.clear();
        input.read("crafts", Craft.CODEC.listOf()).ifPresent(crafts::addAll);
        input.read("task", CraftTask.CODEC).ifPresent(task -> crafts.add(new Craft(task, input.getIntOr("progress", 0), input.getIntOr("duration", 20))));
    }
}
