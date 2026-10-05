/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// The Midrange line's shapes (data/encodedlogistics/shapes/midrange.json, made by tools/midrange_shapes.py from boxes
// that follow the models): for each block (and variant: "expansion_cabinet[attached=pos]"), in px with the model facing
// north, turned to the facing as the blockstates turn the models -
//   outline: the whole model, one shape over the footprint (its overhang too): what's selected, from any of its blocks;
//   collision: each footprint block's boxes, clipped to it.
// Built once, on first use.
public final class MidrangeShapes {
    private static final String PATH = "/data/encodedlogistics/shapes/midrange.json";
    private static final Map<String, VoxelShape> CACHE = new HashMap<>();
    private static JsonObject json;

    private MidrangeShapes() {}

    private static synchronized JsonObject json() {
        if (json == null) {
            try (Reader reader = new InputStreamReader(MidrangeShapes.class.getResourceAsStream(PATH), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                json = new JsonObject();
            }
        }
        return json;
    }

    // What one footprint block (offset: its place in the footprint, model-local) of a block (key) collides with.
    public static synchronized VoxelShape shape(String key, Vec3i offset, Direction facing) {
        String cacheKey = "c:" + key + "@" + offset.getX() + "," + offset.getY() + "," + offset.getZ() + "@" + facing;
        VoxelShape cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        JsonObject blocks = json().getAsJsonObject("collision").getAsJsonObject(key);
        JsonElement boxes = blocks != null ? blocks.get(offset.getX() + "," + offset.getY() + "," + offset.getZ()) : null;
        VoxelShape shape = boxes instanceof JsonArray list ? union(list, Vec3i.ZERO, facing) : Shapes.empty();
        CACHE.put(cacheKey, shape);
        return shape;
    }

    // The whole model's outline, as seen from one of its footprint blocks (offset from the master).
    public static synchronized VoxelShape outline(String key, Vec3i offset, Direction facing) {
        String cacheKey = "o:" + key + "@" + offset.getX() + "," + offset.getY() + "," + offset.getZ() + "@" + facing;
        VoxelShape cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        JsonElement boxes = json().getAsJsonObject("outline").get(key);
        VoxelShape shape = boxes instanceof JsonArray list ? union(list, offset, facing) : Shapes.empty();
        CACHE.put(cacheKey, shape);
        return shape;
    }

    private static VoxelShape union(JsonArray list, Vec3i offset, Direction facing) {
        VoxelShape shape = Shapes.empty();
        double ox = offset.getX() * 16, oy = offset.getY() * 16, oz = offset.getZ() * 16;
        for (JsonElement element : list) {
            JsonArray b = element.getAsJsonArray();
            shape = Shapes.joinUnoptimized(shape, turned(b.get(0).getAsDouble() - ox, b.get(1).getAsDouble() - oy, b.get(2).getAsDouble() - oz,
                    b.get(3).getAsDouble() - ox, b.get(4).getAsDouble() - oy, b.get(5).getAsDouble() - oz, facing), BooleanOp.OR);
        }
        return shape.optimize();
    }

    // A box modelled facing north (px), turned as y rotation turns the model (east 90, south 180, west 270); it may run
    // past its block.
    private static VoxelShape turned(double x1, double y1, double z1, double x2, double y2, double z2, Direction facing) {
        return switch (facing) {
            case EAST -> box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> box(x1, y1, z1, x2, y2, z2);
        };
    }

    private static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.create(new AABB(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16));
    }
}
