/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.menu.SchedulerCoreMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;

// Which Scheduler Cores a player may see and cancel jobs on: the one whose screen they have open, or any on the network
// of the terminal they have open.
final class JobAccess {
    private JobAccess() {}

    static @Nullable SchedulerCoreBlockEntity core(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level) || !player.containerMenu.stillValid(player) || !level.isLoaded(pos)
                || !(level.getBlockEntity(pos) instanceof SchedulerCoreBlockEntity core)) {
            return null;
        }
        if (player.containerMenu instanceof SchedulerCoreMenu menu && menu.pos().equals(pos)) {
            return core;
        }
        if (player.containerMenu instanceof AccessTerminalMenu menu && ControllerStructures.get(level).sameNetwork(menu.pos(), pos)) {
            return core;
        }
        return null;
    }
}
