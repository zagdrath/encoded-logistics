/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// WRKACL RTR() ACL() (docs/routing/layouts/wrkacl.txt): a Router ACL's rules in order, with their counters, and the
// default last. 2=Change (CHGACLE, prompted with the rule's values), 4=Remove (RMVACLE, confirmed), 6=Move up and
// 7=Move down (CHGACLE MOVE); F6 adds a rule (ADDACLE).
final class WrkAclPanel extends OsListPanel {
    private static final Pattern RATE = Pattern.compile("(\\d+)/m\\b"), QUOTA = Pattern.compile("(\\d+)/d\\b");
    private final String router, acl;

    WrkAclPanel(CrtTerminal screen, String router, String acl) {
        super(screen);
        this.router = router;
        this.acl = acl;
    }

    @Override
    String id() {
        return "WRKACL";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrkacl.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrkacl");
    }

    @Override
    String query() {
        return "acl " + router + " " + acl;
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.wrkacl.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.wrkacl.cols");
    }

    @Override
    String defaultOption() {
        return "2";
    }

    private static boolean isDefault(TerminalLine row) {
        return cell(row, 0).equals("*DFT");
    }

    // The Router, the ACL and its default action.
    @Override
    void drawTop(CrtGrid grid) {
        grid.put(2, 1, tr("crt.encodedlogistics.wrkacl.router"));
        grid.put(2, 15, router);
        grid.put(2, 25, tr("crt.encodedlogistics.wrkacl.acl"));
        grid.put(2, 38, acl);
        String dft = "";
        for (TerminalLine row : rows) {
            if (isDefault(row)) {
                dft = cell(row, 3);
            }
        }
        grid.put(2, 49, tr("crt.encodedlogistics.wrkacl.default"));
        grid.put(2, 62, dft);
    }

    // seq, match, dir, action, limit, hits, last matched, the rule as the panel writes it
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        if (isDefault(row)) {
            grid.put(screenRow, 5, cell(row, 0));
            grid.put(screenRow, 11, cell(row, 1));
        } else {
            grid.put(screenRow, 5, CrtGrid.pad(" ".repeat(Math.max(0, 3 - cell(row, 0).length())) + cell(row, 0), 4));
            grid.put(screenRow, 10, CrtGrid.pad(cell(row, 1), 14));
            grid.put(screenRow, 25, CrtGrid.pad(cell(row, 2), 6));
        }
        grid.put(screenRow, 32, CrtGrid.pad(cell(row, 3), 8));
        grid.put(screenRow, 41, CrtGrid.pad(cell(row, 4), 10));
        String hits = cell(row, 5);
        grid.put(screenRow, 57 - hits.length(), hits);
        grid.put(screenRow, 59, cell(row, 6));
    }

    @Override
    boolean functionKey(int f) {
        if (f == 6) {
            screen.prompter("ADDACLE RTR(" + router + ") ACL(" + acl + ")", false, screen::runCommand);
            return true;
        }
        return false;
    }

    private String rule(TerminalLine row) {
        return "RTR(" + router + ") ACL(" + acl + ") SEQ(" + cell(row, 0) + ")";
    }

    private static String limit(Pattern pattern, String spec) {
        Matcher matcher = pattern.matcher(spec);
        return matcher.find() ? matcher.group(1) : "*NONE";
    }

    @Override
    boolean option(String code, TerminalLine row) {
        if (isDefault(row)) {
            return false;
        }
        switch (code) {
            case "2" -> {
                String spec = cell(row, 7), match = cell(row, 1);
                String command = "CHGACLE " + rule(row) + " MATCH(" + (match.contains(" ") ? "'" + match + "'" : match) + ") DIR(" + cell(row, 2)
                        + ") ACTION(" + cell(row, 3) + ") RATE(" + limit(RATE, spec) + ") QUOTA(" + limit(QUOTA, spec) + ")";
                then(() -> screen.prompter(command, false, screen::runCommand));
            }
            case "6", "7" -> then(() -> screen.runCommand("CHGACLE " + rule(row) + " MOVE(" + (code.equals("6") ? "*UP" : "*DOWN") + ")"));
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
    String rowLabel(TerminalLine row) {
        return cell(row, 0) + "  " + cell(row, 1);
    }

    @Override
    void confirmAll(List<TerminalLine> rows) {
        super.confirmAll(rows.stream().filter(row -> !isDefault(row)).toList());
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "RMVACLE " + rule(row);
    }
}
