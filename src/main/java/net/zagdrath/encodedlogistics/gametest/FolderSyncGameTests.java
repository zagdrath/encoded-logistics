/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.sync.FolderSync;

// Folder sync (Part 8): a member saved in game is written to <world>/encodedlogistics/libraries/<SYSNAME>/<LIB>/; a
// changed file comes back in, its unchanged lines keeping their sequence numbers; a new folder becomes a library; ELSYS
// isn't synced in; a file changed outside and then saved in game keeps the outside version as .bak; deleting a file
// keeps the member, deleting the member moves its file to .deleted/; off by default on a dedicated server.
final class FolderSyncGameTests {
    private FolderSyncGameTests() {}

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void roundTrip(GameTestHelper helper) {
        helper.assertTrue(!ElclConfig.folderSync(true) && ElclConfig.folderSync(false), "allowFolderSync's AUTO isn't off on dedicated servers only");
        FolderSync.setEnabled(true);
        ElclStoreGameTests.withDesk(helper, (c, system) -> {
            try {
                run(helper, c.user(), system);
            } catch (ElclException e) {
                helper.fail(e.getMessage());
            } finally {
                FolderSync.setEnabled(null);
            }
        });
    }

    private static void run(GameTestHelper helper, String user, ElclSystem system) throws ElclException {
        var libraries = ElclServices.libraries();
        libraries.createLibrary(system, user, "SYNC", "*PROD", "");
        libraries.createMember(system, user, "SYNC", "HELLO", "");
        libraries.save(system, user, "SYNC", "HELLO", SourceLine.number(List.of("PGM", "ENDPGM"), 1));
        Path hello = FolderSync.file(system, "SYNC", "HELLO");
        helper.assertTrue(Files.exists(hello) && read(hello).equals("PGM\nENDPGM\n"), "Member not written out");

        // Changed outside: back in, the unchanged lines keeping their numbers.
        write(hello, "PGM\r\n  DCL VAR(&A) TYPE(*INT)\r\nENDPGM\r\n");
        FolderSync.poll(system);
        List<SourceLine> lines = libraries.source(system, "SYNC", "HELLO");
        helper.assertTrue(lines.size() == 3 && lines.get(0).seq() == 100 && lines.get(1).seq() == 150 && lines.get(2).seq() == 200
                && lines.get(1).text().equals("  DCL VAR(&A) TYPE(*INT)"), "Not taken in as expected: " + lines);

        // A new folder: a library.
        write(FolderSync.folder(system).resolve("NEWLIB").resolve("NOTE.elclp"), "/* NOTE - from outside */\nPGM\nENDPGM\n");
        write(FolderSync.folder(system).resolve("ELSYS").resolve("SNEAKY.elclp"), "PGM\nENDPGM\n");
        FolderSync.poll(system);
        helper.assertTrue(libraries.member(system, "NEWLIB", "NOTE").lines() == 3, "New folder's member not taken in");
        helper.assertTrue(libraries.members(system, "ELSYS").size() == 8, "ELSYS synced in");

        // Changed outside, then saved in game before it was taken in: the game's save wins, the file's version kept.
        write(hello, "SOMETHING ELSE\n");
        libraries.save(system, user, "SYNC", "HELLO", SourceLine.number(List.of("PGM", "  SNDMSG MSG('hi')", "ENDPGM"), 2));
        helper.assertTrue(read(hello).equals("PGM\n  SNDMSG MSG('hi')\nENDPGM\n"), "The later write didn't win: " + read(hello));
        Path bak = hello.resolveSibling("HELLO.elclp.bak");
        helper.assertTrue(Files.exists(bak) && read(bak).equals("SOMETHING ELSE\n"), "No .bak of the losing version");

        // Deleting the file keeps the member; deleting the member moves its file to .deleted/.
        try {
            Files.delete(hello);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        FolderSync.poll(system);
        helper.assertTrue(libraries.member(system, "SYNC", "HELLO").lines() == 3, "Deleting the file deleted the member");
        libraries.save(system, user, "SYNC", "HELLO", SourceLine.number(List.of("PGM", "ENDPGM"), 3));
        libraries.deleteMember(system, user, "SYNC", "HELLO");
        helper.assertTrue(!Files.exists(hello) && Files.exists(hello.getParent().resolve(".deleted").resolve("HELLO.elclp")), "File not moved to .deleted");
    }
}
