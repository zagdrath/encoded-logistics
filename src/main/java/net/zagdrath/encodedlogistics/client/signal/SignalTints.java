/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.signal;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.signal.CageLightBlock;

// The Cage Lights' bulbs (tint 0 of their shared model): the block's dye colour over the bulb's steel whites, as the
// Alarm Strobes' lenses are coloured, so lamps and strobes match.
public final class SignalTints {
    private SignalTints() {}

    public static void register(RegisterColorHandlersEvent.BlockTintSources event) {
        BlockTintSource bulb = state -> state.getBlock() instanceof CageLightBlock light ? light.color().getTextureDiffuseColor() : -1;
        event.register(List.of(bulb), Stream.of(DyeColor.values()).map(color -> (Block) ModBlocks.cageLight(color).get()).toArray(Block[]::new));
    }
}
