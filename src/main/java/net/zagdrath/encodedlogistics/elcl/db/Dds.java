/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.compile.Listing;

// A physical file's definition (a PF source member) to its record format: DDS-style lines, each starting with A (the
// form type; a line without one is read the same), in the columns the midrange editor puts them or anywhere:
//
//     A                                      UNIQUE
//     A          R ITEMREC                   TEXT('Item counts')
//     A            ITEM          64A         COLHDG('Item')
//     A            QTY           11S 0
//     A            PRICE          9P 2       TEXT('Unit price')
//     A            ACTIVE         1L
//     A            UPDATED         T
//     A          K ITEM
//
// R names the record format (one per file), K a key field (up to 4, in order), anything else a field (up to 50): its
// name, then length and type - A character (1-1024), S integer (1-18 digits, 0 decimals), P decimal (1-31 digits and
// its decimals), L logical (1), T game timestamp (its length ignored) - then keywords. Keywords: UNIQUE (the key is
// unique; before the R), TEXT('...') on the R or a field, COLHDG('...' ['...' ['...']]) on a field. A line with only
// keywords goes with the line before it. A* or * starts a comment line. Messages are ELC22xx with the line they're on;
// any of severity 20 or more means no file.
public final class Dds {
    public record Result(@Nullable RecordFormat format, List<Diagnostic> diagnostics) {
        public boolean ok() {
            return format != null;
        }

        public @Nullable Diagnostic firstError() {
            return diagnostics.stream().filter(Diagnostic::isError).findFirst().orElse(null);
        }
    }

    private static final String NAME = "[A-Z][A-Z0-9_@#$]{0,9}";

    private Dds() {}

    public static Result compile(List<String> lines) {
        return new Checker().compile(lines);
    }

    public static Result compileLines(List<SourceLine> source) {
        return compile(SourceLine.texts(source));
    }

    // A keyword as written: its name and its quoted values ("TEXT('Item count')" -> TEXT, [Item count]).
    private record Keyword(String name, List<String> values, boolean bracketed) {}

    private static final class Checker {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        String formatName = "", formatText = "";
        int formats;
        boolean unique, noFormat;
        int uniqueLine;
        final List<String[]> keys = new ArrayList<>();
        final List<FieldDef> fields = new ArrayList<>();
        final List<Integer> fieldLines = new ArrayList<>();
        // What a keywords-only line goes with: 0 the file, 1 the record, 2 the last field.
        int level;

        void report(int line, String id, Object... data) {
            diagnostics.add(Diagnostic.of(line, id, data));
        }

        Result compile(List<String> lines) {
            for (int i = 0; i < lines.size(); i++) {
                line(i, lines.get(i));
            }
            int last = Math.max(0, lines.size() - 1);
            if (formats == 0 && !noFormat) {
                report(last, "ELC2225");
            }
            if (fields.size() > RecordFormat.MAX_FIELDS) {
                report(fieldLines.get(RecordFormat.MAX_FIELDS), "ELC2224", "fields", RecordFormat.MAX_FIELDS);
            }
            if (formats > 0 && fields.isEmpty()) {
                report(last, "ELC2224", "fields", 0);
            }
            List<String> key = new ArrayList<>();
            Set<String> names = new HashSet<>();
            fields.forEach(field -> names.add(field.name()));
            for (String[] k : keys) {
                int line = Integer.parseInt(k[1]);
                if (!names.contains(k[0])) {
                    report(line, "ELC2223", k[0]);
                } else if (key.contains(k[0])) {
                    report(line, "ELC2221", k[0]);
                } else {
                    key.add(k[0]);
                }
            }
            if (keys.size() > RecordFormat.MAX_KEY) {
                report(Integer.parseInt(keys.get(RecordFormat.MAX_KEY)[1]), "ELC2224", "key fields", RecordFormat.MAX_KEY);
            }
            if (unique && key.isEmpty() && formats > 0) {
                report(uniqueLine, "ELC2226", "UNIQUE");
            }
            diagnostics.sort(Comparator.comparingInt(Diagnostic::line));
            boolean failed = diagnostics.stream().anyMatch(Diagnostic::isError);
            RecordFormat format = failed ? null : new RecordFormat(formatName, formatText, fields, key, unique && !key.isEmpty());
            return new Result(format, List.copyOf(diagnostics));
        }

