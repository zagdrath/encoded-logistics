/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.compile.Listing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The compiler (ELCL HANDOFF testing): every example compiles with no messages at all; one negative test per
// compile-time message ID (MESSAGES.md 00xx / 01xx), each reported on the right line; the listing's parts.
class CompilerTest {
    static final Path EXAMPLES = Path.of("docs/elcl/examples");

    static Stream<Path> examples() throws IOException {
        return Files.list(EXAMPLES).filter(path -> path.toString().endsWith(".elclp")).sorted();
    }

    static List<String> read(Path path) throws IOException {
        return SourceLine.split(Files.readString(path));
    }

    @ParameterizedTest
    @MethodSource("examples")
    void examplesCompileClean(Path example) throws IOException {
        Compiler.Result result = Compiler.compileTexts(read(example));
        assertTrue(result.diagnostics().isEmpty(), example.getFileName() + ": " + result.diagnostics());
        assertNotNull(result.program());
    }

    @Test
    void examplesExist() throws IOException {
        assertEquals(5, examples().count());
    }

    // A program around some body lines: PGM on line 0, the body from line 1, ENDPGM last.
    private static List<String> program(String... body) {
        List<String> lines = new ArrayList<>();
        lines.add("PGM");
        lines.addAll(List.of(body));
        lines.add("ENDPGM");
        return lines;
    }

    private static Diagnostic first(List<String> lines) {
        Compiler.Result result = Compiler.compileTexts(lines);
        assertFalse(result.diagnostics().isEmpty(), "No message for " + lines);
        return result.diagnostics().getFirst();
    }

    private static void expect(String id, int line, String... body) {
        Diagnostic diagnostic = first(program(body));
        assertEquals(id, diagnostic.message().id(), diagnostic.message().toString());
        assertEquals(line, diagnostic.line(), diagnostic.message().toString());
    }

    @Test
    void syntaxError() {
        expect("ELC0001", 2, "DCL VAR(&A) TYPE(*INT)", "CHGVAR VAR(&A) VALUE(&A + )");
        expect("ELC0001", 1, "SNDMSG MSG('unclosed");
        expect("ELC0001", 1, "SNDMSG MSG('x') FOO(1)");
        expect("ELC0001", 2, "DCL VAR(&A) TYPE(*CHAR)", "CHGVAR VAR(&A) VALUE(%NOPE(&A))");
    }

    @Test
    void undeclaredVariable() {
        expect("ELC0002", 3, "DCL VAR(&COUNT) TYPE(*INT)", "", "RTVITMCNT ITEM(IRON_INGOT) RTNCOUNT(&CONT)");
        expect("ELC0002", 1, "SNDMSG MSG(&NOPE)");
    }

    @Test
    void invalidForType() {
        expect("ELC0003", 1, "DCL VAR(&N) TYPE(*INT) VALUE('abc')");
        expect("ELC0003", 3, "DCL VAR(&N) TYPE(*INT)", "DCL VAR(&S) TYPE(*CHAR)", "CHGVAR VAR(&S) VALUE(&N)");
    }

    @Test
    void outOfRange() {
        expect("ELC0004", 1, "CHGRSOUT DEV(CTLIF01) SIDE(*UP) LVL(20)");
        expect("ELC0004", 1, "DCL VAR(&S) TYPE(*CHAR) LEN(2000)");
        expect("ELC0004", 1, "ADDJOBSCDE JOB(X) CMD(CALL PGM(A/B)) TIME(2561)");
    }

    @Test
    void divisionByZero() {
        expect("ELC0005", 2, "DCL VAR(&N) TYPE(*INT)", "CHGVAR VAR(&N) VALUE(&N / 0)");
    }

    @Test
    void overflow() {
        expect("ELC0007", 2, "DCL VAR(&N) TYPE(*INT)", "CHGVAR VAR(&N) VALUE(99999999999999999999)");
    }

    @Test
    void truncatedIsOnlyAWarning() {
        Compiler.Result result = Compiler.compileTexts(program("DCL VAR(&S) TYPE(*CHAR) LEN(3) VALUE('abcdef')"));
        assertEquals("ELC0008", result.diagnostics().getFirst().message().id());
        assertEquals(10, result.maxSeverity());
        assertNotNull(result.program(), "A warning must not stop the program");
    }

