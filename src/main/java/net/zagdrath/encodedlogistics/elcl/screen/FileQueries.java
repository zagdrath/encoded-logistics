/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.compile.FileResolver;
import net.zagdrath.encodedlogistics.elcl.db.DbRecord;
import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.elcl.db.Query;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.db.Report;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;

// The database screens' data (ScreenQueries hands them over): a file is named LIB/NAME, or NAME (the user's library
// list), and comes back qualified. Requests and what they answer:
//  files L                         name, attribute, records, text, size, created, source, changed (*), system (1 / 0)
//  filedesc F                      library, name, attribute, text, format, format text, source, created, records, limit,
//                                  record length, size, key (blank-joined), unique, changed, system; then a row a field:
//                                  name, type, length, decimals, key position, text, its column heading's lines
//  filedata F from | *KEY value   Display Physical File Member's records from a place (a 0-based index, or the first
//                                  at or after a key): qualified name, records, from, keyed, heading lines, of; the
//                                  heading lines; a line a record, up to PAGE
//  runqry F from<TAB>QRYSLT<TAB>SORT   RUNQRY's display, the same (records: those selected, of: in the file)
//  record F *FIRST | *LAST | *NEXT rrn | *PREV rrn | *RRN rrn | *KEY value (the first at or after it)
//                                  Update Data's record: rrn, place (1-based), records; then its values as text (none
//                                  when there's no such record: rrn 0)
//  putrecord F rrn<TAB>values...   changed;  addrecord F<TAB>values...  added;  delrecord F rrn  deleted (the next shown)
//                                  (each answers as record does, with the record written)
//  fileformat LIB FILE             the editor's DCLF check: the key and the format as text (RecordFormat.save)
final class FileQueries {
    // Records a filedata or runqry answer carries.
    static final int PAGE = 200, PAGE_CHARACTERS = 120_000;

    private FileQueries() {}

    private static TerminalLine row(Object... cells) {
        TerminalLine.Builder builder = TerminalLine.builder();
        for (Object cell : cells) {
            builder.left(String.valueOf(cell), 0);
        }
        return builder.build();
    }

    private static FileService files() {
        return ElclServices.files();
    }

    // LIB/NAME or NAME: {library, name} of a file that's there (ELC0201 / ELC2205).
    static String[] file(ElclSystem system, String user, String spec) throws ElclException {
        String value = spec.strip().toUpperCase(Locale.ROOT);
        int slash = value.indexOf('/');
        String[] found = files().resolve(system, user, slash >= 0 ? value.substring(0, slash) : "*LIBL", slash >= 0 ? value.substring(slash + 1) : value);
        files().file(system, found[0], found[1]);
        return found;
    }

    static @Nullable TerminalOutput handle(TerminalContext context, ElclSystem system, String verb, String text) throws ElclException {
        String user = context.user();
        FileService.Who who = new FileService.Who(user, context.allowed(RackPermission.VIEW));
        // The words after the verb; the text after the file (key values and tab-separated values keep their blanks).
        String rest = text.length() > verb.length() ? text.substring(verb.length() + 1) : "";
        String spec = rest.split("[ \t]", 2)[0];
        String after = rest.length() > spec.length() ? rest.substring(spec.length() + 1) : "";
        return switch (verb) {
            case "files" -> files(system, spec);
            case "filedesc" -> describe(system, file(system, user, spec));
            case "filedata" -> data(system, who, file(system, user, spec), after);
            case "runqry" -> query(system, who, file(system, user, spec), after);
            case "record" -> record(system, who, file(system, user, spec), after);
            case "putrecord", "addrecord" -> write(system, who, file(system, user, spec), after, verb.equals("addrecord"));
            case "delrecord" -> {
                // Deleted; the record after it shown (or, the last gone, the one before).
                String[] file = file(system, user, spec);
                RecordFormat format = files().format(system, file[0], file[1]);
                DbRecord gone = files().record(system, who, file[0], file[1], Long.parseLong(after.strip()));
                if (gone == null) {
                    throw new ElclException("ELC2204", file[0] + "/" + file[1]);
                }
                DbRecord next = files().next(system, who, file[0], file[1], gone.position(format));
                DbRecord shown = next != null ? next : files().previous(system, who, file[0], file[1], gone.position(format));
                files().delete(system, who, file[0], file[1], gone.rrn());
                yield shown(system, file, format, shown);
            }
            case "fileformat" -> {
                String[] words = rest.split(" ");
                String library = words[0].toUpperCase(Locale.ROOT), name = words.length > 1 ? words[1].toUpperCase(Locale.ROOT) : "";
                String[] found = files().resolve(system, user, library, name);
                yield new TerminalOutput().line(row(FileResolver.key(library, name), files().format(system, found[0], found[1]).save()));
            }
            default -> null;
        };
    }

