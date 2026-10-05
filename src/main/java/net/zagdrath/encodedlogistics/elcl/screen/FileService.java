/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.db.DbRecord;
import net.zagdrath.encodedlogistics.elcl.db.FileAccess;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;

// Physical files (*FILE, attribute PF): what Work with Files, Display File Description, Display Physical File Member,
// Update Data, the database commands (CRTPF, CHGPF, DLTF, CLRPFM, CPYF, CPYTOIMPF / CPYFRMIMPF, RUNQRY) and running
// programs (FileAccess) work on. Reading a file needs its library (*USE: every library); writing needs *CHANGE on it
// (its owner, *SECOFR, or a *CHANGE library; never ELSYS: ELC0205); ELSYS's system files, made from the network's own
// data, need the Firewall's view permission (ELC0401). Records come in key order (arrival order without a key).
// Failures are escape messages (ELC0201, ELC0204, ELC0207, ELC22xx, ELC0401).
public interface FileService {
    // A file as the screens show it: attribute PF; records and the storage they take (bytes); its source member (LIB/MBR,
    // or *NONE); changed: the source changed after it was created; system: one of ELSYS's live files.
    record File(String library, String name, String attribute, String text, int records, long size, String created, String source, boolean changed,
            boolean system, RecordFormat format) {}

    // Who's asking: the user, and whether the Firewall lets them view the network (ELSYS's system files need it).
    record Who(String user, boolean view) {}

    // What CRTPF / CHGPF did: whether the file was made (changed); records kept and fields dropped (CHGPF); the listing,
    // its messages and the spooled file it went to.
    record Outcome(boolean done, int kept, List<String> dropped, List<String> listing, List<Diagnostic> diagnostics, int spooledFile) {}

    List<File> files(ElclSystem system, String library) throws ElclException;

    File file(ElclSystem system, String library, String name) throws ElclException;

    // LIB/NAME with *LIBL (the user's library list, in order) or *CURLIB resolved: {library, name}. ELC2205 when no
    // library on the list has it.
    String[] resolve(ElclSystem system, String user, String library, String name) throws ElclException;

    // A file's record format (DCLF's compile, the screens).
    RecordFormat format(ElclSystem system, String library, String name) throws ElclException;

    // CRTPF: a file from a PF source member (ELC2247 for another type); text null: the member's.
    Outcome create(ElclSystem system, String user, String library, String name, String sourceLibrary, String sourceMember, @Nullable String text)
            throws ElclException;

    // CHGPF: the file made again from its (or another) source member, its records kept field by field where a field of
    // the same name and type is still there; text null: unchanged.
    Outcome change(ElclSystem system, String user, String library, String name, @Nullable String sourceLibrary, @Nullable String sourceMember,
            @Nullable String text) throws ElclException;

    // A file of a given format with these records (RUNQRY OUTFILE, CPYF CRTFILE(*YES)); replace: one already there is
    // replaced, else ELC0204.
    void createFrom(ElclSystem system, String user, String library, String name, RecordFormat format, String text, List<Object[]> records,
            boolean replace) throws ElclException;

    void delete(ElclSystem system, String user, String library, String name) throws ElclException;

    // CLRPFM: every record gone; how many there were.
    int clear(ElclSystem system, String user, String library, String name) throws ElclException;

    // --- Records ---

    List<DbRecord> records(ElclSystem system, Who who, String library, String name) throws ElclException;

    @Nullable DbRecord record(ElclSystem system, Who who, String library, String name, long rrn) throws ElclException;

    @Nullable DbRecord next(ElclSystem system, Who who, String library, String name, DbRecord.@Nullable Position after) throws ElclException;

    @Nullable DbRecord previous(ElclSystem system, Who who, String library, String name, DbRecord.@Nullable Position before) throws ElclException;

    @Nullable DbRecord chain(ElclSystem system, Who who, String library, String name, Object[] key) throws ElclException;

    // Where a partial key would be in key order (Display Physical File Member's position to): a 0-based index.
    int position(ElclSystem system, Who who, String library, String name, Object[] key) throws ElclException;

    DbRecord write(ElclSystem system, Who who, String library, String name, Object[] values) throws ElclException;

    DbRecord update(ElclSystem system, Who who, String library, String name, long rrn, Object[] values) throws ElclException;

    void delete(ElclSystem system, Who who, String library, String name, long rrn) throws ElclException;

    // Many records at once, all or none (CPYF, CPYFRMIMPF); replace: the file's records go first. How many were added.
    int add(ElclSystem system, Who who, String library, String name, List<Object[]> records, boolean replace) throws ElclException;

    // A running program's files (the VM's RCVF, WRTRCD...), as the job's user: *LIBL on their library list.
    default FileAccess access(ElclSystem system, Who who) {
        FileService files = this;
        return new FileAccess() {
            @Override
            public Opened open(String library, String file) throws ElclException {
                String[] found = files.resolve(system, who.user(), library, file);
                return new Opened(found[0], found[1], files.format(system, found[0], found[1]));
            }

            @Override
            public @Nullable DbRecord next(Opened file, DbRecord.@Nullable Position after) throws ElclException {
                return files.next(system, who, file.library(), file.file(), after);
            }

            @Override
            public @Nullable DbRecord chain(Opened file, Object[] key) throws ElclException {
                return files.chain(system, who, file.library(), file.file(), key);
            }

            @Override
            public DbRecord write(Opened file, Object[] values) throws ElclException {
                return files.write(system, who, file.library(), file.file(), values);
            }

            @Override
            public DbRecord update(Opened file, long rrn, Object[] values) throws ElclException {
                return files.update(system, who, file.library(), file.file(), rrn, values);
            }

            @Override
            public void delete(Opened file, long rrn) throws ElclException {
                files.delete(system, who, file.library(), file.file(), rrn);
            }
        };
    }
}
