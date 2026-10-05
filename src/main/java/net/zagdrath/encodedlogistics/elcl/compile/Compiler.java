/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.compile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.ElclMessages;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef.Kind;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef.VarType;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;
import net.zagdrath.encodedlogistics.elcl.parse.Parser;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;

// Source to a program (ELCL_SPEC.md 1, 3-8, 10): parses the member, then checks it - PGM first and ENDPGM last, DCLs
// before anything else, program-level MONMSGs straight after them, every variable declared and of a type its use
// takes, every command's parameters against its schema (required, special values, ranges, RTN* variables), blocks
// matched (DO/ENDDO, FOREACH/ENDFOR, SELECT/ENDSELECT, SUBR/ENDSUBR; ELSE after its IF, WHEN in a SELECT, LEAVE and
// ITERATE in a loop) and every GOTO's label there and not inside a loop or DO group it's outside of. Any message of
// severity 20 or more means no program. Also gathers the cross reference for the listing.
//
// DCLF (with the DCLs) declares a variable for each field of a file's record format, which a FileResolver supplies;
// the file operations (RCVF, CHNRCD, WRTRCD...) must name a declared file's open ID (ELC2206). The formats used come
// back with the result (formats, by "LIB/FILE" as written) so the program can keep them.
public final class Compiler {
    // A variable's (or label's) uses for the cross reference: the line, and whether it's set there.
    public record Ref(int line, boolean modified) {}

    public record Result(@Nullable CompiledProgram program, List<Diagnostic> diagnostics, Map<String, VarDecl> variables,
            Map<String, List<Ref>> variableRefs, Map<String, List<Ref>> labelRefs, Map<String, List<Ref>> subroutineRefs,
            Map<String, RecordFormat> formats) {
        public boolean ok() {
            return program != null;
        }

        // The worst severity among the messages (0 when there are none).
        public int maxSeverity() {
            return diagnostics.stream().mapToInt(d -> d.message().severity()).max().orElse(0);
        }

        public @Nullable Diagnostic firstError() {
            return diagnostics.stream().filter(Diagnostic::isError).findFirst().orElse(null);
        }
    }

    private static final Set<String> BLOCK_OPENERS = Set.of("DO", "DOWHILE", "DOUNTIL", "DOFOR", "FOREACH");
    private static final Set<String> LOOPS = Set.of("DOWHILE", "DOUNTIL", "DOFOR", "FOREACH");
    private static final Set<String> DO_KINDS = Set.of("DO", "DOWHILE", "DOUNTIL", "DOFOR");
    // Never inside THEN() / EXEC() / CMD().
    private static final Set<String> NOT_NESTED = Set.of("PGM", "ENDPGM", "DCL", "DCLF", "ENDDO", "ENDFOR", "SELECT", "ENDSELECT", "SUBR", "ENDSUBR", "ELSE",
            "WHEN", "OTHERWISE", "MONMSG");

    // Built-ins: argument counts and what they give (null: the type of their numeric arguments).
    private record Builtin(int min, int max, @Nullable VarType result) {}

    private static final Map<String, Builtin> BUILTINS = Map.ofEntries(Map.entry("%SST", new Builtin(3, 3, VarType.CHAR)),
            Map.entry("%TRIM", new Builtin(1, 1, VarType.CHAR)), Map.entry("%TRIML", new Builtin(1, 1, VarType.CHAR)),
            Map.entry("%TRIMR", new Builtin(1, 1, VarType.CHAR)), Map.entry("%LEN", new Builtin(1, 1, VarType.INT)),
            Map.entry("%UPPER", new Builtin(1, 1, VarType.CHAR)), Map.entry("%LOWER", new Builtin(1, 1, VarType.CHAR)),
            Map.entry("%SCAN", new Builtin(2, 3, VarType.INT)), Map.entry("%CHAR", new Builtin(1, 1, VarType.CHAR)),
            Map.entry("%INT", new Builtin(1, 1, VarType.INT)), Map.entry("%DEC", new Builtin(1, 3, VarType.DEC)),
            Map.entry("%ABS", new Builtin(1, 1, null)), Map.entry("%MIN", new Builtin(2, 2, null)), Map.entry("%MAX", new Builtin(2, 2, null)),
            Map.entry("%SIZE", new Builtin(1, 1, VarType.INT)), Map.entry("%ELEM", new Builtin(2, 2, VarType.CHAR)),
            Map.entry("%NAME", new Builtin(1, 1, VarType.CHAR)));

    // The file operations: each names a declared file by OPNID.
    private static final Set<String> FILE_OPS = Set.of("RCVF", "POSDBF", "CLOF", "CHNRCD", "WRTRCD", "UPDRCD", "DLTRCD");
    // Files a program may declare (DCLF).
    public static final int MAX_FILES = 5;

    // What a program is compiled for: a job (batch or interactive), a PLC on a network (every command, RETAIN), or a
    // PLC that isn't cabled to one (only the language, its own faces and modules: anything else is ELC1502).
    public enum Target {
        JOB, PLC, PLC_LOCAL;

        public boolean plc() {
            return this != JOB;
        }
    }

    // What a PLC runs without a network: the language statements, the list commands, the delays, its own faces
    // (RTVRSIN / CHGRSOUT DEV(*SELF)) and its sensor modules (RTVSNSVAL).
    private static final Set<String> PLC_LOCAL_COMMANDS = Set.of("PGM", "ENDPGM", "DCL", "CHGVAR", "IF", "ELSE", "DO", "ENDDO", "DOWHILE", "DOUNTIL",
            "DOFOR", "FOREACH", "ENDFOR", "LEAVE", "ITERATE", "SELECT", "WHEN", "OTHERWISE", "ENDSELECT", "GOTO", "RETURN", "SUBR", "ENDSUBR", "CALLSUBR",
            "MONMSG", "RCVMSG", "SNDPGMMSG", "ADDLSTE", "RMVLSTE", "CLRLST", "DLYJOB", "DLYTICK", "RTVSNSVAL", "RTVRSIN", "CHGRSOUT");

    // Whether a PLC without a network can run a command (by name; DEV given or not: anything but *SELF needs a network).
    public static boolean plcLocal(String command, @Nullable String device) {
        return PLC_LOCAL_COMMANDS.contains(command.toUpperCase(Locale.ROOT)) && (device == null || device.trim().equalsIgnoreCase("*SELF"));
    }

    private Compiler() {}

