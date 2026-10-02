/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// A network device: uses laneCost lanes (it works only while it has them) and passes lanes through it like any block
// unless passesThrough is false. parts: devices mounted on it, if any (their drain is part of passiveDrain).
public record DeviceNode(BlockPos pos, Set<Direction> connections, int laneCost, double passiveDrain, List<NetworkPart> parts,
        boolean passesThrough) implements NetworkNode {}
