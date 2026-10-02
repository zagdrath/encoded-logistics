/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.Set;

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
}
