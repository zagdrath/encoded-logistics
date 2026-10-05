/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Moving items between networks' storage by route, for the Router (between networks) and the L3 Switch (between
// segments). A route goes from one endpoint (by index) to another, for the items its filter passes: a port's filter
// (PartFilter, its options always on, no fuzzy matching), where an empty allow list passes nothing (a new route moves
// nothing until it's set up) and an empty deny list everything. A second's budget is shared between the active routes in
// turn; with priority levels (the L3 Switch's QoS: 0 high, 1 normal, 2 low), every route moves its high-priority items
// first, then normal, then low.
//
// A route is Move (the above) or Share: nothing moves, and the source's own storage, for the items the filter passes, is
// part of the destination's as its terminals and crafting see it (ControllerStructures.sharedStorageOf): Read (view and
// withdraw) or Read/Write (inserts too, after the destination's own storage is full), usable by crafting or not, one way
// or both ways (bidirectional).
public final class ItemRouting {
    public static final int HIGH = 0, NORMAL = 1, LOW = 2, LEVELS = 3;
    // A filter's options, as the panels' option buttons number them.
    public static final int OPTION_DENY = 0, OPTION_TAGS = 1, OPTION_COMPONENTS = 2, OPTIONS = 3;

    public enum Mode {
        MOVE, SHARE
    }

    // A Share route's options, as the panels number them (with the mode toggle first).
    public static final int SHARE_MODE = 0, SHARE_ACCESS = 1, SHARE_CRAFTING = 2, SHARE_BIDIRECTIONAL = 3, SHARE_OPTIONS = 4;

    // The filter is the route's own (changed in place by the panel's actions). readWrite, crafting and bidirectional are
    // a Share route's options.
    public record Route(int source, int dest, PartFilter filter, Mode mode, boolean readWrite, boolean crafting, boolean bidirectional) {
        public Route(int source, int dest, PartFilter filter) {
            this(source, dest, filter, Mode.MOVE, true, true, false);
        }

        public Route(int source, int dest) {
            this(source, dest, new PartFilter());
        }

        public boolean shares() {
            return mode == Mode.SHARE;
        }

        // Whether it shares from endpoint from into endpoint to.
        public boolean sharesInto(int from, int to) {
            return shares() && (source == from && dest == to || bidirectional && source == to && dest == from);
        }

        public boolean matches(StorageKey key) {
            return passes(filter, key.stack());
        }

        public Route withSource(int source) {
            return new Route(source, dest, filter, mode, readWrite, crafting, bidirectional);
        }

        public Route withDest(int dest) {
            return new Route(source, dest, filter, mode, readWrite, crafting, bidirectional);
        }

        // The same with one Share option (SHARE_*) toggled.
        public Route toggled(int option) {
            return switch (option) {
                case SHARE_MODE -> new Route(source, dest, filter, shares() ? Mode.MOVE : Mode.SHARE, readWrite, crafting, bidirectional);
                case SHARE_ACCESS -> new Route(source, dest, filter, mode, !readWrite, crafting, bidirectional);
                case SHARE_CRAFTING -> new Route(source, dest, filter, mode, readWrite, !crafting, bidirectional);
                default -> new Route(source, dest, filter, mode, readWrite, crafting, !bidirectional);
            };
        }

        // Whether it can move anything at all (not an empty allow list).
        public boolean idle() {
            return filter.isEmpty() && !filter.deny();
        }
    }

    public static boolean passes(PartFilter filter, ItemStack stack) {
        return filter.isEmpty() ? filter.deny() : filter.test(stack, true, false);
    }

    // A filter passing everything (an empty deny list).
    public static PartFilter everything() {
        PartFilter filter = new PartFilter();
        filter.toggleDeny();
        return filter;
    }

    // A filter passing only these items (an allow list).
    public static PartFilter only(ItemStack... items) {
        PartFilter filter = new PartFilter();
        for (int i = 0; i < Math.min(items.length, PartFilter.SIZE); i++) {
            filter.set(i, items[i]);
        }
        return filter;
    }

    // --- Panel actions on a list of routes (value packs the route and the entry or option) ---

    // Sets entry value % SIZE of route value / SIZE's filter (an empty stack clears it); false when there's no such route.
    public static boolean setEntry(List<Route> routes, int value, ItemStack stack) {
        int route = value / PartFilter.SIZE, entry = value % PartFilter.SIZE;
        if (value < 0 || route >= routes.size()) {
            return false;
        }
        routes.get(route).filter().set(entry, stack);
        return true;
    }

    // Toggles Share option value % SHARE_OPTIONS of route value / SHARE_OPTIONS; false when there's no such route.
    public static boolean toggleShare(List<Route> routes, int value) {
        int route = value / SHARE_OPTIONS;
        if (value < 0 || route >= routes.size()) {
            return false;
        }
        routes.set(route, routes.get(route).toggled(value % SHARE_OPTIONS));
        return true;
    }

    // Toggles option value % OPTIONS of route value / OPTIONS's filter; false when there's no such route.
    public static boolean toggleOption(List<Route> routes, int value) {
        int route = value / OPTIONS;
        if (value < 0 || route >= routes.size()) {
            return false;
        }
        PartFilter filter = routes.get(route).filter();
        switch (value % OPTIONS) {
            case OPTION_DENY -> filter.toggleDeny();
            case OPTION_TAGS -> filter.toggleTags();
            default -> filter.toggleComponents();
        }
        return true;
    }

