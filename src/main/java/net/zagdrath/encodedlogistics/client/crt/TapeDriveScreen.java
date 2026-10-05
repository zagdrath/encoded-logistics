/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.menu.TapeDriveMenu;

// TAPDRV (HANDOFF 3; docs/midrange/layouts/tapdrv.txt): the Tape Drive's name and status, its reel (volume, items on it,
// last access); 4=Unload (it rewinds first), 5=Display contents (F12 back), 7=Rewind.
public class TapeDriveScreen extends CrtMachineScreen<TapeDriveMenu> {
    public TapeDriveScreen(TapeDriveMenu menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    @Override
    String panelId() {
        return "TAPDRV";
    }

    @Override
    String titleKey() {
        return "crt.encodedlogistics.tapdrv.title";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.tapdrv.keys");
    }

    @Override
    boolean commandLine() {
        return true;
    }

    @Override
    void body(CrtGrid grid) {
        List<String[]> state = lines("S");
        String status = state.isEmpty() ? "OFFLINE" : state.getFirst()[1];
        String shown = tr("crt.encodedlogistics.tapdrv.state." + status.toLowerCase(Locale.ROOT));
        if (status.equals("READY") && state.getFirst()[2].equals("1")) {
            shown += "  " + tr("crt.encodedlogistics.tapdrv.load_point");
        }
        grid.put(2, 2, tr("crt.encodedlogistics.drive.device"), CrtGrid.NORMAL);
        grid.put(2, 21, menu.opening().device(), CrtGrid.BRIGHT);
        grid.put(2, 33, tr("crt.encodedlogistics.drive.status"), CrtGrid.NORMAL);
        grid.put(2, 48, shown, CrtGrid.BRIGHT);
        List<String[]> reel = lines("R");
        grid.put(4, 2, tr("crt.encodedlogistics.tapdrv.reel"), CrtGrid.NORMAL);
        if (reel.isEmpty()) {
            grid.put(4, 21, tr("crt.encodedlogistics.tapdrv.none"), CrtGrid.DIM);
        } else {
            String[] r = reel.getFirst();
            grid.put(4, 21, r[1] + "   " + tr("crt.encodedlogistics.tapdrv.kind"), CrtGrid.BRIGHT);
            grid.put(5, 2, tr("crt.encodedlogistics.tapdrv.used"), CrtGrid.NORMAL);
            grid.put(5, 21, tr("crt.encodedlogistics.drive.items", DiskDriveScreen.number(r[2]), DiskDriveScreen.number(r[3]), r[4]) + "  "
                    + tr("crt.encodedlogistics.tapdrv.cold"), CrtGrid.BRIGHT);
            grid.put(6, 2, tr("crt.encodedlogistics.tapdrv.last_access"), CrtGrid.NORMAL);
            long last = Long.parseLong(r[5]);
            grid.put(6, 21, last < 0 ? tr("crt.encodedlogistics.tapdrv.never") : FunctionKeys.clock(last), CrtGrid.BRIGHT);
        }
        grid.put(8, 2, "Opt", CrtGrid.NORMAL);
        grid.put(8, 10, tr("crt.encodedlogistics.tapdrv.options"), CrtGrid.NORMAL);
        options(List.of(new int[] { 8, 6, 0 }));
        if (!lines("V").isEmpty()) {
            DiskDriveScreen.contents(grid, lines("C"));
        } else {
            grid.put(10, 2, tr("crt.encodedlogistics.hint.reel"), CrtGrid.DIM);
        }
    }

    @Override
    boolean back() {
        if (lines("V").isEmpty()) {
            return false;
        }
        button(TapeDriveMenu.BUTTON_BACK);
        return true;
    }
}
