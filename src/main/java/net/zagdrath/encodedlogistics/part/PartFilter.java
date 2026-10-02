/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// A port's, tap's or plane's filter: nine ghost entries (copies, no real items) and, with a Filter Module installed, three
// options: deny (everything but the entries), tag matching (an item matches an entry it shares a c: tag with) and exact
// components (an item must also have the entry's components). Without the module it's an allow-list by item.
//
// With a Fuzzy Match Module installed, each entry can match loosely instead (picked from the slot's context menu): any
// item in one of the entry's tags, or the entry's item by damage - any damage, or 0-25%, 25-50%, 50-75% or 75-100% worn.
public final class PartFilter {
    public static final int SIZE = 9;
    // Fuzzy settings, as codes: exact, any damage, the four damage quarters, then FUZZY_TAG + the index of a tag in the
    // entry's tags (tags()).
    public static final int FUZZY_NONE = 0, FUZZY_ANY_DAMAGE = 1, FUZZY_QUARTER = 2, FUZZY_TAG = 6;

    private final NonNullList<ItemStack> entries = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final int[] fuzzy = new int[SIZE];
    private final @Nullable TagKey<Item>[] fuzzyTags = newTags();
    private boolean deny, tags, components;

    @SuppressWarnings("unchecked")
    private static TagKey<Item>[] newTags() {
        return new TagKey[SIZE];
    }

