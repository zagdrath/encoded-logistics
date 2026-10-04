/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// One block (or part) on a network. Devices (terminals, buses, drives...) use a lane; cables and the controller use
// none. Cables carry laneCapacity() lanes; anything that just passes lanes through is UNLIMITED.
public interface NetworkNode {
    int UNLIMITED = Integer.MAX_VALUE;
    long NO_CONTROLLER = -1;

    BlockPos pos();

    // Lanes this node needs to work: 1 for a device, 0 for cables and controllers.
    int laneCost();

    // FE per tick the network spends on this node while online.
    double passiveDrain();

    // The sides this node connects on. Two nodes are linked when both connect toward each other.
    Set<Direction> connections();

    // Lanes a link through this node can carry: a controller face's 32, a cable's tier, or UNLIMITED.
    default int laneCapacity() {
        return UNLIMITED;
    }

    // For a controller block: its structure's id. Two or more distinct ids on one network is a conflict.
    default long controllerGroup() {
        return NO_CONTROLLER;
    }

    default boolean isController() {
        return controllerGroup() != NO_CONTROLLER;
    }

    // Whether it's a device the network turns on and off: anything using lanes, and every Server Rack.
    default boolean isDevice() {
        return laneCost() > 0;
    }

    // The Server Rack this node is part of (its master's position), or null.
    default @Nullable BlockPos rackMaster() {
        return null;
    }

    // Devices mounted on this node (terminals on a cable), each listed on its own on the network's screen.
    default List<NetworkPart> parts() {
        return List.of();
    }

    // Whether the network carries on through this node to its other connections. A Segment Isolator doesn't: it ends
    // the network on each side, so the two sides are separate networks.
    default boolean passesThrough() {
        return true;
    }

    // Links to nodes that aren't next to this one (a Network Bridge's partner, a lanes Point-to-Point Link's other end).
    default List<RemoteLink> remoteLinks() {
        return List.of();
    }
}
