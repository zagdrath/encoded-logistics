/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;

import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// WRKIX IX() (docs/routing/layouts/wrkix.txt): an Exchange Switch's members (and join requests) with their ports,
// status, peering, routes learned through it and traffic. 4=Remove member (RMVIXMBR, confirmed), 5=Display,
// 8=Approve request (APVIXMBR) - the owner's. F10 shows the sessions instead, F11 only the requests (again: back).
final class WrkIxPanel extends OsListPanel {
    private final String exchange;
    private String view = "*MEMBERS";

    WrkIxPanel(CrtTerminal screen, String exchange) {
        super(screen);
        this.exchange = exchange;
    }

    @Override
    String id() {
        return "WRKIX";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrkix.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrkix");
    }

    @Override
    String query() {
        return "ix " + exchange + " " + view;
    }

    @Override
    String legend() {
        return tr(view.equals("*SESSIONS") ? "crt.encodedlogistics.wrkix.opts_sessions" : "crt.encodedlogistics.wrkix.opts");
    }

    @Override
    String heading() {
        return tr(view.equals("*SESSIONS") ? "crt.encodedlogistics.wrkix.cols_sessions" : "crt.encodedlogistics.wrkix.cols");
    }

    @Override
    String defaultOption() {
        return "5";
    }

    // The first row is the exchange itself.
    @Override
    void setRows(List<TerminalLine> rows) {
        head = rows.isEmpty() ? null : rows.getFirst();
        super.setRows(rows.isEmpty() ? rows : rows.subList(1, rows.size()));
    }

    private TerminalLine head;

    @Override
    void drawTop(CrtGrid grid) {
        if (head == null) {
            return;
        }
        grid.put(2, 1, tr("crt.encodedlogistics.wrkix.exchange"));
        grid.put(2, 15, cell(head, 0));
        grid.put(2, 22, tr("crt.encodedlogistics.wrkix.owner"));
        grid.put(2, 33, cell(head, 1));
        grid.put(2, 40, tr("crt.encodedlogistics.wrkix.ports"));
        grid.put(2, 51, cell(head, 2) + " / " + cell(head, 3));
        grid.put(2, 60, tr("crt.encodedlogistics.wrkix.join"));
        grid.put(2, 70, cell(head, 4));
    }

    // network, owner, port, status, peering, routes, traffic a minute
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 0), view.equals("*SESSIONS") ? 27 : 11));
        if (!view.equals("*SESSIONS")) {
            grid.put(screenRow, 17, CrtGrid.pad(cell(row, 1), 9));
            String port = cell(row, 2);
            grid.put(screenRow, 31 - port.length(), port);
        }
        grid.put(screenRow, 33, CrtGrid.pad(cell(row, 3), 10));
        grid.put(screenRow, 44, CrtGrid.pad(cell(row, 4), 9));
        String routes = cell(row, 5), traffic = cell(row, 6);
        grid.put(screenRow, 61 - routes.length(), routes);
        grid.put(screenRow, 74 - traffic.length(), traffic);
    }

    @Override
    boolean functionKey(int f) {
        if (f == 10 || f == 11) {
            String wanted = f == 10 ? "*SESSIONS" : "*REQUESTS";
            view = view.equals(wanted) ? "*MEMBERS" : wanted;
            shown();
            return true;
        }
        return false;
    }

    @Override
    boolean option(String code, TerminalLine row) {
        if (view.equals("*SESSIONS")) {
            return false;
        }
        String network = cell(row, 0);
        switch (code) {
            case "5" -> then(() -> screen.push(new TextPanel(screen, "DSPIXMBR", tr("crt.encodedlogistics.wrkix.display", network),
                    "ixmember " + exchange + " " + network)));
            case "8" -> then(() -> screen.runCommand("APVIXMBR IX(" + exchange + ") NETWORK(" + network + ")"));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    String deleteLabel() {
        return tr("crt.encodedlogistics.confirm.remove");
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "RMVIXMBR IX(" + exchange + ") NETWORK(" + cell(row, 0) + ")";
    }
}
