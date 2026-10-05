/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.exec.CommandRunner;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.plc.PlcBlock;
import net.zagdrath.encodedlogistics.plc.PlcBlockEntity;
import net.zagdrath.encodedlogistics.plc.PlcProgram;
import net.zagdrath.encodedlogistics.plc.PlcSensors;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;

// The PLC (docs/plc). On its own: OFF without power, then RUN on FE from its buffer - its program echoes the west
// face's level to the east (a lamp lights), a RETAIN(*YES) counter keeps counting through STOP / RUN, STOP clears the
// outputs; an unmonitored escape is FAULT with its message and line, a command that needs a network ELC1502 (at run
// time, and when the editor's save compiles it); a Light Sensor reads, an empty slot is *NOMODULE with RTNSTS and
// ELC1501 without. On a network: PLC01, SNDPLCPGM (ELC1504 for a program not compiled for a PLC), STRPLC / ENDPLC,
// RTVRSIN / CHGRSOUT by its name.
final class PlcGameTests {
    private static final BlockPos PLC = new BlockPos(1, 2, 1), WEST = new BlockPos(0, 2, 1), LAMP = new BlockPos(2, 2, 1);

    private PlcGameTests() {}

    private static PlcBlockEntity plc(GameTestHelper helper) {
        return helper.getBlockEntity(PLC, PlcBlockEntity.class);
    }

