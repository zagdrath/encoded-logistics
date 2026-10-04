/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// A network controller's FE buffer as ControllerStructures runs it: a Network Controller block, or a rack Network
// Controller (2U / 4U).
public interface ControllerBuffer {
    int getEnergy();

    int getCapacity();

    // Takes up to amount FE out for the network; returns what it took.
    int drain(int amount);

    // Network side (a Power Inlet's FE): fills past the per-tick limit on its faces; returns what went in.
    int fill(int amount, TransactionContext transaction);

    // FE received since the last call; starts a new tick's receive allowance.
    int takeReceived();
}
