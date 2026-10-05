/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.compile.CompiledProgram;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.compile.FileResolver;
import net.zagdrath.encodedlogistics.elcl.compile.Listing;
import net.zagdrath.encodedlogistics.elcl.db.DbFile;
import net.zagdrath.encodedlogistics.elcl.device.LibraryImage;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.screen.LibraryService;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Libraries, members and programs in the system's saved data (OS.md 2-3, 6). ELSYS (the shipped samples) is
// read-only; ELGPL is open to everyone; a library's owner may change what's in it and others only when its authority
// is *CHANGE; one editor per member at a time. Source members take network storage (a byte of drive space per
// charsPerStorageByte characters): a save, create or copy that needs more than the drives have free is ELC0207.
// Members are ELCLP (programs' source, CRTELPGM) or PF (physical files' definitions, CRTPF: StoredFileService, which
// shares these rules); a program keeps the formats of the files it declares as it was compiled.
public final class StoredLibraryService implements LibraryService {
    // Where the drives' bytes used and in all come from (the game tests can swap it).
    public interface StorageMeter {
        long[] bytes(ElclSystem system);
    }

    public static final StorageMeter NETWORK = system -> {
        NetworkStorage storage = ControllerStructures.sharedStorageOf(system.server(), system.network(), false);
        return storage == null ? new long[] { 0, 0 } : storage.hotBytes();
    };

    private StorageMeter meter = NETWORK;
    // Folder sync's hooks: a member saved, deleted (the sync package sets them).
    private MemberListener listener = MemberListener.NONE;

    public interface MemberListener {
        MemberListener NONE = new MemberListener() {};

        default void saved(ElclSystem system, String library, String member, List<SourceLine> lines) {}

        default void deleted(ElclSystem system, String library, String member) {}

        default void libraryCreated(ElclSystem system, String library) {}
    }

    public void setMeter(StorageMeter meter) {
        this.meter = meter;
    }

    public void setListener(MemberListener listener) {
        this.listener = listener;
    }

    private static String upper(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }

    static void checkName(String name, String keyword) throws ElclException {
        if (!name.matches("[A-Z][A-Z0-9_@#$]{0,9}")) {
            throw new ElclException("ELC0103", name, keyword);
        }
    }

    private static Map<String, SystemData.Library> stored(ElclSystem system) {
        return ElclStore.of(system).libraries;
    }

    static SystemData.Library find(ElclSystem system, String name) throws ElclException {
        SystemData.Library library = stored(system).get(upper(name));
        if (library == null) {
            throw new ElclException("ELC0201", upper(name));
        }
        return library;
    }

    static SystemData.Member findMember(SystemData.Library library, String name) throws ElclException {
        SystemData.Member member = library.members.get(upper(name));
        if (member == null) {
            throw new ElclException("ELC0202", upper(name), library.name);
        }
        return member;
    }

    // Changing what's in a library: never ELSYS; its owner, *SECOFR, or anyone when its authority is *CHANGE.
    static void writable(ElclSystem system, SystemData.Library library, String user) throws ElclException {
        if (library.system()) {
            throw new ElclException("ELC0205", library.name);
        }
        if (!library.owner.equalsIgnoreCase(user) && !library.authority.equals("*CHANGE") && !ElclServices.users().securityOfficer(system, user)) {
            throw new ElclException("ELC0401", user, "*CHANGE");
        }
    }

    // Whether the user may change what's in the library (the editor opens read-only otherwise).
    public static boolean canChange(ElclSystem system, LibraryService.Library library, String user) {
        return !library.type().equals("*SYS")
                && (library.owner().equalsIgnoreCase(user) || library.authority().equals("*CHANGE") || ElclServices.users().securityOfficer(system, user));
    }

