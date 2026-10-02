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
// unless passesThrough is false. parts: devices mounted on it, if any (their drain is part of passiveDrain). remoteLinks:
// its links to nodes elsewhere (a lanes Point-to-Point Link on a part host).
public record DeviceNode(BlockPos pos, Set<Direction> connections, int laneCost, double passiveDrain, List<NetworkPart> parts,
        boolean passesThrough, List<RemoteLink> remoteLinks) implements NetworkNode {
    public DeviceNode(BlockPos pos, Set<Direction> connections, int laneCost, double passiveDrain, List<NetworkPart> parts,
            boolean passesThrough) {
        this(pos, connections, laneCost, passiveDrain, parts, passesThrough, List.of());
    }
}
