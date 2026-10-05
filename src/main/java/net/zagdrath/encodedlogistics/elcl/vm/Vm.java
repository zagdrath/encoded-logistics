/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.vm;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.ElclMessages;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.cmd.Wait;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.compile.DeclaredFile;
import net.zagdrath.encodedlogistics.elcl.compile.FileResolver;
import net.zagdrath.encodedlogistics.elcl.compile.VarDecl;
import net.zagdrath.encodedlogistics.elcl.db.DbRecord;
import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.elcl.db.FileAccess;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;
import net.zagdrath.encodedlogistics.elcl.vm.VmProgram.Insn;
import net.zagdrath.encodedlogistics.elcl.vm.VmProgram.Monitor;

// The ELCL VM (ELCL_SPEC.md 9): runs a job's programs a budget of instructions at a time, and is resumable - all of its
// state (each program's frame: pc, variables, subroutine stack, loop counters, where a monitor resumes, the last
// escape; the call stack; the async wait it's in) saves to NBT and loads back to carry on where it was.
//
// A command that fails raises an escape message; the frame's monitors (command level first, the innermost, then
// program level) take it, else the program ends: the message goes to the job log, and its caller gets ELC0013 at its
// CALL, which it may monitor. CALL passes variables by reference (copied in, and back when the called program
// returns), literals and expressions by value. Async commands (DLYJOB, recalls, STRCRAFT WAIT(*YES)) put the job in a
// wait, which costs no budget; the command runs again once the wait is done. The files a program declares (DCLF) keep
// their place in its frame - where its reads have got to, the record last read - so a saved job reads on from there.
public final class Vm {
    public enum State {
        RUNNING, WAITING, ENDED
    }

    // A compiled program by its source and the file formats it was compiled with.
    private record ProgramKey(List<String> source, Map<String, String> files) {}

