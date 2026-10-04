/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.device;

import java.util.List;
import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;

// Where SAVLIB and RSTLIB find the device DEV() names: every registered source's diskette devices on the system.
// ELC1301 with none of that name, ELC1302 when it's offline, ELC1310 when there's no diskette in it.
public final class Diskettes {
    private static final DeviceSources<DisketteDevice> SOURCES = new DeviceSources<>();

    private Diskettes() {}

    public static void register(DeviceSources.Source<DisketteDevice> source) {
        SOURCES.register(source);
    }

    public static void unregister(DeviceSources.Source<DisketteDevice> source) {
        SOURCES.unregister(source);
    }

    public static List<DisketteDevice> all(ElclSystem system) {
        return SOURCES.all(system);
    }

    // The online device of that name with a diskette in it.
    public static DisketteDevice find(ElclSystem system, String name) throws ElclException {
        String wanted = name.trim().toUpperCase(Locale.ROOT);
        for (DisketteDevice device : all(system)) {
            if (device.name().equalsIgnoreCase(wanted)) {
                if (!device.online()) {
                    throw new ElclException("ELC1302", device.name());
                }
                if (device.mounted().isEmpty()) {
                    throw new ElclException("ELC1310", device.name());
                }
                return device;
            }
        }
        throw new ElclException("ELC1301", wanted);
    }
}
