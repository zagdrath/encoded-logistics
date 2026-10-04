/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;

// STUB: waiting on elcl.store (system values kept in the network's saved data). In memory per system; the rules
// (defaults, allowed values, *SECOFR-class to change) are the real ones (OS.md 7).
final class StubSysvalService implements SysvalService {
    private record Definition(String name, String description, List<String> allowed) {}

    private static final List<Definition> DEFINITIONS = List.of(new Definition("SYSNAME", "System name", List.of("Name, 1-8 characters")),
            new Definition("DATFMT", "Date display format", List.of("*DAY", "*MDY", "*DMY", "*YMD")),
            new Definition("SECLVL", "Security level (10 / 30)", List.of("10", "30")),
            new Definition("QMAXJOB", "Maximum batch jobs", List.of("1-999")),
            new Definition("LOGRTN", "Job logs retained", List.of("0-999")),
            new Definition("PHOSPHOR", "Default screen colour", List.of("*GREEN", "*AMBER", "*WHITE")));

    private final ElclServices.Store<Map<String, String>> store = new ElclServices.Store<>(system -> new LinkedHashMap<>(defaults(system)));

    // STUB: waiting on elcl.store
    private static Map<String, String> defaults(ElclSystem system) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("SYSNAME", system.defaultName());
        values.put("DATFMT", "*DAY");
        values.put("SECLVL", "30");
        values.put("QMAXJOB", "16");
        values.put("LOGRTN", "50");
        values.put("PHOSPHOR", "*GREEN");
        return values;
    }

    // STUB: waiting on elcl.store
    @Override
    public List<Sysval> values(ElclSystem system) {
        Map<String, String> values = store.of(system), defaults = defaults(system);
        List<Sysval> list = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            list.add(new Sysval(definition.name(), values.get(definition.name()), defaults.get(definition.name()), definition.description(),
                    definition.allowed()));
        }
        return list;
    }

    // STUB: waiting on elcl.store
    @Override
    public Sysval value(ElclSystem system, String name) throws ElclException {
        for (Sysval sysval : values(system)) {
            if (sysval.name().equalsIgnoreCase(name)) {
                return sysval;
            }
        }
        throw new ElclException("ELC0103", name, "SYSVAL");
    }

    // STUB: waiting on elcl.store
    @Override
    public String get(ElclSystem system, String name) {
        return store.of(system).getOrDefault(name.toUpperCase(Locale.ROOT), "");
    }

    // STUB: waiting on elcl.store
    @Override
    public ElclMessage change(ElclSystem system, String user, boolean securityOfficer, String name, String value) throws ElclException {
        Sysval sysval = value(system, name);
        if (!securityOfficer) {
            throw new ElclException("ELC0401", user, "*SECOFR");
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (!valid(sysval.name(), upper)) {
            throw new ElclException("ELC0103", value, sysval.name());
        }
        store.of(system).put(sysval.name(), upper);
        return ElclMessage.of("ELC0222", sysval.name());
    }

    private static boolean valid(String name, String value) {
        return switch (name) {
            case "SYSNAME" -> value.matches("[A-Z][A-Z0-9]{0,7}");
            case "DATFMT" -> List.of("*DAY", "*MDY", "*DMY", "*YMD").contains(value);
            case "SECLVL" -> value.equals("10") || value.equals("30");
            case "QMAXJOB" -> value.matches("[0-9]{1,3}") && Integer.parseInt(value) >= 1;
            case "LOGRTN" -> value.matches("[0-9]{1,3}");
            case "PHOSPHOR" -> List.of("*GREEN", "*AMBER", "*WHITE").contains(value);
            default -> false;
        };
    }
}
