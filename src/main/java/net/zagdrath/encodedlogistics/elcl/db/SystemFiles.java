/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The read-only files in ELSYS whose records are the network's own data, made as they're read (StoredFileService asks
// the game for the rows): INVITEMS the items in storage, DEVICES the named devices, CRFHIST the crafting job history,
// JOBS the script jobs. Their formats are fixed here, so programs compile against them anywhere.
public final class SystemFiles {
    public static final String INVITEMS = "INVITEMS", DEVICES = "DEVICES", CRFHIST = "CRFHIST", JOBS = "JOBS";

    private static final Map<String, RecordFormat> FORMATS = new LinkedHashMap<>();

    private SystemFiles() {}

    private static FieldDef a(String name, int length, String text, String heading) {
        return new FieldDef(name, FieldDef.Type.CHAR, length, 0, text, List.of(heading));
    }

    private static FieldDef s(String name, int length, String text, String heading) {
        return new FieldDef(name, FieldDef.Type.INT, length, 0, text, List.of(heading));
    }

    private static FieldDef t(String name, String text, String heading) {
        return new FieldDef(name, FieldDef.Type.TIME, FieldDef.TIME_LENGTH, 0, text, List.of(heading));
    }

    static {
        FORMATS.put(INVITEMS, new RecordFormat("INVITEMR", "Network items (live)", List.of(
                a("ITEM", 64, "Item ID", "Item"),
                a("NAME", 48, "Display name", "Name"),
                s("HOT", 18, "Count in hot storage", "Hot"),
                s("COLD", 18, "Count on tape", "Cold"),
                a("MOD", 32, "Mod the item comes from", "Mod")), List.of("ITEM"), true));
        FORMATS.put(DEVICES, new RecordFormat("DEVICER", "Network devices (live)", List.of(
                a("NAME", 10, "Device name", "Device"),
                a("TYPE", 10, "Device type", "Type"),
                a("LOCATION", 40, "Where it is", "Location"),
                s("LANES", 9, "Lanes it uses", "Lanes"),
                a("STATUS", 10, "*ONLINE, *OFFLINE, *FAULT or *DISABLED", "Status")), List.of("NAME"), true));
        FORMATS.put(CRFHIST, new RecordFormat("CRFHISTR", "Crafting job history (live)", List.of(
                s("NUMBER", 9, "Crafting job number", "Number"),
                a("JOBID", 8, "Crafting job ID", "Job"),
                a("ITEM", 64, "Item crafted", "Item"),
                s("REQUESTED", 18, "Quantity asked for", "Requested"),
                s("PRODUCED", 18, "Quantity made", "Produced"),
                a("STATUS", 10, "*DONE, *FAILED or *CANCELLED", "Status"),
                a("USER", 10, "Who asked", "User"),
                t("STARTED", "When it started", "Started"),
                t("ENDED", "When it ended", "Ended")), List.of("NUMBER"), true));
        FORMATS.put(JOBS, new RecordFormat("JOBR", "Script jobs (live)", List.of(
                a("NUMBER", 6, "Job number", "Number"),
                a("JOB", 10, "Job name", "Job"),
                a("USER", 10, "User", "User"),
                a("TYPE", 3, "INT or BCH", "Type"),
                a("HOST", 10, "Job host", "Host"),
                a("STATUS", 7, "*ACTIVE, *JOBQ, *WAIT, *HELD...", "Status"),
                s("PRIORITY", 1, "Job priority", "Pty"),
                s("BUDGET", 3, "Budget used, percent", "Budget")), List.of("NUMBER"), true));
    }

    public static Map<String, RecordFormat> formats() {
        return FORMATS;
    }

    public static boolean is(String library, String file) {
        return library.equals("ELSYS") && FORMATS.containsKey(file);
    }
}
