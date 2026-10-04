/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackScheduler;

// The Compute Server (2U): computeServerThreads crafting threads for its rack's Scheduler (RackScheduler).
public class ComputeServerDevice extends ServerDevice {
    public ComputeServerDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public double drain() {
        return Config.COMPUTE_SERVER_DRAIN.getAsDouble();
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        RackScheduler scheduler = scheduler();
        boolean active = scheduler != null && scheduler.active();
        return List.of(
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.server.threads"),
                        Component.literal(Integer.toString(Config.COMPUTE_SERVER_THREADS.getAsInt()))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.server.threads_used"),
                        Component.literal(active ? scheduler.threadsUsed() + " / " + scheduler.threads() : "—")));
    }
}