    // ELC0207 when the drives can't take `more` bytes on top of what's stored and the members already take.
    void room(ElclSystem system, String member, long more) throws ElclException {
        if (more <= 0) {
            return;
        }
        long[] bytes = meter.bytes(system);
        long members = ElclStore.of(system).storageBytes(ElclConfig.charsPerStorageByte());
        if (bytes[0] + members + more > bytes[1]) {
            throw new ElclException("ELC0207", member);
        }
    }

    // What members take of the network's storage, in bytes (DSPNETSTS, RTVSTGSTS).
    public static long storageBytes(ElclSystem system) {
        synchronized (ElclServices.libraries()) {
            return ElclStore.of(system).storageBytes(ElclConfig.charsPerStorageByte());
        }
    }

    private static long cost(List<SourceLine> lines) {
        long characters = 0;
        for (SourceLine line : lines) {
            characters += line.text().length();
        }
        return SystemData.cost(characters, ElclConfig.charsPerStorageByte());
    }

    private static long size(SystemData.Library library) {
        long size = 0;
        for (SystemData.Member member : library.members.values()) {
            for (SourceLine line : member.lines) {
                size += line.text().length() + 1;
            }
        }
        return size;
    }

    private static LibraryService.Library view(SystemData.Library library) {
        return new LibraryService.Library(library.name, library.type, library.text, library.owner, library.authority, library.members.size(), size(library),
                library.created);
    }

    @Override
    public synchronized List<LibraryService.Library> libraries(ElclSystem system) {
        return stored(system).values().stream().map(StoredLibraryService::view).toList();
    }

    @Override
    public synchronized LibraryService.Library library(ElclSystem system, String name) throws ElclException {
        return view(find(system, name));
    }

    @Override
    public synchronized void createLibrary(ElclSystem system, String user, String name, String type, String text) throws ElclException {
        String lib = upper(name);
        checkName(lib, "LIB");
        if (stored(system).containsKey(lib)) {
            throw new ElclException("ELC0204", lib, SystemData.SYSTEM_OWNER);
        }
        stored(system).put(lib, new SystemData.Library(lib, type, text, upper(user), "*USE", system.nowShort()));
        ElclStore.of(system).changed();
        listener.libraryCreated(system, lib);
    }

    @Override
    public synchronized void changeLibrary(ElclSystem system, String user, String name, @Nullable String text, @Nullable String authority)
            throws ElclException {
        SystemData.Library library = find(system, name);
        if (library.system()) {
            throw new ElclException("ELC0205", library.name);
        }
        if (!library.owner.equalsIgnoreCase(user) && !library.owner.equals(SystemData.SYSTEM_OWNER) && !ElclServices.users().securityOfficer(system, user)) {
            throw new ElclException("ELC0401", user, "*OWNER");
        }
        if (text != null) {
            library.text = text;
        }
        if (authority != null) {
            library.authority = authority;
        }
        ElclStore.of(system).changed();
    }

    @Override
    public synchronized void deleteLibrary(ElclSystem system, String user, String name) throws ElclException {
        SystemData.Library library = find(system, name);
        if (library.system() || library.name.equals(SystemData.GENERAL)) {
            throw new ElclException("ELC0205", library.name);
        }
        if (!library.owner.equalsIgnoreCase(user) && !ElclServices.users().securityOfficer(system, user)) {
            throw new ElclException("ELC0401", user, "*OWNER");
        }
        for (String member : List.copyOf(library.members.keySet())) {
            listener.deleted(system, library.name, member);
        }
        stored(system).remove(library.name);
        ElclStore.of(system).changed();
    }

    // A member as the screens show it: changed since its program (a PF member: its file) was made from it.
    private static LibraryService.Member view(SystemData.Library library, SystemData.Member member) {
        if (member.type.equals(SystemData.PF)) {
            DbFile file = StoredFileService.madeFrom(library, member);
            boolean changed = file != null && file.sourceVersion != member.version;
            return new LibraryService.Member(library.name, member.name, member.type, member.text, changed, file != null, member.lines.size(),
                    member.updated);
        }
        SystemData.Program program = null;
        for (SystemData.Program candidate : library.programs.values()) {
            if (candidate.sourceLibrary.equals(library.name) && candidate.sourceMember.equals(member.name)) {
                program = candidate;
            }
        }
        boolean changed = program != null && program.sourceVersion != member.version;
        return new LibraryService.Member(library.name, member.name, member.type, member.text, changed, program != null, member.lines.size(),
                member.updated);
    }

