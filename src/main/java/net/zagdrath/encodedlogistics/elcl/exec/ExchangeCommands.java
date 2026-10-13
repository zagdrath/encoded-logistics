/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.blockentity.ExchangeSwitchBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.network.routing.ExchangeRegistry;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// Exchange Switches from ELCL (the routing handoff 2, 6): WRKIX's rows, and approving (APVIXMBR) and removing
// (RMVIXMBR) members - the exchange's owner's to do (a member's own side may also remove itself, from the panel).
// An exchange is named by its device name (XSW01) on the caller's network, or by its own name.
public final class ExchangeCommands {
    private ExchangeCommands() {}

    static void bind() {
        CommandRegistry.bind("APVIXMBR", call -> {
            ExchangeSwitchBlockEntity exchange = exchange(call);
            owner(call, exchange);
            int index = member(call, exchange);
            if (!exchange.members().get(index).request() || !exchange.approve(index)) {
                throw new ElclException("ELC2613", exchange.exchangeName());
            }
            call.send(ElclMessage.of("ELC2614", exchange.members().get(index).network(), exchange.exchangeName()));
        });
        CommandRegistry.bind("RMVIXMBR", call -> {
            ExchangeSwitchBlockEntity exchange = exchange(call);
            owner(call, exchange);
            int index = member(call, exchange);
            String network = exchange.members().get(index).network();
            exchange.remove(index);
            call.send(ElclMessage.of("ELC2615", network, exchange.exchangeName()));
        });
    }

    private static ExchangeSwitchBlockEntity exchange(Invocation call) throws ElclException {
        ElclContext context = OsCommands.context(call);
        String name = call.text("IX").trim().toUpperCase(Locale.ROOT);
        ExchangeSwitchBlockEntity exchange = find(context.server(), context, name);
        if (exchange == null) {
            throw new ElclException("ELC2610", name);
        }
        return exchange;
    }

    // By device name on the caller's network, else by its own name anywhere.
    public static @Nullable ExchangeSwitchBlockEntity find(MinecraftServer server, ElclContext context, String name) {
        if (context.network() != null) {
            ElclDevices.Device device = ElclDevices.find(server, context.network(), name);
            if (device != null && device.entity() instanceof ExchangeSwitchBlockEntity exchange) {
                return exchange;
            }
        }
        for (ExchangeSwitchBlockEntity exchange : ExchangeRegistry.all(server)) {
            if (exchange.exchangeName().equalsIgnoreCase(name)) {
                return exchange;
            }
        }
        return null;
    }

    private static void owner(Invocation call, ExchangeSwitchBlockEntity exchange) throws ElclException {
        ElclContext context = OsCommands.context(call);
        boolean allowed = context instanceof TerminalContext terminal ? exchange.isOwner(terminal.player()) : exchange.isOwner(OsCommands.player(call));
        if (!allowed) {
            throw new ElclException("ELC2612", exchange.ownerName());
        }
    }

    private static int member(Invocation call, ExchangeSwitchBlockEntity exchange) throws ElclException {
        String network = call.text("NETWORK").trim();
        for (int i = 0; i < exchange.members().size(); i++) {
            if (exchange.members().get(i).network().equalsIgnoreCase(network)) {
                return i;
            }
        }
        throw new ElclException("ELC2611", network.toUpperCase(Locale.ROOT), exchange.exchangeName());
    }

    // --- WRKIX ---

