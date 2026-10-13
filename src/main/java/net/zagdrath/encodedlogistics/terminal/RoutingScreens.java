/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.exec.ExchangeCommands;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.exec.RoutingCommands;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.routing.RoutingDomain;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// The routing display screens' lines (docs/routing/layouts): DSPROUTE (a Router's route table) and DSPNBR (its
// neighbours). The first line is the screen's row 2 (the Router and its summary); the rest go from row 4 (the column
// headings, the rows, a note).
public final class RoutingScreens {
    private RoutingScreens() {}

    static String head(RouterDevice router, TerminalContext context) {
        NetworkRef home = router.rack() != null ? router.rack().network(router) : null;
        return routerName(router.deviceName(), home != null ? RouterDevice.networkName(context.server(), home) : "*NONE");
    }

    private static TerminalOutput missing(String name) {
        return TerminalOutput.message(Component.literal(ElclMessage.of("ELC2601", name).toString()));
    }

    static TerminalOutput routes(TerminalContext context, String name) {
        RouterDevice router = RoutingCommands.router(context.server(), context, name.toUpperCase(Locale.ROOT));
        if (router == null) {
            return missing(name.toUpperCase(Locale.ROOT));
        }
        TerminalOutput out = new TerminalOutput();
        out.line(routeHead(head(router, context), "*" + router.mode().name(), router.table().size()));
        out.line(TerminalLine.of(Component.literal(ROUTE_COLUMNS), TerminalLine.BRIGHT));
        long now = context.server().overworld().getGameTime();
        for (RouterDevice.TableEntry entry : router.table()) {
            out.line(routeRow(entry.name(), entry.next(), entry.cost(), entry.hops(), entry.source().special(), age((now - entry.since()) / 20)));
        }
        out.line(TerminalLine.blank());
        String note = "*STATIC routes override *DYNAMIC ones.";
        if (router.mode() == RouterDevice.Mode.DYNAMIC && router.lastChangedAt() >= 0) {
            note += String.format(Locale.ROOT, "  %s reconverged %d s ago.", router.lastChanged(), (now - router.lastChangedAt()) / 20);
        }
        out.line(note);
        return out;
    }

    static TerminalOutput neighbours(TerminalContext context, String name) {
        RouterDevice router = RoutingCommands.router(context.server(), context, name.toUpperCase(Locale.ROOT));
        if (router == null) {
            return missing(name.toUpperCase(Locale.ROOT));
        }
        int up = router.upNeighbours();
        TerminalOutput out = new TerminalOutput();
        out.line(neighbourHead(head(router, context), up, router.neighbourTable().size() - up));
        out.line(TerminalLine.of(Component.literal(NEIGHBOUR_COLUMNS), TerminalLine.BRIGHT));
        long now = context.server().overworld().getGameTime();
        for (RouterDevice.NeighbourEntry entry : router.neighbourTable()) {
            boolean full = entry.state() == RoutingDomain.State.FULL;
            out.line(neighbourRow(entry.name(), entry.via().equals("Link") ? "Link Card" : entry.via(), "*" + entry.state().name(),
                    entry.state() == RoutingDomain.State.DOWN ? "-" : Integer.toString(entry.cost()),
                    entry.override() >= 0 ? Integer.toString(entry.override()) : "-", full ? age((now - entry.since()) / 20) : "-"));
        }
        if (router.mode() == RouterDevice.Mode.STATIC) {
            out.line(TerminalLine.blank());
            out.line("The Router is in *STATIC mode: it has no neighbours.");
        }
        return out;
    }

    public static final String ROUTE_COLUMNS = "Destination   Next hop       Cost   Hops  Source     Age",
            NEIGHBOUR_COLUMNS = "Neighbour     Via               State     Cost  Override  Uptime";

    public static String routerName(String router, String network) {
        return String.format(Locale.ROOT, "Router . . :  %s (%s)", router, network);
    }

    public static String routeHead(String router, String mode, int routes) {
        return String.format(Locale.ROOT, "%-30s  Mode . . :  %-9s  Routes . :  %d", router, mode, routes);
    }

    public static String routeRow(String network, String next, int cost, int hops, String source, String age) {
        return String.format(Locale.ROOT, "%-14s%-15s%4d%7d  %-11s%s", network, next, cost, hops, source, age);
    }

    public static String neighbourHead(String router, int up, int down) {
        return String.format(Locale.ROOT, "%-30s  Neighbours :  %d up, %d down", router, up, down);
    }

    public static String neighbourRow(String name, String via, String state, String cost, String override, String uptime) {
        return String.format(Locale.ROOT, "%-14s%-18s%-10s%4s%10s  %s", name, via, state, cost, override, uptime);
    }

    // WRKIX 5=Display: an exchange member's details.
    static TerminalOutput ixMember(TerminalContext context, String exchange, String network) {
        TerminalOutput out = new TerminalOutput();
        try {
            ExchangeCommands.member(context, exchange, network).forEach(out::line);
        } catch (ElclException e) {
            return TerminalOutput.message(Component.literal(e.elclMessage().toString()));
        }
        return out;
    }

    // "2:14:06" from seconds.
    static String age(long seconds) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
