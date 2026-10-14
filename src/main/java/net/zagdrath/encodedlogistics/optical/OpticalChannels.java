/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.optical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.TopologyCause;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.network.RemoteLink;

// The links optical gear makes between client networks: each up channel of an Optical Multiplexer pair, or of a pair
// of Optical Transport Shelves (a transponder at each end with the same channel), joins the network it was given at one
// end (a node on it, from a Link Card) with the one at the other, carrying at most the channel's lanes after its line's
// loss. NetworkDiscovery follows them as it does a Network Bridge pair's link, so the two become one network; each
// channel is a link of its own, so channels never mix.
//
// The end of a pair that leads it (OpticalLine.leads) sets its pair's channels here every time it looks at its line;
// one that stops looking (taken out, unloaded) has its channels go after EXPIRE_TICKS.
public final class OpticalChannels {
    public record Channel(NodePos a, NodePos b, int lanes) {}

    private record Owned(List<Channel> channels, int refreshed) {}

    private static final int EXPIRE_TICKS = 100;
    private static final Map<MinecraftServer, Map<UUID, Owned>> OWNERS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<NodePos, List<RemoteLink>>> INDEX = new WeakHashMap<>();

    private OpticalChannels() {}

    // Sets an owner's channels; the networks are worked out again when they changed.
    public static synchronized void set(MinecraftServer server, UUID owner, List<Channel> channels) {
        Map<UUID, Owned> owners = OWNERS.computeIfAbsent(server, s -> new HashMap<>());
        Owned old = owners.get(owner);
        owners.put(owner, new Owned(List.copyOf(channels), server.getTickCount()));
        if (old == null ? !channels.isEmpty() : !old.channels().equals(channels)) {
            rebuild(server);
        }
    }

    public static synchronized void clear(MinecraftServer server, UUID owner) {
        Map<UUID, Owned> owners = OWNERS.get(server);
        if (owners != null && owners.remove(owner) != null) {
            rebuild(server);
        }
    }

    // Drops the channels of owners that stopped looking at their lines.
    public static synchronized void tick(MinecraftServer server) {
        Map<UUID, Owned> owners = OWNERS.get(server);
        if (owners == null || server.getTickCount() % 20 != 0) {
            return;
        }
        int now = server.getTickCount();
        if (owners.values().removeIf(owned -> now - owned.refreshed() > EXPIRE_TICKS)) {
            rebuild(server);
        }
    }

    // The channel links at a node (NetworkDiscovery).
    public static synchronized List<RemoteLink> links(@Nullable MinecraftServer server, NodePos at) {
        Map<NodePos, List<RemoteLink>> index = server != null ? INDEX.get(server) : null;
        List<RemoteLink> links = index != null ? index.get(at) : null;
        return links != null ? links : List.of();
    }

    // The channel link from a node to another, or null.
    public static @Nullable RemoteLink link(@Nullable MinecraftServer server, NodePos from, NodePos to) {
        for (RemoteLink link : links(server, from)) {
            if (link.target().equals(to)) {
                return link;
            }
        }
        return null;
    }

    private static void rebuild(MinecraftServer server) {
        Map<NodePos, List<RemoteLink>> index = new HashMap<>();
        Map<UUID, Owned> owners = OWNERS.get(server);
        if (owners != null) {
            for (Owned owned : owners.values()) {
                for (Channel channel : owned.channels()) {
                    index.computeIfAbsent(channel.a(), pos -> new ArrayList<>()).add(new RemoteLink(channel.b(), channel.lanes()));
                    index.computeIfAbsent(channel.b(), pos -> new ArrayList<>()).add(new RemoteLink(channel.a(), channel.lanes()));
                }
            }
        }
        Map<NodePos, List<RemoteLink>> before = INDEX.put(server, index);
        // The networks at the ends of every channel that came, went or changed are worked out again.
        Set<NodePos> changed = new HashSet<>();
        Map<NodePos, List<RemoteLink>> was = before != null ? before : Map.of();
        for (Map<NodePos, List<RemoteLink>> side : List.of(was, index)) {
            side.forEach((pos, links) -> {
                if (!links.equals(side == was ? index.get(pos) : was.get(pos))) {
                    changed.add(pos);
                    links.forEach(link -> changed.add(link.target()));
                }
            });
        }
        if (!changed.isEmpty()) {
            ControllerStructures.topologyChanged(server, changed, TopologyCause.OPTICAL);
        }
    }
}
