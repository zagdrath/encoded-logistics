/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.zagdrath.encodedlogistics.block.TerminalDeskBlock;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.terminal.TerminalCommands;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// The OS commands on Command Entry (screens handoff D.1, E): each returns its completion message (and puts it in the
// job log); ELSYS is read-only; the screens' queries answer; SNDMSG leaves a message waiting.
final class ElclGameTests {
    static final BlockPos DESK = new BlockPos(1, 1, 3);

    private ElclGameTests() {}

    static final BlockPos BAY = new BlockPos(2, 1, 3);

    // A networked rack, a Terminal Desk on it and a Drive Bay with an 8K drive (members take storage).
    static void desk(GameTestHelper helper) {
        RackGameTests.networkedRack(helper);
        RackGameTests.driveBay(helper, BAY);
        var state = ModBlocks.TERMINAL_DESK.get().defaultBlockState().setValue(TerminalDeskBlock.FACING, Direction.SOUTH);
        helper.setBlock(DESK, state);
        helper.setBlock(TerminalDeskBlock.other(state, DESK), state.setValue(TerminalDeskBlock.PART, TerminalDeskBlock.Part.DUMMY));
    }

    static void expect(GameTestHelper helper, TerminalContext context, String line, String id) {
        TerminalOutput out = TerminalCommands.execute(context, line);
        String message = out.message() != null ? out.message().getString() : "(none)";
        helper.assertTrue(message.startsWith(id), line + ": " + message + ", wanted " + id);
    }

    @SuppressWarnings("removal")
    static void osCommands(GameTestHelper helper) {
        desk(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    TerminalDeskBlockEntity desk = helper.getBlockEntity(DESK, TerminalDeskBlockEntity.class);
                    TerminalContext context = new TerminalContext(helper.getLevel().getServer(), desk.network(), desk, player);
                    helper.assertTrue(context.network() != null, "Desk offline");
                    ElclSystem system = new ElclSystem(context.server(), context.network());
                    String job = ElclServices.jobs().interactive(system, context.user(), "test", "ELDESK01").number();
                    expect(helper, context, "CRTLIB LIB(ZAGLIB) TEXT('Zagdrath automation scripts')", "ELC0210");
                    expect(helper, context, "CRTLIB ZAGLIB", "ELC0204");
                    expect(helper, context, "CHGLIB LIB(ZAGLIB) AUT(*CHANGE)", "ELC0212");
                    expect(helper, context, "CRTMBR MBR(ZAGLIB/SCRATCH) TEXT('Scratch')", "ELC0214");
                    expect(helper, context, "CPYMBR FROM(ELSYS/RESTOCK) TO(ZAGLIB/RESTOCK)", "ELC0215");
                    expect(helper, context, "CRTELPGM PGM(ZAGLIB/RESTOCK)", "ELC0218");
                    expect(helper, context, "RNMMBR MBR(ZAGLIB/SCRATCH) NEWNAME(TEMP)", "ELC0216");
                    expect(helper, context, "DLTMBR MBR(ZAGLIB/TEMP)", "ELC0217");
                    expect(helper, context, "DLTPGM PGM(ZAGLIB/RESTOCK)", "ELC0219");
                    expect(helper, context, "DLTMBR MBR(ELSYS/RESTOCK)", "ELC0205");
                    expect(helper, context, "CHGSYSVAL SYSVAL(PHOSPHOR) VALUE(*AMBER)", "ELC0222");
                    expect(helper, context, "CHGSYSVAL SYSVAL(PHOSPHOR) VALUE(*PINK)", "ELC0103");
                    expect(helper, context, "ADDJOBSCDE JOB(NOCWALL) CMD(CALL PGM(ZAGLIB/NOCWALL)) FRQ(*INTERVAL) INTERVAL(30)", "ELC0312");
                    expect(helper, context, "RMVJOBSCDE JOB(NOCWALL)", "ELC0313");
                    expect(helper, context, "ADDTRGEVT TRG(LEVER) EVENT(*RSCHANGE) PGM(ZAGLIB/DOORS) DEV(CTLIF01)", "ELC0314");
                    expect(helper, context, "RMVTRGEVT TRG(LEVER)", "ELC0315");
                    expect(helper, context, "HLDJOB JOB(" + job + ")", "ELC0308");
                    expect(helper, context, "RLSJOB JOB(" + job + ")", "ELC0309");
                    expect(helper, context, "ENDJOB JOB(" + job + ") OPTION(*IMMED)", "ELC0311");
                    expect(helper, context, "SBMJOB CMD(CALL PGM(ZAGLIB/RESTOCK)) JOB(RESTOCK)", "ELC0301");
                    expect(helper, context, "CALL PGM(ZAGLIB/RESTOCK)", "ELC0203");
                    expect(helper, context, "DLTLIB LIB(ZAGLIB)", "ELC0211");
                    helper.assertTrue(ElclServices.sysvals().get(system, "PHOSPHOR").equals("*AMBER"), "PHOSPHOR not changed");
                    try {
                        long logged = ElclServices.jobs().log(system, job).stream().filter(entry -> entry.id().equals("ELC0210")).count();
                        helper.assertTrue(logged == 1, "ELC0210 in the job log " + logged + " times");
                    } catch (Exception e) {
                        helper.fail("Job log: " + e);
                    }
                    TerminalOutput members = TerminalService.handle(context, TerminalService.SCREEN, "members ELSYS");
                    helper.assertTrue(members.lines().size() == 8, "ELSYS members " + members.lines().size());
                    TerminalOutput source = TerminalService.handle(context, TerminalService.SCREEN, "source ELSYS RESTOCK");
                    helper.assertTrue(source.lines().getFirst().cells().getFirst().text().getString().equals("20"), "RESTOCK lines");
                    helper.assertTrue(source.lines().getFirst().cells().get(2).text().getString().equals("1"), "ELSYS not read-only");
                    expect(helper, context, "SNDMSG MSG('Restocking') TOUSR(*REQUESTER)", "");
                    helper.assertTrue(ElclServices.messages().unread(system, context.user()) == 1, "No message waiting");
                })
                .thenSucceed();
    }
}
