/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import java.util.ArrayList;
import java.util.List;

// Records laid out in columns, a field to a column, as Display Physical File Member, RUNQRY's display and its printed
// report show them: each column as wide as its values or its heading (COLHDG's lines, else the field's name), two
// blanks between; text left, numbers right; up to three heading lines, as many as the longest COLHDG.
public final class Report {
    public static final String END = "* * * * *  E N D  O F  R E P O R T  * * * * *";

    private Report() {}

    public static int[] widths(RecordFormat format) {
        int[] widths = new int[format.fields().size()];
        for (int i = 0; i < widths.length; i++) {
            FieldDef field = format.fields().get(i);
            int width = field.width();
            for (String line : field.heading()) {
                width = Math.max(width, line.length());
            }
            widths[i] = width;
        }
        return widths;
    }

    public static List<String> headings(RecordFormat format) {
        int[] widths = widths(format);
        int rows = 1;
        for (FieldDef field : format.fields()) {
            rows = Math.max(rows, field.heading().size());
        }
        List<String> lines = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < widths.length; i++) {
                FieldDef field = format.fields().get(i);
                List<String> heading = field.heading();
                // Headings sit on the bottom lines, numbers' to the right.
                int at = r - (rows - heading.size());
                String text = at >= 0 ? heading.get(at) : "";
                line.append(i > 0 ? "  " : "").append(field.numeric() ? padLeft(text, widths[i]) : pad(text, widths[i]));
            }
            lines.add(line.toString().stripTrailing());
        }
        return lines;
    }

    public static String line(RecordFormat format, Object[] values) {
        int[] widths = widths(format);
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            FieldDef field = format.fields().get(i);
            String column = field.column(values[i]);
            line.append(i > 0 ? "  " : "").append(field.numeric() ? padLeft(column, widths[i]) : pad(column, widths[i]));
        }
        return line.toString().stripTrailing();
    }

    // RUNQRY's printed report: a title line (the file, the selection, when, the system), the headings and a rule, the
    // records, the count and the end line.
    public static List<String> print(String file, String selection, String when, String system, RecordFormat format, List<DbRecord> records, int of) {
        List<String> lines = new ArrayList<>();
        lines.add("Query  " + file + "   " + when + "   " + system);
        lines.add("Selection  " + (selection.isBlank() ? "*ALL" : selection));
        lines.add("");
        List<String> headings = headings(format);
        lines.addAll(headings);
        int width = 0;
        for (String heading : headings) {
            width = Math.max(width, heading.length());
        }
        lines.add("-".repeat(Math.max(1, width)));
        for (DbRecord record : records) {
            lines.add(line(format, record.values()));
        }
        lines.add("");
        lines.add(records.size() + " of " + of + " records selected");
        lines.add(END);
        return lines;
    }

    static String pad(String text, int width) {
        return text.length() >= width ? text.substring(0, width) : text + " ".repeat(width - text.length());
    }

    static String padLeft(String text, int width) {
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }
}
