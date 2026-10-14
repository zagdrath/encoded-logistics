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

    // Whether it can carry out a schematic: one it holds, or (a provider that works from a recipe library) one from
    // the network's recipe libraries (RecipeLibraries) it takes.
    default boolean accepts(Schematic schematic) {
        return schematics().contains(schematic);
    }

    // How busy it is: the tasks it has taken and not yet sent back. Jobs offer each task to the least busy provider that
    // can carry it out first (ties in network order), so identical machines share the work instead of the first ones
    // filling up while the rest sit idle. 0 (the default): always offered first, among the providers that are idle.
    default int load() {
        return 0;
    }

    // Takes the task if it holds the schematic and has room for it now; false leaves the inputs with the job.
    boolean offer(ServerLevel level, CraftTask task);
}
