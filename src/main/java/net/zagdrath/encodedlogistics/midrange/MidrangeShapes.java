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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// The Midrange line's collision and selection shapes (data/encodedlogistics/shapes/midrange.json, from the models): for
// each block (and variant: "midrange_system[expansion=pos]"), each footprint block's boxes in its own local px with the
// model facing north, turned to the facing as the blockstates turn the models. Built once, on first use.
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

    // The shape of one footprint block (offset: its place in the footprint, model-local) of a block (key), facing a way.
    public static synchronized VoxelShape shape(String key, Vec3i offset, Direction facing) {
        String cacheKey = key + "@" + offset.getX() + "," + offset.getY() + "," + offset.getZ() + "@" + facing;
        VoxelShape cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        VoxelShape shape = Shapes.empty();
        JsonObject blocks = json().getAsJsonObject(key);
        JsonElement boxes = blocks != null ? blocks.get(offset.getX() + "," + offset.getY() + "," + offset.getZ()) : null;
        if (boxes instanceof JsonArray list) {
            for (JsonElement element : list) {
                JsonArray b = element.getAsJsonArray();
                shape = Shapes.or(shape, turned(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(), b.get(3).getAsDouble(),
                        b.get(4).getAsDouble(), b.get(5).getAsDouble(), facing));
            }
        }
        CACHE.put(cacheKey, shape);
        return shape;
    }

    // A box modelled facing north, turned as y rotation turns the model (east 90, south 180, west 270).
    private static VoxelShape turned(double x1, double y1, double z1, double x2, double y2, double z2, Direction facing) {
        return switch (facing) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }
}
