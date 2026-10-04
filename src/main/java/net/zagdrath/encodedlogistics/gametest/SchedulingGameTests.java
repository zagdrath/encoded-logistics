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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.JobData;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Job schedule entries and trigger events (Part 5): *ONCE (and gone after), *INTERVAL on real seconds, *DAILY on the
// game clock, held entries wait, ELC0301 to the user with no host; triggers run their program with &EVENT and &DATA,
// once per crossing (*ITMBELOW, *STGFULL, *PWRUPS / *PWRRESTORED, device status), debounced (a second between two
// firings), from events (*RSCHANGE, *CRAFTEND), not while held; and they survive a reload.
final class SchedulingGameTests {
    private static final BlockPos PORT_CABLE = new BlockPos(1, 1, 1);

    private SchedulingGameTests() {}

    private static List<BatchJobGameTests.FakeHost> host() {
        return new ArrayList<>(List.of(new BatchJobGameTests.FakeHost("INTEG01", 4, false)));
    }

    // TST/NOTE: says "&EVENT &DATA" to its user.
    private static void note(BatchJobGameTests.Rig rig) {
        BatchJobGameTests.program(rig.system(), rig.user(), "NOTE", "PGM PARM(&EVENT &DATA)", "DCL VAR(&EVENT) TYPE(*CHAR) LEN(10)",
                "DCL VAR(&DATA) TYPE(*CHAR) LEN(256)", "SNDMSG MSG(&EVENT *BCAT %TRIM(&DATA)) TOUSR(*REQUESTER)", "ENDPGM");
    }

    private static long said(BatchJobGameTests.Rig rig, String start) {
        return ElclServices.messages().messages(rig.system(), rig.user()).stream().filter(m -> m.text().startsWith(start)).count();
    }

    private static void expect(GameTestHelper h, BatchJobGameTests.Rig rig, String line, String wanted) {
        BatchJobGameTests.expect(h, rig.context(), line, wanted);
    }

    private static NetworkStorage storage(GameTestHelper h) {
        return RackGameTests.storage(h, ElclGameTests.BAY);
    }

    // --- Schedule entries ---

    static void schedules(GameTestHelper helper) {
        BatchJobGameTests.rig(helper, host(), (h, sequence, rig) -> sequence
                .thenExecute(() -> {
                    expect(h, rig, "ADDJOBSCDE JOB(ONCE) CMD(SNDMSG MSG('once') TOUSR(*REQUESTER)) FRQ(*ONCE)", "ELC0312");
                    expect(h, rig, "ADDJOBSCDE JOB(EVERY) CMD(SNDMSG MSG('every') TOUSR(*REQUESTER)) FRQ(*INTERVAL) INTERVAL(1)", "ELC0312");
                    // A minute or so of game time from now (a game minute is under 17 ticks).
                    String at = ElclSystem.clock(rig.system().ticks() + 60, false);
                    String hhmm = at.substring(at.length() - 5).replace(":", "");
                    expect(h, rig, "ADDJOBSCDE JOB(DAILY) CMD(SNDMSG MSG('daily') TOUSR(*REQUESTER)) FRQ(*DAILY) TIME(" + hhmm + ")", "ELC0312");
                })
                .thenWaitUntil(() -> h.assertTrue(said(rig, "once") == 1 && !ElclStore.of(rig.system()).jobs.schedules.containsKey("ONCE"),
                        "*ONCE didn't run once and go"))
                .thenWaitUntil(() -> h.assertTrue(said(rig, "every") >= 2, "*INTERVAL ran " + said(rig, "every") + " times"))
                .thenWaitUntil(() -> h.assertTrue(said(rig, "daily") == 1, "*DAILY didn't run"))
                .thenExecute(() -> {
                    JobData.Schedule daily = ElclStore.of(rig.system()).jobs.schedules.get("DAILY");
                    h.assertTrue(daily != null && daily.due > rig.system().ticks() + 20_000, "*DAILY not planned for tomorrow");
                    expect(h, rig, "HLDJOBSCDE JOB(EVERY)", "");
                })
                .thenIdle(5)
                .thenExecute(() -> rig.hosts().clear())
                .thenIdle(50)
                .thenExecute(() -> {
                    long every = said(rig, "every");
                    h.assertTrue(ElclServices.jobs().scheduleEntries(rig.system()).stream().anyMatch(e -> e.job().equals("EVERY") && e.status().equals("*HLD")),
                            "Not held");
                    // No job host now: the entry's run is reported to its user.
                    expect(h, rig, "ADDJOBSCDE JOB(NOHOST) CMD(SNDMSG MSG('x')) FRQ(*ONCE)", "ELC0312");
                    h.assertTrue(every == said(rig, "every"), "Held entry ran");
                })
                .thenWaitUntil(() -> h.assertTrue(ElclServices.messages().messages(rig.system(), rig.user()).stream()
                        .anyMatch(m -> m.msgId().equals("ELC0301") && m.text().startsWith("Schedule entry NOHOST")), "ELC0301 not reported")));
    }

