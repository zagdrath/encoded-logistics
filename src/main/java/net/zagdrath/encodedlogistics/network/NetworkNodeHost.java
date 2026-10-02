/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import org.jspecify.annotations.Nullable;

// A block entity that is part of a network: cables, terminals, buses, drives. NetworkDiscovery walks from the
// controllers through these. A host that changes its connections or channel use should call
// ControllerStructures.markTopologyChanged so the channels are recomputed.
public interface NetworkNodeHost {
    @Nullable NetworkNode getNetworkNode();
}
