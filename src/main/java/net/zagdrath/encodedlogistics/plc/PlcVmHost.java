/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.plc;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.Wait;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.db.FileAccess;
import net.zagdrath.encodedlogistics.elcl.exec.CommandRunner;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.elcl.exec.OsCommands;
import net.zagdrath.encodedlogistics.elcl.job.Waits;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.FileService;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.vm.VmHost;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// The VM's host for a PLC's program (docs/plc HANDOFF 4): the same VM a batch job runs on, with the PLC as its job. It
// isn't interactive (a batch job's commands work). Cabled to a network, everything works with the authority of the
// player who last loaded the program: CALL finds programs on their library list, files are the system's. Without one,
// only the language, the delays, its own faces and its modules: anything else is ELC1502 (CALL and the file commands
// too). Its messages go to the PLC's own log; an unmonitored escape puts the PLC in FAULT.
final class PlcVmHost implements VmHost {
    private final PlcBlockEntity plc;
    private final PlcContext context;

    PlcVmHost(PlcBlockEntity plc, PlcContext context) {
        this.plc = plc;
        this.context = context;
    }

    private @Nullable ElclSystem system() {
        return context.network() != null ? new ElclSystem(context.server(), context.network()) : null;
    }

    @Override
    public Loaded program(String library, String name) throws ElclException {
        ElclSystem system = system();
        if (system == null) {
            throw new ElclException("ELC1502", "CALL");
        }
        String lib = library.toUpperCase(Locale.ROOT), pgm = name.toUpperCase(Locale.ROOT);
        if (lib.equals("*LIBL") || lib.equals("*CURLIB")) {
            for (String candidate : OsCommands.libraryList(system, context.user())) {
                try {
                    return loaded(system, candidate, pgm);
                } catch (ElclException e) {
                    // On down the list.
                }
            }
            throw new ElclException("ELC0203", pgm, "*LIBL");
        }
        return loaded(system, lib, pgm);
    }

    private static Loaded loaded(ElclSystem system, String library, String program) throws ElclException {
        return new Loaded(library + "/" + program, SourceLine.texts(ElclServices.libraries().programSource(system, library, program)),
                ElclServices.libraries().programFiles(system, library, program));
    }

    @Override
    public @Nullable FileAccess files() {
        ElclSystem system = system();
        return system == null ? null : ElclServices.files().access(system, new FileService.Who(context.user(), context.allowed(RackPermission.VIEW)));
    }

    @Override
    public @Nullable ElclMessage refuses(Stmt statement) {
        if (context.network() != null) {
            return null;
        }
        Expr device = statement.value("DEV");
        return Compiler.plcLocal(statement.name(), device == null ? null : device.toString()) ? null : ElclMessage.of("ELC1502", statement.name());
    }

    @Override
    public boolean waitDone(Wait wait) {
        return Waits.done(context, wait);
    }

    @Override
    public String itemName(String item) {
        return ElclItems.name(item);
    }

    @Override
    public boolean interactive() {
        return false;
    }

    @Override
    public <T> @Nullable T context(Class<T> type) {
        return type.isInstance(context) ? type.cast(context) : null;
    }

    @Override
    public @Nullable ElclMessage authorise(CommandDefinition.Auth auth) {
        RackPermission permission = CommandRunner.permission(auth);
        return permission == null || context.allowed(permission) ? null : ElclMessage.of("ELC0401", context.user(), permission.name());
    }

    @Override
    public void logCommand(String command) {}

    @Override
    public void message(ElclMessage message) {
        plc.log(message);
    }

    @Override
    public void escaped(String program, ElclMessage message) {
        plc.log(message);
    }

    @Override
    public void failed(ElclMessage message) {
        plc.faulted(message);
    }

    @Override
    public long gameTime() {
        return context.server().overworld().getGameTime();
    }

    @Override
    public long dayTime() {
        ElclSystem system = system();
        return system != null ? system.ticks() : context.server().overworld().getOverworldClockTime();
    }

    @Override
    public int maxList() {
        return ElclConfig.maxListSize();
    }
}
