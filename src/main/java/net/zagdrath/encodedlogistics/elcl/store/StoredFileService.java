/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.db.DbFile;
import net.zagdrath.encodedlogistics.elcl.db.DbRecord;
import net.zagdrath.encodedlogistics.elcl.db.Dds;
import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.db.Timestamps;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.FileService;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;

// Physical files in the system's saved data (each library's files, SystemData.Library.files), on the library service's
// rules: writing needs *CHANGE on the library (StoredLibraryService.writable: ELC0205 for ELSYS, ELC0401); records
// take network storage like source (a file's record length per record, charsPerStorageByte to a byte: ELC0207 when
// the drives can't take it); a file holds at most maxRecordsPerFile records (ELC2208). ELSYS's system files have no
// records of their own: theirs come from the game (live: LiveSource, registered at setup) when they're read - afresh for
// a read from the top or by key, and for reading on from a record the same picture until the tick is over (a program
// reading one record at a time sees one picture each tick) - and need the Firewall's view permission (ELC0401).
// A timestamp written blank takes the game's clock at that moment.
public final class StoredFileService implements FileService {
    // The rows of a system file now, in its format (the game registers it).
    public interface LiveSource {
        List<Object[]> rows(ElclSystem system, String file);
    }

    private static LiveSource live = (system, file) -> List.of();

    private record Snapshot(long tick, DbFile file) {}

    private final Map<String, Snapshot> snapshots = new HashMap<>();

    public static void setLive(LiveSource source) {
        live = source;
    }

