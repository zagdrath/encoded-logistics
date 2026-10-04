/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

import java.util.UUID;

import net.minecraft.world.item.Item;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.cmd.Wait;
import net.zagdrath.encodedlogistics.elcl.exec.ElclContext;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The game's async waits (ELCL_SPEC.md 9; DLYJOB's DELAY the VM checks itself):
//  RECALL item, need   an item coming back from tape: done once no recall of it is left, or enough of it is hot
//  CRAFT job           a crafting job: done once it's left every Scheduler on the network (CraftHistory has how)
// A wait whose network has gone is done (the command then fails as it would).
public final class Waits {
    private Waits() {}

    public static boolean done(ElclContext context, Wait wait) {
        if (context.network() == null) {
            return true;
        }
        return switch (wait.kind()) {
            case "RECALL" -> recalled(context, wait);
            case "CRAFT" -> crafted(context, wait);
            default -> true;
        };
    }

    private static boolean recalled(ElclContext context, Wait wait) {
        NetworkStorage storage = ControllerStructures.sharedStorageOf(context.server(), context.network(), false);
        if (storage == null) {
            return true;
        }
        Item item;
        try {
            item = ElclItems.resolve(wait.get("item"));
        } catch (ElclException e) {
            return true;
        }
        if (ElclItems.count(storage, item, "*HOT") >= wait.number("need")) {
            return true;
        }
        for (ItemKey key : ElclItems.keys(storage, item)) {
            if (storage.cold().progress(key) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean crafted(ElclContext context, Wait wait) {
        UUID job;
        try {
            job = UUID.fromString(wait.get("job"));
        } catch (IllegalArgumentException e) {
            return true;
        }
        for (JobHost host : CraftRequests.schedulers(context.server(), context.network())) {
            if (host.job(job) != null) {
                return false;
            }
        }
        return true;
    }
}
