/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

// A message the parser or compiler found, and the source line (record index, 0-based) it's about.
public record Diagnostic(int line, ElclMessage message) {
    public static Diagnostic of(int line, String id, Object... data) {
        return new Diagnostic(line, ElclMessage.of(id, data));
    }

    public boolean isError() {
        return message.isError();
    }
}
