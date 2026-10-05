/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;

// A Diskette Magazine (HANDOFF 6): a holder for up to CAPACITY 8" Diskettes, which an Integrated Midrange System takes
// whole. Diskettes go in and out as with a bundle: a diskette clicked onto it goes in; right-clicking it (with an empty
// cursor, or onto an empty slot) takes the last one out.
public class DisketteMagazineItem extends Item {
    public static final int CAPACITY = 4;

    public DisketteMagazineItem(Item.Properties properties) {
        super(properties);
    }

    // The diskettes in it, first in first.
    public static List<ItemStack> diskettes(ItemStack magazine) {
        List<ItemStack> diskettes = new ArrayList<>();
        ItemContainerContents contents = magazine.get(ModDataComponents.MAGAZINE_CONTENTS.get());
        if (contents != null) {
            contents.nonEmptyItemCopyStream().forEach(diskettes::add);
        }
        return diskettes;
    }

    public static void setDiskettes(ItemStack magazine, List<ItemStack> diskettes) {
        if (diskettes.isEmpty()) {
            magazine.remove(ModDataComponents.MAGAZINE_CONTENTS.get());
        } else {
            magazine.set(ModDataComponents.MAGAZINE_CONTENTS.get(), ItemContainerContents.fromItems(diskettes));
        }
    }

    // A diskette into it; false when it isn't one or there's no room.
    public static boolean insert(ItemStack magazine, ItemStack diskette) {
        List<ItemStack> diskettes = diskettes(magazine);
        if (!diskette.is(ModItems.DISKETTE_8IN.get()) || diskettes.size() >= CAPACITY) {
            return false;
        }
        diskettes.add(diskette.split(1));
        setDiskettes(magazine, diskettes);
        return true;
    }

    // The last diskette out, or empty.
    public static ItemStack takeLast(ItemStack magazine) {
        List<ItemStack> diskettes = diskettes(magazine);
        if (diskettes.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack last = diskettes.removeLast();
        setDiskettes(magazine, diskettes);
        return last;
    }

    // The magazine held on the cursor, clicked onto a slot: a diskette there goes in; right-click on an empty slot puts
    // the last one there.
    @Override
    public boolean overrideStackedOnOther(ItemStack magazine, Slot slot, ClickAction action, Player player) {
        ItemStack there = slot.getItem();
        if (action == ClickAction.PRIMARY && there.is(ModItems.DISKETTE_8IN.get())) {
            if (insert(magazine, there)) {
                slot.setChanged();
                player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F);
            }
            return true;
        }
        if (action == ClickAction.SECONDARY && there.isEmpty()) {
            ItemStack out = takeLast(magazine);
            if (!out.isEmpty()) {
                slot.safeInsert(out);
                player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F);
            }
            return true;
        }
        return false;
    }

    // Something on the cursor clicked onto the magazine: a diskette goes in; right-click with nothing takes one out.
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack magazine, ItemStack carried, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action == ClickAction.PRIMARY && carried.is(ModItems.DISKETTE_8IN.get())) {
            if (insert(magazine, carried)) {
                player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F);
            }
            return true;
        }
        if (action == ClickAction.SECONDARY && carried.isEmpty()) {
            ItemStack out = takeLast(magazine);
            if (!out.isEmpty()) {
                access.set(out);
                player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F);
            }
            return true;
        }
        return false;
    }
}
