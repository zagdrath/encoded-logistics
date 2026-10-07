/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.Transparency;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.block.cable.CableConnection;

// The facade panels on a cable: a 16x16x1 panel per facade, with the target block's look.
//
// Each face of a panel uses the sprites the target's own model has on the same side (every layer of it, so overlays
// like grass sides come along), with UVs taken from the block-space position by the vanilla face rules, so a wall of
// facades tiles like the real block. Tinted layers keep their tint, as FacadeTints indexes side * TINTS + layer. A blank
// facade uses the facade_blank texture.
//
// Several facades on one cable fit together without overlapping: up and down panels are full; north and south panels
// stop 1 px short of an up / down facade; east and west panels stop short of up / down and north / south facades. Edge
// faces that meet a neighbouring facade are left out, so six facades are an exact cube. A panel over a side the cable
// connects through has a hole for the cable (6x6 slim, 8x8 dense). Outer faces cull against solid neighbours.
final class FacadeQuads {
    // Tint layers per side a facade can carry (grass, leaves, ... use layer 0).
    static final int TINTS = 4;

    private record Key(Direction side, @Nullable BlockState target, int hole, int trims) {}

    private record Placed(@Nullable Direction cull, BakedQuad quad) {}

    private final Map<Direction, List<BakedQuad.MaterialInfo>> blank = new EnumMap<>(Direction.class);
    private final Map<Key, List<Placed>> cache = new ConcurrentHashMap<>();

    FacadeQuads(BlockStateModelPart blankPanel) {
        for (Direction face : Direction.values()) {
            blank.put(face, materials(List.of(blankPanel), face));
        }
    }

    // All the facade panels of a cable as one part, or null when it has none.
    @Nullable BlockStateModelPart build(BlockAndTintGetter level, BlockPos pos, CableAttachments attachments, CableConnection[] connections,
            int hole, Material.Baked particle) {
        QuadCollection.Builder quads = null;
        int trims = 0;
        for (Direction side : Direction.values()) {
            if (attachments.facade(side)) {
                trims |= 1 << side.ordinal();
            }
        }
        for (Direction side : Direction.values()) {
            if (!attachments.facade(side)) {
                continue;
            }
            BlockState target = attachments.get(side).target();
            Key key = new Key(side, target, connections[side.ordinal()].connected() ? hole : 0, trims);
            List<Placed> placed = cache.computeIfAbsent(key, k -> panel(level, pos, k));
            if (quads == null) {
                quads = new QuadCollection.Builder();
            }
            for (Placed quad : placed) {
                if (quad.cull() != null) {
                    quads.addCulledFace(quad.cull(), quad.quad());
                } else {
                    quads.addUnculledFace(quad.quad());
                }
            }
        }
        return quads == null ? null : new SimpleModelWrapper(quads.build(), true, particle);
    }

    // --- One panel ---

    private List<Placed> panel(BlockAndTintGetter level, BlockPos pos, Key key) {
        Direction side = key.side();
        Map<Direction, List<BakedQuad.MaterialInfo>> materials = key.target() == null ? blank : targetMaterials(level, pos, key.target());
        List<Placed> out = new ArrayList<>();
        for (float[] box : boxes(side, key.hole(), key.trims())) {
            for (Direction face : Direction.values()) {
                Direction cull;
                if (face.getAxis() == side.getAxis()) {
                    cull = face == side ? side : null;
                } else {
                    float plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box[3 + face.getAxis().ordinal()]
                            : box[face.getAxis().ordinal()];
                    if (plane == 1 || plane == 15) {
                        continue;       // against a neighbouring facade's panel
                    }
                    cull = plane == 0 || plane == 16 ? face : null;
                }
                for (BakedQuad.MaterialInfo material : materials.get(face)) {
                    out.add(new Placed(cull, quad(box, face, retinted(material, side))));
                }
            }
        }
        return List.copyOf(out);
    }

    // The panel's boxes in pixels {x1, y1, z1, x2, y2, z2}: the trimmed panel, cut round the hole when it has one.
    private static List<float[]> boxes(Direction side, int hole, int trims) {
        float[] panel = { 0, 0, 0, 16, 16, 16 };
        int normal = side.getAxis().ordinal();
        if (side.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            panel[3 + normal] = 1;
        } else {
            panel[normal] = 15;
        }
        // North / south give way to up / down; east / west give way to up / down and north / south.
        if (side.getAxis() != Direction.Axis.Y) {
            trim(panel, trims, Direction.DOWN);
            trim(panel, trims, Direction.UP);
        }
        if (side.getAxis() == Direction.Axis.X) {
            trim(panel, trims, Direction.NORTH);
            trim(panel, trims, Direction.SOUTH);
        }
        if (hole == 0) {
            return List.of(panel);
        }
        // The two axes across the panel: a, then b.
        int a = side.getAxis() == Direction.Axis.X ? 2 : 0;
        int b = side.getAxis() == Direction.Axis.Y ? 2 : 1;
        float lo = 8 - hole / 2F, hi = 8 + hole / 2F;
        List<float[]> boxes = new ArrayList<>(4);
        boxes.add(cut(panel, b, panel[b], lo, a, panel[a], panel[3 + a]));
        boxes.add(cut(panel, b, hi, panel[3 + b], a, panel[a], panel[3 + a]));
        boxes.add(cut(panel, b, lo, hi, a, panel[a], lo));
        boxes.add(cut(panel, b, lo, hi, a, hi, panel[3 + a]));
        return boxes;
    }