    public static Result compile(List<SourceLine> source) {
        return compileTexts(SourceLine.texts(source), FileResolver.NONE);
    }

    public static Result compile(List<SourceLine> source, FileResolver files) {
        return compileTexts(SourceLine.texts(source), files);
    }

    public static Result compileTexts(List<String> texts) {
        return compileTexts(texts, FileResolver.NONE);
    }

    public static Result compile(List<SourceLine> source, FileResolver files, Target target) {
        return compileTexts(SourceLine.texts(source), files, target);
    }

    public static Result compileTexts(List<String> texts, FileResolver files) {
        return compileTexts(texts, files, Target.JOB);
    }

    public static Result compileTexts(List<String> texts, FileResolver files, Target target) {
        Parser.Result parsed = Parser.parse(texts);
        Checker checker = new Checker(false, files, target);
        checker.diagnostics.addAll(parsed.diagnostics());
        CompiledProgram program = checker.program(parsed.statements(), Math.max(0, texts.size() - 1));
        List<Diagnostic> diagnostics = new ArrayList<>(checker.diagnostics);
        diagnostics.sort(Comparator.comparingInt(Diagnostic::line));
        boolean failed = diagnostics.stream().anyMatch(Diagnostic::isError);
        return new Result(failed ? null : program, List.copyOf(diagnostics), checker.variables, checker.variableRefs, checker.labelRefs,
                checker.subroutineRefs, Map.copyOf(checker.formats));
    }

    // One command typed on a command line (no variables there): its problems, empty when it can run. RTN*
    // parameters aren't required on the command line - their values are shown instead.
    public static List<Diagnostic> checkCommand(Stmt statement) {
        Checker checker = new Checker(true, FileResolver.NONE, Target.JOB);
        checker.statement(statement, null);
        return checker.diagnostics;
    }

    // DCLF FILE(LIB/NAME) as {library, file}: unqualified is *LIBL.
    public static String[] qualified(@Nullable Expr file) {
        String text = (file == null ? "" : file instanceof Expr.Str str ? str.value() : file.toString()).strip().toUpperCase(Locale.ROOT);
        int slash = text.indexOf('/');
        return slash >= 0 ? new String[] { text.substring(0, slash), text.substring(slash + 1) } : new String[] { "*LIBL", text };
    }

    // A file operation's or DCLF's open ID: "" for none (OPNID(*NONE) or not given).
    public static String opnid(Stmt statement) {
        Expr value = statement.value("OPNID");
        String text = value == null ? "" : value.toString().toUpperCase(Locale.ROOT);
        return text.equals("*NONE") ? "" : text;
    }

    // --- The checks ---

    private enum Type {
        CHAR, INT, DEC, LGL, LIST, ANY;

        static Type of(VarType type) {
            return switch (type) {
                case CHAR -> CHAR;
                case INT -> INT;
                case DEC -> DEC;
                case LGL -> LGL;
                case LIST -> LIST;
            };
        }

        boolean numeric() {
            return this == INT || this == DEC || this == ANY;
        }

        boolean text() {
            return this == CHAR || this == ANY;
        }

        String special() {
            return this == ANY ? "*CHAR" : "*" + name();
        }
    }

    // An open block: what opened it (DO, DOWHILE, ..., SELECT, SUBR), the statement owning a DO (IF, ELSE, WHEN,
    // OTHERWISE, MONMSG; null for a plain DO), its loop label, its line, and (for a MONMSG's DO) the command the
    // MONMSG belongs to, so another MONMSG may follow its ENDDO.
    private record Frame(int id, String kind, @Nullable String owner, @Nullable String label, int line, @Nullable Stmt monitored) {}

    private record Label(int line, List<Integer> path) {}

    private record Jump(String label, int line, List<Integer> path) {}

    private enum Phase {
        START, DCL, MONMSG, BODY, END
    }

    private static final class Checker {
        final boolean interactive;
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final Map<String, VarDecl> variables = new LinkedHashMap<>();
        final Map<String, List<Ref>> variableRefs = new LinkedHashMap<>(), labelRefs = new LinkedHashMap<>(), subroutineRefs = new LinkedHashMap<>();
        final Deque<Frame> frames = new ArrayDeque<>();
        final Map<String, Label> labels = new HashMap<>();
        final Map<String, Integer> labelIndex = new LinkedHashMap<>(), subroutines = new LinkedHashMap<>();
        final List<Jump> gotos = new ArrayList<>();
        final List<Jump> calls = new ArrayList<>();
        int nextFrame;
        boolean elseAllowed;
        // A SUBR has come: from there on only subroutines, then ENDPGM.
        boolean subroutinesBegun;
        // The SELECTs (by frame id) whose OTHERWISE has come.
        final Set<Integer> otherwiseSeen = new HashSet<>();
        @Nullable Stmt lastCommand;
        // DCLF: where formats come from, the files declared (by open ID), the formats used (by LIB/FILE as written), and
        // whether a file the resolver didn't know leaves variables unchecked (the editor's lenient check).
        final FileResolver files;
        final Map<String, DeclaredFile> declaredFiles = new LinkedHashMap<>();
        final Map<String, RecordFormat> formats = new LinkedHashMap<>();
        boolean unknownVariables;

        final Target target;

        Checker(boolean interactive, FileResolver files, Target target) {
            this.interactive = interactive;
            this.files = files;
            this.target = target;
        }

        void report(int line, String id, Object... data) {
            diagnostics.add(Diagnostic.of(line, id, data));
        }

        // --- The program ---

