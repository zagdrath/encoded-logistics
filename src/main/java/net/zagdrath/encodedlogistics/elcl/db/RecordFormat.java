/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

// A physical file's record format (its DDS, compiled): the format's name (R) and text, its fields in order, its key
// fields (K, in order; none: arrival order) and whether the key is UNIQUE. As text (save / load) it goes with the file
// in the system's data, and with each program compiled against it (DCLF), so the program recompiles the same.
public record RecordFormat(String name, String text, List<FieldDef> fields, List<String> key, boolean unique) {
    public static final int MAX_FIELDS = 50, MAX_KEY = 4;
    // Parts and entries of the text form (DDS text can't hold control characters).
    private static final char PART = '\u001f', ENTRY = '\u001e', LINE = '\u001d';

    public RecordFormat {
        fields = List.copyOf(fields);
        key = List.copyOf(key);
    }

    public @Nullable FieldDef field(String name) {
        int index = index(name);
        return index >= 0 ? fields.get(index) : null;
    }

    public int index(String name) {
        String wanted = FieldDef.upper(name);
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).name().equals(wanted)) {
                return i;
            }
        }
        return -1;
    }

    public boolean keyed() {
        return !key.isEmpty();
    }

    // The key fields' places in a record.
    public int[] keyIndexes() {
        int[] indexes = new int[key.size()];
        for (int i = 0; i < key.size(); i++) {
            indexes[i] = index(key.get(i));
        }
        return indexes;
    }

    // The characters a record takes (what network storage counts).
    public int recordLength() {
        int length = 0;
        for (FieldDef field : fields) {
            length += field.size();
        }
        return length;
    }

    // An empty record.
    public Object[] blank() {
        Object[] values = new Object[fields.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = fields.get(i).blank();
        }
        return values;
    }

    // A record's key values.
    public Object[] keyOf(Object[] values) {
        int[] indexes = keyIndexes();
        Object[] key = new Object[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            key[i] = values[indexes[i]];
        }
        return key;
    }

    // A record's key as text, blank-joined ("IRON_INGOT 00012 06:30:15").
    public String keyText(Object[] values) {
        int[] indexes = keyIndexes();
        StringBuilder out = new StringBuilder();
        for (int index : indexes) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(fields.get(index).text(values[index]));
        }
        return out.toString();
    }

    // --- As text ---

    public String save() {
        StringBuilder out = new StringBuilder();
        out.append(name).append(PART).append(text).append(PART).append(unique ? '1' : '0').append(PART).append(String.join(",", key));
        for (FieldDef field : fields) {
            out.append(ENTRY).append(field.name()).append(PART).append(field.type().code).append(PART).append(field.length()).append(PART)
                    .append(field.decimals()).append(PART).append(field.text()).append(PART).append(String.join(String.valueOf(LINE), field.headings()));
        }
        return out.toString();
    }

    // Null when the text isn't one.
    public static @Nullable RecordFormat load(String saved) {
        try {
            String[] entries = saved.split(String.valueOf(ENTRY), -1);
            String[] head = entries[0].split(String.valueOf(PART), -1);
            List<FieldDef> fields = new ArrayList<>();
            for (int i = 1; i < entries.length; i++) {
                String[] parts = entries[i].split(String.valueOf(PART), -1);
                FieldDef.Type type = FieldDef.Type.of(parts[1].charAt(0));
                if (type == null) {
                    return null;
                }
                List<String> headings = parts[5].isEmpty() ? List.of() : List.of(parts[5].split(String.valueOf(LINE), -1));
                fields.add(new FieldDef(parts[0], type, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), parts[4], headings));
            }
            List<String> key = head[3].isEmpty() ? List.of() : List.of(head[3].split(","));
            return new RecordFormat(head[0], head[1], fields, key, head[2].equals("1"));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
