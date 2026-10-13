/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// What a Directory Server's panel shows and does, in either form (its block, its rack unit): its network's devices
// (click to publish one or not), the networks its Routers reach that have a Directory Server (hops, whether a CONTROL
// policy goes there) and the latest lookups made from its network. And its popup's lines.
public final class DirectoryPanels {
    public static final int ACTION_PUBLISH = 0;

    private DirectoryPanels() {}

    // A network a Directory can look names up on: its name, hops, whether a CONTROL policy goes there.
    public record Remote(NetworkRef network, String name, int hops, boolean control) {}

    public static List<Remote> remotes(MinecraftServer server, @Nullable NetworkRef network) {
        Map<NetworkRef, Remote> found = new LinkedHashMap<>();
        if (network == null) {
            return List.of();
        }
        List<RouterDevice> routers = Directories.routers(server, network);
        for (RouterDevice router : routers) {
            for (RouterDevice.TableEntry entry : router.table()) {
                if (found.containsKey(entry.dest()) || Directories.on(server, entry.dest()).isEmpty()) {
                    continue;
                }
                boolean control = false;
                for (RouterDevice candidate : routers) {
                    for (ItemRouting.Route route : candidate.routes()) {
                        control |= route.controls() && entry.dest().equals(route.target());
                    }
                }
                found.put(entry.dest(), new Remote(entry.dest(), entry.name(), entry.hops(), control));
            }
        }
        return new ArrayList<>(found.values());
    }

    public static void write(Directory.Host host, ValueOutput output, ServerPlayer viewer) {
        MinecraftServer server = viewer.level().getServer();
        NetworkRef network = host.directoryNetwork();
        long now = server.overworld().getGameTime();
        Directory directory = host.directory();
        output.putString("network", network != null ? RouterDevice.networkName(server, network) : "");
        output.putInt("published_count", directory.published().size());
        output.putInt("cached", Directories.cached(server, network));
        output.putInt("cache_seconds", Config.DIRECTORY_CACHE_SECONDS.getAsInt());
        ValueOutput.ValueOutputList devices = output.childrenList("devices");
        for (ElclDevices.Device device : ElclDevices.list(server, network)) {
            ValueOutput row = devices.addChild();
            row.putString("name", device.name());
            row.putString("type", device.type());
            row.putBoolean("published", directory.isPublished(device.name()));
            row.putLong("lookups", directory.lookups(device.name(), now));
        }
        ValueOutput.ValueOutputList remotes = output.childrenList("remotes");
        for (Remote remote : remotes(server, network)) {
            ValueOutput row = remotes.addChild();
            row.putString("name", remote.name());
            row.putInt("hops", remote.hops());
            row.putBoolean("control", remote.control());
        }
        ValueOutput.ValueOutputList lookups = output.childrenList("lookups");
        for (Directory.Lookup lookup : directory.recent()) {
            ValueOutput row = lookups.addChild();
            row.putString("name", lookup.name());
            row.putString("refused", lookup.refused());
            row.putInt("hops", lookup.hops());
        }
    }

    // Publishing needs BUILD on the network.
    public static void handle(Directory.Host host, ServerPlayer player, int action, int value) {
        MinecraftServer server = player.level().getServer();
        NetworkRef network = host.directoryNetwork();
        if (action != ACTION_PUBLISH || network == null || !NetworkAccess.check(server, network, player, RackPermission.BUILD)) {
            return;
        }
        List<ElclDevices.Device> devices = ElclDevices.list(server, network);
        if (value < 0 || value >= devices.size()) {
            return;
        }
        String name = devices.get(value).name();
        host.directory().setPublished(name, !host.directory().isPublished(name));
        host.directoryChanged();
    }

    public static List<RackDeviceInfo.InfoLine> lines(Directory.Host host, MinecraftServer server, boolean rack) {
        int published = host.directory().published().size();
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.directory.published"),
                Component.translatable("hud.encodedlogistics.directory.devices", published)));
        lines.add(rack ? new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.directory.cache"),
                Component.translatable("hud.encodedlogistics.directory.names", Directories.cached(server, host.directoryNetwork())))
                : new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.directory.remote"),
                        Component.translatable("hud.encodedlogistics.directory.networks", remotes(server, host.directoryNetwork()).size())));
        return lines;
    }
}
