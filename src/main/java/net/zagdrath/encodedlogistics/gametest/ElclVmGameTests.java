/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.device.DeviceSources;
import net.zagdrath.encodedlogistics.elcl.device.DisplayDevice;
import net.zagdrath.encodedlogistics.elcl.device.Displays;
import net.zagdrath.encodedlogistics.elcl.device.PrinterDevice;
import net.zagdrath.encodedlogistics.elcl.device.Printers;
import net.zagdrath.encodedlogistics.elcl.job.JobManager;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.MessageService;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.terminal.TerminalCommands;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;

// The VM and the mod commands in the game (Part 3): each mod command on Command Entry against a real network (an Egress
// Port on a chest, items in storage), with fake printer and display devices (the Midrange line's interface, Part 7);
// CALL in the interactive job, its waits and ENDJOB; and every shipped example running where its hardware exists.
final class ElclVmGameTests {
    private static final BlockPos CHEST = new BlockPos(0, 1, 1), PORT_CABLE = new BlockPos(1, 1, 1);

    private ElclVmGameTests() {}

    // A fake Line Printer (PRT01) on one system: what it printed, and whether it has paper.
    static final class FakePrinter implements PrinterDevice {
        final List<String> titles = new ArrayList<>();
        final List<String> printed = new ArrayList<>();
        boolean paper = true;

        @Override
        public String name() {
            return "PRT01";
        }

        @Override
        public boolean online() {
            return true;
        }

        @Override
        public boolean hasPaper() {
            return paper;
        }

        @Override
        public void print(String title, List<String> lines) {
            titles.add(title);
            printed.addAll(lines);
        }
    }

    // A fake Status Display (NOCDSP01): its lines.
    static final class FakeDisplay implements DisplayDevice {
        final String[] shown = new String[8];
        int last;

        @Override
        public String name() {
            return "NOCDSP01";
        }

        @Override
        public boolean online() {
            return true;
        }

        @Override
        public int lines() {
            return shown.length;
        }

        @Override
        public void write(int line, String text, boolean clear) {
            if (clear) {
                java.util.Arrays.fill(shown, null);
            }
            last = line > 0 ? line : last + 1;
            shown[last - 1] = text;
        }
    }

    // The desk rig with an Egress Port on a chest; the body runs once it's all online, with the fakes registered for
    // this system only (and taken away after).
    @SuppressWarnings("removal")
    private static void rig(GameTestHelper helper, Body body) {
        ElclGameTests.desk(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        FakePrinter printer = new FakePrinter();
        FakeDisplay display = new FakeDisplay();
        ElclSystem[] system = new ElclSystem[1];
        DeviceSources.Source<PrinterDevice> printers = s -> s.equals(system[0]) ? List.of(printer) : List.of();
        DeviceSources.Source<DisplayDevice> displays = s -> s.equals(system[0]) ? List.of(display) : List.of();
        TerminalContext[] context = new TerminalContext[1];
        GameTestSequence sequence = helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> Phase4GameTests.mount(helper, PORT_CABLE, Direction.WEST, PartType.EGRESS_PORT))
                .thenIdle(5)
                .thenExecute(() -> {
                    TerminalDeskBlockEntity desk = helper.getBlockEntity(ElclGameTests.DESK, TerminalDeskBlockEntity.class);
                    context[0] = new TerminalContext(helper.getLevel().getServer(), desk.network(), desk, player);
                    helper.assertTrue(context[0].network() != null, "Desk offline");
                    system[0] = new ElclSystem(context[0].server(), context[0].network());
                    ElclServices.jobs().interactive(system[0], context[0].user(), "vm", "ELDESK01");
                    NetworkStorage storage = RackGameTests.storage(helper, ElclGameTests.BAY);
                    storage.insert(StorageKey.of(new ItemStack(Items.COBBLESTONE)), 64, false);
                    storage.insert(StorageKey.of(new ItemStack(Items.IRON_INGOT)), 10, false);
                    Printers.register(printers);
                    Displays.register(displays);
                });
        body.accept(helper, sequence, new Rig(context, system, printer, display));
        sequence.thenExecute(() -> {
            Printers.unregister(printers);
            Displays.unregister(displays);
        }).thenSucceed();
    }

