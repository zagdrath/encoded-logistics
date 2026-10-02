/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.item.NetworkCableItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EncodedLogistics.MODID);

    public static final DeferredItem<BlockItem> NETWORK_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.NETWORK_CONTROLLER);

    // In the same order as ModBlocks.allCables().
    private static final List<DeferredItem<NetworkCableItem>> CABLES = new ArrayList<>();

    static {
        for (DeferredBlock<NetworkCableBlock> block : ModBlocks.allCables()) {
            CABLES.add(ITEMS.registerItem(block.getId().getPath(), p -> new NetworkCableItem(block.get(), p), p -> p.useBlockDescriptionPrefix()));
        }
    }

    private ModItems() {}

    public static List<DeferredItem<NetworkCableItem>> allCables() {
        return CABLES;
    }
}
