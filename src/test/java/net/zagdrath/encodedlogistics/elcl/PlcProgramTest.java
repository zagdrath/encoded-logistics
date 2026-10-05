/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef.VarType;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.compile.VarDecl;
import net.zagdrath.encodedlogistics.elcl.vm.Vm;
import net.zagdrath.encodedlogistics.elcl.vm.VmHost;
import net.zagdrath.encodedlogistics.plc.PlcProgram;

// The PLC's side of ELCL (docs/plc HANDOFF 4): RETAIN (ELC1506 outside a PLC program), the compile target of a PLC
// without a network (ELC1502 for anything but the language, the delays, its own faces and its modules - nested in an IF
// too), DLYTICK, a scan's variables carried into the next (a VALUE set only at the first), the failing line a fault
// shows, a host refusing a command at run time, and retained values kept as text and read back.
class PlcProgramTest {
    private static List<String> program(String... body) {
        List<String> lines = new ArrayList<>();
        lines.add("PGM");
        lines.addAll(List.of(body));
        lines.add("ENDPGM");
        return lines;
    }

    private static List<String> ids(Compiler.Result result) {
        return result.diagnostics().stream().map(d -> d.message().id()).toList();
    }

    @Test
    void retainWarnsOutsideAPlcProgram() {
        List<String> source = program("DCL VAR(&COUNT) TYPE(*INT) RETAIN(*YES)", "CHGVAR VAR(&COUNT) VALUE(&COUNT + 1)");
        Compiler.Result job = Compiler.compileTexts(source, net.zagdrath.encodedlogistics.elcl.compile.FileResolver.NONE, Compiler.Target.JOB);
        assertTrue(job.ok(), "A warning stopped it");
        assertEquals(List.of("ELC1506"), ids(job));
        Compiler.Result plc = Compiler.compileTexts(source, net.zagdrath.encodedlogistics.elcl.compile.FileResolver.NONE, Compiler.Target.PLC);
        assertTrue(plc.ok());
        assertTrue(plc.diagnostics().isEmpty(), plc.diagnostics().toString());
        assertTrue(plc.variables().get("&COUNT").retain());
        Compiler.Result plain = Compiler.compileTexts(program("DCL VAR(&N) TYPE(*INT)"), net.zagdrath.encodedlogistics.elcl.compile.FileResolver.NONE,
                Compiler.Target.PLC);
        assertFalse(plain.variables().get("&N").retain());
    }

    private static Compiler.Result local(String... body) {
        return Compiler.compileTexts(program(body), net.zagdrath.encodedlogistics.elcl.compile.FileResolver.NONE, Compiler.Target.PLC_LOCAL);
    }

    @Test
    void aPlcOnItsOwnRunsOnlyWhatNeedsNoNetwork() {
        assertEquals(List.of("ELC1502"), ids(local("CALL PGM(OTHER)")));
        assertEquals(List.of("ELC1502"), ids(local("DCL VAR(&L) TYPE(*INT)", "RTVRSIN DEV(CTLIF01) SIDE(*UP) RTNLVL(&L)")));
        assertEquals(List.of("ELC1502"), ids(local("DCL VAR(&C) TYPE(*INT)", "RTVITMCNT ITEM(MINECRAFT:STONE) RTNCOUNT(&C)")));
        assertEquals(List.of("ELC1502"), ids(local("DCL VAR(&X) TYPE(*INT)", "IF COND(&X *EQ 1) THEN(CALL PGM(OTHER))")));
        Compiler.Result own = local("DCL VAR(&L) TYPE(*INT)", "DCL VAR(&V) TYPE(*DEC)", "DCL VAR(&S) TYPE(*CHAR) LEN(10)",
                "RTVRSIN DEV(*SELF) SIDE(*NORTH) RTNLVL(&L)", "CHGRSOUT DEV(*SELF) SIDE(*ALL) LVL(&L)", "RTVSNSVAL MODULE(1) RTNVAL(&V) RTNSTS(&S)",
                "DLYTICK TICKS(5)", "DLYJOB DLY(1)", "SNDPGMMSG MSG('scan')");
        assertTrue(own.ok(), own.diagnostics().toString());
        // On a network, all of it.
        Compiler.Result networked = Compiler.compileTexts(program("CALL PGM(OTHER)"), net.zagdrath.encodedlogistics.elcl.compile.FileResolver.NONE,
                Compiler.Target.PLC);
        assertTrue(networked.ok(), networked.diagnostics().toString());
        assertTrue(Compiler.plcLocal("chgrsout", "*self"));
        assertFalse(Compiler.plcLocal("CHGRSOUT", "CTLIF01"));
    }