    private record Rig(TerminalContext[] contexts, ElclSystem[] systems, FakePrinter printer, FakeDisplay display) {
        TerminalContext context() {
            return contexts[0];
        }

        ElclSystem system() {
            return systems[0];
        }
    }

    @FunctionalInterface
    private interface Body {
        void accept(GameTestHelper helper, GameTestSequence sequence, Rig rig);
    }

    // Runs a command line; its output's lines and message as one text.
    private static String run(TerminalContext context, String line) {
        TerminalOutput out = TerminalCommands.execute(context, line);
        StringBuilder text = new StringBuilder();
        for (TerminalLine l : out.lines()) {
            text.append(l.text().trim()).append('\n');
        }
        if (out.message() != null) {
            text.append(out.message().getString());
        }
        return text.toString();
    }

    private static void expect(GameTestHelper helper, TerminalContext context, String line, String wanted) {
        String out = run(context, line);
        helper.assertTrue(out.contains(wanted), line + " -> " + out.replace('\n', '|') + ", wanted " + wanted);
    }

    // --- Mod commands on Command Entry ---

    static void modCommands(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence.thenExecute(() -> {
            TerminalContext c = rig.context();
            expect(h, c, "RTVITMCNT COBBLESTONE", "RTNCOUNT = 64");
            expect(h, c, "RTVITMCNT DIAMOND", "ELC1201");
            expect(h, c, "RTVITMCNT DIAMOND NOTFND(*ZERO)", "RTNCOUNT = 0");
            expect(h, c, "RTVITMCNT 'minecraft:iron_ingot' TIER(*HOT)", "RTNCOUNT = 10");
            expect(h, c, "RTVITMLST FILTER('*ingot')", "IRON_INGOT");
            expect(h, c, "RTVITMLST SORT(*QTY) MAX(1)", "[COBBLESTONE]");
            expect(h, c, "RTVDEVLST TYPE(EGRESS)", "[EGRESS01]");
            expect(h, c, "RTVDEVSTS EGRESS01", "RTNSTS = *ONLINE");
            expect(h, c, "MOVITM COBBLESTONE 10 EGRESS01", "RTNMOVED = 10");
            int inChest = h.getBlockEntity(CHEST, ChestBlockEntity.class).countItem(Items.COBBLESTONE);
            h.assertTrue(inChest == 10, "Chest has " + inChest);
            expect(h, c, "MOVITM COBBLESTONE 999 EGRESS01 PARTIAL(*NO)", "ELC1202");
            expect(h, c, "IMPITM EGRESS01", "RTNMOVED = 10");
            expect(h, c, "RTVITMCNT COBBLESTONE", "RTNCOUNT = 64");
            expect(h, c, "MOVITM IRON_INGOT 3 *DESK", "RTNMOVED = 3");
            expect(h, c, "CHGDEVSTS EGRESS01 *DISABLE", "");
            expect(h, c, "RTVDEVSTS EGRESS01", "RTNSTS = *DISABLED");
            expect(h, c, "MOVITM COBBLESTONE 1 EGRESS01", "ELC1302");
            expect(h, c, "CHGDEVSTS EGRESS01 *ENABLE", "");
            expect(h, c, "CHGDEVSTS ELDESK01 *DISABLE", "ELC1303");
            expect(h, c, "CHGDEVFTR EGRESS01 *ADD ITEM(DIAMOND)", "");
            expect(h, c, "CHGDEVFTR EGRESS01 *RMV ITEM(DIAMOND)", "");
            expect(h, c, "CHGDEVFTR EGRESS01 *ADD", "ELC0102");
            expect(h, c, "RTVLANES", "RTNTOTAL");
            expect(h, c, "RTVPWRSTS", "RTNSRC = *NETWORK");
            expect(h, c, "RTVSTGSTS TIER(*HOT)", "RTNTOTAL");
            expect(h, c, "STRCRAFT IRON_BLOCK 1", "ELC1402");
            expect(h, c, "RTVCRFSTS C9999", "ELC1404");
            expect(h, c, "CHGITMTIER COBBLESTONE *PIN", "ELC1206");
            expect(h, c, "SNDDSPTXT NOCDSP01 'Hello' LINE(2)", "");
            h.assertTrue("Hello".equals(rig.display().shown[1]), "Display line 2: " + rig.display().shown[1]);
            expect(h, c, "SNDDSPTXT ELDESK01 'x'", "ELC1303");
            expect(h, c, "SNDDSPTXT NOWHERE01 'x'", "ELC1301");
            expect(h, c, "PRTRPT RPT(*INV)", "ELC1307");
            h.assertTrue(rig.printer().printed.stream().anyMatch(line -> line.startsWith("cobblestone")), "Inventory report " + rig.printer().printed);
            rig.printer().paper = false;
            expect(h, c, "PRTRPT RPT(*DEV)", "ELC1306");
            expect(h, c, "PRTRPT RPT(*DEV) DEV(PRT09)", "ELC1301");
        }));
    }

    // --- CALL in the interactive job ---

    static void member(ElclSystem system, String user, String library, String name, String... lines) {
        try {
            try {
                ElclServices.libraries().createLibrary(system, user, library, "*PROD", "");
            } catch (ElclException ignored) {}
            ElclServices.libraries().createMember(system, user, library, name, "");
            ElclServices.libraries().save(system, user, library, name, SourceLine.number(List.of(lines), 1));
            if (!ElclServices.libraries().compile(system, user, library, name, library, name).created()) {
                throw new IllegalStateException(name + " didn't compile");
            }
        } catch (ElclException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    static boolean said(ElclSystem system, String user, String text) {
        return ElclServices.messages().messages(system, user).stream().map(MessageService.Message::text).anyMatch(t -> t.contains(text));
    }

    private static boolean idle(Rig rig) {
        String job = net.zagdrath.encodedlogistics.elcl.exec.OsCommands.interactiveJob(rig.system(), rig.context().user()).number();
        return JobManager.of(rig.system().server()).run(rig.system(), job) == null;
    }

    static void interactiveCall(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence
                .thenExecute(() -> {
                    String user = rig.context().user();
                    member(rig.system(), user, "TST", "COUNT", "PGM PARM(&N)", "DCL VAR(&N) TYPE(*INT)", "DCL VAR(&C) TYPE(*INT)",
                            "RTVITMCNT ITEM(COBBLESTONE) RTNCOUNT(&C) NOTFND(*ZERO)", "DLYJOB DLY(1)",
                            "SNDMSG MSG('Count' *BCAT %CHAR(&C + &N)) TOUSR(*REQUESTER)", "ENDPGM");
                    member(rig.system(), user, "TST", "FOREVER", "PGM", "DOWHILE COND(*TRUE)", "ENDDO", "ENDPGM");
                    expect(h, rig.context(), "CALL PGM(TST/COUNT) PARM(5)", "ELC0108");
                    expect(h, rig.context(), "CALL PGM(TST/COUNT) PARM(5)", "ELC0109");
                })
                .thenWaitUntil(() -> h.assertTrue(said(rig.system(), rig.context().user(), "Count 69"), "COUNT not done"))
                .thenWaitUntil(() -> h.assertTrue(idle(rig), "Job still running"))
                .thenExecute(() -> {
                    String job = net.zagdrath.encodedlogistics.elcl.exec.OsCommands.interactiveJob(rig.system(), rig.context().user()).number();
                    try {
                        h.assertTrue(ElclServices.jobs().log(rig.system(), job).stream().anyMatch(e -> e.id().equals("ELC0110")), "No ELC0110 in the log");
                    } catch (ElclException e) {
                        h.fail(e.getMessage());
                    }
                    expect(h, rig.context(), "CALL TST/FOREVER", "ELC0108");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    String job = net.zagdrath.encodedlogistics.elcl.exec.OsCommands.interactiveJob(rig.system(), rig.context().user()).number();
                    JobManager.Run run = JobManager.of(rig.system().server()).run(rig.system(), job);
                    h.assertTrue(run != null && run.vm() != null && run.vm().executed() > 0, "FOREVER not running");
                    h.assertTrue(run.budgetUse() <= 100, "Over budget");
                    expect(h, rig.context(), "ENDJOB JOB(" + job + ") OPTION(*IMMED)", "ELC0311");
                })
                .thenWaitUntil(() -> h.assertTrue(idle(rig), "ENDJOB didn't end it")));
    }

    // --- The shipped examples ---

    static void examples(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence
                // RESTOCK: nothing can craft IRON_BLOCK here, so it tells the operator and returns.
                .thenExecute(() -> expect(h, rig.context(), "CALL ELSYS/RESTOCK PARM('IRON_BLOCK' 5)", "ELC0108"))
                .thenWaitUntil(() -> h.assertTrue(said(rig.system(), "*SYSOPR", "RESTOCK: cannot craft IRON_BLOCK"), "RESTOCK"))
                .thenWaitUntil(() -> h.assertTrue(idle(rig), "RESTOCK still running"))
                // SORTDEMO: its report goes to the printer.
                .thenExecute(() -> expect(h, rig.context(), "CALL ELSYS/SORTDEMO", "ELC0108"))
                .thenWaitUntil(() -> h.assertTrue(rig.printer().printed.stream().anyMatch(l -> l.contains("device(s) in fault")),
                        "SORTDEMO printed " + rig.printer().printed))
                .thenWaitUntil(() -> h.assertTrue(idle(rig), "SORTDEMO still running"))
                // UPSALERT: tells everyone (the operator among them); device errors ignored.
                .thenExecute(() -> expect(h, rig.context(), "CALL ELSYS/UPSALERT PARM('*PWRUPS' '50')", "ELC0108"))
                .thenWaitUntil(() -> h.assertTrue(said(rig.system(), "*SYSOPR", "Running on UPS"), "UPSALERT"))
                .thenWaitUntil(() -> h.assertTrue(idle(rig), "UPSALERT still running"))
                // ARCHIVE: no Tape Library, so it ends with its own escape, which reaches the user's queue.
                .thenExecute(() -> expect(h, rig.context(), "CALL ELSYS/ARCHIVE PARM('*_ORE')", "ELC0108"))
                .thenWaitUntil(() -> h.assertTrue(said(rig.system(), rig.context().user(), "ARCHIVE needs a Tape Library"), "ARCHIVE"))
                .thenWaitUntil(() -> h.assertTrue(idle(rig), "ARCHIVE still running"))
                // NOCWALL: three lines on the display, then it waits 30 seconds; ended.
                .thenExecute(() -> expect(h, rig.context(), "CALL ELSYS/NOCWALL", "ELC0108"))
                .thenWaitUntil(() -> h.assertTrue(rig.display().shown[2] != null && rig.display().shown[2].startsWith("POWER"),
                        "NOCWALL display " + java.util.Arrays.toString(rig.display().shown)))
                .thenExecute(() -> {
                    String job = net.zagdrath.encodedlogistics.elcl.exec.OsCommands.interactiveJob(rig.system(), rig.context().user()).number();
                    JobManager.Run run = JobManager.of(rig.system().server()).run(rig.system(), job);
                    h.assertTrue(run != null && run.vm() != null && run.vm().waiting() != null, "NOCWALL not waiting");
                    expect(h, rig.context(), "ENDJOB JOB(" + job + ")", "ELC0311");
                })
                .thenWaitUntil(() -> h.assertTrue(idle(rig), "NOCWALL not ended")));
    }
}
