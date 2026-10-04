/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import java.util.LinkedHashMap;
import java.util.Map;

// Every ELCL message (docs/elcl/MESSAGES.md): its ID, severity (00 info, 10 warning, 20 error, 30 severe, 40 abnormal
// end) and text, with &1, &2... for its data. Monitoring ELCnn00 covers ELCnnxx; ELC0000 covers everything.
public final class ElclMessages {
    public static final int INFO = 0, WARNING = 10, ERROR = 20, SEVERE = 30, ABEND = 40;

    public record Definition(String id, int severity, String text) {}

    private static final Map<String, Definition> MESSAGES = new LinkedHashMap<>();

    private ElclMessages() {}

    private static void define(String id, int severity, String text) {
        MESSAGES.put(id, new Definition(id, severity, text));
    }

    static {
        // Language and runtime.
        define("ELC0001", SEVERE, "Syntax error at '&1'.");
        define("ELC0002", SEVERE, "Variable &1 is not declared.");
        define("ELC0003", SEVERE, "Value '&1' is not valid for type &2.");
        define("ELC0004", SEVERE, "Value &1 is outside the allowed range.");
        define("ELC0005", SEVERE, "Division by zero.");
        define("ELC0006", SEVERE, "List index &1 is out of range (size &2).");
        define("ELC0007", SEVERE, "Numeric overflow.");
        define("ELC0008", WARNING, "Value truncated to length &1.");
        define("ELC0009", SEVERE, "&1 without matching &2.");
        define("ELC0010", SEVERE, "Label &1 not found.");
        define("ELC0011", SEVERE, "Call depth exceeded (max &1).");
        define("ELC0012", SEVERE, "Parameter count mismatch calling &1: expected &2, got &3.");
        define("ELC0013", SEVERE, "Called program &1 ended abnormally.");
        define("ELC0014", SEVERE, "GOTO into a loop or DO group is not allowed.");
        define("ELC0015", SEVERE, "List limit of &1 elements reached.");
        define("ELC0016", SEVERE, "DCL must come before other commands.");
        // Commands.
        define("ELC0101", SEVERE, "Command &1 not found.");
        define("ELC0102", SEVERE, "Required parameter &1 missing.");
        define("ELC0103", SEVERE, "Value '&1' not valid for parameter &2.");
        define("ELC0104", SEVERE, "Parameter &1 specified more than once.");
        define("ELC0105", SEVERE, "Command &1 is not allowed in a batch job.");
        define("ELC0106", SEVERE, "Command &1 is not allowed in an interactive job.");
        // Objects.
        define("ELC0201", SEVERE, "Library &1 not found.");
        define("ELC0202", SEVERE, "Member &1 not found in library &2.");
        define("ELC0203", SEVERE, "Program &1 not found in library &2.");
        define("ELC0204", SEVERE, "Object &1 already exists in library &2.");
        define("ELC0205", SEVERE, "Library &1 is read-only.");
        define("ELC0206", SEVERE, "Program &1 was not created: compile errors.");
        define("ELC0207", SEVERE, "Not enough network storage to save &1.");
        define("ELC0208", SEVERE, "Member &1 is locked by another user.");
        define("ELC0210", INFO, "Library &1 created.");
        define("ELC0211", INFO, "Library &1 deleted.");
        define("ELC0212", INFO, "Library &1 changed.");
        define("ELC0213", INFO, "Member &1 saved in library &2.");
        define("ELC0214", INFO, "Member &1 created in library &2.");
        define("ELC0215", INFO, "Member &1 copied to &2.");
        define("ELC0216", INFO, "Member &1 renamed to &2.");
        define("ELC0217", INFO, "Member &1 deleted from library &2.");
        define("ELC0218", INFO, "Program &1 created in library &2.");
        define("ELC0219", INFO, "Program &1 deleted from library &2.");
        define("ELC0220", INFO, "Library &1 saved to &2.");
        define("ELC0221", INFO, "Library &1 restored from &2.");
        define("ELC0222", INFO, "System value &1 changed.");
        // Jobs.
        define("ELC0301", SEVERE, "No job host available to run batch job &1.");
        define("ELC0302", SEVERE, "Job &1 not found.");
        define("ELC0303", ABEND, "Job ended by operator.");
        define("ELC0304", INFO, "Job &1 submitted to job queue on &2.");
        define("ELC0305", SEVERE, "Schedule entry &1 already exists.");
        define("ELC0306", SEVERE, "Trigger &1 already exists.");
        define("ELC0307", INFO, "Job &1 ended normally.");
        define("ELC0308", INFO, "Job &1 held.");
        define("ELC0309", INFO, "Job &1 released.");
        define("ELC0310", ABEND, "Job ended: job host &1 was unloaded or lost power.");
        define("ELC0311", INFO, "Job &1 ending (&2).");
        define("ELC0312", INFO, "Schedule entry &1 added.");
        define("ELC0313", INFO, "Schedule entry &1 removed.");
        define("ELC0314", INFO, "Trigger &1 added.");
        define("ELC0315", INFO, "Trigger &1 removed.");
        // Security.
        define("ELC0401", SEVERE, "Not authorized: user &1 lacks &2 permission.");
        define("ELC0402", SEVERE, "Sign-on failed for user &1.");
        // Inventory and storage.
        define("ELC1201", SEVERE, "Item &1 not found in network.");
        define("ELC1202", SEVERE, "Only &1 of &2 items available.");
        define("ELC1203", INFO, "Item &1 is being recalled from tape.");
        define("ELC1204", SEVERE, "Network storage is full.");
        define("ELC1205", SEVERE, "Item name &1 is ambiguous; use a qualified ID.");
        define("ELC1206", SEVERE, "No Tape Library on the network.");
        // Devices.
        define("ELC1301", SEVERE, "Device &1 not found.");
        define("ELC1302", SEVERE, "Device &1 is offline.");
        define("ELC1303", SEVERE, "Device &1 (type &2) does not support this operation.");
        define("ELC1304", SEVERE, "Device &1 is not facing an inventory.");
        define("ELC1305", SEVERE, "Filter on device &1 is full.");
        define("ELC1306", SEVERE, "Printer &1 is out of paper.");
        // Crafting.
        define("ELC1401", SEVERE, "No Scheduler available.");
        define("ELC1402", SEVERE, "No recipe known for &1.");
        define("ELC1403", SEVERE, "Missing ingredients for &1.");
        define("ELC1404", SEVERE, "Craft job &1 not found.");
    }

