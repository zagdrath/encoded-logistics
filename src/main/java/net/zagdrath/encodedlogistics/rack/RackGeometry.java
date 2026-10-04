/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Where things are in a Server Rack. The rack is 1 wide, 3 tall and 2 deep; its master is the middle block of the front
// column, and everything is measured in the master's local model pixels with the rack facing north: front at z = 0,
// back at z = 32, floor at y = -16, roof at y = 32. A facing turns that space about the master's centre, as the
// blockstate's y rotation turns the frame model.
//
// The six blocks, by index: (dy + 1) + 3 * (back ? 1 : 0), dy being -1..1 from the master.
//
// Vertical layout: casters y -16..-15, plinth -15..-13, the 42U space -13..29 (U1 = y -13..-12, one pixel per U), roof
// 29..32.
public final class RackGeometry {
    public static final int UNITS = 42;
    public static final int PARTS = 6;
    public static final int BOTTOM_FRONT = 0, MASTER = 1, TOP_FRONT = 2, BOTTOM_BACK = 3, MIDDLE_BACK = 4, TOP_BACK = 5;

    // A device's box: the width between the rails and the depth between the doors.
    public static final float DEVICE_X0 = 1.5F, DEVICE_X1 = 14.5F, DEVICE_Z0 = 1.75F, DEVICE_Z1 = 29.25F;
    // A hit this close to the front counts as the front; this far back as the rear.
    public static final float FRONT_DEPTH = 2.0F, REAR_FROM = 29.5F;

    private RackGeometry() {}

    // --- The six blocks ---

    public static int dy(int index) {
        return index % 3 - 1;
    }

    public static boolean isBack(int index) {
        return index >= 3;
    }

    public static boolean isTop(int index) {
        return dy(index) == 1;
    }

    public static int index(int dy, boolean back) {
        return dy + 1 + (back ? 3 : 0);
    }

    public static BlockPos partPos(BlockPos master, Direction facing, int index) {
        BlockPos pos = master.above(dy(index));
        return isBack(index) ? pos.relative(facing.getOpposite()) : pos;
    }

    public static BlockPos masterPos(BlockPos part, Direction facing, int index) {
        BlockPos pos = part.below(dy(index));
        return isBack(index) ? pos.relative(facing) : pos;
    }

    // The sides of a part that face another part of the same rack.
    public static Direction[] internalSides(int index, Direction facing) {
        int dy = dy(index);
        Direction across = isBack(index) ? facing : facing.getOpposite();
        if (dy == -1) {
            return new Direction[] { Direction.UP, across };
        }
        if (dy == 1) {
            return new Direction[] { Direction.DOWN, across };
        }
        return new Direction[] { Direction.UP, Direction.DOWN, across };
    }

    // Whether a cable on that side of a part joins the rack: the rear face of the back blocks, the top of the top ones
    // and the bottom of the bottom ones (through the roof's and the plinth's grommets).
    public static boolean connectsOn(int index, Direction facing, Direction side) {
        return isBack(index) && side == facing.getOpposite() || isTop(index) && side == Direction.UP || isBottom(index) && side == Direction.DOWN;
    }

    public static boolean isBottom(int index) {
        return dy(index) == -1;
    }

    // The rack's connection points, in the order its controllers' uplink chips show them: the four grommets, then the
    // rear faces from the top.
    public enum Point {
        TOP_FRONT(RackGeometry.TOP_FRONT, Direction.UP), TOP_REAR(TOP_BACK, Direction.UP), BOTTOM_FRONT(RackGeometry.BOTTOM_FRONT, Direction.DOWN),
        BOTTOM_REAR(BOTTOM_BACK, Direction.DOWN), REAR_TOP(TOP_BACK, null), REAR_MIDDLE(MIDDLE_BACK, null), REAR_BOTTOM(BOTTOM_BACK, null);

        private final int index;
        private final @Nullable Direction vertical;

        Point(int index, @Nullable Direction vertical) {
            this.index = index;
            this.vertical = vertical;
        }

        public int index() {
            return index;
        }

        public Direction side(Direction facing) {
            return vertical != null ? vertical : facing.getOpposite();
        }

        // The block a cable at this point sits in.
        public BlockPos outside(BlockPos master, Direction facing) {
            return partPos(master, facing, index).relative(side(facing));
        }

        public static @Nullable Point at(int index, Direction facing, Direction side) {
            for (Point point : values()) {
                if (point.index == index && point.side(facing) == side) {
                    return point;
                }
            }
            return null;
        }

        public String key() {
            return "gui.encodedlogistics.rack.point." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    // --- Units ---

    // The bottom of U u, in local pixels.
    public static float unitBottom(int u) {
        return -13 + (u - 1);
    }

    // The U at a local height, or 0 outside the 42U space.
    public static int unitAt(double yPx) {
        int u = Mth.floor(yPx + 13) + 1;
        return u >= 1 && u <= UNITS ? u : 0;
    }

    // --- Local space ---

    // Quarter turns clockwise (seen from above) from north.
    public static int turns(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    // Turns a point (in pixels, about the block's centre) clockwise k times.
    private static double[] turn(double x, double z, int k) {
        for (int i = 0; i < (k & 3); i++) {
            double nx = 16 - z, nz = x;
            x = nx;
            z = nz;
        }
        return new double[] { x, z };
    }

    // A point in the world to the master's local pixels.
    public static Vec3 toLocal(Vec3 world, BlockPos master, Direction facing) {
        double x = (world.x - master.getX()) * 16, y = (world.y - master.getY()) * 16, z = (world.z - master.getZ()) * 16;
        double[] turned = turn(x, z, 4 - turns(facing));
        return new Vec3(turned[0], y, turned[1]);
    }

    // A box in the master's local pixels to the world.
    public static AABB toWorld(AABB local, BlockPos master, Direction facing) {
        int k = turns(facing);
        double[] a = turn(local.minX, local.minZ, k), b = turn(local.maxX, local.maxZ, k);
        return new AABB(Math.min(a[0], b[0]) / 16 + master.getX(), local.minY / 16 + master.getY(), Math.min(a[1], b[1]) / 16 + master.getZ(),
                Math.max(a[0], b[0]) / 16 + master.getX(), local.maxY / 16 + master.getY(), Math.max(a[1], b[1]) / 16 + master.getZ());
    }

    // A device's box from U u for size units, in local pixels.
    public static AABB deviceBox(int u, int size) {
        return new AABB(DEVICE_X0, unitBottom(u), DEVICE_Z0, DEVICE_X1, unitBottom(u) + size, DEVICE_Z1);
    }

    // The whole rack in the world.
    public static AABB bounds(BlockPos master, Direction facing) {
        return toWorld(new AABB(0, -16, 0, 16, 32, 32), master, facing);
    }

    // --- What a hit points at ---

    public enum Face {
        FRONT, REAR, OTHER
    }

    // Which face of the rack a local hit point is on.
    public static Face face(Vec3 local) {
        if (local.z <= FRONT_DEPTH) {
            return Face.FRONT;
        }
        return local.z >= REAR_FROM ? Face.REAR : Face.OTHER;
    }

    public static Face face(Direction hitSide, Direction facing) {
        return hitSide == facing ? Face.FRONT : hitSide == facing.getOpposite() ? Face.REAR : Face.OTHER;
    }

    // Smoothstep, for the doors.
    public static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3 - 2 * t);
    }
}