        @Nullable CompiledProgram program(List<Stmt> statements, int lastLine) {
            declarations(statements);
            List<String> params = new ArrayList<>();
            List<Stmt> monitors = new ArrayList<>();
            Phase phase = Phase.START;
            for (int index = 0; index < statements.size(); index++) {
                Stmt s = statements.get(index);
                if (s.definition() == null) {
                    // Unknown command (ELC0101) or not a command at all: already reported.
                    lastCommand = null;
                    continue;
                }
                if (phase == Phase.END) {
                    report(s.firstLine(), "ELC0001", s.name());
                    continue;
                }
                if (phase == Phase.START) {
                    phase = Phase.DCL;
                    if (s.is("PGM")) {
                        statement(s, null);
                        Stmt.Param parm = s.param("PARM");
                        if (parm != null) {
                            for (Expr value : parm.values()) {
                                if (value instanceof Expr.Var var) {
                                    params.add(var.name());
                                }
                            }
                        }
                        continue;
                    }
                    report(s.firstLine(), "ELC0009", s.name(), "PGM");
                }
                for (String name : s.labels()) {
                    label(s, name, index);
                }
                switch (s.name()) {
                    case "PGM" -> report(s.firstLine(), "ELC0001", s.name());
                    case "DCL", "DCLF" -> {
                        if (phase != Phase.DCL) {
                            report(s.firstLine(), "ELC0016");
                        }
                        if (s.is("DCLF")) {
                            statement(s, null);
                        }
                    }
                    case "MONMSG" -> {
                        if ((phase == Phase.DCL || phase == Phase.MONMSG) && frames.isEmpty()) {
                            phase = Phase.MONMSG;
                            monitors.add(s);
                            statement(s, null);
                            opensBlocks(s, "EXEC", "MONMSG", null);
                        } else if (lastCommand == null) {
                            report(s.firstLine(), "ELC0009", "MONMSG", "command");
                            statement(s, null);
                        } else {
                            // Command-level: it stays with that command (a MONMSG after this one, or after its
                            // EXEC(DO) group, monitors it too).
                            statement(s, null);
                            opensBlocks(s, "EXEC", "MONMSG", lastCommand);
                        }
                        elseAllowed = false;
                    }
                    case "ENDPGM" -> {
                        for (Frame frame : frames) {
                            report(frame.line(), "ELC0009", frame.kind(), closer(frame.kind()));
                        }
                        frames.clear();
                        phase = Phase.END;
                    }
                    default -> {
                        phase = Phase.BODY;
                        body(s);
                    }
                }
            }
            if (phase != Phase.END) {
                for (Frame frame : frames) {
                    report(frame.line(), "ELC0009", frame.kind(), closer(frame.kind()));
                }
                report(statements.isEmpty() ? 0 : statements.getLast().lastLine(), "ELC0009", "PGM", "ENDPGM");
            }
            for (Jump jump : gotos) {
                Label label = labels.get(jump.label());
                if (label == null) {
                    report(jump.line(), "ELC0010", jump.label());
                } else if (!prefix(label.path(), jump.path())) {
                    report(jump.line(), "ELC0014");
                }
            }
            for (Jump call : calls) {
                if (!subroutines.containsKey(call.label())) {
                    report(call.line(), "ELC0010", call.label());
                }
            }
            return new CompiledProgram(List.copyOf(params), Map.copyOf(variables), List.copyOf(statements), Map.copyOf(labelIndex), Map.copyOf(subroutines),
                    List.copyOf(monitors), Map.copyOf(declaredFiles));
        }

        private static String closer(String opener) {
            return switch (opener) {
                case "FOREACH" -> "ENDFOR";
                case "SELECT" -> "ENDSELECT";
                case "SUBR" -> "ENDSUBR";
                default -> "ENDDO";
            };
        }

        private static boolean prefix(List<Integer> outer, List<Integer> inner) {
            return outer.size() <= inner.size() && inner.subList(0, outer.size()).equals(outer);
        }

        private List<Integer> path() {
            List<Integer> path = new ArrayList<>();
            frames.descendingIterator().forEachRemaining(frame -> path.add(frame.id()));
            return path;
        }

        private void label(Stmt s, String name, int index) {
            if (labels.containsKey(name)) {
                report(s.firstLine(), "ELC0001", name + ":");
                return;
            }
            labels.put(name, new Label(s.firstLine(), path()));
            labelIndex.put(name, index);
            labelRefs.computeIfAbsent(name, k -> new ArrayList<>()).add(new Ref(s.firstLine(), false));
        }

        private void push(String kind, @Nullable String owner, @Nullable String label, int line, @Nullable Stmt monitored) {
            frames.push(new Frame(nextFrame++, kind, owner, label, line, monitored));
            lastCommand = null;
        }

        // A statement in the program's body: blocks opened and closed, ELSE / WHEN placement, jumps.
        private void body(Stmt s) {
            boolean allowed = elseAllowed;
            elseAllowed = false;
            Frame top = frames.peek();
            String name = s.name();
            if (top != null && top.kind().equals("SELECT") && !name.equals("WHEN") && !name.equals("OTHERWISE") && !name.equals("ENDSELECT")) {
                report(s.firstLine(), "ELC0009", name, "WHEN");
            }
            // Subroutines go after the main body, before ENDPGM: nothing else between or after them.
            if (subroutinesBegun && frames.isEmpty() && !name.equals("SUBR")) {
                report(s.firstLine(), "ELC0001", name);
            }
            // A SELECT's OTHERWISE comes once, after its WHENs.
            if (top != null && top.kind().equals("SELECT") && (name.equals("WHEN") || name.equals("OTHERWISE"))) {
                if (otherwiseSeen.contains(top.id())) {
                    report(s.firstLine(), "ELC0001", name);
                } else if (name.equals("OTHERWISE")) {
                    otherwiseSeen.add(top.id());
                }
            }
            statement(s, null);
            // A MONMSG after it monitors it (opening a block clears that: nothing to monitor inside it yet).
            lastCommand = s;
            switch (name) {
                case "IF" -> opensBlocks(s, "THEN", "IF", null);
                case "ELSE" -> {
                    if (!allowed) {
                        report(s.firstLine(), "ELC0009", "ELSE", "IF");
                    }
                    opensBlocks(s, "CMD", "ELSE", null);
                }
                case "DO", "DOWHILE", "DOUNTIL", "DOFOR", "FOREACH", "SELECT" -> push(name, null, s.label(), s.firstLine(), null);
                case "WHEN", "OTHERWISE" -> {
                    if (top == null || !top.kind().equals("SELECT")) {
                        report(s.firstLine(), "ELC0009", name, "SELECT");
                    }
                    opensBlocks(s, name.equals("WHEN") ? "THEN" : "CMD", name, null);
                }
                case "ENDDO", "ENDFOR", "ENDSELECT", "ENDSUBR" -> close(s, top);
                case "SUBR" -> {
                    if (!frames.isEmpty()) {
                        report(s.firstLine(), "ELC0009", frames.peek().kind(), closer(frames.peek().kind()));
                    }
                    Expr subr = s.value("SUBR");
                    String subrName = subr != null ? subr.toString() : "";
                    if (subroutines.containsKey(subrName)) {
                        report(s.firstLine(), "ELC0103", subrName, "SUBR");
                    }
                    subroutines.put(subrName, -1);
                    subroutinesBegun = true;
                    subroutineRefs.computeIfAbsent(subrName, k -> new ArrayList<>()).add(new Ref(s.firstLine(), false));
                    push("SUBR", null, null, s.firstLine(), null);
                }
                default -> jumps(s);
            }
        }

