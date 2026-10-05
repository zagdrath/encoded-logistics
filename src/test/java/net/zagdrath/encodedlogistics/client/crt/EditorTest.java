/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.parse.Parser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The source editor (screens handoff E): every example round-trips byte for byte (sequence numbers kept), each line
// command on a line and on blocks, the command line's commands, the syntax check's line and message for each
// compile-time message, F4's statement rewrite, the layout; and the prompter on SBMJOB's nested command.
class EditorTest {
    @BeforeAll
    static void language() throws IOException {
        LayoutTest.language();
    }

    static Stream<Path> examples() throws IOException {
        return Files.list(Path.of("docs/elcl/examples")).filter(path -> path.toString().endsWith(".elclp")).sorted();
    }

    static List<SourceLine> numbered(String... texts) {
        return SourceLine.number(List.of(texts), 0);
    }

    static List<String> texts(EditorModel model) {
        return model.texts();
    }

    static EditorModel model(String... texts) {
        return new EditorModel(numbered(texts));
    }

    static EditorModel.Command cmd(EditorModel model, int line, String text) {
        return new EditorModel.Command(model.lines.get(line), text);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void roundTrip(Path example) throws IOException {
        String original = Files.readString(example, StandardCharsets.UTF_8);
        List<SourceLine> source = SourceLine.number(SourceLine.split(original), 0);
        CrtTerminal terminal = LayoutTest.terminal();
        EditorPanel editor = new EditorPanel(terminal, "ZAGLIB", "TEST", source, false);
        terminal.push(editor);
        terminal.command.set("FILE");
        terminal.submit();
        List<SourceLine> saved = editor.model().save(5);
        assertEquals(original, SourceLine.join(SourceLine.texts(saved)), example.getFileName().toString());
        assertEquals(source, saved, "Sequence numbers or dates changed");
        assertEquals("MAIN", terminal.current().id(), "FILE didn't exit");
        LayoutTest.Host host = (LayoutTest.Host) hostOf(terminal);
        assertTrue(host.sent.stream().anyMatch(sent -> sent.endsWith("savecommit")), "FILE didn't save");
    }

    private static CrtTerminal.Host hostOf(CrtTerminal terminal) {
        try {
            var field = CrtTerminal.class.getDeclaredField("host");
            field.setAccessible(true);
            return (CrtTerminal.Host) field.get(terminal);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void typedOverTheSequenceNumber() {
        assertEquals("I3", EditorModel.typed("0012.00", "i312.00"));
        assertEquals("D", EditorModel.typed("0012.00", "d012.00"));
        assertEquals("DD", EditorModel.typed("0012.00", "dd12.00"));
        assertEquals("I10", EditorModel.typed("0012.00", "i102.00"));
        assertEquals("", EditorModel.typed("0012.00", "0012.00"));
        assertEquals("CC", EditorModel.typed("0012.00", "CC"));
    }

    @Test
    void insertAndDelete() {
        EditorModel model = model("A", "B", "C", "D", "E");
        assertNull(model.apply(List.of(cmd(model, 0, "I"))));
        assertEquals(List.of("A", "", "B", "C", "D", "E"), texts(model));
        model.apply(List.of(cmd(model, 2, "I3")));
        assertEquals(List.of("A", "", "B", "", "", "", "C", "D", "E"), texts(model));
        model = model("A", "B", "C", "D", "E");
        model.apply(List.of(cmd(model, 1, "D")));
        assertEquals(List.of("A", "C", "D", "E"), texts(model));
        model.apply(List.of(cmd(model, 1, "D3")));
        assertEquals(List.of("A"), texts(model));
        model = model("A", "B", "C", "D", "E");
        assertNotNull(model.apply(List.of(cmd(model, 1, "DD"))), "Block pending");
        assertTrue(model.pending());
        model.apply(List.of(cmd(model, 3, "DD")));
        assertEquals(List.of("A", "E"), texts(model));
        assertFalse(model.pending());
    }

    @Test
    void copyAndMove() {
        EditorModel model = model("A", "B", "C", "D");
        model.apply(List.of(cmd(model, 0, "C"), cmd(model, 2, "A")));
        assertEquals(List.of("A", "B", "C", "A", "D"), texts(model));
        model = model("A", "B", "C", "D");
        model.apply(List.of(cmd(model, 3, "C"), cmd(model, 1, "B")));
        assertEquals(List.of("A", "D", "B", "C", "D"), texts(model));
        model = model("A", "B", "C", "D");
        model.apply(List.of(cmd(model, 0, "CC"), cmd(model, 1, "CC"), cmd(model, 3, "A")));
        assertEquals(List.of("A", "B", "C", "D", "A", "B"), texts(model));
        model = model("A", "B", "C", "D");
        model.apply(List.of(cmd(model, 2, "CC"), cmd(model, 3, "CC"), cmd(model, 0, "B")));
        assertEquals(List.of("C", "D", "A", "B", "C", "D"), texts(model));
        model = model("A", "B", "C", "D");
        model.apply(List.of(cmd(model, 0, "M"), cmd(model, 2, "A")));
        assertEquals(List.of("B", "C", "A", "D"), texts(model));
        model = model("A", "B", "C", "D");
        model.apply(List.of(cmd(model, 3, "M"), cmd(model, 0, "B")));
        assertEquals(List.of("D", "A", "B", "C"), texts(model));
        model = model("A", "B", "C", "D", "E");
        model.apply(List.of(cmd(model, 0, "MM"), cmd(model, 1, "MM"), cmd(model, 4, "A")));
        assertEquals(List.of("C", "D", "E", "A", "B"), texts(model));
        model = model("A", "B", "C", "D", "E");
        model.apply(List.of(cmd(model, 3, "MM"), cmd(model, 4, "MM"), cmd(model, 1, "B")));
        assertEquals(List.of("A", "D", "E", "B", "C"), texts(model));
        // A copy without its target waits, over a later Enter too.
        model = model("A", "B", "C");
        assertEquals("Block command pending.", model.apply(List.of(cmd(model, 0, "C"))));
        model.apply(List.of(cmd(model, 2, "A")));
        assertEquals(List.of("A", "B", "C", "A"), texts(model));
    }

    @Test
    void repeatExcludeShowAndCols() {
        EditorModel model = model("A", "B", "C");
        model.apply(List.of(cmd(model, 1, "R")));
        assertEquals(List.of("A", "B", "B", "C"), texts(model));
        model.apply(List.of(cmd(model, 0, "R3")));
        assertEquals(List.of("A", "A", "A", "A", "B", "B", "C"), texts(model));
        model = model("A", "B", "C", "D", "E");
        model.apply(List.of(cmd(model, 1, "X")));
        assertTrue(model.lines.get(1).excluded != 0 && model.lines.get(2).excluded == 0);
        model.apply(List.of(cmd(model, 1, "S")));
        assertEquals(0, model.lines.get(1).excluded);
        model.apply(List.of(cmd(model, 1, "X3")));
        assertTrue(model.lines.get(3).excluded != 0 && model.lines.get(4).excluded == 0);
        model.apply(List.of(cmd(model, 1, "F")));
        assertTrue(model.lines.stream().allMatch(line -> line.excluded == 0));
        assertFalse(model.ruler);
        model.apply(List.of(cmd(model, 0, "COLS")));
        assertTrue(model.ruler);
        assertEquals("Line command ZZ not valid.", model.apply(List.of(cmd(model, 0, "ZZ"))));
    }

    @Test
    void numbering() {
        EditorModel model = model("A", "B", "C");
        model.apply(List.of(cmd(model, 0, "I2")));
        // Typed on (inserted lines never typed on aren't saved).
        model.setText(model.lines.get(1), "A1");
        model.setText(model.lines.get(2), "A2");
        List<SourceLine> saved = model.save(7);
        assertEquals(List.of(100, 133, 166, 200, 300), saved.stream().map(SourceLine::seq).toList());
        assertEquals(List.of(0, 7, 7, 0, 0), saved.stream().map(SourceLine::date).toList());
        // No room between 0001.00 and 0001.01: everything is renumbered.
        EditorModel tight = new EditorModel(List.of(new SourceLine(100, "A", 0), new SourceLine(101, "B", 0)));
        tight.apply(List.of(cmd(tight, 0, "I")));
        tight.setText(tight.lines.get(1), "A1");
        assertEquals(List.of(100, 200, 300), tight.save(1).stream().map(SourceLine::seq).toList());
    }

    // An editor on a program, a line's text changed through the screen and Enter pressed: the message line.
    private static String edited(int line, String text, String... program) {
        CrtTerminal terminal = LayoutTest.terminal();
        EditorPanel editor = new EditorPanel(terminal, "ZAGLIB", "TEST", numbered(program), false);
        terminal.push(editor);
        for (CrtField field : terminal.current().fields) {
            if (field.col == 8 && field.row == 4 + line) {
                field.set(text);
            }
        }
        terminal.submit();
        return messageOf(terminal);
    }

    private static String messageOf(CrtTerminal terminal) {
        return LayoutTest.row(terminal.compose(), 22).trim();
    }

    @ParameterizedTest
    @ValueSource(strings = { "ELC0001|CHGVAR VAR(&A) VALUE(&A + )", "ELC0002|CHGVAR VAR(&B) VALUE(1)", "ELC0003|CHGVAR VAR(&A) VALUE('abc')",
            "ELC0004|CHGRSOUT DEV(CTLIF01) SIDE(*UP) LVL(20)", "ELC0005|CHGVAR VAR(&A) VALUE(&A / 0)",
            "ELC0007|CHGVAR VAR(&A) VALUE(99999999999999999999)", "ELC0009|ENDDO", "ELC0010|GOTO CMDLBL(NOWHERE)",
            "ELC0016|DCL VAR(&Z) TYPE(*INT)", "ELC0101|FROBNICATE", "ELC0102|STRCRAFT ITEM(X)", "ELC0103|STRCRAFT ITEM(X) QTY(1) WAIT(*MAYBE)",
            "ELC0104|SNDMSG MSG('a') MSG('b')" })
    void syntaxCheck(String spec) {
        String id = spec.substring(0, 7), text = spec.substring(8);
        // Line 3 (0004.00) is changed; the program round it is good.
        String message = edited(3, text, "PGM", "  DCL VAR(&A) TYPE(*INT)", "  CHGVAR VAR(&A) VALUE(1)", "  CHGVAR VAR(&A) VALUE(2)", "ENDPGM");
        assertTrue(message.startsWith(id + ":"), spec + " -> " + message);
    }

    @Test
    void gotoIntoABlockIsChecked() {
        String message = edited(2, "  GOTO CMDLBL(IN)", "PGM", "  DCL VAR(&A) TYPE(*INT)", "  CHGVAR VAR(&A) VALUE(1)", "  DO", "IN: RETURN", "  ENDDO", "ENDPGM");
        assertTrue(message.startsWith("ELC0014:"), message);
    }

    @Test
    void commandLine() {
        CrtTerminal terminal = LayoutTest.terminal();
        EditorPanel editor = new EditorPanel(terminal, "ZAGLIB", "TEST", numbered("PGM", "  SNDMSG MSG('one')", "  SNDMSG MSG('two')", "ENDPGM"), false);
        terminal.push(editor);
        terminal.command.set("FIND two");
        terminal.submit();
        assertEquals("String two found.", messageOf(terminal));
        terminal.command.set("CHANGE SNDMSG PRTTXT ALL");
        terminal.submit();
        assertEquals(List.of("PGM", "  PRTTXT MSG('one')", "  PRTTXT MSG('two')", "ENDPGM"), editor.model().texts());
        terminal.command.set("BOTTOM");
        terminal.submit();
        terminal.command.set("TOP");
        terminal.submit();
        terminal.command.set("SAVE");
        terminal.submit();
        LayoutTest.Host host = (LayoutTest.Host) hostOf(terminal);
        assertTrue(host.sent.stream().anyMatch(sent -> sent.endsWith("savebegin ZAGLIB TEST")));
        assertTrue(host.sent.stream().anyMatch(sent -> sent.contains("PRTTXT MSG('two')")));
        assertEquals("EDTMBR", terminal.current().id(), "SAVE left the editor");
        terminal.command.set("CANCEL");
        terminal.submit();
        // Changed: CANCEL asks first.
        assertNotNull(terminal.window());
        terminal.submit();
        assertEquals("MAIN", terminal.current().id());
    }

    // A new member opens on a blank line; typing and Enter opens another; one left blank goes on the next Enter;
    // "Beginning of data" takes I.
    @Test
    void emptyMember() {
        CrtTerminal terminal = LayoutTest.terminal();
        EditorPanel editor = new EditorPanel(terminal, "ZAGLIB", "NEW", List.of(), false);
        terminal.push(editor);
        int page = editor.model().lines.size();
        assertTrue(page > 1, "a page of open lines");
        assertTrue(editor.model().lines.stream().allMatch(open -> open.fresh && open.text.isEmpty()), "open lines");
        assertFalse(editor.model().dirty, "untouched blank lines aren't a change");
        CrtField line = terminal.focused();
        assertNotNull(line);
        assertEquals(8, line.col, "the cursor on the line's text");
        line.set("PGM");
        terminal.submit();
        assertEquals(List.of("PGM", ""), editor.model().texts().subList(0, 2), "an open line under the typed one");
        assertEquals(1, terminal.focused().row - 4, "the cursor on the open line");
        terminal.focused().set("ENDPGM");
        terminal.submit();
        assertEquals(List.of("PGM", "ENDPGM", ""), editor.model().texts().subList(0, 3));
        assertEquals(page, editor.model().lines.size(), "the page kept full");
        // Off the open lines (to the command line) and Enter: they stay open, and aren't saved.
        terminal.focus(terminal.command);
        terminal.submit();
        assertEquals(List.of("PGM", "ENDPGM"), SourceLine.texts(editor.model().save(5)));
        // I on "Beginning of data": a line before the first.
        terminal.focus(terminal.command);
        terminal.nextField(false);
        assertEquals(EditorPanel.class, terminal.current().getClass());
        CrtField begin = terminal.focused();
        assertEquals(3, begin.row, "Beginning of data's margin");
        begin.set("I");
        terminal.submit();
        assertEquals(List.of("", "PGM", "ENDPGM", ""), editor.model().texts().subList(0, 4));
    }

    // Lines typed into a new member get their sequence numbers on Enter (the open blank line keeps '''''''), and Up /
    // Down move the cursor between the lines' text at the same column, not into the margins.
    @Test
    void typedLinesNumberedAndArrowKeys() {
        CrtTerminal terminal = LayoutTest.terminal();
        EditorPanel editor = new EditorPanel(terminal, "ZAGLIB", "NEW", List.of(), false);
        terminal.push(editor);
        terminal.focused().set("PGM");
        terminal.submit();
        terminal.focused().set("DCL VAR(&N) TYPE(*INT)");
        terminal.submit();
        List<EditorModel.Line> lines = editor.model().lines;
        assertTrue(lines.get(0).seq > 0 && lines.get(1).seq > lines.get(0).seq, "typed lines not numbered on Enter");
        assertEquals(0, lines.get(2).seq, "the open line numbered before it's typed on");
        assertEquals(List.of("PGM", "DCL VAR(&N) TYPE(*INT)"), SourceLine.texts(editor.model().save(5)), "open lines saved");
        // The cursor on the open line: Up goes to the DCL's text at the same column, then up again to PGM's.
        CrtField open = terminal.focused();
        open.cursor = 4;
        int column = open.cursorColumn();
        terminal.moveVertical(true);
        CrtField above = terminal.focused();
        assertEquals(open.row - 1, above.row, "Up didn't go one row up");
        assertEquals(open.col, above.col, "Up went into the margin");
        assertEquals(column, above.cursorColumn(), "Up lost the column");
        terminal.moveVertical(false);
        assertEquals(open.row, terminal.focused().row, "Down didn't come back");
    }

    // A new member opens on a page of blank lines, kept full as it's typed on; a blank line left between typed ones
    // stays (spacing); a program not finished yet (no ENDPGM) isn't flagged for it on the line last typed.
    @Test
    void newMemberPageSpacingAndOpenProgram() {
        CrtTerminal terminal = LayoutTest.terminal();
        EditorPanel editor = new EditorPanel(terminal, "ZAGLIB", "NEW", List.of(), false);
        terminal.push(editor);
        int page = editor.model().lines.size();
        assertTrue(page > 10, "only " + page + " open lines");
        List<CrtField> texts = terminal.current().fields.stream().filter(field -> field.col == 8).toList();
        texts.get(0).set("PGM");
        texts.get(1).set("DCL VAR(&STS) TYPE(*CHAR) LEN(10)");
        texts.get(3).set("CHGVAR VAR(&STS) VALUE('X')");
        terminal.focus(texts.get(3));
        terminal.submit();
        assertEquals(List.of("PGM", "DCL VAR(&STS) TYPE(*CHAR) LEN(10)", "", "CHGVAR VAR(&STS) VALUE('X')"),
                SourceLine.texts(editor.model().save(5)), "spacing line dropped");
        assertTrue(editor.model().lines.size() >= page, "page not kept full");
        String message = LayoutTest.row(terminal.compose(), 22).strip();
        assertFalse(message.contains("ELC0009"), "flagged an unfinished program: " + message);
        for (int r = 0; r < 24; r++) {
            for (int c = 0; c < 80; c++) {
                assertFalse(terminal.compose().reverse[r][c], "a line shown reversed at row " + r);
            }
        }
    }

    // A click anywhere on "F11=Full screen" (its second word too) presses F11.
    @Test
    void clickingAKeyLabel() {
        CrtTerminal terminal = LayoutTest.terminal();
        EditorPanel editor = new EditorPanel(terminal, "ZAGLIB", "TEST", numbered("PGM", "ENDPGM"), false);
        terminal.push(editor);
        String keys = editor.keys();
        terminal.click(23, 1 + keys.indexOf("screen"), false);
        assertEquals(terminal.current().title(), CrtPanel.tr("crt.encodedlogistics.edit.title_full"));
    }

    @Test
    void promptedStatementIsWrittenBack() {
        CrtTerminal terminal = LayoutTest.terminal();
        EditorPanel editor = new EditorPanel(terminal, "ZAGLIB", "TEST", numbered("PGM", "  DCL VAR(&J) TYPE(*CHAR)", "  STRCRAFT ITEM(LOGIC_DIE) +",
                "    QTY(64)", "ENDPGM"), false);
        terminal.push(editor);
        for (CrtField field : terminal.current().fields) {
            if (field.col == 8 && field.row == 4 + 2) {
                terminal.focus(field);
            }
        }
        terminal.functionKey(4);
        assertEquals("PROMPT", terminal.current().id());
        PrompterPanel prompter = (PrompterPanel) terminal.current();
        terminal.functionKey(10);
        // RTNCRFJOB: the field before TYPE, the last.
        prompter.fields.get(prompter.fields.size() - 2).set("&J");
        terminal.submit();
        assertEquals("EDTMBR", terminal.current().id());
        List<String> texts = editor.model().texts();
        assertEquals("  STRCRAFT ITEM(LOGIC_DIE) QTY(64) RTNCRFJOB(&J)", texts.get(2));
        assertEquals("ENDPGM", texts.get(3));
    }

    @Test
    void wrapsLongStatements() {
        List<String> lines = EditorPanel.wrap("  ", "    ", "SNDMSG MSG('Restocking' *BCAT &ITEM *BCAT 'job' *BCAT &JOB) TOUSR(*SYSOPR) TOTRM(ELDESK01)", 71);
        assertTrue(lines.size() > 1);
        assertTrue(lines.getFirst().endsWith(" +"));
        assertTrue(lines.stream().allMatch(line -> line.length() <= 71), lines.toString());
        // Joined back, it parses to the same command.
        assertTrue(Parser.parse(lines).ok(), lines.toString());
    }

    @Test
    void layout() throws IOException {
        List<String> restock = SourceLine.split(Files.readString(Path.of("docs/elcl/examples/RESTOCK.elclp")));
        CrtTerminal terminal = LayoutTest.terminal();
        EditorPanel editor = new EditorPanel(terminal, "ZAGLIB", "RESTOCK", SourceLine.number(restock, 0), false);
        terminal.push(editor);
        // COLS on line 1, X3 on line 10; line 9's &COUNT typed as &CONT, the cursor at its column 34 (field 0 is
        // "Beginning of data"'s margin).
        List<CrtField> fields = new ArrayList<>(terminal.current().fields);
        fields.get(1).set("COLS");
        fields.get(1 + 2 * 9).set("X3");
        fields.get(1 + 2 * 8 + 1).set(restock.get(8).replace("&COUNT", "&CONT"));
        terminal.submit();
        for (CrtField field : terminal.current().fields) {
            if (field.col == 8 && field.row == 13) {
                terminal.focus(field);
                field.cursor = 33;
            }
        }
        CrtGrid grid = terminal.compose();
        // Row 3's ruler is the real editor's (*...+... 1 ...+... 2, numbers under the tens), not the layout's.
        LayoutTest.compare("05_source_editor", grid, 0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 21, 22, 23);
        assertTrue(grid.reverse[13][10], "The bad line isn't reversed");
    }

    @Test
    void promptsTheNestedCommand() {
        CrtTerminal terminal = LayoutTest.terminal();
        String[] done = new String[1];
        assertTrue(terminal.prompter("SBMJOB CMD(CALL PGM(ZAGLIB/RESTOCK)) JOB(RESTOCK)", false, command -> done[0] = command));
        PrompterPanel sbmjob = (PrompterPanel) terminal.current();
        // F4 on CMD: the inner command's prompter.
        terminal.focus(sbmjob.fields.getFirst());
        terminal.functionKey(4);
        assertEquals("Call Program (CALL)", terminal.current().title());
        PrompterPanel call = (PrompterPanel) terminal.current();
        terminal.functionKey(10);
        call.fields.get(1).set("'IRON_INGOT' 500");
        terminal.submit();
        assertEquals("SBMJOB", sbmjob.command().split(" ")[0]);
        terminal.submit();
        assertEquals("SBMJOB CMD(CALL PGM(ZAGLIB/RESTOCK) PARM('IRON_INGOT' 500)) JOB(RESTOCK)", done[0]);
        assertTrue(Parser.parseCommand(done[0]).ok());
        // Prompted again: the same values.
        assertTrue(terminal.prompter(done[0], false, command -> done[0] = command));
        terminal.submit();
        assertEquals("SBMJOB CMD(CALL PGM(ZAGLIB/RESTOCK) PARM('IRON_INGOT' 500)) JOB(RESTOCK)", done[0]);
    }
}
