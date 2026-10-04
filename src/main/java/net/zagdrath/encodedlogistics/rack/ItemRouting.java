/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Moving items between networks' storage by route, for the Router (between networks) and the L3 Switch (between
// segments). A route goes from one endpoint (by index) to another, for any item or only its filter's. A second's budget
// is shared between the active routes in turn; with priority levels (the L3 Switch's QoS: 0 high, 1 normal, 2 low),
// every route moves its high-priority items first, then normal, then low.
public final class ItemRouting {
    public static final int HIGH = 0, NORMAL = 1, LOW = 2, LEVELS = 3;

    public record Route(int source, int dest, ItemStack filter) {
        public boolean matches(ItemKey key) {
            return filter.isEmpty() || key.stack().is(filter.getItem());
        }
    }

    // An active route this second: its storages at both ends.
    public record Leg(Route route, NetworkStorage from, NetworkStorage to) {}

    private ItemRouting() {}

    // Moves up to budget items over the legs, starting with the leg at cursor; level gives an item's priority level.
    // Returns how many moved.
    public static int move(List<Leg> legs, int budget, int cursor, ToIntFunction<ItemKey> level) {
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

    private static int moveLevel(Leg leg, int limit, ToIntFunction<ItemKey> level, int priority) {
        int left = limit;
        for (Map.Entry<ItemKey, Long> entry : leg.from().list().entrySet()) {
            if (left <= 0) {
                break;
            }
            ItemKey key = entry.getKey();
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
            if (!route.filter().isEmpty()) {
                child.store("filter", ItemStack.CODEC, route.filter());
            }
        }
    }

    // Routes whose endpoints are below endpoints, up to max.
    public static List<Route> load(ValueInput input, String name, int endpoints, int max) {
        List<Route> routes = new ArrayList<>();
        for (ValueInput child : input.childrenListOrEmpty(name)) {
            int source = child.getIntOr("source", 0), dest = child.getIntOr("dest", 0);
            if (source >= 0 && source < endpoints && dest >= 0 && dest < endpoints && routes.size() < max) {
                routes.add(new Route(source, dest, child.read("filter", ItemStack.CODEC).orElse(ItemStack.EMPTY)));
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
                        route.filter()));
            }
        }
        return kept;
    }
}
