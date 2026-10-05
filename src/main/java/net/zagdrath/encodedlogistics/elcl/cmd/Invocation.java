/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.cmd;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;

// One run of a command, as its executor sees it: the arguments (evaluated, defaults filled in, special values as
// "*NAME" in upper case), where its RTN* values go, where its messages go, and the game-side context it runs in.
public interface Invocation {
    CommandDefinition command();

    // The source line (from 1) of the statement in a program, or 0 on a command line.
    default int line() {
        return 0;
    }

    // Whether the parameter was given (not just defaulted).
    boolean given(String keyword);

    // The value as text ("" when not given and no default): names and special values upper-cased, text as typed. A
    // value that can't be worked out is its escape message (ELC0004 from %SST, ELC0005...).
    String text(String keyword) throws ElclException;

    // A whole number; text that isn't one is ELC0003, outside the schema's range ELC0004.
    long integer(String keyword) throws ElclException;

    // Every value of a list parameter.
    List<String> list(String keyword) throws ElclException;

    // Sets a RTN* parameter's variable (ignored when it wasn't given).
    void returns(String keyword, Object value);

    // An informational or completion message: Command Entry shows it, the job log keeps it.
    void send(ElclMessage message);

    boolean interactive();

    // The game side of the run (a TerminalContext on the server), or null when it isn't that type.
    <T> @Nullable T context(Class<T> type);

    // An async command waits: its job goes to *WAIT until the wait is done, then the command runs again (resumed()
    // gives the wait). On a command line, where nothing can wait, it ends there (canWait() is false).
    default void await(Wait wait) {}

    default boolean canWait() {
        return false;
    }

    // The wait this run is coming back from, or null on its first run.
    default @Nullable Wait resumed() {
        return null;
    }
}
