/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// A source member's record (OS.md 2): its sequence number in hundredths (1200 = "0012.00", 6.2 format), its text
// (up to 80 columns) and the game day it last changed (0: shipped, never changed).
public record SourceLine(int seq, String text, int date) {
    public static final int WIDTH = 80, MAX_SEQ = 999_999;

    // "0012.00".
    public String seqText() {
        return seqText(seq);
    }

    public static String seqText(int seq) {
        return String.format(Locale.ROOT, "%04d.%02d", seq / 100, seq % 100);
    }

    // "0012": the whole part, as the cross reference shows it.
    public static String shortSeq(int seq) {
        return String.format(Locale.ROOT, "%04d", seq / 100);
    }

    // The change date as the listing shows it: "Day 12", blank when never changed.
    public String dateText() {
        return date <= 0 ? "" : "Day " + date;
    }

    // Lines of text numbered 0001.00, 0002.00, ... (a new member, or a file read in).
    public static List<SourceLine> number(List<String> texts, int date) {
        List<SourceLine> lines = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            lines.add(new SourceLine((i + 1) * 100, texts.get(i), date));
        }
        return lines;
    }

    public static List<String> texts(List<SourceLine> lines) {
        List<String> texts = new ArrayList<>(lines.size());
        for (SourceLine line : lines) {
            texts.add(line.text());
        }
        return texts;
    }

    // A file's text as lines: split at line breaks (\r\n or \n), the final break not making an empty last line.
    public static List<String> split(String text) {
        List<String> lines = new ArrayList<>(List.of(text.replace("\r\n", "\n").split("\n", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return lines;
    }

    // Lines back to a file's text, each ending with a line break.
    public static String join(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
