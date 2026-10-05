/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;

// A command line typed at a Terminal Desk, run as ELCL (CommandRunner) in the user's interactive job: the command and
// its messages go in the job log; the screen gets each message "ID  text" (bright for escape and completion messages),
// the RTN* values ("RTNLVL = 7") and, on its message line, the last message.
public final class ElclCommandLine {
    // The completion messages (MESSAGES.md, amendment 2): shown bright like escape messages.
    private static final Set<String> COMPLETION = Set.of("ELC0210", "ELC0211", "ELC0212", "ELC0213", "ELC0214", "ELC0215", "ELC0216", "ELC0217", "ELC0218",
            "ELC0219", "ELC0220", "ELC0221", "ELC0222", "ELC0304", "ELC0307", "ELC0308", "ELC0309", "ELC0311", "ELC0312", "ELC0313", "ELC0314", "ELC0315",
            "ELC0108", "ELC0110", "ELC1307", "ELC1309", "ELC1322", "ELC2230", "ELC2231", "ELC2233", "ELC2234", "ELC2235", "ELC2236", "ELC2237",
            "ELC2238");

    private ElclCommandLine() {}

    public static boolean bright(ElclMessage message) {
        return message.isError() || COMPLETION.contains(message.id());
    }

    // "ELC0210  Library ZAGLIB created."
    public static String shown(ElclMessage message) {
        return message.id() + "  " + message.text();
    }

    public static TerminalOutput run(TerminalContext context, String line) {
        CommandRunner.Result result = CommandRunner.run(context, line);
        log(context, line, result);
        TerminalOutput out = new TerminalOutput();
        List<ElclMessage> messages = result.messages();
        ElclMessage last = result.escape() != null ? result.escape() : messages.isEmpty() ? null : messages.getLast();
        for (ElclMessage message : messages) {
            if (message != last) {
                out.line(TerminalLine.of(Component.literal("    " + shown(message)), bright(message) ? TerminalLine.BRIGHT : TerminalLine.NORMAL));
            }
        }
        for (Map.Entry<String, String> value : result.returns().entrySet()) {
            out.line(TerminalLine.of(Component.literal("    " + value.getKey() + " = " + value.getValue()), TerminalLine.NORMAL));
        }
        if (last != null) {
            out.setMessage(Component.literal(shown(last)));
        }
        return out;
    }

    // Into the interactive job's log (when it logs commands): the command, then every message it sent.
    private static void log(TerminalContext context, String line, CommandRunner.Result result) {
        if (context.network() == null) {
            return;
        }
        ElclSystem system = new ElclSystem(context.server(), context.network());
        JobService.Job job = OsCommands.interactiveJob(system, context.user());
        if (job.number().equals("000000")) {
            return;
        }
        if (job.log()) {
            ElclServices.jobs().logCommand(system, job.number(), line.trim());
        }
        for (ElclMessage message : result.messages()) {
            ElclServices.jobs().logMessage(system, job.number(), message);
        }
        if (result.escape() != null) {
            ElclServices.jobs().logMessage(system, job.number(), result.escape());
        }
    }
}
