/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block.cable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.part.PartType;

// Cable collision and selection shapes: the union of the model parts a cable shows (the same rules as the multipart
// blockstates), plus its anchors, parts and facade panels, arms rotated from north like the blockstates rotate
// them. Shapes depend only on the geometry (slim or dense), the six connections and the six attachment kinds, so
// they're cached.
public final class CableShapes {
    // Boxes in pixels {x1, y1, z1, x2, y2, z2}; arms point north (-Z) and the straight cube runs along Z.
    private record Parts(double[][] cubeStraight, double[][] cubeJunction, double[][] armStraight, double[][] armJunction,
            double[][] armBlock, double[][] anchor) {}

    // From tools/export_cables.py (it prints the cable parts) and the handoff's reference shapes (anchors). A straight
    // run is one continuous tube; a junction arm runs straight into the junction cube; a block arm ends in a flange.
    // Network and Fiber Cable share the slim geometry.
    private static final Parts SLIM = new Parts(
            new double[][] { { 5, 5, 5, 11, 11, 11 } },
            new double[][] { { 5, 5, 5, 11, 11, 11 } },
            new double[][] { { 5, 5, 0, 11, 11, 5 } },
            new double[][] { { 5, 5, 0, 11, 11, 5 } },
            new double[][] { { 4, 4, 0, 12, 12, 1 }, { 5, 5, 1, 11, 11, 5 } },
            new double[][] { { 6, 6, 3, 10, 10, 5 }, { 4, 4, 2, 12, 12, 3 }, { 5, 5, 1.5, 6, 6, 2 }, { 10, 5, 1.5, 11, 6, 2 },
                    { 5, 10, 1.5, 6, 11, 2 }, { 10, 10, 1.5, 11, 11, 2 } });
    private static final Parts DENSE = new Parts(
            new double[][] { { 4, 4, 5, 12, 12, 11 } },
            new double[][] { { 3, 3, 3, 13, 13, 13 } },
            new double[][] { { 4, 4, 0, 12, 12, 5 } },
            new double[][] { { 4, 4, 0, 12, 12, 3 } },
            new double[][] { { 2, 2, 0, 14, 14, 1 }, { 4, 4, 1, 12, 12, 3 } },
            new double[][] { { 3, 3, 2, 13, 13, 3 }, { 4, 4, 1.5, 5, 5, 2 }, { 11, 4, 1.5, 12, 5, 2 }, { 4, 11, 1.5, 5, 12, 2 },
                    { 11, 11, 1.5, 12, 12, 2 } });
    // A facade: a 16x16x1 panel over the side.
    private static final double[] FACADE = { 0, 0, 0, 16, 16, 1 };
    // How far a part host moves a part that faces out (terminals, the sensor) from the far side back to the block it's
    // mounted on: the block less a terminal's 2.5 px housing.
    public static final double HOST_SHIFT = 13.5;

    private static final Map<Long, VoxelShape> SLIM_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, VoxelShape> DENSE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, VoxelShape> HOST_CACHE = new ConcurrentHashMap<>();

    private CableShapes() {}

    // connections by Direction ordinal (DOWN, UP, NORTH, SOUTH, WEST, EAST).
    public static VoxelShape get(CableTier tier, CableConnection[] connections, CableAttachments attachments) {
        long key = 0;
        for (CableConnection connection : connections) {
            key = key * 3 + connection.ordinal();
        }
        key = (key << 24) + attachments.shapeKey();
        Parts parts = tier.dense() ? DENSE : SLIM;
        return (tier.dense() ? DENSE_CACHE : SLIM_CACHE).computeIfAbsent(key, k -> build(parts, connections, attachments));
    }

    // The axis of a straight piece: the only connections are cables on two opposite sides, and nothing is mounted (an
    // anchor or a part sits on the junction cube). Otherwise null.
    public static Direction.@Nullable Axis straightAxis(CableConnection[] connections, CableAttachments attachments) {
        if (attachments.hasMountedPart()) {
            return null;
        }
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

    private static VoxelShape build(Parts parts, CableConnection[] connections, CableAttachments attachments) {
        // Six facades enclose the cable: a solid block to stand on and walk against.
        boolean enclosed = true;
        for (Direction side : Direction.values()) {
            enclosed &= attachments.facade(side);
        }
        if (enclosed) {
            return Shapes.block();
        }
        Direction.Axis straight = straightAxis(connections, attachments);
        VoxelShape shape;
        if (straight != null) {
            shape = boxes(parts.cubeStraight(), along(straight));
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
                    case BLOCK -> Shapes.or(shape, boxes(attachments.facade(side) ? parts.armJunction() : parts.armBlock(), side));
                    case NONE -> shape;
                };
            }
        }
        for (Direction side : Direction.values()) {
            if (attachments.anchored(side)) {
                shape = Shapes.or(shape, boxes(parts.anchor(), side));
            } else if (attachments.part(side) != null) {
                shape = Shapes.or(shape, boxes(attachments.part(side).boxes(), side));
            } else if (attachments.facade(side)) {
                shape = Shapes.or(shape, rotated(FACADE, side));
            }
        }
        return shape.optimize();
    }

    // The facade panel on a side, for working out whether a hit landed on it.
    public static VoxelShape facade(Direction side) {
        return rotated(FACADE, side);
    }

    // A part on a cable side, for working out whether a hit landed on it.
    public static VoxelShape part(PartType part, Direction side) {
        return boxes(part.boxes(), side);
    }

    // A part host: just its part on the side toward the block it's mounted on. A part that faces out is the part on the
    // far side moved back against the block (its stub, which would reach into that block, left out); one that faces the
    // block sits as on a cable.
    public static VoxelShape partHost(CableAttachments attachments) {
        return HOST_CACHE.computeIfAbsent(attachments.shapeKey(), key -> {
            VoxelShape shape = Shapes.empty();
            for (Direction side : Direction.values()) {
                PartType part = attachments.part(side);
                if (part != null) {
                    shape = Shapes.or(shape, hostPart(part, side));
                }
            }
            return shape.optimize();
        });
    }

    public static VoxelShape hostPart(PartType part, Direction mount) {
        if (!part.facesOut()) {
            return boxes(part.boxes(), mount);
        }
        VoxelShape shape = Shapes.empty();
        for (double[] box : part.boxes()) {
            if (box[5] + HOST_SHIFT <= 16) {
                shape = Shapes.or(shape, rotated(new double[] { box[0], box[1], box[2] + HOST_SHIFT, box[3], box[4], box[5] + HOST_SHIFT },
                        mount.getOpposite()));
            }
        }
        return shape;
    }

    private static Direction along(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.NORTH;
        };
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
