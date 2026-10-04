/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.elcl.exec.ElclCommandLine;
import net.zagdrath.encodedlogistics.elcl.exec.OsCommands;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.vm.Vm;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// CALL typed on a command line (OS.md 5): the program runs in the session's interactive job, over as many ticks as it
// takes (its budget each), and the command line says so at once (ELC0108). When it ends, the terminal gets what it
// said - its messages on Command Entry, and on the message line how it ended: normally (ELC0110) or the escape that
// ended it. One program at a time per interactive job (ELC0109).
public final class InteractiveCalls {
    private InteractiveCalls() {}

    public static void call(Invocation call) throws ElclException {
        TerminalContext context = call.context(TerminalContext.class);
        if (context == null || context.network() == null) {
            throw new ElclException(context == null ? "ELC0107" : "ELC1302", context == null ? "CALL" : "*NETWORK");
        }
        ElclSystem system = new ElclSystem(context.server(), context.network());
        String user = context.user();
        JobService.Job job = OsCommands.interactiveJob(system, user);
        JobManager.Run busy = JobManager.of(context.server()).run(system, job.number());
        if (busy != null) {
            throw new ElclException("ELC0109", job.qualified(), busy.program);
        }
        String target = call.text("PGM").strip().toUpperCase(Locale.ROOT);
        int slash = target.lastIndexOf('/');
        String library = slash >= 0 ? target.substring(0, slash) : "*LIBL", name = slash >= 0 ? target.substring(slash + 1) : target;
        JobManager.Run probe = new JobManager.Run(system, job.number(), user, name, true, context);
        var loaded = new JobVmHost(probe).program(library, name);
        JobManager.Run run = new JobManager.Run(system, job.number(), user, loaded.key(), true, context);
        run.logCommands = job.log();
        List<Object> args = new ArrayList<>(call.list("PARM"));
        Vm vm = Vm.start(new JobVmHost(run), loaded, args);
        int containerId = context.player().containerMenu.containerId;
        JobManager.of(context.server()).start(run, vm, ended -> reply(ended, context.player(), containerId));
        call.send(ElclMessage.of("ELC0108", loaded.key(), job.qualified()));
    }

    // What the program said, to the terminal it was called from (if it's still open).
    private static void reply(JobManager.Run run, ServerPlayer player, int containerId) {
        Vm vm = run.vm();
        ElclMessage end = vm != null && vm.failure() != null ? vm.failure() : ElclMessage.of("ELC0110", run.program);
        ElclServices.jobs().logMessage(run.system, run.job, end);
        if (!(player.containerMenu instanceof TerminalDeskMenu) || player.containerMenu.containerId != containerId) {
            return;
        }
        List<TerminalLine> lines = new ArrayList<>();
        for (ElclMessage message : run.output) {
            lines.add(TerminalLine.of(Component.literal("    " + ElclCommandLine.shown(message)),
                    ElclCommandLine.bright(message) ? TerminalLine.BRIGHT : TerminalLine.NORMAL));
        }
        int unread = ElclServices.messages().unread(run.system, run.user);
        PacketDistributor.sendToPlayer(player, new CrtResponsePayload(containerId, TerminalService.COMMAND, "call", lines,
                Optional.of(Component.literal(ElclCommandLine.shown(end))), unread));
    }
}
