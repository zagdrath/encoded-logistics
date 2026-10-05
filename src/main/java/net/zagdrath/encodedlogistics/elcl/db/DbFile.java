/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.zagdrath.encodedlogistics.elcl.ElclException;

// A physical file (*FILE, attribute PF): its record format, where its definition came from (the source member and the
// version of it compiled, so Work with Members can say it changed since), when it was made, and its one data member -
// the records, by relative record number in arrival order, and (a keyed file) an index on the key, kept as records are
// written, changed and deleted, so reads in key order and by key are quick. A UNIQUE key refuses a second record with
// the same key (ELC2203). Values given here are already as the format holds them (FieldDef.convert).
public final class DbFile {
    public static final String PF = "PF";
    // A file made from no source member (RUNQRY OUTFILE, CPYF CRTFILE(*YES)).
    public static final String NO_SOURCE = "*NONE";
    private static final char SEPARATOR = '\u001f';

    public final String name;
    public String text;
    public RecordFormat format;
    public String sourceLibrary, sourceMember, created;
    public int sourceVersion;
    // A system file's rows are made when they're read (ELSYS: INVITEMS...): it keeps none.
    public final boolean system;
    private final TreeMap<Long, Object[]> byRrn = new TreeMap<>();
    private TreeMap<IndexKey, Long> index;
    private long nextRrn = 1;

    private record IndexKey(Object[] key, long rrn) {}

    public DbFile(String name, String text, RecordFormat format, String sourceLibrary, String sourceMember, int sourceVersion, String created,
            boolean system) {
        this.name = name;
        this.text = text;
        this.format = format;
        this.sourceLibrary = sourceLibrary;
        this.sourceMember = sourceMember;
        this.sourceVersion = sourceVersion;
        this.created = created;
        this.system = system;
        this.index = new TreeMap<>(order(format));
    }

    // Key order (then arrival); a file without a key is all one key, so arrival order.
    private static Comparator<IndexKey> order(RecordFormat format) {
        int[] indexes = format.keyIndexes();
        return (a, b) -> {
            for (int i = 0; i < indexes.length; i++) {
                int compare = format.fields().get(indexes[i]).compare(a.key()[i], b.key()[i]);
                if (compare != 0) {
                    return compare;
                }
            }
            return Long.compare(a.rrn(), b.rrn());
        };
    }

    public int size() {
        return byRrn.size();
    }

    public long nextRrn() {
        return nextRrn;
    }

    // The characters its records take (what network storage counts).
    public long characters() {
        return (long) byRrn.size() * format.recordLength();
    }

    public @Nullable DbRecord get(long rrn) {
        Object[] values = byRrn.get(rrn);
        return values != null ? new DbRecord(rrn, values) : null;
    }

    // Every record, in key order (arrival order without a key).
    public List<DbRecord> records() {
        List<DbRecord> records = new ArrayList<>(byRrn.size());
        for (Long rrn : index.values()) {
            records.add(new DbRecord(rrn, byRrn.get(rrn)));
        }
        return records;
    }

    // The record after a position (null: the first), or null at the end.
    public @Nullable DbRecord next(DbRecord.@Nullable Position after) throws ElclException {
        Map.Entry<IndexKey, Long> entry;
        if (after == null) {
            entry = index.firstEntry();
        } else {
            Object[] key = DbRecord.key(format, after);
            if (key == null) {
                throw new ElclException("ELC2207", name, String.join(" ", after.key()));
            }
            entry = index.higherEntry(new IndexKey(key, after.rrn()));
        }
        return entry != null ? new DbRecord(entry.getValue(), byRrn.get(entry.getValue())) : null;
    }

    // The record before a position (null: the last), or null at the start.
    public @Nullable DbRecord previous(DbRecord.@Nullable Position before) throws ElclException {
        Map.Entry<IndexKey, Long> entry;
        if (before == null) {
            entry = index.lastEntry();
        } else {
            Object[] key = DbRecord.key(format, before);
            if (key == null) {
                throw new ElclException("ELC2207", name, String.join(" ", before.key()));
            }
            entry = index.lowerEntry(new IndexKey(key, before.rrn()));
        }
        return entry != null ? new DbRecord(entry.getValue(), byRrn.get(entry.getValue())) : null;
    }

