/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block.cable;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Cable collision and selection shapes, from the handoff's reference/cable_shapes.json: the union of the model parts a
// state uses (the same rules as the multipart blockstates), arms rotated from north like the blockstates rotate them.
// Shapes depend only on the tier and the six connections, so they're cached per tier for the 729 combinations.
public final class CableShapes {
    // Boxes in pixels {x1, y1, z1, x2, y2, z2}; arms point north (-Z) and the straight cube runs along Z.
    private record Parts(double[][] cubeStraight, double[][] cubeJunction, double[][] armStraight, double[][] armJunction, double[][] armBlock) {}

    private static final Parts NORMAL = new Parts(
            new double[][] { { 5, 5, 5, 11, 11, 11 } },
            new double[][] { { 5, 5, 5, 11, 11, 11 } },
            new double[][] { { 5, 5, 0, 11, 11, 4 }, { 6, 6, 4, 10, 10, 5 } },
            new double[][] { { 5, 5, 0, 11, 11, 4 }, { 6, 6, 4, 10, 10, 5 } },
            new double[][] { { 6, 6, 4, 10, 10, 5 }, { 5, 5, 1, 11, 11, 4 }, { 4, 4, 0, 12, 12, 1 } });
    private static final Parts DENSE = new Parts(
            new double[][] { { 4, 4, 5, 12, 12, 11 } },
            new double[][] { { 3, 3, 3, 13, 13, 13 } },
            new double[][] { { 4, 4, 0, 12, 12, 4 }, { 5, 5, 4, 11, 11, 5 } },
            new double[][] { { 4, 4, 0, 12, 12, 2 }, { 5, 5, 2, 11, 11, 3 } },
            new double[][] { { 5, 5, 2, 11, 11, 3 }, { 2, 2, 0, 14, 14, 2 } });

    private static final Map<CableTier, VoxelShape[]> CACHE = new EnumMap<>(CableTier.class);

    static {
        for (CableTier tier : CableTier.values()) {
            CACHE.put(tier, new VoxelShape[729]);
        }
    }

    private CableShapes() {}

    // connections by Direction ordinal (DOWN, UP, NORTH, SOUTH, WEST, EAST).
    public static VoxelShape get(CableTier tier, CableConnection[] connections) {
        int key = 0;
        for (CableConnection connection : connections) {
            key = key * 3 + connection.ordinal();
        }
        VoxelShape[] cache = CACHE.get(tier);
        VoxelShape shape = cache[key];
        if (shape == null) {
            shape = build(tier == CableTier.DENSE ? DENSE : NORMAL, connections);
            cache[key] = shape;
        }
        return shape;
    }

    // The axis of a straight piece: the only connections are cables on two opposite sides. Otherwise null.
    public static Direction.@Nullable Axis straightAxis(CableConnection[] connections) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            boolean straight = true;
            for (Direction side : Direction.values()) {
                CableConnection connection = connections[side.ordinal()];
                straight &= side.getAxis() == axis ? connection == CableConnection.CABLE : connection == CableConnection.NONE;
            }
            if (straight) {
                return axis;
            }
        }
        return null;
    }

    private static VoxelShape build(Parts parts, CableConnection[] connections) {
        Direction.Axis straight = straightAxis(connections);
        VoxelShape shape;
        if (straight != null) {
            Direction along = switch (straight) {
                case X -> Direction.EAST;
                case Y -> Direction.UP;
                case Z -> Direction.NORTH;
            };
            shape = boxes(parts.cubeStraight(), along);
            for (Direction side : Direction.values()) {
                if (side.getAxis() == straight) {
                    shape = Shapes.or(shape, boxes(parts.armStraight(), side));
                }
            }
        } else {
            shape = boxes(parts.cubeJunction(), Direction.NORTH);
            for (Direction side : Direction.values()) {
                shape = switch (connections[side.ordinal()]) {
                    case CABLE -> Shapes.or(shape, boxes(parts.armJunction(), side));
                    case BLOCK -> Shapes.or(shape, boxes(parts.armBlock(), side));
                    case NONE -> shape;
                };
            }
        }
        return shape.optimize();
    }

    private static VoxelShape boxes(double[][] boxes, Direction toward) {
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes) {
            shape = Shapes.or(shape, rotated(box, toward));
        }
        return shape;
    }

    // A box modelled pointing north, turned to point toward a side (as the blockstates' y / x rotations do).
    private static VoxelShape rotated(double[] b, Direction toward) {
        double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
        return switch (toward) {
            case NORTH -> Block.box(x1, y1, z1, x2, y2, z2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case UP -> Block.box(x1, 16 - z2, y1, x2, 16 - z1, y2);
            case DOWN -> Block.box(x1, z1, 16 - y2, x2, z2, 16 - y1);
        };
    }
}
