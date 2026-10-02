/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.model;

import java.util.List;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.math.Quadrant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.SchedulerBlock;
import net.zagdrath.encodedlogistics.block.ThreadUnitBlock;

// The Scheduler blocks' connected textures. Blockstates use it as {"type": "encodedlogistics:scheduler", "block": <id>}.
//
// Each face shows block/scheduler/<id>_ctm_<mask>, mask bits 1 = up, 2 = right, 4 = down, 8 = left: a set bit drops the
// frame on that side because the neighbour there is a formed scheduler block (touching scheduler blocks are always one
// structure). Unformed blocks are framed all round (mask 0). Over that, full-bright and without AO: the Core's status
// display (core_display when formed, the red core_display_unformed when not), and a Thread Unit's glow while active.
// Faces measure "right" and "down" the way the Network Controller's do (ControllerModel): against vanilla's default UVs
// north and east are mirrored horizontally and the bottom vertically.
public final class SchedulerModel {
    public static final Identifier ID = EncodedLogistics.id("scheduler");

    // Overlays stand this far (in pixels) in front of the face.
    private static final float OVERLAY_OFFSET = 0.02F;
    private static final ExtraFaceData EMISSIVE = new ExtraFaceData(0xFFFFFFFF, 15, false);
    // Texture "right" and "down" of each face, by Direction ordinal (DOWN, UP, NORTH, SOUTH, WEST, EAST).
    private static final Direction[] RIGHT = { Direction.EAST, Direction.EAST, Direction.EAST, Direction.EAST, Direction.SOUTH, Direction.SOUTH };
    private static final Direction[] DOWN = { Direction.SOUTH, Direction.SOUTH, Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN };

    private SchedulerModel() {}

    public record Unbaked(String block) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("block").forGetter(Unbaked::block))
                .apply(i, Unbaked::new));

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(Resolver resolver) {}

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            return new Baked(baker, block);
        }
    }

    static final class Baked implements DynamicBlockStateModel {
        private final Material.Baked particle;
        // [face][mask]
        private final BakedQuad[][] frames = new BakedQuad[6][16];
        // [face], or null when the block has none.
        private final BakedQuad @Nullable [] formedOverlay, unformedOverlay;
        private final int materialFlags;

        Baked(ModelBaker baker, String block) {
            this.particle = material(baker, block + "_ctm_00");
            int flags = 0;
            for (Direction face : Direction.values()) {
                for (int mask = 0; mask < 16; mask++) {
                    BakedQuad quad = bake(baker, face, 0, material(baker, String.format("%s_ctm_%02d", block, mask)), null);
                    frames[face.ordinal()][mask] = quad;
                    flags |= quad.materialInfo().flags();
                }
            }
            String formed = switch (block) {
                case "scheduler_core" -> "core_display";
                case "thread_unit" -> "thread_unit_glow";
                default -> null;
            };
            formedOverlay = formed != null ? overlay(baker, formed) : null;
            unformedOverlay = block.equals("scheduler_core") ? overlay(baker, "core_display_unformed") : null;
            for (BakedQuad[] overlay : new BakedQuad[][] { formedOverlay, unformedOverlay }) {
                if (overlay != null) {
                    for (BakedQuad quad : overlay) {
                        flags |= quad.materialInfo().flags();
                    }
                }
            }
            this.materialFlags = flags;
        }

        private static BakedQuad[] overlay(ModelBaker baker, String texture) {
            BakedQuad[] quads = new BakedQuad[6];
            for (Direction face : Direction.values()) {
                quads[face.ordinal()] = bake(baker, face, OVERLAY_OFFSET, material(baker, texture), EMISSIVE);
            }
            return quads;
        }

        private static Material.Baked material(ModelBaker baker, String texture) {
            Identifier id = EncodedLogistics.id("block/scheduler/" + texture);
            return baker.materials().get(new Material(id), ID::toString);
        }

        // A full face, offset outward, with the UVs mirrored to the shared convention.
        private static BakedQuad bake(ModelBaker baker, Direction face, float offset, Material.Baked texture, @Nullable ExtraFaceData data) {
            Vector3f from = new Vector3f();
            Vector3f to = new Vector3f();
            switch (face) {
                case DOWN -> { from.set(0, -offset, 0); to.set(16, -offset, 16); }
                case UP -> { from.set(0, 16 + offset, 0); to.set(16, 16 + offset, 16); }
                case NORTH -> { from.set(0, 0, -offset); to.set(16, 16, -offset); }
                case SOUTH -> { from.set(0, 0, 16 + offset); to.set(16, 16, 16 + offset); }
                case WEST -> { from.set(-offset, 0, 0); to.set(-offset, 16, 16); }
                case EAST -> { from.set(16 + offset, 0, 0); to.set(16 + offset, 16, 16); }
            }
            CuboidFace.UVs uvs = switch (face) {
                case NORTH, EAST -> new CuboidFace.UVs(16, 0, 0, 16);
                case DOWN -> new CuboidFace.UVs(0, 16, 16, 0);
                default -> new CuboidFace.UVs(0, 0, 16, 16);
            };
            CuboidFace cuboidFace = new CuboidFace(null, CuboidFace.NO_TINT, "", uvs, Quadrant.R0, data,
                    new org.apache.commons.lang3.mutable.MutableObject<>());
            return FaceBakery.bakeQuad(baker, from, to, cuboidFace, texture, face, BlockModelRotation.IDENTITY, null, null,
                    data != null ? data.lightEmission() : 0);
        }

        // Which sides of the face join a formed neighbour.
        private static int mask(BlockAndTintGetter level, BlockPos pos, Direction face) {
            Direction right = RIGHT[face.ordinal()], down = DOWN[face.ordinal()];
            Direction[] sides = { down.getOpposite(), right, down, right.getOpposite() };
            int mask = 0;
            for (int side = 0; side < 4; side++) {
                if (SchedulerBlock.isFormed(level.getBlockState(pos.relative(sides[side])))) {
                    mask |= 1 << side;
                }
            }
            return mask;
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            QuadCollection.Builder quads = new QuadCollection.Builder();
            boolean formed = SchedulerBlock.isFormed(state);
            BakedQuad[] overlay = formed ? (state.getBlock() instanceof ThreadUnitBlock && !state.getValue(ThreadUnitBlock.ACTIVE) ? null : formedOverlay)
                    : unformedOverlay;
            for (Direction face : Direction.values()) {
                quads.addCulledFace(face, frames[face.ordinal()][formed ? mask(level, pos, face) : 0]);
                if (overlay != null) {
                    quads.addCulledFace(face, overlay[face.ordinal()]);
                }
            }
            parts.add(new SimpleModelWrapper(quads.build(), true, particle));
        }

        @Override
        public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return null;
        }

        @Override
        public Material.Baked particleMaterial() {
            return particle;
        }

        @Override
        @BakedQuad.MaterialFlags
        public int materialFlags() {
            return materialFlags;
        }
    }
}
