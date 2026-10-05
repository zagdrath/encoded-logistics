/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.cmd.Wait;
import net.zagdrath.encodedlogistics.elcl.vm.Vm;
import net.zagdrath.encodedlogistics.elcl.vm.VmHost;
import net.zagdrath.encodedlogistics.elcl.db.FileAccess;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The VM (ELCL_SPEC.md 5-9, HANDOFF testing): arithmetic and precedence, string and list operations, every loop form,
// LEAVE / ITERATE, SELECT, GOTO, subroutines, nested CALL with PARM by reference, MONMSG at command and program level
// (ranges, ELC0000, CMPDTA) with RCVMSG, the runtime errors, the instruction budget (an endless loop yields and can be
// ended), and a job saved part-way (in a DLYJOB wait) and loaded again carrying on to the same end. Programs report
// with SNDPGMMSG (its text without trailing blanks), which the fake host collects.
class VmTest {
    // The fake host (DbVmTest's too): programs by name, compiled with the file formats in `formats` (by LIB/FILE), the
    // files in `files` when a test gives some.
    static final class Host implements VmHost {
        final Map<String, List<String>> programs = new HashMap<>();
        final Map<String, String> formats = new HashMap<>();
        final List<String> said = new ArrayList<>(), escaped = new ArrayList<>();
        @Nullable ElclMessage failed;
        @Nullable FileAccess files;
        long time = 1_000;
        int maxList = 4_096;

        Host program(String name, String... lines) {
            programs.put(name, List.of(lines));
            return this;
        }

        @Override
        public Loaded program(String library, String name) throws ElclException {
            List<String> source = programs.get(name);
            if (source == null) {
                throw new ElclException("ELC0203", name, library);
            }
            return new Loaded("TEST/" + name, source, formats);
        }

        @Override
        public @Nullable FileAccess files() {
            return files;
        }

        @Override
        public boolean waitDone(Wait wait) {
            return true;
        }

        @Override
        public String itemName(String item) {
            return "Name of " + item;
        }

        @Override
        public boolean interactive() {
            return false;
        }

        @Override
        public <T> @Nullable T context(Class<T> type) {
            return null;
        }

        @Override
        public @Nullable ElclMessage authorise(CommandDefinition.Auth auth) {
            return null;
        }

        @Override
        public void logCommand(String command) {}

        @Override
        public void message(ElclMessage message) {
            said.add(message.text());
        }

        @Override
        public void escaped(String program, ElclMessage message) {
            escaped.add(program + " " + message.id());
        }

        @Override
        public void failed(ElclMessage message) {
            failed = message;
        }

        @Override
        public long gameTime() {
            return time;
        }

        @Override
        public long dayTime() {
            return time;
        }

        @Override
        public int maxList() {
            return maxList;
        }
    }

    // Registered commands as executors see their values: TSTSAY says MSG(), TSTSBM says CMD() as it would be submitted.
    static {
        if (CommandRegistry.get("TSTSAY") == null) {
            CommandRegistry.register(CommandDefinition.of("TSTSAY", "Test say").positional(1)
                    .p(ParamDef.of("MSG", "Message", ParamDef.Kind.CHAR)).build());
            CommandRegistry.bind("TSTSAY", call -> call.send(ElclMessage.user("USR0001", 0, call.text("MSG"))));
            CommandRegistry.register(CommandDefinition.of("TSTSBM", "Test submit").positional(1)
                    .p(ParamDef.of("CMD", "Command", ParamDef.Kind.COMMAND)).build());
            CommandRegistry.bind("TSTSBM", call -> call.send(ElclMessage.user("USR0001", 0, call.text("CMD"))));
        }
    }

    static Vm start(Host host, String name, Object... args) throws ElclException {
        return Vm.start(host, host.program("*LIBL", name), List.of(args));
    }

    // Runs MAIN to its end; what it said.
    static List<String> run(Host host) throws ElclException {
        Vm vm = start(host, "MAIN");
        for (int i = 0; i < 1_000 && vm.state() != Vm.State.ENDED; i++) {
            vm.run(10_000);
        }
        assertEquals(Vm.State.ENDED, vm.state(), "Didn't end");
        return host.said;
    }

    private static List<String> run(String... lines) throws ElclException {
        return run(new Host().program("MAIN", lines));
    }

    private static String say(String expr) {
        return "SNDPGMMSG MSG(" + expr + ")";
    }

