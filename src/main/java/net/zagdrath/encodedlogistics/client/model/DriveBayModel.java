/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.model;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.math.Quadrant;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import net.neoforged.neoforge.client.model.standalone.UnbakedStandaloneModel;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// The Drive Bay's drives: its blockstate model (the frame, shelves and pockets) plus, for each drive in it (SLEDS from the
// block entity), the drive's sled in its bay and a 2x2 status light, with a full-bright copy of the light when it's on.
// Slot i is in column i / 5, row i % 5; the sled sits 0.75 px behind the rib fronts and shows its tier's face from
// drive_bay/drives.png (row 2 * tier), or for a fluid, pressurized or energy drive drives_<type>.png; the light is
// leds.png's 2x2 cell k (0 green, 1 yellow, 2 orange, 3 red, 4 off). An Energy Storage Drive's light is its charge
// instead: leds_energy.png's cell k at (3k, 0) (0 full .. 3 a quarter, 4 empty), or leds_energy_charging.png
// (animated) while it charges.
final class DriveBayModel implements DynamicBlockStateModel {
    private static final float[] COLUMN_X = { 1, 9 }, ROW_Y = { 1, 4, 7, 10, 13 };
    private static final ExtraFaceData EMISSIVE = new ExtraFaceData(0xFFFFFFFF, 15, false);
    private static final int LIGHTS = 5, LIT = 4, ENERGY_LIGHTS = 6;

    // The sleds and lights for every facing, baked once: [facing 2d index][slot][type][tier], [facing][slot][light],
    // glow [facing][slot][light], and an energy drive's [facing][slot][light] (LIGHT_CHARGING the charging cell) and glow.
    record Sleds(List<BakedQuad>[][][][] sleds, BakedQuad[][][] lights, BakedQuad[][][] glows, BakedQuad[][][] energyLights,
            BakedQuad[][][] energyGlows) {}

    // Bakes Sleds as a standalone model.
    static final class Unbaked implements UnbakedStandaloneModel<Sleds> {
        @Override
        public void resolveDependencies(Resolver resolver) {}

        @Override
        @SuppressWarnings("unchecked")
        public Sleds bake(ModelBaker baker, ModelDebugName name) {
            ResourceType[] types = ResourceType.values();
            Material.Baked[] drives = new Material.Baked[types.length];
            for (ResourceType type : types) {
                drives[type.ordinal()] = material(baker, type == ResourceType.ITEM ? "block/drive_bay/drives" : "block/drive_bay/drives_" + type.getSerializedName());
            }
            Material.Baked parts = material(baker, "block/drive_bay/parts");
            Material.Baked leds = material(baker, "block/drive_bay/leds");
            Material.Baked glow = material(baker, "block/drive_bay/leds_glow");
            Material.Baked energyLeds = material(baker, "block/drive_bay/leds_energy");
            Material.Baked energyGlow = material(baker, "block/drive_bay/leds_energy_glow");
            Material.Baked charging = material(baker, "block/drive_bay/leds_energy_charging");
            int tiers = StorageTier.values().length;
            List<BakedQuad>[][][][] sleds = new List[4][DriveBayBlockEntity.SLOTS][types.length][tiers];
            BakedQuad[][][] lights = new BakedQuad[4][DriveBayBlockEntity.SLOTS][LIGHTS];
            BakedQuad[][][] glows = new BakedQuad[4][DriveBayBlockEntity.SLOTS][LIT];
            BakedQuad[][][] energyLights = new BakedQuad[4][DriveBayBlockEntity.SLOTS][ENERGY_LIGHTS];
            BakedQuad[][][] energyGlows = new BakedQuad[4][DriveBayBlockEntity.SLOTS][LIT];
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                int f = facing.get2DDataValue();
                BlockModelRotation rotation = BlockModelRotation.get(Quadrant.fromXYZAngles(Quadrant.R0, yRotation(facing), Quadrant.R0));
                for (int slot = 0; slot < DriveBayBlockEntity.SLOTS; slot++) {
                    float x0 = COLUMN_X[slot / 5], y0 = ROW_Y[slot % 5];
                    float left = 16 - x0 - 6, right = 16 - x0, bottom = 16 - y0 - 2, top = 16 - y0;
                    Vector3f from = new Vector3f(left, bottom, 0.75F), to = new Vector3f(right, top, 2);
                    for (ResourceType type : types) {
                        for (int tier = 0; tier < tiers; tier++) {
                            List<BakedQuad> quads = new ArrayList<>(5);
                            quads.add(quad(baker, from, to, Direction.NORTH, new CuboidFace.UVs(0, 2 * tier, 6, 2 * tier + 2), drives[type.ordinal()],
                                    rotation, null));
                            for (Direction side : new Direction[] { Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST }) {
                                quads.add(quad(baker, from, to, side, new CuboidFace.UVs(8, 0, 10, 2), parts, rotation, null));
                            }
                            sleds[f][slot][type.ordinal()][tier] = List.copyOf(quads);
                        }
                    }
                    Vector3f lightFrom = new Vector3f(right - 2, bottom, 0.74F), lightTo = new Vector3f(right, top, 0.74F);
                    Vector3f glowFrom = new Vector3f(right - 2, bottom, 0.73F), glowTo = new Vector3f(right, top, 0.73F);
                    for (int light = 0; light < LIGHTS; light++) {
                        CuboidFace.UVs uvs = new CuboidFace.UVs(3 * light, 0, 3 * light + 2, 2);
                        energyLights[f][slot][light] = quad(baker, lightFrom, lightTo, Direction.NORTH, uvs, energyLeds, rotation, null);
                        if (light < LIT) {
                            energyGlows[f][slot][light] = quad(baker, glowFrom, glowTo, Direction.NORTH, uvs, energyGlow, rotation, EMISSIVE);
                        }
                    }
                    energyLights[f][slot][StorageDriveItem.LIGHT_CHARGING] = quad(baker, glowFrom, glowTo, Direction.NORTH,
                            new CuboidFace.UVs(0, 0, 2, 2), charging, rotation, EMISSIVE);
                    for (int light = 0; light < LIGHTS; light++) {
                        CuboidFace.UVs uvs = new CuboidFace.UVs(2 * light, 0, 2 * light + 2, 2);
                        lights[f][slot][light] = quad(baker, new Vector3f(right - 2, bottom, 0.74F), new Vector3f(right, top, 0.74F),
                                Direction.NORTH, uvs, leds, rotation, null);
                        if (light < LIT) {
                            glows[f][slot][light] = quad(baker, new Vector3f(right - 2, bottom, 0.73F), new Vector3f(right, top, 0.73F),
                                    Direction.NORTH, uvs, glow, rotation, EMISSIVE);
                        }
                    }
                }
            }
            return new Sleds(sleds, lights, glows, energyLights, energyGlows);
        }

