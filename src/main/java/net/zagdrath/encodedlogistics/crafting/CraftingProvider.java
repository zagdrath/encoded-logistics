/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.List;

import net.minecraft.server.level.ServerLevel;

// A block entity that carries out schematics for a Scheduler: the Fabricator (crafting) and the Gateway (processing).
// The Scheduler finds them on its network and offers them tasks; what they make goes back with SchedulerJobs.deliver.
public interface CraftingProvider {
    // The schematics it holds.
    List<Schematic> schematics();

    // Takes the task if it holds the schematic and has room for it now; false leaves the inputs with the job.
    boolean offer(ServerLevel level, CraftTask task);
}
