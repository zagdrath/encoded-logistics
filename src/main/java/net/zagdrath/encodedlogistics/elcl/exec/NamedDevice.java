/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

// A block entity with a device name that ElclDevices gives and keeps: its type code is the name's prefix (MIDRANGE01,
// PRT01, DSP01). The name goes with the block's item (ModDataComponents.DEVICE_NAME).
public interface NamedDevice {
    String deviceType();

    String deviceName();

    void setDeviceName(String name);

    boolean isOnline();
}
