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
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.network.routing.RoutingDomain;
import net.zagdrath.encodedlogistics.rack.RouterAcl;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// Dynamic routing from ELCL (the routing handoff): a Router's route table (RTVRTE) and neighbours (RTVNBRSTS, CHGNBR).
// ACLs: ADDACLE, RMVACLE, CHGACLE and WRKACL's rows. Networks and neighbours are named by their system names (ELNET02), as the Router's route table shows them.
public final class RoutingCommands {
    private RoutingCommands() {}

    static void bind() {
        bindAcls();
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

    // --- ACLs ---

    static void bindAcls() {
        CommandRegistry.bind("ADDACLE", call -> {
            RouterDevice router = router(call);
            String name = call.text("ACL").trim().toUpperCase(Locale.ROOT);
            // A new name makes the ACL (while the Router has room for one).
            if (router.acl(name) == null && !router.createAcl(name)) {
                throw new ElclException("ELC2606", name);
            }
            RouterAcl acl = router.acl(name);
            String seq = call.text("SEQ").trim().toUpperCase(Locale.ROOT);
            int at = seq.isEmpty() || seq.equals("*LAST") ? -1 : number(seq, "SEQ");
            String match = match(call.text("MATCH"));
            if (acl == null || !acl.add(at, match, dir(call.text("DIR")), call.text("ACTION").trim().equalsIgnoreCase("*PERMIT"),
                    limit(call.text("RATE"), "RATE"), limit(call.text("QUOTA"), "QUOTA"))) {
                throw new ElclException("ELC0103", seq, "SEQ");
            }
            router.aclChanged();
        });
        CommandRegistry.bind("RMVACLE", call -> {
            RouterDevice router = router(call);
            RouterAcl acl = acl(call, router);
            int seq = number(call.text("SEQ"), "SEQ");
            if (!acl.remove(seq)) {
                throw new ElclException("ELC2607", acl.name(), Integer.toString(seq));
            }
            router.aclChanged();
        });
        CommandRegistry.bind("CHGACLE", call -> {
            RouterDevice router = router(call);
            RouterAcl acl = acl(call, router);
            int seq = number(call.text("SEQ"), "SEQ");
            RouterAcl.Rule rule = acl.rule(seq);
            if (rule == null) {
                throw new ElclException("ELC2607", acl.name(), Integer.toString(seq));
            }
            String match = same(call.text("MATCH")) ? rule.match() : match(call.text("MATCH"));
            RouterAcl.Dir dir = same(call.text("DIR")) ? rule.dir() : dir(call.text("DIR"));
            boolean permit = same(call.text("ACTION")) ? rule.permit() : call.text("ACTION").trim().equalsIgnoreCase("*PERMIT");
            int rate = same(call.text("RATE")) ? rule.rate() : limit(call.text("RATE"), "RATE");
            int quota = same(call.text("QUOTA")) ? rule.quota() : limit(call.text("QUOTA"), "QUOTA");
            rule.set(match, dir, permit, rate, quota);
            String move = call.text("MOVE").trim().toUpperCase(Locale.ROOT);
            if (move.equals("*UP") || move.equals("*DOWN")) {
                acl.move(seq, move.equals("*UP"));
            }
            router.aclChanged();
        });
    }

    private static boolean same(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase("*SAME");
    }

    private static RouterAcl acl(Invocation call, RouterDevice router) throws ElclException {
        String name = call.text("ACL").trim().toUpperCase(Locale.ROOT);
        RouterAcl acl = router.acl(name);
        if (acl == null) {
            throw new ElclException("ELC2606", name);
        }
        return acl;
    }

    private static String match(String text) throws ElclException {
        String match = RouterAcl.normalise(text);
        if (match == null) {
            throw new ElclException("ELC0103", text.trim(), "MATCH");
        }
        return match;
    }

    private static RouterAcl.Dir dir(String text) {
        return switch (text.trim().toUpperCase(Locale.ROOT)) {
            case "*IN" -> RouterAcl.Dir.IN;
            case "*OUT" -> RouterAcl.Dir.OUT;
            default -> RouterAcl.Dir.BOTH;
        };
    }

    private static int number(String text, String keyword) throws ElclException {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ElclException("ELC0103", text.trim(), keyword);
        }
    }

    // *NONE (or blank) is no limit.
    private static int limit(String text, String keyword) throws ElclException {
        String trimmed = text.trim();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase("*NONE") ? 0 : number(trimmed, keyword);
    }

    // Work with ACLs (WRKACL): one row per rule - seq, match, dir, action, limit, hits, last matched - then the
    // default (*DFT, its action and hits).
    public static List<TerminalLine> aclRows(ElclContext context, String routerName, String aclName) throws ElclException {
        RouterDevice router = router(context.server(), context, routerName.toUpperCase(Locale.ROOT));
        if (router == null) {
            throw new ElclException("ELC2601", routerName.toUpperCase(Locale.ROOT));
        }
        RouterAcl acl = router.acl(aclName);
        if (acl == null) {
            throw new ElclException("ELC2606", aclName.toUpperCase(Locale.ROOT));
        }
        List<TerminalLine> rows = new ArrayList<>();
        for (RouterAcl.Rule rule : acl.rules()) {
            rows.add(row(Integer.toString(rule.seq()), rule.match(), rule.dir().special(), rule.action(), limitText(rule),
                    String.format(Locale.ROOT, "%,d", rule.hits()), clock(context.server(), rule.lastMatched()), RouterAcl.spec(rule)));
        }
        rows.add(row("*DFT", "(default)", "", Config.ACL_DEFAULT_PERMIT.getAsBoolean() ? "*PERMIT" : "*DENY", "",
                String.format(Locale.ROOT, "%,d", acl.defaultHits()), clock(context.server(), acl.defaultLast()), ""));
        return rows;
    }

    // 64/min or 2000/day (a rule with both shows its rate).
    private static String limitText(RouterAcl.Rule rule) {
        return rule.rate() > 0 ? rule.rate() + "/min" : rule.quota() > 0 ? rule.quota() + "/day" : "";
    }

    private static TerminalLine row(String... cells) {
        TerminalLine.Builder builder = TerminalLine.builder();
        for (String cell : cells) {
            builder.left(cell, 0);
        }
        return builder.build();
    }

    // A game time as the time of day it was then: "14:31:02" ("" for never).
    static String clock(MinecraftServer server, long gameTime) {
        if (gameTime < 0) {
            return "";
        }
        long dayTime = server.overworld().getOverworldClockTime() - (server.overworld().getGameTime() - gameTime);
        long ticks = Math.floorMod(dayTime, 24_000L);
        long hours = (ticks / 1_000 + 6) % 24, minutes = ticks % 1_000 * 60 / 1_000, seconds = ticks % 1_000 * 3_600 / 1_000 % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
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
