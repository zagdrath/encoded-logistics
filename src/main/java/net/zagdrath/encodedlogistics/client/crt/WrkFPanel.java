/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// WRKF (Work with Files): a library's physical files - File, Attr (PF), Chg ("*" when its definition changed after it
// was made), Records, Text. Options: 2=Change data (UPDDTA), 4=Delete (confirmed, DLTF), 5=Display data (DSPPFM),
// 8=Display description (DSPFD). In ELSYS 2 and 4 are refused (ELC0205): its system files show the network's data,
// read-only. F6 prompts CRTPF with the library in its File field.
final class WrkFPanel extends OsListPanel {
    private final String library;

    WrkFPanel(CrtTerminal screen, String library) {
        super(screen);
        this.library = library.toUpperCase(Locale.ROOT);
    }

    @Override
    String id() {
        return "WRKF";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrkf.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrklib");
    }

    @Override
    String query() {
        return "files " + library;
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.wrkf.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.wrkf.cols");
    }

    @Override
    String defaultOption() {
        return "5";
    }

    @Override
    int pageSize() {
        return 11;
    }

    private boolean readOnly() {
        return library.equals("ELSYS");
    }

    @Override
    void drawTop(CrtGrid grid) {
        grid.put(2, 1, tr("crt.encodedlogistics.wrkmbr.library"));
        grid.put(2, 21, library, CrtGrid.BRIGHT);
    }

    // name, attribute, records, text, size, created, source, changed, system
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 0), 10));
        grid.put(screenRow, 17, CrtGrid.pad(cell(row, 1), 4));
        grid.put(screenRow, 23, cell(row, 7), CrtGrid.BRIGHT);
        grid.put(screenRow, 27, CrtGrid.padLeft(records(cell(row, 2)), 9));
        grid.put(screenRow, 38, CrtGrid.pad(cell(row, 3), 40));
    }

    static String records(String count) {
        try {
            return String.format(Locale.ROOT, "%,d", Long.parseLong(count));
        } catch (NumberFormatException e) {
            return count;
        }
    }

    @Override
    void draw(CrtGrid grid) {
        super.draw(grid);
        int shown = Math.min(pageSize(), Math.max(0, rows.size() - top));
        int note = firstRow() + shown + 1;
        if (shown > 0 && note < 20 && rows.stream().anyMatch(row -> cell(row, 7).equals("*"))) {
            grid.put(note, 38, tr("crt.encodedlogistics.wrkf.note"), CrtGrid.DIM);
        }
    }

    @Override
    boolean functionKey(int f) {
        if (f == 6) {
            if (screen.prompter("CRTPF", false, screen::runCommand) && screen.current() instanceof PrompterPanel prompter) {
                prompter.preset("FILE", library + "/");
            }
            return true;
        }
        return false;
    }

    @Override
    boolean option(String code, TerminalLine row) {
        return fileOption(screen, this, library, cell(row, 0), code);
    }

    // A file's options, here and on Work with Members' file rows: 2, 5, 8 (4 is the delete, confirmed).
    static boolean fileOption(CrtTerminal screen, ListPanel<?> panel, String library, String file, String code) {
        String qualified = library + "/" + file;
        switch (code) {
            case "2" -> panel.then(() -> {
                if (library.equals("ELSYS")) {
                    screen.message(ElclMessage.of("ELC0205", library).toString());
                    panel.next();
                } else {
                    screen.push(new UpdDtaPanel(screen, qualified));
                }
            });
            case "5" -> panel.then(() -> screen.push(new DspPfmPanel(screen, qualified)));
            case "8" -> panel.then(() -> screen.push(new DspFdPanel(screen, qualified)));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    boolean process(java.util.List<Option<TerminalLine>> chosen) {
        if (readOnly() && chosen.stream().anyMatch(option -> option.option().trim().equals("4"))) {
            java.util.List<Option<TerminalLine>> rest = chosen.stream().filter(option -> !option.option().trim().equals("4")).toList();
            if (!rest.isEmpty()) {
                super.process(rest);
            }
            screen.message(ElclMessage.of("ELC0205", library).toString());
            return true;
        }
        return super.process(chosen);
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "DLTF FILE(" + library + "/" + cell(row, 0) + ")";
    }
}
