/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.job.BatchContext;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.UserService;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// Security and sign-on (Part 6): with a Firewall (no access by default; GUESTP may view and insert), commands fail
// with ELC0401 for users without their Auth permission (at a terminal, and in a batch job as its submitter); the
// network's owner is *SECOFR (system values, managing others' entries); SECLVL 10 drops the sign-on and the Firewall's
// authority in the OS. Sign-on: ELC0402 for a name not the player's, profiles made at first sign-on with ELGPL ELSYS,
// a current library that must exist and comes first on the list, kept across a reload; until it signs on a session gets
// nothing but its info and the sign-on.
final class SecurityGameTests {
    private SecurityGameTests() {}

    private record People(ServerPlayer owner, ServerPlayer guest, ServerPlayer stranger) {}

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
    }

    // The desk rig and a Firewall owned by OWNERP: no access by default, GUESTP may view and insert.
    private static People rig(GameTestHelper helper) {
        ElclGameTests.desk(helper);
        People people = new People(player(helper, "OWNERP"), player(helper, "GUESTP"), player(helper, "STRANGER"));
        BlockPos master = RackGeometry.masterPos(new BlockPos(3, 1, 0), Direction.NORTH, RackGeometry.BOTTOM_FRONT);
        FirewallDevice firewall = (FirewallDevice) RackDeviceType.FIREWALL.create();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        output.store("owner", UUIDUtil.CODEC, people.owner().getUUID());
        output.putString("owner_name", "OWNERP");
        output.putInt("policy", FirewallDevice.Policy.DENY.ordinal());
        ValueOutput entry = output.childrenList("players").addChild();
        entry.store("id", UUIDUtil.CODEC, people.guest().getUUID());
        entry.putString("name", "GUESTP");
        entry.putIntArray("permissions", new int[] { FirewallDevice.ON, FirewallDevice.ON, FirewallDevice.INHERIT, FirewallDevice.INHERIT,
                FirewallDevice.INHERIT });
        firewall.loadSettings(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
        helper.getBlockEntity(master, RackBlockEntity.class).install(firewall, 9, null);
        return people;
    }

    private static TerminalContext at(GameTestHelper helper, ServerPlayer player) {
        TerminalDeskBlockEntity desk = helper.getBlockEntity(ElclGameTests.DESK, TerminalDeskBlockEntity.class);
        return new TerminalContext(helper.getLevel().getServer(), desk.network(), desk, player);
    }

    private static void expect(GameTestHelper helper, TerminalContext context, String line, String wanted) {
        BatchJobGameTests.expect(helper, context, line, wanted);
    }

    static void authority(GameTestHelper helper) {
        People people = rig(helper);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    TerminalContext owner = at(helper, people.owner()), guest = at(helper, people.guest()), stranger = at(helper, people.stranger());
                    ElclSystem system = new ElclSystem(owner.server(), owner.network());
                    helper.assertTrue(ElclServices.users().securityOfficer(system, "OWNERP"), "The owner isn't *SECOFR");
                    helper.assertTrue(ElclServices.users().profile(system, "OWNERP", people.owner().getUUID()).userClass().equals(UserService.SECOFR),
                            "Profile class");
                    helper.assertFalse(ElclServices.users().securityOfficer(system, "GUESTP"), "The guest is *SECOFR");
                    // View: the guest may; extract and configure: no; the stranger: nothing.
                    expect(helper, guest, "RTVITMCNT DIAMOND NOTFND(*ZERO)", "");
                    helper.assertTrue(BatchJobGameTests.run(guest, "RTVITMCNT DIAMOND NOTFND(*ZERO)").isEmpty(), "Guest can't view");
                    expect(helper, guest, "MOVITM DIAMOND 1 *DESK", "ELC0401");
                    expect(helper, guest, "CHGDEVSTS ELDESK01 *DISABLE", "ELC0401");
                    expect(helper, stranger, "RTVITMCNT DIAMOND NOTFND(*ZERO)", "ELC0401");
                    String screen = TerminalService.handle(stranger, TerminalService.SCREEN, "libraries").message().getString();
                    helper.assertTrue(screen.startsWith("ELC0401"), "Screen for a stranger: " + screen);
                    // System values: *SECOFR only.
                    expect(helper, guest, "CHGSYSVAL SYSVAL(LOGRTN) VALUE(40)", "ELC0401");
                    expect(helper, owner, "CHGSYSVAL SYSVAL(LOGRTN) VALUE(40)", "ELC0222");
                    // Others' schedule entries: theirs or *SECOFR.
                    expect(helper, guest, "ADDJOBSCDE JOB(GUESTJOB) CMD(SNDMSG MSG('x')) FRQ(*DAILY) TIME(0300)", "ELC0312");
                    expect(helper, stranger, "RMVJOBSCDE JOB(GUESTJOB)", "ELC0401");
                    expect(helper, owner, "HLDJOBSCDE JOB(GUESTJOB)", "");
                    expect(helper, guest, "RMVJOBSCDE JOB(GUESTJOB)", "ELC0313");
                    // A batch job has its submitter's permissions, online or not.
                    BatchContext batch = new BatchContext(owner.server(), owner.network(), "GUESTP", people.guest().getUUID(), "000001");
                    helper.assertTrue(batch.allowed(RackPermission.VIEW) && !batch.allowed(RackPermission.EXTRACT), "Batch job authority");
                    helper.assertFalse(new BatchContext(owner.server(), owner.network(), "X", null, "000001").allowed(RackPermission.VIEW),
                            "A job with no submitter has the default policy");
                    // SECLVL 10: no sign-on, and the Firewall isn't asked in the OS.
                    expect(helper, owner, "CHGSYSVAL SYSVAL(SECLVL) VALUE(10)", "ELC0222");
                    helper.assertFalse(ElclServices.users().signOnRequired(system), "Sign-on still needed at SECLVL 10");
                    expect(helper, stranger, "RTVITMCNT DIAMOND NOTFND(*ZERO)", "");
                    helper.assertTrue(BatchJobGameTests.run(stranger, "RTVITMCNT DIAMOND NOTFND(*ZERO)").isEmpty(), "Stranger refused at SECLVL 10");
                    expect(helper, stranger, "CHGSYSVAL SYSVAL(SECLVL) VALUE(30)", "ELC0401");
                    expect(helper, owner, "CHGSYSVAL SYSVAL(SECLVL) VALUE(30)", "ELC0222");
                    expect(helper, stranger, "RTVITMCNT DIAMOND NOTFND(*ZERO)", "ELC0401");
                })
                .thenSucceed();
    }

    static void signOn(GameTestHelper helper) {
        People people = rig(helper);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    TerminalContext guest = at(helper, people.guest());
                    ElclSystem system = new ElclSystem(guest.server(), guest.network());
                    UserService users = ElclServices.users();
                    helper.assertTrue(users.signOnRequired(system), "No sign-on with a Firewall at SECLVL 30");
                    // Until it signs on, a session gets its info and the sign-on only.
                    helper.assertTrue(TerminalDeskMenu.refused(system, false, TerminalService.COMMAND, "WRKLIB"), "A command before sign-on");
                    helper.assertTrue(TerminalDeskMenu.refused(system, false, TerminalService.SCREEN, "libraries"), "A screen before sign-on");
                    helper.assertFalse(TerminalDeskMenu.refused(system, false, TerminalService.QUERY, "info"), "Info refused");
                    helper.assertFalse(TerminalDeskMenu.refused(system, false, TerminalService.SCREEN, "signon GUESTP"), "Sign-on refused");
                    helper.assertFalse(TerminalDeskMenu.refused(system, true, TerminalService.COMMAND, "WRKLIB"), "Refused after sign-on");
                    try {
                        users.signOn(system, "GUESTP", people.guest().getUUID(), "OWNERP", "*USRPRF");
                        helper.fail("Signed on as someone else");
                    } catch (ElclException e) {
                        helper.assertTrue(e.elclMessage().id().equals("ELC0402"), "Wanted ELC0402, got " + e.getMessage());
                    }
                    try {
                        users.signOn(system, "GUESTP", people.guest().getUUID(), "guestp", "NOLIB");
                        helper.fail("Signed on with a library that isn't there");
                    } catch (ElclException e) {
                        helper.assertTrue(e.elclMessage().id().equals("ELC0201"), "Wanted ELC0201, got " + e.getMessage());
                    }
                    try {
                        UserService.Profile first = users.signOn(system, "GUESTP", people.guest().getUUID(), "guestp", "*USRPRF");
                        helper.assertTrue(first.libraryList().equals(List.of("ELGPL", "ELSYS")) && first.currentLibrary().equals("ELGPL"),
                                "First profile " + first);
                        ElclServices.libraries().createLibrary(system, "GUESTP", "GUESTLIB", "*PROD", "");
                        UserService.Profile second = users.signOn(system, "GUESTP", people.guest().getUUID(), "GUESTP", "GUESTLIB");
                        helper.assertTrue(second.libraryList().getFirst().equals("GUESTLIB") && second.currentLibrary().equals("GUESTLIB"),
                                "Current library " + second);
                    } catch (ElclException e) {
                        helper.fail("Sign-on: " + e.getMessage());
                    }
                    // A bare member name goes to the current library.
                    expect(helper, guest, "CRTMBR MBR(NOTES)", "ELC0214");
                    try {
                        ElclServices.libraries().member(system, "GUESTLIB", "NOTES");
                    } catch (ElclException e) {
                        helper.fail("Not in the current library: " + e.getMessage());
                    }
                    ElclStore.get(system.server()).reload(system.network());
                    UserService.Profile kept = users.profile(system, "GUESTP", null);
                    helper.assertTrue(kept.currentLibrary().equals("GUESTLIB") && kept.player() != null, "Profile lost in a reload: " + kept);
                })
                .thenSucceed();
    }
}