        void line(int at, String text) {
            String body = text.strip();
            if (body.isEmpty() || body.startsWith("*") || body.toUpperCase(Locale.ROOT).startsWith("A*")) {
                return;
            }
            List<String> tokens;
            try {
                tokens = tokens(body);
            } catch (IllegalArgumentException e) {
                report(at, "ELC2220", e.getMessage());
                return;
            }
            if (!tokens.isEmpty() && tokens.getFirst().equalsIgnoreCase("A")) {
                tokens.removeFirst();
            }
            if (tokens.isEmpty()) {
                return;
            }
            String first = tokens.getFirst().toUpperCase(Locale.ROOT);
            // R and K name a record format or key field, unless what follows is a length and type (a field named R or K).
            boolean named = tokens.size() > 1 && !keywordLike(tokens.get(1)) && !tokens.get(1).matches("\\d+[A-Za-z]\\d*");
            if (first.equals("R") && named) {
                record(at, tokens);
            } else if (first.equals("K") && named) {
                key(at, tokens);
            } else if (keywordLike(tokens.getFirst())) {
                keywords(at, tokens, 0);
            } else {
                field(at, tokens);
            }
        }

        // A keyword: a name with (...) after it, or UNIQUE alone.
        static boolean keywordLike(String token) {
            String upper = token.toUpperCase(Locale.ROOT);
            return upper.equals("UNIQUE") || upper.matches("[A-Z]+\\(.*\\)");
        }

        void record(int at, List<String> tokens) {
            formats++;
            if (formats > 1) {
                report(at, "ELC2224", "record formats", 1);
                return;
            }
            String name = tokens.get(1).toUpperCase(Locale.ROOT);
            if (!name.matches(NAME)) {
                report(at, "ELC2228", tokens.get(1));
            }
            if (!fields.isEmpty()) {
                report(at, "ELC2220", "R");
            }
            formatName = name;
            level = 1;
            keywords(at, tokens, 2);
        }

        void key(int at, List<String> tokens) {
            String name = tokens.get(1).toUpperCase(Locale.ROOT);
            if (!name.matches(NAME)) {
                report(at, "ELC2228", tokens.get(1));
                return;
            }
            keys.add(new String[] { name, Integer.toString(at) });
            // Keywords on a key line (DESCEND and the like) aren't taken.
            for (int i = 2; i < tokens.size(); i++) {
                report(at, "ELC2226", keyword(tokens.get(i)).name());
            }
            level = 3;
        }

