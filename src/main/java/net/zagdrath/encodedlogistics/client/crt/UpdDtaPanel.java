/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.net.CrtRequestPayload;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// UPDDTA (Update Data, WRKF / WRKMBR 2=Change data): a file's records one at a time, as a midrange data file utility
// does. Rows 2-4: the file, the mode (CHANGE or ENTRY), the record format, which record of how many, and (change mode,
// a keyed file) Position to key. Then a row a field from row 8: its name, its text (or column heading) with leaders,
// its value in a field as long as the field takes (it scrolls past 30), and its definition (64A, 9P 2) dim; more fields
// than fit go on further pages (PageUp / PageDown).
//
// Change mode shows a record: change its values and press Enter to change it (no change: on to the next record); F7 /
// F8 the record before or after (PageUp / PageDown too when the fields fit one page); F11 deletes it (asks first); a key
// typed in Position to and Enter goes to the first record at or after it. F6 (or an empty file) goes to entry mode:
// type a new record's values and press Enter to add it, the fields cleared for the next; F10 back to change mode.
// Values are checked by type here first (FieldDef.convert: a number for S and P, 1 / 0 for L, a timestamp for T, blank
// for the time it's written) and the server checks them again (ELC2209, the cursor to the field it names).
final class UpdDtaPanel extends CrtPanel {
    private static final int FIRST = 8, ROWS = 12, LABEL = 12, VALUE = 39, SHOWN = 30;
    private static final Pattern FIELD = Pattern.compile("field (\\S+?)\\.?$");

    private record Field(FieldDef def, String label, CrtField input) {}

    private final String spec;
    private String qualified, format = "";
    private final CrtField position = new CrtField(4, 23, 30, 64, "");
    private final List<Field> inputs = new ArrayList<>();
    private List<String> shownValues = List.of();
    private boolean entry, keyed, loaded;
    private long rrn;
    private int place, total, page, stepping;

    UpdDtaPanel(CrtTerminal screen, String spec) {
        super(screen);
        this.spec = spec;
        this.qualified = spec.toUpperCase(Locale.ROOT);
    }

    @Override
    String id() {
        return "UPDDTA";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.upddta.title");
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return tr(entry ? "crt.encodedlogistics.fkeys.upddta_entry" : "crt.encodedlogistics.fkeys.upddta");
    }

    @Override
    void shown() {
        if (!loaded) {
            screen.query("filedesc " + spec);
        }
    }

