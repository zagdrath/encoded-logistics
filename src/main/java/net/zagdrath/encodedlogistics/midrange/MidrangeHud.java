/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;

// A block with a popup by the crosshair, as the rack's units have (client.rack.WirelessHud asks for it through
// net.WirelessInfoPayloads): the Midrange Disk Drive and Tape Drive (on their footprint's master), and the signal
// devices (Cage Lights, Alarm Strobes, Speakers). Server side.
public interface MidrangeHud {
    // Its ELCL name (DISK01, TAPE01, LGT01), or "" for none.
    String deviceName();

    RackDeviceInfo hudInfo();
}
