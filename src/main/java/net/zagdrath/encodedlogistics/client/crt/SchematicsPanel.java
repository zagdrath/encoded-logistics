/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// WRKSCH (HOLDER *ALL or one): the network's schematic holders and their schematics, a row each - Opt, Holder, Output,
// Qty, Kind, R/O (a Midrange System's diskettes), Location - and a row for a holder with none. Options: 4=Remove (into
// the Terminal Desk's drawer; confirmed, all together), 5=Display (what it makes, from what), 8=Locate (its holder
// outlined in the world for ten seconds, its coordinates on the message line).
final class SchematicsPanel extends ListPanel<TerminalLine> {
    private final String holder;

    SchematicsPanel(CrtTerminal screen, String holder) {
        super(screen);
        this.holder = holder.isBlank() ? "*ALL" : holder;
    }

    @Override
    String id() {
        return "WRKSCH";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.sch.title");
    }

    @Override
    int firstRow() {
        return 7;
    }

    @Override
    int pageSize() {
        return 12;
    }

    // A row's key: its first cell (not shown).
    @Override
    Object key(TerminalLine row) {
        return row.cells().isEmpty() ? "" : row.cells().getFirst().text().getString();
    }

    private static String key(Option<TerminalLine> option) {
        TerminalLine row = option.row();
        return row.cells().isEmpty() ? "" : row.cells().getFirst().text().getString();
    }

    @Override
    String defaultOption() {
        return "5";
    }

    @Override
    void shown() {
        reload();
        next();
    }

    private void reload() {
        screen.send(TerminalService.QUERY, "schematics " + holder);
    }

    @Override
    void refresh() {
        reload();
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() != TerminalService.QUERY) {
            return;
        }
        switch (response.topic()) {
            case "schematics" -> setRows(response.lines());
            case "locateschematic" -> {
                if (!response.lines().isEmpty()) {
                    CrtLocate.locate(response.lines().getFirst().text());
                }
                response.message().ifPresent(screen::message);
                next();
            }
            case "removeschematic" -> {
                response.message().ifPresent(screen::message);
                reload();
                next();
            }
            default -> {}
        }
    }

    @Override
    void drawHead(CrtGrid grid) {
        grid.put(3, 0, tr("crt.encodedlogistics.type_options"));
        grid.put(4, 0, tr("crt.encodedlogistics.sch.opts"));
        grid.put(6, 0, "Opt  Holder            Output                 Qty  Kind  R/O Location", CrtGrid.BRIGHT);
        if (rows.isEmpty()) {
            grid.put(8, 5, tr("crt.encodedlogistics.sch.empty"), CrtGrid.DIM);
        }
    }

    // The row without its key.
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        TerminalLine shown = new TerminalLine(row.cells().subList(Math.min(1, row.cells().size()), row.cells().size()), row.attr());
        grid.put(screenRow, 5, shown.text(), (byte) row.attr());
    }

    @Override
    boolean process(List<Option<TerminalLine>> chosen) {
        pending.clear();
        List<Option<TerminalLine>> removing = new ArrayList<>();
        for (Option<TerminalLine> option : chosen) {
            String key = key(option);
            switch (option.option().trim()) {
                case "4" -> removing.add(option);
                case "5" -> then(() -> screen.push(new TextPanel(screen, "DSPSCH", tr("crt.encodedlogistics.sch.display"), "schematic " + key)));
                case "8" -> then(() -> screen.send(TerminalService.QUERY, "locateschematic " + key));
                default -> {
                    pending.clear();
                    return invalid(option);
                }
            }
        }
        if (!removing.isEmpty()) {
            StringBuilder text = new StringBuilder(tr("crt.encodedlogistics.confirm.option", tr("crt.encodedlogistics.confirm.remove"))).append("\n");
            for (Option<TerminalLine> option : removing) {
                List<TerminalLine.Cell> cells = option.row().cells();
                text.append("\n   ").append(cells.size() > 2 ? cells.get(2).text().getString().trim() : "").append("  ")
                        .append(cells.size() > 1 ? cells.get(1).text().getString().trim() : "");
            }
            then(() -> screen.confirm(text.toString(), () -> {
                for (Option<TerminalLine> option : removing) {
                    screen.send(TerminalService.QUERY, "removeschematic " + key(option));
                }
            }, null));
        }
        next();
        return true;
    }
}
