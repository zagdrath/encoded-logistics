/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.exec.Authority;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.RouterAcl;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// Qualified device names across networks (the routing handoff 5): SYSNAME/DEVICE names a device on another network, for
// any ELCL command that takes a device. It resolves when
//  - a Router on the caller's network has a route to SYSNAME's network (its route table),
//  - a Directory Server there is online and publishes DEVICE,
//  - one of those Routers has a CONTROL policy from the caller's network to it, with a path, its ACL permitting *CONTROL,
//  - and the caller has the command's Firewall permission on the target network (checked on the target side).
// Where a name's network is (its route table search) is kept directoryCacheSeconds (until a route or anything networks
// are made of changes); whether the device is still published, the CONTROL policy and its ACL, and the Firewall are
// asked every time. A name on the caller's own network resolves as a plain name.
public final class Directories {
    private static final Map<MinecraftServer, Map<String, Cached>> CACHE = new WeakHashMap<>();

    private Directories() {}

    // Where a name went: its network, the device's own name there, the hops to it, and whether it came from the cache.
    public record Resolution(NetworkRef network, String device, String type, int hops, boolean cached) {
        Resolution fromCache() {
            return new Resolution(network, device, type, hops, true);
        }
    }

    // Why a name didn't resolve: an ELCL message (ELC2603, ELC2604) and its data.
    public static final class Refusal extends Exception {
        private final String id;
        private final String name;

        Refusal(String id, String name) {
            super(id + " " + name, null, false, false);
            this.id = id;
            this.name = name;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }
    }

    private record Cached(NetworkRef network, String device, String type, int hops, long at, long key) {}

    // The Directory Servers (block or rack) online on a network.
    public static List<Directory.Host> on(MinecraftServer server, @Nullable NetworkRef network) {
        List<Directory.Host> hosts = new ArrayList<>();
        if (network == null) {
            return hosts;
        }
        for (Directory.Host host : ControllerStructures.onNetwork(server, network, Directory.Host.class, true)) {
            if (host.isOnline()) {
                hosts.add(host);
            }
        }
        for (RackDevice device : ControllerStructures.rackDevicesServing(server, network)) {
            if (device instanceof Directory.Host host && host.isOnline()) {
                hosts.add(host);
            }
        }
        return hosts;
    }

    public static boolean published(MinecraftServer server, NetworkRef network, String device) {
        for (Directory.Host host : on(server, network)) {
            if (host.directory().isPublished(device)) {
                return true;
            }
        }
        return false;
    }

    // The Routers on a network (in its own racks, serving it).
    public static List<RouterDevice> routers(MinecraftServer server, NetworkRef network) {
        List<RouterDevice> routers = new ArrayList<>();
        for (RackDevice device : ControllerStructures.rackDevicesServing(server, network)) {
            if (device instanceof RouterDevice router && router.isOnline() && network.equals(router.network(server, 0))) {
                routers.add(router);
            }
        }
        return routers;
    }

