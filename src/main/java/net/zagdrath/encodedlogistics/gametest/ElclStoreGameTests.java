/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.LibraryService;
import net.zagdrath.encodedlogistics.elcl.screen.MessageService;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;

// elcl.store (Part 1): what a system keeps survives a save and reload; members take network storage (ELC0207 when
// it's full); message queues and spooled files keep to their caps, oldest out first; library authority.
final class ElclStoreGameTests {
    private ElclStoreGameTests() {}

    // Runs the body a few ticks in, once the desk is online, with its context and system.
    @SuppressWarnings("removal")
    static void withDesk(GameTestHelper helper, BiConsumer<TerminalContext, ElclSystem> body) {
        ElclGameTests.desk(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    TerminalDeskBlockEntity desk = helper.getBlockEntity(ElclGameTests.DESK, TerminalDeskBlockEntity.class);
                    TerminalContext context = new TerminalContext(helper.getLevel().getServer(), desk.network(), desk, player);
                    helper.assertTrue(context.network() != null, "Desk offline");
                    body.accept(context, new ElclSystem(context.server(), context.network()));
                })
                .thenSucceed();
    }

    static void persistence(GameTestHelper helper) {
        withDesk(helper, (context, system) -> {
            String user = context.user();
            ElclServices.jobs().interactive(system, user, "store", "ELDESK01");
            ElclGameTests.expect(helper, context, "CRTLIB LIB(KEEP) TEXT('Kept')", "ELC0210");
            ElclGameTests.expect(helper, context, "CHGLIB LIB(KEEP) AUT(*CHANGE)", "ELC0212");
            ElclGameTests.expect(helper, context, "CPYMBR FROM(ELSYS/RESTOCK) TO(KEEP/RESTOCK)", "ELC0215");
            ElclGameTests.expect(helper, context, "CRTELPGM PGM(KEEP/RESTOCK)", "ELC0218");
            ElclGameTests.expect(helper, context, "CHGSYSVAL SYSVAL(LOGRTN) VALUE(40)", "ELC0222");
            ElclGameTests.expect(helper, context, "SNDMSG MSG('Survives') TOUSR(*REQUESTER)", "");
            ElclGameTests.expect(helper, context, "PRTTXT TEXT('Printed line') SPLF(KEPT)", "");
            List<SourceLine> edited;
            try {
                // A changed line on day 7 (its own change date), so the member is "changed since compile".
                List<SourceLine> lines = new java.util.ArrayList<>(ElclServices.libraries().source(system, "KEEP", "RESTOCK"));
                lines.set(0, new SourceLine(lines.getFirst().seq(), "/* RESTOCK - edited */", 7));
                lines.add(new SourceLine(2_050, "/* inserted */", 7));
                lines.sort(java.util.Comparator.comparingInt(SourceLine::seq));
                ElclServices.libraries().save(system, user, "KEEP", "RESTOCK", lines);
                edited = List.copyOf(lines);
            } catch (ElclException e) {
                helper.fail("Save: " + e.getMessage());
                return;
            }

            ElclStore.get(context.server()).reload(context.network());

            try {
                LibraryService.Library library = ElclServices.libraries().library(system, "KEEP");
                helper.assertTrue(library.authority().equals("*CHANGE") && library.text().equals("Kept") && library.owner().equals(user), "Library " + library);
                helper.assertTrue(ElclServices.libraries().source(system, "KEEP", "RESTOCK").equals(edited), "Member lines, sequence numbers or dates lost");
                LibraryService.Member member = ElclServices.libraries().member(system, "KEEP", "RESTOCK");
                helper.assertTrue(member.program() && member.changed(), "Program link or changed-since-compile lost: " + member);
                helper.assertTrue(ElclServices.libraries().program(system, "KEEP", "RESTOCK") != null, "Program not runnable");
                helper.assertTrue(ElclServices.libraries().members(system, "ELSYS").size() == 5, "ELSYS not rebuilt");
                helper.assertTrue(ElclServices.libraries().program(system, "ELSYS", "NOCWALL") != null, "ELSYS program missing");
            } catch (ElclException e) {
                helper.fail("After reload: " + e.getMessage());
            }
            ElclGameTests.expect(helper, context, "DLTMBR MBR(ELSYS/RESTOCK)", "ELC0205");
            helper.assertTrue(ElclServices.sysvals().get(system, "LOGRTN").equals("40"), "System value lost");
            List<MessageService.Message> messages = ElclServices.messages().messages(system, user);
            helper.assertTrue(messages.size() == 1 && messages.getFirst().text().equals("Survives") && messages.getFirst().unread(), "Message lost");
            List<SpoolService.SpooledFile> files = ElclServices.spool().files(system, user, null);
            helper.assertTrue(files.stream().anyMatch(file -> file.name().equals("KEPT") && file.lines().equals(List.of("Printed line"))), "Spooled file lost");
            helper.assertTrue(files.stream().anyMatch(file -> file.name().equals("RESTOCK")), "Compile listing lost");
        });
    }

    static void storageFull(GameTestHelper helper) {
        withDesk(helper, (context, system) -> {
            ElclGameTests.expect(helper, context, "CRTLIB LIB(FULL)", "ELC0210");
            ElclGameTests.expect(helper, context, "CRTMBR MBR(FULL/EMPTY)", "ELC0214");
            // Fill the drive.
            RackGameTests.storage(helper, ElclGameTests.BAY).insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), Long.MAX_VALUE / 4, false);
            ElclGameTests.expect(helper, context, "CPYMBR FROM(ELSYS/RESTOCK) TO(FULL/RESTOCK)", "ELC0207");
            try {
                ElclServices.libraries().save(system, context.user(), "FULL", "EMPTY", SourceLine.number(List.of("PGM", "ENDPGM"), 1));
                helper.fail("Saved with storage full");
            } catch (ElclException e) {
                helper.assertTrue(e.elclMessage().id().equals("ELC0207"), "Wanted ELC0207, got " + e.getMessage());
            }
            // Room again: the save goes through.
            RackGameTests.storage(helper, ElclGameTests.BAY).extract(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 4_096, false);
            try {
                ElclServices.libraries().save(system, context.user(), "FULL", "EMPTY", SourceLine.number(List.of("PGM", "ENDPGM"), 1));
            } catch (ElclException e) {
                helper.fail("Save with room: " + e.getMessage());
            }
        });
    }

    static void retention(GameTestHelper helper) {
        withDesk(helper, (context, system) -> {
            int messages = ElclConfig.messageCap(), files = ElclConfig.spooledFileCap();
            for (int i = 0; i <= messages; i++) {
                ElclServices.messages().send(system, "QSYS", "CAPPED", "", 0, "Message " + i);
            }
            List<MessageService.Message> queue = ElclServices.messages().messages(system, "CAPPED");
            helper.assertTrue(queue.size() == messages, "Queue " + queue.size());
            helper.assertTrue(queue.getFirst().text().equals("Message " + messages) && queue.getLast().text().equals("Message 1"), "Oldest not removed first");
            for (int i = 0; i <= files; i++) {
                ElclServices.spool().create(system, "F" + i, "000001", "CAP", "CAPPED", List.of("x"));
            }
            List<SpoolService.SpooledFile> spooled = ElclServices.spool().files(system, null, null);
            helper.assertTrue(spooled.size() == files, "Spooled " + spooled.size());
            helper.assertTrue(spooled.getLast().name().equals("F1"), "Oldest spooled file not removed first: " + spooled.getLast().name());
        });
    }

    static void libraryAuthority(GameTestHelper helper) {
        withDesk(helper, (context, system) -> {
            LibraryService libraries = ElclServices.libraries();
            try {
                libraries.createLibrary(system, "OWNER", "OWNED", "*PROD", "");
                libraries.createMember(system, "OWNER", "OWNED", "MINE", "");
            } catch (ElclException e) {
                helper.fail("Owner: " + e.getMessage());
            }
            try {
                libraries.createMember(system, "OTHER", "OWNED", "THEIRS", "");
                helper.fail("Another user changed a *USE library");
            } catch (ElclException e) {
                helper.assertTrue(e.elclMessage().id().equals("ELC0401"), "Wanted ELC0401, got " + e.getMessage());
            }
            try {
                libraries.source(system, "OWNED", "MINE");
                libraries.changeLibrary(system, "OWNER", "OWNED", null, "*CHANGE");
                libraries.createMember(system, "OTHER", "OWNED", "THEIRS", "");
            } catch (ElclException e) {
                helper.fail("*CHANGE: " + e.getMessage());
            }
            try {
                libraries.changeLibrary(system, "OTHER", "OWNED", null, "*USE");
                helper.fail("Another user changed the library's authority");
            } catch (ElclException e) {
                helper.assertTrue(e.elclMessage().id().equals("ELC0401"), "Wanted ELC0401, got " + e.getMessage());
            }
        });
    }
}
