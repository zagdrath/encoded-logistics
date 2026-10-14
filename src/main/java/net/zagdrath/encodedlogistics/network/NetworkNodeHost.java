/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import org.jspecify.annotations.Nullable;

// A block entity that is part of a network: cables, terminals, buses, drives. NetworkDiscovery walks from the
// controllers through these. A host that changes its connections or lane use should call
// ControllerStructures.topologyChanged so its network works out its lanes again.
public interface NetworkNodeHost {
    @Nullable NetworkNode getNetworkNode();
}
