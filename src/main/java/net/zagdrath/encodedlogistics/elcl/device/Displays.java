/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.device;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;

// Where SNDDSPTXT finds the display it names: every registered source's displays on the system.
public final class Displays {
    private static final DeviceSources<DisplayDevice> SOURCES = new DeviceSources<>();

    private Displays() {}

    public static void register(DeviceSources.Source<DisplayDevice> source) {
        SOURCES.register(source);
    }

    public static void unregister(DeviceSources.Source<DisplayDevice> source) {
        SOURCES.unregister(source);
    }

    public static List<DisplayDevice> all(ElclSystem system) {
        return SOURCES.all(system);
    }

    public static @Nullable DisplayDevice find(ElclSystem system, String name) {
        for (DisplayDevice display : all(system)) {
            if (display.name().equalsIgnoreCase(name.trim().toUpperCase(Locale.ROOT))) {
                return display;
            }
        }
        return null;
    }
}