    // view: *MEMBERS (and requests), *SESSIONS or *REQUESTS. The first row is the exchange: name, owner, ports used,
    // ports, membership. Members: network, owner, port, *UP / *DOWN / *REQUEST, *RS / *BILAT / -, routes learned
    // through the exchange, traffic a minute. Sessions: the two networks, *UP / *DOWN, *RS / *BILAT, traffic a minute.
    public static List<TerminalLine> rows(ElclContext context, String name, String view) throws ElclException {
        MinecraftServer server = context.server();
        ExchangeSwitchBlockEntity exchange = find(server, context, name.toUpperCase(Locale.ROOT));
        if (exchange == null) {
            throw new ElclException("ELC2610", name.toUpperCase(Locale.ROOT));
        }
        long now = server.overworld().getGameTime();
        List<TerminalLine> rows = new ArrayList<>();
        rows.add(row(exchange.exchangeName(), exchange.ownerName(), Integer.toString(exchange.approved().size()), Integer.toString(exchange.ports()),
                "*" + exchange.membership().name()));
        List<ExchangeSwitchBlockEntity.Session> sessions = exchange.sessions();
        if (view.equalsIgnoreCase("*SESSIONS")) {
            for (ExchangeSwitchBlockEntity.Session session : sessions) {
                rows.add(row(session.a().network() + "-" + session.b().network(), "", "", session.up() ? "*UP" : "*DOWN",
                        session.viaRouteServer() ? "*RS" : "*BILAT", "", String.format(Locale.ROOT, "%,d", exchange.trafficPerMinute(session.a(), session.b(), now))));
            }
            return rows;
        }
        boolean requestsOnly = view.equalsIgnoreCase("*REQUESTS");
        for (ExchangeSwitchBlockEntity.Member member : exchange.members()) {
            if (requestsOnly && !member.request()) {
                continue;
            }
            if (member.request()) {
                rows.add(row(member.network(), member.ownerName(), "-", "*REQUEST", "-", "-", "-"));
                continue;
            }
            long traffic = 0;
            boolean bilateral = false;
            for (ExchangeSwitchBlockEntity.Session session : sessions) {
                if (session.a() == member || session.b() == member) {
                    traffic += exchange.trafficPerMinute(session.a(), session.b(), now);
                    bilateral |= !session.viaRouteServer();
                }
            }
            String peering = exchange.routeServer() && member.routeServer() ? "*RS" : bilateral ? "*BILAT" : "-";
            RouterDevice router = RouterDevice.at(server, member.router());
            int routes = 0;
            if (router != null) {
                for (RouterDevice.TableEntry entry : router.table()) {
                    if (entry.next().equalsIgnoreCase("IX:" + exchange.exchangeName())) {
                        routes++;
                    }
                }
            }
            rows.add(row(member.network(), member.ownerName(), Integer.toString(member.port()), member.up() && exchange.online() ? "*UP" : "*DOWN", peering,
                    Integer.toString(routes), String.format(Locale.ROOT, "%,d", traffic)));
        }
        return rows;
    }

    private static TerminalLine row(String... cells) {
        TerminalLine.Builder builder = TerminalLine.builder();
        for (String cell : cells) {
            builder.left(cell, 0);
        }
        return builder.build();
    }

    // 5=Display: a member's details.
    public static List<String> member(ElclContext context, String name, String network) throws ElclException {
        MinecraftServer server = context.server();
        ExchangeSwitchBlockEntity exchange = find(server, context, name.toUpperCase(Locale.ROOT));
        if (exchange == null) {
            throw new ElclException("ELC2610", name.toUpperCase(Locale.ROOT));
        }
        ExchangeSwitchBlockEntity.Member member = exchange.memberOn(network);
        if (member == null) {
            throw new ElclException("ELC2611", network.toUpperCase(Locale.ROOT), exchange.exchangeName());
        }
        RouterDevice router = RouterDevice.at(server, member.router());
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "%-24s%s", "Network", member.network()));
        lines.add(String.format(Locale.ROOT, "%-24s%s", "Router", router != null ? router.deviceName() : "*NOTLOADED"));
        lines.add(String.format(Locale.ROOT, "%-24s%s", "Joined by", member.ownerName()));
        lines.add(String.format(Locale.ROOT, "%-24s%s", "Port", member.request() ? "*REQUEST" : Integer.toString(member.port())));
        lines.add(String.format(Locale.ROOT, "%-24s%s", "Status", member.up() && exchange.online() ? "*UP" : "*DOWN"));
        lines.add(String.format(Locale.ROOT, "%-24s%s", "Route server", member.routeServer() ? "*YES" : "*NO"));
        lines.add("");
        lines.add("Sessions:");
        long now = server.overworld().getGameTime();
        for (ExchangeSwitchBlockEntity.Session session : exchange.sessions()) {
            if (session.a() == member || session.b() == member) {
                ExchangeSwitchBlockEntity.Member peer = session.other(member);
                lines.add(String.format(Locale.ROOT, "  %-12s%-8s%-8s%,d/min", peer.network(), session.up() ? "*UP" : "*DOWN",
                        session.viaRouteServer() ? "*RS" : "*BILAT", exchange.trafficPerMinute(session.a(), session.b(), now)));
            }
        }
        return lines;
    }
}
