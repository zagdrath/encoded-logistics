/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.elcl.exec.Authority;
import net.zagdrath.encodedlogistics.elcl.exec.ElclContext;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Where a command runs: the server, the network the desk reaches (null while it's offline), the desk itself (its
// drawer; null for a desk-less caller), and the player at it. Jobs are known on a network by a 4-digit number.
public record TerminalContext(MinecraftServer server, @Nullable NetworkRef network, @Nullable TerminalDeskBlockEntity desk, ServerPlayer player) implements ElclContext {
    public @Nullable NetworkStorage storage() {
        return network != null ? ControllerStructures.sharedStorageOf(server, network, false) : null;
    }

    // The user commands run as: the player's name, upper case (ZAGDRATH).
    @Override
    public String user() {
        return player.getName().getString().toUpperCase(Locale.ROOT);
    }

    // Whether the network's Firewall lets the player do that (always, without one, or at SECLVL 10).
    @Override
    public boolean allowed(RackPermission permission) {
        return Authority.allowed(server, network, player, player.getUUID(), permission);
    }

    // A job's number on this network ("0042").
    public String jobNumber(UUID job) {
        return String.format("%04d", ControllerStructures.jobNumber(server, network, job));
    }

    // The job with that number, or null.
    public @Nullable UUID job(int number) {
        return ControllerStructures.jobByNumber(server, network, number);
    }
}
