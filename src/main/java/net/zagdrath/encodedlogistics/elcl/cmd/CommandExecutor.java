/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.cmd;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// What runs a command: it reads its arguments from the invocation, sets its RTN* values and sends its completion
// message; a failure is an escape message (ElclException).
@FunctionalInterface
public interface CommandExecutor {
    void run(Invocation call) throws ElclException;
}
