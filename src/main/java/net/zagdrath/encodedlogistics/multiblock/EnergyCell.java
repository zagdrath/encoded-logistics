/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// Storage in a network's energy pool besides its controllers: a Capacitor Bank, or an Energy Storage Drive in a drive
// holder (EnergyDrives.Cell). The pool drains these before its controllers and fills them after (ControllerStructures).
public interface EnergyCell {
    long getStored();

    long getCapacity();

    // Puts up to amount in, as part of a transaction; returns what went in.
    int fill(int amount, TransactionContext transaction);

    // Takes up to amount out now; returns what it had.
    int drain(int amount);
}