    @Override
    public synchronized List<LibraryService.Member> members(ElclSystem system, String library) throws ElclException {
        SystemData.Library lib = find(system, library);
        return lib.members.values().stream().map(member -> view(lib, member)).toList();
    }

    @Override
    public synchronized LibraryService.Member member(ElclSystem system, String library, String name) throws ElclException {
        SystemData.Library lib = find(system, library);
        return view(lib, findMember(lib, name));
    }

    @Override
    public synchronized List<SourceLine> source(ElclSystem system, String library, String member) throws ElclException {
        return List.copyOf(findMember(find(system, library), member).lines);
    }

    @Override
    public synchronized void save(ElclSystem system, String user, String library, String member, List<SourceLine> lines) throws ElclException {
        SystemData.Library lib = find(system, library);
        writable(system, lib, user);
        SystemData.Member target = findMember(lib, member);
        if (target.locker != null && !target.locker.equalsIgnoreCase(user)) {
            throw new ElclException("ELC0208", target.name);
        }
        lineLimit(lines);
        if (!target.lines.equals(lines)) {
            room(system, target.name, cost(lines) - cost(target.lines));
            target.lines = List.copyOf(lines);
            target.version++;
            target.updated = system.nowShort();
            ElclStore.of(system).changed();
            listener.saved(system, lib.name, target.name, target.lines);
        }
    }

    // A member may have at most maxSourceLines lines (ELC0004), however it comes: saved, synced, copied, restored.
    private static void lineLimit(List<SourceLine> lines) throws ElclException {
        if (lines.size() > ElclConfig.maxSourceLines()) {
            throw new ElclException("ELC0004", lines.size());
        }
    }

    // A library from outside (folder sync: a new folder), owned by the system and open to everyone (*CHANGE).
    public synchronized SystemData.Library makeLibrary(ElclSystem system, String library) {
        SystemData.Library lib = stored(system).get(library);
        if (lib == null) {
            lib = new SystemData.Library(library, "*PROD", "", SystemData.SYSTEM_OWNER, "*CHANGE", system.nowShort());
            stored(system).put(library, lib);
            ElclStore.of(system).changed();
        }
        return lib;
    }

    // A member from outside (folder sync): made if new (of the type given), replaced if changed. No authority or lock
    // checks; storage is.
    public synchronized void put(ElclSystem system, String library, String member, String type, List<SourceLine> lines) throws ElclException {
        SystemData.Library lib = makeLibrary(system, library);
        if (lib.system()) {
            throw new ElclException("ELC0205", library);
        }
        lineLimit(lines);
        SystemData.Member target = lib.members.get(member);
        if (target == null) {
            room(system, member, cost(lines));
            List<String> texts = SourceLine.texts(lines);
            String text = type.equals(SystemData.PF) ? SystemData.ddsDescription(texts) : SystemData.description(texts);
            lib.members.put(member, new SystemData.Member(member, type, text, List.copyOf(lines), 1, system.nowShort()));
        } else if (!target.lines.equals(lines)) {
            room(system, member, cost(lines) - cost(target.lines));
            target.lines = List.copyOf(lines);
            target.version++;
            target.updated = system.nowShort();
        }
        ElclStore.of(system).changed();
    }

