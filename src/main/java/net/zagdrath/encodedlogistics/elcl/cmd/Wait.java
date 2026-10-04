/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.cmd;

import java.util.Map;

// What an async command waits for (ELCL_SPEC.md 9): DLYJOB's time, a recall from tape, a craft finishing. The job sits
// in *WAIT (costing no budget) until it's done, then the command runs again with it as Invocation.resumed(). Plain
// text so it saves with the job: its kind (the VM's wait checks know each kind) and data.
public record Wait(String kind, Map<String, String> data) {
    public static Wait of(String kind, String... keysAndValues) {
        java.util.LinkedHashMap<String, String> data = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            data.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return new Wait(kind, Map.copyOf(data));
    }

    public String get(String key) {
        return data.getOrDefault(key, "");
    }

    public long number(String key) {
        try {
            return Long.parseLong(get(key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
