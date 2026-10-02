/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.model;

import java.util.List;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.math.Quadrant;
import com.mojang.serialization.MapCodec;

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
import net.zagdrath.encodedlogistics.block.ControllerState;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;

// The Network Controller's connected textures, AE2-style. Blockstates use it as
// {"type": "encodedlogistics:network_controller"}.
//
// A formed controller with formed neighbours on both ends of one axis and nowhere else is a column piece; every other
// controller (corners, line ends, cubes, singles, anything unformed) is a block. A block shows controller_block_<v> on
// every face: framed all round, so each corner reads as its own section. A column piece shows
// controller_column_<v|h>_<mask>_<v> on its side faces (v when the column runs up the face's texture, h when across):
// rails along the column, joined (mask bit set: 1 = up, 2 = right, 4 = down, 8 = left) toward the next column piece,
// and left open where the column meets a block so its rails run into the block's frame. Where a formed block touches
// another part of the structure, a controller_divider_<side> lays a darker steel over that side's frame edge so the
// join blends in rather than reading as a bright line. <v> is one of
// VARIANTS mazes picked from the block's position and face, so a structure never repeats; all variants cross a joined
// seam at the same pixel. Over each piece goes the state's overlay, full-bright and without AO: the hue cycle
// (<piece>_emissive) when online, the red <piece>_error_emissive on error, nothing when offline.
//
// Every face measures "right" and "down" from the same world ends so the traces line up round cube edges: sides run
// right along +X (north, south) or +Z (east, west) and down along -Y; the top and bottom run right along +X and down
// along +Z. Against vanilla's default UVs that mirrors north and east horizontally and the bottom vertically.
public final class ControllerModel {
    public static final Identifier ID = EncodedLogistics.id("network_controller");

    // Must match VARIANTS and COLUMN_MASKS in tools/ctrl.py.
    private static final int VARIANTS = 8;
    private static final int[] VERTICAL_MASKS = { 0, 1, 4, 5 }, HORIZONTAL_MASKS = { 0, 2, 8, 10 };

    // Overlays stand this far (in pixels) in front of the face, so they never z-fight with it or each other.
    private static final float DIVIDER_OFFSET = 0.01F, OVERLAY_OFFSET = 0.02F;
    // Divider textures by texture side: up, right, down, left.
    private static final String[] DIVIDER_SIDES = { "u", "r", "d", "l" };
    private static final ExtraFaceData EMISSIVE = new ExtraFaceData(0xFFFFFFFF, 15, false);

    private ControllerModel() {}

