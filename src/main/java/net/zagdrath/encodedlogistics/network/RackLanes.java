/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.List;
import java.util.Set;

// What the LaneSolver decided for one Server Rack:
//   source:    it holds a usable rack Network Controller (its devices take lanes straight from it)
//   granted:   the units (LaneDemand#u) whose demands got their lanes; the others are shed
//   used:      lanes its granted demands take
//   uplinks:   its links to anything outside it, each with the lanes it can carry, what runs over it for this rack's own
//              devices, and whether it leads toward a source (down: a cut cable's stub, or nothing beyond it)
//   available, total: lanes over its working uplinks, and over all of them (a source: the controller's lanes, twice)
public record RackLanes(boolean source, Set<Integer> granted, int used, int available, int total, List<Uplink> uplinks, boolean shed) {
    public static final RackLanes NONE = new RackLanes(false, Set.of(), 0, 0, 0, List.of(), false);

    public RackLanes {
        granted = Set.copyOf(granted);
        uplinks = List.copyOf(uplinks);
    }

    // outside: the node beyond the rack the link goes to.
    public record Uplink(NodePos outside, int capacity, int used, boolean active) {}

    public int activeUplinks() {
        return (int) uplinks.stream().filter(Uplink::active).count();
    }

    // Short of lanes (a device shed) or an uplink down.
    public boolean degraded() {
        return shed || activeUplinks() < uplinks.size();
    }
}