        // ENDDO / ENDFOR / ENDSELECT / ENDSUBR: closes the block on top, if it's theirs.
        private void close(Stmt s, @Nullable Frame top) {
            String name = s.name();
            boolean matches = top != null && switch (name) {
                case "ENDDO" -> DO_KINDS.contains(top.kind());
                case "ENDFOR" -> top.kind().equals("FOREACH");
                case "ENDSELECT" -> top.kind().equals("SELECT");
                default -> top.kind().equals("SUBR");
            };
            if (!matches) {
                String opener = switch (name) {
                    case "ENDDO" -> "DO";
                    case "ENDFOR" -> "FOREACH";
                    case "ENDSELECT" -> "SELECT";
                    default -> "SUBR";
                };
                report(s.firstLine(), "ELC0009", name, opener);
                lastCommand = null;
                return;
            }
            frames.pop();
            lastCommand = top.monitored();
            elseAllowed = "IF".equals(top.owner());
        }

        // A statement whose THEN / CMD / EXEC holds a block opener opens that block; an IF there may open one through
        // its own THEN. A plain command there leaves an IF's ELSE allowed next.
        private void opensBlocks(Stmt s, String keyword, String owner, @Nullable Stmt monitored) {
            Stmt nested = s.nested(keyword);
            if (nested == null) {
                elseAllowed = owner.equals("IF");
                return;
            }
            if (BLOCK_OPENERS.contains(nested.name())) {
                push(nested.name(), owner, null, s.firstLine(), monitored);
                return;
            }
            if (nested.is("IF")) {
                opensBlocks(nested, "THEN", "IF", null);
                return;
            }
            jumps(nested);
            elseAllowed = owner.equals("IF");
        }