    public record Unbaked() implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(Resolver resolver) {}

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            return new Baked(baker);
        }
    }

    // Texture "right" and "down" of each face, by Direction ordinal (DOWN, UP, NORTH, SOUTH, WEST, EAST).
    private static final Direction[] RIGHT = { Direction.EAST, Direction.EAST, Direction.EAST, Direction.EAST, Direction.SOUTH, Direction.SOUTH };
    private static final Direction[] DOWN = { Direction.SOUTH, Direction.SOUTH, Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN };

    // A piece's base quad and its two overlays.
    private record Piece(BakedQuad base, BakedQuad online, BakedQuad error) {}

    static final class Baked implements DynamicBlockStateModel {
        private final Material.Baked particle;
        // [face][variant]
        private final Piece[][] blocks = new Piece[6][VARIANTS];
        // [face][mask][variant], for columns running up / across the face's texture; only their masks are filled.
        private final Piece[][][] vertical = new Piece[6][16][VARIANTS];
        private final Piece[][][] horizontal = new Piece[6][16][VARIANTS];
        // [face][side: up, right, down, left]
        private final BakedQuad[][] dividers = new BakedQuad[6][4];
        private final int materialFlags;

        Baked(ModelBaker baker) {
            this.particle = material(baker, "controller");
            int flags = 0;
            for (int v = 0; v < VARIANTS; v++) {
                for (Direction face : Direction.values()) {
                    Piece block = piece(baker, face, "controller_block_" + v);
                    blocks[face.ordinal()][v] = block;
                    flags |= flags(block);
                    for (int mask : VERTICAL_MASKS) {
                        Piece column = piece(baker, face, String.format("controller_column_v_%02d_%d", mask, v));
                        vertical[face.ordinal()][mask][v] = column;
                        flags |= flags(column);
                    }
                    for (int mask : HORIZONTAL_MASKS) {
                        Piece column = piece(baker, face, String.format("controller_column_h_%02d_%d", mask, v));
                        horizontal[face.ordinal()][mask][v] = column;
                        flags |= flags(column);
                    }
                }
            }
            for (Direction face : Direction.values()) {
                for (int side = 0; side < 4; side++) {
                    BakedQuad divider = bake(baker, face, DIVIDER_OFFSET, material(baker, "controller_divider_" + DIVIDER_SIDES[side]), null);
                    dividers[face.ordinal()][side] = divider;
                    flags |= divider.materialInfo().flags();
                }
            }
            this.materialFlags = flags;
        }

        private static Piece piece(ModelBaker baker, Direction face, String texture) {
            return new Piece(bake(baker, face, 0, material(baker, texture), null),
                    bake(baker, face, OVERLAY_OFFSET, material(baker, texture + "_emissive"), EMISSIVE),
                    bake(baker, face, OVERLAY_OFFSET, material(baker, texture + "_error_emissive"), EMISSIVE));
        }

        private static int flags(Piece piece) {
            return piece.base().materialInfo().flags() | piece.online().materialInfo().flags() | piece.error().materialInfo().flags();
        }

        private static Material.Baked material(ModelBaker baker, String texture) {
            Identifier id = EncodedLogistics.id("block/" + texture);
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

        // The axis this controller runs along as a column piece, or null when it's a block.
        static Direction.@Nullable Axis columnAxis(BlockAndTintGetter level, BlockPos pos) {
            if (!NetworkControllerBlock.isFormed(level.getBlockState(pos))) {
                return null;
            }
            Direction.Axis axis = null;
            int neighbours = 0;
            for (Direction side : Direction.values()) {
                if (NetworkControllerBlock.isFormed(level.getBlockState(pos.relative(side)))) {
                    if (axis != null && axis != side.getAxis()) {
                        return null;
                    }
                    axis = side.getAxis();
                    neighbours++;
                }
            }
            return neighbours == 2 ? axis : null;
        }

        // Which of the face's sides join the next column piece along the axis.
        private static int columnMask(BlockAndTintGetter level, BlockPos pos, Direction face, Direction.Axis axis) {
            Direction right = RIGHT[face.ordinal()];
            Direction down = DOWN[face.ordinal()];
            int mask = 0;
            for (Direction side : new Direction[] { down.getOpposite(), right, down, right.getOpposite() }) {
                if (side.getAxis() == axis && columnAxis(level, pos.relative(side)) == axis) {
                    mask |= side == down.getOpposite() ? 1 : side == right ? 2 : side == down ? 4 : 8;
                }
            }
            return mask;
        }

        // A stable choice of maze for one face of one block.
        private static int variant(BlockPos pos, Direction face) {
            long seed = pos.asLong() * 31 + face.ordinal();
            seed ^= seed >>> 33;
            seed *= 0xff51afd7ed558ccdL;
            seed ^= seed >>> 33;
            return (int) Math.floorMod(seed, (long) VARIANTS);
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            QuadCollection.Builder quads = new QuadCollection.Builder();
            ControllerState shown = state.getBlock() instanceof NetworkControllerBlock ? state.getValue(NetworkControllerBlock.STATE)
                    : ControllerState.OFFLINE;
            Direction.Axis axis = columnAxis(level, pos);
            boolean formed = NetworkControllerBlock.isFormed(state);
            for (Direction face : Direction.values()) {
                int f = face.ordinal();
                int v = variant(pos, face);
                Piece piece = blocks[f][v];
                if (axis != null && face.getAxis() != axis) {
                    Piece[][] columns = axis == DOWN[f].getAxis() ? vertical[f] : horizontal[f];
                    piece = columns[columnMask(level, pos, face, axis)][v];
                }
                quads.addCulledFace(face, piece.base());
                if (axis == null && formed) {
                    Direction right = RIGHT[f], down = DOWN[f];
                    Direction[] sides = { down.getOpposite(), right, down, right.getOpposite() };
                    for (int side = 0; side < 4; side++) {
                        if (NetworkControllerBlock.isFormed(level.getBlockState(pos.relative(sides[side])))) {
                            quads.addCulledFace(face, dividers[f][side]);
                        }
                    }
                }
                if (shown == ControllerState.ONLINE) {
                    quads.addCulledFace(face, piece.online());
                } else if (shown == ControllerState.ERROR) {
                    quads.addCulledFace(face, piece.error());
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