    @Test
    void unmatchedBlocks() {
        expect("ELC0009", 1, "IF COND(*TRUE) THEN(DO)");
        expect("ELC0009", 1, "ENDDO");
        expect("ELC0009", 1, "ELSE CMD(RETURN)");
        expect("ELC0009", 1, "WHEN COND(*TRUE) THEN(RETURN)");
        expect("ELC0009", 1, "LEAVE");
        expect("ELC0009", 1, "ENDFOR");
        Diagnostic noEnd = first(List.of("PGM", "RETURN"));
        assertEquals("ELC0009", noEnd.message().id());
        assertEquals("PGM without matching ENDPGM.", noEnd.message().text());
    }

    @Test
    void labelNotFound() {
        expect("ELC0010", 1, "GOTO CMDLBL(NOWHERE)");
        expect("ELC0010", 1, "CALLSUBR SUBR(NOSUCH)");
    }

    @Test
    void gotoIntoABlock() {
        expect("ELC0014", 1, "GOTO CMDLBL(INSIDE)", "DO", "INSIDE: RETURN", "ENDDO");
        // Out of a block (or within it) is fine.
        assertTrue(Compiler.compileTexts(program("DO", "GOTO CMDLBL(OUT)", "ENDDO", "OUT: RETURN")).ok());
    }

    @Test
    void dclAfterCommands() {
        expect("ELC0016", 2, "RETURN", "DCL VAR(&A) TYPE(*INT)");
    }

    @Test
    void commandErrors() {
        expect("ELC0101", 1, "FROBNICATE X(1)");
        expect("ELC0102", 1, "STRCRAFT ITEM(LOGIC_DIE)");
        expect("ELC0103", 1, "STRCRAFT ITEM(LOGIC_DIE) QTY(5) WAIT(*MAYBE)");
        expect("ELC0103", 2, "DCL VAR(&S) TYPE(*CHAR)", "RTVITMCNT ITEM(X) RTNCOUNT(&S)");
        expect("ELC0104", 1, "SNDMSG MSG('a') MSG('b')");
    }

    @Test
    void positionalAndNested() {
        Compiler.Result result = Compiler.compileTexts(program("DCL VAR(&Y) TYPE(*INT)", "IF (&Y *EQ 0) THEN(CHGVAR &Y 1)", "ELSE CMD(CHGVAR &Y 2)",
                "CALL ELSYS/RESTOCK PARM('IRON_INGOT' 500)", "SBMJOB CMD(CALL PGM(ZAGLIB/RESTOCK)) JOB(RESTOCK)"));
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    }

    @Test
    void monitors() {
        assertTrue(ElclMessages.monitors("ELC1200", "ELC1201"));
        assertTrue(ElclMessages.monitors("ELC0000", "ELC1403"));
        assertFalse(ElclMessages.monitors("ELC1200", "ELC1301"));
        assertEquals("Device CTLIF01 is offline.", ElclMessage.of("ELC1302", "CTLIF01").text());
    }

    @Test
    void listing() throws IOException {
        List<SourceLine> source = SourceLine.number(read(EXAMPLES.resolve("NOCWALL.elclp")), 0);
        Compiler.Result result = Compiler.compile(source);
        List<String> listing = Listing.build("ZAGLIB", "NOCWALL", "Day 2 07:13", "ELNET01", source, result);
        assertTrue(listing.getFirst().startsWith("ELCL Compile Listing   ZAGLIB/NOCWALL"));
        assertTrue(listing.get(2).startsWith(" 0001.00 /* NOCWALL"));
        assertTrue(listing.stream().anyMatch(line -> line.startsWith("  &PCT       *DEC    5 1    0006   0014*  0018")), String.join("\n", listing));
        assertEquals("  ELC0218  Program NOCWALL created in library ZAGLIB.", listing.getLast());
        Compiler.Result bad = Compiler.compileTexts(List.of("PGM", "FOO"));
        assertNull(bad.program());
    }
}