    // --- Triggers: an item count crossing, edge and debounce, held, reload ---

    static void itemTrigger(GameTestHelper helper) {
        ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
        BatchJobGameTests.rig(helper, host(), (h, sequence, rig) -> sequence
                .thenExecute(() -> {
                    note(rig);
                    storage(h).insert(cobble, 64, false);
                    expect(h, rig, "ADDTRGEVT TRG(LOW) EVENT(*ITMBELOW) PGM(TST/NOTE) ITEM(COBBLESTONE) VALUE(10)", "ELC0314");
                })
                .thenIdle(15)
                .thenExecute(() -> storage(h).extract(cobble, 60, false))
                .thenWaitUntil(() -> h.assertTrue(said(rig, "*ITMBELOW COBBLESTONE 4") == 1, "Didn't fire below 10"))
                // Still below: no second firing.
                .thenExecute(() -> storage(h).extract(cobble, 1, false))
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(said(rig, "*ITMBELOW") == 1, "Fired again without a crossing");
                    storage(h).insert(cobble, 20, false);
                })
                .thenIdle(15)
                .thenExecute(() -> storage(h).extract(cobble, 21, false))
                .thenWaitUntil(() -> h.assertTrue(said(rig, "*ITMBELOW COBBLESTONE 2") == 1, "Didn't fire on the second crossing"))
                .thenExecute(() -> {
                    // Held: up and down again, nothing.
                    expect(h, rig, "HLDTRGEVT TRG(LOW)", "");
                    storage(h).insert(cobble, 50, false);
                })
                .thenIdle(15)
                .thenExecute(() -> storage(h).extract(cobble, 50, false))
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(said(rig, "*ITMBELOW") == 2, "A held trigger fired");
                    ElclStore.get(rig.system().server()).reload(rig.system().network());
                    JobData.Trigger low = ElclStore.of(rig.system()).jobs.triggers.get("LOW");
                    h.assertTrue(low != null && low.trigger.status().equals("*HELD") && low.primed && low.player != null, "Trigger lost in a reload");
                }));
    }

    // --- Triggers from events: redstone (debounced), an ended craft, a device's status ---

    static void eventTriggers(GameTestHelper helper) {
        BatchJobGameTests.rig(helper, host(), (h, sequence, rig) -> sequence
                .thenExecute(() -> Phase4GameTests.mount(h, PORT_CABLE, Direction.WEST, PartType.EGRESS_PORT))
                .thenExecute(() -> {
                    note(rig);
                    expect(h, rig, "ADDTRGEVT TRG(RS) EVENT(*RSCHANGE) PGM(TST/NOTE) DEV(*ANY)", "ELC0314");
                    expect(h, rig, "ADDTRGEVT TRG(CRAFT) EVENT(*CRAFTEND) PGM(TST/NOTE) ITEM(IRON_BLOCK)", "ELC0314");
                    expect(h, rig, "ADDTRGEVT TRG(OFF) EVENT(*DEVOFFLINE) PGM(TST/NOTE) DEV(EGRESS01)", "ELC0314");
                    expect(h, rig, "ADDTRGEVT TRG(ON) EVENT(*DEVONLINE) PGM(TST/NOTE) DEV(EGRESS01)", "ELC0314");
                    // Twice in a moment: one firing (the debounce).
                    ElclEvents.redstoneChanged(rig.system().server(), rig.system().network(), "CTLIF01", Direction.NORTH, 7);
                    ElclEvents.redstoneChanged(rig.system().server(), rig.system().network(), "CTLIF01", Direction.NORTH, 8);
                    ElclEvents.fire(rig.system().server(), new ElclEvents.Event(rig.system().network(), "*CRAFTEND", "", "IRON_BLOCK", "C0001 *DONE"));
                    ElclEvents.fire(rig.system().server(), new ElclEvents.Event(rig.system().network(), "*CRAFTEND", "", "GOLD_BLOCK", "C0002 *DONE"));
                })
                .thenWaitUntil(() -> h.assertTrue(said(rig, "*RSCHANGE *NORTH 7") == 1 && said(rig, "*CRAFTEND C0001 *DONE") == 1, "Events didn't fire"))
                .thenIdle(25)
                .thenExecute(() -> {
                    h.assertTrue(said(rig, "*RSCHANGE") == 1 && said(rig, "*CRAFTEND") == 1, "Debounce or ITEM() let more through");
                    ElclEvents.redstoneChanged(rig.system().server(), rig.system().network(), "CTLIF01", Direction.NORTH, 9);
                    expect(h, rig, "CHGDEVSTS EGRESS01 *DISABLE", "");
                })
                .thenWaitUntil(() -> h.assertTrue(said(rig, "*RSCHANGE *NORTH 9") == 1 && said(rig, "*DEVOFFLIN EGRESS01") == 1,
                        "Second redstone change or *DEVOFFLINE missing"))
                .thenExecute(() -> expect(h, rig, "CHGDEVSTS EGRESS01 *ENABLE", ""))
                .thenWaitUntil(() -> h.assertTrue(said(rig, "*DEVONLINE EGRESS01") == 1, "*DEVONLINE missing")));
    }

    // --- Triggers on power: onto UPS and back ---

    static void powerTriggers(GameTestHelper helper) {
        BlockPos master = RackGeometry.masterPos(new BlockPos(3, 1, 0), Direction.NORTH, RackGeometry.BOTTOM_FRONT);
        BatchJobGameTests.rig(helper, host(), (h, sequence, rig) -> sequence
                .thenExecute(() -> {
                    note(rig);
                    expect(h, rig, "ADDTRGEVT TRG(UPS) EVENT(*PWRUPS) PGM(TST/NOTE)", "ELC0314");
                    expect(h, rig, "ADDTRGEVT TRG(MAINS) EVENT(*PWRRESTORED) PGM(TST/NOTE)", "ELC0314");
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    UpsDevice ups = RackGameTests.install(h, master, RackDeviceType.UPS, 1, UpsDevice.class);
                    ups.loadSettings(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), RackGameTests.upsState(h, 500_000)));
                })
                .thenWaitUntil(() -> h.assertTrue(said(rig, "*PWRUPS") == 1, "*PWRUPS didn't fire"))
                // Mains back (and kept on) long enough for the half-second look to see it.
                .thenExecuteFor(40, () -> RackGameTests.insert(h, RackGameTests.CONTROLLER, 4_096))
                .thenWaitUntil(() -> h.assertTrue(said(rig, "*PWRRESTOR") >= 1, "*PWRRESTORED didn't fire")));
    }

    // --- Storage fullness ---

    static void storageTrigger(GameTestHelper helper) {
        BatchJobGameTests.rig(helper, host(), (h, sequence, rig) -> sequence
                .thenExecute(() -> {
                    note(rig);
                    expect(h, rig, "ADDTRGEVT TRG(FULL) EVENT(*STGFULL) PGM(TST/NOTE) VALUE(5)", "ELC0314");
                })
                .thenIdle(15)
                .thenExecute(() -> storage(h).insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 10_000, false))
                .thenWaitUntil(() -> h.assertTrue(said(rig, "*STGFULL") == 1, "*STGFULL didn't fire")));
    }
}