    // Names the cache holds for a network's callers.
    public static int cached(MinecraftServer server, @Nullable NetworkRef caller) {
        if (caller == null) {
            return 0;
        }
        String prefix = key(caller, "");
        int count = 0;
        synchronized (CACHE) {
            for (String key : CACHE.getOrDefault(server, Map.of()).keySet()) {
                if (key.startsWith(prefix)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String key(NetworkRef caller, String name) {
        return caller.dimension().identifier() + "#" + caller.id() + "#" + name;
    }

    private static long epoch(MinecraftServer server) {
        return ControllerStructures.cacheKey(server) * 31 + RoutingDomain.get(server).version();
    }

    public static Resolution resolve(MinecraftServer server, @Nullable NetworkRef caller, String qualified, @Nullable Player player, @Nullable UUID id,
            RackPermission permission) throws Refusal {
        String name = qualified.trim().toUpperCase(Locale.ROOT);
        int slash = name.indexOf('/');
        if (caller == null || slash <= 0 || slash == name.length() - 1) {
            throw new Refusal("ELC2603", name);
        }
        String system = name.substring(0, slash), device = name.substring(slash + 1);
        long now = server.overworld().getGameTime();
        // On its own network: a plain name.
        if (RouterDevice.networkName(server, caller).equalsIgnoreCase(system)) {
            ElclDevices.Device local = ElclDevices.find(server, caller, device);
            if (local == null) {
                throw new Refusal("ELC2603", name);
            }
            return new Resolution(caller, local.name(), local.type(), 0, false);
        }
        long epoch = epoch(server);
        Cached cached;
        synchronized (CACHE) {
            cached = CACHE.computeIfAbsent(server, k -> new HashMap<>()).get(key(caller, name));
        }
        Resolution resolution;
        if (cached != null && cached.key() == epoch && now - cached.at() <= Config.DIRECTORY_CACHE_SECONDS.getAsInt() * 20L) {
            try {
                resolution = verify(server, caller, name, cached.network(), device, cached.hops(), now).fromCache();
            } catch (Refusal refusal) {
                record(server, caller, name, refusal.id(), 0, now);
                throw refusal;
            }
        } else {
            try {
                resolution = lookUp(server, caller, name, system, device, now);
            } catch (Refusal refusal) {
                record(server, caller, name, refusal.id(), 0, now);
                throw refusal;
            }
            synchronized (CACHE) {
                CACHE.computeIfAbsent(server, k -> new HashMap<>()).put(key(caller, name),
                        new Cached(resolution.network(), resolution.device(), resolution.type(), resolution.hops(), now, epoch));
            }
        }
        if (!Authority.allowed(server, resolution.network(), player, id, permission)) {
            record(server, caller, name, "ELC2604", 0, now);
            throw new Refusal("ELC2604", name);
        }
        record(server, caller, name, "", resolution.hops(), now);
        for (Directory.Host host : on(server, resolution.network())) {
            host.directory().counted(resolution.device(), now);
        }
        return resolution;
    }

    private static Resolution lookUp(MinecraftServer server, NetworkRef caller, String name, String system, String device, long now) throws Refusal {
        List<RouterDevice> routers = routers(server, caller);
        NetworkRef target = null;
        int hops = 0;
        for (RouterDevice router : routers) {
            for (RouterDevice.TableEntry entry : router.table()) {
                if (entry.name().equalsIgnoreCase(system)) {
                    target = entry.dest();
                    hops = entry.hops();
                    break;
                }
            }
            if (target != null) {
                break;
            }
        }
        if (target == null) {
            throw new Refusal("ELC2603", name);
        }
        return verify(server, caller, name, target, device, hops, now);
    }

    // What's asked every time: the device is published and there, and a CONTROL policy (its ACL permitting) goes there.
    private static Resolution verify(MinecraftServer server, NetworkRef caller, String name, NetworkRef target, String device, int hops, long now)
            throws Refusal {
        if (!published(server, target, device)) {
            throw new Refusal("ELC2603", name);
        }
        ElclDevices.Device found = ElclDevices.find(server, target, device);
        if (found == null) {
            throw new Refusal("ELC2603", name);
        }
        boolean byDefault = Config.ACL_DEFAULT_PERMIT.getAsBoolean();
        for (RouterDevice router : routers(server, caller)) {
            for (ItemRouting.Route route : router.routes()) {
                if (!route.controls() || !target.equals(route.target()) || !caller.equals(router.network(server, route.source()))) {
                    continue;
                }
                boolean reachable = route.remote() ? router.path(server, route) != null : router.network(server, route.dest()) != null;
                RouterAcl acl = route.acl().isEmpty() ? null : router.acl(route.acl());
                if (reachable && (acl == null || acl.permitsControl(byDefault, now))) {
                    return new Resolution(target, found.name(), found.type(), hops, false);
                }
            }
        }
        throw new Refusal("ELC2604", name);
    }

    // A lookup made from a network: in its own Directory Servers' Lookups.
    private static void record(MinecraftServer server, NetworkRef caller, String name, String refused, int hops, long now) {
        for (Directory.Host host : on(server, caller)) {
            host.directory().looked(new Directory.Lookup(name, refused, hops, now));
        }
    }
}
