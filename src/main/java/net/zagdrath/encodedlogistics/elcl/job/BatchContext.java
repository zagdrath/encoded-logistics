/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.elcl.exec.Authority;
import net.zagdrath.encodedlogistics.elcl.exec.ElclContext;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// Where a batch job's commands run (OS.md 6): its network, as the user who submitted it (or created its schedule entry
// or trigger), with that player's Firewall permissions - whether or not they're online. No Firewall: full authority.
public record BatchContext(MinecraftServer server, @Nullable NetworkRef network, String user, @Nullable UUID player, String job) implements ElclContext {
    @Override
    public boolean allowed(RackPermission permission) {
        return Authority.allowed(server, network, null, player, permission);
    }
}
