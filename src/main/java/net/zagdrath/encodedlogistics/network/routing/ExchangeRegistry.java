/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.blockentity.ExchangeSwitchBlockEntity;

// The Exchange Switches loaded on a server (they keep their chunk loaded while they have members), for the Routers to
// find the exchanges they're members of.
public final class ExchangeRegistry {
    private static final Map<MinecraftServer, Map<GlobalPos, ExchangeSwitchBlockEntity>> LOADED = new WeakHashMap<>();

    private ExchangeRegistry() {}

    public static synchronized void add(MinecraftServer server, GlobalPos pos, ExchangeSwitchBlockEntity exchange) {
        LOADED.computeIfAbsent(server, key -> new LinkedHashMap<>()).put(pos, exchange);
    }

    public static synchronized void remove(MinecraftServer server, GlobalPos pos, ExchangeSwitchBlockEntity exchange) {
        Map<GlobalPos, ExchangeSwitchBlockEntity> loaded = LOADED.get(server);
        if (loaded != null) {
            loaded.remove(pos, exchange);
        }
    }

    public static synchronized List<ExchangeSwitchBlockEntity> all(MinecraftServer server) {
        Map<GlobalPos, ExchangeSwitchBlockEntity> loaded = LOADED.get(server);
        return loaded == null ? List.of() : new ArrayList<>(loaded.values());
    }

    // The exchanges a Router is an approved member of.
    public static List<ExchangeSwitchBlockEntity> of(MinecraftServer server, RoutingDomain.RouterId router) {
        List<ExchangeSwitchBlockEntity> found = new ArrayList<>();
        for (ExchangeSwitchBlockEntity exchange : all(server)) {
            if (exchange.member(router) != null) {
                found.add(exchange);
            }
        }
        return found;
    }

    // Items crossed between two members (an exchange hop on a path): counted where they both are.
    public static void traffic(MinecraftServer server, RoutingDomain.RouterId a, RoutingDomain.RouterId b, long amount, long now) {
        for (ExchangeSwitchBlockEntity exchange : all(server)) {
            exchange.traffic(a, b, amount, now);
        }
    }
}
