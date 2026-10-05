/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.menu.DiskDriveMenu;

// DSKDRV (HANDOFF 3; docs/midrange/layouts/dskdrv.txt): the Disk Drive's name and status, its pack (the Storage Drive),
// the items and types on it; 4=Unload pack (it spins down first), 5=Display contents (F12 back).
public class DiskDriveScreen extends CrtMachineScreen<DiskDriveMenu> {
    public DiskDriveScreen(DiskDriveMenu menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    @Override
    String panelId() {
        return "DSKDRV";
    }

    @Override
    String titleKey() {
        return "crt.encodedlogistics.dskdrv.title";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.dskdrv.keys");
    }

    @Override
    boolean commandLine() {
        return true;
    }

    static String number(String value) {
        try {
            return String.format(Locale.ROOT, "%,d", Long.parseLong(value));
        } catch (NumberFormatException e) {
            return value;
        }
    }

    @Override
    void body(CrtGrid grid) {
        List<String[]> state = lines("S");
        String status = state.isEmpty() ? "OFFLINE" : state.getFirst()[1];
        grid.put(2, 2, tr("crt.encodedlogistics.drive.device"), CrtGrid.NORMAL);
        grid.put(2, 21, menu.opening().device(), CrtGrid.BRIGHT);
        grid.put(2, 33, tr("crt.encodedlogistics.drive.status"), CrtGrid.NORMAL);
        grid.put(2, 48, tr("crt.encodedlogistics.dskdrv.state." + status.toLowerCase(Locale.ROOT)), CrtGrid.BRIGHT);
        List<String[]> pack = lines("P");
        grid.put(4, 2, tr("crt.encodedlogistics.dskdrv.pack"), CrtGrid.NORMAL);
        if (pack.isEmpty()) {
            grid.put(4, 21, tr("crt.encodedlogistics.dskdrv.none"), CrtGrid.DIM);
        } else {
            String[] p = pack.getFirst();
            grid.put(4, 21, name(p[1]), CrtGrid.BRIGHT);
            grid.put(5, 2, tr("crt.encodedlogistics.dskdrv.capacity"), CrtGrid.NORMAL);
            // Items, B / mB of a fluid or gas, or an Energy Storage Drive's FE: the server sends the amounts formatted.
            String unit = p.length > 7 && !p[7].equals("item") ? "crt.encodedlogistics.drive.amount" : "crt.encodedlogistics.drive.items";
            grid.put(5, 21, tr(unit, p[2], p[3], p[4]), CrtGrid.BRIGHT);
            grid.put(6, 2, tr("crt.encodedlogistics.dskdrv.types"), CrtGrid.NORMAL);
            grid.put(6, 21, number(p[5]) + " / " + number(p[6]), CrtGrid.BRIGHT);
        }
        grid.put(8, 2, "Opt", CrtGrid.NORMAL);
        grid.put(8, 10, tr("crt.encodedlogistics.dskdrv.options"), CrtGrid.NORMAL);
        options(List.of(new int[] { 8, 6, 0 }));
        if (!lines("V").isEmpty()) {
            contents(grid, lines("C"));
        } else {
            grid.put(10, 2, tr("crt.encodedlogistics.hint.pack"), CrtGrid.DIM);
        }
    }

    // 5=Display contents: what's on it, most first.
    static void contents(CrtGrid grid, List<String[]> items) {
        grid.put(10, 2, tr("crt.encodedlogistics.drive.contents"), CrtGrid.BRIGHT);
        if (items.isEmpty()) {
            grid.put(11, 4, tr("crt.encodedlogistics.drive.empty"), CrtGrid.DIM);
        }
        for (int i = 0; i < items.size(); i++) {
            grid.put(11 + i, 4, cut(name(items.get(i)[1]), 40), CrtGrid.NORMAL);
            String count = number(items.get(i)[2]);
            grid.put(11 + i, 58 - count.length(), count, CrtGrid.NORMAL);
        }
    }

    @Override
    boolean back() {
        if (lines("V").isEmpty()) {
            return false;
        }
        button(DiskDriveMenu.BUTTON_BACK);
        return true;
    }
}
