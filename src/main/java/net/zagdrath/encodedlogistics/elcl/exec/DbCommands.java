/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.elcl.db.Csv;
import net.zagdrath.encodedlogistics.elcl.db.DbRecord;
import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.elcl.db.Query;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.db.Report;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.FileService;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.sync.FolderSync;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// The database commands (COMMANDS.md 11) on the file service: CRTPF and CHGPF (a PF member's definition, the listing in
// Work with Output), DLTF, CLRPFM, CPYF (fields copied by name; CRTFILE(*YES) makes the file), CPYTOIMPF / CPYFRMIMPF
// (CSV through the system's folder-sync folder, only where folder sync is on: ELC2241), and RUNQRY (QRYSLT and SORT;
// *PRINT a spooled file, *OUTFILE a file made or replaced; *DISPLAY opens the report on a terminal, so anywhere else -
// a program, a batch job - it's printed). A file named without a library is looked for down the user's library list;
// one being made goes in their current library. The file operations (RCVF...) run in the VM.
final class DbCommands {
    // Spooled file of RUNQRY's printed report.
    static final String REPORT = "QPQUPRFIL";

    private DbCommands() {}

    private static FileService files() {
        return ElclServices.files();
    }

    private static FileService.Who who(Invocation call) throws ElclException {
        ElclContext context = OsCommands.context(call);
        return new FileService.Who(context.user(), context.allowed(RackPermission.VIEW));
    }

    private static String[] split(String text) {
        String value = text.strip().toUpperCase(Locale.ROOT);
        int slash = value.indexOf('/');
        return slash >= 0 ? new String[] { value.substring(0, slash), value.substring(slash + 1) } : new String[] { "*LIBL", value };
    }

    // A file that's there: {library, name} (*LIBL searched; ELC0201 / ELC2205 when it isn't anywhere).
    static String[] existing(ElclSystem system, String user, String text) throws ElclException {
        String[] name = split(text);
        String[] found = files().resolve(system, user, name[0], name[1]);
        files().file(system, found[0], found[1]);
        return found;
    }

    // A file to make: in the library given, else the user's current library.
    static String[] target(ElclSystem system, String user, String text) {
        String[] name = split(text);
        if (name[0].equals("*LIBL") || name[0].equals("*CURLIB")) {
            name[0] = ElclServices.users().profile(system, user, null).currentLibrary();
        }
        return name;
    }

    private static String qualified(String[] name) {
        return name[0] + "/" + name[1];
    }

    // TEXT(): *BLANK is no text; the special keeping what's there (or the member's) null.
    private static String text(Invocation call, String keep) throws ElclException {
        String text = call.text("TEXT");
        return text.equalsIgnoreCase(keep) ? null : text.equalsIgnoreCase("*BLANK") ? "" : text;
    }