    @Override
    public synchronized void createMember(ElclSystem system, String user, String library, String member, String text, String type)
            throws ElclException {
        SystemData.Library lib = find(system, library);
        writable(system, lib, user);
        String name = upper(member), sourceType = upper(type);
        checkName(name, "MBR");
        if (!sourceType.equals(SystemData.ELCLP) && !sourceType.equals(SystemData.PF)) {
            throw new ElclException("ELC0103", sourceType, "SRCTYPE");
        }
        if (lib.members.containsKey(name)) {
            throw new ElclException("ELC0204", name, lib.name);
        }
        lib.members.put(name, new SystemData.Member(name, sourceType, text, List.of(), 0, system.nowShort()));
        ElclStore.of(system).changed();
        listener.saved(system, lib.name, name, List.of());
    }

    @Override
    public synchronized void copyMember(ElclSystem system, String user, String fromLibrary, String fromMember, String toLibrary, String toMember)
            throws ElclException {
        SystemData.Member from = findMember(find(system, fromLibrary), fromMember);
        SystemData.Library to = find(system, toLibrary);
        writable(system, to, user);
        String name = upper(toMember);
        checkName(name, "TO");
        if (to.members.containsKey(name)) {
            throw new ElclException("ELC0204", name, to.name);
        }
        lineLimit(from.lines);
        room(system, name, cost(from.lines));
        to.members.put(name, new SystemData.Member(name, from.type, from.text, List.copyOf(from.lines), 0, system.nowShort()));
        ElclStore.of(system).changed();
        listener.saved(system, to.name, name, from.lines);
    }

    @Override
    public synchronized void renameMember(ElclSystem system, String user, String library, String member, String newName) throws ElclException {
        SystemData.Library lib = find(system, library);
        writable(system, lib, user);
        SystemData.Member old = findMember(lib, member);
        String name = upper(newName);
        checkName(name, "NEWNAME");
        if (lib.members.containsKey(name)) {
            throw new ElclException("ELC0204", name, lib.name);
        }
        lib.members.remove(old.name);
        lib.members.put(name, new SystemData.Member(name, old.type, old.text, old.lines, old.version, old.updated));
        ElclStore.of(system).changed();
        listener.deleted(system, lib.name, old.name);
        listener.saved(system, lib.name, name, old.lines);
    }

    @Override
    public synchronized void deleteMember(ElclSystem system, String user, String library, String member) throws ElclException {
        SystemData.Library lib = find(system, library);
        writable(system, lib, user);
        SystemData.Member old = findMember(lib, member);
        if (old.locker != null && !old.locker.equalsIgnoreCase(user)) {
            throw new ElclException("ELC0208", old.name);
        }
        lib.members.remove(old.name);
        ElclStore.of(system).changed();
        listener.deleted(system, lib.name, old.name);
    }

    @Override
    public synchronized void lock(ElclSystem system, String user, String library, String member) throws ElclException {
        SystemData.Member target = findMember(find(system, library), member);
        if (target.locker != null && !target.locker.equalsIgnoreCase(user)) {
            throw new ElclException("ELC0208", target.name);
        }
        target.locker = upper(user);
    }

    @Override
    public synchronized void unlock(ElclSystem system, String user, String library, String member) {
        SystemData.Library lib = stored(system).get(upper(library));
        SystemData.Member target = lib != null ? lib.members.get(upper(member)) : null;
        if (target != null && user.equalsIgnoreCase(target.locker)) {
            target.locker = null;
        }
    }

    @Override
    public synchronized void unlockAll(ElclSystem system, String user) {
        for (SystemData.Library library : stored(system).values()) {
            for (SystemData.Member member : library.members.values()) {
                if (user.equalsIgnoreCase(member.locker)) {
                    member.locker = null;
                }
            }
        }
    }

