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
        // Not in MESSAGES.md: a command whose ELCL package hasn't landed yet (SCREEN_INVENTORY.md).
        define("ELC0107", SEVERE, "Command &1 is not available yet.");
        // Not in MESSAGES.md originally: a program called on a command line runs in the interactive job.
        define("ELC0108", INFO, "Program &1 running in job &2.");
        define("ELC0109", SEVERE, "Job &1 is already running program &2.");
        define("ELC0110", INFO, "Program &1 ended normally.");
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
        // Not in MESSAGES.md originally: printing done.
        define("ELC1307", INFO, "Spooled file &1 printed on &2.");
        // Not in MESSAGES.md originally: device renaming (RNMDEV).
        define("ELC1308", SEVERE, "Device name &1 is already used on &2.");
        define("ELC1309", INFO, "Device &1 renamed to &2.");
        // Not in MESSAGES.md originally: 8" Diskettes (SAVLIB / RSTLIB).
        define("ELC1310", SEVERE, "No diskette in device &1.");
        define("ELC1311", SEVERE, "Library &1 needs &2 bytes; the diskette has &3 free.");
        // Not in MESSAGES.md originally: Display Panels.
        define("ELC1312", SEVERE, "Image &1 not found in the images folder.");
        define("ELC1313", SEVERE, "Image &1 is larger than the allowed size (&2).");
        define("ELC1314", SEVERE, "Region &1 is outside the screen or overlaps another region.");
        define("ELC1315", SEVERE, "Images are disabled on this server.");
        define("ELC1316", SEVERE, "Data source &1 is not available for this widget.");
        define("ELC1317", WARNING, "Colour mode &1 is above the server's limit; &2 used.");
        // Not in MESSAGES.md originally: machines with a Small Wireless Bridge (Arcforge).
        define("ELC1318", SEVERE, "Device &1 is not an Arcforge machine.");
        define("ELC1319", SEVERE, "Machine &1 is not formed.");
        define("ELC1320", SEVERE, "Machine &1 does not support &2.");
        define("ELC1321", SEVERE, "Machine &1 rejected &2(&3).");
        define("ELC1322", INFO, "Machine &1 changed.");
        // Database files: reading and writing them.
        define("ELC2201", SEVERE, "End of file &1 reached.");
        define("ELC2202", SEVERE, "Record with key &1 not found in file &2.");
        define("ELC2203", SEVERE, "Duplicate key &1 in file &2.");
        define("ELC2204", SEVERE, "No record read from file &1 to change or delete.");
        define("ELC2205", SEVERE, "File &1 not found in library &2.");
        define("ELC2206", SEVERE, "File with open identifier &1 not declared.");
        define("ELC2207", SEVERE, "Level check on file &1: &2 changed since the program was compiled.");
        define("ELC2208", SEVERE, "File &1 is full: limit of &2 records.");
        define("ELC2209", SEVERE, "Value '&1' not valid for field &2.");
        // Database files: their definitions (PF source members).
        define("ELC2220", SEVERE, "Definition statement not valid at '&1'.");
        define("ELC2221", SEVERE, "Field &1 defined more than once.");
        define("ELC2222", SEVERE, "Length &1 not valid for field &2 (type &3).");
        define("ELC2223", SEVERE, "Key field &1 is not a field of the record.");
        define("ELC2224", SEVERE, "Too many &1 (max &2).");
        define("ELC2225", SEVERE, "No record format (R) defined before the fields.");
        define("ELC2226", SEVERE, "Keyword &1 not valid here.");
        define("ELC2227", SEVERE, "Type &1 not valid for field &2.");
        define("ELC2228", SEVERE, "Name &1 not valid.");
        // Database files: the OS commands.
        define("ELC2230", INFO, "File &1 created in library &2.");
        define("ELC2231", INFO, "File &1 changed in library &2: &3 records kept.");
        define("ELC2232", WARNING, "Field &1 dropped from file &2: its values are lost.");
        define("ELC2233", INFO, "File &1 deleted from library &2.");
        define("ELC2234", INFO, "File &1 cleared: &2 records removed.");
        define("ELC2235", INFO, "&1 records copied to file &2.");
        define("ELC2236", INFO, "&1 records copied to &2.");
        define("ELC2237", INFO, "&1 records copied from &2.");
        define("ELC2238", INFO, "Query selected &1 of &2 records.");
        define("ELC2239", SEVERE, "File &1 was not created: definition errors.");
        define("ELC2240", SEVERE, "Stream file &1 not found.");
        define("ELC2241", SEVERE, "Folder sync is off: &1 needs the system's folder.");
        define("ELC2242", SEVERE, "Field &1 not found in file &2.");
        define("ELC2243", SEVERE, "Row &1: value '&2' not valid for field &3.");
        define("ELC2244", SEVERE, "Files &1 and &2 have no fields in common.");
        define("ELC2245", SEVERE, "Stream file name &1 not valid.");
        define("ELC2246", SEVERE, "File &1 was not changed: definition errors.");
        define("ELC2247", SEVERE, "Member &1 is not a &2 source member.");
        // Crafting.
        define("ELC1401", SEVERE, "No Scheduler available.");
        define("ELC1402", SEVERE, "No recipe known for &1.");
        define("ELC1403", SEVERE, "Missing ingredients for &1.");
        define("ELC1404", SEVERE, "Craft job &1 not found.");
        // PLCs (docs/plc): sensor modules, programs, RUN / STOP.
        define("ELC1501", SEVERE, "Module &1 is not installed or is not a &2.");
        define("ELC1502", SEVERE, "Command &1 needs a network; this PLC is not cabled to one.");
        define("ELC1503", ERROR, "Sensor module &1 has no target (&2).");
        define("ELC1504", SEVERE, "Program &1 was not compiled for a PLC.");
        define("ELC1505", SEVERE, "PLC &1 has no program loaded.");
        define("ELC1506", WARNING, "RETAIN is only meaningful in a PLC program; ignored.");
        define("ELC1507", INFO, "PLC &1 started.");
        define("ELC1508", INFO, "PLC &1 stopped.");
        // Not in the PLC handoff: SNDPLCPGM's completion message.
        define("ELC1509", INFO, "Program &1 loaded into PLC &2.");
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