        // GOTO, LEAVE, ITERATE and CALLSUBR (as statements or nested in THEN / EXEC / CMD): their targets.
        private void jumps(Stmt s) {
            switch (s.name()) {
                case "GOTO" -> {
                    Expr label = s.value("CMDLBL");
                    if (label != null) {
                        gotos.add(new Jump(label.toString(), s.firstLine(), path()));
                        labelRefs.computeIfAbsent(label.toString(), k -> new ArrayList<>()).add(new Ref(s.firstLine(), false));
                    }
                }
                case "LEAVE", "ITERATE" -> {
                    Expr label = s.value("CMDLBL");
                    String target = label == null || label.toString().equals("*CURRENT") ? null : label.toString();
                    boolean found = false, loop = false;
                    for (Frame frame : frames) {
                        if (frame.kind().equals("SUBR")) {
                            break;
                        }
                        if (LOOPS.contains(frame.kind())) {
                            loop = true;
                            if (target == null || target.equals(frame.label())) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!loop) {
                        report(s.firstLine(), "ELC0009", s.name(), "DOWHILE");
                    } else if (!found) {
                        report(s.firstLine(), "ELC0010", target);
                    }
                    if (target != null) {
                        labelRefs.computeIfAbsent(target, k -> new ArrayList<>()).add(new Ref(s.firstLine(), false));
                    }
                }
                case "CALLSUBR" -> {
                    Expr subr = s.value("SUBR");
                    if (subr != null) {
                        calls.add(new Jump(subr.toString(), s.firstLine(), path()));
                        subroutineRefs.computeIfAbsent(subr.toString(), k -> new ArrayList<>()).add(new Ref(s.firstLine(), false));
                    }
                }
                default -> {}
            }
        }

        // --- Declarations (a first pass, so a variable used before its DCL - PGM PARM - is known) ---

        private void declarations(List<Stmt> statements) {
            for (Stmt s : statements) {
                if (s.is("DCLF") && s.definition() != null) {
                    declareFile(s);
                    continue;
                }
                if (!s.is("DCL") || s.definition() == null) {
                    continue;
                }
                if (!(s.value("VAR") instanceof Expr.Var var)) {
                    if (s.value("VAR") != null) {
                        report(s.firstLine(), "ELC0103", s.value("VAR"), "VAR");
                    }
                    continue;
                }
                if (variables.containsKey(var.name())) {
                    report(s.firstLine(), "ELC0103", var.name(), "VAR");
                    continue;
                }
                Expr typeValue = s.value("TYPE");
                VarType type = typeValue instanceof Expr.Special special ? VarType.of(special.name()) : null;
                if (type == null) {
                    // Reported with the statement's own checks.
                    type = VarType.CHAR;
                }
                int length = type == VarType.CHAR ? 32 : type == VarType.DEC ? 15 : 0, decimals = type == VarType.DEC ? 5 : 0;
                Stmt.Param len = s.param("LEN");
                if (len != null && !len.values().isEmpty()) {
                    Long total = integer(len.values().getFirst()), dec = len.values().size() > 1 ? integer(len.values().get(1)) : null;
                    switch (type) {
                        case CHAR -> {
                            if (len.values().size() > 1) {
                                report(s.firstLine(), "ELC0103", len.values().get(1), "LEN");
                            } else if (total != null && (total < 1 || total > 1024)) {
                                report(s.firstLine(), "ELC0004", total);
                            } else if (total != null) {
                                length = total.intValue();
                            }
                        }
                        case DEC -> {
                            if (total != null && (total < 1 || total > 31)) {
                                report(s.firstLine(), "ELC0004", total);
                            } else if (dec != null && (dec < 0 || total != null && dec > total)) {
                                report(s.firstLine(), "ELC0004", dec);
                            } else if (total != null) {
                                length = total.intValue();
                                decimals = dec != null ? dec.intValue() : 0;
                            }
                        }
                        default -> report(s.firstLine(), "ELC0103", len.values().getFirst(), "LEN");
                    }
                }
                Expr retainValue = s.value("RETAIN");
                boolean retain = retainValue != null && retainValue.toString().equalsIgnoreCase("*YES");
                if (retain && !target.plc()) {
                    report(s.firstLine(), "ELC1506");
                }
                VarDecl decl = new VarDecl(var.name(), type, length, decimals, s.value("VALUE"), s.firstLine(), retain);
                variables.put(var.name(), decl);
                variableRefs.computeIfAbsent(var.name(), k -> new ArrayList<>()).add(new Ref(s.firstLine(), false));
            }
            // Initial values, now every variable is known.
            for (VarDecl decl : variables.values()) {
                if (decl.value() != null) {
                    if (decl.type() == VarType.LIST) {
                        report(decl.line(), "ELC0103", decl.value(), "VALUE");
                    } else {
                        assign(decl, decl.value(), decl.line(), true);
                    }
                }
            }
        }

        // DCLF FILE() OPNID(): the file's format from the resolver, a variable for each field (&FIELD, &OPNID_FIELD). A
        // second file with the same open ID, more than MAX_FILES, or a variable already declared is ELC0103 / ELC2224;
        // a file there's no format for ELC2205 (or, lenient, its variables go unchecked).
        private void declareFile(Stmt s) {
            String[] file = qualified(s.value("FILE"));
            String opnid = opnid(s);
            if (file[1].isEmpty()) {
                return;
            }
            if (declaredFiles.containsKey(opnid)) {
                report(s.firstLine(), "ELC0103", opnid.isEmpty() ? "*NONE" : opnid, "OPNID");
                return;
            }
            if (declaredFiles.size() >= MAX_FILES) {
                report(s.firstLine(), "ELC2224", "DCLF", MAX_FILES);
                return;
            }
            RecordFormat format = files.format(file[0], file[1]);
            if (format == null) {
                if (files.lenient()) {
                    unknownVariables = true;
                    declaredFiles.put(opnid, new DeclaredFile(opnid, file[0], file[1], null));
                } else {
                    report(s.firstLine(), "ELC2205", file[1], file[0]);
                }
                return;
            }
            DeclaredFile declared = new DeclaredFile(opnid, file[0], file[1], format);
            declaredFiles.put(opnid, declared);
            formats.put(FileResolver.key(file[0], file[1]), format);
            for (FieldDef field : format.fields()) {
                String name = declared.variable(field);
                if (variables.containsKey(name)) {
                    report(s.firstLine(), "ELC0103", name, "DCLF");
                    continue;
                }
                variables.put(name, new VarDecl(name, field.varType(), field.varLength(), field.type() == FieldDef.Type.DEC ? field.decimals() : 0, null,
                        s.firstLine()));
                variableRefs.computeIfAbsent(name, k -> new ArrayList<>()).add(new Ref(s.firstLine(), false));
            }
        }

        // A file operation's OPNID names a declared file (ELC2206); CHNRCD's KEY has no more values than the key has
        // fields, on a keyed file. The file's variables are in the cross reference: set by a read, used by a write.
        private void fileOperation(Stmt s) {
            String opnid = opnid(s);
            DeclaredFile file = declaredFiles.get(opnid);
            if (file == null) {
                report(s.firstLine(), "ELC2206", opnid.isEmpty() ? "*NONE" : opnid);
                return;
            }
            RecordFormat format = file.format();
            if (format == null) {
                return;
            }
            if (s.is("CHNRCD")) {
                Stmt.Param key = s.param("KEY");
                int given = key != null ? key.values().size() : 0;
                if (!format.keyed() || given > format.key().size()) {
                    report(s.firstLine(), "ELC0103", key != null && !key.values().isEmpty() ? key.values().getLast() : "*NONE", "KEY");
                }
            }
            boolean sets = s.is("RCVF") || s.is("CHNRCD");
            if (sets || s.is("WRTRCD") || s.is("UPDRCD")) {
                for (FieldDef field : format.fields()) {
                    List<Ref> refs = variableRefs.get(file.variable(field));
                    if (refs != null) {
                        refs.add(new Ref(s.firstLine(), sets));
                    }
                }
            }
        }

        private static @Nullable Long integer(Expr value) {
            if (value instanceof Expr.Num num) {
                try {
                    return num.value().longValueExact();
                } catch (ArithmeticException e) {
                    return null;
                }
            }
            return null;
        }

        // --- One statement against its schema ---

        // nestedIn: the parameter holding it (THEN, EXEC, CMD...) when nested, else null.
        void statement(Stmt s, @Nullable String nestedIn) {
            CommandDefinition definition = s.definition();
            if (definition == null) {
                return;
            }
            if (nestedIn != null && NOT_NESTED.contains(s.name())) {
                report(s.firstLine(), "ELC0103", s.name(), nestedIn);
                return;
            }
            if (target == Target.PLC_LOCAL) {
                Expr device = s.value("DEV");
                if (!plcLocal(s.name(), device == null ? null : device.toString())) {
                    report(s.firstLine(), "ELC1502", s.name());
                }
            }
            for (Stmt.Param param : s.params()) {
                ParamDef def = param.keyword() != null ? definition.param(param.keyword()) : null;
                // CHGVAR's and DCL's values are checked against their variable's type (assign).
                boolean assigned = (s.is("CHGVAR") || s.is("DCL")) && "VALUE".equals(param.keyword());
                if (def != null && !assigned) {
                    param(s, def, param);
                }
            }
            if (s.broken()) {
                return;
            }
            for (ParamDef def : definition.params()) {
                if (def.required() && s.param(def.keyword()) == null && !(interactive && def.isReturn())) {
                    report(s.firstLine(), "ELC0102", def.keyword());
                }
            }
            if (FILE_OPS.contains(s.name()) && !interactive) {
                fileOperation(s);
            }
            // Parameters required only together with another's value.
            switch (s.name()) {
                case "CHGDEVFTR" -> {
                    Expr action = s.value("ACTION");
                    if (action != null && !action.toString().equals("*CLR") && s.param("ITEM") == null) {
                        report(s.firstLine(), "ELC0102", "ITEM");
                    }
                }
                case "DLYJOB" -> {
                    if (s.param("DLY") == null && s.param("RSMTIME") == null) {
                        report(s.firstLine(), "ELC0102", "DLY");
                    }
                }
                case "ADDJOBSCDE" -> {
                    Expr frequency = s.value("FRQ");
                    if (frequency != null && frequency.toString().equals("*INTERVAL") && s.param("INTERVAL") == null) {
                        report(s.firstLine(), "ELC0102", "INTERVAL");
                    }
                }
                case "RUNQRY" -> {
                    Expr output = s.value("OUTPUT");
                    if (output != null && output.toString().equals("*OUTFILE") && s.param("OUTFILE") == null) {
                        report(s.firstLine(), "ELC0102", "OUTFILE");
                    }
                }
                case "CHGVAR" -> {
                    if (s.value("VAR") instanceof Expr.Var var && variables.get(var.name()) != null && s.value("VALUE") != null) {
                        VarDecl decl = variables.get(var.name());
                        if (decl.type() == VarType.LIST) {
                            report(s.firstLine(), "ELC0103", s.value("VALUE"), "VALUE");
                        } else {
                            assign(decl, s.value("VALUE"), s.firstLine(), false);
                        }
                    }
                }
                default -> {}
            }
        }

        private void param(Stmt s, ParamDef def, Stmt.Param param) {
            List<Expr> values = param.values();
            if (values.size() > def.maxValues()) {
                report(param.line(), "ELC0103", values.get(def.maxValues()), def.keyword());
                return;
            }
            for (Expr value : values) {
                value(s, def, value);
            }
        }

        private void value(Stmt s, ParamDef def, Expr value) {
            int line = value.line();
            String keyword = def.keyword();
            if (value instanceof Expr.Special special && def.kind() != Kind.LGL && def.kind() != Kind.VALUE) {
                if (!def.acceptsSpecial(special.name())) {
                    report(line, "ELC0103", special.name(), keyword);
                }
                return;
            }
            switch (def.kind()) {
                case VARIABLE -> {
                    if (!(value instanceof Expr.Var var)) {
                        report(line, "ELC0103", value, keyword);
                        return;
                    }
                    if (s.is("DCL")) {
                        return;
                    }
                    VarDecl decl = variables.get(var.name());
                    if (decl == null) {
                        if (!unknownVariables) {
                            report(line, "ELC0002", var.name());
                        }
                        return;
                    }
                    boolean modified = def.isReturn() || s.is("CHGVAR") && keyword.equals("VAR");
                    variableRefs.get(var.name()).add(new Ref(line, modified));
                    VarType wanted = def.returns() != null ? def.returns() : keyword.equals("IN") ? VarType.LIST : null;
                    // DOFOR's control variable and CALLSUBR's RTNVAL are *INT, not *DEC.
                    boolean exact = s.is("DOFOR") || s.is("CALLSUBR");
                    if (wanted != null && (exact ? wanted != decl.type() : !compatible(wanted, decl.type()))) {
                        report(line, "ELC0103", var.name(), keyword);
                    }
                }
                case SPECIAL -> {
                    Type type = type(value);
                    if (!(value instanceof Expr.Var) || !type.text()) {
                        report(line, "ELC0103", value, keyword);
                    }
                }
                case INT, DEC -> {
                    Type type = type(value);
                    if (!type.numeric()) {
                        report(line, "ELC0103", value, keyword);
                        return;
                    }
                    BigDecimal literal = literal(value);
                    if (literal != null) {
                        if (def.kind() == Kind.INT && literal.stripTrailingZeros().scale() > 0) {
                            report(line, "ELC0003", value, "*INT");
                        } else if (def.hasRange() && (literal.compareTo(BigDecimal.valueOf(def.min())) < 0 || literal.compareTo(BigDecimal.valueOf(def.max())) > 0)) {
                            report(line, "ELC0004", value);
                        } else if (s.is("DOFOR") && keyword.equals("BY") && literal.signum() == 0) {
                            // A loop that would never end.
                            report(line, "ELC0004", value);
                        }
                    }
                }
                case LGL -> {
                    if (value instanceof Expr.Str str && (str.value().equals("1") || str.value().equals("0"))) {
                        return;
                    }
                    Type type = type(value);
                    if (type != Type.LGL && type != Type.ANY) {
                        report(line, "ELC0103", value, keyword);
                    }
                }
                case CHAR -> {
                    // A literal number is taken as text (VALUE(500)); a number worked out goes through %CHAR.
                    Type type = type(value);
                    if ((type == Type.INT || type == Type.DEC) && literal(value) == null || type == Type.LIST) {
                        report(line, "ELC0103", value, keyword);
                    }
                }
                case NAME, DEVICE, ITEM -> {
                    switch (value) {
                        case Expr.Name name -> {
                            if (def.kind() == Kind.NAME && def.length() <= 10 && name.name().length() > 10) {
                                report(line, "ELC0103", name.name(), keyword);
                            }
                        }
                        case Expr.Str str -> {}
                        case Expr.Num num -> {}
                        case Expr.Path path -> {
                            if (def.kind() != Kind.NAME || def.length() <= 10) {
                                report(line, "ELC0103", path, keyword);
                            }
                        }
                        case Expr.Var var -> {
                            Type type = type(value);
                            if (!type.text()) {
                                report(line, "ELC0103", var.name(), keyword);
                            }
                        }
                        default -> report(line, "ELC0103", value, keyword);
                    }
                }
                case QUALIFIED -> {
                    switch (value) {
                        case Expr.Name name -> {
                            if (name.name().length() > 10) {
                                report(line, "ELC0103", name.name(), keyword);
                            }
                        }
                        case Expr.Path path -> {
                            if (path.parts().size() != 2 || path.parts().stream().anyMatch(part -> part.length() > 10)) {
                                report(line, "ELC0103", path, keyword);
                            }
                        }
                        case Expr.Str str -> {}
                        case Expr.Var var -> {
                            if (!type(value).text()) {
                                report(line, "ELC0103", var.name(), keyword);
                            }
                        }
                        default -> report(line, "ELC0103", value, keyword);
                    }
                }
                case MSGID -> {
                    if (!(value instanceof Expr.Name name) || !ElclMessages.isId(name.name())) {
                        report(line, "ELC0103", value, keyword);
                    }
                }
                case LABEL -> {
                    if (!(value instanceof Expr.Name name) || name.name().length() > 10) {
                        report(line, "ELC0103", value, keyword);
                    }
                }
                case COMMAND -> {
                    if (!(value instanceof Expr.Nested nested)) {
                        report(line, "ELC0103", value, keyword);
                        return;
                    }
                    Stmt inner = nested.command();
                    // A job's command (SBMJOB, a schedule entry) runs on its own: not a statement of this program.
                    CommandDefinition definition = inner.definition();
                    if (!s.definition().context().equals(CommandDefinition.Context.PROGRAM) && definition != null
                            && definition.context() == CommandDefinition.Context.PROGRAM) {
                        report(line, "ELC0103", inner.name(), keyword);
                        return;
                    }
                    statement(inner, keyword);
                }
                case VALUE -> type(value);
                case TIME -> {
                    if (value instanceof Expr.Num num) {
                        String digits = num.text();
                        long time = integer(num) != null ? integer(num) : -1;
                        boolean six = digits.length() == 6;
                        long hours = six ? time / 10_000 : time / 100, minutes = six ? time / 100 % 100 : time % 100, seconds = six ? time % 100 : 0;
                        if (time < 0 || digits.length() != 4 && !six || hours > 23 || minutes > 59 || seconds > 59) {
                            report(line, "ELC0004", digits);
                        }
                    } else if (!(value instanceof Expr.Str) && !(value instanceof Expr.Var)) {
                        report(line, "ELC0103", value, keyword);
                    } else {
                        type(value);
                    }
                }
            }
        }

        private static boolean compatible(VarType wanted, VarType actual) {
            if (wanted == VarType.INT || wanted == VarType.DEC) {
                return actual == VarType.INT || actual == VarType.DEC;
            }
            return wanted == actual;
        }

        private static @Nullable BigDecimal literal(Expr value) {
            if (value instanceof Expr.Num num) {
                return num.value();
            }
            if (value instanceof Expr.Unary unary && unary.op().equals("-") && unary.operand() instanceof Expr.Num num) {
                return num.value().negate();
            }
            return null;
        }

        // The value of an expression of literals alone, worked out as the VM would; null when it isn't one (or it's a
        // whole number past 64 bits, already reported).
        private static @Nullable BigDecimal constant(Expr expr) {
            BigDecimal value = switch (expr) {
                case Expr.Num num -> num.value();
                case Expr.Group group -> constant(group.inner());
                case Expr.Unary unary when unary.op().equals("-") -> {
                    BigDecimal operand = constant(unary.operand());
                    yield operand != null ? operand.negate() : null;
                }
                case Expr.Binary binary -> fold(binary);
                default -> null;
            };
            return value != null && whole(value) && !fits(value) ? null : value;
        }

        private static @Nullable BigDecimal fold(Expr.Binary binary) {
            BigDecimal left = constant(binary.left()), right = constant(binary.right());
            if (left == null || right == null) {
                return null;
            }
            boolean ints = whole(left) && whole(right);
            return switch (binary.op()) {
                case "+" -> left.add(right);
                case "-" -> left.subtract(right);
                case "*" -> left.multiply(right);
                case "/" -> ints && right.signum() != 0 ? left.divide(right, 0, RoundingMode.DOWN) : null;
                case "//" -> {
                    BigDecimal divisor = right.setScale(0, RoundingMode.DOWN);
                    yield divisor.signum() != 0 ? left.setScale(0, RoundingMode.DOWN).remainder(divisor) : null;
                }
                default -> null;
            };
        }

        private static boolean whole(BigDecimal value) {
            return value.stripTrailingZeros().scale() <= 0;
        }

        // Within 64 bits.
        private static boolean fits(BigDecimal value) {
            return value.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) >= 0 && value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0;
        }

