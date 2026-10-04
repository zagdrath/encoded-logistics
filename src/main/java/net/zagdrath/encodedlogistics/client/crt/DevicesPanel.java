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

// WRKDEV: every device on the network - Opt, Type, Device, Location (a rack's units under it), Lanes, Status (Online,
// Offline dim, a fault or what it's busy with bright). Options: 5=Display (its details), 8=Locate (it's picked out in
// the world for ten seconds).
final class DevicesPanel extends ListPanel<DevicesPanel.Row> {
    record Row(int index, TerminalLine line) {}

    DevicesPanel(CrtScreen screen) {
        super(screen);
    }

    @Override
    String id() {
        return "WRKDEV";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.dev.title");
    }

    @Override
    int firstRow() {
        return 7;
    }

    @Override
    int pageSize() {
        return 12;
    }

    @Override
    Object key(Row row) {
        return row.index();
    }

    @Override
    String defaultOption() {
        return "5";
    }

    @Override
    void shown() {
        screen.send(TerminalService.QUERY, "devices");
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() != TerminalService.QUERY) {
            return;
        }
        if (response.topic().equals("devices")) {
            List<Row> list = new ArrayList<>();
            for (int i = 0; i < response.lines().size(); i++) {
                list.add(new Row(i, response.lines().get(i)));
            }
            setRows(list);
        } else if (response.topic().equals("locate") && !response.lines().isEmpty()) {
            CrtLocate.locate(response.lines().getFirst().text());
        }
    }

    @Override
    void drawHead(CrtGrid grid) {
        grid.put(3, 0, tr("crt.encodedlogistics.type_options"));
        grid.put(4, 0, tr("crt.encodedlogistics.dev.opts"));
        grid.put(6, 0, "Opt  Type        Device                  Location           Lanes    Status", CrtGrid.BRIGHT);
    }

    @Override
    void drawRow(CrtGrid grid, int screenRow, Row row) {
        grid.put(screenRow, 5, row.line().text(), (byte) row.line().attr());
    }

    @Override
    boolean process(List<Option<Row>> chosen) {
        for (Option<Row> option : chosen) {
            int index = option.row().index();
            switch (option.option()) {
                case "5" -> {
                    screen.push(new TextPanel(screen, "DSPDEV", tr("crt.encodedlogistics.dev.display"), "device " + index));
                    return true;
                }
                case "8" -> screen.send(TerminalService.QUERY, "locate " + index);
                default -> {
                    screen.message(tr("crt.encodedlogistics.msg.invalid_option", option.option()));
                    return true;
                }
            }
        }
        return true;
    }
}
