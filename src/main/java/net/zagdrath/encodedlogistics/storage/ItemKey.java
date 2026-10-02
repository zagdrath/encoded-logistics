/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

// One kind of item as storage counts it: the item and its components, without a count. Two keys are equal when the
// stacks would stack together.
public final class ItemKey {
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemKey> STREAM_CODEC = ItemStack.STREAM_CODEC.map(ItemKey::of, ItemKey::stack);

    private final ItemStack stack;
    private final int hash;

    private ItemKey(ItemStack stack) {
        this.stack = stack;
        this.hash = ItemStack.hashItemAndComponents(stack);
    }

    public static ItemKey of(ItemStack stack) {
        return new ItemKey(stack.copyWithCount(1));
    }

    // A count-1 copy; don't change it.
    public ItemStack stack() {
        return stack;
    }

    public ItemStack toStack(int count) {
        return stack.copyWithCount(count);
    }

    public int maxStackSize() {
        return stack.getMaxStackSize();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemKey key && hash == key.hash && ItemStack.isSameItemSameComponents(stack, key.stack);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return stack.toString();
    }
}
