/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.item.CableFacadeItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EncodedLogistics.MODID);

    public static final DeferredItem<BlockItem> NETWORK_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.NETWORK_CONTROLLER);
    public static final DeferredItem<BlockItem> POWER_INLET = ITEMS.registerSimpleBlockItem(ModBlocks.POWER_INLET);
    public static final DeferredItem<BlockItem> CAPACITOR_BANK = ITEMS.registerSimpleBlockItem(ModBlocks.CAPACITOR_BANK);
    public static final DeferredItem<BlockItem> SEGMENT_ISOLATOR = ITEMS.registerSimpleBlockItem(ModBlocks.SEGMENT_ISOLATOR);

    // Cable attachments: mounted on a cable's side (see NetworkCableBlock).
    public static final DeferredItem<Item> CABLE_ANCHOR = ITEMS.registerSimpleItem("cable_anchor");
    public static final DeferredItem<CableFacadeItem> CABLE_FACADE = ITEMS.registerItem("cable_facade", CableFacadeItem::new);

    // In the same order as ModBlocks.allCables().
    private static final List<DeferredItem<BlockItem>> CABLES = new ArrayList<>();

    static {
        for (DeferredBlock<NetworkCableBlock> block : ModBlocks.allCables()) {
            CABLES.add(ITEMS.registerSimpleBlockItem(block));
        }
    }

    private ModItems() {}

    public static List<DeferredItem<BlockItem>> allCables() {
        return CABLES;
    }
}
