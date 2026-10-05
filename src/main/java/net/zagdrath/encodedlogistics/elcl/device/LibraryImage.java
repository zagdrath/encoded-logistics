/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.device;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.db.DbFile;

// A library as SAVLIB writes it to an 8" Diskette (OS.md 4) and RSTLIB reads it back: its name, type and text, where
// and when it was saved, its source members (their type, sequence numbers and change dates kept), its programs (as
// their source, compiled again on restore, with the file formats they were compiled with) and its physical files with
// their records. As NBT for the diskette item's data.
public record LibraryImage(String library, String type, String text, String savedFrom, String saved, List<MemberImage> members,
        List<ProgramImage> programs, List<FileImage> files) {
    public LibraryImage(String library, String type, String text, String savedFrom, String saved, List<MemberImage> members, List<ProgramImage> programs) {
        this(library, type, text, savedFrom, saved, members, programs, List.of());
    }

    // type: ELCLP or PF.
    public record MemberImage(String name, String type, String text, List<SourceLine> lines) {
        public MemberImage(String name, String text, List<SourceLine> lines) {
            this(name, "ELCLP", text, lines);
        }
    }

    // files: the formats of the files it declares, by LIB/FILE as written (RecordFormat.save).
    public record ProgramImage(String name, String sourceMember, List<SourceLine> source, Map<String, String> files) {
        public ProgramImage(String name, String sourceMember, List<SourceLine> source) {
            this(name, sourceMember, source, Map.of());
        }
    }

    // A file as saved: its definition and records (DbFile.save()).
    public record FileImage(String name, CompoundTag data) {
        public static FileImage of(DbFile file) {
            return new FileImage(file.name, file.save());
        }

        // The file it saved, made afresh (null when it no longer loads).
        public @org.jspecify.annotations.Nullable DbFile file() {
            return DbFile.load(data);
        }
    }

    // Its size: the source it holds, in characters and line ends, and its files' records, each its record length and a
    // line end (what a diskette's capacity counts).
    public long bytes() {
        long bytes = 0;
        for (MemberImage member : members) {
            for (SourceLine line : member.lines()) {
                bytes += line.text().length() + 1;
            }
        }
        for (ProgramImage program : programs) {
            for (SourceLine line : program.source()) {
                bytes += line.text().length() + 1;
            }
        }
        for (FileImage image : files) {
            DbFile file = image.file();
            if (file != null) {
                bytes += file.characters() + file.size();
            }
        }
        return bytes;
    }

    private static ListTag lines(List<SourceLine> lines) {
        ListTag list = new ListTag();
        lines.forEach(line -> list.add(StringTag.valueOf(line.seq() + "\t" + line.date() + "\t" + line.text())));
        return list;
    }

    private static List<SourceLine> lines(ListTag list) {
        List<SourceLine> lines = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String[] parts = list.getStringOr(i, "").split("\t", 3);
            try {
                lines.add(new SourceLine(Integer.parseInt(parts[0]), parts.length > 2 ? parts[2] : "", Integer.parseInt(parts[1])));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}
        }
        return lines;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("library", library);
        tag.putString("type", type);
        tag.putString("text", text);
        tag.putString("saved_from", savedFrom);
        tag.putString("saved", saved);
        ListTag members = new ListTag();
        for (MemberImage member : this.members) {
            CompoundTag m = new CompoundTag();
            m.putString("name", member.name());
            m.putString("type", member.type());
            m.putString("text", member.text());
            m.put("lines", lines(member.lines()));
            members.add(m);
        }
        tag.put("members", members);
        ListTag programs = new ListTag();
        for (ProgramImage program : this.programs) {
            CompoundTag p = new CompoundTag();
            p.putString("name", program.name());
            p.putString("source_member", program.sourceMember());
            p.put("source", lines(program.source()));
            if (!program.files().isEmpty()) {
                CompoundTag formats = new CompoundTag();
                program.files().forEach(formats::putString);
                p.put("files", formats);
            }
            programs.add(p);
        }
        tag.put("programs", programs);
        ListTag files = new ListTag();
        this.files.forEach(file -> files.add(file.data()));
        tag.put("files", files);
        return tag;
    }

    public static LibraryImage load(CompoundTag tag) {
        List<MemberImage> members = new ArrayList<>();
        ListTag m = tag.getListOrEmpty("members");
        for (int i = 0; i < m.size(); i++) {
            CompoundTag member = m.getCompoundOrEmpty(i);
            members.add(new MemberImage(member.getStringOr("name", ""), member.getStringOr("type", "ELCLP"), member.getStringOr("text", ""),
                    lines(member.getListOrEmpty("lines"))));
        }
        List<ProgramImage> programs = new ArrayList<>();
        ListTag p = tag.getListOrEmpty("programs");
        for (int i = 0; i < p.size(); i++) {
            CompoundTag program = p.getCompoundOrEmpty(i);
            Map<String, String> formats = new LinkedHashMap<>();
            CompoundTag saved = program.getCompoundOrEmpty("files");
            for (String key : saved.keySet()) {
                formats.put(key, saved.getStringOr(key, ""));
            }
            programs.add(new ProgramImage(program.getStringOr("name", ""), program.getStringOr("source_member", ""),
                    lines(program.getListOrEmpty("source")), Map.copyOf(formats)));
        }
        List<FileImage> files = new ArrayList<>();
        ListTag f = tag.getListOrEmpty("files");
        for (int i = 0; i < f.size(); i++) {
            CompoundTag data = f.getCompoundOrEmpty(i);
            files.add(new FileImage(data.getStringOr("name", ""), data));
        }
        return new LibraryImage(tag.getStringOr("library", ""), tag.getStringOr("type", "*PROD"), tag.getStringOr("text", ""),
                tag.getStringOr("saved_from", ""), tag.getStringOr("saved", ""), List.copyOf(members), List.copyOf(programs), List.copyOf(files));
    }
}
