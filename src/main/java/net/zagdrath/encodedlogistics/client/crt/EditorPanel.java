/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.compile.FileResolver;
import net.zagdrath.encodedlogistics.elcl.db.Dds;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.parse.Parser;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// EDTMBR (screen 5): a source member in the editor. Row 1 the member, row 2 the columns shown and the cursor's line and
// column, row 3 the column ruler when it's on (COLS, F15), then "Beginning of data", the source - a margin field per row
// with its sequence number (type a line command over it) and the text, 72 of the 80 columns at a time (F19 / F20) - and
// "End of data" (an empty member opens on one blank line; "Beginning of data" takes I, A and COLS). Enter applies the line
// commands (EditorModel), drops new lines left blank, opens another after a new line just typed on (SEU's insert mode),
// and checks the changed statements with the ELCL parser:
// the first bad one shows reversed, the cursor goes to it, its message to the message line (saving is allowed anyway).
// The command line takes FIND / CHANGE (F16 / F17 repeat them), TOP, BOTTOM, SAVE, FILE, CANCEL, RESET, or any ELCL
// command (F4 prompts it). F4 on a source line prompts its statement and writes the answer back. F11: full-screen edit
// (all 80 columns, no margins); F10: the cursor to the command line. Read-only (5=Display, ELSYS): F3 only. Another
// user editing it: ELC0208. The member comes in pages and goes back in pieces (ScreenQueries' source / save).
// A PF member (a physical file's definition) is checked as DDS (Dds: each line its own statement) and has no prompter.
// An ELCLP member's DCLFs are checked against the files' formats, which the editor asks the server for (fileformat)
// as it meets them; until one has come, that file's variables go unchecked.
final class EditorPanel extends CrtPanel {
    private static final int LAST = 19, MARGIN = 7, TEXT = 8, VISIBLE = 72, SHIFT = 8, CHUNK = 7_500;

    private final String library, member;
    // What it's checked for: a job's program, or a PLC's (on a network or not: ELC1502).
    private final Compiler.Target target;
    private boolean readOnly, full, loaded;
    // ELCLP or PF (the source query says).
    private String type = "ELCLP";
    // DCLF formats by LIB/FILE as written: those the server sent, those it hasn't got, those asked for (in order).
    private final Map<String, RecordFormat> formats = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private final Deque<String> asked = new ArrayDeque<>();
    private final List<SourceLine> incoming = new ArrayList<>();
    private EditorModel model = new EditorModel(List.of());
    private int top, offset;
    private EditorModel.@Nullable Line error;
    private final Set<EditorModel.Line> unchecked = new HashSet<>();
    private boolean closing;

    // What a screen row holds: the top or bottom marker, a line, or a run of excluded lines.
    private record Item(int kind, EditorModel.@Nullable Line line, int count) {
        static final int BEGIN = 0, LINE = 1, EXCLUDED = 2, END = 3;
    }

    // A row's fields, with what they showed (only what changed is taken back).
    private record RowFields(Item item, @Nullable CrtField margin, String marginShown, @Nullable CrtField text, String textShown) {}

    private final List<Item> items = new ArrayList<>();
    private final List<RowFields> rows = new ArrayList<>();

    EditorPanel(CrtTerminal screen, String library, String member, boolean readOnly) {
        this(screen, library, member, readOnly, Compiler.Target.JOB);
    }

    EditorPanel(CrtTerminal screen, String library, String member, boolean readOnly, Compiler.Target target) {
        super(screen);
        this.library = library;
        this.member = member;
        this.readOnly = readOnly;
        this.target = target;
    }

    // For the tests: a member already in hand.
    EditorPanel(CrtTerminal screen, String library, String member, List<SourceLine> source, boolean readOnly) {
        this(screen, library, member, readOnly);
        load(source);
    }

    EditorModel model() {
        return model;
    }

    @Override
    String id() {
        return "EDTMBR";
    }

    @Override
    String title() {
        return tr(full ? "crt.encodedlogistics.edit.title_full" : readOnly ? "crt.encodedlogistics.edit.title_display" : "crt.encodedlogistics.edit.title");
    }

    @Override
    String prompt() {
        return full ? "" : tr(readOnly ? "crt.encodedlogistics.edit.prompt_display" : "crt.encodedlogistics.edit.prompt");
    }

