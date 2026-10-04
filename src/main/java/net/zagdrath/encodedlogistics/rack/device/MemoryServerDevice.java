/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackScheduler;

// The Memory Server (1U): memoryServerMemory job memory (buffer) for its rack's Scheduler (RackScheduler).
public class MemoryServerDevice extends ServerDevice {
    public MemoryServerDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public double drain() {
        return Config.MEMORY_SERVER_DRAIN.getAsDouble();
    }

    // "32K", "1.5M".
    public static String memory(long amount) {
        if (amount >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1fM", amount / (1024.0 * 1024));
        }
        return amount >= 1024 ? amount / 1024 + "K" : Long.toString(amount);
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        RackScheduler scheduler = scheduler();
        boolean active = scheduler != null && scheduler.active();
        return List.of(
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.server.buffer"),
                        Component.literal(memory(Config.MEMORY_SERVER_MEMORY.getAsInt()))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.server.buffer_used"),
                        Component.literal(active ? memory(scheduler.memoryUsed()) + " / " + memory(scheduler.memory()) : "—"),
                        active ? new RackDeviceInfo.Bar((float) scheduler.memoryUsed() / Math.max(1, scheduler.memory()), RackDeviceInfo.BarStyle.NORMAL)
                                : null));
    }
}
