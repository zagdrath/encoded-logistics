/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;

import net.zagdrath.encodedlogistics.display.DisplayPanelBlockEntity;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.device.DisplayDevice;
import net.zagdrath.encodedlogistics.elcl.device.Displays;
import net.zagdrath.encodedlogistics.elcl.job.Triggers;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.store.StoredFileService;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.elcl.sync.FolderSync;
import net.zagdrath.encodedlogistics.midrange.Midranges;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;

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
        DisplayCommands.bind();
        MachineCommands.bind();
        DbCommands.bind();
        StoredFileService.setLive(new LiveFiles());
        Midranges.register();
        Displays.register(system -> {
            ElclDevices.list(system.server(), system.network());
            return new ArrayList<DisplayDevice>(ControllerStructures.onNetwork(system.server(), system.network(), DisplayPanelBlockEntity.class, false)
                    .stream().filter(DisplayPanelBlockEntity::isMaster).toList());
        });
        ParamDef.listLimit(ElclConfig::maxListSize);
        ElclEvents.listen(Triggers::fired);
        if (ElclServices.libraries() instanceof StoredLibraryService libraries) {
            libraries.setListener(new FolderSync());
        }
    }
}
