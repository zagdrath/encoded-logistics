/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

// A Midrange-line block with a device name (HANDOFF 7): MIDRANGE01, KEYPUNCH01, CARDRDR01, PRT01. ElclDevices gives and
// keeps it; it goes with the block's item (ModDataComponents.DEVICE_NAME).
public interface MidrangeDevice {
    // Its type code, the name's prefix (MIDRANGE, KEYPUNCH, CARDRDR, PRT).
    String deviceType();

    String deviceName();

    void setDeviceName(String name);

    boolean isOnline();
}
