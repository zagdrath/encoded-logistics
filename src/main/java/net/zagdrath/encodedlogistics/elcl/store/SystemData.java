/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.compile.CompiledProgram;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.compile.FileResolver;
import net.zagdrath.encodedlogistics.elcl.db.DbFile;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.db.SystemFiles;
import net.zagdrath.encodedlogistics.elcl.screen.MessageService;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;

// One system's saved data (OS.md 1-3): its libraries (members with their type, sequence numbers and change dates,
// programs with their source member, compile date and the file formats they were compiled with, physical files with
// their records), each user's message queue, its spooled files and its system values. ELSYS is never saved: it's
// rebuilt from the bundled examples (and its live system files) whenever a system is made or loaded. The later parts'
// data (device names, user profiles, jobs, schedule entries, triggers, the crafting job history) is kept here too, as
// sections of their own.
public final class SystemData {
    public static final String SYSTEM_LIBRARY = "ELSYS", GENERAL = "ELGPL", SYSTEM_OWNER = "QSYS", SHIPPED = "Day 1  06:00";
    // Source member types: ELCL programs, physical file definitions.
    public static final String ELCLP = "ELCLP", PF = "PF";
    private static final Pattern DESCRIPTION = Pattern.compile("/\\*\\s*\\S+\\s+-\\s+(.*?)\\s*\\*/");
    // A PF member's text from its first comment line: "A* ITEMHIST - Item counts logged by LOGITEMS".
    private static final Pattern DDS_DESCRIPTION = Pattern.compile("^\\s*A?\\*\\s*\\S+\\s+-\\s+(.*?)\\s*$", Pattern.CASE_INSENSITIVE);

    public static final class Library {
        public final String name, type, owner, created;
        public String text, authority;
        public final Map<String, Member> members = new TreeMap<>();
        public final Map<String, Program> programs = new TreeMap<>();
        public final Map<String, DbFile> files = new TreeMap<>();

        public Library(String name, String type, String text, String owner, String authority, String created) {
            this.name = name;
            this.type = type;
            this.text = text;
            this.owner = owner;
            this.authority = authority;
            this.created = created;
        }

        public boolean system() {
            return type.equals("*SYS");
        }
    }

    public static final class Member {
        public final String name;
        // ELCLP (a program's source) or PF (a physical file's definition).
        public final String type;
        public String text;
        public List<SourceLine> lines;
        // Goes up with every saved change (a program compiled from an older one is "changed since compile").
        public int version;
        public String updated;
        // Who is editing it (not saved: an edit doesn't survive a restart).
        public @Nullable String locker;

        public Member(String name, String text, List<SourceLine> lines, int version, String updated) {
            this(name, ELCLP, text, lines, version, updated);
        }

        public Member(String name, String type, String text, List<SourceLine> lines, int version, String updated) {
            this.name = name;
            this.type = type;
            this.text = text;
            this.lines = lines;
            this.version = version;
            this.updated = updated;
        }

        // Its characters: what it costs in network storage (OS.md 3).
        public long characters() {
            long count = 0;
            for (SourceLine line : lines) {
                count += line.text().length();
            }
            return count;
        }
    }

    // A program: its source as compiled (so it can be compiled again on load, the same), where that came from and when,
    // and the record formats of the files it declares (DCLF), as it was compiled with them - by "LIB/FILE" as written,
    // each RecordFormat.save()'s text.
    public static final class Program {
        public final String name, sourceLibrary, sourceMember, compiled;
        public final int sourceVersion;
        public final List<SourceLine> source;
        public final Map<String, String> files;
        private @Nullable CompiledProgram program;

        public Program(String name, String sourceLibrary, String sourceMember, int sourceVersion, String compiled, List<SourceLine> source) {
            this(name, sourceLibrary, sourceMember, sourceVersion, compiled, source, Map.of());
        }

        public Program(String name, String sourceLibrary, String sourceMember, int sourceVersion, String compiled, List<SourceLine> source,
                Map<String, String> files) {
            this.name = name;
            this.sourceLibrary = sourceLibrary;
            this.sourceMember = sourceMember;
            this.sourceVersion = sourceVersion;
            this.compiled = compiled;
            this.source = List.copyOf(source);
            this.files = Map.copyOf(files);
        }

