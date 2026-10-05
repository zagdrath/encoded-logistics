/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;

// A Midrange peripheral with a popup by the crosshair, as the rack's units have (client.rack.WirelessHud asks for it
// through net.WirelessInfoPayloads): the Disk Drive and the Tape Drive. Server side.
public interface MidrangeHud {
    // Its ELCL name (DISK01, TAPE01).
    String deviceName();

    RackDeviceInfo hudInfo();
}
