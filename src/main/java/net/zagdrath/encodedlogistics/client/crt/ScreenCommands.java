/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;
import net.zagdrath.encodedlogistics.elcl.parse.Parser;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;

// The commands that open a screen (screens handoff D: WRKLIB, WRKMBR LIB(), EDTMBR MBR(), WRKACTJOB, WRKJOB JOB(),
// DSPJOBLOG JOB(), WRKJOBSCDE, WRKTRGEVT, DSPMSG, WRKSPLF, WRKSYSVAL, and the desk's own WRKINV, WRKDEV, DSPNETSTS,
// WRKCRFJOB; the database's WRKF LIB(), DSPPFM, DSPFD, UPDDTA, and RUNQRY OUTPUT(*DISPLAY)), typed on any command line:
// the screen opens on this terminal, checked against the command's schema first. GO MAIN goes back to the main menu,
// SIGNOFF signs off. RUNQRY's other outputs run on the server as any command does.
final class ScreenCommands {
    private ScreenCommands() {}

    // True when the line was a screen's command (opened, or its problem on the message line).
    static boolean open(CrtTerminal screen, String line) {
        Parser.Result parsed = Parser.parseCommand(line);
        if (parsed.statements().isEmpty() || parsed.statements().getFirst().definition() == null) {
            return false;
        }
        Stmt statement = parsed.statements().getFirst();
        CrtPanel panel = panel(screen, statement);
        if (panel == null && !handled(screen, statement)) {
            return false;
        }
        List<Diagnostic> problems = parsed.diagnostics().isEmpty() ? Compiler.checkCommand(statement) : parsed.diagnostics();
        if (!problems.isEmpty()) {
            screen.message(problems.getFirst().message().toString());
            return true;
        }
        if (panel != null) {
            screen.push(panel);
        } else {
            act(screen, statement);
        }
        return true;
    }

    static String value(Stmt statement, String keyword, String fallback) {
        Expr value = statement.value(keyword);
        if (value == null) {
            return fallback;
        }
        return value instanceof Expr.Str str ? str.value() : value.toString().toUpperCase(Locale.ROOT);
    }

    private static @Nullable CrtPanel panel(CrtTerminal screen, Stmt statement) {
        return switch (statement.name()) {
            case "WRKLIB" -> new WrkLibPanel(screen);
            case "WRKMBR" -> {
                String library = value(statement, "LIB", "*CURLIB");
                yield new WrkMbrPanel(screen, library.equals("*CURLIB") ? screen.currentLibrary : library);
            }
            case "WRKINV" -> new InventoryPanel(screen);
            case "WRKDEV" -> new DevicesPanel(screen);
            case "WRKMCH" -> new WrkMchPanel(screen);
            case "DSPNETSTS" -> new StatusPanel(screen);
            case "WRKCRFJOB" -> new JobsPanel(screen);
            case "WRKACTJOB" -> new WrkActJobPanel(screen);
            case "WRKJOB" -> new WrkJobPanel(screen, value(statement, "JOB", "*"));
            case "DSPJOBLOG" -> new DspJobLogPanel(screen, value(statement, "JOB", "*"));
            case "WRKJOBSCDE" -> new WrkJobScdePanel(screen);
            case "WRKTRGEVT" -> new WrkTrgEvtPanel(screen);
            case "DSPMSG" -> new DspMsgPanel(screen, value(statement, "USR", "*CURRENT"));
            case "WRKSYSVAL" -> new WrkSysvalPanel(screen);
            case "WRKSPLF" -> {
                String job = value(statement, "JOB", "*ALL");
                yield new WrkSplfPanel(screen, job.equals("*ALL") ? null : job);
            }
            case "WRKF" -> {
                String library = value(statement, "LIB", "*CURLIB");
                yield new WrkFPanel(screen, library.equals("*CURLIB") ? screen.currentLibrary : library);
            }
            case "DSPPFM" -> new DspPfmPanel(screen, value(statement, "FILE", ""));
            case "DSPFD" -> new DspFdPanel(screen, value(statement, "FILE", ""));
            case "UPDDTA" -> {
                // ELSYS's files are read-only (act says so).
                String file = value(statement, "FILE", "");
                yield file.startsWith("ELSYS/") ? null : new UpdDtaPanel(screen, file);
            }
            case "RUNQRY" -> {
                if (!value(statement, "OUTPUT", "*DISPLAY").equals("*DISPLAY")) {
                    yield null;
                }
                Stmt.Param sort = statement.param("SORT");
                yield new DspPfmPanel(screen, value(statement, "FILE", ""), value(statement, "QRYSLT", "*ALL"),
                        sort == null ? "" : String.join(" ", sort.values().stream().map(Expr::toString).toList()));
            }
            case "EDTMBR" -> {
                String member = value(statement, "MBR", "");
                int slash = member.indexOf('/');
                String library = slash < 0 || member.startsWith("*") ? screen.currentLibrary : member.substring(0, slash);
                yield new EditorPanel(screen, library, member.substring(slash + 1), library.equals("ELSYS"));
            }
            default -> null;
        };
    }

    // GO, SIGNOFF and UPDDTA on ELSYS.
    private static boolean handled(CrtTerminal screen, Stmt statement) {
        return statement.is("GO") || statement.is("SIGNOFF") || statement.is("UPDDTA");
    }

    private static void act(CrtTerminal screen, Stmt statement) {
        switch (statement.name()) {
            case "GO" -> {
                String menu = value(statement, "MENU", "MAIN");
                if (menu.equals("HELP")) {
                    screen.help();
                } else if (menu.equals("MAIN")) {
                    screen.home();
                } else {
                    screen.message(CrtPanel.tr("crt.encodedlogistics.msg.no_menu", menu));
                }
            }
            case "UPDDTA" -> screen.message(ElclMessage.of("ELC0205", "ELSYS").toString());
            default -> screen.signOff();
        }
    }
}
