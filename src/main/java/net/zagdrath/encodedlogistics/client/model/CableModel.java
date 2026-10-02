/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.model;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.block.cable.CableConnection;
import net.zagdrath.encodedlogistics.block.cable.CableShapes;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;

// A cable blockstate's model, wrapped. Without attachments it's the blockstate's own multipart model. With them (from
// the block entity's ModelData) it builds the cable from the same parts (CableParts) by the same rules, except that an
// anchor puts the cable on its junction cube and a facade over a block connection drops that arm's flange, then adds
// the anchors (rotated like arms) and the facade panels (FacadeQuads).
final class CableModel implements DynamicBlockStateModel {
    private final BlockStateModel original;
    private final CableTier tier;
    private final CableParts.Body body;
    private final Map<Direction, BlockStateModelPart> anchors;
    private final FacadeQuads facades;
    private final int materialFlags;

    CableModel(BlockStateModel original, CableTier tier, CableParts.Body body, Map<Direction, BlockStateModelPart> anchors, FacadeQuads facades) {
        this.original = original;
        this.tier = tier;
        this.body = body;
        this.anchors = anchors;
        this.facades = facades;
        @SuppressWarnings("deprecation")
        int flags = original.materialFlags();
        for (BlockStateModelPart anchor : anchors.values()) {
            flags |= anchor.materialFlags();
        }
        this.materialFlags = flags;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        CableAttachments attachments = level.getModelData(pos).get(CableBlockEntity.ATTACHMENTS);
        if (attachments == null || attachments.isEmpty() || !(state.getBlock() instanceof NetworkCableBlock)) {
            original.collectParts(level, pos, state, random, parts);
            return;
        }
        CableConnection[] connections = NetworkCableBlock.connections(state);
        Direction.Axis straight = CableShapes.straightAxis(connections, attachments);
        if (straight != null) {
            parts.add(body.cubeStraight()[straight.ordinal()]);
            for (Direction side : Direction.values()) {
                if (side.getAxis() == straight) {
                    parts.add(body.armStraight()[side.ordinal()]);
                }
            }
        } else {
            parts.add(body.cubeJunction());
            for (Direction side : Direction.values()) {
                switch (connections[side.ordinal()]) {
                    case CABLE -> parts.add(body.armJunction()[side.ordinal()]);
                    case BLOCK -> parts.add(attachments.facade(side) ? body.armJunction()[side.ordinal()] : body.armBlock()[side.ordinal()]);
                    case NONE -> {}
                }
            }
        }
        for (Direction side : Direction.values()) {
            if (attachments.anchored(side)) {
                parts.add(anchors.get(side));
            }
        }
        BlockStateModelPart panels = facades.build(level, pos, attachments, connections, tier.dense() ? 8 : 6,
                original.particleMaterial(level, pos, state));
        if (panels != null) {
            parts.add(panels);
        }
    }

    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return null;
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return original.particleMaterial(level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Material.Baked particleMaterial() {
        return original.particleMaterial();
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags() {
        return materialFlags;
    }
}
