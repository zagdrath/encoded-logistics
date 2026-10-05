/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.JobData;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// Touching a Display Panel screen (HANDOFF 5): the *DSPTOUCH trigger event, &DATA "DSP01 A 40 12" - the screen's name,
// the region under the point (*NONE outside them all) and the point in canvas px from the top left. At most TOUCHES a
// second for each player.
public final class DisplayTouch {
    private static final int TOUCHES = 4;
    private static final long WINDOW_MS = 1_000;
    private static final Map<UUID, Deque<Long>> RECENT = new HashMap<>();

    private DisplayTouch() {}

    public static boolean touch(ServerLevel level, DisplayPanelBlockEntity master, BlockPos clicked, Vec3 hit, Player player) {
        NetworkRef network = master.network();
        if (network == null || !allowed(player.getUUID())) {
            return false;
        }
        int[] point = DisplayScreens.canvasPoint(master, clicked, hit);
        String region = "*NONE";
        for (DisplayContent.Region candidate : master.displayContent().regions(master.canvasWidth(), master.canvasHeight())) {
            if (candidate.contains(point[0], point[1])) {
                region = candidate.name();
                break;
            }
        }
        ElclEvents.displayTouched(level.getServer(), network, master.name(), region, point[0], point[1]);
        return true;
    }

    private static synchronized boolean allowed(UUID player) {
        long now = System.currentTimeMillis();
        Deque<Long> times = RECENT.computeIfAbsent(player, id -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
            times.pollFirst();
        }
        if (times.size() >= TOUCHES) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    // Whether a screen has touch triggers (then configuring it needs sneaking).
    public static boolean hasTriggers(ServerLevel level, DisplayPanelBlockEntity master) {
        NetworkRef network = master.network();
        SystemData data = network != null ? ElclStore.get(level.getServer()).systems().get(network) : null;
        if (data == null) {
            return false;
        }
        for (JobData.Trigger trigger : data.jobs.triggers.values()) {
            String device = trigger.trigger.device().trim();
            if (trigger.trigger.event().equals("*DSPTOUCH") && (device.isEmpty() || device.equalsIgnoreCase("*ANY") || device.equalsIgnoreCase(master.name()))) {
                return true;
            }
        }
        return false;
    }
}
