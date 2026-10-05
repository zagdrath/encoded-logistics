/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

// One record of a file: its relative record number (given when it's written, never reused; 0 for a system file's
// generated rows) and its values, in the format's field order, as FieldDef holds them.
public record DbRecord(long rrn, Object[] values) {
    public DbRecord {
        values = values.clone();
    }

    @Override
    public Object[] values() {
        return values.clone();
    }

    public Object value(int index) {
        return values[index];
    }

    // Where a read stopped (RCVF): the record's key values as text and its number, so the next read carries on after
    // it - and saved with a job, carries on after a restart.
    public record Position(List<String> key, long rrn) {
        public Position {
            key = List.copyOf(key);
        }
    }

    public Position position(RecordFormat format) {
        List<String> key = new ArrayList<>();
        for (int index : format.keyIndexes()) {
            key.add(format.fields().get(index).text(values[index]));
        }
        return new Position(key, rrn);
    }

    // A position's key values back as the format holds them (null when they no longer fit it).
    public static Object @Nullable [] key(RecordFormat format, Position position) {
        int[] indexes = format.keyIndexes();
        if (position.key().size() != indexes.length) {
            return null;
        }
        Object[] key = new Object[indexes.length];
        try {
            for (int i = 0; i < indexes.length; i++) {
                key[i] = format.fields().get(indexes[i]).convert(position.key().get(i));
            }
        } catch (net.zagdrath.encodedlogistics.elcl.ElclException e) {
            return null;
        }
        return key;
    }
}
