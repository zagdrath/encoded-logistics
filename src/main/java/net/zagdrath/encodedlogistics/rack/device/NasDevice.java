/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.StorageDevice;

// The NAS (2U): six Storage Drives in front-loaded bays, as network storage (StorageDevice).
public class NasDevice extends StorageDevice {
    public static final int DRIVES = 6;

    public NasDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public int drives() {
        return DRIVES;
    }

    @Override
    protected double baseDrain() {
        return Config.NAS_DRAIN.getAsDouble();
    }
}