    private static void trim(float[] panel, int trims, Direction neighbour) {
        if ((trims & 1 << neighbour.ordinal()) != 0) {
            int axis = neighbour.getAxis().ordinal();
            if (neighbour.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
                panel[axis] = 1;
            } else {
                panel[3 + axis] = 15;
            }
        }
    }

    private static float[] cut(float[] panel, int b, float b1, float b2, int a, float a1, float a2) {
        float[] box = panel.clone();
        box[b] = b1;
        box[3 + b] = b2;
        box[a] = a1;
        box[3 + a] = a2;
        return box;
    }

    // --- Materials ---

    // The target's materials on each side: every distinct sprite / tint its model has there, the particle sprite where it
    // has none.
    private static Map<Direction, List<BakedQuad.MaterialInfo>> targetMaterials(BlockAndTintGetter level, BlockPos pos, BlockState target) {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(target);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(level, pos, target, RandomSource.create(42), parts);
        Map<Direction, List<BakedQuad.MaterialInfo>> materials = new EnumMap<>(Direction.class);
        BakedQuad.MaterialInfo fallback = null;
        for (Direction face : Direction.values()) {
            List<BakedQuad.MaterialInfo> found = materials(parts, face);
            materials.put(face, found);
            if (fallback == null && !found.isEmpty()) {
                fallback = found.getFirst();
            }
        }
        if (fallback == null) {
            fallback = BakedQuad.MaterialInfo.of(model.particleMaterial(level, pos, target), Transparency.NONE, -1, true, 0, true);
        }
        for (Direction face : Direction.values()) {
            if (materials.get(face).isEmpty()) {
                materials.put(face, List.of(fallback));
            }
        }
        return materials;
    }

    private static List<BakedQuad.MaterialInfo> materials(List<BlockStateModelPart> parts, Direction face) {
        Set<BakedQuad.MaterialInfo> found = new LinkedHashSet<>();
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(face)) {
                found.add(quad.materialInfo());
            }
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == face) {
                    found.add(quad.materialInfo());
                }
            }
        }
        return List.copyOf(found);
    }

    // The target's tint layer moved to this side's slots (FacadeTints); layers past TINTS go untinted.
    private static BakedQuad.MaterialInfo retinted(BakedQuad.MaterialInfo material, Direction side) {
        int tint = material.tintIndex();
        int moved = tint >= 0 && tint < TINTS ? side.ordinal() * TINTS + tint : -1;
        if (moved == tint) {
            return material;
        }
        return new BakedQuad.MaterialInfo(material.sprite(), material.layer(), material.itemRenderType(), moved, material.shade(),
                material.lightEmission(), material.ambientOcclusion());
    }

    // --- Quads ---

    // One face of a box, with world-aligned UVs (vanilla's default UVs for a face at that position).
    private static BakedQuad quad(float[] box, Direction face, BakedQuad.MaterialInfo material) {
        float x1 = box[0], y1 = box[1], z1 = box[2], x2 = box[3], y2 = box[4], z2 = box[5];
        // Corners in the vertex order vanilla bakes each face in (FaceInfo).
        float[][] corners = switch (face) {
            case DOWN -> new float[][] { { x1, y1, z2 }, { x1, y1, z1 }, { x2, y1, z1 }, { x2, y1, z2 } };
            case UP -> new float[][] { { x1, y2, z1 }, { x1, y2, z2 }, { x2, y2, z2 }, { x2, y2, z1 } };
            case NORTH -> new float[][] { { x2, y2, z1 }, { x2, y1, z1 }, { x1, y1, z1 }, { x1, y2, z1 } };
            case SOUTH -> new float[][] { { x1, y2, z2 }, { x1, y1, z2 }, { x2, y1, z2 }, { x2, y2, z2 } };
            case WEST -> new float[][] { { x1, y2, z1 }, { x1, y1, z1 }, { x1, y1, z2 }, { x1, y2, z2 } };
            case EAST -> new float[][] { { x2, y2, z2 }, { x2, y1, z2 }, { x2, y1, z1 }, { x2, y2, z1 } };
        };
        TextureAtlasSprite sprite = material.sprite();
        Vector3f[] positions = new Vector3f[4];
        long[] uvs = new long[4];
        for (int i = 0; i < 4; i++) {
            float x = corners[i][0], y = corners[i][1], z = corners[i][2];
            positions[i] = new Vector3f(x / 16, y / 16, z / 16);
            float u, v;
            switch (face) {
                case DOWN -> { u = x; v = 16 - z; }
                case UP -> { u = x; v = z; }
                case NORTH -> { u = 16 - x; v = 16 - y; }
                case SOUTH -> { u = x; v = 16 - y; }
                case WEST -> { u = z; v = 16 - y; }
                default -> { u = 16 - z; v = 16 - y; }
            }
            uvs[i] = UVPair.pack(sprite.getU(u / 16), sprite.getV(v / 16));
        }
        return new BakedQuad(positions[0], positions[1], positions[2], positions[3], uvs[0], uvs[1], uvs[2], uvs[3], face, material);
    }
}
