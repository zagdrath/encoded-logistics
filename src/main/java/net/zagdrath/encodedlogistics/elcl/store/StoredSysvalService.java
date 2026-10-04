/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.SysvalService;

// System values in the system's saved data (OS.md 7): the defaults, allowed values and *SECOFR-class to change. A
// system keeps only the values changed from their defaults.
public final class StoredSysvalService implements SysvalService {
    private record Definition(String name, String description, List<String> allowed) {}

    private static final List<Definition> DEFINITIONS = List.of(new Definition("SYSNAME", "System name", List.of("Name, 1-8 characters")),
            new Definition("DATFMT", "Date display format", List.of("*DAY", "*MDY", "*DMY", "*YMD")),
            new Definition("SECLVL", "Security level (10 / 30)", List.of("10", "30")),
            new Definition("QMAXJOB", "Maximum batch jobs", List.of("1-999")),
            new Definition("LOGRTN", "Job logs retained", List.of("0-999")),
            new Definition("CRFLOGRTN", "Crafting jobs retained", List.of("0-999")),
            new Definition("PHOSPHOR", "Default screen colour", List.of("*GREEN", "*AMBER", "*WHITE")));

    private static Map<String, String> defaults(ElclSystem system) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("SYSNAME", system.defaultName());
        values.put("DATFMT", "*DAY");
        values.put("SECLVL", "30");
        values.put("QMAXJOB", "16");
        values.put("LOGRTN", "50");
        values.put("CRFLOGRTN", Integer.toString(ElclConfig.craftLogRetention()));
        values.put("PHOSPHOR", "*GREEN");
        return values;
    }

    @Override
    public List<Sysval> values(ElclSystem system) {
        Map<String, String> defaults = defaults(system), values = new LinkedHashMap<>(defaults);
        synchronized (this) {
            values.putAll(ElclStore.of(system).sysvals);
        }
        List<Sysval> list = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            list.add(new Sysval(definition.name(), values.get(definition.name()), defaults.get(definition.name()), definition.description(),
                    definition.allowed()));
        }
        return list;
    }

    @Override
    public Sysval value(ElclSystem system, String name) throws ElclException {
        for (Sysval sysval : values(system)) {
            if (sysval.name().equalsIgnoreCase(name)) {
                return sysval;
            }
        }
        throw new ElclException("ELC0103", name, "SYSVAL");
    }

    @Override
    public String get(ElclSystem system, String name) {
        String key = name.toUpperCase(Locale.ROOT);
        synchronized (this) {
            String value = ElclStore.of(system).sysvals.get(key);
            if (value != null) {
                return value;
            }
        }
        return defaults(system).getOrDefault(key, "");
    }

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
        synchronized (this) {
            SystemData data = ElclStore.of(system);
            data.sysvals.put(sysval.name(), upper);
            data.changed();
        }
        if (sysval.name().equals("CRFLOGRTN")) {
            // Lowered: the history down to it now.
            CraftLog.trim(system);
        }
        return ElclMessage.of("ELC0222", sysval.name());
    }

    private static boolean valid(String name, String value) {
        return switch (name) {
            case "SYSNAME" -> value.matches("[A-Z][A-Z0-9]{0,7}");
            case "DATFMT" -> List.of("*DAY", "*MDY", "*DMY", "*YMD").contains(value);
            case "SECLVL" -> value.equals("10") || value.equals("30");
            case "QMAXJOB" -> value.matches("[0-9]{1,3}") && Integer.parseInt(value) >= 1;
            case "LOGRTN", "CRFLOGRTN" -> value.matches("[0-9]{1,3}");
            case "PHOSPHOR" -> List.of("*GREEN", "*AMBER", "*WHITE").contains(value);
            default -> false;
        };
    }
}