    static void bind() {
        CommandRegistry.bind("CRTPF", call -> {
            ElclSystem system = OsCommands.system(call);
            String user = OsCommands.user(call);
            String[] file = target(system, user, call.text("FILE"));
            String[] source = call.text("SRCMBR").equalsIgnoreCase("*FILE") ? file : OsCommands.qualified(system, user, call.text("SRCMBR"));
            FileService.Outcome outcome = files().create(system, user, file[0], file[1], source[0], source[1], text(call, "*SRCMBRTXT"));
            if (!outcome.done()) {
                throw new ElclException("ELC2239", file[1]);
            }
            call.send(ElclMessage.of("ELC2230", file[1], file[0]));
        });
        CommandRegistry.bind("CHGPF", call -> {
            ElclSystem system = OsCommands.system(call);
            String user = OsCommands.user(call);
            String[] file = existing(system, user, call.text("FILE"));
            String[] source = call.text("SRCMBR").equalsIgnoreCase("*FILE") ? null : OsCommands.qualified(system, user, call.text("SRCMBR"));
            FileService.Outcome outcome = files().change(system, user, file[0], file[1], source != null ? source[0] : null, source != null ? source[1] : null,
                    text(call, "*SAME"));
            if (!outcome.done()) {
                throw new ElclException("ELC2246", file[1]);
            }
            for (String field : outcome.dropped()) {
                call.send(ElclMessage.of("ELC2232", field, file[1]));
            }
            call.send(ElclMessage.of("ELC2231", file[1], file[0], outcome.kept()));
        });
        CommandRegistry.bind("DLTF", call -> {
            ElclSystem system = OsCommands.system(call);
            String[] file = existing(system, OsCommands.user(call), call.text("FILE"));
            files().delete(system, OsCommands.user(call), file[0], file[1]);
            call.send(ElclMessage.of("ELC2233", file[1], file[0]));
        });
        CommandRegistry.bind("CLRPFM", call -> {
            ElclSystem system = OsCommands.system(call);
            String[] file = existing(system, OsCommands.user(call), call.text("FILE"));
            int removed = files().clear(system, OsCommands.user(call), file[0], file[1]);
            call.send(ElclMessage.of("ELC2234", file[1], removed));
        });
        CommandRegistry.bind("CPYF", DbCommands::copy);
        CommandRegistry.bind("CPYTOIMPF", DbCommands::export);
        CommandRegistry.bind("CPYFRMIMPF", DbCommands::importCsv);
        CommandRegistry.bind("RUNQRY", DbCommands::query);
    }

    // --- CPYF ---

    // Records of one format as another's: each field of the target from the source's field of the same name (ELC2243
    // for a value that doesn't fit it), the rest blank. ELC2244 when they share no field.
    static List<Object[]> map(List<DbRecord> records, RecordFormat from, RecordFormat to, String fromName, String toName) throws ElclException {
        int[] sources = new int[to.fields().size()];
        boolean any = false;
        for (int i = 0; i < sources.length; i++) {
            sources[i] = from.index(to.fields().get(i).name());
            any |= sources[i] >= 0;
        }
        if (!any) {
            throw new ElclException("ELC2244", fromName, toName);
        }
        List<Object[]> mapped = new ArrayList<>(records.size());
        int row = 0;
        for (DbRecord record : records) {
            row++;
            Object[] values = to.blank();
            for (int i = 0; i < values.length; i++) {
                if (sources[i] >= 0) {
                    FieldDef field = to.fields().get(i);
                    Object value = record.value(sources[i]);
                    try {
                        values[i] = field.convert(value);
                    } catch (ElclException e) {
                        throw new ElclException("ELC2243", row, from.fields().get(sources[i]).text(value), field.name());
                    }
                }
            }
            mapped.add(values);
        }
        return mapped;
    }

    private static void copy(Invocation call) throws ElclException {
        ElclSystem system = OsCommands.system(call);
        String user = OsCommands.user(call);
        FileService.Who who = who(call);
        String[] from = existing(system, user, call.text("FROMFILE"));
        RecordFormat format = files().format(system, from[0], from[1]);
        List<DbRecord> records = files().records(system, who, from[0], from[1]);
        boolean replace = call.text("MBROPT").equals("*REPLACE");
        String[] to;
        try {
            to = existing(system, user, call.text("TOFILE"));
        } catch (ElclException e) {
            if (!call.text("CRTFILE").equals("*YES") || !e.elclMessage().id().equals("ELC2205")) {
                throw e;
            }
            // CRTFILE(*YES): the file made with the source's format.
            to = target(system, user, call.text("TOFILE"));
            List<Object[]> values = new ArrayList<>();
            records.forEach(record -> values.add(record.values()));
            files().createFrom(system, user, to[0], to[1], format, files().file(system, from[0], from[1]).text(), values, false);
            call.send(ElclMessage.of("ELC2235", values.size(), qualified(to)));
            return;
        }
        RecordFormat target = files().format(system, to[0], to[1]);
        int added = files().add(system, who, to[0], to[1], map(records, format, target, qualified(from), qualified(to)), replace);
        call.send(ElclMessage.of("ELC2235", added, qualified(to)));
    }

