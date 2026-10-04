/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import java.util.List;

// One message as sent: its ID, severity and data (ElclMessages has the text). "ELC0002  Variable &CONT is not
// declared." on Command Entry and in job logs; "ELC0002: ..." on the message line.
public record ElclMessage(String id, int severity, List<String> data) {
    public static ElclMessage of(String id, Object... data) {
        String[] texts = new String[data.length];
        for (int i = 0; i < data.length; i++) {
            texts[i] = String.valueOf(data[i]);
        }
        return new ElclMessage(id, ElclMessages.severity(id), List.of(texts));
    }

    // A user message (SNDPGMMSG): its own ID and text.
    public static ElclMessage user(String id, int severity, String text) {
        return new ElclMessage(id, severity, List.of(text));
    }

    public String text() {
        return ElclMessages.text(id, data.toArray(String[]::new));
    }

    public boolean isError() {
        return severity >= ElclMessages.ERROR;
    }

    // "ELC0002: Variable &CONT is not declared." - the message line's form.
    @Override
    public String toString() {
        return id + ": " + text();
    }
}
