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
import net.zagdrath.encodedlogistics.part.PartType;

// A cable blockstate's model, wrapped. Without attachments it's the blockstate's own multipart model. With them (from
// the block entity's ModelData) it builds the cable from the same parts (CableParts) by the same rules, except that an
// anchor or terminal puts the cable on its junction cube and a facade over a block connection drops that arm's flange,
// then adds the anchors and parts (rotated like arms; each part's lit model while it's lit - a terminal online, a port
// moving items, a tap attached, a sensor emitting) and the facade panels (FacadeQuads). A part host has no body: just
// its part.
final class CableModel implements DynamicBlockStateModel {
    private final BlockStateModel original;
    private final CableTier tier;
    private final CableParts.@Nullable Body body;
    private final Map<Direction, BlockStateModelPart> anchors;
    // [part][lit ? 1 : 0][Direction ordinal]
    private final BlockStateModelPart[][][] parts;
    private final FacadeQuads facades;
    private final int materialFlags;

    CableModel(BlockStateModel original, CableTier tier, CableParts.@Nullable Body body, Map<Direction, BlockStateModelPart> anchors,
            BlockStateModelPart[][][] parts, FacadeQuads facades) {
        this.original = original;
        this.tier = tier;
        this.body = body;
        this.anchors = anchors;
        this.parts = parts;
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
        if (attachments == null || attachments.isEmpty()) {
            original.collectParts(level, pos, state, random, parts);
            return;
        }
        Integer litMask = level.getModelData(pos).get(CableBlockEntity.LIT);
        int lit = litMask != null ? litMask : 0;
        if (body == null || !(state.getBlock() instanceof NetworkCableBlock)) {
            for (Direction side : Direction.values()) {
                addPart(attachments, lit, side, parts);
            }
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
            } else {
                addPart(attachments, lit, side, parts);
            }
        }
        BlockStateModelPart panels = facades.build(level, pos, attachments, connections, tier.dense() ? 8 : 6,
                original.particleMaterial(level, pos, state));
        if (panels != null) {
            parts.add(panels);
        }
    }

    private void addPart(CableAttachments attachments, int lit, Direction side, List<BlockStateModelPart> out) {
        PartType part = attachments.part(side);
        if (part != null) {
            out.add(parts[part.ordinal()][(lit >> side.ordinal()) & 1][side.ordinal()]);
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