        private static Quadrant yRotation(Direction facing) {
            return switch (facing) {
                case EAST -> Quadrant.R90;
                case SOUTH -> Quadrant.R180;
                case WEST -> Quadrant.R270;
                default -> Quadrant.R0;
            };
        }

        private static Material.Baked material(ModelBaker baker, String texture) {
            return baker.materials().get(new Material(EncodedLogistics.id(texture)), () -> "encodedlogistics:drive_bay sleds");
        }

        private static BakedQuad quad(ModelBaker baker, Vector3f from, Vector3f to, Direction face, CuboidFace.UVs uvs, Material.Baked material,
                BlockModelRotation rotation, @Nullable ExtraFaceData data) {
            CuboidFace cuboidFace = new CuboidFace(null, CuboidFace.NO_TINT, "", uvs, Quadrant.R0, data,
                    new org.apache.commons.lang3.mutable.MutableObject<>());
            return FaceBakery.bakeQuad(baker, from, to, cuboidFace, material, face, rotation, null, data == null,
                    data != null ? data.lightEmission() : 0);
        }
    }

    private final BlockStateModel original;
    private final Sleds sleds;

    DriveBayModel(BlockStateModel original, Sleds sleds) {
        this.original = original;
        this.sleds = sleds;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        original.collectParts(level, pos, state, random, parts);
        int[] codes = level.getModelData(pos).get(DriveBayBlockEntity.SLEDS);
        if (codes == null || !(state.getBlock() instanceof DriveBayBlock)) {
            return;
        }
        int f = state.getValue(DriveBayBlock.FACING).get2DDataValue();
        QuadCollection.Builder quads = new QuadCollection.Builder();
        boolean any = false;
        for (int slot = 0; slot < Math.min(codes.length, DriveBayBlockEntity.SLOTS); slot++) {
            if (codes[slot] < 0) {
                continue;
            }
            ResourceType type = StorageDriveItem.sledType(codes[slot]);
            int tier = StorageDriveItem.sledTier(codes[slot]), light = StorageDriveItem.sledLight(codes[slot]);
            for (BakedQuad quad : sleds.sleds()[f][slot][type.ordinal()][tier]) {
                quads.addUnculledFace(quad);
            }
            if (type == ResourceType.ENERGY) {
                light = Math.min(light, ENERGY_LIGHTS - 1);
                quads.addUnculledFace(sleds.energyLights()[f][slot][light]);
                if (light < LIT) {
                    quads.addUnculledFace(sleds.energyGlows()[f][slot][light]);
                }
            } else {
                light = Math.min(light, LIT);
                quads.addUnculledFace(sleds.lights()[f][slot][light]);
                if (light < LIT) {
                    quads.addUnculledFace(sleds.glows()[f][slot][light]);
                }
            }
            any = true;
        }
        if (any) {
            parts.add(new SimpleModelWrapper(quads.build(), true, original.particleMaterial(level, pos, state)));
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
    @SuppressWarnings("deprecation")
    @BakedQuad.MaterialFlags
    public int materialFlags() {
        return original.materialFlags();
    }
}
