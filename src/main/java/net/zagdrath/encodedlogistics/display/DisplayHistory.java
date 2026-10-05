/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.zagdrath.encodedlogistics.Config;

// A screen's own graph history (HANDOFF 3): one sample a second of each stat its graphs show (keyed "*ENERGY",
// "*ITEM minecraft:cobblestone"), the last displayHistorySeconds of them, kept while it's loaded. Used without a
// Monitoring Server on the network, and always for *STORAGE and *ITEM.
final class DisplayHistory {
    private final Map<String, float[]> rings = new HashMap<>();
    private final Map<String, Integer> heads = new HashMap<>(), counts = new HashMap<>();

    private static int size() {
        return Math.max(10, Config.DISPLAY_HISTORY_SECONDS.getAsInt());
    }

    void add(String key, float value) {
        float[] ring = rings.computeIfAbsent(key, k -> new float[size()]);
        int head = heads.getOrDefault(key, 0);
        ring[head] = value;
        heads.put(key, (head + 1) % ring.length);
        counts.merge(key, 1, (a, b) -> Math.min(ring.length, a + b));
    }

    // The last n samples, oldest first (fewer while it's filling).
    float[] last(String key, int n) {
        float[] ring = rings.get(key);
        if (ring == null) {
            return new float[0];
        }
        int count = Math.min(n, counts.getOrDefault(key, 0)), head = heads.getOrDefault(key, 0);
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = ring[Math.floorMod(head - count + i, ring.length)];
        }
        return out;
    }

    // A counter's rate a minute, from its change since the last second's sample (0 the first time).
    float perMinute(String key, long cumulative) {
        Long before = totals.put(key, cumulative);
        return before == null ? 0 : (cumulative - before) * 60F;
    }

    private final Map<String, Long> totals = new HashMap<>();

    // Drops what no graph shows any more.
    void keep(Set<String> keys) {
        rings.keySet().retainAll(keys);
        heads.keySet().retainAll(keys);
        counts.keySet().retainAll(keys);
    }
}
