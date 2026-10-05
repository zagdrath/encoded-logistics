/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.ElclMessages;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.device.Diskette;
import net.zagdrath.encodedlogistics.elcl.device.DisketteDevice;
import net.zagdrath.encodedlogistics.elcl.device.Diskettes;
import net.zagdrath.encodedlogistics.elcl.device.LibraryImage;
import net.zagdrath.encodedlogistics.elcl.job.BatchContext;
import net.zagdrath.encodedlogistics.elcl.job.InteractiveCalls;
import net.zagdrath.encodedlogistics.elcl.job.JobManager;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.screen.LibraryService;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;

// The OS commands (COMMANDS.md 8-9) on the screens' services: libraries, members and programs; jobs, schedule entries
// and triggers; system values; messages and printed text. Each sends its completion message (MESSAGES.md, amendment 2).
public final class OsCommands {
    private OsCommands() {}

    // A user's library list (OS.md 2, their profile's): where *LIBL looks, in order; the first is *CURLIB.
    public static List<String> libraryList(ElclSystem system, String user) {
        return ElclServices.users().profile(system, user, null).libraryList();
    }

    // The system the command runs on: the context's network (ELC1302 on *NETWORK while there's none).
    static ElclSystem system(Invocation call) throws ElclException {
        ElclContext context = context(call);
        if (context.network() == null) {
            throw new ElclException("ELC1302", "*NETWORK");
        }
        return new ElclSystem(context.server(), context.network());
    }

    static ElclContext context(Invocation call) throws ElclException {
        ElclContext context = call.context(ElclContext.class);
        if (context == null) {
            throw new ElclException("ELC0107", call.command().name());
        }
        return context;
    }

    static String user(Invocation call) throws ElclException {
        return context(call).user();
    }

    // The player a command runs for: at a terminal, theirs; in a batch job, its submitter's; else null.
    static @Nullable UUID player(Invocation call) throws ElclException {
        ElclContext context = context(call);
        if (context instanceof TerminalContext terminal) {
            return terminal.player().getUUID();
        }
        return context instanceof BatchContext batch ? batch.player() : null;
    }

    // LIB/NAME as {library, name}: a bare name (or *LIBL/NAME) is looked for down the user's library list, else it's in
    // their current library; *CURLIB is the current library.
    static String[] qualified(ElclSystem system, String user, String text) {
        String value = text.toUpperCase(Locale.ROOT);
        int slash = value.indexOf('/');
        String library = slash >= 0 ? value.substring(0, slash) : "*LIBL", name = slash >= 0 ? value.substring(slash + 1) : value;
        var profile = ElclServices.users().profile(system, user, null);
        if (library.equals("*CURLIB")) {
            library = profile.currentLibrary();
        } else if (library.equals("*LIBL")) {
            library = profile.currentLibrary();
            for (String candidate : profile.libraryList()) {
                try {
                    ElclServices.libraries().member(system, candidate, name);
                    library = candidate;
                    break;
                } catch (ElclException ignored) {}
            }
        }
        return new String[] { library, name };
    }

    private static @Nullable String same(String value, String special) {
        return value.equalsIgnoreCase(special) ? null : value;
    }

