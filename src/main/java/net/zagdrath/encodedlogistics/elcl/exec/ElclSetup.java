/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

// Binds the game side's executors to the built-in commands' schemas (CommandRegistry), once at startup.
public final class ElclSetup {
    private static boolean done;

    private ElclSetup() {}

    public static synchronized void init() {
        if (done) {
            return;
        }
        done = true;
        RedstoneCommands.bind();
        OsCommands.bind();
    }
}
