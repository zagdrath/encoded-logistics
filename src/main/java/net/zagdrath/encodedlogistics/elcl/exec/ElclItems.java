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

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.PressurizedSource;
import net.zagdrath.encodedlogistics.storage.PressurizedSources;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// Item IDs as scripts write them (ELCL_SPEC.md 2): 'minecraft:iron_ingot', or unqualified (IRON_INGOT) - first
// minecraft:, then encodedlogistics:, then any mod's unique match (ELC1205 when more than one has it; ELC1201 when
// none). Scripts get IDs back the same way: minecraft's as their path in upper case, others in full.
//
// Fluids and gases (TYPE(*FLUID) / TYPE(*PRES)) are named by their full registry ID ('minecraft:water',
// 'arcforge:hydrogen'; an unqualified ID is taken as minecraft's). Where one is kept as text among items (crafting
// history), it's its type code and ID: "FLUID minecraft:water", "PRES arcforge:hydrogen" (text / parse).
public final class ElclItems {
    private ElclItems() {}

    // A fluid or gas by ID, of that type: ELC1201 when there's no such fluid or gas, ELC1207 when it's the other type
    // (a gas asked for as a fluid, or the other way round).
    public static StorageKey resolveResource(String spec, ResourceType type) throws ElclException {
        String text = spec.strip().toLowerCase(Locale.ROOT);
        Identifier id = Identifier.tryParse(text.contains(":") ? text : "minecraft:" + text);
        if (id == null || text.isEmpty()) {
            throw new ElclException("ELC1201", spec);
        }
        if (type == ResourceType.ITEM) {
            return StorageKey.of(new ItemStack(resolve(spec)));
        }
        StorageKey key = null;
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (fluid != null && fluid != Fluids.EMPTY) {
            key = StorageKey.fluid(fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid);
        } else {
            for (PressurizedSource source : PressurizedSources.all()) {
                if (source.all().contains(id)) {
                    key = StorageKey.pressurized(source.id(), id);
                }
            }
        }
        if (key == null) {
            throw new ElclException("ELC1201", spec);
        }
        if (!key.is(type)) {
            throw new ElclException("ELC1207", key.id().toString(), key.type().special(), type.special());
        }
        return key;
    }

    // TYPE(*ITEM|*FLUID|*PRES|*ALL): the types a command works on (*ALL: every keyed type).
    public static List<ResourceType> types(String special) {
        ResourceType type = ResourceType.bySpecial(special);
        return type != null ? List.of(type) : List.of(ResourceType.KEYED);
    }

    // The stored kinds a script's ITEM names under TYPE(): an item's kinds (with any components), or the fluid or gas with
    // that ID; *ALL, whichever of them the ID names. ELC1201 (or ELC1205, ELC1207 for one type) when it names none.
    public static List<StorageKey> keys(NetworkStorage storage, String spec, String type) throws ElclException {
        List<ResourceType> types = types(type);
        List<StorageKey> keys = new ArrayList<>();
        ElclException first = null;
        for (ResourceType each : types) {
            try {
                if (each == ResourceType.ITEM) {
                    keys.addAll(keys(storage, resolve(spec)));
                    if (keys.isEmpty()) {
                        keys.add(StorageKey.of(new ItemStack(resolve(spec))));
                    }
                } else {
                    keys.add(resolveResource(spec, each));
                }
            } catch (ElclException e) {
                if (types.size() == 1) {
                    throw e;
                }
                if (first == null || !e.elclMessage().id().equals("ELC1207")) {
                    first = e;
                }
            }
        }
        if (keys.isEmpty()) {
            throw first != null ? first : new ElclException("ELC1201", spec);
        }
        return keys;
    }

    // How much of the keys there is: hot, cold or both (*HOT, *COLD, *ALL); only items go to tape.
    public static long count(NetworkStorage storage, List<StorageKey> keys, String tier) {
        long count = 0;
        for (StorageKey key : keys) {
            if (!tier.equals("*COLD")) {
                count += storage.count(key);
            }
            if (!tier.equals("*HOT") && key.isItem()) {
                count += storage.cold().count(key);
            }
        }
        return count;
    }

    // A resource's ID as a script gets it back: an item's (IRON_INGOT), or a fluid's or gas's full ID.
    public static String scriptId(StorageKey key) {
        return key.isItem() ? id(key.stack().getItem()) : key.id().toString();
    }

    // A resource as text: an item's ID (IRON_INGOT), a fluid's or gas's type code and ID ("FLUID minecraft:water").
    public static String text(StorageKey key) {
        return key.isItem() ? id(key.stack().getItem()) : key.type().code() + " " + key.id();
    }

    // The resource text names (text), or null when it's gone.
    public static @Nullable StorageKey parse(String text) {
        int space = text.indexOf(' ');
        ResourceType type = space > 0 ? ResourceType.bySpecial(text.substring(0, space)) : null;
        try {
            if (type != null && type != ResourceType.ITEM) {
                return resolveResource(text.substring(space + 1), type);
            }
            return StorageKey.of(new ItemStack(resolve(text)));
        } catch (ElclException e) {
            return null;
        }
    }

    // A resource's name for its text: the item's, fluid's or gas's display name, or the text itself.
    public static String displayName(String text) {
        StorageKey key = parse(text);
        return key != null ? key.displayName().getString() : text;
    }

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
        if (spec.indexOf(' ') > 0) {
            return displayName(spec);
        }
        try {
            return new ItemStack(resolve(spec)).getHoverName().getString();
        } catch (ElclException e) {
            return spec;
        }
    }

    // The stored kinds of an item (it may be stored with different components), hot and cold.
    public static List<StorageKey> keys(NetworkStorage storage, Item item) {
        List<StorageKey> keys = new ArrayList<>();
        for (StorageKey key : storage.listAll().keySet()) {
            if (key.stack().is(item)) {
                keys.add(key);
            }
        }
        return keys;
    }

    // How many of an item there are: hot, cold or both (*HOT, *COLD, *ALL).
    public static long count(NetworkStorage storage, Item item, String tier) {
        long count = 0;
        for (StorageKey key : keys(storage, item)) {
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
    public static boolean matches(StorageKey key, String filter) {
        String f = filter.strip();
        if (f.isEmpty() || f.equalsIgnoreCase("*ALL")) {
            return true;
        }
        if (f.startsWith("#")) {
            return TerminalService.matchesFilter(key, f.toLowerCase(Locale.ROOT));
        }
        Pattern pattern = Pattern.compile(("\\Q" + f.toLowerCase(Locale.ROOT) + "\\E").replace("*", "\\E.*\\Q"));
        Identifier id = key.id();
        return pattern.matcher(id.toString()).matches() || pattern.matcher(id.getPath()).matches()
                || pattern.matcher(key.displayName().getString().toLowerCase(Locale.ROOT)).matches();
    }

    // Totals per item (kinds added together), hot, cold or both; items only.
    public static Map<Item, Long> totals(NetworkStorage storage, String tier) {
        Map<Item, Long> totals = new java.util.LinkedHashMap<>();
        if (!tier.equals("*COLD")) {
            storage.list(ResourceType.ITEM).forEach((key, count) -> totals.merge(key.stack().getItem(), count, Long::sum));
        }
        if (!tier.equals("*HOT")) {
            storage.coldList().forEach((key, count) -> totals.merge(key.stack().getItem(), count, Long::sum));
        }
        return totals;
    }
}
