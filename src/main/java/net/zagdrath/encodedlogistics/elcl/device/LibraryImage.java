/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.device;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.zagdrath.encodedlogistics.elcl.SourceLine;

// A library as SAVLIB writes it to an 8" Diskette (OS.md 4) and RSTLIB reads it back: its name, type and text, where
// and when it was saved, its source members (sequence numbers and change dates kept) and its programs (as their source,
// compiled again on restore). As NBT for the diskette item's data.
public record LibraryImage(String library, String type, String text, String savedFrom, String saved, List<MemberImage> members,
        List<ProgramImage> programs) {
    public record MemberImage(String name, String text, List<SourceLine> lines) {}

    public record ProgramImage(String name, String sourceMember, List<SourceLine> source) {}

    // Its size: the source it holds, in characters and line ends (what a diskette's capacity counts).
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
            programs.add(p);
        }
        tag.put("programs", programs);
        return tag;
    }

    public static LibraryImage load(CompoundTag tag) {
        List<MemberImage> members = new ArrayList<>();
        ListTag m = tag.getListOrEmpty("members");
        for (int i = 0; i < m.size(); i++) {
            CompoundTag member = m.getCompoundOrEmpty(i);
            members.add(new MemberImage(member.getStringOr("name", ""), member.getStringOr("text", ""), lines(member.getListOrEmpty("lines"))));
        }
        List<ProgramImage> programs = new ArrayList<>();
        ListTag p = tag.getListOrEmpty("programs");
        for (int i = 0; i < p.size(); i++) {
            CompoundTag program = p.getCompoundOrEmpty(i);
            programs.add(new ProgramImage(program.getStringOr("name", ""), program.getStringOr("source_member", ""),
                    lines(program.getListOrEmpty("source"))));
        }
        return new LibraryImage(tag.getStringOr("library", ""), tag.getStringOr("type", "*PROD"), tag.getStringOr("text", ""),
                tag.getStringOr("saved_from", ""), tag.getStringOr("saved", ""), List.copyOf(members), List.copyOf(programs));
    }
}