    // --- Work with Files ---

    private static TerminalOutput files(ElclSystem system, String library) throws ElclException {
        TerminalOutput out = new TerminalOutput();
        for (FileService.File file : files().files(system, library.toUpperCase(Locale.ROOT))) {
            out.line(row(file.name(), file.attribute(), file.records(), file.text(), file.size(), file.created(), file.source(), file.changed() ? "*" : "",
                    file.system() ? "1" : "0"));
        }
        return out;
    }

    // --- Display File Description ---

    private static TerminalOutput describe(ElclSystem system, String[] name) throws ElclException {
        FileService.File file = files().file(system, name[0], name[1]);
        RecordFormat format = file.format();
        TerminalOutput out = new TerminalOutput();
        out.line(row(file.library(), file.name(), file.attribute(), file.text(), format.name(), format.text(), file.source(), file.created(), file.records(),
                net.zagdrath.encodedlogistics.elcl.store.ElclConfig.maxRecordsPerFile(), format.recordLength(), file.size(), String.join(" ", format.key()),
                format.unique() ? "1" : "0", file.changed() ? "1" : "0", file.system() ? "1" : "0"));
        for (FieldDef field : format.fields()) {
            int key = format.key().indexOf(field.name());
            List<Object> cells = new ArrayList<>(List.of(field.name(), String.valueOf(field.type().code), field.length(), field.decimals(),
                    key < 0 ? "" : key + 1, field.text()));
            cells.addAll(field.headings());
            out.line(row(cells.toArray()));
        }
        return out;
    }

    // --- Display Physical File Member, RUNQRY's display ---

    private static TerminalOutput page(String qualified, RecordFormat format, List<DbRecord> records, int from, int of) {
        TerminalOutput out = new TerminalOutput();
        List<String> headings = Report.headings(format);
        int start = Math.max(0, Math.min(from, Math.max(0, records.size() - 1)));
        out.line(row(qualified, records.size(), start, format.keyed() ? "1" : "0", headings.size(), of));
        headings.forEach(heading -> out.line(row(heading)));
        // Up to PAGE records, fewer when they're wide (the answer stays a reasonable size).
        int characters = 0;
        for (int i = start; i < Math.min(records.size(), start + PAGE) && characters < PAGE_CHARACTERS; i++) {
            String line = Report.line(format, records.get(i).values());
            characters += line.length();
            out.line(row(line));
        }
        return out;
    }

    private static TerminalOutput data(ElclSystem system, FileService.Who who, String[] file, String place) throws ElclException {
        RecordFormat format = files().format(system, file[0], file[1]);
        List<DbRecord> records = files().records(system, who, file[0], file[1]);
        int from;
        if (place.startsWith("*KEY ")) {
            from = files().position(system, who, file[0], file[1], key(format, place.substring(5)));
        } else {
            from = place.isBlank() ? 0 : Integer.parseInt(place.strip());
        }
        return page(file[0] + "/" + file[1], format, records, from, records.size());
    }

    // A key typed on a screen: its fields' values, blank-separated, the last taking the rest of the text (ELC2209 for a
    // value that doesn't fit its field).
    private static Object[] key(RecordFormat format, String typed) throws ElclException {
        int[] indexes = format.keyIndexes();
        String[] values = typed.strip().split(" +", Math.max(1, indexes.length));
        Object[] key = new Object[Math.min(values.length, indexes.length)];
        for (int i = 0; i < key.length; i++) {
            key[i] = format.fields().get(indexes[i]).convert(values[i]);
        }
        return key;
    }

