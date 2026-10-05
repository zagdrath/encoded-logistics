/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.plc;

import net.minecraft.world.item.Item;

// A PLC's plug-in sensor module (docs/plc HANDOFF 2): used on a PLC, it goes in the first free slot.
public class PlcModuleItem extends Item {
    private final PlcModule module;

    public PlcModuleItem(PlcModule module, Item.Properties properties) {
        super(properties);
        this.module = module;
    }

    public PlcModule module() {
        return module;
    }
}