    @Override
    public CompileOutcome compile(ElclSystem system, String user, String library, String program, String sourceLibrary, String sourceMember,
            Compiler.Target compileTarget) throws ElclException {
        SystemData.Library target;
        SystemData.Member source;
        List<SourceLine> lines;
        int version;
        synchronized (this) {
            target = find(system, library);
            writable(system, target, user);
            source = findMember(find(system, sourceLibrary), sourceMember);
            if (!source.type.equals(SystemData.ELCLP)) {
                throw new ElclException("ELC2247", upper(sourceLibrary) + "/" + source.name, SystemData.ELCLP);
            }
            lines = List.copyOf(source.lines);
            version = source.version;
        }
        String name = upper(program);
        Compiler.Result result = Compiler.compile(lines, files(system, user), compileTarget);
        List<String> listing = Listing.build(target.name, name, system.nowShort().replace("  ", " "), system.name(), lines, result);
        JobService.Job job = interactiveJob(system, user);
        int file = ElclServices.spool().create(system, name, job.number(), job.name(), upper(user), listing);
        if (result.ok()) {
            synchronized (this) {
                target.programs.put(name, new SystemData.Program(name, upper(sourceLibrary), source.name, version, system.nowShort(), lines,
                        SystemData.saved(result.formats()), compileTarget.plc() ? "*PLC" : "*JOB"));
                ElclStore.of(system).changed();
            }
        }
        return new CompileOutcome(result.ok(), listing, result.diagnostics(), file);
    }

    // The system's files as DCLF finds them at compile time: *LIBL on the user's library list.
    static FileResolver files(ElclSystem system, String user) {
        return (library, file) -> {
            try {
                String[] found = ElclServices.files().resolve(system, user, library, file);
                return ElclServices.files().format(system, found[0], found[1]);
            } catch (ElclException e) {
                return null;
            }
        };
    }

    // The user's interactive job (whose spooled files a compile's listing goes with).
    static JobService.Job interactiveJob(ElclSystem system, String user) {
        for (JobService.Job job : ElclServices.jobs().jobs(system)) {
            if (job.type().equals("INT") && job.user().equalsIgnoreCase(user)) {
                return job;
            }
        }
        return new JobService.Job("000000", "QINTER", upper(user), "INT", "", "*ACTIVE", 0, 5, false);
    }

    @Override
    public synchronized List<String> programs(ElclSystem system, String library) throws ElclException {
        return new ArrayList<>(find(system, library).programs.keySet());
    }

    @Override
    public synchronized CompiledProgram program(ElclSystem system, String library, String program) throws ElclException {
        SystemData.Library lib = find(system, library);
        SystemData.Program found = lib.programs.get(upper(program));
        CompiledProgram compiled = found != null ? found.compiled() : null;
        if (compiled == null) {
            throw new ElclException("ELC0203", upper(program), lib.name);
        }
        return compiled;
    }

    @Override
    public synchronized LibraryImage image(ElclSystem system, String library) throws ElclException {
        SystemData.Library lib = find(system, library);
        List<LibraryImage.MemberImage> members = new ArrayList<>();
        lib.members.values().forEach(m -> members.add(new LibraryImage.MemberImage(m.name, m.type, m.text, List.copyOf(m.lines))));
        List<LibraryImage.ProgramImage> programs = new ArrayList<>();
        lib.programs.values().forEach(p -> programs.add(new LibraryImage.ProgramImage(p.name, p.sourceMember, p.source, p.files, p.target)));
        // Its files and their records (not ELSYS's system files: those are the network's own data).
        List<LibraryImage.FileImage> files = new ArrayList<>();
        lib.files.values().stream().filter(file -> !file.system).forEach(file -> files.add(LibraryImage.FileImage.of(file)));
        return new LibraryImage(lib.name, lib.system() ? "*PROD" : lib.type, lib.text, system.name(), system.nowShort(), List.copyOf(members),
                List.copyOf(programs), List.copyOf(files));
    }

