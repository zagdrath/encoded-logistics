/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// WRKMCH: the network's machines with a Small Wireless Bridge on (Arcforge's) - Machine (its device name), Type (the
// machine's name), Status (bright when it needs attention), Progress, Energy and Rate (operations a minute). Options:
// 2=Change (a window: redstone mode, auto-eject, power from network and Gateway, F4 stepping through each one's values;
// CHGMCHCFG with what was changed), 5=Display (DSPMCH: everything about it, its statistics and settings),
// 7=Enable/Disable (CHGMCHSTS, the other way to how it is), 8=Sides (a single-block machine's: a window with each side's
// mode, F4 stepping through the modes; CHGMCHCFG SIDE() SIDEMODE() for each one changed). Changing needs build
// permission (the commands' CONFIGURE).
final class WrkMchPanel extends OsListPanel {
    // A row's cells (ScreenQueries "machines").
    static final int NAME = 0, TYPE = 1, STATUS = 2, PROGRESS = 3, ENERGY = 4, RATE = 5, REDSTONE = 6, EJECT = 7, POWER = 8, GATEWAY = 9, MODES = 10,
            ENABLED = 11, SIDES = 12, SIDE_MODES = 13;

    WrkMchPanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "WRKMCH";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrkmch.title");
    }

    @Override
    String query() {
        return "machines";
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.wrkmch.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.wrkmch.cols");
    }

    @Override
    String defaultOption() {
        return "5";
    }

    @Override
    String deleteOption() {
        return "";
    }

    // Statuses that need attention: bright.
    private static final List<String> ATTENTION = List.of("*FAULT", "*NOPOWER", "*NOINPUT", "*BLOCKED", "*NOTFORMED", "*OFFLINE");

    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, NAME), 10));
        grid.put(screenRow, 16, CrtGrid.pad(cell(row, TYPE), 16));
        String status = cell(row, STATUS);
        grid.put(screenRow, 33, CrtGrid.pad(status, 10), ATTENTION.contains(status) ? CrtGrid.BRIGHT : CrtGrid.NORMAL);
        grid.put(screenRow, 44, CrtGrid.pad(cell(row, PROGRESS), 5));
        grid.put(screenRow, 50, CrtGrid.pad(cell(row, ENERGY), 17));
        grid.put(screenRow, 68, CrtGrid.pad(cell(row, RATE), 11));
    }

    @Override
    boolean option(String code, TerminalLine row) {
        String name = cell(row, NAME);
        switch (code) {
            case "2" -> then(() -> window(new FormWindow(screen, tr("crt.encodedlogistics.wrkmch.change", name), cell(row, TYPE),
                    values -> screen.runCommand(change(name, row, values)))
                    .field(tr("crt.encodedlogistics.wrkmch.redstone"), 10, cell(row, REDSTONE), cell(row, MODES), words(cell(row, MODES)))
                    .field(tr("crt.encodedlogistics.wrkmch.eject"), 4, cell(row, EJECT), "*YES *NO", cell(row, EJECT).equals("*NONE") ? List.of()
                            : List.of("*YES", "*NO"))
                    .field(tr("crt.encodedlogistics.wrkmch.power"), 4, cell(row, POWER), "*YES *NO", List.of("*YES", "*NO"))
                    .field(tr("crt.encodedlogistics.wrkmch.gateway"), 10, cell(row, GATEWAY), tr("crt.encodedlogistics.wrkmch.gateway_hint"))));
            case "8" -> {
                Map<String, String> sides = sides(cell(row, SIDES));
                if (sides.isEmpty()) {
                    screen.message(tr("crt.encodedlogistics.wrkmch.no_sides", name));
                    return true;
                }
                then(() -> {
                    List<String> modes = words(cell(row, SIDE_MODES));
                    FormWindow form = new FormWindow(screen, 4, 14, tr("crt.encodedlogistics.wrkmch.sides", name), cell(row, TYPE),
                            values -> sideChanges(name, sides, values).forEach(screen::runCommand));
                    sides.forEach((side, mode) -> form.field(tr("crt.encodedlogistics.wrkmch.side." + side), 12, mode, String.join(" ", modes), modes));
                    window(form);
                });
            }
            case "5" -> then(() -> screen.push(new TextPanel(screen, "DSPMCH", tr("crt.encodedlogistics.wrkmch.display", name), "machine " + name)));
            case "7" -> then(() -> screen.runCommand("CHGMCHSTS MCH(" + name + ") STATUS(" + (cell(row, ENABLED).equals("*YES") ? "*DISABLE" : "*ENABLE")
                    + ")"));
            default -> {
                return false;
            }
        }
        return true;
    }

    // CHGMCHCFG with the fields that changed (one the machine doesn't have, shown *NONE, is never sent).
    static String change(String name, TerminalLine row, List<String> values) {
        StringBuilder command = new StringBuilder("CHGMCHCFG MCH(").append(name).append(")");
        String[] keywords = { "RSMODE", "AUTOEJECT", "PWRNET", "GATEWAY" };
        int[] cells = { REDSTONE, EJECT, POWER, GATEWAY };
        for (int i = 0; i < keywords.length; i++) {
            String value = values.get(i).trim().toUpperCase(Locale.ROOT);
            if (!value.isEmpty() && !value.equals(cell(row, cells[i]).toUpperCase(Locale.ROOT))) {
                command.append(' ').append(keywords[i]).append('(').append(value).append(')');
            }
        }
        return command.toString();
    }

    private static List<String> words(String text) {
        return text.isBlank() ? List.of() : Arrays.asList(text.trim().split(" +"));
    }

    // "top=input bottom=output": side -> mode, in that order.
    static Map<String, String> sides(String text) {
        Map<String, String> sides = new LinkedHashMap<>();
        for (String pair : words(text)) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                sides.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return sides;
    }

    // A CHGMCHCFG SIDE() SIDEMODE() for each side whose mode was changed.
    static List<String> sideChanges(String name, Map<String, String> sides, List<String> values) {
        List<String> commands = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> side : sides.entrySet()) {
            String value = values.get(i++).trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty() && !value.equals(side.getValue().toLowerCase(Locale.ROOT))) {
                commands.add("CHGMCHCFG MCH(" + name + ") SIDE(*" + side.getKey().toUpperCase(Locale.ROOT) + ") SIDEMODE(" + value + ")");
            }
        }
        return commands;
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "";
    }
}
