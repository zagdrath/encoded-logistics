/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.Map;

// What the LaneSolver decided for one network.
//   status:    ONLINE, CONFLICT or ADHOC_OVERLOAD (power is not the solver's business)
//   adHoc:     no controller on the network
//   capacity:  lanes the network can hand out (32 per connected controller face, a rack controller's lanes; the ad-hoc
//              limit without one)
//   used:      lanes handed out
//   lanes:     for every device, whether it got its lane (a Server Rack: whether it reaches a source at all)
//   linkUsage: lanes running over each link, for smart cables to draw lit strands and Bridges to show
//   racks:     each Server Rack's devices' lanes and uplinks
public record LaneResult(NetworkStatus status, boolean adHoc, int capacity, int used, Map<NodePos, Boolean> lanes,
        Map<NetworkLink, Integer> linkUsage, Map<NodePos, RackLanes> racks) {
    public LaneResult {
        lanes = Map.copyOf(lanes);
        linkUsage = Map.copyOf(linkUsage);
        racks = Map.copyOf(racks);
    }

    public LaneResult(NetworkStatus status, boolean adHoc, int capacity, int used, Map<NodePos, Boolean> lanes, Map<NetworkLink, Integer> linkUsage) {
        this(status, adHoc, capacity, used, lanes, linkUsage, Map.of());
    }

    public boolean hasLane(NodePos device) {
        return lanes.getOrDefault(device, false);
    }

    public int usage(NetworkLink link) {
        return linkUsage.getOrDefault(link, 0);
    }

    public RackLanes rack(NodePos rack) {
        return racks.getOrDefault(rack, RackLanes.NONE);
    }

    // Devices short of their lanes: whole devices, and each shed unit in a rack.
    public int missing() {
        int missing = (int) lanes.values().stream().filter(has -> !has).count();
        for (Map.Entry<NodePos, RackLanes> rack : racks.entrySet()) {
            if (hasLane(rack.getKey()) && rack.getValue().shed()) {
                missing++;
            }
        }
        return missing;
    }
}
