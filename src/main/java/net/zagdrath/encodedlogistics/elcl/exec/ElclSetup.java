/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.job.Triggers;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.elcl.sync.FolderSync;
import net.zagdrath.encodedlogistics.midrange.Midranges;

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
        Midranges.register();
        ParamDef.listLimit(ElclConfig::maxListSize);
        ElclEvents.listen(Triggers::fired);
        if (ElclServices.libraries() instanceof StoredLibraryService libraries) {
            libraries.setListener(new FolderSync());
        }
    }
}
