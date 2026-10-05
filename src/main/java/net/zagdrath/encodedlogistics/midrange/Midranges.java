/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.encodedlogistics.crafting.RecipeLibraries;
import net.zagdrath.encodedlogistics.crafting.RecipeLibrarySource;
import net.zagdrath.encodedlogistics.elcl.device.DisketteDevice;
import net.zagdrath.encodedlogistics.elcl.device.Diskettes;
import net.zagdrath.encodedlogistics.elcl.device.PrinterDevice;
import net.zagdrath.encodedlogistics.elcl.device.Printers;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.job.JobHosts;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// The Midrange line on a network: the system a peripheral works for (the first online Midrange System on its network:
// beside it, or cabled to it), and what it adds to ELCL and crafting: diskette drives for SAVLIB / RSTLIB (Card Readers,
// Midrange Systems), printers for PRTRPT / 6=Print (Line Printers), batch job hosts and recipe libraries (Midrange
// Systems).
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

    // A screen's header: the system's name, the device's (named now if it has none yet) and the system's PHOSPHOR.
    public static void writeOpening(BlockEntity entity, MidrangeDevice device, RegistryFriendlyByteBuf buf) {
        String system = "", phosphor = "*GREEN";
        if (entity.getLevel() instanceof ServerLevel level) {
            NetworkRef network = ControllerStructures.networkOf(level, entity.getBlockPos());
            if (network != null) {
                ElclSystem elcl = new ElclSystem(level.getServer(), network);
                system = elcl.name();
                if (device.deviceName().isEmpty()) {
                    ElclDevices.list(level.getServer(), network);
                }
                try {
                    phosphor = ElclServices.sysvals().get(elcl, "PHOSPHOR");
                } catch (RuntimeException e) {
                    // The default.
                }
            }
        }
        buf.writeUtf(system);
        buf.writeUtf(device.deviceName().isEmpty() ? device.deviceType() : device.deviceName());
        buf.writeUtf(phosphor);
    }

    // The ELCL device sources (ElclSetup.init).
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        Diskettes.register(system -> new ArrayList<DisketteDevice>(on(system, CardReaderBlockEntity.class)));
        Printers.register(system -> new ArrayList<PrinterDevice>(on(system, LinePrinterBlockEntity.class)));
        Diskettes.register(system -> new ArrayList<DisketteDevice>(on(system, MidrangeSystemBlockEntity.class)));
        RecipeLibraries.register(system -> new ArrayList<RecipeLibrarySource>(on(system, MidrangeSystemBlockEntity.class)));
        JobHosts.register(system -> {
            List<net.zagdrath.encodedlogistics.elcl.job.JobHost> hosts = new ArrayList<>();
            for (MidrangeSystemBlockEntity midrange : on(system, MidrangeSystemBlockEntity.class)) {
                hosts.add(midrange.batchHost());
            }
            return hosts;
        });
    }

    // Named first: DEV() finds them by name.
    private static <T> List<T> on(ElclSystem system, Class<T> kind) {
        ElclDevices.list(system.server(), system.network());
        return ControllerStructures.onNetwork(system.server(), system.network(), kind, false);
    }
}
