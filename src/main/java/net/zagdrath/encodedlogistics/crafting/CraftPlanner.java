/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// Works out a crafting request: the ingredient tree, what of it the network already has, what has to be made (and by
// which schematics, how many times), and what's missing. The requested item itself is always made. Each ingredient is
// taken first from what the job's own crafts leave over (four planks from a log when two are needed), then from storage,
// and the rest is made by a schematic that outputs it - or is missing when there's none, or when making it would need
// itself. The schematics are tried in their order (the first found keeps priority): the first whose whole ingredient tree
// can be had is used (a chest from birch planks when the oak one's planks can't be had); when none can, the first, so the
// plan shows what that one is missing. What's on tape counts as stored, after what's hot: the plan takes it as a recall.
public final class CraftPlanner {
    private static final int MAX_DEPTH = 32, MAX_LINES = 2_000;
    // Nodes planned in all, alternatives tried included; past it, no more alternatives are tried (the first schematic
    // is used), so a deep tree with many recipes can't take forever.
    private static final int MAX_WORK = 50_000;

    // One node of the tree: its depth, the item, how many of it the node needs, and of those how many are in storage
    // (have; of them, cold are on tape), will be made (make, which can be more than needed when a craft makes several)
    // and are missing.
    public record Line(int depth, StorageKey key, long have, long make, long missing, long cold) {
        public Line(int depth, StorageKey key, long have, long make, long missing) {
            this(depth, key, have, make, missing, 0);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Line::depth,
                StorageKey.STREAM_CODEC, Line::key,
                ByteBufCodecs.VAR_LONG, Line::have,
                ByteBufCodecs.VAR_LONG, Line::make,
                ByteBufCodecs.VAR_LONG, Line::missing,
                ByteBufCodecs.VAR_LONG, Line::cold,
                Line::new);
    }

    // crafts: each schematic and how many times it runs; take: what comes out of storage when the job starts; recall:
    // what comes back from tape for it (recallTicks: how long that's expected to take); memory: the job memory it needs
    // (every node's amount added up); missing: how many different items are missing.
    public record Plan(StorageKey target, long amount, List<Line> lines, Map<Schematic, Long> crafts, Map<StorageKey, Long> take, Map<StorageKey, Long> recall,
            int recallTicks, long memory, int missing) {
        public boolean complete() {
            return missing == 0 && !crafts.isEmpty();
        }

        public Plan withRecallTicks(int ticks) {
            return new Plan(target, amount, lines, crafts, take, recall, ticks, memory, missing);
        }
    }

    private final Map<StorageKey, Long> stored, cold;
    private final Map<StorageKey, List<Schematic>> recipes;
    private final Map<StorageKey, Long> leftover = new HashMap<>();
    private final Map<StorageKey, Long> take = new LinkedHashMap<>(), recall = new LinkedHashMap<>();
    private final Map<Schematic, Long> crafts = new LinkedHashMap<>();
    private final List<Line> lines = new ArrayList<>();
    private final Set<StorageKey> missingKeys = new HashSet<>();
    private long memory;
    // Nodes found missing (a node can be missing an item already missing elsewhere), and nodes planned in all.
    private int missingNodes, work;

    private CraftPlanner(Map<StorageKey, Long> stored, Map<StorageKey, Long> cold, Map<StorageKey, List<Schematic>> recipes) {
        this.stored = new HashMap<>(stored);
        this.cold = new HashMap<>(cold);
        this.recipes = recipes;
    }

    // stored: what the network holds hot; recipes: every schematic on the network by what it outputs.
    public static Plan plan(Map<StorageKey, Long> stored, Map<StorageKey, List<Schematic>> recipes, StorageKey target, long amount) {
        return plan(stored, Map.of(), recipes, target, amount);
    }

    // cold: what's on tape.
    public static Plan plan(Map<StorageKey, Long> stored, Map<StorageKey, Long> cold, Map<StorageKey, List<Schematic>> recipes, StorageKey target, long amount) {
        CraftPlanner planner = new CraftPlanner(stored, cold, recipes);
        planner.node(target, amount, 0, new ArrayList<>());
        return new Plan(target, amount, List.copyOf(planner.lines), planner.crafts, planner.take, planner.recall, 0, planner.memory,
                planner.missingKeys.size());
    }

    // Every schematic among these, by each item it outputs, in the order given.
    public static Map<StorageKey, List<Schematic>> byOutput(List<Schematic> schematics) {
        Map<StorageKey, List<Schematic>> recipes = new LinkedHashMap<>();
        for (Schematic schematic : schematics) {
            for (StorageKey output : schematic.outputTotals().keySet()) {
                List<Schematic> list = recipes.computeIfAbsent(output, key -> new ArrayList<>());
                if (!list.contains(schematic)) {
                    list.add(schematic);
                }
            }
        }
        return recipes;
    }

