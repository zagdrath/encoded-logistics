/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.zagdrath.encodedlogistics.Config;

// A Server Rack isn't broken by a stray left click (rackBreakConfirm): a player sneaks while breaking it, or left-clicks
// it twice within CONFIRM_TICKS - the first click arms it (mining does nothing meanwhile), the second confirms, and it
// then mines as usual. In creative, where a click breaks at once, the first break arms it and a second confirms
// (holding the button does the same, a creative break repeating). Kept per side, so the client's prediction and the
// server agree without a packet; keyed by the rack's master, so any of its blocks counts.
public final class RackBreakGuard {
    // The second click within this of the first; a confirmed rack stays confirmed this long to mine it.
    private static final int CONFIRM_TICKS = 20, CONFIRMED_TICKS = 600;

    private record Arm(BlockPos rack, long at, boolean confirmed) {}

    private static final Map<UUID, Arm> CLIENT = new HashMap<>(), SERVER = new HashMap<>();

    private RackBreakGuard() {}

    private static Map<UUID, Arm> side(Player player) {
        return player.level().isClientSide() ? CLIENT : SERVER;
    }

    // Whether the player may break the rack now: sneaking, confirmed, or the guard off.
    public static boolean allowed(Player player, BlockPos rack) {
        if (!Config.RACK_BREAK_CONFIRM.getAsBoolean() || player.isShiftKeyDown()) {
            return true;
        }
        Arm arm = side(player).get(player.getUUID());
        return arm != null && arm.confirmed() && arm.rack().equals(rack) && player.level().getGameTime() - arm.at() <= CONFIRMED_TICKS;
    }

    // A left click on the rack (a break attempt in creative): arms it, or confirms it if armed just before.
    public static void clicked(Player player, BlockPos rack) {
        if (allowed(player, rack)) {
            return;
        }
        long now = player.level().getGameTime();
        Map<UUID, Arm> arms = side(player);
        Arm arm = arms.get(player.getUUID());
        if (arm != null && !arm.confirmed() && arm.rack().equals(rack) && now - arm.at() <= CONFIRM_TICKS) {
            arms.put(player.getUUID(), new Arm(rack.immutable(), now, true));
            return;
        }
        arms.put(player.getUUID(), new Arm(rack.immutable(), now, false));
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.encodedlogistics.rack.break_confirm"));
        }
    }

    // Broken (or the player gone): nothing armed.
    public static void clear(Player player) {
        side(player).remove(player.getUUID());
    }
}