        // Within a *DEC's LEN(digits decimals), once rounded to its decimals.
        private static boolean fits(BigDecimal value, int digits, int decimals) {
            BigDecimal rounded = value.setScale(decimals, RoundingMode.HALF_UP);
            return rounded.signum() == 0 || rounded.precision() - rounded.scale() <= digits - decimals;
        }

        // An assignment (CHGVAR, DCL VALUE): the value's type against the variable's; a literal too long for a *CHAR
        // is a warning (ELC0008).
        private void assign(VarDecl decl, Expr value, int line, boolean literalOnly) {
            Type type = type(value);
            switch (decl.type()) {
                case CHAR -> {
                    // Numbers only through %CHAR, a literal one too.
                    if (type == Type.INT || type == Type.DEC || type == Type.LGL || type == Type.LIST) {
                        report(line, "ELC0003", value, "*CHAR");
                    } else if (value instanceof Expr.Str str && str.value().length() > decl.length()) {
                        report(line, "ELC0008", decl.length());
                    }
                }
                case INT, DEC -> {
                    if (!type.numeric()) {
                        report(line, "ELC0003", value instanceof Expr.Str str ? str.value() : value.toString(), decl.type().special());
                    } else if (decl.type() == VarType.INT && literalOnly && literal(value) != null && literal(value).stripTrailingZeros().scale() > 0) {
                        report(line, "ELC0003", value, "*INT");
                    } else if (constant(value) != null) {
                        // A value known now that the variable can't hold (a whole number past 64 bits is reported
                        // where it is).
                        BigDecimal known = constant(value);
                        boolean over = decl.type() == VarType.DEC ? !fits(known, decl.length(), decl.decimals())
                                : !whole(known) && !fits(known.setScale(0, RoundingMode.DOWN));
                        if (over) {
                            report(line, "ELC0007");
                        }
                    }
                }
                case LGL -> {
                    boolean flag = value instanceof Expr.Str str && (str.value().equals("1") || str.value().equals("0"));
                    if (!flag && type != Type.LGL && type != Type.ANY) {
                        report(line, "ELC0003", value instanceof Expr.Str str ? str.value() : value.toString(), "*LGL");
                    }
                }
                case LIST -> {}
            }
        }

