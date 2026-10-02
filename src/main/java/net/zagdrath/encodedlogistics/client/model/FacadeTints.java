/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.model;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// Facade colours: a facade of a tinted block (grass, leaves...) takes the target's own tint at the cable's position.
// FacadeQuads moves each target tint layer to side * TINTS + layer; these sources look the facade up and ask the
// target's tint source for that layer.
public final class FacadeTints {
    private FacadeTints() {}

    public static void register(RegisterColorHandlersEvent.BlockTintSources event) {
        BlockColors colors = event.getBlockColors();
        List<BlockTintSource> sources = new ArrayList<>(6 * FacadeQuads.TINTS);
        for (Direction side : Direction.values()) {
            for (int layer = 0; layer < FacadeQuads.TINTS; layer++) {
                sources.add(new Source(colors, side, layer));
            }
        }
        event.register(sources, ModBlocks.allCables().stream().map(cable -> (Block) cable.get()).toArray(Block[]::new));
    }

    private record Source(BlockColors colors, Direction side, int layer) implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            return -1;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            CableAttachments attachments = level.getModelData(pos).get(CableBlockEntity.ATTACHMENTS);
            BlockState target = attachments != null && attachments.facade(side) ? attachments.get(side).target() : null;
            BlockTintSource source = target != null ? colors.getTintSource(target, layer) : null;
            return source != null ? source.colorInWorld(target, level, pos) : -1;
        }
    }
}
