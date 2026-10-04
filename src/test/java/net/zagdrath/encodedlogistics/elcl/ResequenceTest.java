/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.elcl.sync.Resequence;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A file read in against the member it replaces (OS.md 4): unchanged lines keep their sequence numbers and dates, new
// and changed lines get numbers between their neighbours and today's date; with no room, the member is numbered again.
class ResequenceTest {
    private static final List<SourceLine> OLD = List.of(new SourceLine(100, "PGM", 1), new SourceLine(200, "  CHGVAR &A 1", 2),
            new SourceLine(300, "ENDPGM", 1));

    @Test
    void unchangedLinesKeepTheirNumbers() {
        assertEquals(OLD, Resequence.merge(OLD, List.of("PGM", "  CHGVAR &A 1", "ENDPGM"), 9));
    }

    @Test
    void newLinesGoBetweenTheirNeighbours() {
        List<SourceLine> merged = Resequence.merge(OLD, List.of("PGM", "  DCL &A", "  CHGVAR &A 1", "ENDPGM", "/* end */"), 9);
        assertEquals(List.of(new SourceLine(100, "PGM", 1), new SourceLine(150, "  DCL &A", 9), new SourceLine(200, "  CHGVAR &A 1", 2),
                new SourceLine(300, "ENDPGM", 1), new SourceLine(400, "/* end */", 9)), merged);
    }

    @Test
    void aChangedLineIsNew() {
        List<SourceLine> merged = Resequence.merge(OLD, List.of("PGM", "  CHGVAR &A 2", "ENDPGM"), 9);
        assertEquals(List.of(new SourceLine(100, "PGM", 1), new SourceLine(200, "  CHGVAR &A 2", 9), new SourceLine(300, "ENDPGM", 1)), merged);
    }

    @Test
    void noRoomNumbersItAgain() {
        List<SourceLine> tight = List.of(new SourceLine(100, "A", 1), new SourceLine(101, "B", 1));
        List<SourceLine> merged = Resequence.merge(tight, List.of("A", "new", "B"), 9);
        assertEquals(List.of(new SourceLine(100, "A", 1), new SourceLine(200, "new", 9), new SourceLine(300, "B", 1)), merged);
    }
}