        // --- Expressions: their type, reporting what's wrong inside them ---

        private Type type(Expr expr) {
            return switch (expr) {
                case Expr.Str str -> Type.CHAR;
                case Expr.Num num -> {
                    if (num.value().stripTrailingZeros().scale() <= 0) {
                        if (num.value().abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                            report(num.line(), "ELC0007");
                        }
                        yield Type.INT;
                    }
                    yield Type.DEC;
                }
                case Expr.Var var -> {
                    VarDecl decl = variables.get(var.name());
                    if (decl == null) {
                        if (!unknownVariables) {
                            report(var.line(), "ELC0002", var.name());
                        }
                        yield Type.ANY;
                    }
                    variableRefs.get(var.name()).add(new Ref(var.line(), false));
                    yield Type.of(decl.type());
                }
                case Expr.Special special -> special.name().equals("*TRUE") || special.name().equals("*FALSE") ? Type.LGL : Type.ANY;
                case Expr.Name name -> Type.ANY;
                case Expr.Path path -> Type.ANY;
                case Expr.Group group -> type(group.inner());
                case Expr.Nested nested -> {
                    report(nested.line(), "ELC0001", nested.command().name());
                    yield Type.ANY;
                }
                case Expr.Builtin builtin -> builtin(builtin);
                case Expr.Unary unary -> {
                    Type operand = type(unary.operand());
                    if (unary.op().equals("*NOT")) {
                        if (operand != Type.LGL && operand != Type.ANY && !flag(unary.operand())) {
                            report(unary.line(), "ELC0003", unary.operand(), "*LGL");
                        }
                        yield Type.LGL;
                    }
                    if (!operand.numeric()) {
                        report(unary.line(), "ELC0003", unary.operand(), "*DEC");
                        yield Type.ANY;
                    }
                    yield operand;
                }
                case Expr.Binary binary -> binary(binary);
            };
        }

