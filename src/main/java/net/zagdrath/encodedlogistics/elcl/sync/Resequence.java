/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.sync;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.encodedlogistics.elcl.SourceLine;

// A member's new text (a file read in) against its old lines (OS.md 4): lines that didn't change keep their sequence
// numbers and change dates; new or changed lines get numbers between their neighbours and today's date. When there's
// no room between two kept numbers (or the member is too long to compare), the whole member is numbered again - its
// dates kept for the unchanged lines.
public final class Resequence {
    // Past this many line pairs, the lines aren't matched up (numbered again instead).
    private static final long MAX_COMPARE = 4_000_000;

    private Resequence() {}

    public static List<SourceLine> merge(List<SourceLine> old, List<String> texts, int today) {
        boolean[] kept = new boolean[texts.size()];
        int[] from = new int[texts.size()];
        if ((long) old.size() * texts.size() <= MAX_COMPARE) {
            match(old, texts, kept, from);
        }
        List<SourceLine> lines = new ArrayList<>(texts.size());
        int i = 0;
        while (i < texts.size()) {
            if (kept[i]) {
                lines.add(old.get(from[i]));
                i++;
                continue;
            }
            // A run of new lines, between the kept ones around it.
            int end = i;
            while (end < texts.size() && !kept[end]) {
                end++;
            }
            int low = lines.isEmpty() ? 0 : lines.getLast().seq();
            int high = end < texts.size() ? old.get(from[end]).seq() : Math.max(low, 0) + (end - i + 1) * 100;
            int count = end - i, step = (high - low) / (count + 1);
            if (step < 1 || low + step * count > SourceLine.MAX_SEQ) {
                return renumber(old, texts, kept, from, today);
            }
            for (int k = 0; k < count; k++) {
                lines.add(new SourceLine(end < texts.size() ? low + step * (k + 1) : low + 100 * (k + 1), texts.get(i + k), today));
            }
            i = end;
        }
        return lines;
    }

    // Every line numbered 0001.00, 0002.00, ...; unchanged lines keep their dates.
    private static List<SourceLine> renumber(List<SourceLine> old, List<String> texts, boolean[] kept, int[] from, int today) {
        List<SourceLine> lines = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            lines.add(new SourceLine((i + 1) * 100, texts.get(i), kept[i] ? old.get(from[i]).date() : today));
        }
        return lines;
    }

    // The longest common run of lines (in order): kept[i] when the new line i is old line from[i].
    private static void match(List<SourceLine> old, List<String> texts, boolean[] kept, int[] from) {
        int n = old.size(), m = texts.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int a = n - 1; a >= 0; a--) {
            for (int b = m - 1; b >= 0; b--) {
                lcs[a][b] = old.get(a).text().equals(texts.get(b)) ? lcs[a + 1][b + 1] + 1 : Math.max(lcs[a + 1][b], lcs[a][b + 1]);
            }
        }
        int a = 0, b = 0;
        while (a < n && b < m) {
            if (old.get(a).text().equals(texts.get(b))) {
                kept[b] = true;
                from[b] = a;
                a++;
                b++;
            } else if (lcs[a + 1][b] >= lcs[a][b + 1]) {
                a++;
            } else {
                b++;
            }
        }
    }
}