    private static PlcProgram program(String name, String... body) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("PGM");
        lines.addAll(List.of(body));
        lines.add("ENDPGM");
        return new PlcProgram(name, lines, Map.of(), "TEST", Optional.empty(), "*ALL", "", Map.of());
    }

    private static void charge(GameTestHelper helper, int amount) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(PLC), Direction.UP);
        helper.assertTrue(handler != null, "No energy handler on the PLC");
        try (Transaction transaction = Transaction.openRoot()) {
            handler.insert(amount, transaction);
            transaction.commit();
        }
    }

    private static long retained(PlcBlockEntity plc, String name) {
        List<String> kept = plc.program() != null ? plc.program().retained().get(name) : null;
        return kept == null || kept.isEmpty() ? -1 : Long.parseLong(kept.getFirst());
    }

    @SuppressWarnings("removal")
    static void standalone(GameTestHelper helper) {
        helper.setBlock(PLC, ModBlocks.PLC.get());
        helper.setBlock(WEST, Blocks.REDSTONE_BLOCK);
        helper.setBlock(LAMP, Blocks.REDSTONE_LAMP);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        long[] count = new long[1];
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(plc(helper).mode() == PlcBlock.Mode.OFF, "Not OFF without power: " + plc(helper).mode());
                    charge(helper, 1_000);
                    plc(helper).load(program("ECHO", "DCL VAR(&IN) TYPE(*INT)", "DCL VAR(&N) TYPE(*INT) RETAIN(*YES)",
                            "RTVRSIN DEV(*SELF) SIDE(*WEST) RTNLVL(&IN)", "CHGRSOUT DEV(*SELF) SIDE(*EAST) LVL(&IN)", "CHGVAR VAR(&N) VALUE(&N + 1)"), true);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    PlcBlockEntity plc = plc(helper);
                    helper.assertTrue(plc.mode() == PlcBlock.Mode.RUN, "Not RUN: " + plc.mode() + " " + plc.fault() + " " + plc.note());
                    helper.assertTrue(helper.getBlockState(PLC).getValue(PlcBlock.STATE) == PlcBlock.Mode.RUN, "Model not RUN");
                    helper.assertTrue(plc.output(Direction.EAST) == 15, "East output " + plc.output(Direction.EAST));
                    helper.assertTrue(helper.getBlockState(LAMP).getValue(BlockStateProperties.LIT), "Lamp not lit");
                    helper.assertTrue(plc.drivenLine(Direction.EAST) == 5, "Driven by line " + plc.drivenLine(Direction.EAST));
                    helper.assertTrue(plc.scanTicks() >= 1, "No scan time");
                    count[0] = retained(plc, "&N");
                    helper.assertTrue(count[0] >= 2, "Retained counter " + count[0]);
                    plc.stop();
                    helper.assertTrue(plc.mode() == PlcBlock.Mode.STOP && plc.output(Direction.EAST) == 0, "STOP kept its outputs");
                })
                // A lamp goes out 4 ticks after its power does.
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertTrue(!helper.getBlockState(LAMP).getValue(BlockStateProperties.LIT), "Lamp lit in STOP");
                    helper.assertTrue(retained(plc(helper), "&N") >= count[0], "Retained counter lost on STOP");
                    count[0] = retained(plc(helper), "&N");
                    plc(helper).start();
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(retained(plc(helper), "&N") > count[0], "Retained counter started again: " + retained(plc(helper), "&N"));
                    plc(helper).load(program("BOOM", "DCL VAR(&N) TYPE(*INT)", "SNDPGMMSG MSG('boom') MSGTYPE(*ESCAPE)"), true);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    PlcBlockEntity plc = plc(helper);
                    helper.assertTrue(plc.mode() == PlcBlock.Mode.FAULT, "Not FAULT: " + plc.mode());
                    helper.assertTrue(plc.fault().line() == 3 && plc.fault().text().equals("boom"), "Fault " + plc.fault());
                    helper.assertTrue(helper.getBlockState(PLC).getValue(PlcBlock.STATE) == PlcBlock.Mode.FAULT, "Model not FAULT");
                    // A command that needs a network: refused at run time, and when the editor's save compiles it.
                    plc.load(program("CALLS", "CALL PGM(OTHER)"), true);
                    String error = plc.save(List.of("PGM", "CALL PGM(OTHER)", "ENDPGM"), player);
                    helper.assertTrue(error != null && error.startsWith("ELC1502") && error.endsWith("(line 2)"), "Save: " + error);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    PlcBlockEntity plc = plc(helper);
                    helper.assertTrue(plc.fault() != null && plc.fault().id().equals("ELC1502"), "Runtime ELC1502: " + plc.fault());
                    helper.assertTrue(plc.insert(new ItemStack(ModItems.LIGHT_SENSOR.get())), "Module not taken");
                    PlcSensors.Reading light = plc.read(0);
                    helper.assertTrue(light.ok() && light.value().intValue() >= 0 && light.value().intValue() <= 15, "Light " + light);
                    plc.load(program("SENSE", "DCL VAR(&V) TYPE(*DEC)", "DCL VAR(&S) TYPE(*CHAR) LEN(10)", "RTVSNSVAL MODULE(1) RTNVAL(&V) RTNSTS(&S)",
                            "IF COND(&S *NE '*OK') THEN(SNDPGMMSG MSG('light') MSGTYPE(*ESCAPE))", "RTVSNSVAL MODULE(2) RTNVAL(&V) RTNSTS(&S)",
                            "IF COND(&S *NE '*NOMODULE') THEN(SNDPGMMSG MSG('empty') MSGTYPE(*ESCAPE))", "RTVSNSVAL MODULE(2) TYPE(*LIGHT) RTNVAL(&V)"), true);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    PlcBlockEntity plc = plc(helper);
                    helper.assertTrue(plc.fault() != null && plc.fault().id().equals("ELC1501") && plc.fault().line() == 8, "Sensor fault " + plc.fault());
                    // The block state shows the module (next tick after it went in).
                    helper.assertTrue(helper.getBlockState(PLC).getValue(PlcBlock.SLOTS.get(0)) == net.zagdrath.encodedlogistics.plc.PlcModule.LIGHT_SENSOR,
                            "Slot model");
                    ItemStack out = plc.removeLast();
                    helper.assertTrue(out.is(ModItems.LIGHT_SENSOR.get()), "Module out " + out);
                })
                .thenSucceed();
    }

    private static NetworkRef network;

    private static CommandRunner.Result run(GameTestHelper helper, ServerPlayer player, String line) {
        return CommandRunner.run(new TerminalContext(helper.getLevel().getServer(), network, null, player), line);
    }

    private static void fails(GameTestHelper helper, ServerPlayer player, String line, String id) {
        CommandRunner.Result result = run(helper, player, line);
        helper.assertTrue(result.escape() != null && result.escape().id().equals(id), line + ": " + result.escape() + ", wanted " + id);
    }

    private static void ok(GameTestHelper helper, ServerPlayer player, String line) {
        CommandRunner.Result result = run(helper, player, line);
        helper.assertTrue(result.ok(), line + ": " + result.escape());
    }

    private static void compile(ElclSystem system, String user, String name, Compiler.Target target, String... lines) throws ElclException {
        try {
            ElclServices.libraries().createLibrary(system, user, "PLCT", "*PROD", "");
        } catch (ElclException ignored) {}
        ElclServices.libraries().createMember(system, user, "PLCT", name, "");
        ElclServices.libraries().save(system, user, "PLCT", name, SourceLine.number(List.of(lines), 1));
        if (!ElclServices.libraries().compile(system, user, "PLCT", name, "PLCT", name, target).created()) {
            throw new IllegalStateException(name + " didn't compile");
        }
    }

    @SuppressWarnings("removal")
    static void networked(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 1, UpsDevice.class);
        // Storage for the test library's members.
        RackGameTests.driveBay(helper, ElclGameTests.BAY);
        helper.setBlock(PLC, ModBlocks.PLC.get());
        helper.setBlock(LAMP, Blocks.REDSTONE_LAMP);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    PlcBlockEntity plc = plc(helper);
                    helper.assertTrue(plc.isOnline(), "PLC offline");
                    network = plc.network();
                    var server = helper.getLevel().getServer();
                    ElclDevices.list(server, network);
                    helper.assertTrue(plc.deviceName().equals("PLC01"), "Name " + plc.deviceName());
                    helper.assertTrue(plc.mode() == PlcBlock.Mode.STOP, "Powered by the network: " + plc.mode());
                    ElclSystem system = new ElclSystem(server, network);
                    String user = new TerminalContext(server, network, null, player).user();
                    ElclServices.jobs().interactive(system, user, "plc", "ELDESK01");
                    try {
                        compile(system, user, "LAMPON", Compiler.Target.PLC, "PGM", "CHGRSOUT DEV(*SELF) SIDE(*EAST) LVL(15)", "ENDPGM");
                        compile(system, user, "JOBPGM", Compiler.Target.JOB, "PGM", "ENDPGM");
                    } catch (ElclException e) {
                        throw new IllegalStateException(e.getMessage());
                    }
                    fails(helper, player, "STRPLC DEV(PLC01)", "ELC1505");
                    fails(helper, player, "SNDPLCPGM PGM(PLCT/JOBPGM) DEV(PLC01)", "ELC1504");
                    fails(helper, player, "SNDPLCPGM PGM(PLCT/LAMPON) DEV(NOPE01)", "ELC1301");
                    fails(helper, player, "SNDPLCPGM PGM(PLCT/LAMPON) DEV(UPS01)", "ELC1303");
                    ok(helper, player, "SNDPLCPGM PGM(PLCT/LAMPON) DEV(PLC01)");
                    helper.assertTrue(plc.program() != null && plc.program().name().equals("LAMPON"), "Not loaded");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    PlcBlockEntity plc = plc(helper);
                    helper.assertTrue(plc.mode() == PlcBlock.Mode.RUN, "Not RUN: " + plc.mode() + " " + plc.fault());
                    helper.assertTrue(helper.getBlockState(LAMP).getValue(BlockStateProperties.LIT), "Lamp not lit");
                    CommandRunner.Result in = run(helper, player, "RTVRSIN DEV(PLC01) SIDE(*MAX)");
                    helper.assertTrue(in.ok(), "RTVRSIN by name: " + in.escape());
                    fails(helper, player, "RTVRSIN DEV(*SELF) SIDE(*UP)", "ELC1301");
                    ok(helper, player, "ENDPLC DEV(PLC01)");
                    helper.assertTrue(plc.mode() == PlcBlock.Mode.STOP, "ENDPLC: " + plc.mode());
                    ok(helper, player, "CHGRSOUT DEV(PLC01) SIDE(*WEST) LVL(9)");
                    helper.assertTrue(plc.output(Direction.WEST) == 9, "CHGRSOUT by name");
                    ok(helper, player, "STRPLC DEV(PLC01)");
                    helper.assertTrue(plc.mode() == PlcBlock.Mode.RUN, "STRPLC: " + plc.mode());
                })
                .thenSucceed();
    }
}