    private static String upper(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private static @Nullable StoredLibraryService libraries() {
        return ElclServices.libraries() instanceof StoredLibraryService stored ? stored : null;
    }

    // --- Finding files ---

    private static DbFile find(SystemData.Library library, String name) throws ElclException {
        DbFile file = library.files.get(upper(name));
        if (file == null) {
            throw new ElclException("ELC2205", upper(name), library.name);
        }
        return file;
    }

    private static DbFile find(ElclSystem system, String library, String name) throws ElclException {
        return find(StoredLibraryService.find(system, library), name);
    }

    @Override
    public synchronized String[] resolve(ElclSystem system, String user, String library, String name) throws ElclException {
        String lib = upper(library), file = upper(name);
        UserProfile profile = profile(system, user);
        if (lib.equals("*CURLIB")) {
            return new String[] { profile.currentLibrary(), file };
        }
        if (!lib.equals("*LIBL") && !lib.isEmpty()) {
            return new String[] { lib, file };
        }
        for (String candidate : profile.libraryList()) {
            SystemData.Library found = ElclStore.of(system).libraries.get(candidate);
            if (found != null && found.files.containsKey(file)) {
                return new String[] { candidate, file };
            }
        }
        throw new ElclException("ELC2205", file, "*LIBL");
    }

    private record UserProfile(String currentLibrary, List<String> libraryList) {}

    private static UserProfile profile(ElclSystem system, String user) {
        var profile = ElclServices.users().profile(system, user, null);
        return new UserProfile(profile.currentLibrary(), profile.libraryList());
    }

    // The ELSYS system files need the Firewall's view permission.
    private static void readable(DbFile file, Who who) throws ElclException {
        if (file.system && !who.view()) {
            throw new ElclException("ELC0401", who.user(), "VIEW");
        }
    }

    // A file's records as they are to be read: its own, or (a system file) a picture of the game's data - made now, or
    // (reading on: fresh false) this tick's when there is one.
    private DbFile data(ElclSystem system, SystemData.Library library, DbFile file, boolean fresh) {
        if (!file.system) {
            return file;
        }
        String key = system.network() + "/" + library.name + "/" + file.name;
        long tick = system.server().getTickCount();
        Snapshot snapshot = snapshots.get(key);
        if (!fresh && snapshot != null && snapshot.tick() == tick) {
            return snapshot.file();
        }
        DbFile rows = new DbFile(file.name, file.text, file.format, file.sourceLibrary, file.sourceMember, 0, file.created, true);
        for (Object[] values : live.rows(system, file.name)) {
            try {
                rows.add(values);
            } catch (ElclException | IllegalArgumentException ignored) {
                // A second row with the same key (two items of one ID): the first is kept.
            }
        }
        snapshots.put(key, new Snapshot(tick, rows));
        return rows;
    }

    private DbFile reading(ElclSystem system, Who who, String library, String name, boolean fresh) throws ElclException {
        SystemData.Library lib = StoredLibraryService.find(system, library);
        DbFile file = find(lib, name);
        readable(file, who);
        return data(system, lib, file, fresh);
    }

    private DbFile reading(ElclSystem system, Who who, String library, String name) throws ElclException {
        return reading(system, who, library, name, true);
    }

    // A file to write: *CHANGE on its library (system files are in ELSYS: ELC0205).
    private static DbFile writing(ElclSystem system, Who who, String library, String name) throws ElclException {
        SystemData.Library lib = StoredLibraryService.find(system, library);
        StoredLibraryService.writable(system, lib, who.user());
        return find(lib, name);
    }

    private static File view(SystemData.Library library, DbFile file) {
        SystemData.Member source = file.sourceLibrary.isEmpty() || file.sourceMember.isEmpty() ? null : member(library, file);
        boolean changed = source != null && source.version != file.sourceVersion;
        String from = file.system ? "*SYSTEM" : file.sourceMember.isEmpty() || file.sourceMember.equals(DbFile.NO_SOURCE) ? DbFile.NO_SOURCE
                : file.sourceLibrary + "/" + file.sourceMember;
        return new File(library.name, file.name, DbFile.PF, file.text, file.system ? 0 : file.size(),
                SystemData.cost(file.characters(), ElclConfig.charsPerStorageByte()), file.created, from, changed, file.system, file.format);
    }

    // The member a file was made from, when it's in the same library (WRKMBR's flag looks there).
    private static SystemData.@Nullable Member member(SystemData.Library library, DbFile file) {
        return library.name.equals(file.sourceLibrary) ? library.members.get(file.sourceMember) : null;
    }

    @Override
    public synchronized List<File> files(ElclSystem system, String library) throws ElclException {
        SystemData.Library lib = StoredLibraryService.find(system, library);
        List<File> files = new ArrayList<>();
        lib.files.values().forEach(file -> files.add(view(lib, file)));
        return files;
    }

    @Override
    public synchronized File file(ElclSystem system, String library, String name) throws ElclException {
        SystemData.Library lib = StoredLibraryService.find(system, library);
        DbFile file = find(lib, name);
        File shown = view(lib, file);
        if (file.system) {
            // A system file's record count is what's there now.
            DbFile rows = data(system, lib, file, true);
            return new File(shown.library(), shown.name(), shown.attribute(), shown.text(), rows.size(), 0, shown.created(), shown.source(), false, true,
                    shown.format());
        }
        return shown;
    }

    @Override
    public synchronized RecordFormat format(ElclSystem system, String library, String name) throws ElclException {
        return find(system, library, name).format;
    }

    // --- Making and changing files ---

    private static long cost(long characters) {
        return SystemData.cost(characters, ElclConfig.charsPerStorageByte());
    }

    // ELC0207 when the drives can't take `more` bytes.
    private static void room(ElclSystem system, String name, long more) throws ElclException {
        StoredLibraryService libraries = libraries();
        if (libraries != null && more > 0) {
            libraries.room(system, name, more);
        }
    }

    // ELC2208 when a file would hold more than the limit.
    private static void limit(DbFile file, long records) throws ElclException {
        if (records > ElclConfig.maxRecordsPerFile()) {
            throw new ElclException("ELC2208", file.name, ElclConfig.maxRecordsPerFile());
        }
    }

    // The PF member a definition comes from (ELC2247 for an ELCLP one).
    private static SystemData.Member source(ElclSystem system, String library, String member) throws ElclException {
        SystemData.Member source = StoredLibraryService.findMember(StoredLibraryService.find(system, library), member);
        if (!source.type.equals(SystemData.PF)) {
            throw new ElclException("ELC2247", upper(library) + "/" + source.name, SystemData.PF);
        }
        return source;
    }

    // The listing to a spooled file of the file's name in the user's interactive job.
    private static int spool(ElclSystem system, String user, String name, List<String> listing) {
        JobService.Job job = StoredLibraryService.interactiveJob(system, user);
        return ElclServices.spool().create(system, name, job.number(), job.name(), upper(user), listing);
    }

    @Override
    public Outcome create(ElclSystem system, String user, String library, String name, String sourceLibrary, String sourceMember, @Nullable String text)
            throws ElclException {
        String file = upper(name);
        SystemData.Library target;
        SystemData.Member source;
        List<SourceLine> lines;
        synchronized (this) {
            target = StoredLibraryService.find(system, library);
            StoredLibraryService.writable(system, target, user);
            StoredLibraryService.checkName(file, "FILE");
            if (target.files.containsKey(file)) {
                throw new ElclException("ELC0204", file, target.name);
            }
            source = source(system, sourceLibrary, sourceMember);
            lines = List.copyOf(source.lines);
        }
        Dds.Result result = Dds.compileLines(lines);
        ElclMessage end = result.ok() ? ElclMessage.of("ELC2230", file, target.name) : ElclMessage.of("ELC2239", file);
        List<String> listing = Dds.listing(target.name, file, system.nowShort().replace("  ", " "), system.name(), lines, result, end);
        int spooled = spool(system, user, file, listing);
        if (result.ok()) {
            synchronized (this) {
                if (target.files.containsKey(file)) {
                    throw new ElclException("ELC0204", file, target.name);
                }
                String description = text != null ? text : !source.text.isEmpty() ? source.text : result.format().text();
                target.files.put(file, new DbFile(file, description, result.format(), upper(sourceLibrary), source.name, source.version, system.nowShort(),
                        false));
                ElclStore.of(system).changed();
            }
        }
        return new Outcome(result.ok(), 0, List.of(), listing, result.diagnostics(), spooled);
    }

    @Override
    public Outcome change(ElclSystem system, String user, String library, String name, @Nullable String sourceLibrary, @Nullable String sourceMember,
            @Nullable String text) throws ElclException {
        SystemData.Library target;
        DbFile file;
        SystemData.Member source;
        List<SourceLine> lines;
        String fromLibrary;
        synchronized (this) {
            target = StoredLibraryService.find(system, library);
            StoredLibraryService.writable(system, target, user);
            file = find(target, name);
            fromLibrary = sourceLibrary != null ? upper(sourceLibrary) : file.sourceLibrary;
            String fromMember = sourceMember != null ? sourceMember : file.sourceMember;
            if (fromMember.isEmpty() || fromMember.equals(DbFile.NO_SOURCE)) {
                throw new ElclException("ELC2247", DbFile.NO_SOURCE, SystemData.PF);
            }
            source = source(system, fromLibrary, fromMember);
            lines = List.copyOf(source.lines);
        }
        Dds.Result result = Dds.compileLines(lines);
        if (!result.ok()) {
            List<String> listing = Dds.listing(target.name, file.name, system.nowShort().replace("  ", " "), system.name(), lines, result,
                    ElclMessage.of("ELC2246", file.name));
            return new Outcome(false, 0, List.of(), listing, result.diagnostics(), spool(system, user, file.name, listing));
        }
        RecordFormat format = result.format();
        List<String> dropped = new ArrayList<>();
        Map<Long, Object[]> rows;
        synchronized (this) {
            RecordFormat old = file.format;
            for (FieldDef field : old.fields()) {
                FieldDef now = format.field(field.name());
                if (now == null || !now.sameType(field)) {
                    dropped.add(field.name());
                }
            }
            // Each record in the new format: fields kept by name and type, the rest blank.
            rows = new LinkedHashMap<>();
            for (Map.Entry<Long, Object[]> record : file.rows().entrySet()) {
                Object[] values = format.blank();
                for (int i = 0; i < values.length; i++) {
                    FieldDef field = format.fields().get(i);
                    int from = old.index(field.name());
                    if (from >= 0 && old.fields().get(from).sameType(field)) {
                        values[i] = field.convert(record.getValue()[from]);
                    }
                }
                rows.put(record.getKey(), values);
            }
            room(system, file.name, cost((long) rows.size() * format.recordLength()) - cost(file.characters()));
            file.reformat(format, rows);
            file.sourceLibrary = fromLibrary;
            file.sourceMember = source.name;
            file.sourceVersion = source.version;
            if (text != null) {
                file.text = text;
            }
            ElclStore.of(system).changed();
        }
        // Each field dropped is a warning in the listing's messages.
        List<Diagnostic> diagnostics = new ArrayList<>(result.diagnostics());
        dropped.forEach(field -> diagnostics.add(new Diagnostic(-1, ElclMessage.of("ELC2232", field, file.name))));
        Dds.Result listed = new Dds.Result(format, List.copyOf(diagnostics));
        List<String> listing = Dds.listing(target.name, file.name, system.nowShort().replace("  ", " "), system.name(), lines, listed,
                ElclMessage.of("ELC2231", file.name, target.name, rows.size()));
        return new Outcome(true, rows.size(), List.copyOf(dropped), listing, List.copyOf(diagnostics), spool(system, user, file.name, listing));
    }

    @Override
    public synchronized void createFrom(ElclSystem system, String user, String library, String name, RecordFormat format, String text,
            List<Object[]> records, boolean replace) throws ElclException {
        SystemData.Library target = StoredLibraryService.find(system, library);
        StoredLibraryService.writable(system, target, user);
        String file = upper(name);
        StoredLibraryService.checkName(file, "FILE");
        DbFile old = target.files.get(file);
        if (old != null && !replace) {
            throw new ElclException("ELC0204", file, target.name);
        }
        DbFile made = new DbFile(file, text, format, target.name, DbFile.NO_SOURCE, 0, old != null ? old.created : system.nowShort(), false);
        limit(made, records.size());
        room(system, file, cost((long) records.size() * format.recordLength()) - (old != null ? cost(old.characters()) : 0));
        String now = Timestamps.of(system.ticks());
        for (Object[] values : records) {
            made.add(stamped(format, values, now));
        }
        target.files.put(file, made);
        ElclStore.of(system).changed();
    }

    @Override
    public synchronized void delete(ElclSystem system, String user, String library, String name) throws ElclException {
        SystemData.Library target = StoredLibraryService.find(system, library);
        StoredLibraryService.writable(system, target, user);
        DbFile file = find(target, name);
        target.files.remove(file.name);
        ElclStore.of(system).changed();
    }

    @Override
    public synchronized int clear(ElclSystem system, String user, String library, String name) throws ElclException {
        DbFile file = writing(system, new Who(user, true), library, name);
        int removed = file.size();
        file.clear();
        ElclStore.of(system).changed();
        return removed;
    }

    // --- Records ---

    // Blank timestamps take the clock's time now.
    private static Object[] stamped(RecordFormat format, Object[] values, String now) {
        Object[] stamped = values.clone();
        for (int i = 0; i < stamped.length; i++) {
            if (format.fields().get(i).type() == FieldDef.Type.TIME && String.valueOf(stamped[i]).isBlank()) {
                stamped[i] = now;
            }
        }
        return stamped;
    }

    @Override
    public synchronized List<DbRecord> records(ElclSystem system, Who who, String library, String name) throws ElclException {
        return reading(system, who, library, name).records();
    }

    @Override
    public synchronized @Nullable DbRecord record(ElclSystem system, Who who, String library, String name, long rrn) throws ElclException {
        return reading(system, who, library, name).get(rrn);
    }

    @Override
    public synchronized @Nullable DbRecord next(ElclSystem system, Who who, String library, String name, DbRecord.@Nullable Position after)
            throws ElclException {
        return reading(system, who, library, name, after == null).next(after);
    }

    @Override
    public synchronized @Nullable DbRecord previous(ElclSystem system, Who who, String library, String name, DbRecord.@Nullable Position before)
            throws ElclException {
        return reading(system, who, library, name, before == null).previous(before);
    }

    @Override
    public synchronized @Nullable DbRecord chain(ElclSystem system, Who who, String library, String name, Object[] key) throws ElclException {
        return reading(system, who, library, name).chain(key);
    }

    @Override
    public synchronized int position(ElclSystem system, Who who, String library, String name, Object[] key) throws ElclException {
        return reading(system, who, library, name).position(key);
    }

    @Override
    public synchronized DbRecord write(ElclSystem system, Who who, String library, String name, Object[] values) throws ElclException {
        DbFile file = writing(system, who, library, name);
        limit(file, file.size() + 1L);
        room(system, file.name, cost(file.characters() + file.format.recordLength()) - cost(file.characters()));
        DbRecord written = file.add(stamped(file.format, values, Timestamps.of(system.ticks())));
        ElclStore.of(system).changed();
        return written;
    }

    @Override
    public synchronized DbRecord update(ElclSystem system, Who who, String library, String name, long rrn, Object[] values) throws ElclException {
        DbFile file = writing(system, who, library, name);
        DbRecord changed = file.update(rrn, stamped(file.format, values, Timestamps.of(system.ticks())));
        ElclStore.of(system).changed();
        return changed;
    }

    @Override
    public synchronized void delete(ElclSystem system, Who who, String library, String name, long rrn) throws ElclException {
        DbFile file = writing(system, who, library, name);
        if (!file.delete(rrn)) {
            throw new ElclException("ELC2204", file.name);
        }
        ElclStore.of(system).changed();
    }

    @Override
    public synchronized int add(ElclSystem system, Who who, String library, String name, List<Object[]> records, boolean replace) throws ElclException {
        DbFile file = writing(system, who, library, name);
        long after = (replace ? 0 : file.size()) + (long) records.size();
        limit(file, after);
        room(system, file.name, cost(after * file.format.recordLength()) - cost(file.characters()));
        DbFile.Snapshot before = file.snapshot();
        String now = Timestamps.of(system.ticks());
        try {
            if (replace) {
                file.clear();
            }
            for (Object[] values : records) {
                file.add(stamped(file.format, values, now));
            }
        } catch (ElclException e) {
            file.restore(before);
            throw e;
        }
        ElclStore.of(system).changed();
        return records.size();
    }

    // The files a library's member made (CRTPF from it): WRKMBR's changed-since flag for PF members.
    static @Nullable DbFile madeFrom(SystemData.Library library, SystemData.Member member) {
        for (DbFile file : library.files.values()) {
            if (file.sourceLibrary.equals(library.name) && file.sourceMember.equals(member.name)) {
                return file;
            }
        }
        return null;
    }
}
