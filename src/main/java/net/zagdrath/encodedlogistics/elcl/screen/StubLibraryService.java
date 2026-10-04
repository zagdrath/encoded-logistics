/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.compile.Listing;

// STUB: waiting on elcl.store (libraries in the network's saved data, storage cost, folder sync). In memory per system,
// with the real rules: ELSYS (the shipped samples, docs/elcl/examples) read-only, ELGPL open to everyone, a library's
// owner may change it and others only when its authority is *CHANGE, one editor per member at a time.
final class StubLibraryService implements LibraryService {
    public static final String SYSTEM_LIBRARY = "ELSYS", GENERAL = "ELGPL", SYSTEM_OWNER = "QSYS";
    private static final Pattern DESCRIPTION = Pattern.compile("/\\*\\s*\\S+\\s+-\\s+(.*?)\\s*\\*/");

    private static final class Member {
        final String name;
        String text;
        List<SourceLine> lines;
        int version;
        String updated;
        @Nullable String locker;

        Member(String name, String text, List<SourceLine> lines, String updated) {
            this.name = name;
            this.text = text;
            this.lines = lines;
            this.updated = updated;
        }
    }

    private record Program(String name, String sourceLibrary, String sourceMember, int sourceVersion) {}

    private static final class Library {
        final String name, type, owner, created;
        String text, authority;
        final Map<String, Member> members = new TreeMap<>();
        final Map<String, Program> programs = new TreeMap<>();

        Library(String name, String type, String text, String owner, String authority, String created) {
            this.name = name;
            this.type = type;
            this.text = text;
            this.owner = owner;
            this.authority = authority;
            this.created = created;
        }
    }

    private final ElclServices.Store<Map<String, Library>> store = new ElclServices.Store<>(StubLibraryService::initial);

    // STUB: waiting on elcl.store
    private static Map<String, Library> initial(ElclSystem system) {
        Map<String, Library> libraries = new TreeMap<>();
        Library samples = new Library(SYSTEM_LIBRARY, "*SYS", "System library - samples (read-only)", SYSTEM_OWNER, "*USE", "Day 1  06:00");
        for (String name : resource("members.txt").lines().map(String::trim).filter(line -> !line.isEmpty()).toList()) {
            List<String> texts = SourceLine.split(resource(name + ".elclp"));
            samples.members.put(name, new Member(name, description(texts), SourceLine.number(texts, 0), "Day 1  06:00"));
        }
        libraries.put(SYSTEM_LIBRARY, samples);
        libraries.put(GENERAL, new Library(GENERAL, "*PROD", "General purpose library", SYSTEM_OWNER, "*CHANGE", "Day 1  06:00"));
        return libraries;
    }

