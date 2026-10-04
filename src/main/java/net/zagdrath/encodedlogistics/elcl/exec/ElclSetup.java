/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import net.zagdrath.encodedlogistics.elcl.job.Triggers;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.elcl.sync.FolderSync;

// Binds the game side's executors to the built-in commands' schemas (CommandRegistry), once at startup.
public final class ElclSetup {
    private static boolean done;

    private ElclSetup() {}

    public static synchronized void init() {
        if (done) {
            return;
        }
        done = true;
        RedstoneCommands.bind();
        OsCommands.bind();
        ModCommands.bind();
        ElclEvents.listen(Triggers::fired);
        if (ElclServices.libraries() instanceof StoredLibraryService libraries) {
            libraries.setListener(new FolderSync());
        }
    }
}
