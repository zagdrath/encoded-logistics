/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// An Encoded Schematic (crafting or processing): a Schematic Card the Schematic Encoder wrote a schematic onto. Its
// tooltip lists what it makes and takes (ItemInfoTooltips); holding Shift shows its output as its icon.
public class SchematicItem extends Item {
    private final Schematic.Kind kind;

    public SchematicItem(Item.Properties properties, Schematic.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Schematic.Kind getKind() {
        return kind;
    }

    // The schematic an encoded card holds, or null.
    public static @Nullable Schematic schematic(ItemStack stack) {
        return stack.getItem() instanceof SchematicItem ? stack.get(ModDataComponents.SCHEMATIC.get()) : null;
    }
}
