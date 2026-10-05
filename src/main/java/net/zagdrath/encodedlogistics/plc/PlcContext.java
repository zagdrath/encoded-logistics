/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.plc;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.elcl.exec.Authority;
import net.zagdrath.encodedlogistics.elcl.exec.ElclContext;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// Where a PLC's program runs, as its commands see it: the PLC itself (DEV(*SELF), RTVSNSVAL), its network while it's
// cabled to one (null without), and the player who last loaded the program - whose Firewall authority it runs with.
// Without a network there's no Firewall to ask: everything it can run is allowed.
public record PlcContext(PlcBlockEntity plc, MinecraftServer server, @Nullable NetworkRef network, String user, @Nullable UUID player)
        implements ElclContext {
    @Override
    public boolean allowed(RackPermission permission) {
        return network == null || Authority.allowed(server, network, null, player, permission);
    }

    @Override
    public @Nullable String job() {
        return plc.deviceName().isEmpty() ? PlcBlockEntity.TYPE : plc.deviceName();
    }
}
