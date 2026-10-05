/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.SourceLine;

// The source editor's member (screen 5), apart from the screen: its lines (sequence number, text, change date, whether
// changed), the margin's line commands, FIND / CHANGE, and what saving gives back. Line commands (typed over a
// sequence number; Enter applies them top to bottom): I / In insert, D / Dn delete, DD..DD delete a block, C / CC..CC
// copy and M / MM..MM move (to A after / B before a line), R / Rn repeat, X / Xn exclude (S or F on the excluded row
// shows them again), COLS the ruler; on the "Beginning of data" row (TOP) I, A and COLS only. A copy or move waiting for its target (or a block for its end) stays pending
// until it's completed or reset. Saving keeps every unchanged line's sequence number and date; new and moved lines are
// numbered between their neighbours (all of them are renumbered when there's no room). Inserted lines stay "fresh"
// until something is typed on them: fresh blank lines are dropped on the next Enter (dropFresh), as SEU does.
final class EditorModel {
    static final int WIDTH = SourceLine.WIDTH;
    private static final Pattern COMMAND = Pattern.compile("(COLS|DD|CC|MM|RR|I|D|C|M|R|X|A|B|S|F)([0-9]*)");

    static final class Line {
        int seq;
        String text;
        int date;
        boolean changed;
        // Excluded lines in a run share a group (0: shown).
        int excluded;
        // Inserted and not typed on yet.
        boolean fresh;

        Line(int seq, String text, int date) {
            this.seq = seq;
            this.text = text;
            this.date = date;
        }

        Line copy() {
            return new Line(0, text, date);
        }
    }

    // The "Beginning of data" row's place: before the first line (it's never in the list, so its index is -1).
    static final Line TOP = new Line(-1, "", 0);

    // A line command typed on a line's margin (or TOP's).
    record Command(Line line, String text) {}

    private record Insert(Line after, int count) {}

    private record Repeat(List<Line> block, int times) {}

    final List<Line> lines = new ArrayList<>();
    boolean dirty, ruler;
    private int nextGroup = 1;
    // Pending: a block's first mark (DD, CC, MM, RR), a copy / move source waiting for its target, a target waiting.
    private @Nullable Line blockStart;
    private @Nullable String blockKind;
    private @Nullable List<Line> source;
    private boolean moving;
    private @Nullable Line target;
    private boolean before;
    // The last FIND / CHANGE (F16 / F17 repeat them).
    String lastFind = "", lastFrom = "", lastTo = "";

    EditorModel(List<SourceLine> source) {
        for (SourceLine line : source) {
            lines.add(new Line(line.seq(), line.text(), line.date()));
        }
    }

    boolean pending() {
        return blockStart != null || source != null || target != null;
    }

    void reset() {
        blockStart = null;
        blockKind = null;
        source = null;
        target = null;
    }

    // Whether a line is marked by a pending command (the screen shows its mark in the margin).
    @Nullable String mark(Line line) {
        if (line == blockStart) {
            return blockKind;
        }
        if (source != null && source.contains(line)) {
            return moving ? "M" : "C";
        }
        if (line == target) {
            return before ? "B" : "A";
        }
        return null;
    }

    // The command typed over a margin: what differs from what it showed, the unchanged tail of it dropped ("i312.00"
    // over "0012.00" is I3).
    static String typed(String shown, String value) {
        String v = value.stripTrailing();
        int end = v.length();
        while (end > 0 && end <= shown.length() && v.charAt(end - 1) == shown.charAt(end - 1)) {
            end--;
        }
        return v.substring(0, end).trim().toUpperCase(Locale.ROOT);
    }