    // An active route this second: its storages at both ends.
    public record Leg(Route route, NetworkStorage from, NetworkStorage to) {}

    // An active Share: from one network into another (each way of a bidirectional route is one), what passes, and the
    // source endpoint's name.
    public record ShareLink(NetworkRef from, NetworkRef into, Route route, Component fromName) {}

    // A device whose routes can share (the L3 Switch, the Router): its active Shares, while it's online.
    public interface ShareSource {
        List<ShareLink> shareLinks(MinecraftServer server);
    }

    // A device's Share routes as links, given how it finds an endpoint's network and name.
    public static List<ShareLink> shareLinks(List<Route> routes, IntFunction<@Nullable NetworkRef> network, IntFunction<Component> name) {
        List<ShareLink> links = new ArrayList<>();
        for (Route route : routes) {
            if (!route.shares() || route.idle()) {
                continue;
            }
            NetworkRef source = network.apply(route.source()), dest = network.apply(route.dest());
            if (source == null || dest == null || source.equals(dest)) {
                continue;
            }
            links.add(new ShareLink(source, dest, route, name.apply(route.source())));
            if (route.bidirectional()) {
                links.add(new ShareLink(dest, source, route, name.apply(route.dest())));
            }
        }
        return links;
    }

    // "2 move, 1 share": active routes by mode.
    public static Component split(int move, int share) {
        return Component.translatable("hud.encodedlogistics.routes.split", move, share);
    }

    private ItemRouting() {}

    // Moves up to budget items over the legs, starting with the leg at cursor; level gives an item's priority level.
    // Returns how many moved.
    public static int move(List<Leg> legs, int budget, int cursor, ToIntFunction<StorageKey> level) {
        int total = 0;
        if (legs.isEmpty()) {
            return 0;
        }
        for (int priority = HIGH; priority < LEVELS && budget > 0; priority++) {
            int count = legs.size();
            for (int n = 0; n < count && budget > 0; n++) {
                Leg leg = legs.get(Math.floorMod(cursor + n, count));
                // An even share, the remainder to the legs whose turn comes first; what a leg can't use goes on.
                int share = Math.max(1, budget / (count - n));
                int done = moveLevel(leg, share, level, priority);
                budget -= done;
                total += done;
            }
        }
        return total;
    }

    private static int moveLevel(Leg leg, int limit, ToIntFunction<StorageKey> level, int priority) {
        int left = limit;
        for (Map.Entry<StorageKey, Long> entry : leg.from().list().entrySet()) {
            if (left <= 0) {
                break;
            }
            StorageKey key = entry.getKey();
            if (!leg.route().matches(key) || level.applyAsInt(key) != priority) {
                continue;
            }
            long fits = leg.to().insert(key, Math.min(left, entry.getValue()), true);
            if (fits <= 0) {
                continue;
            }
            long taken = leg.from().extract(key, fits, false);
            long put = leg.to().insert(key, taken, false);
            if (put < taken) {
                leg.from().insert(key, taken - put, false);
            }
            left -= (int) put;
        }
        return limit - left;
    }

    // --- Saving a list of routes ---

    public static void save(ValueOutput output, String name, List<Route> routes) {
        ValueOutput.ValueOutputList list = output.childrenList(name);
        for (Route route : routes) {
            ValueOutput child = list.addChild();
            child.putInt("source", route.source());
            child.putInt("dest", route.dest());
            child.putBoolean("lists", true);
            route.filter().save(child);
            if (route.shares()) {
                child.putString("mode", "share");
                child.putBoolean("read_write", route.readWrite());
                child.putBoolean("crafting", route.crafting());
                child.putBoolean("bidirectional", route.bidirectional());
            }
        }
    }

    // Routes whose endpoints are below endpoints, up to max. Routes saved before filter lists had one item or none: they
    // load as an allow list of that item, or a deny list of nothing (everything, as before). Routes saved before modes
    // load as Move.
    public static List<Route> load(ValueInput input, String name, int endpoints, int max) {
        List<Route> routes = new ArrayList<>();
        for (ValueInput child : input.childrenListOrEmpty(name)) {
            int source = child.getIntOr("source", 0), dest = child.getIntOr("dest", 0);
            if (source >= 0 && source < endpoints && dest >= 0 && dest < endpoints && routes.size() < max) {
                PartFilter filter;
                if (child.getBooleanOr("lists", false)) {
                    filter = new PartFilter();
                    filter.load(child);
                } else {
                    ItemStack old = child.read("filter", ItemStack.CODEC).orElse(ItemStack.EMPTY);
                    filter = old.isEmpty() ? everything() : only(old);
                }
                boolean share = child.getStringOr("mode", "move").equals("share");
                routes.add(new Route(source, dest, filter, share ? Mode.SHARE : Mode.MOVE, child.getBooleanOr("read_write", true),
                        child.getBooleanOr("crafting", true), child.getBooleanOr("bidirectional", false)));
            }
        }
        return routes;
    }

    // After endpoint index is removed: routes through it go, later endpoints move down one.
    public static List<Route> withoutEndpoint(List<Route> routes, int index) {
        List<Route> kept = new ArrayList<>();
        for (Route route : routes) {
            if (route.source() != index && route.dest() != index) {
                kept.add(new Route(route.source() > index ? route.source() - 1 : route.source(), route.dest() > index ? route.dest() - 1 : route.dest(),
                        route.filter(), route.mode(), route.readWrite(), route.crafting(), route.bidirectional()));
            }
        }
        return kept;
    }
}
