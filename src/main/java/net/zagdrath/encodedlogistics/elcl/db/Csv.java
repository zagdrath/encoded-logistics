/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// A file's records as CSV (CPYTOIMPF / CPYFRMIMPF): a heading row of field names, then a row a record, values as text
// (FieldDef.text: numbers plain, logicals 1 / 0, timestamps "00012 06:30:15"), quoted where they hold a comma, a
// quote, a line break or blanks at either end. Read back, a heading row naming the file's fields maps the columns by
// name (any order, fields left out take their blank value); without one, columns are the fields in order.
public final class Csv {
    private Csv() {}

    public static String write(RecordFormat format, List<DbRecord> records) {
        StringBuilder out = new StringBuilder();
        List<String> names = new ArrayList<>();
        format.fields().forEach(field -> names.add(field.name()));
        row(out, names);
        for (DbRecord record : records) {
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < format.fields().size(); i++) {
                cells.add(format.fields().get(i).text(record.value(i)));
            }
            row(out, cells);
        }
        return out.toString();
    }

    private static void row(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            String cell = cells.get(i);
            boolean quote = cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")
                    || !cell.isEmpty() && (cell.charAt(0) == ' ' || cell.charAt(cell.length() - 1) == ' ');
            out.append(quote ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell);
        }
        out.append('\n');
    }

    // The rows of a CSV text: cells, quotes taken off ("" a quote), line breaks inside quotes kept. Blank lines skipped.
    public static List<List<String>> read(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false, any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
                any = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
                any = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (any || !cell.isEmpty()) {
                    row.add(cell.toString());
                    rows.add(row);
                }
                row = new ArrayList<>();
                cell.setLength(0);
                any = false;
            } else {
                cell.append(c);
                any = true;
            }
        }
        if (any || !cell.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    // CSV rows as records of the format: ELC2209 (the row and column) for a value that doesn't fit its field.
    public static List<Object[]> records(RecordFormat format, List<List<String>> rows) throws ElclException {
        int[] columns = new int[format.fields().size()];
        boolean headed = !rows.isEmpty() && heading(format, rows.getFirst(), columns);
        if (!headed) {
            for (int i = 0; i < columns.length; i++) {
                columns[i] = i;
            }
        }
        List<Object[]> records = new ArrayList<>();
        for (int r = headed ? 1 : 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Object[] values = new Object[columns.length];
            for (int f = 0; f < columns.length; f++) {
                FieldDef field = format.fields().get(f);
                String cell = columns[f] >= 0 && columns[f] < row.size() ? row.get(columns[f]) : "";
                try {
                    values[f] = field.convert(cell);
                } catch (ElclException e) {
                    throw new ElclException("ELC2243", r + 1, cell, field.name());
                }
            }
            records.add(values);
        }
        return records;
    }

    // Whether the row is a heading naming the format's fields; if so, where each field's column is (-1: not there).
    private static boolean heading(RecordFormat format, List<String> row, int[] columns) {
        int named = 0;
        java.util.Arrays.fill(columns, -1);
        for (int c = 0; c < row.size(); c++) {
            int index = format.index(row.get(c).strip().toUpperCase(Locale.ROOT));
            if (index >= 0 && columns[index] < 0) {
                columns[index] = c;
                named++;
            }
        }
        return named > 0 && named == row.stream().filter(cell -> !cell.isBlank()).count();
    }
}