    @Test
    void arithmeticAndPrecedence() throws ElclException {
        List<String> said = run("PGM", "DCL VAR(&I) TYPE(*INT)", "DCL VAR(&D) TYPE(*DEC) LEN(7 2)",
                "CHGVAR VAR(&I) VALUE(2 + 3 * 4)", say("%CHAR(&I)"),
                "CHGVAR VAR(&I) VALUE((2 + 3) * 4)", say("%CHAR(&I)"),
                "CHGVAR VAR(&I) VALUE(-7 / 2)", say("%CHAR(&I)"),
                "CHGVAR VAR(&I) VALUE(17 // 5)", say("%CHAR(&I)"),
                "CHGVAR VAR(&D) VALUE(10.0 / 3)", say("%CHAR(&D)"),
                "CHGVAR VAR(&D) VALUE(2.005)", say("%CHAR(&D)"),
                "CHGVAR VAR(&I) VALUE(&D * 10)", say("%CHAR(&I)"),
                "ENDPGM");
        assertEquals(List.of("14", "20", "-3", "2", "3.33", "2.01", "20"), said);
    }

    @Test
    void strings() throws ElclException {
        List<String> said = run("PGM", "DCL VAR(&S) TYPE(*CHAR) LEN(10) VALUE('Iron')",
                say("&S *CAT 'X'"), say("&S *BCAT 'Ingot'"), say("&S *TCAT 'X'"),
                say("%SST('Logistics' 2 3)"), say("%TRIM('  pad  ') *TCAT '|'"), say("%CHAR(%SCAN('st' 'Logistics'))"),
                say("%UPPER(&S)"), say("%CHAR(%LEN(&S))"), say("%NAME(IRON_INGOT)"),
                "ENDPGM");
        assertEquals(List.of("Iron      X", "Iron Ingot", "IronX", "ogi", "pad|", "5", "IRON", "4", "Name of IRON_INGOT"), said);
    }

    @Test
    void listsAndForeach() throws ElclException {
        List<String> said = run("PGM", "DCL VAR(&L) TYPE(*LIST)", "DCL VAR(&E) TYPE(*CHAR) LEN(8)",
                "ADDLSTE LIST(&L) VALUE('B')", "ADDLSTE LIST(&L) VALUE('C')", "ADDLSTE LIST(&L) VALUE('A') POS(1)",
                "RMVLSTE LIST(&L) POS(2)", say("%CHAR(%SIZE(&L)) *BCAT %ELEM(&L 2)"),
                "FOREACH VAR(&E) IN(&L)", say("&E"), "ENDFOR",
                "CLRLST LIST(&L)", say("%CHAR(%SIZE(&L))"), "ENDPGM");
        assertEquals(List.of("2 C", "A", "C", "0"), said);
    }

    @Test
    void loops() throws ElclException {
        List<String> said = run("PGM", "DCL VAR(&I) TYPE(*INT)", "DCL VAR(&N) TYPE(*INT)",
                "DOWHILE COND(&I *LT 3)", "CHGVAR VAR(&I) VALUE(&I + 1)", "ENDDO", say("'while' *BCAT %CHAR(&I)"),
                "DOUNTIL COND(*TRUE)", "CHGVAR VAR(&I) VALUE(&I + 10)", "ENDDO", say("'until' *BCAT %CHAR(&I)"),
                "DOFOR VAR(&N) FROM(10) TO(1) BY(-3)", say("'for' *BCAT %CHAR(&N)"), "ENDDO",
                // ITERATE skips the even ones; LEAVE OUTER leaves both loops at 5.
                "CHGVAR VAR(&I) VALUE(0)",
                "OUTER: DOWHILE COND(*TRUE)",
                "  DOFOR VAR(&N) FROM(1) TO(9)",
                "    IF COND(&N // 2 *EQ 0) THEN(ITERATE)",
                "    IF COND(&N *GT 5) THEN(LEAVE CMDLBL(OUTER))",
                "    CHGVAR VAR(&I) VALUE(&I + &N)",
                "  ENDDO",
                "ENDDO", say("'odd' *BCAT %CHAR(&I)"), "ENDPGM");
        assertEquals(List.of("while 3", "until 13", "for 10", "for 7", "for 4", "for 1", "odd 9"), said);
    }

