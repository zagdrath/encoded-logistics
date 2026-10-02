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
// moving items, a tap attached, a sensor emitting - and the model for its look: a Point-to-Point Link's type and
// direction, a plane's connected-texture mask) and the facade panels (FacadeQuads). A part host has no body: just its
// part.
//
// A plane's mask (bits 1 up, 2 right, 4 down, 8 left, as seen from its front) has a bit set for each neighbour in its own
// plane with the same kind of plane on the same side; its texture drops the rim there so the two read as one.
final class CableModel implements DynamicBlockStateModel {
    // A plane's up and right as seen from its front, by the side it's on (Direction ordinal): the model rotations turn the
    // north-facing model's up and west (its right) these ways.
    private static final Direction[] PLANE_UP = { Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.UP, Direction.UP, Direction.UP };
    private static final Direction[] PLANE_RIGHT = { Direction.WEST, Direction.WEST, Direction.WEST, Direction.EAST, Direction.SOUTH,
            Direction.NORTH };

    private final BlockStateModel original;
    private final CableTier tier;
    private final CableParts.@Nullable Body body;
    private final Map<Direction, BlockStateModelPart> anchors;
    // [part][look][lit ? 1 : 0][Direction ordinal]
    private final BlockStateModelPart[][][][] parts;
    private final FacadeQuads facades;
    private final int materialFlags;

    CableModel(BlockStateModel original, CableTier tier, CableParts.@Nullable Body body, Map<Direction, BlockStateModelPart> anchors,
            BlockStateModelPart[][][][] parts, FacadeQuads facades) {
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
        Integer looksMask = level.getModelData(pos).get(CableBlockEntity.LOOKS);
        int looks = looksMask != null ? looksMask : 0;
        if (body == null || !(state.getBlock() instanceof NetworkCableBlock)) {
            for (Direction side : Direction.values()) {
                addPart(level, pos, attachments, lit, looks, side, parts);
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
                addPart(level, pos, attachments, lit, looks, side, parts);
            }
        }
        BlockStateModelPart panels = facades.build(level, pos, attachments, connections, tier.dense() ? 8 : 6,
                original.particleMaterial(level, pos, state));
        if (panels != null) {
            parts.add(panels);
        }
    }

    private void addPart(BlockAndTintGetter level, BlockPos pos, CableAttachments attachments, int lit, int looks, Direction side,
            List<BlockStateModelPart> out) {
        PartType part = attachments.part(side);
        if (part != null) {
            int look = part.isPlane() ? planeMask(level, pos, part, side) : (looks >> side.ordinal() * 4) & 0xF;
            BlockStateModelPart[][][] models = parts[part.ordinal()];
            out.add(models[Math.min(look, models.length - 1)][(lit >> side.ordinal()) & 1][side.ordinal()]);
        }
    }

    private static int planeMask(BlockAndTintGetter level, BlockPos pos, PartType plane, Direction side) {
        Direction up = PLANE_UP[side.ordinal()], right = PLANE_RIGHT[side.ordinal()];
        Direction[] around = { up, right, up.getOpposite(), right.getOpposite() };
        int mask = 0;
        for (int bit = 0; bit < 4; bit++) {
            CableAttachments neighbour = level.getModelData(pos.relative(around[bit])).get(CableBlockEntity.ATTACHMENTS);
            if (neighbour != null && neighbour.part(side) == plane) {
                mask |= 1 << bit;
            }
        }
        return mask;
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
