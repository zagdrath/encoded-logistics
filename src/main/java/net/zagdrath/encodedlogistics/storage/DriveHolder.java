/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

// A block holding Storage Drives that are its network's hot storage, a slot each (DriveView): a Drive Bay's ten, a Disk
// Drive's one.
public interface DriveHolder {
    int driveSlots();

    // The drive in a slot when it can be read now (it has its id), else null.
    @Nullable ItemStack drive(int slot);

    // The network put items in or took them out of the drive in a slot.
    void driveChanged(int slot);
}
