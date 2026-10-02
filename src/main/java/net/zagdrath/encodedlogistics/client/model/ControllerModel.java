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

// The Network Controller's connected textures. Blockstates use it as {"type": "encodedlogistics:network_controller"}.
//
// Each face picks one of 16 pieces from a mask of its in-plane neighbours (1 = up, 2 = right, 4 = down, 8 = left; a bit
// is set when that neighbour is a controller of the same formed structure): controller.png for 0, controller_ctm_NN
// otherwise. An unformed block always uses controller.png. Over it goes the state's overlay, full-bright and without
// AO: the hue cycle (<piece>_emissive) when online, the red <piece>_error_emissive on error, nothing when offline.
//
// Every face measures "right" and "down" from the same world ends so the traces line up round cube edges: sides run
// right along +X (north, south) or +Z (east, west) and down along -Y; the top and bottom run right along +X and down
// along +Z. Against vanilla's default UVs that mirrors north and east horizontally and the bottom vertically.
public final class ControllerModel {
    public static final Identifier ID = EncodedLogistics.id("network_controller");

    // The overlay stands this far (in pixels) in front of the face it lights, so the two never z-fight.
    private static final float OVERLAY_OFFSET = 0.02F;
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

    static final class Baked implements DynamicBlockStateModel {
        private final Material.Baked particle;
        // [face][mask]
        private final BakedQuad[][] base = new BakedQuad[6][16];
        private final BakedQuad[][] online = new BakedQuad[6][16];
        private final BakedQuad[][] error = new BakedQuad[6][16];
        private final int materialFlags;

        Baked(ModelBaker baker) {
            this.particle = material(baker, "controller");
            int flags = 0;
            for (int mask = 0; mask < 16; mask++) {
                String piece = mask == 0 ? "controller" : String.format("controller_ctm_%02d", mask);
                Material.Baked baseTexture = material(baker, piece);
                Material.Baked onlineTexture = material(baker, piece + "_emissive");
                Material.Baked errorTexture = material(baker, piece + "_error_emissive");
                for (Direction face : Direction.values()) {
                    int f = face.ordinal();
                    base[f][mask] = bake(baker, face, 0, baseTexture, null);
                    online[f][mask] = bake(baker, face, OVERLAY_OFFSET, onlineTexture, EMISSIVE);
                    error[f][mask] = bake(baker, face, OVERLAY_OFFSET, errorTexture, EMISSIVE);
                    flags |= base[f][mask].materialInfo().flags() | online[f][mask].materialInfo().flags()
                            | error[f][mask].materialInfo().flags();
                }
            }
            this.materialFlags = flags;
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

        private static int mask(BlockAndTintGetter level, BlockPos pos, Direction face) {
            Direction right = RIGHT[face.ordinal()];
            Direction down = DOWN[face.ordinal()];
            int mask = 0;
            if (NetworkControllerBlock.isFormed(level.getBlockState(pos.relative(down.getOpposite())))) {
                mask |= 1;
            }
            if (NetworkControllerBlock.isFormed(level.getBlockState(pos.relative(right)))) {
                mask |= 2;
            }
            if (NetworkControllerBlock.isFormed(level.getBlockState(pos.relative(down)))) {
                mask |= 4;
            }
            if (NetworkControllerBlock.isFormed(level.getBlockState(pos.relative(right.getOpposite())))) {
                mask |= 8;
            }
            return mask;
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            QuadCollection.Builder quads = new QuadCollection.Builder();
            boolean formed = state.getBlock() instanceof NetworkControllerBlock && state.getValue(NetworkControllerBlock.FORMED);
            ControllerState shown = state.getBlock() instanceof NetworkControllerBlock ? state.getValue(NetworkControllerBlock.STATE)
                    : ControllerState.OFFLINE;
            for (Direction face : Direction.values()) {
                int f = face.ordinal();
                int mask = formed ? mask(level, pos, face) : 0;
                quads.addCulledFace(face, base[f][mask]);
                if (shown == ControllerState.ONLINE) {
                    quads.addCulledFace(face, online[f][mask]);
                } else if (shown == ControllerState.ERROR) {
                    quads.addCulledFace(face, error[f][mask]);
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