    // Applies the line commands (top to bottom): a message for the message line, or null.
    @Nullable String apply(List<Command> commands) {
        List<Line> deleting = new ArrayList<>();
        List<Insert> inserting = new ArrayList<>();
        List<Repeat> repeating = new ArrayList<>();
        Set<Integer> showing = new HashSet<>();
        String problem = null;
        for (Command command : commands) {
            Matcher matcher = COMMAND.matcher(command.text());
            if (!matcher.matches()) {
                return CrtPanel.tr("crt.encodedlogistics.edit.bad_command", command.text());
            }
            String kind = matcher.group(1);
            int count = matcher.group(2).isEmpty() ? 1 : Math.max(1, Math.min(9999, Integer.parseInt(matcher.group(2))));
            Line line = command.line();
            int at = lines.indexOf(line);
            if (line == TOP && !kind.equals("I") && !kind.equals("A") && !kind.equals("COLS")) {
                problem = CrtPanel.tr("crt.encodedlogistics.edit.bad_command", command.text());
                continue;
            }
            switch (kind) {
                case "COLS" -> ruler = !ruler;
                case "I" -> inserting.add(new Insert(line, count));
                case "D" -> {
                    for (int i = at; i < Math.min(lines.size(), at + count); i++) {
                        deleting.add(lines.get(i));
                    }
                }
                case "X" -> {
                    int group = nextGroup++;
                    for (int i = at; i < Math.min(lines.size(), at + count); i++) {
                        lines.get(i).excluded = group;
                    }
                }
                case "S", "F" -> showing.add(line.excluded);
                case "R" -> repeating.add(new Repeat(List.of(line), count));
                case "DD", "CC", "MM", "RR" -> {
                    if (blockStart == null || !kind.equals(blockKind)) {
                        blockStart = line;
                        blockKind = kind;
                        continue;
                    }
                    int from = Math.min(lines.indexOf(blockStart), at), to = Math.max(lines.indexOf(blockStart), at);
                    List<Line> block = new ArrayList<>(lines.subList(from, to + 1));
                    blockStart = null;
                    blockKind = null;
                    switch (kind) {
                        case "DD" -> deleting.addAll(block);
                        case "RR" -> repeating.add(new Repeat(block, 1));
                        default -> {
                            source = block;
                            moving = kind.equals("MM");
                        }
                    }
                }
                case "C", "M" -> {
                    source = new ArrayList<>(List.of(line));
                    moving = kind.equals("M");
                }
                case "A", "B" -> {
                    target = line;
                    before = kind.equals("B");
                }
                default -> problem = CrtPanel.tr("crt.encodedlogistics.edit.bad_command", command.text());
            }
        }
        if (!showing.isEmpty()) {
            for (Line line : lines) {
                if (showing.contains(line.excluded)) {
                    line.excluded = 0;
                }
            }
        }
        // Copies and moves once both ends are there (the target can't be inside a move's own block).
        if (source != null && target != null) {
            if (moving && source.contains(target)) {
                problem = CrtPanel.tr("crt.encodedlogistics.edit.target_in_block");
            } else {
                List<Line> moved = new ArrayList<>();
                for (Line line : source) {
                    Line copy = moving ? line : line.copy();
                    if (moving) {
                        lines.remove(line);
                        line.seq = 0;
                    }
                    copy.excluded = 0;
                    moved.add(copy);
                }
                int at = lines.indexOf(target) + (before ? 0 : 1);
                lines.addAll(at, moved);
                dirty = true;
            }
            source = null;
            target = null;
        }
        for (Repeat repeat : repeating) {
            int at = lines.indexOf(repeat.block().getLast()) + 1;
            List<Line> copies = new ArrayList<>();
            for (int t = 0; t < repeat.times(); t++) {
                for (Line line : repeat.block()) {
                    copies.add(line.copy());
                }
            }
            lines.addAll(at, copies);
            dirty = true;
        }
        for (Insert insert : inserting) {
            int at = lines.indexOf(insert.after()) + 1;
            for (int i = 0; i < insert.count(); i++) {
                lines.add(at + i, blank());
            }
            dirty = true;
        }
        if (!deleting.isEmpty()) {
            lines.removeAll(deleting);
            dirty = true;
        }
        if (problem != null) {
            return problem;
        }
        return pending() ? CrtPanel.tr("crt.encodedlogistics.edit.pending") : null;
    }

    // A new, fresh blank line.
    static Line blank() {
        Line blank = new Line(0, "", 0);
        blank.changed = true;
        blank.fresh = true;
        return blank;
    }

