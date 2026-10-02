/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;

// The Network Controller's shape rule. A connected group of controllers is valid when its bounding box is at most
// maxSize on every axis and the group is exactly the edge set of that box: every position where at least two of the
// three coordinates sit on the box's min or max. That makes single blocks, lines, 2x2x2 cubes and flat rings valid,
// and any filled face (a solid 3x3 wall, say) invalid, since the face's centre is not an edge.
public final class ControllerFrame {
    public enum Problem {
        NONE, INVALID_SHAPE, TOO_LARGE
    }

    public record Result(BlockPos min, BlockPos max, int blockCount, Problem problem) {
        public boolean valid() {
            return problem == Problem.NONE;
        }

        // A valid structure of more than one block: the blocks join up visually.
        public boolean formed() {
            return valid() && blockCount > 1;
        }

        public int sizeX() {
            return max.getX() - min.getX() + 1;
        }

        public int sizeY() {
            return max.getY() - min.getY() + 1;
        }

        public int sizeZ() {
            return max.getZ() - min.getZ() + 1;
        }
    }

    private ControllerFrame() {}

    public static Result validate(Collection<BlockPos> blocks, int maxSize) {
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("A structure has at least one block");
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);
        Result tooLarge = new Result(min, max, blocks.size(), Problem.TOO_LARGE);
        if (tooLarge.sizeX() > maxSize || tooLarge.sizeY() > maxSize || tooLarge.sizeZ() > maxSize) {
            return tooLarge;
        }
        Set<BlockPos> present = blocks instanceof Set<BlockPos> set ? set : new HashSet<>(blocks);
        for (BlockPos pos : present) {
            if (!isEdge(pos, min, max)) {
                return new Result(min, max, blocks.size(), Problem.INVALID_SHAPE);
            }
        }
        // Every block is an edge; the frame is complete when no edge is missing.
        boolean complete = present.size() == edgeCount(tooLarge.sizeX(), tooLarge.sizeY(), tooLarge.sizeZ());
        return new Result(min, max, blocks.size(), complete ? Problem.NONE : Problem.INVALID_SHAPE);
    }

    // At least two of x, y, z on the box's boundary. A box 1 or 2 wide on an axis has every position on its boundary.
    public static boolean isEdge(BlockPos pos, BlockPos min, BlockPos max) {
        int onBoundary = 0;
        if (pos.getX() == min.getX() || pos.getX() == max.getX()) {
            onBoundary++;
        }
        if (pos.getY() == min.getY() || pos.getY() == max.getY()) {
            onBoundary++;
        }
        if (pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) {
            onBoundary++;
        }
        return onBoundary >= 2;
    }

    // How many edge positions a box of this size has (4 * (x + y + z) - 16 once every side is at least 2).
    public static int edgeCount(int sizeX, int sizeY, int sizeZ) {
        BlockPos min = BlockPos.ZERO;
        BlockPos max = new BlockPos(sizeX - 1, sizeY - 1, sizeZ - 1);
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (isEdge(pos, min, max)) {
                count++;
            }
        }
        return count;
    }
}
