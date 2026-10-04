/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

// An escape message: a command failed (ELCL_SPEC.md 8). MONMSG catches it by ID; unmonitored, it ends the program.
public final class ElclException extends Exception {
    private final ElclMessage message;

    public ElclException(ElclMessage message) {
        super(message.toString(), null, false, false);
        this.message = message;
    }

    public ElclException(String id, Object... data) {
        this(ElclMessage.of(id, data));
    }

    public ElclMessage elclMessage() {
        return message;
    }
}