    // The first record whose leading key fields are these values (CHNRCD: all of the key, or the first of its fields);
    // null when there's none.
    public @Nullable DbRecord chain(Object[] key) {
        if (!format.keyed()) {
            return null;
        }
        Map.Entry<IndexKey, Long> entry = index.ceilingEntry(new IndexKey(padded(key), Long.MIN_VALUE));
        if (entry == null || compare(entry.getKey().key(), key) != 0) {
            return null;
        }
        return new DbRecord(entry.getValue(), byRrn.get(entry.getValue()));
    }

    // Where the first record at or after a partial key is in key order (position to), 0-based; -1 for an unkeyed file.
    public int position(Object[] key) {
        if (!format.keyed()) {
            return -1;
        }
        IndexKey from = new IndexKey(padded(key), Long.MIN_VALUE);
        return index.headMap(from, false).size();
    }

    // Where a record is in key (or arrival) order, 0-based; -1 when it isn't there.
    public int indexOf(long rrn) {
        Object[] values = byRrn.get(rrn);
        if (values == null) {
            return -1;
        }
        return index.headMap(new IndexKey(format.keyOf(values), rrn), false).size();
    }

    // A partial key filled out to the whole key with the lowest values (blanks, zero, false sort first enough: a
    // compare of the leading fields decides, and padded ones only order within them).
    private Object[] padded(Object[] key) {
        int[] indexes = format.keyIndexes();
        Object[] full = new Object[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            full[i] = i < key.length ? key[i] : lowest(format.fields().get(indexes[i]));
        }
        return full;
    }

    private static Object lowest(FieldDef field) {
        return switch (field.type()) {
            case CHAR, TIME -> "";
            case INT -> Long.MIN_VALUE;
            case DEC -> new java.math.BigDecimal("-1e40");
            case LGL -> Boolean.FALSE;
        };
    }

