/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// Where an ELCL command runs, as its executor needs it: the server, the network (null while offline), the user it
// runs as (OS.md 6) and what the Firewall lets that user do.
public interface ElclContext {
    MinecraftServer server();

    @Nullable NetworkRef network();

    String user();

    boolean allowed(RackPermission permission);

    // The job a command runs in, by number; null: the user's interactive job.
    default @Nullable String job() {
        return null;
    }
}
