/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import net.minecraft.world.item.Item;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// A Storage Die of one tier: the tooltip names its tier (the icon only shows its colour).
public class StorageTierItem extends Item {
    private final StorageTier tier;

    public StorageTierItem(Item.Properties properties, StorageTier tier) {
        super(properties);
        this.tier = tier;
    }

    public StorageTier getTier() {
        return tier;
    }
}
