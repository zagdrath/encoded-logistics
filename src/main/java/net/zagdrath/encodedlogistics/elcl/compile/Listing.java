/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.SourceLine;

// The compile listing (ELCL_SPEC.md 10, the screens handoff's screen 7), as the spooled file's lines: a header (member,
// day and time, system), the source with sequence numbers (change dates past column 80), the cross reference -
// variables (type, length, the lines using them, * where set) then labels and subroutines - and the message summary:
// each message's sequence number, ID, severity and text, totals by severity, and ELC0218 or ELC0206.
public final class Listing {
    public static final String RULER = "*...+... 1 ...+... 2 ...+... 3 ...+... 4 ...+... 5 ...+... 6 ...+... 7 ...+... 8";

    private Listing() {}

    // when: the game's day and time ("Day 2 07:13"); system: SYSNAME.
    public static List<String> build(String library, String member, String when, String system, List<SourceLine> source, Compiler.Result result) {
        List<String> out = new ArrayList<>();
        out.add("ELCL Compile Listing   " + library + "/" + member + "   " + when + "   " + system);
        out.add(" SEQNBR  " + RULER + "   Date");
        for (SourceLine line : source) {
            out.add(" " + line.seqText() + " " + pad(line.text(), SourceLine.WIDTH) + "   " + line.dateText());
        }
        out.add("");
        out.add(" ".repeat(21) + "Cross Reference");
        out.add("  Variable   Type    Length  References");
        result.variables().forEach((name, decl) -> out.add("  " + pad(name, 10) + " " + pad(decl.type().special(), 7) + " " + pad(decl.lengthText(), 7)
                + refs(result.variableRefs().get(name), source)));
        if (!result.labelRefs().isEmpty() || !result.subroutineRefs().isEmpty()) {
            out.add("  Label      Type            References");
            result.labelRefs().forEach((name, refs) -> out.add("  " + pad(name, 10) + " " + pad("*LABEL", 15) + refs(refs, source)));
            result.subroutineRefs().forEach((name, refs) -> out.add("  " + pad(name, 10) + " " + pad("*SUBR", 15) + refs(refs, source)));
        }
        out.add("");
        out.add(" ".repeat(21) + "Message Summary");
        out.add("  Seq      Msg ID   Sev  Text");
        int[] bySeverity = new int[4];
        for (Diagnostic diagnostic : result.diagnostics()) {
            ElclMessage message = diagnostic.message();
            out.add("  " + seq(diagnostic.line(), source) + "  " + message.id() + "  " + String.format(Locale.ROOT, "%3d", message.severity()) + "  "
                    + message.text());
            bySeverity[Math.min(3, message.severity() / 10)]++;
        }
        out.add(String.format(Locale.ROOT, "  Total %d  Info %d  Warning %d  Error %d  Severe %d", result.diagnostics().size(), bySeverity[0],
                bySeverity[1], bySeverity[2], bySeverity[3]));
        ElclMessage end = result.ok() ? ElclMessage.of("ELC0218", member, library) : ElclMessage.of("ELC0206", member);
        out.add("  " + end.id() + "  " + end.text());
        return out;
    }

    // The sequence number of a record, "0012.00" (blank past the end).
    private static String seq(int line, List<SourceLine> source) {
        return line >= 0 && line < source.size() ? source.get(line).seqText() : "       ";
    }

    // "0004   0014*   0017": each line once (set there if any use there sets it), in order.
    private static String refs(List<Compiler.Ref> refs, List<SourceLine> source) {
        Map<Integer, Boolean> lines = new TreeMap<>();
        if (refs != null) {
            for (Compiler.Ref ref : refs) {
                lines.merge(ref.line(), ref.modified(), Boolean::logicalOr);
            }
        }
        StringBuilder out = new StringBuilder();
        lines.forEach((line, modified) -> {
            String seq = line < source.size() ? SourceLine.shortSeq(source.get(line).seq()) : "????";
            out.append(pad(seq + (modified ? "*" : ""), 7));
        });
        return out.toString().stripTrailing();
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }
}