    private static TerminalOutput query(ElclSystem system, FileService.Who who, String[] file, String spec) throws ElclException {
        String[] parts = spec.split("\t", -1);
        int from = parts[0].isBlank() ? 0 : Integer.parseInt(parts[0].strip());
        String qryslt = parts.length > 1 ? parts[1] : "*ALL";
        List<String> sort = parts.length > 2 && !parts[2].isBlank() ? List.of(parts[2].strip().split(" +")) : List.of();
        String qualified = file[0] + "/" + file[1];
        RecordFormat format = files().format(system, file[0], file[1]);
        List<DbRecord> all = files().records(system, who, file[0], file[1]);
        List<DbRecord> selected = Query.run(all, Query.selection(qryslt, format, qualified), Query.sort(sort, format, qualified));
        return page(qualified, format, selected, from, all.size());
    }

    // --- Update Data ---

    private static TerminalOutput record(ElclSystem system, FileService.Who who, String[] file, String which) throws ElclException {
        String[] words = which.strip().split(" ", 2);
        String verb = words[0].toUpperCase(Locale.ROOT), arg = words.length > 1 ? words[1] : "";
        RecordFormat format = files().format(system, file[0], file[1]);
        DbRecord record = switch (verb) {
            case "*LAST" -> files().previous(system, who, file[0], file[1], null);
            case "*NEXT", "*PREV" -> {
                DbRecord at = files().record(system, who, file[0], file[1], Long.parseLong(arg.strip()));
                if (at == null) {
                    yield files().next(system, who, file[0], file[1], null);
                }
                yield verb.equals("*NEXT") ? files().next(system, who, file[0], file[1], at.position(format))
                        : files().previous(system, who, file[0], file[1], at.position(format));
            }
            case "*RRN" -> files().record(system, who, file[0], file[1], Long.parseLong(arg.strip()));
            case "*KEY" -> {
                // Position to: the first record at or after the key.
                int at = files().position(system, who, file[0], file[1], key(format, arg));
                List<DbRecord> records = files().records(system, who, file[0], file[1]);
                if (at < 0 || at >= records.size()) {
                    throw new ElclException("ELC2202", arg.strip(), file[0] + "/" + file[1]);
                }
                yield records.get(at);
            }
            default -> files().next(system, who, file[0], file[1], null);
        };
        return shown(system, file, format, record);
    }

    // A record as Update Data shows it: rrn, place, records; its values.
    private static TerminalOutput shown(ElclSystem system, String[] file, RecordFormat format, @Nullable DbRecord record) throws ElclException {
        FileService.File info = files().file(system, file[0], file[1]);
        TerminalOutput out = new TerminalOutput();
        if (record == null) {
            out.line(row(0, 0, info.records()));
            return out;
        }
        int place = 1;
        for (DbRecord other : files().records(system, new FileService.Who("", true), file[0], file[1])) {
            if (other.rrn() == record.rrn()) {
                break;
            }
            place++;
        }
        out.line(row(record.rrn(), place, info.records()));
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < format.fields().size(); i++) {
            values.add(format.fields().get(i).text(record.value(i)));
        }
        out.line(row(values.toArray()));
        return out;
    }

    // putrecord / addrecord: the values typed, each as its field takes it (ELC2209 names the field).
    private static TerminalOutput write(ElclSystem system, FileService.Who who, String[] file, String text, boolean add) throws ElclException {
        String[] parts = text.split("\t", -1);
        int first = add ? 0 : 1;
        RecordFormat format = files().format(system, file[0], file[1]);
        Object[] values = format.blank();
        for (int i = 0; i < values.length; i++) {
            String typed = first + i < parts.length ? parts[first + i] : "";
            values[i] = format.fields().get(i).convert(typed);
        }
        DbRecord written = add ? files().write(system, who, file[0], file[1], values)
                : files().update(system, who, file[0], file[1], Long.parseLong(parts[0].strip()), values);
        return shown(system, file, format, written);
    }
}