        // What the VM runs (null if it no longer compiles: the language changed under it).
        public synchronized @Nullable CompiledProgram compiled() {
            if (program == null) {
                program = Compiler.compile(source, FileResolver.of(formats(files))).program();
            }
            return program;
        }
    }

    // Saved formats (RecordFormat.save()) back, by the same keys; any no longer readable left out.
    public static Map<String, RecordFormat> formats(Map<String, String> saved) {
        Map<String, RecordFormat> formats = new LinkedHashMap<>();
        saved.forEach((key, text) -> {
            RecordFormat format = RecordFormat.load(text);
            if (format != null) {
                formats.put(key, format);
            }
        });
        return formats;
    }

    // Formats to save with a program.
    public static Map<String, String> saved(Map<String, RecordFormat> formats) {
        Map<String, String> saved = new LinkedHashMap<>();
        formats.forEach((key, format) -> saved.put(key, format.save()));
        return saved;
    }

    // A user's profile (OS.md 6): who they are (player id), their library list and current library, when it was made
    // and last signed on with.
    public static final class Profile {
        public final String user, created;
        public @Nullable UUID player;
        public final List<String> libraryList;
        public String currentLibrary, lastSignOn = "";

        public Profile(String user, @Nullable UUID player, List<String> libraryList, String currentLibrary, String created) {
            this.user = user;
            this.player = player;
            this.libraryList = libraryList;
            this.currentLibrary = currentLibrary;
            this.created = created;
        }
    }

    public final Map<String, Library> libraries = new TreeMap<>();
    public final Map<String, Profile> profiles = new TreeMap<>();
    public final Map<String, List<MessageService.Message>> queues = new LinkedHashMap<>();
    public long nextMessage = 1;
    // Oldest first.
    public final List<SpoolService.SpooledFile> spooled = new ArrayList<>();
    public int nextSpooled = 1;
    public final Map<String, String> sysvals = new LinkedHashMap<>();
    // Device names (Part 2): each name given out on this system and the device holding it (ElclDevices.identity), so a
    // device that arrives with a name already taken here gets another rather than taking it.
    public final Map<String, String> deviceNames = new TreeMap<>();
    // Folder sync (elcl.sync): each LIB/MEMBER's text as last synced with its file (a hash).
    public final Map<String, String> syncHashes = new TreeMap<>();
    // Jobs, their logs, schedule entries and triggers (OS.md 5).
    public JobData jobs = new JobData();
    // Crafting jobs that ended, oldest first (CraftLog).
    public final List<CraftLog.Entry> craftLog = new ArrayList<>();
    // The later parts' sections, kept as they were saved until their code reads them.
    public final Map<String, Tag> sections = new LinkedHashMap<>();
    private Runnable changed = () -> {};

    public SystemData() {
        libraries.put(SYSTEM_LIBRARY, systemLibrary());
        libraries.put(GENERAL, new Library(GENERAL, "*PROD", "General purpose library", SYSTEM_OWNER, "*CHANGE", SHIPPED));
    }

    // Marks the system's data to be saved.
    public void changed() {
        changed.run();
    }

    void onChange(Runnable changed) {
        this.changed = changed;
    }

    // --- ELSYS: the shipped samples (docs/elcl/examples), read-only; the system files (SystemFiles) ---

    // Each member in members.txt is an ELCLP (NAME.elclp) or PF (NAME.pf) member; an ELCLP one is a program too when it
    // compiles against ELSYS's own files (LOGITEMS, which needs a file ITEMSETUP makes, stays a member to copy).
    public static Library systemLibrary() {
        Library samples = new Library(SYSTEM_LIBRARY, "*SYS", "System library - samples (read-only)", SYSTEM_OWNER, "*USE", SHIPPED);
        Map<String, RecordFormat> systemFormats = new LinkedHashMap<>();
        SystemFiles.formats().forEach((name, format) -> {
            systemFormats.put(SYSTEM_LIBRARY + "/" + name, format);
            samples.files.put(name, new DbFile(name, format.text(), format, SYSTEM_LIBRARY, "", 0, SHIPPED, true));
        });
        for (String name : resource("members.txt").lines().map(String::trim).filter(line -> !line.isEmpty()).toList()) {
            boolean pf = exists(name + ".pf");
            List<String> texts = SourceLine.split(resource(name + (pf ? ".pf" : ".elclp")));
            List<SourceLine> lines = SourceLine.number(texts, 0);
            samples.members.put(name, new Member(name, pf ? PF : ELCLP, pf ? ddsDescription(texts) : description(texts), lines, 0, SHIPPED));
            if (!pf) {
                Compiler.Result result = Compiler.compileTexts(texts, FileResolver.of(systemFormats));
                if (result.ok()) {
                    samples.programs.put(name, new Program(name, SYSTEM_LIBRARY, name, 0, SHIPPED, lines, saved(result.formats())));
                }
            }
        }
        return samples;
    }

