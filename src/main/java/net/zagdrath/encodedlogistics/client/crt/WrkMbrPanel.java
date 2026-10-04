/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// WRKMBR (screen 4): a library's source members - Member, Type (ELCLP), Chg ("*" when the source changed after its
// program was compiled), Text. Options: 2=Edit, 3=Copy (a window: to library and member, CPYMBR), 4=Delete (confirmed,
// DLTMBR), 5=Display (the editor, read-only), 6=Print (the source to a spooled file), 7=Rename (a window: the new name,
// RNMMBR), 14=Compile (CRTELPGM: ELC0218 or ELC0206, the listing in Work with Output). In ELSYS 2, 4 and 7 are refused
// (ELC0205). F6 prompts CRTMBR; F11 sorts by name, changed, text.
final class WrkMbrPanel extends OsListPanel {
    private enum Sort {
        NAME, CHANGED, TEXT
    }

    private final String library;
    private Sort sort = Sort.NAME;

    WrkMbrPanel(CrtTerminal screen, String library) {
        super(screen);
        this.library = library;
    }

    @Override
    String id() {
        return "WRKMBR";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrkmbr.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrkmbr");
    }

    @Override
    String query() {
        return "members " + library;
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.wrkmbr.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.wrkmbr.cols");
    }

    @Override
    String defaultOption() {
        return "2";
    }

    @Override
    int pageSize() {
        return 11;
    }

    private boolean readOnly() {
        return library.equals("ELSYS");
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, topic())) {
            List<TerminalLine> lines = new ArrayList<>(response.lines());
            lines.sort(switch (sort) {
                case NAME -> Comparator.comparing((TerminalLine line) -> cell(line, 0));
                case CHANGED -> Comparator.comparing((TerminalLine line) -> cell(line, 2)).reversed().thenComparing(line -> cell(line, 0));
                case TEXT -> Comparator.comparing((TerminalLine line) -> cell(line, 3), String.CASE_INSENSITIVE_ORDER);
            });
            setRows(lines);
            response.message().ifPresent(screen::message);
            return;
        }
        super.receive(response);
    }

    @Override
    void drawTop(CrtGrid grid) {
        grid.put(2, 1, tr("crt.encodedlogistics.wrkmbr.library"));
        grid.put(2, 21, library, CrtGrid.BRIGHT);
    }

    // name, type, changed, text, lines, updated, program
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 0), 10));
        grid.put(screenRow, 17, CrtGrid.pad(cell(row, 1), 7));
        grid.put(screenRow, 26, cell(row, 2), CrtGrid.BRIGHT);
        grid.put(screenRow, 30, CrtGrid.pad(cell(row, 3), 48));
    }

    @Override
    void draw(CrtGrid grid) {
        super.draw(grid);
        int shown = Math.min(pageSize(), Math.max(0, rows.size() - top));
        int note = firstRow() + shown + 1;
        if (shown > 0 && note < 20 && rows.stream().anyMatch(row -> cell(row, 2).equals("*"))) {
            grid.put(note, 30, tr("crt.encodedlogistics.wrkmbr.note"), CrtGrid.DIM);
        }
    }

    @Override
    void sort() {
        sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
        screen.message(tr("crt.encodedlogistics.wrkmbr.sorted", tr("crt.encodedlogistics.wrkmbr.sort." + sort.name().toLowerCase(java.util.Locale.ROOT))));
        screen.query(query());
    }

    @Override
    boolean functionKey(int f) {
        if (f == 6) {
            if (screen.prompter("CRTMBR", false, screen::runCommand) && screen.current() instanceof PrompterPanel prompter) {
                prompter.preset("MBR", library + "/");
            }
            return true;
        }
        return false;
    }

    private void readOnlyRefused() {
        screen.message(ElclMessage.of("ELC0205", library).toString());
    }

    @Override
    boolean option(String code, TerminalLine row) {
        String member = cell(row, 0), qualified = library + "/" + member;
        switch (code) {
            case "2" -> then(() -> {
                if (readOnly()) {
                    readOnlyRefused();
                    next();
                } else {
                    screen.editMember(library, member, false);
                }
            });
            case "5" -> then(() -> screen.editMember(library, member, true));
            case "3" -> then(() -> window(new FormWindow(screen, tr("crt.encodedlogistics.wrkmbr.copy", qualified), tr("crt.encodedlogistics.wrkmbr.copy_text"),
                    values -> screen.runCommand("CPYMBR FROM(" + qualified + ") TO(" + values.get(0) + "/" + values.get(1) + ")"))
                    .field(tr("crt.encodedlogistics.wrkmbr.to_library"), 10, readOnly() ? screen.currentLibrary : library, "")
                    .field(tr("crt.encodedlogistics.wrkmbr.to_member"), 10, member, "")));
            case "6" -> then(() -> {
                screen.query("printmember " + library + " " + member);
                next();
            });
            case "7" -> then(() -> {
                if (readOnly()) {
                    readOnlyRefused();
                    next();
                    return;
                }
                window(new FormWindow(screen, tr("crt.encodedlogistics.wrkmbr.rename", qualified), tr("crt.encodedlogistics.wrkmbr.rename_text"),
                        values -> screen.runCommand("RNMMBR MBR(" + qualified + ") NEWNAME(" + values.get(0) + ")"))
                        .field(tr("crt.encodedlogistics.wrkmbr.new_name"), 10, member, ""));
            });
            case "14" -> then(() -> screen.runCommand("CRTELPGM PGM(" + qualified + ")"));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    boolean process(List<Option<TerminalLine>> chosen) {
        if (readOnly() && chosen.stream().anyMatch(option -> option.option().trim().equals("4"))) {
            List<Option<TerminalLine>> rest = chosen.stream().filter(option -> !option.option().trim().equals("4")).toList();
            if (!rest.isEmpty()) {
                super.process(rest);
            }
            readOnlyRefused();
            return true;
        }
        return super.process(chosen);
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "DLTMBR MBR(" + library + "/" + cell(row, 0) + ")";
    }
}
