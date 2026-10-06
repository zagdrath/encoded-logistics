/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// WRKMBR (screen 4): a library's source members - Member, Type (ELCLP, or PF: a file's definition), Chg ("*" when the
// source changed after its program, or file, was made), Text - and after them the library's files (Type *FILE).
// Options: 2=Edit, 3=Copy (a window: to library and member, CPYMBR), 4=Delete (confirmed, DLTMBR), 5=Display (the
// editor, read-only), 6=Print (the source to a spooled file), 7=Rename (a window: the new name, RNMMBR), 9=Submit
// (SBMJOB's prompter with CMD(CALL PGM(LIB/MEMBER)) JOB(MEMBER), for parameters or a host first), 14=Compile (an ELCLP
// member CRTELPGM: ELC0218 or ELC0206; a PF member CRTPF, or CHGPF when its file is there already; the listing in Work
// with Output), 16=Call (CALL's prompter with PGM(LIB/MEMBER), run in the interactive job). 9 and 16 need the member's
// program, up to date: with none, or Chg *, they say so on the message line and offer to compile first (Enter: 14,
// then on to the prompter once ELC0218 comes back). On a file: 2=Change data, 4=Delete (DLTF), 5=Display data,
// 8=Display description (as Work with Files). In ELSYS 2, 4 and 7 are refused (ELC0205). F6 prompts CRTMBR; F11 sorts
// the members by name, changed, text.
final class WrkMbrPanel extends OsListPanel {
    private enum Sort {
        NAME, CHANGED, TEXT
    }

    private final String library;
    private Sort sort = Sort.NAME;
    // The members (sorted) and the files, the files after the members.
    private List<TerminalLine> members = List.of(), files = List.of();
    // The last command's message (a compile run before 9 / 16 goes on only on ELC0218).
    private String lastMessage = "";

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
    String legend2() {
        return tr("crt.encodedlogistics.wrkmbr.opts2");
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
    void shown() {
        screen.query("files " + library);
        super.shown();
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() == TerminalService.COMMAND) {
            lastMessage = response.message().map(Component::getString).orElse("");
        }
        if (answers(response, topic())) {
            List<TerminalLine> lines = new ArrayList<>(response.lines());
            lines.sort(switch (sort) {
                case NAME -> Comparator.comparing((TerminalLine line) -> cell(line, 0));
                case CHANGED -> Comparator.comparing((TerminalLine line) -> cell(line, 2)).reversed().thenComparing(line -> cell(line, 0));
                case TEXT -> Comparator.comparing((TerminalLine line) -> cell(line, 3), String.CASE_INSENSITIVE_ORDER);
            });
            members = lines;
            combine();
            response.message().ifPresent(screen::message);
            return;
        }
        if (answers(response, "files")) {
            // A file as a row like a member's: name, *FILE, changed, text.
            List<TerminalLine> lines = new ArrayList<>();
            for (TerminalLine file : response.lines()) {
                lines.add(TerminalLine.builder().left(cell(file, 0), 0).left(FILE, 0).left(cell(file, 7), 0).left(cell(file, 3), 0).build());
            }
            files = lines;
            combine();
            return;
        }
        if (response.kind() == TerminalService.COMMAND) {
            screen.query("files " + library);
        }
        super.receive(response);
    }

    private void combine() {
        List<TerminalLine> all = new ArrayList<>(members);
        all.addAll(files);
        setRows(all);
    }

    static final String FILE = "*FILE";

    private static boolean isFile(TerminalLine row) {
        return cell(row, 1).equals(FILE);
    }

    // A file and a member may share a name.
    @Override
    Object key(TerminalLine row) {
        return isFile(row) ? FILE + "/" + cell(row, 0) : cell(row, 0);
    }

    @Override
    String rowLabel(TerminalLine row) {
        return isFile(row) ? cell(row, 0) + "  " + FILE : cell(row, 0);
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
        if (isFile(row)) {
            return WrkFPanel.fileOption(screen, this, library, member, code);
        }
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
            case "9", "16" -> {
                if (cell(row, 1).equals("PF")) {
                    // A file's definition: nothing to run.
                    return false;
                }
                then(() -> run(code, row));
            }
            case "14" -> then(() -> {
                if (!cell(row, 1).equals("PF")) {
                    screen.runCommand("CRTELPGM PGM(" + qualified + ")");
                } else if (files.stream().anyMatch(file -> cell(file, 0).equals(member))) {
                    // Its file is there: made again, its records kept.
                    screen.runCommand("CHGPF FILE(" + qualified + ") SRCMBR(" + qualified + ")");
                } else {
                    screen.runCommand("CRTPF FILE(" + qualified + ") SRCMBR(" + qualified + ")");
                }
            });
            default -> {
                return false;
            }
        }
        return true;
    }

    // 9=Submit / 16=Call: the prompter, or - without an up-to-date program - why not, and the offer to compile first.
    private void run(String code, TerminalLine target) {
        String qualified = library + "/" + cell(target, 0);
        String problem = notReady(target);
        if (problem == null) {
            prompt(code, target);
            return;
        }
        screen.message(problem);
        window(new CrtWindow(screen, 7, 10, 10, 60, tr("crt.encodedlogistics.wrkmbr.compile_first.title")) {
            @Override
            boolean enter() {
                screen.closeWindow();
                // Compiled, then on to the prompter (the step after the compile's answer).
                pending.addFirst(() -> {
                    if (lastMessage.startsWith("ELC0218")) {
                        prompt(code, target);
                    } else {
                        next();
                    }
                });
                screen.runCommand("CRTELPGM PGM(" + qualified + ")");
                return true;
            }
        }.text(problem + "\n\n" + tr("crt.encodedlogistics.wrkmbr.compile_first", tr("crt.encodedlogistics.wrkmbr.option." + code)))
                .keys(tr("crt.encodedlogistics.wrkmbr.compile_first.keys")));
    }

    // Why a member's program can't run now: none ("Program LIB/MEMBER is not compiled."), or one older than the source
    // (Chg *); null when it's ready.
    static @Nullable String notReady(String library, TerminalLine row) {
        String qualified = library + "/" + cell(row, 0);
        if (!cell(row, 6).equals("1")) {
            return tr("crt.encodedlogistics.wrkmbr.not_compiled", qualified);
        }
        if (cell(row, 2).equals("*")) {
            return tr("crt.encodedlogistics.wrkmbr.changed_since", qualified);
        }
        return null;
    }

    private @Nullable String notReady(TerminalLine row) {
        return notReady(library, row);
    }

    // The command for 9=Submit or 16=Call on a member, as the prompter opens on it.
    static String command(String code, String library, String member) {
        String program = library + "/" + member;
        return code.equals("9") ? "SBMJOB CMD(CALL PGM(" + program + ")) JOB(" + member + ")" : "CALL PGM(" + program + ")";
    }

    private void prompt(String code, TerminalLine row) {
        if (!screen.prompter(command(code, library, cell(row, 0)), false, screen::runCommand)) {
            next();
        }
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
        return (isFile(row) ? "DLTF FILE(" : "DLTMBR MBR(") + library + "/" + cell(row, 0) + ")";
    }
}