    private static boolean exists(String name) {
        return EncodedLogistics.class.getResource("/data/encodedlogistics/elcl/ELSYS/" + name) != null;
    }

    private static String resource(String name) {
        try (InputStream in = EncodedLogistics.class.getResourceAsStream("/data/encodedlogistics/elcl/ELSYS/" + name)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    // A member's text from its first comment: "/* RESTOCK - keep an item stocked ... */".
    public static String description(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = DESCRIPTION.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    // A PF member's text from its first comment line: "A* ITEMHIST - Item counts logged by LOGITEMS".
    public static String ddsDescription(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = DDS_DESCRIPTION.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    // --- Storage (OS.md 3): a member costs a byte per charsPerStorageByte characters, rounded up, and so do a file's
    // records (each its record length); ELSYS and programs are free ---

    public static long cost(long characters, int perByte) {
        return (characters + perByte - 1) / perByte;
    }

    public long storageBytes(int perByte) {
        long bytes = 0;
        for (Library library : libraries.values()) {
            if (!library.system()) {
                for (Member member : library.members.values()) {
                    bytes += cost(member.characters(), perByte);
                }
                for (DbFile file : library.files.values()) {
                    bytes += cost(file.characters(), perByte);
                }
            }
        }
        return bytes;
    }

    // --- Saving ---

    private static StringTag line(SourceLine line) {
        return StringTag.valueOf(line.seq() + "\t" + line.date() + "\t" + line.text());
    }

    private static ListTag lines(List<SourceLine> lines) {
        ListTag list = new ListTag();
        lines.forEach(line -> list.add(line(line)));
        return list;
    }

    private static List<SourceLine> lines(ListTag list) {
        List<SourceLine> lines = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            String[] parts = list.getStringOr(i, "").split("\t", 3);
            try {
                lines.add(new SourceLine(Integer.parseInt(parts[0]), parts.length > 2 ? parts[2] : "", Integer.parseInt(parts[1])));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}
        }
        return lines;
    }

    private static ListTag strings(List<String> texts) {
        ListTag list = new ListTag();
        texts.forEach(text -> list.add(StringTag.valueOf(text)));
        return list;
    }

    private static List<String> strings(ListTag list) {
        List<String> texts = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            texts.add(list.getStringOr(i, ""));
        }
        return texts;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag libs = new ListTag();
        for (Library library : libraries.values()) {
            if (library.system()) {
                continue;
            }
            CompoundTag lib = new CompoundTag();
            lib.putString("name", library.name);
            lib.putString("type", library.type);
            lib.putString("text", library.text);
            lib.putString("owner", library.owner);
            lib.putString("authority", library.authority);
            lib.putString("created", library.created);
            ListTag members = new ListTag();
            for (Member member : library.members.values()) {
                CompoundTag mbr = new CompoundTag();
                mbr.putString("name", member.name);
                mbr.putString("type", member.type);
                mbr.putString("text", member.text);
                mbr.putInt("version", member.version);
                mbr.putString("updated", member.updated);
                mbr.put("lines", lines(member.lines));
                members.add(mbr);
            }
            lib.put("members", members);
            ListTag programs = new ListTag();
            for (Program program : library.programs.values()) {
                CompoundTag pgm = new CompoundTag();
                pgm.putString("name", program.name);
                pgm.putString("source_library", program.sourceLibrary);
                pgm.putString("source_member", program.sourceMember);
                pgm.putInt("source_version", program.sourceVersion);
                pgm.putString("compiled", program.compiled);
                pgm.put("lines", lines(program.source));
                if (!program.files.isEmpty()) {
                    CompoundTag files = new CompoundTag();
                    program.files.forEach(files::putString);
                    pgm.put("files", files);
                }
                programs.add(pgm);
            }
            lib.put("programs", programs);
            ListTag files = new ListTag();
            library.files.values().forEach(file -> files.add(file.save()));
            lib.put("files", files);
            libs.add(lib);
        }
        tag.put("libraries", libs);

        ListTag queues = new ListTag();
        for (Map.Entry<String, List<MessageService.Message>> queue : this.queues.entrySet()) {
            CompoundTag q = new CompoundTag();
            q.putString("user", queue.getKey());
            ListTag messages = new ListTag();
            for (MessageService.Message message : queue.getValue()) {
                CompoundTag m = new CompoundTag();
                m.putLong("id", message.id());
                m.putString("msg_id", message.msgId());
                m.putInt("severity", message.severity());
                m.putString("from", message.from());
                m.putString("sent", message.sent());
                m.putString("text", message.text());
                m.putBoolean("unread", message.unread());
                messages.add(m);
            }
            q.put("messages", messages);
            queues.add(q);
        }
        tag.put("queues", queues);
        tag.putLong("next_message", nextMessage);

        ListTag files = new ListTag();
        for (SpoolService.SpooledFile file : spooled) {
            CompoundTag f = new CompoundTag();
            f.putInt("id", file.id());
            f.putString("name", file.name());
            f.putString("job_number", file.jobNumber());
            f.putString("job_name", file.jobName());
            f.putString("user", file.user());
            f.putString("status", file.status());
            f.putString("created", file.created());
            f.put("lines", strings(file.lines()));
            files.add(f);
        }
        tag.put("spooled", files);
        tag.putInt("next_spooled", nextSpooled);

        CompoundTag values = new CompoundTag();
        sysvals.forEach(values::putString);
        tag.put("sysvals", values);

        tag.put("jobs", jobs.save());

        ListTag crafts = new ListTag();
        craftLog.forEach(entry -> crafts.add(entry.save()));
        tag.put("craft_log", crafts);

        ListTag users = new ListTag();
        for (Profile profile : profiles.values()) {
            CompoundTag p = new CompoundTag();
            p.putString("user", profile.user);
            if (profile.player != null) {
                p.putString("player", profile.player.toString());
            }
            p.put("library_list", strings(profile.libraryList));
            p.putString("current_library", profile.currentLibrary);
            p.putString("created", profile.created);
            p.putString("last_sign_on", profile.lastSignOn);
            users.add(p);
        }
        tag.put("profiles", users);

        CompoundTag synced = new CompoundTag();
        syncHashes.forEach(synced::putString);
        tag.put("sync_hashes", synced);

        CompoundTag names = new CompoundTag();
        deviceNames.forEach(names::putString);
        tag.put("device_names", names);

        CompoundTag extra = new CompoundTag();
        sections.forEach(extra::put);
        tag.put("sections", extra);
        return tag;
    }

    public static SystemData load(CompoundTag tag) {
        SystemData data = new SystemData();
        ListTag libs = tag.getListOrEmpty("libraries");
        for (int i = 0; i < libs.size(); i++) {
            CompoundTag lib = libs.getCompoundOrEmpty(i);
            String name = lib.getStringOr("name", "");
            if (name.isEmpty() || name.equals(SYSTEM_LIBRARY)) {
                continue;
            }
            Library library = new Library(name, lib.getStringOr("type", "*PROD"), lib.getStringOr("text", ""), lib.getStringOr("owner", SYSTEM_OWNER),
                    lib.getStringOr("authority", "*USE"), lib.getStringOr("created", SHIPPED));
            ListTag members = lib.getListOrEmpty("members");
            for (int j = 0; j < members.size(); j++) {
                CompoundTag mbr = members.getCompoundOrEmpty(j);
                Member member = new Member(mbr.getStringOr("name", ""), mbr.getStringOr("type", ELCLP), mbr.getStringOr("text", ""),
                        lines(mbr.getListOrEmpty("lines")), mbr.getIntOr("version", 0), mbr.getStringOr("updated", SHIPPED));
                library.members.put(member.name, member);
            }
            ListTag programs = lib.getListOrEmpty("programs");
            for (int j = 0; j < programs.size(); j++) {
                CompoundTag pgm = programs.getCompoundOrEmpty(j);
                Map<String, String> files = new LinkedHashMap<>();
                CompoundTag formats = pgm.getCompoundOrEmpty("files");
                for (String key : formats.keySet()) {
                    files.put(key, formats.getStringOr(key, ""));
                }
                Program program = new Program(pgm.getStringOr("name", ""), pgm.getStringOr("source_library", name), pgm.getStringOr("source_member", ""),
                        pgm.getIntOr("source_version", 0), pgm.getStringOr("compiled", SHIPPED), lines(pgm.getListOrEmpty("lines")), files);
                library.programs.put(program.name, program);
            }
            ListTag files = lib.getListOrEmpty("files");
            for (int j = 0; j < files.size(); j++) {
                DbFile file = DbFile.load(files.getCompoundOrEmpty(j));
                if (file != null) {
                    library.files.put(file.name, file);
                }
            }
            data.libraries.put(name, library);
        }

        ListTag queues = tag.getListOrEmpty("queues");
        for (int i = 0; i < queues.size(); i++) {
            CompoundTag q = queues.getCompoundOrEmpty(i);
            List<MessageService.Message> messages = new ArrayList<>();
            ListTag list = q.getListOrEmpty("messages");
            for (int j = 0; j < list.size(); j++) {
                CompoundTag m = list.getCompoundOrEmpty(j);
                messages.add(new MessageService.Message(m.getLongOr("id", 0), m.getStringOr("msg_id", ""), m.getIntOr("severity", 0),
                        m.getStringOr("from", ""), m.getStringOr("sent", ""), m.getStringOr("text", ""), m.getBooleanOr("unread", false)));
            }
            data.queues.put(q.getStringOr("user", ""), messages);
        }
        data.nextMessage = Math.max(1, tag.getLongOr("next_message", 1));

        ListTag files = tag.getListOrEmpty("spooled");
        for (int i = 0; i < files.size(); i++) {
            CompoundTag f = files.getCompoundOrEmpty(i);
            List<String> lines = strings(f.getListOrEmpty("lines"));
            data.spooled.add(new SpoolService.SpooledFile(f.getIntOr("id", 0), f.getStringOr("name", ""), f.getStringOr("job_number", ""),
                    f.getStringOr("job_name", ""), f.getStringOr("user", ""), StoredSpoolService.pages(lines.size()), f.getStringOr("status", "*RDY"),
                    f.getStringOr("created", ""), List.copyOf(lines)));
        }
        data.nextSpooled = Math.max(1, tag.getIntOr("next_spooled", 1));

        CompoundTag values = tag.getCompoundOrEmpty("sysvals");
        for (String key : values.keySet()) {
            data.sysvals.put(key, values.getStringOr(key, ""));
        }

        data.jobs = JobData.load(tag.getCompoundOrEmpty("jobs"));

        ListTag crafts = tag.getListOrEmpty("craft_log");
        for (int i = 0; i < crafts.size(); i++) {
            CraftLog.Entry entry = CraftLog.Entry.load(crafts.getCompoundOrEmpty(i));
            if (entry != null) {
                data.craftLog.add(entry);
            }
        }

        ListTag users = tag.getListOrEmpty("profiles");
        for (int i = 0; i < users.size(); i++) {
            CompoundTag p = users.getCompoundOrEmpty(i);
            UUID player = null;
            try {
                String id = p.getStringOr("player", "");
                player = id.isEmpty() ? null : UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {}
            Profile profile = new Profile(p.getStringOr("user", ""), player, new ArrayList<>(strings(p.getListOrEmpty("library_list"))),
                    p.getStringOr("current_library", GENERAL), p.getStringOr("created", ""));
            profile.lastSignOn = p.getStringOr("last_sign_on", "");
            if (!profile.user.isEmpty()) {
                data.profiles.put(profile.user, profile);
            }
        }

        CompoundTag synced = tag.getCompoundOrEmpty("sync_hashes");
        for (String key : synced.keySet()) {
            data.syncHashes.put(key, synced.getStringOr(key, ""));
        }

        CompoundTag names = tag.getCompoundOrEmpty("device_names");
        for (String key : names.keySet()) {
            data.deviceNames.put(key, names.getStringOr(key, ""));
        }

        CompoundTag extra = tag.getCompoundOrEmpty("sections");
        for (String key : extra.keySet()) {
            Tag section = extra.get(key);
            if (section != null) {
                data.sections.put(key, section);
            }
        }
        return data;
    }
}