    public static Map<String, Definition> all() {
        return MESSAGES;
    }

    public static boolean exists(String id) {
        return MESSAGES.containsKey(id);
    }

    // A user message (USRnnnn, SNDPGMMSG) is an escape message of severity 30 with its text as given.
    public static int severity(String id) {
        Definition definition = MESSAGES.get(id);
        return definition != null ? definition.severity() : SEVERE;
    }

    // The text with its data put in: &1 is data[0]. User messages (no definition) are their first datum.
    public static String text(String id, String... data) {
        Definition definition = MESSAGES.get(id);
        if (definition == null) {
            return data.length > 0 ? data[0] : id;
        }
        String text = definition.text();
        // From &9 down, so &1 doesn't eat the start of &10.
        for (int i = Math.min(9, data.length); i >= 1; i--) {
            text = text.replace("&" + i, data[i - 1]);
        }
        return text;
    }

    // Whether a MONMSG for monitor catches id: the same ID, ELCnn00 for its range, ELC0000 (or XXX0000) for all.
    public static boolean monitors(String monitor, String id) {
        if (monitor.equalsIgnoreCase(id)) {
            return true;
        }
        if (monitor.length() != 7 || id.length() != 7 || !monitor.regionMatches(true, 0, id, 0, 3)) {
            return false;
        }
        if (monitor.endsWith("0000")) {
            return true;
        }
        return monitor.endsWith("00") && monitor.regionMatches(true, 3, id, 3, 2);
    }

    // A message ID's shape: three letters and four digits ("ELC1201", "USR0001").
    public static boolean isId(String text) {
        return text.matches("[A-Za-z]{3}[0-9]{4}");
    }
}