    // Blank lines left between new lines typed on: spacing, kept as blank lines of the member (no longer fresh). A blank
    // new line with nothing typed under it in its run of new lines is still dropped.
    void keepSpacing() {
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (!line.fresh || !line.text.isBlank()) {
                continue;
            }
            for (int j = i + 1; j < lines.size() && lines.get(j).seq == 0; j++) {
                Line below = lines.get(j);
                if (!below.fresh && !below.text.isBlank()) {
                    line.fresh = false;
                    line.changed = true;
                    dirty = true;
                    break;
                }
            }
        }
    }

    // Fresh lines still blank (from an earlier Enter) dropped; any kept are given.
    void dropFresh(Set<Line> keep) {
        lines.removeIf(line -> line.fresh && line.text.isBlank() && !keep.contains(line) && line != target && line != blockStart);
    }

    // A line's text changed on the screen.
    void setText(Line line, String text) {
        String clean = text.length() > WIDTH ? text.substring(0, WIDTH) : text;
        if (!clean.equals(line.text)) {
            line.text = clean;
            line.changed = true;
            line.fresh = false;
            dirty = true;
        }
    }

    // --- FIND / CHANGE ---

    // Where `find` is next from (line, column) on: {line, column} or null. FIND's NEXT / PREV / FIRST / LAST.
    int @Nullable [] find(String find, int line, int column, String direction) {
        if (find.isEmpty() || lines.isEmpty()) {
            return null;
        }
        String needle = find.toUpperCase(Locale.ROOT);
        switch (direction) {
            case "FIRST" -> {
                return scan(needle, 0, -1, true);
            }
            case "LAST" -> {
                return scan(needle, lines.size() - 1, Integer.MAX_VALUE, false);
            }
            case "PREV" -> {
                return scan(needle, line, column, false);
            }
            default -> {
                return scan(needle, line, column, true);
            }
        }
    }

    private int @Nullable [] scan(String needle, int line, int column, boolean forward) {
        for (int i = line; forward ? i < lines.size() : i >= 0; i += forward ? 1 : -1) {
            String text = lines.get(i).text.toUpperCase(Locale.ROOT);
            int found = forward ? text.indexOf(needle, i == line ? column + 1 : 0) : text.lastIndexOf(needle, i == line ? column - 1 : Integer.MAX_VALUE);
            if (found >= 0) {
                return new int[] { i, found };
            }
        }
        return null;
    }

    int count(String find) {
        String needle = find.toUpperCase(Locale.ROOT);
        int count = 0;
        for (Line line : lines) {
            String text = line.text.toUpperCase(Locale.ROOT);
            for (int at = text.indexOf(needle); at >= 0 && !needle.isEmpty(); at = text.indexOf(needle, at + needle.length())) {
                count++;
            }
        }
        return count;
    }

    // CHANGE: the next occurrence from (line, column), or all of them; how many changed (lines stay within 80).
    int change(String from, String to, int line, int column, boolean all) {
        int changed = 0;
        int[] at = find(from, line, column - 1, "NEXT");
        while (at != null) {
            Line target = lines.get(at[0]);
            String text = target.text.substring(0, at[1]) + to + target.text.substring(at[1] + from.length());
            if (text.length() <= WIDTH) {
                setText(target, text);
                changed++;
            }
            if (!all) {
                break;
            }
            at = find(from, at[0], at[1] + to.length() - 1, "NEXT");
        }
        return changed;
    }

    // --- Saving ---

    // The member's lines as saved: changed lines dated today, new and moved ones numbered between their neighbours.
    // The member as saved: open lines never typed on (still fresh and blank) aren't part of it.
    List<SourceLine> save(int today) {
        keepSpacing();
        numberTyped();
        List<SourceLine> out = new ArrayList<>();
        for (Line line : lines) {
            if (line.fresh && line.text.isBlank()) {
                continue;
            }
            out.add(new SourceLine(line.seq, line.text, line.changed ? today : line.date));
        }
        return out;
    }

    // After a save: nothing changed any more.
    void saved(int today) {
        for (Line line : lines) {
            if (line.changed) {
                line.date = today;
                line.changed = false;
            }
        }
        dirty = false;
    }

    // After Enter, as SEU does: every new line typed on gets its sequence number; an open blank one (still fresh) keeps
    // ' until something's typed on it.
    void numberTyped() {
        List<Line> all = new ArrayList<>(lines);
        lines.removeIf(line -> line.fresh && line.text.isBlank());
        number();
        lines.clear();
        lines.addAll(all);
    }

    // Sequence numbers for the lines without one (0): spread over the gap to the next numbered line, or everything
    // renumbered by 1.00 when a gap is too small.
    void number() {
        int last = 0;
        for (int i = 0; i < lines.size();) {
            if (lines.get(i).seq > last) {
                last = lines.get(i).seq;
                i++;
                continue;
            }
            int j = i;
            while (j < lines.size() && lines.get(j).seq <= last) {
                j++;
            }
            int next = j < lines.size() ? lines.get(j).seq : last + 100 * (j - i + 1);
            int step = Math.min(100, (next - last) / (j - i + 1));
            if (step < 1 || next > SourceLine.MAX_SEQ) {
                for (int k = 0; k < lines.size(); k++) {
                    lines.get(k).seq = (k + 1) * 100;
                }
                return;
            }
            for (int k = i; k < j; k++) {
                last += step;
                lines.get(k).seq = last;
            }
            i = j;
        }
    }

    List<String> texts() {
        List<String> texts = new ArrayList<>(lines.size());
        for (Line line : lines) {
            texts.add(line.text);
        }
        return texts;
    }
}
