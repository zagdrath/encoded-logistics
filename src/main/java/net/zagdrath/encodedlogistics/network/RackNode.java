/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// A Server Rack's master on the network. Its devices take lanes one by one (demands: each switch's uplink and each
// device no switch pools, with the device's priority), not all or none, over any of the rack's uplinks that lead toward
// a source (LaneSolver). controllers: the rack Network Controllers in it; with a usable one the rack is a lane source.
// structure: the rack's controller structure id (ControllerStructures), 0 without controllers. remoteLinks: its devices'
// links to nodes elsewhere (a Wireless Controller's Wireless Bridges and Ports).
public record RackNode(BlockPos pos, Set<Direction> connections, double passiveDrain, List<NetworkPart> parts, List<LaneDemand> demands,
        List<RackController> controllers, long structure, List<RemoteLink> remoteLinks) implements NetworkNode {
    public RackNode {
        demands = List.copyOf(demands);
        controllers = List.copyOf(controllers);
        remoteLinks = List.copyOf(remoteLinks);
    }

    public RackNode(BlockPos pos, Set<Direction> connections, double passiveDrain, List<NetworkPart> parts, List<LaneDemand> demands,
            List<RackController> controllers, long structure) {
        this(pos, connections, passiveDrain, parts, demands, controllers, structure, List.of());
    }

    // What a device in the rack needs from the network: its unit, lanes and priority (0 High, 1 Normal, 2 Low).
    public record LaneDemand(int u, int cost, int priority) {}

    // A rack Network Controller: its unit, its size in U (2 or 4: a pair must match), the lanes it hands out, and whether
    // it can run (not faulted).
    public record RackController(int u, int size, int lanes, boolean usable) {}

    @Override
    public int laneCost() {
        return demands.stream().mapToInt(LaneDemand::cost).sum();
    }

    // Always a device: it goes online with the network even when everything in it is free (a lone controller).
    @Override
    public boolean isDevice() {
        return true;
    }

    @Override
    public BlockPos rackMaster() {
        return pos;
    }

    public boolean isSource() {
        return controllers.stream().anyMatch(RackController::usable);
    }
}
