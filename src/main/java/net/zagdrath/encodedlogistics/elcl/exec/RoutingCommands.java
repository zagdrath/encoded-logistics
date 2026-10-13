/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.network.routing.RoutingDomain;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// Dynamic routing from ELCL (the routing handoff): a Router's route table (RTVRTE) and neighbours (RTVNBRSTS, CHGNBR).
// Networks and neighbours are named by their system names (ELNET02), as the Router's route table shows them.
public final class RoutingCommands {
    private RoutingCommands() {}

    static void bind() {
        CommandRegistry.bind("RTVRTE", call -> {
            RouterDevice router = router(call);
            String network = call.text("NETWORK").trim().toUpperCase(Locale.ROOT);
            RouterDevice.TableEntry entry = router.table().stream().filter(row -> row.name().equalsIgnoreCase(network)).findFirst().orElse(null);
            if (entry == null) {
                throw new ElclException("ELC2602", network);
            }
            call.returns("RTNNEXT", entry.next().toUpperCase(Locale.ROOT));
            call.returns("RTNCOST", (long) entry.cost());
            call.returns("RTNHOPS", (long) entry.hops());
        });
        CommandRegistry.bind("RTVNBRSTS", call -> {
            RouterDevice router = router(call);
            RouterDevice.NeighbourEntry neighbour = neighbour(call, router);
            call.returns("RTNSTS", "*" + neighbour.state().name());
            call.returns("RTNCOST", (long) neighbour.cost());
            long now = OsCommands.context(call).server().overworld().getGameTime();
            call.returns("RTNUPTIME", neighbour.state() == RoutingDomain.State.FULL ? (now - neighbour.since()) / 20 : 0L);
        });
        CommandRegistry.bind("CHGNBR", call -> {
            RouterDevice router = router(call);
            RouterDevice.NeighbourEntry neighbour = neighbour(call, router);
            String cost = call.text("COST").trim().toUpperCase(Locale.ROOT);
            int value;
            try {
                value = cost.equals("*DFT") ? -1 : Integer.parseInt(cost);
            } catch (NumberFormatException e) {
                throw new ElclException("ELC0103", cost, "COST");
            }
            router.overrideCost(neighbour.home(), value);
            call.send(ElclMessage.of("ELC2609", neighbour.name(), router.deviceName()));
        });
    }

    // The Router RTR() names on the caller's network.
    static RouterDevice router(Invocation call) throws ElclException {
        ElclContext context = OsCommands.context(call);
        String name = call.text("RTR").trim().toUpperCase(Locale.ROOT);
        RouterDevice router = router(context.server(), context, name);
        if (router == null) {
            throw new ElclException("ELC2601", name);
        }
        return router;
    }

    public static @Nullable RouterDevice router(MinecraftServer server, ElclContext context, String name) {
        if (context.network() == null) {
            return null;
        }
        ElclDevices.Device device = ElclDevices.find(server, context.network(), name);
        return device != null && device.rack() instanceof RouterDevice router ? router : null;
    }

    private static RouterDevice.NeighbourEntry neighbour(Invocation call, RouterDevice router) throws ElclException {
        String name = call.text("NBR").trim().toUpperCase(Locale.ROOT);
        RouterDevice.NeighbourEntry neighbour = router.neighbour(name);
        if (neighbour == null) {
            throw new ElclException("ELC2605", name, router.deviceName());
        }
        return neighbour;
    }
}