    // How the leading fields of a record's key compare with a partial key.
    private int compare(Object[] recordKey, Object[] key) {
        int[] indexes = format.keyIndexes();
        for (int i = 0; i < key.length && i < indexes.length; i++) {
            int compare = format.fields().get(indexes[i]).compare(recordKey[i], key[i]);
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    // ELC2203 when a UNIQUE key would have a second record (except the one being changed).
    private void unique(Object[] values, long except) throws ElclException {
        if (!format.unique()) {
            return;
        }
        Object[] key = format.keyOf(values);
        Map.Entry<IndexKey, Long> entry = index.ceilingEntry(new IndexKey(key, Long.MIN_VALUE));
        while (entry != null && compare(entry.getKey().key(), key) == 0) {
            if (entry.getValue() != except) {
                throw new ElclException("ELC2203", format.keyText(values), name);
            }
            entry = index.higherEntry(entry.getKey());
        }
    }

    public DbRecord add(Object[] values) throws ElclException {
        check(values);
        unique(values, -1);
        long rrn = nextRrn++;
        put(rrn, values);
        return new DbRecord(rrn, values);
    }

    // ELC2204 when there's no such record (another job deleted it).
    public DbRecord update(long rrn, Object[] values) throws ElclException {
        check(values);
        Object[] old = byRrn.get(rrn);
        if (old == null) {
            throw new ElclException("ELC2204", name);
        }
        unique(values, rrn);
        index.remove(new IndexKey(format.keyOf(old), rrn));
        put(rrn, values);
        return new DbRecord(rrn, values);
    }

    public boolean delete(long rrn) {
        Object[] old = byRrn.remove(rrn);
        if (old != null) {
            index.remove(new IndexKey(format.keyOf(old), rrn));
        }
        return old != null;
    }

    public void clear() {
        byRrn.clear();
        index.clear();
    }

    private void put(long rrn, Object[] values) {
        Object[] copy = values.clone();
        byRrn.put(rrn, copy);
        index.put(new IndexKey(format.keyOf(copy), rrn), rrn);
    }

    private void check(Object[] values) {
        if (values.length != format.fields().size()) {
            throw new IllegalArgumentException("A record of " + values.length + " values for " + format.fields().size() + " fields");
        }
    }

    // A new format (CHGPF) and the records as they are in it, each keeping its number. ELC2203 (and nothing changed)
    // when a UNIQUE key would have two.
    public void reformat(RecordFormat format, Map<Long, Object[]> records) throws ElclException {
        Snapshot before = snapshot();
        this.format = format;
        byRrn.clear();
        index = new TreeMap<>(order(format));
        try {
            for (Map.Entry<Long, Object[]> record : records.entrySet()) {
                check(record.getValue());
                unique(record.getValue(), record.getKey());
                put(record.getKey(), record.getValue());
            }
        } catch (ElclException e) {
            restore(before);
            throw e;
        }
    }

    // Its format, records and next number as they are, to put back if a change across many records fails part-way.
    public record Snapshot(RecordFormat format, Map<Long, Object[]> rows, long nextRrn) {}

    public Snapshot snapshot() {
        return new Snapshot(format, rows(), nextRrn);
    }

    public void restore(Snapshot snapshot) {
        format = snapshot.format();
        byRrn.clear();
        index = new TreeMap<>(order(format));
        snapshot.rows().forEach(this::put);
        nextRrn = snapshot.nextRrn();
    }

    // The records as rrn -> values (CHGPF works from them).
    public Map<Long, Object[]> rows() {
        Map<Long, Object[]> rows = new TreeMap<>();
        byRrn.forEach((rrn, values) -> rows.put(rrn, values.clone()));
        return rows;
    }

    // --- Saving ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("text", text);
        tag.putString("format", format.save());
        tag.putString("source_library", sourceLibrary);
        tag.putString("source_member", sourceMember);
        tag.putInt("source_version", sourceVersion);
        tag.putString("created", created);
        tag.putLong("next_rrn", nextRrn);
        ListTag records = new ListTag();
        StringBuilder line = new StringBuilder();
        for (Map.Entry<Long, Object[]> record : byRrn.entrySet()) {
            line.setLength(0);
            line.append(record.getKey());
            Object[] values = record.getValue();
            for (int i = 0; i < values.length; i++) {
                line.append(SEPARATOR).append(format.fields().get(i).text(values[i]));
            }
            records.add(StringTag.valueOf(line.toString()));
        }
        tag.put("records", records);
        return tag;
    }

    // Null when the tag isn't a file (its format unreadable). A record that no longer fits its format is dropped.
    public static @Nullable DbFile load(CompoundTag tag) {
        RecordFormat format = RecordFormat.load(tag.getStringOr("format", ""));
        String name = tag.getStringOr("name", "");
        if (format == null || name.isEmpty()) {
            return null;
        }
        DbFile file = new DbFile(name, tag.getStringOr("text", ""), format, tag.getStringOr("source_library", ""), tag.getStringOr("source_member", ""),
                tag.getIntOr("source_version", 0), tag.getStringOr("created", ""), false);
        ListTag records = tag.getListOrEmpty("records");
        for (int i = 0; i < records.size(); i++) {
            String[] parts = records.getStringOr(i, "").split(String.valueOf(SEPARATOR), -1);
            if (parts.length != format.fields().size() + 1) {
                continue;
            }
            try {
                Object[] values = new Object[format.fields().size()];
                for (int f = 0; f < values.length; f++) {
                    values[f] = format.fields().get(f).convert(parts[f + 1]);
                }
                file.put(Long.parseLong(parts[0]), values);
            } catch (ElclException | NumberFormatException ignored) {}
        }
        file.nextRrn = Math.max(tag.getLongOr("next_rrn", 1), file.byRrn.isEmpty() ? 1 : file.byRrn.lastKey() + 1);
        return file;
    }
}
