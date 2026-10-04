/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.wireless;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;

// An Access Point, Wireless Bridge or Wireless Port as the HUD popup, Work with Devices and Display Device describe it:
// its name, status and lines (RackDeviceInfo, as a rack device's), and the controller it works through.
public interface WirelessDevice {
    RackDeviceInfo describe(MinecraftServer server);

    // The controller's name (WLC01), or "" for none.
    String controllerName(MinecraftServer server);

    // What scripts call it (AP01, WBRIDGE01; a port's is its part's): ElclDevices gives and keeps it.
    String deviceName();

    void setDeviceName(String name);

    // Its name for a popup's subtitle: the device name, or its kind.
    default Component shownName() {
        return Component.literal(deviceName());
    }
}
