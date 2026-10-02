/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.Map;

import net.minecraft.core.BlockPos;

// What the ChannelSolver decided for one network.
//   status:    ONLINE, CONFLICT or ADHOC_OVERLOAD (power is not the solver's business)
//   adHoc:     no controller on the network
//   capacity:  channels the network can hand out (32 per connected controller face; the ad-hoc limit without one)
//   used:      channels handed out
//   channels:  for every channel-using device, whether it got its channel
//   linkUsage: channels running over each link, for smart cables to draw lit strands
public record ChannelResult(NetworkStatus status, boolean adHoc, int capacity, int used, Map<BlockPos, Boolean> channels,
        Map<NetworkLink, Integer> linkUsage) {
    public ChannelResult {
        channels = Map.copyOf(channels);
        linkUsage = Map.copyOf(linkUsage);
    }

    public boolean hasChannel(BlockPos device) {
        return channels.getOrDefault(device, false);
    }

    public int usage(NetworkLink link) {
        return linkUsage.getOrDefault(link, 0);
    }

    public int missing() {
        return (int) channels.values().stream().filter(has -> !has).count();
    }
}
