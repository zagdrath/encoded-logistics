/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

// Where a node is: its dimension and position. Like vanilla's GlobalPos, but loading it doesn't load Level, so the
// graph and the LaneSolver stay plain code (and unit-testable).
public record NodePos(ResourceKey<Level> dimension, BlockPos pos) {
    public NodePos {
        pos = pos.immutable();
    }

    public static NodePos of(ResourceKey<Level> dimension, BlockPos pos) {
        return new NodePos(dimension, pos);
    }

    public static NodePos of(GlobalPos pos) {
        return new NodePos(pos.dimension(), pos.pos());
    }
}
