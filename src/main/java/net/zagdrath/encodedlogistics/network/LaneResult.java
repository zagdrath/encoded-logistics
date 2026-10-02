/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.Map;

import net.minecraft.core.BlockPos;

// What the LaneSolver decided for one network.
//   status:    ONLINE, CONFLICT or ADHOC_OVERLOAD (power is not the solver's business)
//   adHoc:     no controller on the network
//   capacity:  lanes the network can hand out (32 per connected controller face; the ad-hoc limit without one)
//   used:      lanes handed out
//   lanes:  for every lane-using device, whether it got its lane
//   linkUsage: lanes running over each link, for smart cables to draw lit strands
public record LaneResult(NetworkStatus status, boolean adHoc, int capacity, int used, Map<BlockPos, Boolean> lanes,
        Map<NetworkLink, Integer> linkUsage) {
    public LaneResult {
        lanes = Map.copyOf(lanes);
        linkUsage = Map.copyOf(linkUsage);
    }

    public boolean hasLane(BlockPos device) {
        return lanes.getOrDefault(device, false);
    }

    public int usage(NetworkLink link) {
        return linkUsage.getOrDefault(link, 0);
    }

    public int missing() {
        return (int) lanes.values().stream().filter(has -> !has).count();
    }
}