    @Override
    public synchronized void restore(ElclSystem system, String user, LibraryImage image) throws ElclException {
        String name = upper(image.library());
        checkName(name, "LIB");
        SystemData.Library lib = stored(system).get(name);
        if (lib != null) {
            writable(system, lib, user);
        }
        // What it'll take of the network's storage, less what the library's members and files took: they're all replaced.
        long more = 0;
        for (LibraryImage.MemberImage member : image.members()) {
            lineLimit(member.lines());
            more += cost(member.lines());
        }
        List<DbFile> files = new ArrayList<>();
        for (LibraryImage.FileImage saved : image.files()) {
            DbFile file = saved.file();
            if (file != null) {
                files.add(file);
                more += SystemData.cost(file.characters(), ElclConfig.charsPerStorageByte());
            }
        }
        if (lib != null) {
            for (SystemData.Member old : lib.members.values()) {
                more -= cost(old.lines);
            }
            for (DbFile old : lib.files.values()) {
                more -= SystemData.cost(old.characters(), ElclConfig.charsPerStorageByte());
            }
        }
        room(system, name, more);
        if (lib == null) {
            lib = new SystemData.Library(name, image.type().equals("*SYS") ? "*PROD" : image.type(), image.text(), upper(user), "*USE", system.nowShort());
            stored(system).put(name, lib);
            listener.libraryCreated(system, name);
        }
        // Its members and programs become the diskette's: those not on it go.
        Set<String> kept = new HashSet<>();
        image.members().forEach(member -> kept.add(upper(member.name())));
        for (String mbr : List.copyOf(lib.members.keySet())) {
            if (!kept.contains(mbr)) {
                lib.members.remove(mbr);
                listener.deleted(system, name, mbr);
            }
        }
        lib.programs.clear();
        for (LibraryImage.MemberImage member : image.members()) {
            String mbr = upper(member.name());
            SystemData.Member old = lib.members.get(mbr);
            lib.members.put(mbr, new SystemData.Member(mbr, member.type(), member.text(), List.copyOf(member.lines()), old != null ? old.version + 1 : 0,
                    system.nowShort()));
            listener.saved(system, name, mbr, member.lines());
        }
        for (LibraryImage.ProgramImage program : image.programs()) {
            SystemData.Member source = lib.members.get(upper(program.sourceMember()));
            String pgm = upper(program.name());
            lib.programs.put(pgm, new SystemData.Program(pgm, name, upper(program.sourceMember()), source != null ? source.version : 0, system.nowShort(),
                    program.source(), program.files(), program.target()));
        }
        // Its files become the diskette's too, each made from a member here as its definition now is.
        lib.files.clear();
        for (DbFile file : files) {
            SystemData.Member source = file.sourceLibrary.equals(name) ? lib.members.get(file.sourceMember) : null;
            if (source != null) {
                file.sourceVersion = source.version;
            }
            lib.files.put(file.name, file);
        }
        ElclStore.of(system).changed();
    }

    @Override
    public synchronized List<SourceLine> programSource(ElclSystem system, String library, String program) throws ElclException {
        SystemData.Library lib = find(system, library);
        SystemData.Program found = lib.programs.get(upper(program));
        if (found == null) {
            throw new ElclException("ELC0203", upper(program), lib.name);
        }
        return found.source;
    }

    @Override
    public synchronized Map<String, String> programFiles(ElclSystem system, String library, String program) throws ElclException {
        SystemData.Library lib = find(system, library);
        SystemData.Program found = lib.programs.get(upper(program));
        if (found == null) {
            throw new ElclException("ELC0203", upper(program), lib.name);
        }
        return found.files;
    }

    @Override
    public synchronized String programTarget(ElclSystem system, String library, String program) throws ElclException {
        SystemData.Library lib = find(system, library);
        SystemData.Program found = lib.programs.get(upper(program));
        if (found == null) {
            throw new ElclException("ELC0203", upper(program), lib.name);
        }
        return found.target;
    }

    @Override
    public synchronized void deleteProgram(ElclSystem system, String user, String library, String program) throws ElclException {
        SystemData.Library lib = find(system, library);
        writable(system, lib, user);
        if (lib.programs.remove(upper(program)) == null) {
            throw new ElclException("ELC0203", upper(program), lib.name);
        }
        ElclStore.of(system).changed();
    }
}