    // --- CPYTOIMPF / CPYFRMIMPF ---

    // The stream file in the system's folder: a plain name ending .csv (ELC2245 otherwise); ELC2241 with folder sync off.
    static Path stream(ElclSystem system, String command, String name) throws ElclException {
        if (!FolderSync.enabled(system.server())) {
            throw new ElclException("ELC2241", command);
        }
        String file = name.strip();
        if (!file.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,59}\\.[Cc][Ss][Vv]")) {
            throw new ElclException("ELC2245", file);
        }
        return FolderSync.folder(system).resolve(file);
    }

    private static void export(Invocation call) throws ElclException {
        ElclSystem system = OsCommands.system(call);
        String[] file = existing(system, OsCommands.user(call), call.text("FILE"));
        Path path = stream(system, "CPYTOIMPF", call.text("TOSTMF"));
        List<DbRecord> records = files().records(system, who(call), file[0], file[1]);
        String csv = Csv.write(files().format(system, file[0], file[1]), records);
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, csv, StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new ElclException("ELC2245", path.getFileName().toString());
        }
        call.send(ElclMessage.of("ELC2236", records.size(), path.getFileName().toString()));
    }

    private static void importCsv(Invocation call) throws ElclException {
        ElclSystem system = OsCommands.system(call);
        String[] file = existing(system, OsCommands.user(call), call.text("FILE"));
        Path path = stream(system, "CPYFRMIMPF", call.text("FROMSTMF"));
        String csv;
        try {
            csv = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ElclException("ELC2240", path.getFileName().toString());
        }
        List<Object[]> records = Csv.records(files().format(system, file[0], file[1]), Csv.read(csv));
        int added = files().add(system, who(call), file[0], file[1], records, call.text("MBROPT").equals("*REPLACE"));
        call.send(ElclMessage.of("ELC2237", added, path.getFileName().toString()));
    }

    // --- RUNQRY ---

    // The records a query selects, sorted: {records, of how many}.
    record Result(RecordFormat format, List<DbRecord> records, int of) {}

    static Result run(ElclSystem system, FileService.Who who, String[] file, String qryslt, List<String> sort) throws ElclException {
        RecordFormat format = files().format(system, file[0], file[1]);
        List<DbRecord> all = files().records(system, who, file[0], file[1]);
        Query.Selection selection = Query.selection(qryslt, format, qualified(file));
        List<Query.SortKey> keys = Query.sort(sort, format, qualified(file));
        return new Result(format, Query.run(all, selection, keys), all.size());
    }

    private static void query(Invocation call) throws ElclException {
        ElclSystem system = OsCommands.system(call);
        String user = OsCommands.user(call);
        String[] file = existing(system, user, call.text("FILE"));
        Result result = run(system, who(call), file, call.text("QRYSLT"), call.list("SORT"));
        if (call.text("OUTPUT").equals("*OUTFILE")) {
            // The query's order: a file without a key, its records in arrival order.
            String[] out = target(system, user, call.text("OUTFILE"));
            List<Object[]> values = new ArrayList<>();
            result.records().forEach(record -> values.add(record.values()));
            RecordFormat format = result.format();
            files().createFrom(system, user, out[0], out[1], new RecordFormat(format.name(), format.text(), format.fields(), List.of(), false),
                    "Query of " + qualified(file), values, true);
        } else {
            // *PRINT, and *DISPLAY where there's no screen to show it on.
            JobService.Job job = OsCommands.currentJob(call, system);
            String selection = call.text("QRYSLT");
            ElclServices.spool().create(system, REPORT, job.number(), job.name(), user,
                    Report.print(qualified(file), selection.equalsIgnoreCase("*ALL") ? "" : selection, system.nowShort(), system.name(), result.format(),
                            result.records(), result.of()));
        }
        call.send(ElclMessage.of("ELC2238", result.records().size(), result.of()));
    }
}