    public NonNullList<ItemStack> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.stream().allMatch(ItemStack::isEmpty);
    }

    public List<ItemStack> nonEmpty() {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack entry : entries) {
            if (!entry.isEmpty()) {
                list.add(entry);
            }
        }
        return list;
    }

    public void set(int index, ItemStack stack) {
        entries.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        clearFuzzy(index);
    }

    public boolean deny() {
        return deny;
    }

    public boolean tags() {
        return tags;
    }

    public boolean components() {
        return components;
    }

    public void toggleDeny() {
        deny = !deny;
    }

    public void toggleTags() {
        tags = !tags;
    }

    public void toggleComponents() {
        components = !components;
    }

    // --- Fuzzy ---

    // An item's tags in a stable order: the context menu lists them so, and FUZZY_TAG codes count in it.
    public static List<TagKey<Item>> tags(ItemStack stack) {
        List<TagKey<Item>> list = new ArrayList<>(stack.typeHolder().tags().toList());
        list.sort(Comparator.comparing(tag -> tag.location().toString()));
        return list;
    }

    // An entry's fuzzy setting as a code (see FUZZY_*).
    public int fuzzy(int index) {
        if (fuzzy[index] != FUZZY_TAG) {
            return fuzzy[index];
        }
        int at = tags(entries.get(index)).indexOf(fuzzyTags[index]);
        return at >= 0 ? FUZZY_TAG + at : FUZZY_NONE;
    }

    public void setFuzzy(int index, int code) {
        if (index < 0 || index >= SIZE || entries.get(index).isEmpty()) {
            return;
        }
        if (code >= FUZZY_TAG) {
            List<TagKey<Item>> list = tags(entries.get(index));
            if (code - FUZZY_TAG >= list.size()) {
                return;
            }
            fuzzy[index] = FUZZY_TAG;
            fuzzyTags[index] = list.get(code - FUZZY_TAG);
        } else {
            fuzzy[index] = Math.max(FUZZY_NONE, code);
            fuzzyTags[index] = null;
        }
    }

    public void clearFuzzy(int index) {
        fuzzy[index] = FUZZY_NONE;
        fuzzyTags[index] = null;
    }

    // --- Matching ---

    // Whether an item passes. options: the Filter Module is installed (its options apply). emptyPasses: what an empty
    // filter means (everything for an Ingress Port or tap, nothing for an Egress Port).
    public boolean test(ItemStack stack, boolean options, boolean emptyPasses) {
        return test(stack, options, emptyPasses, false);
    }

    // fuzzy: a Fuzzy Match Module is installed (entries' fuzzy settings apply).
    public boolean test(ItemStack stack, boolean options, boolean emptyPasses, boolean fuzzy) {
        if (isEmpty()) {
            return emptyPasses;
        }
        boolean matched = false;
        for (int index = 0; index < SIZE; index++) {
            if (!entries.get(index).isEmpty() && matches(index, stack, options, fuzzy)) {
                matched = true;
                break;
            }
        }
        return options && deny ? !matched : matched;
    }

    // Whether an item matches one entry.
    public boolean matches(int index, ItemStack stack, boolean options, boolean fuzzyOn) {
        ItemStack entry = entries.get(index);
        if (fuzzyOn && fuzzy[index] != FUZZY_NONE) {
            if (fuzzy[index] == FUZZY_TAG) {
                return fuzzyTags[index] != null && stack.is(fuzzyTags[index]);
            }
            if (!ItemStack.isSameItem(entry, stack)) {
                return false;
            }
            if (fuzzy[index] == FUZZY_ANY_DAMAGE) {
                return true;
            }
            if (!stack.isDamageableItem()) {
                return false;
            }
            int quarter = fuzzy[index] - FUZZY_QUARTER;
            double worn = (double) stack.getDamageValue() / Math.max(1, stack.getMaxDamage());
            return quarter == 0 ? worn <= 0.25 : worn > quarter * 0.25 && worn <= (quarter + 1) * 0.25;
        }
        if (options && components ? ItemStack.isSameItemSameComponents(entry, stack) : ItemStack.isSameItem(entry, stack)) {
            return true;
        }
        return options && tags && shareConventionTag(entry, stack);
    }

    private static boolean shareConventionTag(ItemStack a, ItemStack b) {
        return a.typeHolder().tags().anyMatch(tag -> isConvention(tag) && b.is(tag));
    }

    private static boolean isConvention(TagKey<Item> tag) {
        return tag.location().getNamespace().equals("c");
    }

    // --- Saving ---

    public void load(ValueInput input) {
        ValueInput filter = input.childOrEmpty("filter");
        for (int i = 0; i < SIZE; i++) {
            entries.set(i, ItemStack.EMPTY);
            clearFuzzy(i);
        }
        ContainerHelper.loadAllItems(filter, entries);
        deny = filter.getBooleanOr("deny", false);
        tags = filter.getBooleanOr("tags", false);
        components = filter.getBooleanOr("components", false);
        List<String> saved = filter.read("fuzzy", Codec.STRING.listOf()).orElse(List.of());
        for (int i = 0; i < Math.min(SIZE, saved.size()); i++) {
            String setting = saved.get(i);
            if (setting.startsWith("tag:")) {
                Identifier tag = Identifier.tryParse(setting.substring(4));
                if (tag != null) {
                    fuzzy[i] = FUZZY_TAG;
                    fuzzyTags[i] = TagKey.create(Registries.ITEM, tag);
                }
            } else if (setting.equals("damage:any")) {
                fuzzy[i] = FUZZY_ANY_DAMAGE;
            } else if (setting.startsWith("damage:")) {
                try {
                    fuzzy[i] = FUZZY_QUARTER + Math.clamp(Integer.parseInt(setting.substring(7)), 0, 3);
                } catch (NumberFormatException e) {
                    fuzzy[i] = FUZZY_NONE;
                }
            }
        }
    }

    public void save(ValueOutput output) {
        ValueOutput filter = output.child("filter");
        ContainerHelper.saveAllItems(filter, entries);
        filter.putBoolean("deny", deny);
        filter.putBoolean("tags", tags);
        filter.putBoolean("components", components);
        if (Arrays.stream(fuzzy).anyMatch(code -> code != FUZZY_NONE)) {
            List<String> saved = new ArrayList<>(SIZE);
            for (int i = 0; i < SIZE; i++) {
                saved.add(switch (fuzzy[i]) {
                    case FUZZY_NONE -> "";
                    case FUZZY_ANY_DAMAGE -> "damage:any";
                    case FUZZY_TAG -> fuzzyTags[i] != null ? "tag:" + fuzzyTags[i].location() : "";
                    default -> "damage:" + (fuzzy[i] - FUZZY_QUARTER);
                });
            }
            filter.store("fuzzy", Codec.STRING.listOf(), saved);
        }
    }
}