    @Test
    void selectIfElseAndGoto() throws ElclException {
        List<String> said = run("PGM", "DCL VAR(&T) TYPE(*CHAR) LEN(4)", "DCL VAR(&I) TYPE(*INT)",
                "AGAIN: CHGVAR VAR(&I) VALUE(&I + 1)",
                "SELECT", "WHEN COND(&I *EQ 1) THEN(CHGVAR VAR(&T) VALUE('ONE'))", "WHEN COND(&I *EQ 2) THEN(DO)", "CHGVAR VAR(&T) VALUE('TWO')",
                "ENDDO", "OTHERWISE CMD(CHGVAR VAR(&T) VALUE('MANY'))", "ENDSELECT", say("&T"),
                "IF COND(&I *LT 3) THEN(GOTO CMDLBL(AGAIN))", "ELSE CMD(" + say("'done'") + ")",
                "IF COND(*FALSE) THEN(DO)", say("'no'"), "ENDDO", "ELSE CMD(DO)", say("'else'"), "ENDDO", "ENDPGM");
        assertEquals(List.of("ONE", "TWO", "MANY", "done", "else"), said);
    }

    @Test
    void subroutines() throws ElclException {
        List<String> said = run("PGM", "DCL VAR(&R) TYPE(*INT)", "DCL VAR(&N) TYPE(*INT) VALUE(3)",
                "CALLSUBR SUBR(TWICE) RTNVAL(&R)", say("%CHAR(&R)"), "CALLSUBR SUBR(OUTER)", say("%CHAR(&N)"), "RETURN",
                "SUBR SUBR(TWICE)", "ENDSUBR RTNVAL(&N * 2)",
                "SUBR SUBR(OUTER)", "CALLSUBR SUBR(TWICE) RTNVAL(&N)", "ENDSUBR", "ENDPGM");
        assertEquals(List.of("6", "6"), said);
    }

    @Test
    void callPassesVariablesByReference() throws ElclException {
        Host host = new Host().program("MAIN", "PGM", "DCL VAR(&A) TYPE(*INT) VALUE(5)", "DCL VAR(&S) TYPE(*CHAR) LEN(8) VALUE('x')",
                "CALL PGM(TEST/ADD) PARM(&A 10 &S)", say("%CHAR(&A) *BCAT &S"), "ENDPGM")
                .program("ADD", "PGM PARM(&X &Y &T)", "DCL VAR(&X) TYPE(*INT)", "DCL VAR(&Y) TYPE(*INT)", "DCL VAR(&T) TYPE(*CHAR) LEN(8)",
                        "CHGVAR VAR(&X) VALUE(&X + &Y)", "CALL PGM(DEEPER) PARM(&T)", "ENDPGM")
                .program("DEEPER", "PGM PARM(&Z)", "DCL VAR(&Z) TYPE(*CHAR) LEN(8)", "CHGVAR VAR(&Z) VALUE('changed')", "ENDPGM");
        assertEquals(List.of("15 changed"), run(host));
    }

    @Test
    void monitors() throws ElclException {
        Host host = new Host().program("MAIN", "PGM", "DCL VAR(&I) TYPE(*INT)", "DCL VAR(&ID) TYPE(*CHAR) LEN(7)", "DCL VAR(&L) TYPE(*LIST)",
                "MONMSG MSGID(ELC0006) EXEC(DO)", "RCVMSG RTNMSGID(&ID)", say("'program-level' *BCAT &ID"), "ENDDO",
                // Command level, a range: ELC0005 is in ELC0000's.
                "CHGVAR VAR(&I) VALUE(1 / &I)", "MONMSG MSGID(ELC1200 ELC0000) EXEC(" + say("'divided'") + ")",
                // Not taken by the first (wrong data), taken by the second.
                "SNDPGMMSG MSG('boom') MSGID(USR0042) MSGTYPE(*ESCAPE)", "MONMSG MSGID(USR0042) CMPDTA('bang')",
                "MONMSG MSGID(USR0000) EXEC(" + say("'user range'") + ")",
                // Program level: carries on after it.
                "CHGVAR VAR(&ID) VALUE(%ELEM(&L 3))", say("'after'"),
                // A called program's unmonitored escape: ELC0013 here.
                "CALL PGM(FAILS)", "MONMSG MSGID(ELC0013) EXEC(DO)", "RCVMSG RTNMSGID(&ID)", say("&ID"), "ENDDO",
                "ENDPGM")
                .program("FAILS", "PGM", "DCL VAR(&I) TYPE(*INT)", "CHGVAR VAR(&I) VALUE(5 / &I)", "ENDPGM");
        assertEquals(List.of("divided", "user range", "program-level ELC0006", "after", "ELC0013"), run(host));
        assertTrue(host.escaped.contains("TEST/FAILS ELC0005"), "Called program's escape not logged: " + host.escaped);
        assertNull(host.failed);
    }

