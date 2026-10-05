/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// A block that is a network's controller on its own (a Midrange System or Integrated Midrange System, HANDOFF 4): on
// the network as a device is (lanes pass through it), but a lane source - sourceLanes lanes, one budget shared by
// everything drawing on it - with a structure of its own (sourceStructure). Another controller of any kind on the same
// network is a conflict (LaneSolver).
public record SourceNode(BlockPos pos, Set<Direction> connections, double passiveDrain, long sourceStructure, int sourceLanes) implements NetworkNode {
    @Override
    public int laneCost() {
        return 0;
    }
}