    private void node(StorageKey key, long amount, int depth, List<StorageKey> path) {
        work++;
        memory += amount;
        int at = lines.size();
        if (lines.size() >= MAX_LINES) {
            missingKeys.add(key);
            missingNodes++;
            return;
        }
        lines.add(null);
        long need = amount, have = 0, fromCold = 0;
        if (depth > 0) {
            long fromLeftover = Math.min(need, leftover.getOrDefault(key, 0L));
            leftover.merge(key, -fromLeftover, Long::sum);
            need -= fromLeftover;
            long fromStorage = Math.min(need, stored.getOrDefault(key, 0L));
            if (fromStorage > 0) {
                stored.merge(key, -fromStorage, Long::sum);
                take.merge(key, fromStorage, Long::sum);
            }
            need -= fromStorage;
            fromCold = Math.min(need, cold.getOrDefault(key, 0L));
            if (fromCold > 0) {
                cold.merge(key, -fromCold, Long::sum);
                recall.merge(key, fromCold, Long::sum);
            }
            need -= fromCold;
            have = fromLeftover + fromStorage + fromCold;
        }
        if (need <= 0) {
            lines.set(at, new Line(depth, key, have, 0, 0, fromCold));
            return;
        }
        List<Schematic> options = depth < MAX_DEPTH && !path.contains(key) ? recipes.getOrDefault(key, List.of()) : List.of();
        if (options.isEmpty()) {
            missingKeys.add(key);
            missingNodes++;
            lines.set(at, new Line(depth, key, have, 0, need, fromCold));
            return;
        }
        // In order, the first that leaves nothing missing; failing all, the first again.
        if (options.size() > 1) {
            Snapshot before = new Snapshot();
            for (Schematic option : options) {
                if (work >= MAX_WORK) {
                    break;
                }
                int missingBefore = missingNodes;
                make(key, option, need, have, fromCold, depth, at, path);
                if (missingNodes == missingBefore) {
                    return;
                }
                before.restore(at);
            }
        }
        make(key, options.getFirst(), need, have, fromCold, depth, at, path);
    }

    // The node made by one schematic: its runs, what they leave over, and its ingredients' nodes.
    private void make(StorageKey key, Schematic schematic, long need, long have, long fromCold, int depth, int at, List<StorageKey> path) {
        long perRun = schematic.outputCount(key);
        long runs = (need + perRun - 1) / perRun;
        crafts.merge(schematic, runs, Long::sum);
        long made = runs * perRun;
        // What the runs make beyond what's needed here (extra of this item, other outputs) can serve later nodes.
        for (Map.Entry<StorageKey, Long> output : schematic.outputTotals().entrySet()) {
            long extra = output.getValue() * runs - (output.getKey().equals(key) ? need : 0);
            if (extra > 0) {
                leftover.merge(output.getKey(), extra, Long::sum);
            }
        }
        lines.set(at, new Line(depth, key, have, made, 0, fromCold));
        path.add(key);
        for (Map.Entry<StorageKey, Long> input : schematic.inputTotals().entrySet()) {
            node(input.getKey(), input.getValue() * runs, depth + 1, path);
        }
        path.removeLast();
    }

    // The planner's state before a node tries a schematic, to go back to when it can't be had.
    private final class Snapshot {
        private final Map<StorageKey, Long> stored = new HashMap<>(CraftPlanner.this.stored), cold = new HashMap<>(CraftPlanner.this.cold),
                leftover = new HashMap<>(CraftPlanner.this.leftover), take = new LinkedHashMap<>(CraftPlanner.this.take),
                recall = new LinkedHashMap<>(CraftPlanner.this.recall);
        private final Map<Schematic, Long> crafts = new LinkedHashMap<>(CraftPlanner.this.crafts);
        private final Set<StorageKey> missingKeys = new HashSet<>(CraftPlanner.this.missingKeys);
        private final long memory = CraftPlanner.this.memory;
        private final int missingNodes = CraftPlanner.this.missingNodes;

        // Back to it; the lines after the node's own (at) dropped.
        void restore(int at) {
            reset(CraftPlanner.this.stored, stored);
            reset(CraftPlanner.this.cold, cold);
            reset(CraftPlanner.this.leftover, leftover);
            reset(CraftPlanner.this.take, take);
            reset(CraftPlanner.this.recall, recall);
            reset(CraftPlanner.this.crafts, crafts);
            CraftPlanner.this.missingKeys.clear();
            CraftPlanner.this.missingKeys.addAll(missingKeys);
            CraftPlanner.this.memory = memory;
            CraftPlanner.this.missingNodes = missingNodes;
            while (lines.size() > at + 1) {
                lines.removeLast();
            }
        }

        private static <K> void reset(Map<K, Long> map, Map<K, Long> to) {
            map.clear();
            map.putAll(to);
        }
    }
}
