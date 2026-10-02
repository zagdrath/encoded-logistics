/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// A client-side stand-in for a menu slot, drawn dy pixels lower: everything else is the original slot's. A terminal
// whose grid changes height while open swaps its client menu's slots for these (slot positions are final, and the
// server never looks at them), so the inventory and any section follow the grid.
final class MovedSlot extends Slot {
    final Slot base;

    private MovedSlot(Slot base, int dy) {
        super(base.container, base.getContainerSlot(), base.x, base.y + dy);
        this.base = base;
        this.index = base.index;
    }

    // The original slot moved by dy (the original itself for 0), whether or not it has been moved before.
    static Slot of(Slot slot, int dy) {
        Slot base = slot instanceof MovedSlot moved ? moved.base : slot;
        return dy == 0 ? base : new MovedSlot(base, dy);
    }

    @Override
    public void onQuickCraft(ItemStack picked, ItemStack original) {
        base.onQuickCraft(picked, original);
    }

    @Override
    public void onTake(Player player, ItemStack carried) {
        base.onTake(player, carried);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return base.mayPlace(stack);
    }

    @Override
    public ItemStack getItem() {
        return base.getItem();
    }

    @Override
    public boolean hasItem() {
        return base.hasItem();
    }

    @Override
    public void setByPlayer(ItemStack stack) {
        base.setByPlayer(stack);
    }

    @Override
    public void setByPlayer(ItemStack stack, ItemStack previous) {
        base.setByPlayer(stack, previous);
    }

    @Override
    public void set(ItemStack stack) {
        base.set(stack);
    }

    @Override
    public void setChanged() {
        base.setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return base.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return base.getMaxStackSize(stack);
    }

    @Override
    public @Nullable Identifier getNoItemIcon() {
        return base.getNoItemIcon();
    }

    @Override
    public ItemStack remove(int amount) {
        return base.remove(amount);
    }

    @Override
    public boolean mayPickup(Player player) {
        return base.mayPickup(player);
    }

    @Override
    public boolean isActive() {
        return base.isActive();
    }

    @Override
    public Optional<ItemStack> tryRemove(int amount, int maxAmount, Player player) {
        return base.tryRemove(amount, maxAmount, player);
    }

    @Override
    public ItemStack safeTake(int amount, int maxAmount, Player player) {
        return base.safeTake(amount, maxAmount, player);
    }

    @Override
    public ItemStack safeInsert(ItemStack stack, int amount) {
        return base.safeInsert(stack, amount);
    }

    @Override
    public boolean allowModification(Player player) {
        return base.allowModification(player);
    }

    @Override
    public boolean isHighlightable() {
        return base.isHighlightable();
    }

    @Override
    public boolean isFake() {
        return base.isFake();
    }
}
