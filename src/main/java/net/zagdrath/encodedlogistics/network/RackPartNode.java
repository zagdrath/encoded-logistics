/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// One of a Server Rack's five other blocks: passes lanes through like any block, but belongs to its rack (master), so
// the LaneSolver can tell the rack's uplinks (links from its blocks to anything else) from its insides.
public record RackPartNode(BlockPos pos, Set<Direction> connections, BlockPos master) implements NetworkNode {
    @Override
    public int laneCost() {
        return 0;
    }

    @Override
    public double passiveDrain() {
        return 0;
    }

    @Override
    public BlockPos rackMaster() {
        return master;
    }
}