        void field(int at, List<String> tokens) {
            if (formats == 0 && !noFormat) {
                report(at, "ELC2225");
                noFormat = true;
            }
            String name = tokens.getFirst().toUpperCase(Locale.ROOT);
            if (!name.matches(NAME)) {
                report(at, "ELC2228", tokens.getFirst());
                return;
            }
            if (fields.stream().anyMatch(field -> field.name().equals(name))) {
                report(at, "ELC2221", name);
                return;
            }
            int index = 1;
            String spec = index < tokens.size() ? tokens.get(index).toUpperCase(Locale.ROOT) : "";
            if (!spec.matches("\\d*[A-Z]\\d*")) {
                report(at, "ELC2227", spec.isEmpty() ? "*NONE" : spec, name);
                return;
            }
            index++;
            int letter = 0;
            while (letter < spec.length() && Character.isDigit(spec.charAt(letter))) {
                letter++;
            }
            String lengthText = spec.substring(0, letter), decimalsText = spec.substring(letter + 1);
            // "11S 0": the decimals on their own after it.
            if (decimalsText.isEmpty() && index < tokens.size() && tokens.get(index).matches("\\d{1,2}")) {
                decimalsText = tokens.get(index);
                index++;
            }
            FieldDef.Type type = FieldDef.Type.of(spec.charAt(letter));
            if (type == null) {
                report(at, "ELC2227", String.valueOf(spec.charAt(letter)), name);
                return;
            }
            int length = lengthText.isEmpty() ? -1 : Integer.parseInt(lengthText);
            int decimals = decimalsText.isEmpty() ? 0 : Integer.parseInt(decimalsText);
            boolean ok = switch (type) {
                case CHAR -> length >= 1 && length <= FieldDef.MAX_CHAR && decimalsText.isEmpty();
                case INT -> length >= 1 && length <= FieldDef.MAX_INT && decimals == 0;
                case DEC -> length >= 1 && length <= FieldDef.MAX_DEC && decimals <= length;
                case LGL -> (length == -1 || length == 1) && decimalsText.isEmpty();
                case TIME -> decimalsText.isEmpty();
            };
            if (!ok) {
                report(at, "ELC2222", lengthText.isEmpty() ? "*NONE" : lengthText + (decimalsText.isEmpty() ? "" : " " + decimalsText), name, type.code);
                return;
            }
            if (type == FieldDef.Type.LGL) {
                length = 1;
            } else if (type == FieldDef.Type.TIME) {
                length = FieldDef.TIME_LENGTH;
                decimals = 0;
            }
            fields.add(new FieldDef(name, type, length, decimals, "", List.of()));
            fieldLines.add(at);
            level = 2;
            keywords(at, tokens, index);
        }

        // Keywords from tokens[from] on, for what the line (or, keywords alone, the line before) defines.
        void keywords(int at, List<String> tokens, int from) {
            for (int i = from; i < tokens.size(); i++) {
                Keyword keyword = keyword(tokens.get(i));
                switch (keyword.name()) {
                    case "UNIQUE" -> {
                        if (level > 1 || keyword.bracketed()) {
                            report(at, "ELC2226", "UNIQUE");
                        } else {
                            unique = true;
                            uniqueLine = at;
                        }
                    }
                    case "TEXT" -> {
                        if (keyword.values().size() != 1 || level == 3) {
                            report(at, "ELC2226", "TEXT");
                        } else if (level == 2) {
                            FieldDef last = fields.getLast();
                            fields.set(fields.size() - 1, new FieldDef(last.name(), last.type(), last.length(), last.decimals(), keyword.values().getFirst(),
                                    last.headings()));
                        } else {
                            formatText = keyword.values().getFirst();
                        }
                    }
                    case "COLHDG" -> {
                        if (level != 2 || keyword.values().isEmpty()) {
                            report(at, "ELC2226", "COLHDG");
                        } else if (keyword.values().size() > 3) {
                            report(at, "ELC2224", "COLHDG lines", 3);
                        } else {
                            FieldDef last = fields.getLast();
                            fields.set(fields.size() - 1, new FieldDef(last.name(), last.type(), last.length(), last.decimals(), last.text(),
                                    keyword.values()));
                        }
                    }
                    default -> report(at, "ELC2226", keyword.name().isEmpty() ? tokens.get(i) : keyword.name());
                }
            }
        }

        // NAME('a' 'b'): its name and quoted values ('' a quote).
        static Keyword keyword(String token) {
            int open = token.indexOf('(');
            if (open < 0) {
                return new Keyword(token.toUpperCase(Locale.ROOT), List.of(), false);
            }
            String name = token.substring(0, open).toUpperCase(Locale.ROOT);
            String inside = token.substring(open + 1, token.length() - 1);
            List<String> values = new ArrayList<>();
            int i = 0;
            while (i < inside.length()) {
                char c = inside.charAt(i);
                if (c == ' ') {
                    i++;
                } else if (c == '\'') {
                    StringBuilder value = new StringBuilder();
                    i++;
                    while (i < inside.length()) {
                        if (inside.charAt(i) == '\'') {
                            if (i + 1 < inside.length() && inside.charAt(i + 1) == '\'') {
                                value.append('\'');
                                i += 2;
                                continue;
                            }
                            break;
                        }
                        value.append(inside.charAt(i++));
                    }
                    i++;
                    values.add(value.toString());
                } else {
                    int end = inside.indexOf(' ', i);
                    values.add(inside.substring(i, end < 0 ? inside.length() : end));
                    i = end < 0 ? inside.length() : end;
                }
            }
            return new Keyword(name, values, true);
        }