    @Override
    void refresh() {
        if (entry) {
            clear();
        } else if (rrn > 0) {
            screen.query("record " + spec + " *RRN " + rrn);
        } else {
            screen.query("record " + spec + " *FIRST");
        }
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, "filedesc")) {
            describe(response);
            return;
        }
        if (!answers(response, "record") && !answers(response, "putrecord") && !answers(response, "addrecord") && !answers(response, "delrecord")) {
            return;
        }
        String topic = response.topic();
        response.message().ifPresent(message -> {
            screen.message(message);
            focusNamed(message.getString());
        });
        if (response.lines().isEmpty()) {
            return;
        }
        TerminalLine head = response.lines().getFirst();
        long found = Long.parseLong(cell(head, 0));
        total = Integer.parseInt(cell(head, 2));
        if (topic.equals("addrecord")) {
            // Added: the fields cleared for the next.
            clear();
            screen.message(tr("crt.encodedlogistics.upddta.added", total));
            return;
        }
        if (found == 0) {
            if (total == 0) {
                // Nothing to change: entry mode.
                entry(true);
            } else if (topic.equals("record") && response.message().isEmpty()) {
                screen.message(tr(stepping < 0 ? "crt.encodedlogistics.msg.at_top" : "crt.encodedlogistics.msg.at_bottom"));
            }
            return;
        }
        rrn = found;
        place = Integer.parseInt(cell(head, 1));
        List<String> values = new ArrayList<>();
        if (response.lines().size() > 1) {
            TerminalLine row = response.lines().get(1);
            for (int i = 0; i < inputs.size(); i++) {
                values.add(cell(row, i));
            }
        }
        show(values);
        if (topic.equals("putrecord")) {
            screen.message(tr("crt.encodedlogistics.upddta.changed"));
        } else if (topic.equals("delrecord")) {
            screen.message(tr("crt.encodedlogistics.upddta.deleted"));
        }
    }

    // The format: a row a field (FileQueries' filedesc), then the first record.
    private void describe(CrtResponsePayload response) {
        response.message().ifPresent(screen::message);
        if (response.lines().isEmpty()) {
            screen.back();
            return;
        }
        TerminalLine head = response.lines().getFirst();
        qualified = cell(head, 0) + "/" + cell(head, 1);
        format = cell(head, 4);
        keyed = !cell(head, 12).isEmpty();
        inputs.clear();
        for (TerminalLine line : response.lines().subList(1, response.lines().size())) {
            FieldDef.Type type = FieldDef.Type.of(cell(line, 1).charAt(0));
            List<String> heading = new ArrayList<>();
            for (int i = 6; i < line.cells().size(); i++) {
                heading.add(cell(line, i));
            }
            FieldDef def = new FieldDef(cell(line, 0), type, Integer.parseInt(cell(line, 2)), Integer.parseInt(cell(line, 3)), cell(line, 5), heading);
            String label = !def.text().isEmpty() ? def.text() : String.join(" ", heading);
            CrtField input = new CrtField(0, VALUE, Math.min(SHOWN, def.width()), def.width(), "");
            if (def.numeric()) {
                input.numeric();
            }
            inputs.add(new Field(def, label, input));
        }
        loaded = true;
        page = 0;
        layout();
        screen.query("record " + spec + " *FIRST");
    }

    // The fields of the page shown, on their rows.
    private void layout() {
        fields.clear();
        if (!entry && keyed) {
            fields.add(position);
        }
        for (int i = 0; i < inputs.size(); i++) {
            Field field = inputs.get(i);
            if (i / ROWS == page) {
                CrtField at = new CrtField(FIRST + i % ROWS, VALUE, field.input().length, field.input().capacity, field.input().value);
                if (field.def().numeric()) {
                    at.numeric();
                }
                inputs.set(i, new Field(field.def(), field.label(), at));
                fields.add(at);
            }
        }
        screen.focusFirst();
        if (!entry && keyed && fields.size() > 1) {
            screen.focus(fields.get(1));
        }
    }

    private int pages() {
        return Math.max(1, (inputs.size() + ROWS - 1) / ROWS);
    }

    private void show(List<String> values) {
        shownValues = List.copyOf(values);
        for (int i = 0; i < inputs.size(); i++) {
            inputs.get(i).input().set(i < values.size() ? values.get(i) : "");
        }
        layout();
    }

    private void clear() {
        List<String> blanks = new ArrayList<>();
        inputs.forEach(field -> blanks.add(""));
        show(blanks);
    }

    private void entry(boolean on) {
        entry = on;
        page = 0;
        if (on) {
            clear();
        } else {
            screen.query("record " + spec + (rrn > 0 ? " *RRN " + rrn : " *FIRST"));
        }
        layout();
    }

    // The cursor to the field an error names ("... not valid for field QTY.").
    private void focusNamed(String message) {
        Matcher matcher = FIELD.matcher(message);
        if (!matcher.find()) {
            return;
        }
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).def().name().equals(matcher.group(1))) {
                if (i / ROWS != page) {
                    page = i / ROWS;
                    layout();
                }
                screen.focus(inputs.get(i).input());
            }
        }
    }

    @Override
    void draw(CrtGrid grid) {
        grid.put(2, 1, tr("crt.encodedlogistics.dsppfm.file"));
        grid.put(2, 21, qualified, CrtGrid.BRIGHT);
        grid.put(2, 48, tr("crt.encodedlogistics.upddta.mode"));
        grid.put(2, 64, tr(entry ? "crt.encodedlogistics.upddta.entry" : "crt.encodedlogistics.upddta.change"), CrtGrid.BRIGHT);
        grid.put(3, 1, tr("crt.encodedlogistics.upddta.format"));
        grid.put(3, 21, format, CrtGrid.BRIGHT);
        grid.put(3, 48, tr(entry ? "crt.encodedlogistics.dsppfm.records" : "crt.encodedlogistics.upddta.record"));
        grid.put(3, 64, entry ? String.format(Locale.ROOT, "%,d", total) : String.format(Locale.ROOT, "%,d of %,d", place, total), CrtGrid.BRIGHT);
        if (!entry && keyed) {
            grid.put(4, 1, tr("crt.encodedlogistics.dsppfm.position_key"));
        }
        grid.put(6, 1, tr(entry ? "crt.encodedlogistics.upddta.type_entry" : "crt.encodedlogistics.upddta.type_change"));
        for (int i = page * ROWS; i < Math.min(inputs.size(), (page + 1) * ROWS); i++) {
            Field field = inputs.get(i);
            int row = FIRST + i % ROWS;
            grid.put(row, 1, CrtGrid.pad(field.def().name(), 10));
            grid.put(row, LABEL, PrompterPanel.leaders(field.label().length() > 22 ? field.label().substring(0, 22) : field.label(), LABEL, LABEL + 23)
                    + " :");
            grid.put(row, VALUE + field.input().length + 1, field.def().definition(), CrtGrid.DIM);
        }
        if (pages() > 1) {
            grid.right(20, tr(page + 1 < pages() ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
        }
    }

    // --- Keys ---

    @Override
    void page(int direction) {
        if (pages() > 1) {
            int next = page + direction;
            if (next < 0 || next >= pages()) {
                screen.message(tr(direction > 0 ? "crt.encodedlogistics.msg.at_bottom" : "crt.encodedlogistics.msg.at_top"));
                return;
            }
            page = next;
            layout();
        } else if (!entry) {
            step(direction);
        }
    }

    // The record before or after this one.
    private void step(int direction) {
        if (rrn > 0) {
            stepping = direction;
            screen.query("record " + spec + (direction > 0 ? " *NEXT " : " *PREV ") + rrn);
        }
    }

    @Override
    boolean functionKey(int f) {
        switch (f) {
            case 6 -> entry(true);
            case 7, 8 -> {
                if (!entry) {
                    step(f == 8 ? 1 : -1);
                }
            }
            case 10 -> {
                if (entry) {
                    entry(false);
                }
            }
            case 11 -> {
                if (!entry && rrn > 0) {
                    screen.confirm(tr("crt.encodedlogistics.upddta.delete", place, qualified), () -> screen.query("delrecord " + spec + " " + rrn), null);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // Enter: Position to, else the values checked and the record changed (or added).
    @Override
    boolean enter() {
        if (!loaded) {
            return true;
        }
        String key = position.trimmed();
        if (!entry && !key.isEmpty()) {
            position.set("");
            screen.query("record " + spec + " *KEY " + key);
            return true;
        }
        List<String> values = new ArrayList<>();
        for (Field field : inputs) {
            String typed = field.input().value.stripTrailing();
            try {
                field.def().convert(typed);
            } catch (ElclException e) {
                screen.message(e.elclMessage().toString());
                focusNamed(e.elclMessage().text());
                return true;
            }
            values.add(typed);
        }
        if (!entry && values.equals(shownValues)) {
            // Nothing changed: on to the next record.
            step(1);
            return true;
        }
        String request = (entry ? "addrecord " + spec : "putrecord " + spec + " " + rrn) + "\t" + String.join("\t", values);
        if (request.length() > CrtRequestPayload.MAX_TEXT) {
            screen.message(tr("crt.encodedlogistics.upddta.too_long"));
            return true;
        }
        screen.query(request);
        return true;
    }

    @Override
    @Nullable String helpField(@Nullable CrtField field) {
        return field == position ? "position" : field != null ? "value" : null;
    }
}
