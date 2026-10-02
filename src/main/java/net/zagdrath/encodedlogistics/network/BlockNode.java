/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// A network block that uses no lanes and carries any number through it: Power Inlet, Capacitor Bank, and (without
// passing through) the Segment Isolator.
public record BlockNode(BlockPos pos, Set<Direction> connections, double passiveDrain, boolean passesThrough)
        implements NetworkNode {
    @Override
    public int laneCost() {
        return 0;
    }
}