        // The line's words: blanks split them, except inside quotes and parentheses (TEXT('a b') is one).
        static List<String> tokens(String line) {
            List<String> tokens = new ArrayList<>();
            StringBuilder token = new StringBuilder();
            int depth = 0;
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\'') {
                    quoted = !quoted;
                } else if (!quoted && c == '(') {
                    depth++;
                } else if (!quoted && c == ')') {
                    depth--;
                    if (depth < 0) {
                        throw new IllegalArgumentException(")");
                    }
                }
                if (c == ' ' && depth == 0 && !quoted) {
                    if (!token.isEmpty()) {
                        tokens.add(token.toString());
                        token.setLength(0);
                    }
                } else {
                    token.append(c);
                }
            }
            if (quoted || depth != 0) {
                throw new IllegalArgumentException(token.toString());
            }
            if (!token.isEmpty()) {
                tokens.add(token.toString());
            }
            return tokens;
        }
    }

    // --- The listing (CRTPF, CHGPF: a spooled file) ---

    // when: the game's day and time; end: the message it ends with (ELC2230 created, ELC2231 changed, ELC2239 not).
    public static List<String> listing(String library, String file, String when, String system, List<SourceLine> source, Result result, ElclMessage end) {
        List<String> out = new ArrayList<>();
        out.add("DDS Compile Listing   " + library + "/" + file + "   " + when + "   " + system);
        out.add(" SEQNBR  " + Listing.RULER + "   Date");
        for (SourceLine line : source) {
            out.add(" " + line.seqText() + " " + pad(line.text(), SourceLine.WIDTH) + "   " + line.dateText());
        }
        out.add("");
        RecordFormat format = result.format();
        if (format != null) {
            out.add(" ".repeat(21) + "Record Format");
            out.add("  Format " + format.name() + "   Fields " + format.fields().size() + "   Record length " + format.recordLength());
            out.add("  Key    " + (format.keyed() ? String.join(" ", format.key()) + (format.unique() ? "   (UNIQUE)" : "") : "*NONE (arrival order)"));
            out.add("  Field       Type   Length  Dec  Text");
            for (FieldDef field : format.fields()) {
                out.add("  " + pad(field.name(), 10) + "  " + field.type().code + "    " + String.format(Locale.ROOT, "%7d", field.length()) + "  "
                        + (field.type() == FieldDef.Type.DEC || field.type() == FieldDef.Type.INT ? String.format(Locale.ROOT, "%3d", field.decimals()) : "   ")
                        + "  " + field.text());
            }
            out.add("");
        }
        out.add(" ".repeat(21) + "Message Summary");
        out.add("  Seq      Msg ID   Sev  Text");
        int[] bySeverity = new int[4];
        for (Diagnostic diagnostic : result.diagnostics()) {
            ElclMessage message = diagnostic.message();
            String seq = diagnostic.line() >= 0 && diagnostic.line() < source.size() ? source.get(diagnostic.line()).seqText() : "       ";
            out.add("  " + seq + "  " + message.id() + "  " + String.format(Locale.ROOT, "%3d", message.severity()) + "  " + message.text());
            bySeverity[Math.min(3, message.severity() / 10)]++;
        }
        out.add(String.format(Locale.ROOT, "  Total %d  Info %d  Warning %d  Error %d  Severe %d", result.diagnostics().size(), bySeverity[0],
                bySeverity[1], bySeverity[2], bySeverity[3]));
        out.add("  " + end.id() + "  " + end.text());
        return out;
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }
}
