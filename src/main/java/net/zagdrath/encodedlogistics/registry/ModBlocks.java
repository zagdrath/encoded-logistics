/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EncodedLogistics.MODID);

    // TODO: the recipe (data/encodedlogistics/recipe/network_controller.json) is a placeholder until the mod's own
    // materials exist.
    public static final DeferredBlock<NetworkControllerBlock> NETWORK_CONTROLLER = BLOCKS.registerBlock("network_controller",
            NetworkControllerBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(NetworkControllerBlock::lightLevel));

    private ModBlocks() {}
}
