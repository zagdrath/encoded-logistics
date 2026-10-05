/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.wireless;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// The wireless blocks' outlines (shapes/collision_shapes.json in the handoff), turned as their blockstates turn their
// models: the Access Point (a disc mounted on what's below it) is modelled facing up (x 90 / 180 for the sides and down), the Wireless Ports facing north.
public final class WirelessShapes {
    public static final Map<Direction, VoxelShape> ACCESS_POINT = new EnumMap<>(Direction.class), PORT = new EnumMap<>(Direction.class);
    public static final VoxelShape BRIDGE = Shapes.or(Block.box(0, 0, 0, 16, 10, 16), Block.box(7, 10, 7, 9, 15, 9), Block.box(6.5, 15, 6.5, 9.5, 16, 9.5));

    // The disc (12 across, 2 thick) on its plate against the block it's mounted on.
    private static final double[][] AP_UP = { { 5, 0, 5, 11, 1, 11 }, { 2, 1, 5, 14, 3, 11 }, { 3, 1, 4, 13, 3, 12 }, { 4, 1, 3, 12, 3, 13 },
            { 5, 1, 2, 11, 3, 14 } };
    private static final double[][] PORT_NORTH = { { 3, 3, 0, 13, 13, 4 } };

    static {
        for (Direction facing : Direction.values()) {
            VoxelShape ap = Shapes.empty(), port = Shapes.empty();
            for (double[] box : AP_UP) {
                ap = Shapes.or(ap, fromUp(box, facing));
            }
            for (double[] box : PORT_NORTH) {
                port = Shapes.or(port, fromNorth(box, facing));
            }
            ACCESS_POINT.put(facing, ap);
            PORT.put(facing, port);
        }
    }

    private WirelessShapes() {}

    // A box modelled facing up, as x 90 (then y) turns it to face a side, or x 180 down.
    private static VoxelShape fromUp(double[] b, Direction facing) {
        double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
        return switch (facing) {
            case UP -> Block.box(x1, y1, z1, x2, y2, z2);
            case DOWN -> Block.box(x1, 16 - y2, 16 - z2, x2, 16 - y1, 16 - z1);
            default -> fromNorth(new double[] { x1, z1, 16 - y2, x2, z2, 16 - y1 }, facing);
        };
    }

    // A box modelled facing north, turned to face a side.
    private static VoxelShape fromNorth(double[] b, Direction facing) {
        double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
        return switch (facing) {
            case NORTH -> Block.box(x1, y1, z1, x2, y2, z2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case UP -> Block.box(x1, 16 - z2, y1, x2, 16 - z1, y2);
            case DOWN -> Block.box(x1, z1, 16 - y2, x2, z2, 16 - y1);
        };
    }
}
