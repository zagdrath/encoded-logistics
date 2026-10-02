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

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// Works out a crafting request: the ingredient tree, what of it the network already has, what has to be made (and by
// which schematics, how many times), and what's missing. The requested item itself is always made. Each ingredient is
// taken first from what the job's own crafts leave over (four planks from a log when two are needed), then from storage,
// and the rest is made with the first schematic that outputs it - or is missing when there's none, or when making it
// would need itself.
public final class CraftPlanner {
    private static final int MAX_DEPTH = 32, MAX_LINES = 2_000;

    // One node of the tree: its depth, the item, how many of it the node needs, and of those how many are in storage
    // (have), will be made (make, which can be more than needed when a craft makes several) and are missing.
    public record Line(int depth, ItemKey key, long have, long make, long missing) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Line::depth,
                ItemKey.STREAM_CODEC, Line::key,
                ByteBufCodecs.VAR_LONG, Line::have,
                ByteBufCodecs.VAR_LONG, Line::make,
                ByteBufCodecs.VAR_LONG, Line::missing,
                Line::new);
    }

    // crafts: each schematic and how many times it runs; take: what comes out of storage when the job starts; memory:
    // the job memory it needs (every node's amount added up); missing: how many different items are missing.
    public record Plan(ItemKey target, long amount, List<Line> lines, Map<Schematic, Long> crafts, Map<ItemKey, Long> take, long memory,
            int missing) {
        public boolean complete() {
            return missing == 0 && !crafts.isEmpty();
        }
    }

    private final Map<ItemKey, Long> stored;
    private final Map<ItemKey, List<Schematic>> recipes;
    private final Map<ItemKey, Long> leftover = new HashMap<>();
    private final Map<ItemKey, Long> take = new LinkedHashMap<>();
    private final Map<Schematic, Long> crafts = new LinkedHashMap<>();
    private final List<Line> lines = new ArrayList<>();
    private final Set<ItemKey> missingKeys = new HashSet<>();
    private long memory;

    private CraftPlanner(Map<ItemKey, Long> stored, Map<ItemKey, List<Schematic>> recipes) {
        this.stored = new HashMap<>(stored);
        this.recipes = recipes;
    }

    // stored: what the network holds; recipes: every schematic on the network by what it outputs.
    public static Plan plan(Map<ItemKey, Long> stored, Map<ItemKey, List<Schematic>> recipes, ItemKey target, long amount) {
        CraftPlanner planner = new CraftPlanner(stored, recipes);
        planner.node(target, amount, 0, new ArrayList<>());
        return new Plan(target, amount, List.copyOf(planner.lines), planner.crafts, planner.take, planner.memory, planner.missingKeys.size());
    }

    // Every schematic among these, by each item it outputs, in the order given.
    public static Map<ItemKey, List<Schematic>> byOutput(List<Schematic> schematics) {
        Map<ItemKey, List<Schematic>> recipes = new LinkedHashMap<>();
        for (Schematic schematic : schematics) {
            for (ItemKey output : schematic.outputTotals().keySet()) {
                List<Schematic> list = recipes.computeIfAbsent(output, key -> new ArrayList<>());
                if (!list.contains(schematic)) {
                    list.add(schematic);
                }
            }
        }
        return recipes;
    }

    private void node(ItemKey key, long amount, int depth, List<ItemKey> path) {
        memory += amount;
        int at = lines.size();
        if (lines.size() >= MAX_LINES) {
            missingKeys.add(key);
            return;
        }
        lines.add(null);
        long need = amount, have = 0;
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
            have = fromLeftover + fromStorage;
        }
        if (need <= 0) {
            lines.set(at, new Line(depth, key, have, 0, 0));
            return;
        }
        Schematic schematic = depth < MAX_DEPTH && !path.contains(key) ? schematicFor(key) : null;
        if (schematic == null) {
            missingKeys.add(key);
            lines.set(at, new Line(depth, key, have, 0, need));
            return;
        }
        long perRun = schematic.outputCount(key);
        long runs = (need + perRun - 1) / perRun;
        crafts.merge(schematic, runs, Long::sum);
        long made = runs * perRun;
        // What the runs make beyond what's needed here (extra of this item, other outputs) can serve later nodes.
        for (Map.Entry<ItemKey, Long> output : schematic.outputTotals().entrySet()) {
            long extra = output.getValue() * runs - (output.getKey().equals(key) ? need : 0);
            if (extra > 0) {
                leftover.merge(output.getKey(), extra, Long::sum);
            }
        }
        lines.set(at, new Line(depth, key, have, made, 0));
        path.add(key);
        for (Map.Entry<ItemKey, Long> input : schematic.inputTotals().entrySet()) {
            node(input.getKey(), input.getValue() * runs, depth + 1, path);
        }
        path.removeLast();
    }

    private @Nullable Schematic schematicFor(ItemKey key) {
        List<Schematic> options = recipes.get(key);
        return options == null || options.isEmpty() ? null : options.getFirst();
    }
}
