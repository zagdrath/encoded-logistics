/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;

// Where the network asks its Firewall (if it has one) whether a player may do something. A network without a Firewall
// lets everyone do everything. An offline Firewall still enforces its rules unless firewallFailClosed is off. Server
// operators (gamemaster level) are never stopped, so a network can't lock everyone out. A block on no network (no
// controller) is open to everyone. SECLVL doesn't change any of this: it only relaxes the Terminal OS.
public final class NetworkAccess {
    private NetworkAccess() {}

    public static boolean allowed(MinecraftServer server, @Nullable NetworkRef network, Player player, RackPermission permission) {
        if (network == null || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return true;
        }
        FirewallDevice firewall = ControllerStructures.firewall(server, network);
        if (firewall == null || !firewall.isOnline() && !Config.FIREWALL_FAIL_CLOSED.getAsBoolean()) {
            return true;
        }
        return firewall.allows(player.getUUID(), permission);
    }

    // For the network the node at pos is on (none: allowed).
    public static boolean allowed(ServerLevel level, BlockPos node, Player player, RackPermission permission) {
        return allowed(level.getServer(), ControllerStructures.networkOf(level, node), player, permission);
    }

    // For a block about to be placed or broken at pos: the network it's on, or any network next to it.
    public static boolean allowedToBuild(ServerLevel level, BlockPos pos, Player player) {
        if (!allowed(level, pos, player, RackPermission.BUILD)) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (!allowed(level, pos.relative(side), player, RackPermission.BUILD)) {
                return false;
            }
        }
        return true;
    }

    // allowed(), telling the player when it isn't.
    public static boolean check(ServerLevel level, BlockPos node, Player player, RackPermission permission) {
        return tell(allowed(level, node, player, permission), player, permission);
    }

    public static boolean check(MinecraftServer server, @Nullable NetworkRef network, Player player, RackPermission permission) {
        return tell(allowed(server, network, player, permission), player, permission);
    }

    // allowed(), telling the player "Access denied: SYSNAME Firewall" when it isn't (rack access and linking).
    public static boolean guard(MinecraftServer server, @Nullable NetworkRef network, Player player, RackPermission permission) {
        boolean allowed = allowed(server, network, player, permission);
        if (!allowed && network != null && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.encodedlogistics.firewall.access_denied", new ElclSystem(server, network).name()));
        }
        return allowed;
    }

    public static boolean guard(ServerLevel level, BlockPos node, Player player, RackPermission permission) {
        return guard(level.getServer(), ControllerStructures.networkOf(level, node), player, permission);
    }

    // At the network a stored link points to (loaded or not; none there: allowed).
    public static boolean guard(MinecraftServer server, GlobalPos pos, Player player, RackPermission permission) {
        ServerLevel level = server.getLevel(pos.dimension());
        return level == null || guard(level, pos.pos(), player, permission);
    }

    // Rack access at a Server Rack: denied, its latch rattles and the player is told.
    public static boolean rack(ServerLevel level, BlockPos rack, Player player) {
        if (guard(level, rack, player, RackPermission.RACK)) {
            return true;
        }
        level.playSound(null, rack, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 1.0F);
        return false;
    }

    public static boolean tell(boolean allowed, Player player, RackPermission permission) {
        if (!allowed && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.encodedlogistics.firewall.denied", permission.label()));
        }
        return allowed;
    }
}
