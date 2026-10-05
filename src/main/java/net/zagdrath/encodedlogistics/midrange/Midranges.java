/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.encodedlogistics.elcl.device.DisketteDevice;
import net.zagdrath.encodedlogistics.elcl.device.Diskettes;
import net.zagdrath.encodedlogistics.elcl.device.PrinterDevice;
import net.zagdrath.encodedlogistics.elcl.device.Printers;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// The Midrange line on a network: the system a peripheral works for (the first online Midrange System on its network:
// beside it, or cabled to it), and the devices SAVLIB / RSTLIB (Card Readers) and PRTRPT / 6=Print (Line Printers) find.
public final class Midranges {
    private static boolean registered;

    private Midranges() {}

    public static @Nullable MidrangeSystemBlockEntity host(ServerLevel level, BlockPos pos) {
        NetworkRef network = ControllerStructures.networkOf(level, pos);
        if (network == null) {
            return null;
        }
        List<MidrangeSystemBlockEntity> hosts = ControllerStructures.onNetwork(level.getServer(), network, MidrangeSystemBlockEntity.class, true);
        return hosts.isEmpty() ? null : hosts.getFirst();
    }

    // The ELCL device sources (ElclSetup.init).
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        Diskettes.register(system -> new ArrayList<DisketteDevice>(on(system, CardReaderBlockEntity.class)));
        Printers.register(system -> new ArrayList<PrinterDevice>(on(system, LinePrinterBlockEntity.class)));
    }

    // Named first: DEV() finds them by name.
    private static <T> List<T> on(ElclSystem system, Class<T> kind) {
        ElclDevices.list(system.server(), system.network());
        return ControllerStructures.onNetwork(system.server(), system.network(), kind, false);
    }
}
