/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.menu.LinePrinterMenu;

// PRINTER (HANDOFF 3; docs/midrange/layouts/printer.txt): the report (1-4), the spooled file for 4 (a name or *LAST;
// F4 lists the network's), the pages it takes, the paper loaded and the printer's status; F6=Print.
public class LinePrinterScreen extends CrtMachineScreen<LinePrinterMenu> {
    private static final String[] REPORTS = { "inventory", "joblog", "devices", "splf" };

    public LinePrinterScreen(LinePrinterMenu menu, Inventory inventory, Component title) {
        super(menu, title);
        fields.add(new Field(4, 22, 1, LinePrinterMenu.FIELD_REPORT, Kind.VALUE, "1"));
        fields.add(new Field(9, 22, 10, LinePrinterMenu.FIELD_FILE, Kind.VALUE, "*LAST"));
    }

    @Override
    String panelId() {
        return "PRINTER";
    }

    @Override
    String titleKey() {
        return "crt.encodedlogistics.printer.title";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.printer.keys");
    }

    @Override
    @Nullable List<String> listFor(Field field) {
        if (field.key == LinePrinterMenu.FIELD_FILE) {
            List<String> files = new ArrayList<>(List.of("*LAST"));
            for (String[] file : lines("F")) {
                if (!files.contains(file[1])) {
                    files.add(file[1]);
                }
            }
            return files;
        }
        if (field.key == LinePrinterMenu.FIELD_REPORT) {
            return List.of("1", "2", "3", "4");
        }
        return null;
    }

    @Override
    void body(CrtGrid grid) {
        grid.put(2, 2, tr("crt.encodedlogistics.printer.instructions"), CrtGrid.NORMAL);
        grid.put(4, 2, tr("crt.encodedlogistics.printer.report"), CrtGrid.NORMAL);
        for (int i = 0; i < REPORTS.length; i++) {
            grid.put(4 + i, 26, (i + 1) + "=" + tr("crt.encodedlogistics.printer.report." + REPORTS[i]), CrtGrid.NORMAL);
        }
        grid.put(9, 2, tr("crt.encodedlogistics.printer.file"), CrtGrid.NORMAL);
        grid.put(9, 34, tr("crt.encodedlogistics.printer.file_hint"), CrtGrid.DIM);
        List<String[]> state = lines("P");
        String pages = state.isEmpty() ? "-" : state.getFirst()[1], paper = state.isEmpty() ? "-" : state.getFirst()[2];
        grid.put(11, 2, tr("crt.encodedlogistics.printer.pages"), CrtGrid.NORMAL);
        grid.put(11, 22, pages, CrtGrid.BRIGHT);
        grid.put(11, 33, tr("crt.encodedlogistics.printer.paper_loaded"), CrtGrid.NORMAL);
        grid.put(11, 52, tr("crt.encodedlogistics.printer.sheets", paper), CrtGrid.BRIGHT);
        grid.put(12, 2, tr("crt.encodedlogistics.printer.status"), CrtGrid.NORMAL);
        grid.put(12, 22, state.isEmpty() ? "" : state.getFirst()[3], CrtGrid.BRIGHT);
        List<String[]> problem = lines("X");
        if (!problem.isEmpty()) {
            grid.put(13, 22, cut(problem.getFirst()[1], 56), CrtGrid.DIM);
        }
        grid.put(14, 2, tr("crt.encodedlogistics.hint.paper"), CrtGrid.DIM);
    }

    @Override
    boolean machineKey(int key) {
        if (key != 6) {
            return false;
        }
        submit();
        button(LinePrinterMenu.BUTTON_PRINT);
        return true;
    }
}
