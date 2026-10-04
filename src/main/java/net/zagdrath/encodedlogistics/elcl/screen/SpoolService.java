/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;

// Spooled output (Work with Output, Display Spooled File): compile listings, job logs, PRTTXT lines, printed sources.
public interface SpoolService {
    // status: *RDY, *PRT or *HLD.
    record SpooledFile(int id, String name, String jobNumber, String jobName, String user, int pages, String status, String created, List<String> lines) {
        public String job() {
            return jobNumber + "/" + jobName;
        }
    }

    int LINES_PER_PAGE = 60;

    // A user's files (null: everyone's), newest first; job: only that job's (null: all).
    List<SpooledFile> files(ElclSystem system, @Nullable String user, @Nullable String job);

    SpooledFile file(ElclSystem system, int id) throws ElclException;

    // A new file; its id.
    int create(ElclSystem system, String name, String jobNumber, String jobName, String user, List<String> lines);

    // PRTTXT: a line onto the job's file of that name (made when there's none).
    void append(ElclSystem system, String name, String jobNumber, String jobName, String user, String line);

    void delete(ElclSystem system, String user, int id) throws ElclException;

    // To the Line Printer (printer: *DFT or a device name).
    ElclMessage print(ElclSystem system, String user, int id, String printer) throws ElclException;
}
