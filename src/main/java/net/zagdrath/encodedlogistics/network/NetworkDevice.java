/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

// A block entity whose block uses lanes and shows whether it's working (Drive Bay lights, terminal screens).
// ControllerStructures tells it every tick: online while its network is powered and it has its lanes.
public interface NetworkDevice {
    void setNetworkOnline(boolean online);
}
