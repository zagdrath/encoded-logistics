/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.Set;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// DSPMSG (screen 12): the user's message queue, newest first - Sev, From (job or user), Sent, Message (cut; 5 shows all
// of it). Unread messages are bright; showing them marks them read, and "MW" goes. Options: 4=Remove, 5=Display details
// (a window: the text, ID, severity, from, sent). F11 removes them all (confirmed).
final class DspMsgPanel extends OsListPanel {
    // The ones unread when the screen got them (bright while it shows them).
    private Set<String> unread = Set.of();

    DspMsgPanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "DSPMSG";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.dspmsg.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.dspmsg");
    }

    @Override
    String query() {
        return "messages";
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.dspmsg.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.dspmsg.cols");
    }

    @Override
    String defaultOption() {
        return "5";
    }

    // No confirmation for 4=Remove: it's done straight away.
    @Override
    String deleteOption() {
        return "";
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, topic())) {
            unread = new java.util.HashSet<>();
            for (TerminalLine line : response.lines()) {
                if (cell(line, 6).equals("1")) {
                    unread.add(cell(line, 0));
                }
            }
            if (!unread.isEmpty()) {
                screen.query("readmessages");
            }
        }
        super.receive(response);
    }

    @Override
    void drawTop(CrtGrid grid) {
        grid.put(2, 1, tr("crt.encodedlogistics.dspmsg.queue"));
        grid.put(2, 19, screen.user.toUpperCase(java.util.Locale.ROOT), CrtGrid.BRIGHT);
        grid.put(2, 44, tr("crt.encodedlogistics.dspmsg.program"));
        grid.put(2, 64, "*DSPMSG", CrtGrid.BRIGHT);
    }

    // id, message ID, severity, from, sent, text, unread
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        byte attr = unread.contains(cell(row, 0)) ? CrtGrid.BRIGHT : CrtGrid.NORMAL;
        grid.put(screenRow, 5, CrtGrid.padLeft(cell(row, 2), 2), attr);
        grid.put(screenRow, 10, CrtGrid.pad(cell(row, 3), 10), attr);
        grid.put(screenRow, 22, CrtGrid.pad(cell(row, 4), 15), attr);
        grid.put(screenRow, 39, CrtGrid.pad(cell(row, 5), 40), attr);
    }

    @Override
    void draw(CrtGrid grid) {
        super.draw(grid);
        int shown = Math.min(pageSize(), Math.max(0, rows.size() - top));
        int note = firstRow() + shown + 1;
        if (shown > 0 && note < 20) {
            grid.put(note, 39, tr("crt.encodedlogistics.dspmsg.note"), CrtGrid.DIM);
        }
        if (rows.isEmpty()) {
            grid.put(firstRow() + 1, 5, tr("crt.encodedlogistics.dspmsg.none"), CrtGrid.DIM);
        }
    }

    @Override
    boolean functionKey(int f) {
        if (f == 11) {
            if (!rows.isEmpty()) {
                screen.confirm(tr("crt.encodedlogistics.dspmsg.remove_all"), () -> {
                    screen.query("removemessages");
                    screen.query(query());
                }, null);
            }
            return true;
        }
        return false;
    }

    @Override
    boolean option(String code, TerminalLine row) {
        switch (code) {
            case "4" -> then(() -> {
                screen.query("removemessage " + cell(row, 0));
                screen.query(query());
                next();
            });
            case "5" -> then(() -> {
                String id = cell(row, 1).isEmpty() ? "-" : cell(row, 1);
                window(new CrtWindow(screen, 5, 6, 14, 68, tr("crt.encodedlogistics.dspmsg.details"))
                        .text(cell(row, 5), CrtGrid.BRIGHT).text("")
                        .text(tr("crt.encodedlogistics.dspmsg.detail", id, cell(row, 2), cell(row, 3), cell(row, 4))));
            });
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "";
    }
}