    private static String resource(String name) {
        try (InputStream in = EncodedLogistics.class.getResourceAsStream("/data/encodedlogistics/elcl/ELSYS/" + name)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    // A member's text from its first comment: "/* RESTOCK - keep an item stocked ... */".
    static String description(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = DESCRIPTION.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private static String upper(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private static void checkName(String name, String keyword) throws ElclException {
        if (!name.matches("[A-Z][A-Z0-9_@#$]{0,9}")) {
            throw new ElclException("ELC0103", name, keyword);
        }
    }

    private Library find(ElclSystem system, String name) throws ElclException {
        Library library = store.of(system).get(upper(name));
        if (library == null) {
            throw new ElclException("ELC0201", upper(name));
        }
        return library;
    }

    private Member findMember(Library library, String name) throws ElclException {
        Member member = library.members.get(upper(name));
        if (member == null) {
            throw new ElclException("ELC0202", upper(name), library.name);
        }
        return member;
    }

    // Changing what's in a library: never ELSYS; its owner, or anyone when its authority is *CHANGE.
    private static void writable(Library library, String user) throws ElclException {
        if (library.type.equals("*SYS")) {
            throw new ElclException("ELC0205", library.name);
        }
        if (!library.owner.equalsIgnoreCase(user) && !library.authority.equals("*CHANGE")) {
            throw new ElclException("ELC0401", user, "*CHANGE");
        }
    }

    private static long size(Library library) {
        long size = 0;
        for (Member member : library.members.values()) {
            for (SourceLine line : member.lines) {
                size += line.text().length() + 1;
            }
        }
        return size;
    }

    private static LibraryService.Library view(Library library) {
        return new LibraryService.Library(library.name, library.type, library.text, library.owner, library.authority, library.members.size(), size(library),
                library.created);
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized List<LibraryService.Library> libraries(ElclSystem system) {
        return store.of(system).values().stream().map(StubLibraryService::view).toList();
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized LibraryService.Library library(ElclSystem system, String name) throws ElclException {
        return view(find(system, name));
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized void createLibrary(ElclSystem system, String user, String name, String type, String text) throws ElclException {
        String lib = upper(name);
        checkName(lib, "LIB");
        if (store.of(system).containsKey(lib)) {
            throw new ElclException("ELC0204", lib, SYSTEM_OWNER);
        }
        store.of(system).put(lib, new Library(lib, type, text, upper(user), "*USE", system.nowShort()));
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized void changeLibrary(ElclSystem system, String user, String name, @Nullable String text, @Nullable String authority)
            throws ElclException {
        Library library = find(system, name);
        if (library.type.equals("*SYS")) {
            throw new ElclException("ELC0205", library.name);
        }
        if (!library.owner.equalsIgnoreCase(user) && !library.owner.equals(SYSTEM_OWNER)) {
            throw new ElclException("ELC0401", user, "*OWNER");
        }
        if (text != null) {
            library.text = text;
        }
        if (authority != null) {
            library.authority = authority;
        }
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized void deleteLibrary(ElclSystem system, String user, String name) throws ElclException {
        Library library = find(system, name);
        if (library.type.equals("*SYS") || library.name.equals(GENERAL)) {
            throw new ElclException("ELC0205", library.name);
        }
        if (!library.owner.equalsIgnoreCase(user)) {
            throw new ElclException("ELC0401", user, "*OWNER");
        }
        store.of(system).remove(library.name);
    }

    private static LibraryService.Member view(Library library, Member member) {
        Program program = null;
        for (Program candidate : library.programs.values()) {
            if (candidate.sourceLibrary().equals(library.name) && candidate.sourceMember().equals(member.name)) {
                program = candidate;
            }
        }
        boolean changed = program != null && program.sourceVersion() != member.version;
        return new LibraryService.Member(library.name, member.name, "ELCLP", member.text, changed, program != null, member.lines.size(), member.updated);
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized List<LibraryService.Member> members(ElclSystem system, String library) throws ElclException {
        Library lib = find(system, library);
        return lib.members.values().stream().map(member -> view(lib, member)).toList();
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized LibraryService.Member member(ElclSystem system, String library, String name) throws ElclException {
        Library lib = find(system, library);
        return view(lib, findMember(lib, name));
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized List<SourceLine> source(ElclSystem system, String library, String member) throws ElclException {
        return List.copyOf(findMember(find(system, library), member).lines);
    }

    // STUB: waiting on elcl.store (storage cost: ELC0207 when the network is full)
    @Override
    public synchronized void save(ElclSystem system, String user, String library, String member, List<SourceLine> lines) throws ElclException {
        Library lib = find(system, library);
        writable(lib, user);
        Member target = findMember(lib, member);
        if (target.locker != null && !target.locker.equalsIgnoreCase(user)) {
            throw new ElclException("ELC0208", target.name);
        }
        if (!target.lines.equals(lines)) {
            target.lines = List.copyOf(lines);
            target.version++;
            target.updated = system.nowShort();
        }
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized void createMember(ElclSystem system, String user, String library, String member, String text) throws ElclException {
        Library lib = find(system, library);
        writable(lib, user);
        String name = upper(member);
        checkName(name, "MBR");
        if (lib.members.containsKey(name)) {
            throw new ElclException("ELC0204", name, lib.name);
        }
        lib.members.put(name, new Member(name, text, List.of(), system.nowShort()));
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized void copyMember(ElclSystem system, String user, String fromLibrary, String fromMember, String toLibrary, String toMember)
            throws ElclException {
        Member from = findMember(find(system, fromLibrary), fromMember);
        Library to = find(system, toLibrary);
        writable(to, user);
        String name = upper(toMember);
        checkName(name, "TO");
        if (to.members.containsKey(name)) {
            throw new ElclException("ELC0204", name, to.name);
        }
        to.members.put(name, new Member(name, from.text, List.copyOf(from.lines), system.nowShort()));
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized void renameMember(ElclSystem system, String user, String library, String member, String newName) throws ElclException {
        Library lib = find(system, library);
        writable(lib, user);
        Member old = findMember(lib, member);
        String name = upper(newName);
        checkName(name, "NEWNAME");
        if (lib.members.containsKey(name)) {
            throw new ElclException("ELC0204", name, lib.name);
        }
        lib.members.remove(old.name);
        Member renamed = new Member(name, old.text, old.lines, old.updated);
        renamed.version = old.version;
        lib.members.put(name, renamed);
    }

    // STUB: waiting on elcl.store (folder sync moves the file to .deleted/)
    @Override
    public synchronized void deleteMember(ElclSystem system, String user, String library, String member) throws ElclException {
        Library lib = find(system, library);
        writable(lib, user);
        Member old = findMember(lib, member);
        if (old.locker != null && !old.locker.equalsIgnoreCase(user)) {
            throw new ElclException("ELC0208", old.name);
        }
        lib.members.remove(old.name);
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized void lock(ElclSystem system, String user, String library, String member) throws ElclException {
        Member target = findMember(find(system, library), member);
        if (target.locker != null && !target.locker.equalsIgnoreCase(user)) {
            throw new ElclException("ELC0208", target.name);
        }
        target.locker = upper(user);
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized void unlock(ElclSystem system, String user, String library, String member) {
        Library lib = store.of(system).get(upper(library));
        Member target = lib != null ? lib.members.get(upper(member)) : null;
        if (target != null && user.equalsIgnoreCase(target.locker)) {
            target.locker = null;
        }
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized void unlockAll(ElclSystem system, String user) {
        for (Library library : store.of(system).values()) {
            for (Member member : library.members.values()) {
                if (user.equalsIgnoreCase(member.locker)) {
                    member.locker = null;
                }
            }
        }
    }

    // STUB: waiting on elcl.store (the program object kept for elcl.vm)
    @Override
    public CompileOutcome compile(ElclSystem system, String user, String library, String program, String sourceLibrary, String sourceMember)
            throws ElclException {
        Library target;
        Member source;
        List<SourceLine> lines;
        int version;
        synchronized (this) {
            target = find(system, library);
            writable(target, user);
            source = findMember(find(system, sourceLibrary), sourceMember);
            lines = List.copyOf(source.lines);
            version = source.version;
        }
        String name = upper(program);
        Compiler.Result result = Compiler.compile(lines);
        List<String> listing = Listing.build(target.name, name, system.nowShort().replace("  ", " "), system.name(), lines, result);
        JobService.Job job = interactiveJob(system, user);
        int file = ElclServices.spool().create(system, name, job.number(), job.name(), upper(user), listing);
        if (result.ok()) {
            synchronized (this) {
                target.programs.put(name, new Program(name, upper(sourceLibrary), source.name, version));
            }
        }
        return new CompileOutcome(result.ok(), listing, result.diagnostics(), file);
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

    // STUB: waiting on elcl.store
    @Override
    public synchronized List<String> programs(ElclSystem system, String library) throws ElclException {
        return new ArrayList<>(find(system, library).programs.keySet());
    }

    // STUB: waiting on elcl.store
    @Override
    public synchronized void deleteProgram(ElclSystem system, String user, String library, String program) throws ElclException {
        Library lib = find(system, library);
        writable(lib, user);
        if (lib.programs.remove(upper(program)) == null) {
            throw new ElclException("ELC0203", upper(program), lib.name);
        }
    }
}
