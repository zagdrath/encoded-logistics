/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;

// Authority in the Terminal OS (OS.md 6): the Firewall's per-player permissions, for a player at a terminal (operators
// may do everything) or by id for a batch job (its submitter, online or not). No Firewall: full authority. SECLVL 10:
// the Firewall isn't asked (everyone has full authority in the OS; it still guards the blocks themselves).
public final class Authority {
    // Nobody: a player id no Firewall entry has, so its default policy applies.
    private static final UUID NOBODY = new UUID(0, 0);

    private Authority() {}

    public static boolean allowed(MinecraftServer server, @Nullable NetworkRef network, @Nullable Player player, @Nullable UUID id,
            RackPermission permission) {
        if (network != null && !ElclServices.users().checksAuthority(new ElclSystem(server, network))) {
            return true;
        }
        if (player != null) {
            return NetworkAccess.allowed(server, network, player, permission);
        }
        FirewallDevice firewall = ControllerStructures.firewall(server, network);
        if (firewall == null || !firewall.isOnline() && !Config.FIREWALL_FAIL_CLOSED.getAsBoolean()) {
            return true;
        }
        return firewall.allows(id != null ? id : NOBODY, permission);
    }
}
