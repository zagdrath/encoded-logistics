/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

// A block entity whose block uses no lanes but is listed on the Terminal Desk's Devices screen all the same, as a device
// (the Midrange peripherals: Keypunch, Card Reader, Line Printer).
public interface ListedDevice {
    // Whether it's shown online (a peripheral: while a Midrange System on its network is).
    boolean listedOnline();
}
