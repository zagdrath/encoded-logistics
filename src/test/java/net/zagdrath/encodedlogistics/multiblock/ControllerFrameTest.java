/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

class ControllerFrameTest {
    private static final int MAX = 7;

    private static Set<BlockPos> frame(int sizeX, int sizeY, int sizeZ) {
        BlockPos min = new BlockPos(10, 64, -3);
        BlockPos max = min.offset(sizeX - 1, sizeY - 1, sizeZ - 1);
        Set<BlockPos> blocks = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (ControllerFrame.isEdge(pos, min, max)) {
                blocks.add(pos.immutable());
            }
        }
        return blocks;
    }

    private static Set<BlockPos> box(int sizeX, int sizeY, int sizeZ) {
        Set<BlockPos> blocks = new HashSet<>();
        BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(sizeX - 1, sizeY - 1, sizeZ - 1)).forEach(pos -> blocks.add(pos.immutable()));
        return blocks;
    }

    @Test
    void singleBlockIsValidButNotFormed() {
        ControllerFrame.Result result = ControllerFrame.validate(List.of(BlockPos.ZERO), MAX);
        assertTrue(result.valid());
        assertFalse(result.formed());
    }

    @Test
    void linesUpToTheLimitAreValid() {
        assertTrue(ControllerFrame.validate(box(1, 7, 1), MAX).formed());
        assertEquals(ControllerFrame.Problem.TOO_LARGE, ControllerFrame.validate(box(8, 1, 1), MAX).problem());
    }

    @Test
    void cubesAndFrames() {
        assertTrue(ControllerFrame.validate(box(2, 2, 2), MAX).formed());
        ControllerFrame.Result threeCube = ControllerFrame.validate(frame(3, 3, 3), MAX);
        assertTrue(threeCube.formed());
        assertEquals(20, threeCube.blockCount());
        assertTrue(ControllerFrame.validate(frame(4, 2, 3), MAX).formed());
        // A flat ring.
        assertTrue(ControllerFrame.validate(frame(5, 1, 4), MAX).formed());
        assertEquals(68, frame(7, 7, 7).size());
        assertTrue(ControllerFrame.validate(frame(7, 7, 7), MAX).formed());
    }

    @Test
    void frameBlockCountFormula() {
        for (int x = 2; x <= 7; x++) {
            for (int y = 2; y <= 7; y++) {
                for (int z = 2; z <= 7; z++) {
                    assertEquals(4 * (x + y + z) - 16, ControllerFrame.edgeCount(x, y, z));
                }
            }
        }
    }

    @Test
    void filledFacesAreInvalid() {
        // A solid 3x3 wall: its centre is not an edge.
        assertEquals(ControllerFrame.Problem.INVALID_SHAPE, ControllerFrame.validate(box(3, 3, 1), MAX).problem());
        // A 3x3x3 frame with one face centre filled in.
        List<BlockPos> filled = new ArrayList<>(frame(3, 3, 3));
        filled.add(new BlockPos(11, 65, -3));
        assertEquals(ControllerFrame.Problem.INVALID_SHAPE, ControllerFrame.validate(filled, MAX).problem());
    }

    @Test
    void incompleteFramesAreInvalid() {
        Set<BlockPos> missingCorner = frame(3, 3, 3);
        missingCorner.remove(new BlockPos(10, 64, -3));
        assertEquals(ControllerFrame.Problem.INVALID_SHAPE, ControllerFrame.validate(missingCorner, MAX).problem());
    }

    @Test
    void sizeIsCheckedOnEveryAxis() {
        assertEquals(ControllerFrame.Problem.TOO_LARGE, ControllerFrame.validate(frame(3, 8, 3), MAX).problem());
        assertEquals(ControllerFrame.Problem.TOO_LARGE, ControllerFrame.validate(frame(2, 2, 9), MAX).problem());
    }
}
