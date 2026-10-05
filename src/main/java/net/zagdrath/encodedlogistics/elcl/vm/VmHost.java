/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.vm;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.Wait;
import net.zagdrath.encodedlogistics.elcl.db.FileAccess;

// What the VM needs from the job running it: programs to CALL, its waits, the game (item names, its context for
// commands, the clock), the job's authority, and where its messages go (job log, the user's message queue).
public interface VmHost {
    // A program to run: its key (LIB/NAME) and source (compiled again from it, so a saved job runs what it ran), and the
    // record formats of the files it declares, as it was compiled with them (by "LIB/FILE" as written; RecordFormat.save).
    record Loaded(String key, List<String> source, Map<String, String> files) {
        public Loaded(String key, List<String> source) {
            this(key, source, Map.of());
        }
    }

    // CALL PGM(lib/name) (library *LIBL: the user's library list): ELC0203 / ELC0201 when there's none.
    Loaded program(String library, String name) throws ElclException;

    boolean waitDone(Wait wait);

    String itemName(String item);

    boolean interactive();

    <T> @Nullable T context(Class<T> type);

    // ELC0401 when the job's user lacks the permission (null: allowed).
    @Nullable ElclMessage authorise(CommandDefinition.Auth auth);

    // The job logs the commands it runs (LOG(*YES)).
    default boolean logsCommands() {
        return false;
    }

    void logCommand(String command);

    // A message for the job log (and, from a command or SNDPGMMSG *INFO, what an interactive caller sees).
    void message(ElclMessage message);

    // An escape no monitor took, ending a program: the job log.
    void escaped(String program, ElclMessage message);

    // The job's program ended abnormally (the last frame): the user's message queue.
    void failed(ElclMessage message);

    // The overworld's game time (ticks, never going back) and its clock (time of day, ticks).
    long gameTime();

    long dayTime();

    default int maxList() {
        return 4_096;
    }

    default int maxCallDepth() {
        return 16;
    }

    // The job's files (RCVF, WRTRCD...), or null where there are none to be had (ELC0107).
    default @Nullable FileAccess files() {
        return null;
    }
}
