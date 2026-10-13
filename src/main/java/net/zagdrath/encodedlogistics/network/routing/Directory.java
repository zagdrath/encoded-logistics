/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network.routing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// A Directory Server's state (block or rack form, the routing handoff 5): the devices on its network it publishes
// (by their ELCL names: only published devices can be reached as SYSNAME/DEVICE from other networks), how often each
// was looked up in the last hour, and the latest lookups made from its network.
public final class Directory {
    public static final int RECENT = 16;
    private static final long HOUR = 72_000;

    // A lookup made from this directory's network: the qualified name, what came of it ("" resolved, else the message:
    // ELC2603 no route, ELC2604 not permitted), the hops, and when (game time).
    public record Lookup(String name, String refused, int hops, long at) {
        public boolean ok() {
            return refused.isEmpty();
        }
    }

    private final Set<String> published = new TreeSet<>();
    private final Deque<Lookup> recent = new ArrayDeque<>();
    // Per published name: the hour window and its count.
    private final Map<String, long[]> counts = new HashMap<>();

    public interface Host {
        Directory directory();

        boolean isOnline();

        @Nullable NetworkRef directoryNetwork();

        String deviceName();

        // Its published list changed (save, resync).
        void directoryChanged();
    }

    public Set<String> published() {
        return published;
    }

    public boolean isPublished(String device) {
        return published.contains(device.toUpperCase(Locale.ROOT));
    }

    public boolean setPublished(String device, boolean publish) {
        String name = device.toUpperCase(Locale.ROOT);
        return publish ? published.add(name) : published.remove(name);
    }

    public List<Lookup> recent() {
        return List.copyOf(recent);
    }

    public void looked(Lookup lookup) {
        recent.addFirst(lookup);
        while (recent.size() > RECENT) {
            recent.removeLast();
        }
    }

    // A published device of its network was looked up.
    public void counted(String device, long now) {
        long[] window = counts.computeIfAbsent(device.toUpperCase(Locale.ROOT), key -> new long[] {now / HOUR, 0});
        if (window[0] != now / HOUR) {
            window[0] = now / HOUR;
            window[1] = 0;
        }
        window[1]++;
    }

    public long lookups(String device, long now) {
        long[] window = counts.get(device.toUpperCase(Locale.ROOT));
        return window == null || window[0] != now / HOUR ? 0 : window[1];
    }

    public void save(ValueOutput output) {
        output.store("published", net.minecraft.util.ExtraCodecs.NON_EMPTY_STRING.listOf(), new ArrayList<>(published));
    }

    public void load(ValueInput input) {
        published.clear();
        input.read("published", net.minecraft.util.ExtraCodecs.NON_EMPTY_STRING.listOf()).ifPresent(published::addAll);
    }
}