    @Test
    void unmonitoredEscapeEndsTheJob() throws ElclException {
        Host host = new Host().program("MAIN", "PGM", "DCL VAR(&L) TYPE(*LIST)", "ADDLSTE LIST(&L) VALUE('a')", "ADDLSTE LIST(&L) VALUE('b')",
                say("'never'"), "ENDPGM");
        host.maxList = 1;
        run(host);
        assertNotNull(host.failed);
        assertEquals("ELC0015", host.failed.id());
        assertTrue(host.said.isEmpty());
    }

    @Test
    void parameterCountAndCallDepth() throws ElclException {
        Host host = new Host().program("MAIN", "PGM", "CALL PGM(TWO) PARM(1)", "MONMSG MSGID(ELC0012) EXEC(" + say("'count'") + ")",
                "CALL PGM(SELF)", "MONMSG MSGID(ELC0013) EXEC(" + say("'deep'") + ")", "ENDPGM")
                .program("TWO", "PGM PARM(&A &B)", "DCL VAR(&A) TYPE(*INT)", "DCL VAR(&B) TYPE(*INT)", "ENDPGM")
                .program("SELF", "PGM", "CALL PGM(SELF)", "ENDPGM");
        assertEquals(List.of("count", "deep"), run(host));
        assertTrue(host.escaped.stream().anyMatch(e -> e.endsWith("ELC0011")), "No ELC0011: " + host.escaped);
    }

    @Test
    void anEndlessLoopKeepsToItsBudgetAndCanBeEnded() throws ElclException {
        Host host = new Host().program("MAIN", "PGM", "DCL VAR(&I) TYPE(*INT)", "DOWHILE COND(*TRUE)", "CHGVAR VAR(&I) VALUE(&I + 1)", "ENDDO", "ENDPGM");
        Vm vm = start(host, "MAIN");
        for (int tick = 0; tick < 50; tick++) {
            assertEquals(100, vm.run(100), "Over or under its budget");
            assertEquals(Vm.State.RUNNING, vm.state());
        }
        vm.end();
        vm.run(100);
        assertEquals(Vm.State.ENDED, vm.state());
    }

    @Test
    void aSavedJobCarriesOnWhereItWas() throws ElclException {
        Host host = new Host().program("MAIN", "PGM", "DCL VAR(&I) TYPE(*INT)", "DCL VAR(&L) TYPE(*LIST)", "DCL VAR(&D) TYPE(*DEC) LEN(5 1)",
                "DOFOR VAR(&I) FROM(1) TO(3)", "ADDLSTE LIST(&L) VALUE(%CHAR(&I))", "CHGVAR VAR(&D) VALUE(&D + 0.5)", "CALLSUBR SUBR(NAP)", "ENDDO",
                say("%CHAR(%SIZE(&L)) *BCAT %ELEM(&L 3) *BCAT %CHAR(&D)"), "RETURN", "SUBR SUBR(NAP)", "DLYJOB DLY(5)", "ENDSUBR", "ENDPGM");
        Vm vm = start(host, "MAIN");
        vm.run(1_000);
        assertEquals(Vm.State.WAITING, vm.state());
        assertEquals(0, vm.run(1_000), "A wait cost budget");
        CompoundTag saved = vm.save();
        Vm loaded = Vm.load(host, saved);
        for (int i = 0; i < 10 && loaded.state() != Vm.State.ENDED; i++) {
            host.time += 100;
            loaded.run(1_000);
        }
        assertEquals(Vm.State.ENDED, loaded.state());
        assertEquals(List.of("3 3 1.5"), host.said);
    }

    // A registered command's value that fails is its escape message, not the expression's source.
    @Test
    void aCommandsValueThatFailsIsItsEscape() throws ElclException {
        List<String> said = run("PGM", "DCL VAR(&S) TYPE(*CHAR) LEN(4) VALUE('abcd')", "DCL VAR(&Z) TYPE(*INT)", "DCL VAR(&ID) TYPE(*CHAR) LEN(7)",
                "TSTSAY MSG(%SST(&S 2 2))",
                "TSTSAY MSG(%SST(&S 1 99))", "MONMSG MSGID(ELC0000) EXEC(DO)", "RCVMSG RTNMSGID(&ID)", "TSTSAY MSG(&ID)", "ENDDO",
                "TSTSAY MSG(%CHAR(1 / &Z))", "MONMSG MSGID(ELC0000) EXEC(DO)", "RCVMSG RTNMSGID(&ID)", "TSTSAY MSG(&ID)", "ENDDO",
                "ENDPGM");
        assertEquals(List.of("bc", "ELC0004", "ELC0005"), said);
    }

