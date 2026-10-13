/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// WRKDIR DIR() (docs/routing/layouts/wrkdir.txt): a Directory Server's network's devices by their qualified names,
// published or private, with their lookups in the last hour; under them the latest lookups made from this network.
// 2=Publish, 3=Unpublish (CHGDIRENT), 5=Display (RTVDIRENT's answer, on the message line). F10 shows only the lookups,
// a page of them (again: back).
final class WrkDirPanel extends OsListPanel {
    private static final int LOOKUPS_ROW = 12, LOOKUP_ROWS = 4;
    private final String directory;
    private boolean lookupsOnly;
    private TerminalLine head;
    private final List<TerminalLine> lookups = new ArrayList<>();

    WrkDirPanel(CrtTerminal screen, String directory) {
        super(screen);
        this.directory = directory;
    }

    @Override
    String id() {
        return "WRKDIR";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrkdir.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrkdir");
    }

    @Override
    String query() {
        return "directory " + directory;
    }

    @Override
    String legend() {
        return lookupsOnly ? tr("crt.encodedlogistics.wrkdir.opts_lookups") : tr("crt.encodedlogistics.wrkdir.opts");
    }

    @Override
    String heading() {
        return lookupsOnly ? tr("crt.encodedlogistics.wrkdir.cols_lookups") : tr("crt.encodedlogistics.wrkdir.cols");
    }

    @Override
    String defaultOption() {
        return "5";
    }

    // The devices fit above the latest lookups (F10: a page of lookups).
    @Override
    int pageSize() {
        return lookupsOnly ? 13 : LOOKUPS_ROW - 1 - firstRow();
    }

    @Override
    void setRows(List<TerminalLine> rows) {
        head = rows.isEmpty() ? null : rows.getFirst();
        lookups.clear();
        List<TerminalLine> devices = new ArrayList<>();
        for (TerminalLine row : rows.isEmpty() ? rows : rows.subList(1, rows.size())) {
            if (cell(row, 0).equals("*LOOKUP")) {
                lookups.add(row);
            } else {
                devices.add(row);
            }
        }
        super.setRows(lookupsOnly ? List.copyOf(lookups) : devices);
    }

    // A lookup's result as the screen words it.
    private static String result(TerminalLine lookup) {
        String refused = cell(lookup, 2);
        if (refused.isEmpty()) {
            return tr("crt.encodedlogistics.wrkdir.resolved", cell(lookup, 3));
        }
        return refused + " " + tr(refused.equals("ELC2604") ? "crt.encodedlogistics.wrkdir.denied" : "crt.encodedlogistics.wrkdir.no_route");
    }

    @Override
    void drawTop(CrtGrid grid) {
        if (head == null) {
            return;
        }
        grid.put(2, 1, tr("crt.encodedlogistics.wrkdir.directory"));
        grid.put(2, 15, cell(head, 0) + " (" + cell(head, 1) + ")");
        grid.put(2, 33, tr("crt.encodedlogistics.wrkdir.published"));
        grid.put(2, 46, cell(head, 2));
        grid.put(2, 50, tr("crt.encodedlogistics.wrkdir.cache"));
        grid.put(2, 59, tr("crt.encodedlogistics.wrkdir.cache_value", cell(head, 3), cell(head, 4)));
    }

    @Override
    void draw(CrtGrid grid) {
        super.draw(grid);
        if (lookupsOnly) {
            return;
        }
        grid.put(LOOKUPS_ROW, 1, tr("crt.encodedlogistics.wrkdir.recent"));
        for (int i = 0; i < LOOKUP_ROWS && i < lookups.size(); i++) {
            grid.put(LOOKUPS_ROW + 1 + i, 3, CrtGrid.pad(cell(lookups.get(i), 1), 18));
            grid.put(LOOKUPS_ROW + 1 + i, 22, result(lookups.get(i)));
        }
    }

    // qualified name, type, status, lookups; or (F10) a lookup
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        if (lookupsOnly) {
            grid.put(screenRow, 5, CrtGrid.pad(cell(row, 1), 18));
            grid.put(screenRow, 24, result(row));
            return;
        }
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 0), 18));
        grid.put(screenRow, 24, CrtGrid.pad(cell(row, 1), 6));
        grid.put(screenRow, 31, CrtGrid.pad(cell(row, 2), 12));
        String count = cell(row, 3);
        grid.put(screenRow, 49 - count.length(), count);
    }

    @Override
    boolean functionKey(int f) {
        if (f == 10) {
            lookupsOnly = !lookupsOnly;
            shown();
            return true;
        }
        return false;
    }

    @Override
    boolean option(String code, TerminalLine row) {
        if (lookupsOnly) {
            return false;
        }
        String device = cell(row, 4);
        switch (code) {
            case "2", "3" -> then(() -> screen.runCommand("CHGDIRENT DEV(" + device + ") PUBLISH(" + (code.equals("2") ? "*YES" : "*NO") + ")"));
            case "5" -> then(() -> screen.runCommand("RTVDIRENT NAME(" + cell(row, 0) + ")"));
            default -> {
                return false;
            }
        }
        return true;
    }

    // Nothing here deletes.
    @Override
    String deleteOption() {
        return "";
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "";
    }
}