    @Override
    String keys() {
        return tr(full ? "crt.encodedlogistics.fkeys.edit_full" : readOnly ? "crt.encodedlogistics.fkeys.edit_display" : "crt.encodedlogistics.fkeys.edit");
    }

    @Override
    void shown() {
        if (loaded && !model.lines.isEmpty() && model.lines.getFirst().fresh) {
            show(0, 0);
        }
        if (!loaded && incoming.isEmpty()) {
            if (!readOnly) {
                screen.query("lock " + library + " " + member);
            }
            screen.query("source " + library + " " + member + " 0");
        }
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, "lock") && response.message().isPresent()) {
            // ELC0208: someone else has it.
            readOnly = true;
            rebuild();
        } else if (answers(response, "source") && !loaded) {
            if (response.lines().isEmpty()) {
                screen.back();
                return;
            }
            TerminalLine head = response.lines().getFirst();
            int total = Integer.parseInt(cell(head, 0));
            readOnly |= cell(head, 2).equals("1");
            if (!cell(head, 3).isEmpty()) {
                type = cell(head, 3);
            }
            for (TerminalLine line : response.lines().subList(1, response.lines().size())) {
                incoming.add(new SourceLine(Integer.parseInt(cell(line, 0)), cell(line, 2), Integer.parseInt(cell(line, 1))));
            }
            if (incoming.size() < total) {
                screen.query("source " + library + " " + member + " " + incoming.size());
                return;
            }
            load(incoming);
        } else if (answers(response, "savecommit") && response.message().isPresent() && response.message().get().getString().startsWith("ELC0213")) {
            model.saved(today());
        } else if (answers(response, "savecommit") && response.message().isPresent() && target.plc()) {
            // A PLC compiles what's saved: its error shows on its line, as Enter's check does.
            unchecked.addAll(model.lines);
            check();
        } else if (answers(response, "fileformat") && !asked.isEmpty()) {
            String key = asked.poll();
            RecordFormat format = response.lines().isEmpty() ? null : RecordFormat.load(cell(response.lines().getFirst(), 1));
            if (format != null) {
                formats.put(key, format);
            } else {
                missing.add(key);
            }
        }
    }

    private boolean pf() {
        return type.equals("PF");
    }

    // The formats of the files the source's DCLFs name that haven't been asked for yet, asked for.
    private void askFormats(List<Stmt> statements) {
        for (Stmt statement : statements) {
            if (statement.is("DCLF")) {
                String[] file = Compiler.qualified(statement.value("FILE"));
                String key = FileResolver.key(file[0], file[1]);
                if (!file[1].isEmpty() && !formats.containsKey(key) && !missing.contains(key) && !asked.contains(key)) {
                    asked.add(key);
                    screen.query("fileformat " + file[0] + " " + file[1]);
                }
            }
        }
    }

    // The editor's resolver: the formats in hand; lenient while one is still to come.
    private FileResolver resolver() {
        boolean waiting = !asked.isEmpty();
        return new FileResolver() {
            @Override
            public @Nullable RecordFormat format(String library, String file) {
                return formats.get(FileResolver.key(library, file));
            }

            @Override
            public boolean lenient() {
                return waiting;
            }
        };
    }

    // The member in hand: an empty one opens on a blank line, the cursor on it.
    private void load(List<SourceLine> source) {
        model = new EditorModel(source);
        loaded = true;
        if (!pf()) {
            askFormats(Parser.parse(model.texts()).statements());
        }
        if (model.lines.isEmpty() && !readOnly) {
            model.lines.add(EditorModel.blank());
        }
        rebuild();
        shown();
    }

    // The game day (a changed line's date), from the clock.
    private int today() {
        String clock = screen.clock();
        try {
            return clock.startsWith("Day ") ? Integer.parseInt(clock.substring(4).split(" ")[0]) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --- Rows ---

    private int firstRow() {
        return full || !model.ruler ? 3 : 4;
    }

    private int rowCount() {
        return LAST - firstRow() + 1;
    }

    private int width() {
        return full ? SourceLine.WIDTH : VISIBLE;
    }

    private void items() {
        items.clear();
        if (!full) {
            items.add(new Item(Item.BEGIN, null, 0));
        }
        List<EditorModel.Line> lines = model.lines;
        for (int i = 0; i < lines.size(); i++) {
            EditorModel.Line line = lines.get(i);
            if (line.excluded != 0 && !full) {
                int j = i;
                while (j < lines.size() && lines.get(j).excluded == line.excluded) {
                    j++;
                }
                items.add(new Item(Item.EXCLUDED, line, j - i));
                i = j - 1;
            } else {
                items.add(new Item(Item.LINE, line, 0));
            }
        }
        if (!full) {
            items.add(new Item(Item.END, null, 0));
        }
    }

    // The fields for the rows shown (after any change to the lines, the page or the mode).
    private void rebuild() {
        items();
        top = Math.max(0, Math.min(top, items.size() - 1));
        EditorModel.Line focusedLine = null;
        boolean focusedText = false;
        int cursor = 0;
        for (RowFields row : rows) {
            if (screen.focused() != null && (screen.focused() == row.margin() || screen.focused() == row.text())) {
                focusedLine = row.item().line();
                focusedText = screen.focused() == row.text();
                cursor = screen.focused().cursor;
            }
        }
        rows.clear();
        fields.clear();
        int width = width();
        for (int i = 0; i < rowCount() && top + i < items.size(); i++) {
            Item item = items.get(top + i);
            int row = firstRow() + i;
            CrtField margin = null, text = null;
            String marginShown = "", textShown = "";
            if (item.kind() == Item.BEGIN && !readOnly) {
                // Line commands before the first line (I, A).
                margin = new CrtField(row, 0, MARGIN, "").uppercase();
                fields.add(margin);
            }
            if (item.kind() == Item.LINE || item.kind() == Item.EXCLUDED) {
                EditorModel.Line line = item.line();
                if (!full) {
                    String mark = model.mark(line);
                    marginShown = item.kind() == Item.EXCLUDED ? "" : mark != null ? CrtGrid.pad(mark, MARGIN) : line.seq > 0 ? SourceLine.seqText(line.seq) : "'''''''";
                    margin = new CrtField(row, 0, MARGIN, marginShown).uppercase();
                    if (readOnly) {
                        margin.protect();
                    }
                    fields.add(margin);
                }
                if (item.kind() == Item.LINE) {
                    textShown = slice(line.text, full ? 0 : offset, width);
                    text = new CrtField(row, full ? 0 : TEXT, width, textShown);
                    if (readOnly) {
                        text.protect();
                    }
                    fields.add(text);
                }
            }
            rows.add(new RowFields(item, margin, marginShown, text, textShown));
        }
        // The cursor back where it was.
        for (RowFields row : rows) {
            if (focusedLine != null && row.item().line() == focusedLine) {
                CrtField field = focusedText && row.text() != null ? row.text() : row.margin() != null ? row.margin() : row.text();
                if (field != null) {
                    field.cursor = Math.min(cursor, field.length - 1);
                    screen.focus(field);
                    return;
                }
            }
        }
        if (screen.focused() == null || !fields.contains(screen.focused()) && screen.focused() != screen.command) {
            CrtField first = firstEditable();
            if (first != null) {
                screen.focus(first);
            } else {
                screen.focus(screen.command);
            }
        }
    }

    // The first line's margin before "Beginning of data"'s.
    private @Nullable CrtField firstEditable() {
        CrtField begin = null;
        for (RowFields row : rows) {
            for (CrtField field : new CrtField[] { row.margin(), row.text() }) {
                if (field != null && !field.isProtected) {
                    if (row.item().kind() != Item.BEGIN) {
                        return field;
                    }
                    begin = begin != null ? begin : field;
                }
            }
        }
        return begin;
    }

    private static String slice(String text, int from, int width) {
        if (from >= text.length()) {
            return "";
        }
        return text.substring(from, Math.min(text.length(), from + width));
    }

    // What was typed into the rows' text fields back into the lines; their margins' commands.
    private List<EditorModel.Command> sync() {
        List<EditorModel.Command> commands = new ArrayList<>();
        for (RowFields row : rows) {
            EditorModel.Line line = row.item().kind() == Item.BEGIN ? EditorModel.TOP : row.item().line();
            if (line == null) {
                continue;
            }
            if (row.text() != null && !row.text().value.equals(row.textShown())) {
                int from = full ? 0 : offset;
                String text = line.text;
                String head = text.length() >= from ? text.substring(0, from) : CrtGrid.pad(text, from);
                String tail = text.length() > from + width() ? text.substring(from + width()) : "";
                String value = row.text().value;
                String joined = tail.isEmpty() ? (head + value).stripTrailing() : head + CrtGrid.pad(value, width()) + tail;
                model.setText(line, joined);
                unchecked.add(line);
            }
            if (row.margin() != null && !row.margin().value.equals(row.marginShown())) {
                String command = EditorModel.typed(row.marginShown(), row.margin().value);
                if (!command.isEmpty()) {
                    commands.add(new EditorModel.Command(line, command));
                }
            }
        }
        return commands;
    }

    // --- Drawing ---

    @Override
    void draw(CrtGrid grid) {
        grid.put(1, 1, library + "/" + member + "   " + type);
        if (full) {
            grid.put(1, 27, tr("crt.encodedlogistics.edit.full_note"), CrtGrid.DIM);
        }
        int width = width(), first = full ? 1 : offset + 1;
        String line = "0000", col = "";
        CrtField focused = screen.focused();
        for (RowFields row : rows) {
            if (focused != null && (focused == row.text() || focused == row.margin()) && row.item().line() != null) {
                line = SourceLine.shortSeq(Math.max(0, row.item().line().seq));
                col = Integer.toString(focused == row.text() ? first + focused.cursor : 1);
            }
        }
        grid.put(2, 1, String.format(Locale.ROOT, "%s %4d %3d", tr("crt.encodedlogistics.edit.columns"), first, first + width - 1));
        if (!full) {
            grid.put(2, 30, tr("crt.encodedlogistics.edit.line") + "   " + line);
            grid.put(2, 52, tr("crt.encodedlogistics.edit.col") + String.format(Locale.ROOT, " %4s", col));
            if (model.ruler) {
                grid.put(3, 0, "FMT **");
                grid.put(3, TEXT, CrtGrid.columnRuler(VISIBLE, offset + 1), CrtGrid.DIM);
            }
        }
        if (!loaded) {
            grid.put(firstRow() + 1, TEXT, tr("crt.encodedlogistics.edit.loading"), CrtGrid.DIM);
            return;
        }
        for (RowFields row : rows) {
            int r = row.margin() != null ? row.margin().row : row.text() != null ? row.text().row : -1;
            Item item = row.item();
            if (r < 0) {
                r = firstRow() + rows.indexOf(row);
            }
            switch (item.kind()) {
                case Item.BEGIN -> grid.put(r, MARGIN, "*".repeat(14) + " " + tr("crt.encodedlogistics.edit.begin") + " " + "*".repeat(37), CrtGrid.DIM);
                case Item.END -> grid.put(r, 0, "*".repeat(18) + " " + tr("crt.encodedlogistics.edit.end") + " " + "*".repeat(46), CrtGrid.DIM);
                case Item.EXCLUDED -> grid.put(r, MARGIN, "- ".repeat(24) + " " + tr("crt.encodedlogistics.edit.excluded", item.count()), CrtGrid.DIM);
                default -> {
                    if (item.line() == error && row.text() != null) {
                        grid.reverse(r, row.text().col, Math.max(1, Math.min(width, item.line().text.length() - (full ? 0 : offset))));
                    }
                }
            }
        }
        if (!full) {
            grid.right(20, tr(top + rowCount() < items.size() ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
        }
    }

    @Override
    @Nullable String helpField(@Nullable CrtField field) {
        for (RowFields row : rows) {
            if (field != null && field == row.margin()) {
                return "margin";
            }
            if (field != null && field == row.text()) {
                return "text";
            }
        }
        return null;
    }

    // --- Keys ---

    @Override
    void page(int direction) {
        sync();
        int next = top + direction * rowCount();
        if (next >= 0 && next < items.size()) {
            top = next;
        } else {
            top = direction < 0 ? 0 : Math.max(0, items.size() - rowCount());
        }
        rebuild();
    }

    @Override
    boolean functionKey(int f) {
        switch (f) {
            case 3 -> exit();
            case 4 -> {
                if (screen.focused() == screen.command) {
                    return false;
                }
                promptStatement();
            }
            case 5 -> {
                if (!model.dirty) {
                    loaded = false;
                    incoming.clear();
                    screen.query("source " + library + " " + member + " 0");
                }
            }
            case 10 -> screen.focus(screen.command);
            case 11 -> {
                if (!readOnly) {
                    sync();
                    full = !full;
                    rebuild();
                }
            }
            case 12 -> {
                if (model.pending()) {
                    model.reset();
                    rebuild();
                    screen.message((String) null);
                } else {
                    exit();
                }
            }
            case 15 -> {
                model.ruler = !model.ruler;
                rebuild();
            }
            case 16 -> find(model.lastFind, "NEXT");
            case 17 -> change(model.lastFrom, model.lastTo, false);
            case 19, 20 -> {
                if (!full) {
                    sync();
                    offset = f == 19 ? 0 : SHIFT;
                    rebuild();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // F3: straight out when nothing changed; else Enter saves and exits, F11 exits without saving.
    private void exit() {
        sync();
        if (readOnly || !model.dirty) {
            leave();
            return;
        }
        screen.confirm(tr("crt.encodedlogistics.edit.exit_changed", member), () -> {
            save();
            leave();
        }, this::leave, "crt.encodedlogistics.edit.exit_keys");
    }

    private void leave() {
        if (closing) {
            return;
        }
        closing = true;
        if (!readOnly) {
            screen.query("unlock " + library + " " + member);
        }
        screen.back();
    }

    // Enter: the line commands, then the syntax check of what changed.
    @Override
    boolean enter() {
        if (!loaded) {
            return true;
        }
        EditorModel.Line at = focusedLine();
        List<EditorModel.Command> commands = sync();
        String message = null;
        EditorModel.Line opened = null;
        if (!readOnly) {
            List<EditorModel.Line> before = new ArrayList<>(model.lines);
            message = model.apply(commands);
            // Lines this Enter inserted stay; so does the one the cursor is on.
            Set<EditorModel.Line> keep = new HashSet<>(model.lines);
            keep.removeAll(before);
            if (at != null) {
                keep.add(at);
            }
            // Typed on a new line, the last of its run: another under it, the cursor there.
            EditorModel.Line next = null;
            int index = at != null ? model.lines.indexOf(at) : -1;
            if (commands.isEmpty() && index >= 0 && at.seq == 0 && at.changed && !at.text.isBlank()
                    && (index + 1 >= model.lines.size() || model.lines.get(index + 1).seq != 0)) {
                next = EditorModel.blank();
                model.lines.add(index + 1, next);
                keep.add(next);
            }
            model.dropFresh(keep);
            // The lines typed on are numbered now; the open one shows ' until it's typed on.
            model.numberTyped();
            if (next != null) {
                rebuild();
                show(model.lines.indexOf(next), 0);
            }
            opened = next;
        }
        rebuild();
        String problem = check();
        if (opened != null) {
            // Still typing: the cursor stays on the new line (the problem only on the message line).
            show(model.lines.indexOf(opened), 0);
        }
        screen.message(message != null ? message : problem);
        return true;
    }

    // The line the cursor is on (its margin or text), or null.
    private EditorModel.@Nullable Line focusedLine() {
        CrtField focused = screen.focused();
        for (RowFields row : rows) {
            if (focused != null && (focused == row.text() || focused == row.margin())) {
                return row.item().line();
            }
        }
        return null;
    }

    // The changed statements (and their continuation lines) through the parser and compiler: the first error in one.
    private @Nullable String check() {
        if (unchecked.isEmpty()) {
            error = null;
            return null;
        }
        List<String> texts = model.texts();
        List<Stmt> statements;
        List<Diagnostic> diagnostics;
        if (pf()) {
            // A definition: each line is its own statement.
            statements = List.of();
            diagnostics = Dds.compile(texts).diagnostics();
        } else {
            statements = Parser.parse(texts).statements();
            askFormats(statements);
            diagnostics = Compiler.compileTexts(texts, resolver(), target).diagnostics();
        }
        Set<Integer> changed = new HashSet<>();
        for (EditorModel.Line line : unchecked) {
            int at = model.lines.indexOf(line);
            if (at >= 0) {
                changed.add(at);
            }
        }
        unchecked.clear();
        error = null;
        for (Diagnostic diagnostic : diagnostics) {
            if (!diagnostic.isError() || diagnostic.line() < 0 || diagnostic.line() >= model.lines.size()) {
                continue;
            }
            int first = diagnostic.line(), last = diagnostic.line();
            for (Stmt statement : statements) {
                if (statement.firstLine() <= diagnostic.line() && diagnostic.line() <= statement.lastLine()) {
                    first = statement.firstLine();
                    last = statement.lastLine();
                }
            }
            for (int i = first; i <= last; i++) {
                if (changed.contains(i)) {
                    error = model.lines.get(diagnostic.line());
                    show(diagnostic.line(), 0);
                    unchecked.add(error);
                    return diagnostic.message().toString();
                }
            }
        }
        return null;
    }

    // The editor's commands on the command line (any other command runs as ELCL): true when it was one of them.
    @Override
    boolean option(String text) {
        enter();
        List<String> words = words(text);
        String verb = words.getFirst().toUpperCase(Locale.ROOT);
        switch (verb) {
            case "FIND", "F" -> {
                if (words.size() < 2) {
                    screen.message(tr("crt.encodedlogistics.edit.find_what"));
                } else {
                    model.lastFind = words.get(1);
                    find(words.get(1), words.size() > 2 ? words.get(2).toUpperCase(Locale.ROOT) : "NEXT");
                }
            }
            case "CHANGE", "C" -> {
                if (words.size() < 3) {
                    screen.message(tr("crt.encodedlogistics.edit.change_what"));
                } else {
                    model.lastFrom = words.get(1);
                    model.lastTo = words.get(2);
                    change(words.get(1), words.get(2), words.size() > 3 && words.get(3).equalsIgnoreCase("ALL"));
                }
            }
            case "TOP" -> {
                top = 0;
                rebuild();
            }
            case "BOTTOM", "BOT" -> {
                top = Math.max(0, items.size() - rowCount());
                rebuild();
            }
            case "SAVE" -> save();
            case "FILE" -> {
                save();
                leave();
            }
            case "CANCEL", "CAN" -> {
                if (model.dirty) {
                    screen.confirm(tr("crt.encodedlogistics.edit.cancel_changed", member), this::leave, null);
                } else {
                    leave();
                }
            }
            case "RESET", "RES" -> {
                model.reset();
                error = null;
                rebuild();
                screen.message((String) null);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // Words of an editor command: blanks split them, quotes keep 'two words' together ('' is a quote).
    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean quoted = false, any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && quoted && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                word.append('\'');
                i++;
            } else if (c == '\'') {
                quoted = !quoted;
                any = true;
            } else if (c == ' ' && !quoted) {
                if (any) {
                    words.add(word.toString());
                    word.setLength(0);
                    any = false;
                }
            } else {
                word.append(c);
                any = true;
            }
        }
        if (any) {
            words.add(word.toString());
        }
        return words;
    }

    // The cursor's line (index) and column in the member, or the top shown.
    private int[] cursor() {
        CrtField focused = screen.focused();
        for (RowFields row : rows) {
            if (row.item().line() != null && (focused == row.text() || focused == row.margin())) {
                return new int[] { model.lines.indexOf(row.item().line()), focused == row.text() ? focused.cursor + (full ? 0 : offset) : -1 };
            }
        }
        for (int i = top; i < items.size(); i++) {
            if (items.get(i).line() != null) {
                return new int[] { model.lines.indexOf(items.get(i).line()), -1 };
            }
        }
        return new int[] { 0, -1 };
    }

    private void find(String what, String direction) {
        if (what.isEmpty()) {
            screen.message(tr("crt.encodedlogistics.edit.find_what"));
            return;
        }
        if (direction.equals("ALL")) {
            int count = model.count(what);
            int[] first = model.find(what, 0, -1, "FIRST");
            if (first != null) {
                show(first[0], first[1]);
            }
            screen.message(tr("crt.encodedlogistics.edit.found_all", count, what));
            return;
        }
        int[] at = cursor();
        int[] found = model.find(what, at[0], at[1], direction);
        if (found == null && direction.equals("NEXT")) {
            found = model.find(what, 0, -1, "FIRST");
        }
        if (found == null) {
            screen.message(tr("crt.encodedlogistics.edit.not_found", what));
            return;
        }
        show(found[0], found[1]);
        screen.message(tr("crt.encodedlogistics.edit.found", what));
    }

    private void change(String from, String to, boolean all) {
        if (from.isEmpty()) {
            screen.message(tr("crt.encodedlogistics.edit.change_what"));
            return;
        }
        int[] at = all ? new int[] { 0, -1 } : cursor();
        int before = model.lines.size();
        int changed = model.change(from, to, at[0], at[1] + 1, all);
        if (model.lines.size() == before && changed > 0) {
            int[] found = model.find(to, at[0], at[1], "NEXT");
            if (found != null) {
                unchecked.add(model.lines.get(found[0]));
                show(found[0], found[1]);
            }
        }
        rebuild();
        screen.message(changed == 0 ? tr("crt.encodedlogistics.edit.not_found", from) : tr("crt.encodedlogistics.edit.changed", changed, from));
    }

    // A line on the screen (its excluded run shown first), the cursor on its column.
    private void show(int index, int column) {
        if (index < 0 || index >= model.lines.size()) {
            return;
        }
        EditorModel.Line line = model.lines.get(index);
        line.excluded = 0;
        items();
        int at = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).line() == line) {
                at = i;
            }
        }
        if (at < top || at >= top + rowCount()) {
            top = Math.max(0, at - 1);
        }
        rebuild();
        for (RowFields row : rows) {
            if (row.item().line() == line && row.text() != null) {
                screen.focus(row.text());
                row.text().cursor = Math.max(0, Math.min(row.text().length - 1, column - (full ? 0 : offset)));
            }
        }
    }

    // --- Saving ---

    // The member to the server in pieces; ELC0213 comes back.
    void save() {
        sync();
        if (readOnly) {
            return;
        }
        List<SourceLine> lines = model.save(today());
        screen.query("savebegin " + library + " " + member);
        StringBuilder part = new StringBuilder();
        for (SourceLine line : lines) {
            String encoded = line.seq() + "\t" + line.date() + "\t" + line.text();
            if (part.length() + encoded.length() + 1 > CHUNK) {
                screen.query("savepart " + part);
                part.setLength(0);
            }
            if (!part.isEmpty()) {
                part.append('\n');
            }
            part.append(encoded);
        }
        if (!part.isEmpty()) {
            screen.query("savepart " + part);
        }
        screen.query("savecommit");
        rebuild();
    }

    // --- F4 on a statement ---

    private void promptStatement() {
        sync();
        if (pf()) {
            screen.message(tr("crt.encodedlogistics.edit.no_prompt_pf"));
            return;
        }
        int[] at = cursor();
        List<Stmt> statements = Parser.parse(model.texts()).statements();
        for (Stmt statement : statements) {
            if (statement.firstLine() <= at[0] && at[0] <= statement.lastLine() && statement.definition() != null) {
                String text = new Stmt(null, statement.name(), statement.params(), statement.firstLine(), statement.lastLine(), statement.definition(),
                        statement.broken()).toSource();
                int first = statement.firstLine(), last = statement.lastLine();
                String label = statement.label();
                String original = model.lines.get(first).text;
                String indent = original.substring(0, original.length() - original.stripLeading().length());
                screen.prompter(text, true, command -> replace(first, last, indent, label, command));
                return;
            }
        }
        screen.message(tr("crt.encodedlogistics.edit.no_statement"));
    }

    // The prompter's command in place of the statement's lines: its indentation and label kept, wrapped with "+"
    // before column 72.
    private void replace(int first, int last, String indent, @Nullable String label, String command) {
        List<String> wrapped = wrap(indent + (label != null ? label + ": " : ""), indent + "  ", command, VISIBLE - 1);
        List<EditorModel.Line> lines = model.lines;
        int date = lines.get(first).date;
        for (int i = last; i >= first; i--) {
            lines.remove(i);
        }
        for (int i = 0; i < wrapped.size(); i++) {
            EditorModel.Line line = new EditorModel.Line(0, wrapped.get(i), date);
            line.changed = true;
            lines.add(first + i, line);
            unchecked.add(line);
        }
        model.dirty = true;
        rebuild();
    }

    // A command over lines no longer than width: words (quoted strings kept whole) joined, " +" ending all but the last.
    static List<String> wrap(String firstPrefix, String prefix, String command, int width) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean quoted = false;
        for (char c : command.toCharArray()) {
            if (c == '\'') {
                quoted = !quoted;
            }
            if (c == ' ' && !quoted) {
                if (!word.isEmpty()) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.append(c);
            }
        }
        if (!word.isEmpty()) {
            words.add(word.toString());
        }
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder(firstPrefix);
        boolean empty = true;
        for (String w : words) {
            if (!empty && line.length() + 1 + w.length() + 2 > width) {
                out.add(line + " +");
                line = new StringBuilder(prefix);
                empty = true;
            }
            if (!empty) {
                line.append(' ');
            }
            line.append(w);
            empty = false;
        }
        out.add(line.toString());
        return out;
    }
}