    private static final Map<ProgramKey, VmProgram> PROGRAMS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ProgramKey, VmProgram> eldest) {
            return size() > 64;
        }
    };

    // A declared file's state in a frame: where its reads have got to (null: the start), whether they're at the end
    // (end of file, or POSDBF *END), and the record last read (UPDRCD and DLTRCD change it; -1 for none).
    private static final class OpenFile {
        DbRecord.@Nullable Position position;
        boolean end;
        long current = -1;
    }

    private static final class Frame {
        final String key;
        final VmProgram program;
        int pc, resume;
        final Map<String, Object> vars = new HashMap<>();
        // CALLSUBR: where each goes back to, and its RTNVAL variable ("" for none).
        final Deque<Integer> returns = new ArrayDeque<>();
        final Deque<String> returnVars = new ArrayDeque<>();
        final Map<Integer, Integer> counters = new HashMap<>();
        // The caller's variable each parameter came from (null: a value).
        final List<@Nullable String> bindings = new ArrayList<>();
        @Nullable ElclMessage lastEscape, lastMessage;
        // Its declared files' states, by open ID (one is made the first time a file is used; CLOF forgets it).
        final Map<String, OpenFile> files = new HashMap<>();

        Frame(String key, VmProgram program) {
            this.key = key;
            this.program = program;
        }
    }

    private final VmHost host;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Map<String, List<String>> sources = new HashMap<>();
    // Each program's file formats as it was compiled with them (VmHost.Loaded.files).
    private final Map<String, Map<String, String>> fileFormats = new HashMap<>();
    private @Nullable Wait wait, resumed;
    private @Nullable ElclMessage failure;
    private long executed;
    private boolean ending;

    public Vm(VmHost host) {
        this.host = host;
    }

    // --- Starting, running, ending ---

    // A job's program, with its parameters (values). ELC0012 when their number doesn't match its PGM PARM.
    public static Vm start(VmHost host, VmHost.Loaded program, List<Object> args) throws ElclException {
        Vm vm = new Vm(host);
        Frame frame = vm.frame(program);
        if (frame.program.params().size() != args.size()) {
            throw new ElclException("ELC0012", program.key(), frame.program.params().size(), args.size());
        }
        for (int i = 0; i < args.size(); i++) {
            vm.assign(frame, frame.program.params().get(i), args.get(i));
            frame.bindings.add(null);
        }
        vm.frames.push(frame);
        return vm;
    }

    // The program a source compiles to, with the file formats it was compiled against (ELC0203 if it no longer does).
    static VmProgram compile(String key, List<String> source, Map<String, String> files) throws ElclException {
        ProgramKey cached = new ProgramKey(List.copyOf(source), Map.copyOf(files));
        synchronized (PROGRAMS) {
            VmProgram program = PROGRAMS.get(cached);
            if (program != null) {
                return program;
            }
        }
        Map<String, RecordFormat> formats = new HashMap<>();
        files.forEach((file, saved) -> {
            RecordFormat format = RecordFormat.load(saved);
            if (format != null) {
                formats.put(file, format);
            }
        });
        var result = Compiler.compileTexts(source, FileResolver.of(formats));
        if (result.program() == null) {
            int slash = key.indexOf('/');
            throw new ElclException("ELC0203", key.substring(slash + 1), slash > 0 ? key.substring(0, slash) : "*LIBL");
        }
        VmProgram program = Lowerer.lower(result.program());
        synchronized (PROGRAMS) {
            PROGRAMS.put(cached, program);
        }
        return program;
    }

    private Frame frame(VmHost.Loaded loaded) throws ElclException {
        VmProgram program = compile(loaded.key(), loaded.source(), loaded.files());
        sources.put(loaded.key(), List.copyOf(loaded.source()));
        fileFormats.put(loaded.key(), Map.copyOf(loaded.files()));
        Frame frame = new Frame(loaded.key(), program);
        for (VarDecl decl : program.variables().values()) {
            frame.vars.put(decl.name(), Values.initial(decl));
        }
        for (VarDecl decl : program.variables().values()) {
            if (decl.value() != null) {
                assign(frame, decl.name(), Values.eval(decl.value(), scope(frame)));
            }
        }
        return frame;
    }

    public State state() {
        return frames.isEmpty() ? State.ENDED : wait != null ? State.WAITING : State.RUNNING;
    }

    // Runs up to budget instructions (fewer when it ends or starts waiting); returns how many it ran. A wait that's done
    // lets the command run again first.
    public int run(int budget) {
        int used = 0;
        while (used < budget && !frames.isEmpty()) {
            if (ending) {
                frames.clear();
                break;
            }
            if (wait != null) {
                if (!waitDone(wait)) {
                    break;
                }
                resumed = wait;
                wait = null;
            }
            Frame frame = frames.peek();
            used++;
            executed++;
            try {
                step(frame);
            } catch (ElclException e) {
                escape(e.elclMessage());
            }
            if (wait == null) {
                resumed = null;
            } else {
                break;
            }
        }
        return used;
    }

    private boolean waitDone(Wait wait) {
        if (wait.kind().equals("DELAY")) {
            return host.gameTime() >= wait.number("until");
        }
        return host.waitDone(wait);
    }

    // ENDJOB *IMMED: stops before the next instruction. *CNTRLD lets the command running finish first (an async one
    // still waiting is ended with it).
    public void end() {
        ending = true;
        wait = null;
    }

    public @Nullable Wait waiting() {
        return wait;
    }

    // The escape that ended the job's program abnormally, or null.
    public @Nullable ElclMessage failure() {
        return failure;
    }

    public long executed() {
        return executed;
    }

    // The call stack, newest first: "LIB/NAME  line 12" (and its subroutines).
    public List<String> callStack() {
        List<String> stack = new ArrayList<>();
        for (Frame frame : frames) {
            int line = frame.pc < frame.program.code().size() ? frame.program.code().get(frame.pc).line + 1 : 0;
            stack.add(String.format(Locale.ROOT, "%-21s line %d%s", frame.key, line, frame.returns.isEmpty() ? "" : "  (" + frame.returns.size() + " subr)"));
        }
        return stack;
    }

    // --- One instruction ---

    private void step(Frame f) throws ElclException {
        List<Insn> code = f.program.code();
        if (f.pc < 0 || f.pc >= code.size()) {
            returnFrame();
            return;
        }
        Insn in = code.get(f.pc);
        switch (in.op) {
            case JUMP -> f.pc = in.target;
            case JUMP_IF_NOT -> f.pc = in.expr == null || Values.truth(eval(f, in.expr)) ? f.pc + 1 : in.target;
            case FOR_INIT -> {
                assign(f, in.var, eval(f, in.expr));
                f.pc++;
            }
            case FOR_TEST -> {
                BigDecimal value = Values.number(f.vars.get(in.var)), to = Values.number(eval(f, in.expr));
                BigDecimal by = in.expr2 != null ? Values.number(eval(f, in.expr2)) : BigDecimal.ONE;
                // BY(0) would never get there.
                if (by.signum() == 0) {
                    throw new ElclException("ELC0004", by.toPlainString());
                }
                boolean past = by.signum() >= 0 ? value.compareTo(to) > 0 : value.compareTo(to) < 0;
                f.pc = past ? in.target : f.pc + 1;
            }
            case FOR_STEP -> {
                Object by = in.expr2 != null ? eval(f, in.expr2) : 1L;
                assign(f, in.var, Values.eval(new Expr.Binary("+", new Expr.Var(in.var, in.line), literal(by, in.line), in.line), scope(f)));
                f.pc = in.target;
            }
            case EACH_INIT -> {
                f.counters.put(in.slot, 0);
                f.pc++;
            }
            case EACH_NEXT -> {
                Object list = f.vars.get(in.list);
                List<?> elements = list instanceof List<?> l ? l : List.of();
                int at = f.counters.getOrDefault(in.slot, 0);
                if (at >= elements.size()) {
                    f.pc = in.target;
                } else {
                    assign(f, in.var, String.valueOf(elements.get(at)));
                    f.counters.put(in.slot, at + 1);
                    f.pc++;
                }
            }
            case CALL_SUBR -> {
                if (f.returns.size() >= host.maxCallDepth()) {
                    throw new ElclException("ELC0011", host.maxCallDepth());
                }
                f.returns.push(f.pc + 1);
                f.returnVars.push(in.var != null ? in.var : "");
                f.pc = in.target;
            }
            case END_SUBR -> {
                if (f.returns.isEmpty()) {
                    // Reached in the program's own flow: its end.
                    returnFrame();
                    return;
                }
                String var = f.returnVars.pop();
                int back = f.returns.pop();
                if (!var.isEmpty()) {
                    assign(f, var, in.expr != null ? eval(f, in.expr) : 0L);
                }
                f.pc = back;
            }
            case RESUME -> f.pc = f.resume;
            case RETURN -> returnFrame();
            case COMMAND -> command(f, in);
        }
    }

    private static Expr literal(Object value, int line) throws ElclException {
        return value instanceof Long || value instanceof BigDecimal ? new Expr.Num(Values.number(value), Values.text(value), line)
                : new Expr.Str(Values.text(value), line);
    }

    // The frame on top ends normally: its by-reference parameters go back to its caller, which carries on after the CALL.
    private void returnFrame() throws ElclException {
        Frame done = frames.pop();
        Frame caller = frames.peek();
        if (caller == null) {
            return;
        }
        for (int i = 0; i < done.bindings.size(); i++) {
            String var = done.bindings.get(i);
            if (var != null) {
                assign(caller, var, done.vars.get(done.program.params().get(i)));
            }
        }
        caller.pc++;
    }

    // An escape message: the innermost monitor covering where it happened takes it (then program-level ones); else the
    // program ends and its caller gets ELC0013 at its CALL.
    private void escape(ElclMessage message) {
        ElclMessage current = message;
        while (!frames.isEmpty()) {
            Frame f = frames.peek();
            Monitor monitor = monitor(f, current);
            if (monitor != null) {
                f.lastEscape = current;
                f.lastMessage = current;
                int at = Math.min(Math.max(f.pc, 0), f.program.code().size() - 1);
                int resume = f.program.code().get(at).resume;
                f.resume = resume >= 0 ? resume : f.pc + 1;
                f.pc = monitor.handler() >= 0 ? monitor.handler() : f.resume;
                return;
            }
            host.escaped(f.key, current);
            frames.pop();
            if (frames.isEmpty()) {
                failure = current;
                host.failed(current);
                return;
            }
            current = ElclMessage.of("ELC0013", f.key);
        }
    }

    private static @Nullable Monitor monitor(Frame f, ElclMessage message) {
        List<Monitor> covering = new ArrayList<>();
        for (Monitor monitor : f.program.monitors()) {
            if (monitor.covers(f.pc)) {
                covering.add(monitor);
            }
        }
        covering.sort(Comparator.comparingInt(m -> m.to() - m.from()));
        covering.addAll(f.program.programMonitors());
        for (Monitor monitor : covering) {
            if (takes(monitor, message)) {
                return monitor;
            }
        }
        return null;
    }

    private static boolean takes(Monitor monitor, ElclMessage message) {
        boolean id = monitor.ids().stream().anyMatch(m -> VmProgram.matches(m, message.id()));
        return id && (monitor.cmpdta() == null || String.join(" ", message.data()).startsWith(monitor.cmpdta().stripTrailing()));
    }

    // --- Variables ---

    private Values.Scope scope(Frame f) {
        return new Values.Scope() {
            @Override
            public Object get(String variable) throws ElclException {
                Object value = f.vars.get(variable);
                if (value == null) {
                    throw new ElclException("ELC0002", variable);
                }
                return value;
            }

            @Override
            public String itemName(String item) {
                return host.itemName(item);
            }
        };
    }

    private Object eval(Frame f, @Nullable Expr expr) throws ElclException {
        return expr == null ? "" : Values.eval(expr, scope(f));
    }

    private void assign(Frame f, @Nullable String var, Object value) throws ElclException {
        VarDecl decl = var != null ? f.program.variables().get(var) : null;
        if (decl == null) {
            throw new ElclException("ELC0002", String.valueOf(var));
        }
        f.vars.put(var, Values.convert(decl, value, host.maxList()));
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Frame f, @Nullable String var) throws ElclException {
        Object value = var != null ? f.vars.get(var) : null;
        if (!(value instanceof List<?>)) {
            throw new ElclException("ELC0003", String.valueOf(var), "*LIST");
        }
        return (List<String>) value;
    }

    private static @Nullable String varName(Stmt s, String keyword) {
        return s.value(keyword) instanceof Expr.Var var ? var.name() : null;
    }

    // --- Commands ---

    private void command(Frame f, Insn in) throws ElclException {
        Stmt s = in.stmt;
        if (s == null) {
            f.pc++;
            return;
        }
        if (host.logsCommands()) {
            host.logCommand(s.toSource());
        }
        switch (s.name()) {
            case "CHGVAR" -> assign(f, varName(s, "VAR"), eval(f, s.value("VALUE")));
            case "ADDLSTE" -> {
                List<String> list = list(f, varName(s, "LIST"));
                String value = Values.text(eval(f, s.value("VALUE"))).stripTrailing();
                if (list.size() >= host.maxList()) {
                    throw new ElclException("ELC0015", host.maxList());
                }
                Expr pos = s.value("POS");
                if (pos == null || pos instanceof Expr.Special) {
                    list.add(value);
                } else {
                    long at = Values.integer(eval(f, pos));
                    if (at < 1 || at > list.size() + 1) {
                        throw new ElclException("ELC0006", at, list.size());
                    }
                    list.add((int) at - 1, value);
                }
            }
            case "RMVLSTE" -> {
                List<String> list = list(f, varName(s, "LIST"));
                long at = Values.integer(eval(f, s.value("POS")));
                if (at < 1 || at > list.size()) {
                    throw new ElclException("ELC0006", at, list.size());
                }
                list.remove((int) at - 1);
            }
            case "CLRLST" -> list(f, varName(s, "LIST")).clear();
            case "CALL" -> {
                call(f, s);
                return;
            }
            case "DLYJOB" -> {
                if (resumed == null) {
                    wait = Wait.of("DELAY", "until", Long.toString(host.gameTime() + delay(f, s)));
                    return;
                }
            }
            case "RCVMSG" -> {
                Expr type = s.value("MSGTYPE");
                ElclMessage message = type != null && type.toString().equals("*LAST") ? f.lastMessage : f.lastEscape;
                setIfGiven(f, s, "RTNMSGID", message != null ? message.id() : "");
                setIfGiven(f, s, "RTNMSG", message != null ? message.text() : "");
                setIfGiven(f, s, "RTNMSGDTA", message != null ? String.join(" ", message.data()) : "");
            }
            case "SNDPGMMSG" -> {
                Expr id = s.value("MSGID"), type = s.value("MSGTYPE");
                boolean escape = type != null && type.toString().equals("*ESCAPE");
                String text = Values.text(eval(f, s.value("MSG"))).stripTrailing();
                ElclMessage message = ElclMessage.user(id != null ? id.toString().toUpperCase(Locale.ROOT) : "USR0001",
                        escape ? ElclMessages.SEVERE : ElclMessages.INFO, text);
                if (escape) {
                    throw new ElclException(message);
                }
                f.lastMessage = message;
                host.message(message);
            }
            case "RCVF", "POSDBF", "CLOF", "CHNRCD", "WRTRCD", "UPDRCD", "DLTRCD" -> fileOperation(f, s);
            default -> registered(f, s);
        }
        if (wait == null) {
            f.pc++;
        }
    }

    // --- Files (DCLF) ---

    // A file operation on a declared file (by OPNID): RCVF reads the next record into its variables (ELC2201 at the
    // end, and again until POSDBF or CLOF), POSDBF goes back to the start or on to the end, CLOF forgets where it was,
    // CHNRCD reads by key (ELC2202), WRTRCD writes a new record from the variables, UPDRCD and DLTRCD change or delete
    // the last record read (ELC2204 without one). The file is opened again each time, checked against the format the
    // program was compiled with: a field it declares gone or of another type is ELC2207.
    private void fileOperation(Frame f, Stmt s) throws ElclException {
        String opnid = Compiler.opnid(s);
        DeclaredFile declared = f.program.files().get(opnid);
        if (declared == null || declared.format() == null) {
            throw new ElclException("ELC2206", opnid.isEmpty() ? "*NONE" : opnid);
        }
        if (s.is("CLOF")) {
            f.files.remove(opnid);
            return;
        }
        OpenFile file = f.files.computeIfAbsent(opnid, k -> new OpenFile());
        if (s.is("POSDBF")) {
            Expr position = s.value("POSITION");
            file.position = null;
            file.end = position != null && position.toString().equals("*END");
            file.current = -1;
            return;
        }
        FileAccess access = host.files();
        if (access == null) {
            throw new ElclException("ELC0107", s.name());
        }
        FileAccess.Opened opened = access.open(declared.library(), declared.file());
        RecordFormat format = opened.format();
        for (FieldDef field : declared.format().fields()) {
            FieldDef now = format.field(field.name());
            if (now == null || !now.sameType(field)) {
                throw new ElclException("ELC2207", opened.qualified(), field.name());
            }
        }
        switch (s.name()) {
            case "RCVF" -> {
                DbRecord record = file.end ? null : access.next(opened, file.position);
                if (record == null) {
                    file.end = true;
                    file.current = -1;
                    throw new ElclException("ELC2201", opened.qualified());
                }
                file.position = record.position(format);
                file.current = record.rrn();
                receive(f, declared, format, record);
            }
            case "CHNRCD" -> {
                Stmt.Param keys = s.param("KEY");
                List<Expr> given = keys != null ? keys.values() : List.of();
                int[] indexes = format.keyIndexes();
                if (given.isEmpty() || given.size() > indexes.length) {
                    throw new ElclException("ELC0103", given.isEmpty() ? "*NONE" : given.getLast().toString(), "KEY");
                }
                Object[] key = new Object[given.size()];
                List<String> shown = new ArrayList<>();
                for (int i = 0; i < key.length; i++) {
                    key[i] = format.fields().get(indexes[i]).convert(eval(f, given.get(i)));
                    shown.add(format.fields().get(indexes[i]).text(key[i]));
                }
                DbRecord record = access.chain(opened, key);
                if (record == null) {
                    throw new ElclException("ELC2202", String.join(" ", shown), opened.qualified());
                }
                file.position = record.position(format);
                file.current = record.rrn();
                file.end = false;
                receive(f, declared, format, record);
            }
            case "WRTRCD" -> receive(f, declared, format, access.write(opened, values(f, declared, format)));
            case "UPDRCD" -> {
                if (file.current < 0) {
                    throw new ElclException("ELC2204", opened.qualified());
                }
                receive(f, declared, format, access.update(opened, file.current, values(f, declared, format)));
            }
            default -> {
                if (file.current < 0) {
                    throw new ElclException("ELC2204", opened.qualified());
                }
                access.delete(opened, file.current);
                file.current = -1;
            }
        }
    }

    // A record into the file's variables (each field the program declares).
    private void receive(Frame f, DeclaredFile declared, RecordFormat format, DbRecord record) throws ElclException {
        for (FieldDef field : declared.format().fields()) {
            int index = format.index(field.name());
            if (index >= 0) {
                assign(f, declared.variable(field), record.value(index));
            }
        }
    }

    // The variables as a record of the file as it is now: fields the program doesn't declare blank (ELC2209 for a
    // value that doesn't fit its field).
    private static Object[] values(Frame f, DeclaredFile declared, RecordFormat format) throws ElclException {
        Object[] values = format.blank();
        for (int i = 0; i < values.length; i++) {
            FieldDef field = format.fields().get(i);
            Object value = declared.format().field(field.name()) != null ? f.vars.get(declared.prefix() + field.name()) : null;
            if (value != null) {
                values[i] = field.convert(value);
            }
        }
        return values;
    }

    private void setIfGiven(Frame f, Stmt s, String keyword, Object value) throws ElclException {
        String var = varName(s, keyword);
        if (var != null) {
            assign(f, var, value);
        }
    }

    // DLYJOB: DLY seconds, or RSMTIME (HHMMSS on the game clock, the next time it comes round) - in game ticks.
    private long delay(Frame f, Stmt s) throws ElclException {
        Expr dly = s.value("DLY"), at = s.value("RSMTIME");
        if (dly != null) {
            return Values.integer(eval(f, dly)) * 20;
        }
        if (at == null) {
            return 0;
        }
        String text = Values.text(eval(f, at)).trim();
        if (!text.matches("\\d{4}|\\d{6}")) {
            throw new ElclException("ELC0103", text, "RSMTIME");
        }
        int seconds = Integer.parseInt(text.substring(0, 2)) * 3_600 + Integer.parseInt(text.substring(2, 4)) * 60
                + (text.length() == 6 ? Integer.parseInt(text.substring(4, 6)) : 0);
        // The game day starts at 06:00 (tick 0).
        long tickOfDay = (long) ((seconds - 6 * 3_600 + 86_400) % 86_400) * 24_000 / 86_400;
        long now = Math.floorMod(host.dayTime(), 24_000L);
        long ticks = Math.floorMod(tickOfDay - now, 24_000L);
        return ticks == 0 ? 24_000 : ticks;
    }

    private void call(Frame f, Stmt s) throws ElclException {
        String target = Values.text(eval(f, s.value("PGM"))).strip().toUpperCase(Locale.ROOT);
        int slash = target.lastIndexOf('/');
        String library = slash >= 0 ? target.substring(0, slash) : "*LIBL", name = slash >= 0 ? target.substring(slash + 1) : target;
        if (frames.size() >= host.maxCallDepth()) {
            throw new ElclException("ELC0011", host.maxCallDepth());
        }
        VmHost.Loaded loaded = host.program(library, name);
        Frame callee = frame(loaded);
        Stmt.Param parm = s.param("PARM");
        List<Expr> args = parm != null ? parm.values() : List.of();
        if (args.size() != callee.program.params().size()) {
            throw new ElclException("ELC0012", loaded.key(), callee.program.params().size(), args.size());
        }
        for (int i = 0; i < args.size(); i++) {
            Expr arg = args.get(i);
            assign(callee, callee.program.params().get(i), copy(eval(f, arg)));
            callee.bindings.add(arg instanceof Expr.Var var ? var.name() : null);
        }
        frames.push(callee);
    }

    private static Object copy(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : value;
    }

    // A registered command (COMMANDS.md 2-9, a device's own): allowed in this job, authorised, then run.
    private void registered(Frame f, Stmt s) throws ElclException {
        CommandDefinition command = s.definition();
        if (command == null) {
            throw new ElclException("ELC0101", s.name());
        }
        if (host.interactive() ? command.context() == CommandDefinition.Context.BATCH : command.context() == CommandDefinition.Context.INTERACTIVE) {
            throw new ElclException(host.interactive() ? "ELC0106" : "ELC0105", command.name());
        }
        ElclMessage denied = host.authorise(command.auth());
        if (denied != null) {
            throw new ElclException(denied);
        }
        if (command.executor() == null) {
            throw new ElclException("ELC0107", command.name());
        }
        command.executor().run(new Call(f, s, command));
    }

    // A command's run inside the VM: its values from the frame's variables, its RTN* values into them.
    private final class Call implements Invocation {
        private final Frame frame;
        private final Stmt statement;
        private final CommandDefinition command;

        Call(Frame frame, Stmt statement, CommandDefinition command) {
            this.frame = frame;
            this.statement = statement;
            this.command = command;
        }

        @Override
        public CommandDefinition command() {
            return command;
        }

        @Override
        public boolean given(String keyword) {
            return statement.param(keyword) != null;
        }

        @Override
        public String text(String keyword) throws ElclException {
            List<String> values = list(keyword);
            return values.isEmpty() ? "" : values.getFirst();
        }

        @Override
        public long integer(String keyword) throws ElclException {
            Stmt.Param param = statement.param(keyword);
            long value;
            if (param == null || param.values().isEmpty()) {
                value = Values.integer(text(keyword));
            } else {
                Object raw = Values.eval(param.values().getFirst(), scope(frame));
                value = Values.integer(raw);
            }
            ParamDef def = command.param(keyword);
            if (def != null && def.hasRange() && (value < def.min() || value > def.max())) {
                throw new ElclException("ELC0004", value);
            }
            return value;
        }

        // A value that fails (%SST out of range, a division by zero...) is that escape message.
        @Override
        public List<String> list(String keyword) throws ElclException {
            Stmt.Param param = statement.param(keyword);
            if (param == null) {
                ParamDef def = command.param(keyword);
                return def != null && def.defaultValue() != null ? List.of(def.defaultValue()) : List.of();
            }
            List<String> values = new ArrayList<>();
            for (Expr value : param.values()) {
                Object v = Values.eval(value, scope(frame));
                if (v instanceof List<?> l) {
                    l.forEach(element -> values.add(String.valueOf(element)));
                } else {
                    values.add(value instanceof Expr.Var ? Values.text(v).stripTrailing() : Values.text(v));
                }
            }
            return values;
        }

        @Override
        public void returns(String keyword, Object value) {
            String var = varName(statement, keyword);
            if (var == null) {
                return;
            }
            try {
                assign(frame, var, value);
            } catch (ElclException e) {
                // A value its variable can't take: left as it was.
            }
        }

        @Override
        public void send(ElclMessage message) {
            frame.lastMessage = message;
            host.message(message);
        }

        @Override
        public boolean interactive() {
            return host.interactive();
        }

        @Override
        public <T> @Nullable T context(Class<T> type) {
            return host.context(type);
        }

        @Override
        public void await(Wait wait) {
            Vm.this.wait = wait;
        }

        @Override
        public boolean canWait() {
            return true;
        }

        @Override
        public @Nullable Wait resumed() {
            return resumed;
        }
    }

    // --- Saving ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag programs = new CompoundTag();
        sources.forEach((key, source) -> {
            ListTag lines = new ListTag();
            source.forEach(line -> lines.add(StringTag.valueOf(line)));
            programs.put(key, lines);
        });
        tag.put("programs", programs);
        CompoundTag formats = new CompoundTag();
        fileFormats.forEach((key, files) -> {
            if (!files.isEmpty()) {
                CompoundTag program = new CompoundTag();
                files.forEach(program::putString);
                formats.put(key, program);
            }
        });
        tag.put("file_formats", formats);
        ListTag list = new ListTag();
        // Oldest (the job's own program) first.
        Iterator<Frame> oldestFirst = frames.descendingIterator();
        while (oldestFirst.hasNext()) {
            list.add(saveFrame(oldestFirst.next()));
        }
        tag.put("frames", list);
        if (wait != null) {
            tag.put("wait", saveWait(wait));
        }
        if (failure != null) {
            tag.put("failure", saveMessage(failure));
        }
        tag.putLong("executed", executed);
        tag.putBoolean("ending", ending);
        return tag;
    }

    private CompoundTag saveFrame(Frame f) {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", f.key);
        tag.putInt("pc", f.pc);
        tag.putInt("resume", f.resume);
        CompoundTag vars = new CompoundTag();
        for (VarDecl decl : f.program.variables().values()) {
            Object value = f.vars.get(decl.name());
            if (value == null) {
                continue;
            }
            switch (decl.type()) {
                case INT -> vars.putLong(decl.name(), (Long) value);
                case LGL -> vars.putBoolean(decl.name(), (Boolean) value);
                case LIST -> {
                    ListTag elements = new ListTag();
                    ((List<?>) value).forEach(element -> elements.add(StringTag.valueOf(String.valueOf(element))));
                    vars.put(decl.name(), elements);
                }
                default -> vars.putString(decl.name(), Values.text(value));
            }
        }
        tag.put("vars", vars);
        tag.putIntArray("returns", f.returns.stream().mapToInt(Integer::intValue).toArray());
        ListTag returnVars = new ListTag();
        f.returnVars.forEach(var -> returnVars.add(StringTag.valueOf(var)));
        tag.put("return_vars", returnVars);
        CompoundTag counters = new CompoundTag();
        f.counters.forEach((slot, at) -> counters.putInt(Integer.toString(slot), at));
        tag.put("counters", counters);
        ListTag bindings = new ListTag();
        f.bindings.forEach(var -> bindings.add(StringTag.valueOf(var != null ? var : "")));
        tag.put("bindings", bindings);
        if (f.lastEscape != null) {
            tag.put("last_escape", saveMessage(f.lastEscape));
        }
        if (f.lastMessage != null) {
            tag.put("last_message", saveMessage(f.lastMessage));
        }
        ListTag files = new ListTag();
        f.files.forEach((opnid, file) -> {
            CompoundTag state = new CompoundTag();
            state.putString("opnid", opnid);
            state.putBoolean("end", file.end);
            state.putLong("current", file.current);
            if (file.position != null) {
                ListTag key = new ListTag();
                file.position.key().forEach(value -> key.add(StringTag.valueOf(value)));
                state.put("key", key);
                state.putLong("rrn", file.position.rrn());
            }
            files.add(state);
        });
        tag.put("files", files);
        return tag;
    }

    private static CompoundTag saveWait(Wait wait) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", wait.kind());
        CompoundTag data = new CompoundTag();
        wait.data().forEach(data::putString);
        tag.put("data", data);
        return tag;
    }

    private static Wait loadWait(CompoundTag tag) {
        CompoundTag data = tag.getCompoundOrEmpty("data");
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : data.keySet()) {
            map.put(key, data.getStringOr(key, ""));
        }
        return new Wait(tag.getStringOr("kind", ""), Map.copyOf(map));
    }

    private static CompoundTag saveMessage(ElclMessage message) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", message.id());
        tag.putInt("severity", message.severity());
        ListTag data = new ListTag();
        message.data().forEach(text -> data.add(StringTag.valueOf(text)));
        tag.put("data", data);
        return tag;
    }

    private static ElclMessage loadMessage(CompoundTag tag) {
        ListTag data = tag.getListOrEmpty("data");
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            texts.add(data.getStringOr(i, ""));
        }
        return new ElclMessage(tag.getStringOr("id", "ELC0001"), tag.getIntOr("severity", ElclMessages.SEVERE), List.copyOf(texts));
    }

    // A saved VM back, ready to carry on (ELC0203 if a program it was running no longer compiles).
    public static Vm load(VmHost host, CompoundTag tag) throws ElclException {
        Vm vm = new Vm(host);
        CompoundTag programs = tag.getCompoundOrEmpty("programs");
        for (String key : programs.keySet()) {
            ListTag lines = programs.getListOrEmpty(key);
            List<String> source = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                source.add(lines.getStringOr(i, ""));
            }
            vm.sources.put(key, List.copyOf(source));
        }
        CompoundTag formats = tag.getCompoundOrEmpty("file_formats");
        for (String key : formats.keySet()) {
            CompoundTag program = formats.getCompoundOrEmpty(key);
            Map<String, String> files = new HashMap<>();
            for (String file : program.keySet()) {
                files.put(file, program.getStringOr(file, ""));
            }
            vm.fileFormats.put(key, Map.copyOf(files));
        }
        ListTag list = tag.getListOrEmpty("frames");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompoundOrEmpty(i);
            String key = saved.getStringOr("key", "");
            List<String> source = vm.sources.getOrDefault(key, List.of());
            Frame f = new Frame(key, compile(key, source, vm.fileFormats.getOrDefault(key, Map.of())));
            f.pc = saved.getIntOr("pc", 0);
            f.resume = saved.getIntOr("resume", 0);
            CompoundTag vars = saved.getCompoundOrEmpty("vars");
            for (VarDecl decl : f.program.variables().values()) {
                Object value = switch (decl.type()) {
                    case INT -> vars.getLongOr(decl.name(), 0L);
                    case LGL -> vars.getBooleanOr(decl.name(), false);
                    case DEC -> Values.decimal(vars.getStringOr(decl.name(), "0"), decl.length(), decl.decimals());
                    case LIST -> {
                        ListTag elements = vars.getListOrEmpty(decl.name());
                        List<String> values = new ArrayList<>();
                        for (int j = 0; j < elements.size(); j++) {
                            values.add(elements.getStringOr(j, ""));
                        }
                        yield values;
                    }
                    case CHAR -> Values.fit(vars.getStringOr(decl.name(), ""), decl.length());
                };
                f.vars.put(decl.name(), value);
            }
            for (int at : saved.getIntArray("returns").orElse(new int[0])) {
                f.returns.addLast(at);
            }
            ListTag returnVars = saved.getListOrEmpty("return_vars");
            for (int j = 0; j < returnVars.size(); j++) {
                f.returnVars.addLast(returnVars.getStringOr(j, ""));
            }
            CompoundTag counters = saved.getCompoundOrEmpty("counters");
            for (String slot : counters.keySet()) {
                f.counters.put(Integer.parseInt(slot), counters.getIntOr(slot, 0));
            }
            ListTag bindings = saved.getListOrEmpty("bindings");
            for (int j = 0; j < bindings.size(); j++) {
                String var = bindings.getStringOr(j, "");
                f.bindings.add(var.isEmpty() ? null : var);
            }
            saved.getCompound("last_escape").ifPresent(m -> f.lastEscape = loadMessage(m));
            saved.getCompound("last_message").ifPresent(m -> f.lastMessage = loadMessage(m));
            ListTag files = saved.getListOrEmpty("files");
            for (int j = 0; j < files.size(); j++) {
                CompoundTag state = files.getCompoundOrEmpty(j);
                OpenFile file = new OpenFile();
                file.end = state.getBooleanOr("end", false);
                file.current = state.getLongOr("current", -1);
                if (state.contains("key")) {
                    ListTag keyValues = state.getListOrEmpty("key");
                    List<String> values = new ArrayList<>();
                    for (int k = 0; k < keyValues.size(); k++) {
                        values.add(keyValues.getStringOr(k, ""));
                    }
                    file.position = new DbRecord.Position(values, state.getLongOr("rrn", 0));
                }
                f.files.put(state.getStringOr("opnid", ""), file);
            }
            vm.frames.push(f);
        }
        tag.getCompound("wait").ifPresent(w -> vm.wait = loadWait(w));
        tag.getCompound("failure").ifPresent(m -> vm.failure = loadMessage(m));
        vm.executed = tag.getLongOr("executed", 0);
        vm.ending = tag.getBooleanOr("ending", false);
        return vm;
    }
}
