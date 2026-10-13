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
import net.minecraft.world.entity.player.Player;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.routing.Directories;
import net.zagdrath.encodedlogistics.network.routing.Directory;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// Directory Servers from ELCL (the routing handoff 5, 6): looking a qualified name up (RTVDIRENT), publishing a device
// (CHGDIRENT) and WRKDIR's rows.
public final class DirectoryCommands {
    private DirectoryCommands() {}

    static void bind() {
        CommandRegistry.bind("RTVDIRENT", call -> {
            ElclContext context = OsCommands.context(call);
            String name = call.text("NAME").trim().toUpperCase(Locale.ROOT);
            Player player = context instanceof TerminalContext terminal ? terminal.player() : null;
            Directories.Resolution resolution;
            try {
                resolution = Directories.resolve(context.server(), context.network(), name, player, OsCommands.player(call), RackPermission.VIEW);
            } catch (Directories.Refusal refusal) {
                throw new ElclException(refusal.id(), refusal.name());
            }
            if (resolution.cached()) {
                call.send(ElclMessage.of("ELC2608", name));
            }
            call.returns("RTNTYPE", resolution.type());
            call.returns("RTNNETWORK", RouterDevice.networkName(context.server(), resolution.network()));
            call.returns("RTNHOPS", (long) resolution.hops());
        });
        CommandRegistry.bind("CHGDIRENT", call -> {
            ElclContext context = OsCommands.context(call);
            if (context.network() == null) {
                throw new ElclException("ELC1302", "*NETWORK");
            }
            String name = call.text("DEV").trim().toUpperCase(Locale.ROOT);
            if (ElclDevices.find(context.server(), context.network(), name) == null) {
                throw new ElclException("ELC1301", name);
            }
            List<Directory.Host> hosts = Directories.on(context.server(), context.network());
            if (hosts.isEmpty()) {
                throw new ElclException("ELC2616");
            }
            boolean publish = call.text("PUBLISH").trim().equalsIgnoreCase("*YES");
            for (Directory.Host host : hosts) {
                host.directory().setPublished(name, publish);
                host.directoryChanged();
            }
            call.send(ElclMessage.of(publish ? "ELC2617" : "ELC2618", name));
        });
    }

    // The Directory Server on the caller's network a name picks (*FIRST: the first one).
    private static Directory.@Nullable Host host(MinecraftServer server, @Nullable NetworkRef network, String name) {
        for (Directory.Host host : Directories.on(server, network)) {
            if (name.equals("*FIRST") || host.deviceName().equalsIgnoreCase(name)) {
                return host;
            }
        }
        return null;
    }

    // WRKDIR: the directory (its name, network, published, cached names, cache seconds), its network's devices
    // (qualified name, type, *PUBLISHED / *PRIVATE, lookups in the last hour), then the latest lookups (*LOOKUP, the
    // name, "" or the message that refused it, hops).
    public static List<TerminalLine> rows(ElclContext context, String name) throws ElclException {
        MinecraftServer server = context.server();
        NetworkRef network = context.network();
        Directory.Host host = host(server, network, name.toUpperCase(Locale.ROOT));
        if (host == null || network == null) {
            throw new ElclException("ELC2616");
        }
        long now = server.overworld().getGameTime();
        String system = RouterDevice.networkName(server, network);
        List<TerminalLine> rows = new ArrayList<>();
        rows.add(row(host.deviceName(), system, Integer.toString(host.directory().published().size()), Integer.toString(Directories.cached(server, network)),
                Integer.toString(Config.DIRECTORY_CACHE_SECONDS.getAsInt())));
        for (ElclDevices.Device device : ElclDevices.list(server, network)) {
            boolean published = host.directory().isPublished(device.name());
            rows.add(row(system + "/" + device.name(), device.type(), published ? "*PUBLISHED" : "*PRIVATE", Long.toString(host.directory().lookups(device.name(), now)),
                    device.name()));
        }
        for (Directory.Lookup lookup : host.directory().recent()) {
            rows.add(row("*LOOKUP", lookup.name(), lookup.refused(), Integer.toString(lookup.hops())));
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
}
