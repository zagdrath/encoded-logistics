/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.menu.SchedulerCoreMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackScheduler;

// Which job hosts (Scheduler Cores, racks' Schedulers) a player may see and cancel jobs on: the Core whose screen they
// have open, or any serving the network of the terminal they have open.
final class JobAccess {
    private JobAccess() {}

    static @Nullable JobHost core(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level) || !player.containerMenu.stillValid(player)) {
            return null;
        }
        JobHost host = JobHost.at(level, pos);
        if (host == null) {
            return null;
        }
        if (player.containerMenu instanceof SchedulerCoreMenu menu && menu.pos().equals(pos)) {
            return host;
        }
        if (player.containerMenu instanceof AccessTerminalMenu menu) {
            NetworkRef terminal = ControllerStructures.networkOf(level, menu.pos());
            NetworkRef served = host instanceof RackScheduler rack ? rack.network() : ControllerStructures.networkOf(level, pos);
            return terminal != null && terminal.equals(served) ? host : null;
        }
        return null;
    }
}
