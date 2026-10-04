/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.List;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;

// System values (OS.md 7): SYSNAME, DATFMT, SECLVL, QMAXJOB, LOGRTN, PHOSPHOR. Changing one takes *SECOFR-class
// authority (ELC0401 otherwise); a value outside its allowed ones is ELC0103.
public interface SysvalService {
    // allowed: the special values it takes, or a description ("1-999") when it's a number or a name.
    record Sysval(String name, String value, String defaultValue, String description, List<String> allowed) {}

    List<Sysval> values(ElclSystem system);

    Sysval value(ElclSystem system, String name) throws ElclException;

    // The value's text ("" for an unknown name).
    String get(ElclSystem system, String name);

    ElclMessage change(ElclSystem system, String user, boolean securityOfficer, String name, String value) throws ElclException;
}