    @Test
    void dlytickIsInRange() {
        Compiler.Result result = Compiler.compileTexts(program("DLYTICK TICKS(0)"));
        assertEquals("ELC0004", result.firstError().message().id());
        assertEquals("ELC0004", Compiler.compileTexts(program("DLYTICK TICKS(1201)")).firstError().message().id());
    }

    @Test
    void dlytickWaitsItsTicks() throws ElclException {
        VmTest.Host host = new VmTest.Host().program("MAIN", "PGM", "DLYTICK TICKS(5)", "SNDPGMMSG MSG('done')", "ENDPGM");
        Vm vm = Vm.start(host, host.program("*LIBL", "MAIN"), List.of());
        vm.run(100);
        assertEquals(Vm.State.WAITING, vm.state());
        host.time += 4;
        vm.run(100);
        assertEquals(Vm.State.WAITING, vm.state(), "It woke a tick early");
        host.time += 1;
        vm.run(100);
        assertEquals(Vm.State.ENDED, vm.state());
        assertEquals(List.of("done"), host.said);
    }

    @Test
    void aScanCarriesItsVariablesIntoTheNext() throws ElclException {
        VmTest.Host host = new VmTest.Host().program("SCAN", "PGM", "DCL VAR(&N) TYPE(*INT) VALUE(5)", "DCL VAR(&S) TYPE(*CHAR) LEN(4)",
                "CHGVAR VAR(&N) VALUE(&N + 1)", "CHGVAR VAR(&S) VALUE('X')", "ENDPGM");
        VmHost.Loaded loaded = host.program("*LIBL", "SCAN");
        Vm first = Vm.start(host, loaded, Map.of());
        first.run(100);
        assertEquals(Vm.State.ENDED, first.state());
        Map<String, Object> carried = first.variables();
        assertEquals(6L, carried.get("&N"));
        Vm second = Vm.start(host, loaded, carried);
        second.run(100);
        assertEquals(7L, second.variables().get("&N"), "VALUE was set again on the next scan");
        assertEquals(VarType.INT, second.declarations().get("&N").type());
        // A carried value of a variable no longer declared is left out; a fresh start (STOP -> RUN) sets VALUE.
        Vm fresh = Vm.start(host, loaded, Map.of("&GONE", 1L));
        fresh.run(100);
        assertEquals(6L, fresh.variables().get("&N"));
        assertNull(fresh.variables().get("&GONE"));
    }

    @Test
    void aFaultKnowsItsLine() throws ElclException {
        VmTest.Host host = new VmTest.Host().program("BAD", "PGM", "DCL VAR(&N) TYPE(*INT)", "CHGVAR VAR(&N) VALUE(1)",
                "SNDPGMMSG MSG('broken') MSGTYPE(*ESCAPE)", "ENDPGM");
        Vm vm = Vm.start(host, host.program("*LIBL", "BAD"), List.of());
        vm.run(100);
        assertNotNull(vm.failure());
        assertEquals(4, vm.failureLine());
        assertEquals(1L, vm.variables().get("&N"), "The variables at the fault are gone");
    }

    @Test
    void aHostCanRefuseACommand() throws ElclException {
        VmTest.Host host = new VmTest.Host().program("MAIN", "PGM", "TSTSAY MSG('hi')", "ENDPGM");
        host.refused = "TSTSAY";
        Vm vm = Vm.start(host, host.program("*LIBL", "MAIN"), List.of());
        vm.run(100);
        assertEquals("ELC1502", vm.failure().id());
        assertTrue(host.said.isEmpty());
    }

    @Test
    void retainedValuesComeBackAsTheyWere() {
        assertEquals(42L, PlcProgram.decode(new VarDecl("&I", VarType.INT, 0, 0, null, 0, true), PlcProgram.encode(42L)));
        assertEquals(new BigDecimal("3.25"), PlcProgram.decode(new VarDecl("&D", VarType.DEC, 15, 2, null, 0, true), PlcProgram.encode(new BigDecimal("3.25"))));
        assertEquals(true, PlcProgram.decode(new VarDecl("&B", VarType.LGL, 0, 0, null, 0, true), PlcProgram.encode(true)));
        assertEquals("AB  ", PlcProgram.decode(new VarDecl("&C", VarType.CHAR, 4, 0, null, 0, true), PlcProgram.encode("AB  ")));
        assertEquals(List.of("A", "B"), PlcProgram.decode(new VarDecl("&L", VarType.LIST, 0, 0, null, 0, true), PlcProgram.encode(List.of("A", "B"))));
        assertNull(PlcProgram.decode(new VarDecl("&I", VarType.INT, 0, 0, null, 0, true), List.of("not a number")));
    }
}
