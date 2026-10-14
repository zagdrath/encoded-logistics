/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.chassis;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// A card, blade, power supply or module for a modular chassis (ChassisPart). Put in by hand (used on its bay through
// the rack's open door, or clicked onto its bay in the chassis's panel) or shift-clicked into the first bay that takes
// it. A part with settings (a Transponder Card's channel and client network, a Protection Card's revertive switch)
// keeps them on the item (settings), so they go with it.
public class ChassisPartItem extends Item {
    private final ChassisPart part;

    public ChassisPartItem(Item.Properties properties, ChassisPart part) {
        super(properties);
        this.part = part;
    }

    public ChassisPart part() {
        return part;
    }

    // A part's settings (empty without any).
    public static CompoundTag settings(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    // A copy of the part with these settings.
    public static ItemStack withSettings(ItemStack stack, CompoundTag settings) {
        ItemStack copy = stack.copy();
        if (settings.isEmpty()) {
            copy.remove(DataComponents.CUSTOM_DATA);
        } else {
            copy.set(DataComponents.CUSTOM_DATA, CustomData.of(settings));
        }
        return copy;
    }
}
