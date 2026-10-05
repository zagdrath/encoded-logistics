/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.zagdrath.encodedlogistics.client.screen.CraftAmountScreen;
import net.zagdrath.encodedlogistics.client.screen.CraftPlanScreen;
import net.zagdrath.encodedlogistics.client.screen.JobStatusScreen;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.net.CraftPlanPayload;
import net.zagdrath.encodedlogistics.net.JobStatusPayload;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// The crafting request screens, opened over a terminal (whose menu stays open underneath): Craft Amount, then Craft Plan
// once the server answers, then Job Status once the job starts. Each goes back to the terminal when closed. Called from
// the terminal screen and the payload handlers, client side only.
public final class CraftingClient {
    private CraftingClient() {}

    public static void openAmount(Screen terminal, AccessTerminalMenu menu, StorageKey key) {
        Minecraft.getInstance().gui.setScreen(new CraftAmountScreen(terminal, menu, key));
    }

    // A plan came: shown on the plan screen (opened from the amount screen), or the job it started is shown.
    public static void plan(CraftPlanPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();
        if (screen instanceof CraftAmountScreen amount && amount.containerId() == payload.containerId()) {
            minecraft.gui.setScreen(new CraftPlanScreen(amount.terminal(), amount.menu(), payload));
        } else if (screen instanceof CraftPlanScreen plan && plan.containerId() == payload.containerId()) {
            if (payload.started().isPresent()) {
                CraftPlanPayload.Started started = payload.started().get();
                minecraft.gui.setScreen(new JobStatusScreen(plan.terminal(), started.core(), started.job()));
            } else {
                plan.update(payload);
            }
        }
    }

    public static void jobStatus(JobStatusPayload payload) {
        if (Minecraft.getInstance().gui.screen() instanceof JobStatusScreen screen) {
            screen.update(payload);
        }
    }
}
