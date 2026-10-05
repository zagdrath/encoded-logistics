/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.device;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;

// Where PRTRPT and Work with Output's 6=Print find a printer: every registered source's printers on the system. With
// none, ELC1301; out of paper, ELC1306. *DFT is the first online printer.
public final class Printers {
    private static final DeviceSources<PrinterDevice> SOURCES = new DeviceSources<>();

    private Printers() {}

    public static void register(DeviceSources.Source<PrinterDevice> source) {
        SOURCES.register(source);
    }

    public static void unregister(DeviceSources.Source<PrinterDevice> source) {
        SOURCES.unregister(source);
    }

    public static List<PrinterDevice> all(ElclSystem system) {
        return SOURCES.all(system);
    }

    // The printer a name means (*DFT: the first online one; else by name).
    public static PrinterDevice find(ElclSystem system, String name) throws ElclException {
        List<PrinterDevice> printers = new ArrayList<>(all(system));
        for (PrinterDevice printer : printers) {
            if (name.equalsIgnoreCase("*DFT") ? printer.online() : printer.name().equalsIgnoreCase(name)) {
                return printer;
            }
        }
        throw new ElclException("ELC1301", name.equalsIgnoreCase("*DFT") ? "PRT01" : name.toUpperCase(java.util.Locale.ROOT));
    }

    // Prints, or says why it can't: ELC1301 (no such printer), ELC1302 (offline), ELC1306 (out of paper).
    public static ElclMessage print(ElclSystem system, String name, String title, List<String> lines) throws ElclException {
        return print(system, name, title, "splf:" + title, lines);
    }

    public static ElclMessage print(ElclSystem system, String name, String title, String report, List<String> lines) throws ElclException {
        PrinterDevice printer = find(system, name);
        if (!printer.online()) {
            throw new ElclException("ELC1302", printer.name());
        }
        if (!printer.hasPaper()) {
            throw new ElclException("ELC1306", printer.name());
        }
        printer.print(title, report, lines);
        return ElclMessage.of("ELC1307", title, printer.name());
    }
}
