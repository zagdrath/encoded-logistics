/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.Wait;
import net.zagdrath.encodedlogistics.elcl.exec.CommandRunner;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.elcl.exec.OsCommands;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.vm.VmHost;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// The VM's host in the game, for one run: programs from the system's libraries (*LIBL: the user's library list), its
// waits (Waits), item names, the run's context and authority, and its messages - the job log, the run's output, and
// the user's message queue when its program ends abnormally.
public final class JobVmHost implements VmHost {
    private final JobManager.Run run;

    public JobVmHost(JobManager.Run run) {
        this.run = run;
    }

    @Override
    public Loaded program(String library, String name) throws ElclException {
        String lib = library.toUpperCase(Locale.ROOT), pgm = name.toUpperCase(Locale.ROOT);
        if (lib.equals("*LIBL") || lib.equals("*CURLIB")) {
            ElclException first = null;
            for (String candidate : OsCommands.libraryList(run.system, run.user)) {
                try {
                    return new Loaded(candidate + "/" + pgm, SourceLine.texts(ElclServices.libraries().programSource(run.system, candidate, pgm)));
                } catch (ElclException e) {
                    if (first == null) {
                        first = e;
                    }
                }
            }
            throw new ElclException("ELC0203", pgm, "*LIBL");
        }
        return new Loaded(lib + "/" + pgm, SourceLine.texts(ElclServices.libraries().programSource(run.system, lib, pgm)));
    }

    @Override
    public boolean waitDone(Wait wait) {
        return Waits.done(run.context, wait);
    }

    @Override
    public String itemName(String item) {
        return ElclItems.name(item);
    }

    @Override
    public boolean interactive() {
        return run.interactive;
    }

    @Override
    public <T> @Nullable T context(Class<T> type) {
        return type.isInstance(run.context) ? type.cast(run.context) : null;
    }

    @Override
    public @Nullable ElclMessage authorise(CommandDefinition.Auth auth) {
        RackPermission permission = CommandRunner.permission(auth);
        return permission == null || run.context.allowed(permission) ? null : ElclMessage.of("ELC0401", run.user, permission.name());
    }

    @Override
    public boolean logsCommands() {
        return run.logCommands;
    }

    @Override
    public void logCommand(String command) {
        ElclServices.jobs().logCommand(run.system, run.job, command);
    }

    @Override
    public void message(ElclMessage message) {
        run.output.add(message);
        ElclServices.jobs().logMessage(run.system, run.job, message);
    }

    @Override
    public void escaped(String program, ElclMessage message) {
        ElclServices.jobs().logMessage(run.system, run.job, message);
    }

    @Override
    public void failed(ElclMessage message) {
        ElclServices.messages().send(run.system, run.program, run.user, message.id(), message.severity(), message.text());
    }

    @Override
    public long gameTime() {
        return run.system.server().overworld().getGameTime();
    }

    @Override
    public long dayTime() {
        return run.system.ticks();
    }

    @Override
    public int maxList() {
        return ElclConfig.maxListSize();
    }
}