    // A submitted command takes the variables' values now: text quoted, numbers as they are, a special value as one.
    @Test
    void aNestedCommandTakesItsVariablesValues() throws ElclException {
        List<String> said = run("PGM", "DCL VAR(&ITEM) TYPE(*CHAR) LEN(16) VALUE('IRON_INGOT')", "DCL VAR(&N) TYPE(*INT) VALUE(-5)",
                "DCL VAR(&M) TYPE(*CHAR) LEN(9) VALUE('*partial')", "DCL VAR(&Q) TYPE(*CHAR) LEN(8) VALUE('it''s')",
                "TSTSBM CMD(STRCRAFT ITEM(&ITEM) QTY(&N * -2))",
                "TSTSBM CMD(STRCRAFT ITEM(&Q) QTY(1) MISSING(&M))", "ENDPGM");
        assertEquals(List.of("STRCRAFT ITEM('IRON_INGOT') QTY((-5) * -2)", "STRCRAFT ITEM('it''s') QTY(1) MISSING(*PARTIAL)"), said);
    }

    @Test
    void numericEdges() throws ElclException {
        Host host = new Host().program("MAIN", "PGM", "DCL VAR(&I) TYPE(*INT)",
                // // is an integer remainder, of *DEC operands too.
                "CHGVAR VAR(&I) VALUE(7.5 // 2)", say("%CHAR(&I)"),
                // %DEC with one argument is LEN(15 5).
                say("%CHAR(%DEC(1.123456))"),
                "CHGVAR VAR(&I) VALUE(-9223372036854775807 - 1)",
                say("%CHAR(%ABS(&I))"), "MONMSG MSGID(ELC0007) EXEC(" + say("'abs overflow'") + ")",
                "CALL PGM(BYZERO)", "MONMSG MSGID(ELC0013) EXEC(" + say("'by zero'") + ")",
                "CALL PGM(LGL) PARM('x')", "MONMSG MSGID(ELC0003) EXEC(" + say("'not a logical'") + ")",
                "CALL PGM(LGL) PARM('1')", "ENDPGM")
                .program("LGL", "PGM PARM(&L)", "DCL VAR(&L) TYPE(*LGL)", "IF COND(&L) THEN(" + say("'true'") + ")", "ENDPGM")
                // DOFOR BY(0) would never end: ELC0004.
                .program("BYZERO", "PGM", "DCL VAR(&I) TYPE(*INT)", "DCL VAR(&Z) TYPE(*INT)", "DOFOR VAR(&I) FROM(1) TO(3) BY(&Z)", say("'never'"),
                        "ENDDO", "ENDPGM");
        assertEquals(List.of("1", "1.12346", "abs overflow", "by zero", "not a logical", "true"), run(host));
        assertTrue(host.escaped.contains("TEST/BYZERO ELC0004"), host.escaped.toString());
    }

    // Labels alone on two lines both name the statement after them; a label on ENDSUBR or ENDPGM is that end.
    @Test
    void labels() throws ElclException {
        List<String> said = run("PGM", "DCL VAR(&I) TYPE(*INT)", "GOTO CMDLBL(SECOND)", "FIRST:", "SECOND:", "CHGVAR VAR(&I) VALUE(&I + 1)",
                "IF COND(&I *LT 2) THEN(GOTO CMDLBL(FIRST))", say("%CHAR(&I)"), "CALLSUBR SUBR(S)", "GOTO CMDLBL(DONE)", say("'skipped'"),
                "SUBR SUBR(S)", "GOTO CMDLBL(OUT)", say("'skipped'"), "OUT: ENDSUBR", "DONE: ENDPGM");
        assertEquals(List.of("2"), said);
    }

    // Every shipped example lowers (and starts) without trouble, with the files they declare.
    @Test
    void examplesStart() throws IOException, ElclException {
        try (var files = Files.list(Path.of("docs/elcl/examples"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".elclp")).toList()) {
                Host host = new Host().program("EX", SourceLine.split(Files.readString(file)).toArray(String[]::new));
                host.formats.putAll(SystemData.saved(CompilerTest.shippedFormats()));
                var compiled = net.zagdrath.encodedlogistics.elcl.compile.Compiler.compileTexts(host.programs.get("EX"), CompilerTest.shippedFiles());
                assertTrue(compiled.ok(), file + " doesn't compile");
                int params = compiled.program().params().size();
                Object[] args = new Object[params];
                java.util.Arrays.fill(args, "1");
                assertNotNull(start(host, "EX", args), file.toString());
            }
        }
    }
}