        private static boolean flag(Expr expr) {
            return expr instanceof Expr.Str str && (str.value().equals("1") || str.value().equals("0"));
        }

        private Type binary(Expr.Binary binary) {
            Type left = type(binary.left()), right = type(binary.right());
            String op = binary.op();
            switch (op) {
                case "+", "-", "*", "/", "//" -> {
                    if (!left.numeric()) {
                        report(binary.line(), "ELC0003", binary.left(), "*DEC");
                        return Type.ANY;
                    }
                    if (!right.numeric()) {
                        report(binary.line(), "ELC0003", binary.right(), "*DEC");
                        return Type.ANY;
                    }
                    BigDecimal divisor = literal(binary.right());
                    if ((op.equals("/") || op.equals("//")) && divisor != null && divisor.signum() == 0) {
                        report(binary.line(), "ELC0005");
                    }
                    // Whole numbers worked out now past 64 bits.
                    BigDecimal a = constant(binary.left()), b = constant(binary.right()), folded = fold(binary);
                    if (a != null && b != null && whole(a) && whole(b) && folded != null && !fits(folded)) {
                        report(binary.line(), "ELC0007");
                    }
                    if (op.equals("//")) {
                        return Type.INT;
                    }
                    return left == Type.DEC || right == Type.DEC ? Type.DEC : left == Type.ANY || right == Type.ANY ? Type.ANY : Type.INT;
                }
                case "*CAT", "*BCAT", "*TCAT" -> {
                    for (Expr side : List.of(binary.left(), binary.right())) {
                        Type type = side == binary.left() ? left : right;
                        if ((type == Type.INT || type == Type.DEC) && !(side instanceof Expr.Num) || type == Type.LGL || type == Type.LIST) {
                            report(binary.line(), "ELC0003", side, "*CHAR");
                            return Type.CHAR;
                        }
                    }
                    return Type.CHAR;
                }
                case "*AND", "*OR" -> {
                    for (Expr side : List.of(binary.left(), binary.right())) {
                        Type type = side == binary.left() ? left : right;
                        if (type != Type.LGL && type != Type.ANY && !flag(side)) {
                            report(binary.line(), "ELC0003", side, "*LGL");
                            return Type.LGL;
                        }
                    }
                    return Type.LGL;
                }
                default -> {
                    // Relational: numbers with numbers, text with text (a logical with '1' / '0').
                    boolean ok = left == Type.ANY || right == Type.ANY || left.numeric() && right.numeric() || left == Type.CHAR && right == Type.CHAR
                            || left == Type.LGL && (right == Type.LGL || flag(binary.right())) || right == Type.LGL && flag(binary.left());
                    if (!ok || left == Type.LIST || right == Type.LIST) {
                        report(binary.line(), "ELC0003", binary.right(), left.special());
                    }
                    return Type.LGL;
                }
            }
        }

        private Type builtin(Expr.Builtin builtin) {
            Builtin def = BUILTINS.get(builtin.function());
            // A command line has only literals: no lists, and no items' names.
            boolean program = !interactive || !Set.of("%SIZE", "%ELEM", "%NAME").contains(builtin.function());
            if (def == null || !program || builtin.args().size() < def.min() || builtin.args().size() > def.max()) {
                report(builtin.line(), "ELC0001", builtin.function());
                builtin.args().forEach(this::type);
                return Type.ANY;
            }
            List<Type> args = new ArrayList<>();
            for (Expr arg : builtin.args()) {
                args.add(type(arg));
            }
            switch (builtin.function()) {
                case "%SIZE", "%ELEM" -> {
                    if (args.getFirst() != Type.LIST && args.getFirst() != Type.ANY) {
                        report(builtin.line(), "ELC0003", builtin.args().getFirst(), "*LIST");
                    }
                    if (builtin.function().equals("%ELEM") && !args.get(1).numeric()) {
                        report(builtin.line(), "ELC0003", builtin.args().get(1), "*INT");
                    }
                }
                case "%ABS", "%MIN", "%MAX", "%CHAR" -> {
                    for (int i = 0; i < args.size(); i++) {
                        if (!args.get(i).numeric()) {
                            report(builtin.line(), "ELC0003", builtin.args().get(i), "*DEC");
                        }
                    }
                }
                case "%SST" -> {
                    text(builtin, args, 0);
                    if (!args.get(1).numeric() || !args.get(2).numeric()) {
                        report(builtin.line(), "ELC0003", builtin.args().get(!args.get(1).numeric() ? 1 : 2), "*INT");
                    }
                }
                case "%SCAN" -> {
                    text(builtin, args, 0);
                    text(builtin, args, 1);
                    if (args.size() > 2 && !args.get(2).numeric()) {
                        report(builtin.line(), "ELC0003", builtin.args().get(2), "*INT");
                    }
                }
                case "%TRIM", "%TRIML", "%TRIMR", "%UPPER", "%LOWER", "%LEN", "%NAME" -> text(builtin, args, 0);
                case "%INT", "%DEC" -> {
                    for (int i = 1; i < args.size(); i++) {
                        if (!args.get(i).numeric()) {
                            report(builtin.line(), "ELC0003", builtin.args().get(i), "*INT");
                        }
                    }
                }
                default -> {}
            }
            if (def.result() != null) {
                return Type.of(def.result());
            }
            return args.stream().anyMatch(type -> type == Type.DEC) ? Type.DEC : Type.INT;
        }

        // A built-in's text argument: *CHAR (a number goes through %CHAR first).
        private void text(Expr.Builtin builtin, List<Type> args, int index) {
            if (!args.get(index).text()) {
                report(builtin.line(), "ELC0003", builtin.args().get(index), "*CHAR");
            }
        }
    }
}
