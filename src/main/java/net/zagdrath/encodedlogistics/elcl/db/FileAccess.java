/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// What a running program does with files (RCVF, CHNRCD, WRTRCD, UPDRCD, DLTRCD), as the job's user: the game's runs
// against the system's files (reading needs the library, writing *CHANGE on it, ELSYS's system files the Firewall's
// view permission: ELC0401); the VM's tests against files in memory. Values go in and come out as the file's format
// holds them; a T field written blank takes the moment it's written.
public interface FileAccess {
    // A file opened: its library (a *LIBL one found), name and record format now.
    record Opened(String library, String file, RecordFormat format) {
        public String qualified() {
            return library + "/" + file;
        }
    }

    // ELC0201 / ELC2205 when there's no such file.
    Opened open(String library, String file) throws ElclException;

    // The record after a position (null: the first), in key order (arrival order without a key); null at the end.
    @Nullable DbRecord next(Opened file, DbRecord.@Nullable Position after) throws ElclException;

    // The first record whose leading key fields are these; null when there's none.
    @Nullable DbRecord chain(Opened file, Object[] key) throws ElclException;

    // A new record: as written (its number, a blank timestamp filled). ELC2203 for a duplicate unique key, ELC2208 when
    // the file is full, ELC0207 when the network's storage is.
    DbRecord write(Opened file, Object[] values) throws ElclException;

    // ELC2204 when the record isn't there any more.
    DbRecord update(Opened file, long rrn, Object[] values) throws ElclException;

    void delete(Opened file, long rrn) throws ElclException;
}