    static void bind() {
        LibraryService libraries = ElclServices.libraries();
        CommandRegistry.bind("CRTLIB", call -> {
            String lib = call.text("LIB").toUpperCase(Locale.ROOT);
            libraries.createLibrary(system(call), user(call), lib, call.text("TYPE"), call.text("TEXT").equals("*BLANK") ? "" : call.text("TEXT"));
            call.send(ElclMessage.of("ELC0210", lib));
        });
        CommandRegistry.bind("CHGLIB", call -> {
            String lib = call.text("LIB").toUpperCase(Locale.ROOT);
            libraries.changeLibrary(system(call), user(call), lib, same(call.text("TEXT"), "*SAME"), same(call.text("AUT"), "*SAME"));
            call.send(ElclMessage.of("ELC0212", lib));
        });
        CommandRegistry.bind("DLTLIB", call -> {
            String lib = call.text("LIB").toUpperCase(Locale.ROOT);
            libraries.deleteLibrary(system(call), user(call), lib);
            call.send(ElclMessage.of("ELC0211", lib));
        });
        CommandRegistry.bind("CRTMBR", call -> {
            ElclSystem system = system(call);
            String[] mbr = qualified(system, user(call), call.text("MBR"));
            libraries.createMember(system, user(call), mbr[0], mbr[1], call.text("TEXT").equals("*BLANK") ? "" : call.text("TEXT"), call.text("SRCTYPE"));
            call.send(ElclMessage.of("ELC0214", mbr[1], mbr[0]));
        });
        CommandRegistry.bind("CPYMBR", call -> {
            ElclSystem system = system(call);
            String[] from = qualified(system, user(call), call.text("FROM")), to = qualified(system, user(call), call.text("TO"));
            libraries.copyMember(system, user(call), from[0], from[1], to[0], to[1]);
            call.send(ElclMessage.of("ELC0215", from[1], to[0] + "/" + to[1]));
        });
        CommandRegistry.bind("RNMMBR", call -> {
            ElclSystem system = system(call);
            String[] mbr = qualified(system, user(call), call.text("MBR"));
            String name = call.text("NEWNAME").toUpperCase(Locale.ROOT);
            libraries.renameMember(system, user(call), mbr[0], mbr[1], name);
            call.send(ElclMessage.of("ELC0216", mbr[1], name));
        });
        CommandRegistry.bind("DLTMBR", call -> {
            ElclSystem system = system(call);
            String[] mbr = qualified(system, user(call), call.text("MBR"));
            libraries.deleteMember(system, user(call), mbr[0], mbr[1]);
            call.send(ElclMessage.of("ELC0217", mbr[1], mbr[0]));
        });
        CommandRegistry.bind("CRTELPGM", call -> {
            ElclSystem system = system(call);
            String[] pgm = qualified(system, user(call), call.text("PGM"));
            String[] src = call.text("SRCMBR").equals("*PGM") ? pgm : qualified(system, user(call), call.text("SRCMBR"));
            Compiler.Target target = call.text("TGT").equals("*PLC") ? Compiler.Target.PLC : Compiler.Target.JOB;
            LibraryService.CompileOutcome outcome = libraries.compile(system, user(call), pgm[0], pgm[1], src[0], src[1], target);
            if (!outcome.created()) {
                throw new ElclException("ELC0206", pgm[1]);
            }
            call.send(ElclMessage.of("ELC0218", pgm[1], pgm[0]));
        });
        CommandRegistry.bind("DLTPGM", call -> {
            ElclSystem system = system(call);
            String[] pgm = qualified(system, user(call), call.text("PGM"));
            libraries.deleteProgram(system, user(call), pgm[0], pgm[1]);
            call.send(ElclMessage.of("ELC0219", pgm[1], pgm[0]));
        });

        CommandRegistry.bind("CALL", InteractiveCalls::call);
        // In a program the VM waits; typed on a command line there's nothing to hold up, so it's done at once.
        CommandRegistry.bind("DLYJOB", call -> {});
        CommandRegistry.bind("DLYTICK", call -> {});

        // SAVLIB / RSTLIB: a library to or from the 8" Diskette in the device DEV() names (the Midrange line's
        // DisketteDevice): ELC1301 with no such device, ELC1310 with no diskette, ELC1311 when it doesn't fit.
        CommandRegistry.bind("SAVLIB", call -> {
            ElclSystem system = system(call);
            String lib = call.text("LIB").toUpperCase(Locale.ROOT);
            // The device first, as RSTLIB: without the hardware, ELC1301 whatever the library.
            DisketteDevice device = Diskettes.find(system, call.text("DEV"));
            LibraryImage image = libraries.image(system, lib);
            Diskette diskette = device.mounted().getFirst();
            long free = diskette.capacity() - diskette.used(lib);
            if (image.bytes() > free) {
                throw new ElclException("ELC1311", lib, image.bytes(), Math.max(0, free));
            }
            diskette.write(image);
            call.send(ElclMessage.of("ELC0220", lib, device.name() + " (" + diskette.label() + ")"));
        });
        CommandRegistry.bind("RSTLIB", call -> {
            ElclSystem system = system(call);
            String lib = call.text("LIB").toUpperCase(Locale.ROOT);
            DisketteDevice device = Diskettes.find(system, call.text("DEV"));
            LibraryImage image = null;
            String label = "";
            for (Diskette diskette : device.mounted()) {
                image = diskette.library(lib);
                if (image != null) {
                    label = diskette.label();
                    break;
                }
            }
            if (image == null) {
                throw new ElclException("ELC0201", lib);
            }
            libraries.restore(system, user(call), image);
            call.send(ElclMessage.of("ELC0221", lib, device.name() + " (" + label + ")"));
        });

        JobService jobs = ElclServices.jobs();
        CommandRegistry.bind("SBMJOB", call -> {
            String name = call.text("JOB").equals("*JOBD") ? "QDFTJOBD" : call.text("JOB");
            call.send(jobs.submit(system(call), user(call), player(call), call.text("CMD"), name, call.text("HOST"), call.text("LOG").equals("*YES")));
        });
        CommandRegistry.bind("HLDJOB", call -> call.send(jobs.hold(system(call), user(call), call.text("JOB"))));
        CommandRegistry.bind("RLSJOB", call -> call.send(jobs.release(system(call), user(call), call.text("JOB"))));
        CommandRegistry.bind("ENDJOB", call -> {
            ElclSystem system = system(call);
            JobService.Job job = jobs.job(system, call.text("JOB"));
            call.send(jobs.end(system, user(call), call.text("JOB"), call.text("OPTION")));
            JobManager.of(system.server()).end(system, job.number());
        });
        CommandRegistry.bind("CHGJOB", call -> jobs.change(system(call), user(call), call.text("JOB"),
                call.text("JOBPTY").equals("*SAME") ? 0 : (int) call.integer("JOBPTY"), call.text("LOG")));
        CommandRegistry.bind("ADDJOBSCDE", call -> call.send(jobs.addScheduleEntry(system(call), user(call), player(call), call.text("JOB"), call.text("CMD"),
                call.text("FRQ"), call.text("TIME"), call.given("INTERVAL") ? (int) call.integer("INTERVAL") : 0)));
        CommandRegistry.bind("RMVJOBSCDE", call -> call.send(jobs.removeScheduleEntry(system(call), user(call), call.text("JOB"))));
        CommandRegistry.bind("HLDJOBSCDE", call -> jobs.holdScheduleEntry(system(call), user(call), call.text("JOB"), true));
        CommandRegistry.bind("RLSJOBSCDE", call -> jobs.holdScheduleEntry(system(call), user(call), call.text("JOB"), false));
        CommandRegistry.bind("ADDTRGEVT", call -> call.send(jobs.addTrigger(system(call), user(call), player(call), new JobService.Trigger(call.text("TRG"),
                call.text("EVENT"), call.text("ITEM"), call.text("DEV"), call.text("VALUE").equals("*NONE") ? "" : call.text("VALUE"),
                call.text("PGM").toUpperCase(Locale.ROOT), "*ACTIVE", user(call)))));
        CommandRegistry.bind("RMVTRGEVT", call -> call.send(jobs.removeTrigger(system(call), user(call), call.text("TRG"))));
        CommandRegistry.bind("HLDTRGEVT", call -> jobs.holdTrigger(system(call), user(call), call.text("TRG"), true));
        CommandRegistry.bind("RLSTRGEVT", call -> jobs.holdTrigger(system(call), user(call), call.text("TRG"), false));
        CommandRegistry.bind("RTVJOBA", call -> {
            ElclSystem system = system(call);
            JobService.Job job = currentJob(call, system);
            call.returns("RTNUSR", job.user());
            call.returns("RTNJOB", job.name());
            call.returns("RTNTYPE", call.interactive() ? "*INTER" : "*BATCH");
            call.returns("RTNHOST", job.host());
        });

        CommandRegistry.bind("RTVSYSVAL", call -> call.returns("RTNVAR", ElclServices.sysvals().value(system(call), call.text("SYSVAL")).value()));
        CommandRegistry.bind("CHGSYSVAL", call -> {
            ElclSystem system = system(call);
            boolean officer = ElclServices.users().securityOfficer(system, user(call))
                    || ControllerStructures.firewall(system.server(), system.network()) == null;
            call.send(ElclServices.sysvals().change(system, user(call), officer, call.text("SYSVAL"), call.text("VALUE")));
        });

        CommandRegistry.bind("SNDMSG", call -> {
            String to = call.text("TOUSR");
            ElclServices.messages().send(system(call), user(call), to.equals("*REQUESTER") ? user(call) : to, "", ElclMessages.INFO, call.text("MSG"));
        });
        CommandRegistry.bind("SNDPGMMSG", call -> {
            String id = call.text("MSGID").toUpperCase(Locale.ROOT);
            ElclMessage message = ElclMessage.user(id, call.text("MSGTYPE").equals("*ESCAPE") ? ElclMessages.SEVERE : ElclMessages.INFO, call.text("MSG"));
            if (call.text("MSGTYPE").equals("*ESCAPE")) {
                throw new ElclException(message);
            }
            call.send(message);
        });
        CommandRegistry.bind("PRTTXT", call -> {
            ElclSystem system = system(call);
            JobService.Job job = currentJob(call, system);
            String file = call.text("SPLF").equals("*JOB") ? "QPRINT" : call.text("SPLF").toUpperCase(Locale.ROOT);
            ElclServices.spool().append(system, file, job.number(), job.name(), user(call), call.text("TEXT"));
        });
    }

    // The job a command runs in: its context's (a batch job), else the user's interactive one.
    public static JobService.Job currentJob(Invocation call, ElclSystem system) throws ElclException {
        ElclContext context = context(call);
        if (context.job() != null) {
            return ElclServices.jobs().job(system, context.job());
        }
        return interactiveJob(system, context.user());
    }

    public static JobService.Job interactiveJob(ElclSystem system, String user) {
        for (JobService.Job job : ElclServices.jobs().jobs(system)) {
            if (job.type().equals("INT") && job.user().equalsIgnoreCase(user)) {
                return job;
            }
        }
        return new JobService.Job("000000", "QINTER", user.toUpperCase(Locale.ROOT), "INT", "", "*ACTIVE", 0, 5, false);
    }
}
