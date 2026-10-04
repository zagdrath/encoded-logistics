/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// Item IDs as scripts write them (ELCL_SPEC.md 2): 'minecraft:iron_ingot', or unqualified (IRON_INGOT) - first
// minecraft:, then encodedlogistics:, then any mod's unique match (ELC1205 when more than one has it; ELC1201 when
// none). Scripts get IDs back the same way: minecraft's as their path in upper case, others in full.
public final class ElclItems {
    private ElclItems() {}

    public static Item resolve(String spec) throws ElclException {
        String text = spec.strip();
        if (text.isEmpty()) {
            throw new ElclException("ELC1201", spec);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains(":")) {
            Identifier id = Identifier.tryParse(lower);
            Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : null;
            if (item == null || item == Items.AIR && !lower.equals("minecraft:air")) {
                throw new ElclException("ELC1201", text);
            }
            return item;
        }
        for (String namespace : new String[] { "minecraft", EncodedLogistics.MODID }) {
            Identifier id = Identifier.tryParse(namespace + ":" + lower);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                return BuiltInRegistries.ITEM.getValue(id);
            }
        }
        List<Item> found = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (id.getPath().equals(lower)) {
                found.add(BuiltInRegistries.ITEM.getValue(id));
            }
        }
        if (found.size() > 1) {
            throw new ElclException("ELC1205", text);
        }
        if (found.isEmpty()) {
            throw new ElclException("ELC1201", text);
        }
        return found.getFirst();
    }

    // IRON_INGOT, or encodedlogistics:logic_die.
    public static String id(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals("minecraft") ? id.getPath().toUpperCase(Locale.ROOT) : id.toString();
    }

    public static String name(String spec) {
        try {
            return new ItemStack(resolve(spec)).getHoverName().getString();
        } catch (ElclException e) {
            return spec;
        }
    }

    // The stored kinds of an item (it may be stored with different components), hot and cold.
    public static List<ItemKey> keys(NetworkStorage storage, Item item) {
        List<ItemKey> keys = new ArrayList<>();
        for (ItemKey key : storage.listAll().keySet()) {
            if (key.stack().is(item)) {
                keys.add(key);
            }
        }
        return keys;
    }

    // How many of an item there are: hot, cold or both (*HOT, *COLD, *ALL).
    public static long count(NetworkStorage storage, Item item, String tier) {
        long count = 0;
        for (ItemKey key : keys(storage, item)) {
            if (!tier.equals("*COLD")) {
                count += storage.count(key);
            }
            if (!tier.equals("*HOT")) {
                count += storage.cold().count(key);
            }
        }
        return count;
    }

    // RTVITMLST's FILTER: *ALL, text with * wildcards (on the ID, its path or the display name), or #tag.
    public static boolean matches(ItemKey key, String filter) {
        String f = filter.strip();
        if (f.isEmpty() || f.equalsIgnoreCase("*ALL")) {
            return true;
        }
        if (f.startsWith("#")) {
            return TerminalService.matchesFilter(key, f.toLowerCase(Locale.ROOT));
        }
        Pattern pattern = Pattern.compile(("\\Q" + f.toLowerCase(Locale.ROOT) + "\\E").replace("*", "\\E.*\\Q"));
        Identifier id = BuiltInRegistries.ITEM.getKey(key.stack().getItem());
        return pattern.matcher(id.toString()).matches() || pattern.matcher(id.getPath()).matches()
                || pattern.matcher(key.stack().getHoverName().getString().toLowerCase(Locale.ROOT)).matches();
    }

    // Totals per item (kinds added together), hot, cold or both.
    public static Map<Item, Long> totals(NetworkStorage storage, String tier) {
        Map<Item, Long> totals = new java.util.LinkedHashMap<>();
        if (!tier.equals("*COLD")) {
            storage.list().forEach((key, count) -> totals.merge(key.stack().getItem(), count, Long::sum));
        }
        if (!tier.equals("*HOT")) {
            storage.coldList().forEach((key, count) -> totals.merge(key.stack().getItem(), count, Long::sum));
        }
        return totals;
    }
}
