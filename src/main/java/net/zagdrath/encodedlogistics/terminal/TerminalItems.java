/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Items as the command line names them (HANDOFF 7.3): a registry id (minecraft:iron_ingot; the namespace may be left
// off when only one matches) or a display name (quoted, or with underscores for spaces), among what the network holds
// (hot or cold) or can craft. Amounts take k and M suffixes (2k = 2000). Quantities print with thousands separators.
public final class TerminalItems {
    private TerminalItems() {}

    public static String id(ItemKey key) {
        return BuiltInRegistries.ITEM.getKey(key.stack().getItem()).toString();
    }

    // Everything the network knows of: what it holds, hot and cold, and what it can craft.
    public static Set<ItemKey> known(TerminalContext context) {
        Set<ItemKey> keys = new LinkedHashSet<>();
        NetworkStorage storage = context.storage();
        if (storage != null) {
            keys.addAll(storage.listAll().keySet());
        }
        keys.addAll(CraftRequests.craftables(context.server(), context.network()));
        return keys;
    }

    // The items a name or id could mean, best first (an exact id, then names; of several stacks of one item, the
    // plainest first).
    public static List<ItemKey> matches(TerminalContext context, String spec) {
        String wanted = spec.toLowerCase(Locale.ROOT).trim();
        String asName = wanted.replace('_', ' ');
        List<ItemKey> exact = new ArrayList<>(), named = new ArrayList<>();
        for (ItemKey key : known(context)) {
            Identifier id = BuiltInRegistries.ITEM.getKey(key.stack().getItem());
            String name = key.stack().getHoverName().getString().toLowerCase(Locale.ROOT);
            if (id.toString().equals(wanted) || !wanted.contains(":") && id.getPath().equals(wanted)) {
                exact.add(key);
            } else if (name.equals(asName)) {
                named.add(key);
            }
        }
        Comparator<ItemKey> plainest = Comparator.comparingInt(key -> key.stack().getComponentsPatch().size());
        exact.sort(plainest);
        named.sort(plainest);
        exact.addAll(named);
        return exact;
    }

    // The one item a spec means, or null (none, or several different items: the caller lists them).
    public static @Nullable ItemKey resolve(TerminalContext context, String spec) {
        List<ItemKey> found = matches(context, spec);
        if (found.isEmpty()) {
            return null;
        }
        ItemKey first = found.getFirst();
        return found.stream().allMatch(key -> key.stack().getItem() == first.stack().getItem()) ? first : null;
    }

    // A count: digits with an optional k (thousands) or M (millions); -1 if it isn't one.
    public static long amount(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        long scale = 1;
        char last = trimmed.charAt(trimmed.length() - 1);
        if (last == 'k' || last == 'K') {
            scale = 1_000;
        } else if (last == 'm' || last == 'M') {
            scale = 1_000_000;
        }
        String digits = scale > 1 ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        try {
            double value = Double.parseDouble(digits.replace(",", ""));
            return value < 0 ? -1 : (long) Math.floor(value * scale);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // "14s" ("14 s" spaced), "2m 05s" past a minute; ticks rounded up to seconds.
    public static String seconds(int ticks, boolean spaced) {
        int seconds = (ticks + 19) / 20;
        return seconds < 60 ? seconds + (spaced ? " s" : "s") : String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60);
    }

    public static String count(long count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    // Completions for an item argument: ids and (as typed) names starting with what's there.
    public static List<String> complete(TerminalContext context, String partial) {
        String start = partial.toLowerCase(Locale.ROOT);
        Set<String> out = new LinkedHashSet<>();
        for (ItemKey key : known(context)) {
            Identifier id = BuiltInRegistries.ITEM.getKey(key.stack().getItem());
            if (id.toString().startsWith(start)) {
                out.add(id.toString());
            } else if (id.getPath().startsWith(start)) {
                out.add(id.getPath());
            }
        }
        List<String> sorted = new ArrayList<>(out);
        sorted.sort(String::compareTo);
        return sorted;
    }

    // Hot and cold counts of an item.
    public static long[] counts(NetworkStorage storage, ItemKey key) {
        return new long[] { storage.count(key), storage.cold().count(key) };
    }
}
