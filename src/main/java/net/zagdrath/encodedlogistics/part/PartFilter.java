/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// A port's or tap's filter: nine ghost entries (copies, no real items) and, with a Filter Module installed, three options:
// deny (everything but the entries), tag matching (an item matches an entry it shares a c: tag with) and exact
// components (an item must also have the entry's components). Without the module it's an allow-list by item.
public final class PartFilter {
    public static final int SIZE = 9;

    private final NonNullList<ItemStack> entries = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean deny, tags, components;

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

    // Whether an item passes. options: the Filter Module is installed (its options apply). emptyPasses: what an empty
    // filter means (everything for an Ingress Port or tap, nothing for an Egress Port).
    public boolean test(ItemStack stack, boolean options, boolean emptyPasses) {
        if (isEmpty()) {
            return emptyPasses;
        }
        boolean matched = false;
        for (ItemStack entry : entries) {
            if (!entry.isEmpty() && matches(entry, stack, options)) {
                matched = true;
                break;
            }
        }
        return options && deny ? !matched : matched;
    }

    // Whether an item matches one entry.
    public boolean matches(ItemStack entry, ItemStack stack, boolean options) {
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

    public void load(ValueInput input) {
        ValueInput filter = input.childOrEmpty("filter");
        for (int i = 0; i < SIZE; i++) {
            entries.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(filter, entries);
        deny = filter.getBooleanOr("deny", false);
        tags = filter.getBooleanOr("tags", false);
        components = filter.getBooleanOr("components", false);
    }

    public void save(ValueOutput output) {
        ValueOutput filter = output.child("filter");
        ContainerHelper.saveAllItems(filter, entries);
        filter.putBoolean("deny", deny);
        filter.putBoolean("tags", tags);
        filter.putBoolean("components", components);
    }
}
