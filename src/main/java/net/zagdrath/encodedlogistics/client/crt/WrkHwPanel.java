/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// WRKHW DEV(*ALL | one) (docs/optical/layouts/wrkhw.txt): every chassis on the network, a row for each bay with a part
// (or a failed one) - Device, Slot, Part, Status, Detail - under the network's counts (chassis, cards, faults). F11
// shows every bay, empty ones too (again: back). Options: 5=Display (the part, as its popup has it), 8=Locate (its
// faceplate outlined in the world for ten seconds, its coordinates on the message line).
final class WrkHwPanel extends ListPanel<TerminalLine> {
    private final String device;
    private boolean every;
    private @Nullable TerminalLine head;

    WrkHwPanel(CrtTerminal screen, String device) {
        super(screen);
        this.device = device.isBlank() ? "*ALL" : device;
    }

    @Override
    String id() {
        return "WRKHW";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrkhw.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrkhw");
    }

    @Override
    int firstRow() {
        return 7;
    }

    @Override
    int pageSize() {
        return 8;
    }

    // A row's key: its first cell (device and slot, not shown).
    @Override
    Object key(TerminalLine row) {
        return cell(row, 0);
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
        screen.send(TerminalService.QUERY, "hardware " + device + (every ? " all" : ""));
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() != TerminalService.QUERY) {
            return;
        }
        switch (response.topic()) {
            case "hardware" -> {
                List<TerminalLine> lines = response.lines();
                head = lines.isEmpty() ? null : lines.getFirst();
                setRows(lines.isEmpty() ? List.of() : lines.subList(1, lines.size()));
            }
            case "locatehw" -> {
                if (!response.lines().isEmpty()) {
                    CrtLocate.locate(response.lines().getFirst().text());
                }
                response.message().ifPresent(screen::message);
                next();
            }
            default -> {
            }
        }
    }

    @Override
    boolean functionKey(int f) {
        if (f == 11) {
            every = !every;
            clearOptions();
            reload();
            return true;
        }
        return false;
    }

    @Override
    void drawHead(CrtGrid grid) {
        if (head != null) {
            grid.put(2, 1, head.text());
        }
        grid.put(3, 1, tr("crt.encodedlogistics.type_options"));
        grid.put(4, 3, tr("crt.encodedlogistics.wrkhw.opts"));
        grid.put(6, 0, tr("crt.encodedlogistics.wrkhw.cols"), CrtGrid.BRIGHT);
        if (rows.isEmpty()) {
            grid.put(8, 5, tr("crt.encodedlogistics.wrkhw.empty"), CrtGrid.DIM);
        }
    }

    // Device, Slot, Part, Status, Detail.
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 1), 10));
        grid.put(screenRow, 15, CrtGrid.pad(cell(row, 2), 7));
        grid.put(screenRow, 22, CrtGrid.pad(cell(row, 3), 18));
        grid.put(screenRow, 40, CrtGrid.pad(cell(row, 4), 12));
        grid.put(screenRow, 52, CrtGrid.pad(cell(row, 5), 27));
    }

    @Override
    boolean process(List<Option<TerminalLine>> chosen) {
        pending.clear();
        for (Option<TerminalLine> option : chosen) {
            String dev = cell(option.row(), 1), slot = cell(option.row(), 2);
            switch (option.option().trim()) {
                case "5" -> then(() -> screen.push(new TextPanel(screen, "DSPHW", tr("crt.encodedlogistics.wrkhw.display"), "hwpart " + dev + " " + slot)));
                case "8" -> then(() -> screen.send(TerminalService.QUERY, "locatehw " + dev + " " + slot));
                default -> {
                    pending.clear();
                    return invalid(option);
                }
            }
        }
        next();
        return true;
    }
}
