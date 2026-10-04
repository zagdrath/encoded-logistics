/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.vm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.compile.CompiledProgram;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;
import net.zagdrath.encodedlogistics.elcl.vm.VmProgram.Insn;
import net.zagdrath.encodedlogistics.elcl.vm.VmProgram.Monitor;
import net.zagdrath.encodedlogistics.elcl.vm.VmProgram.Op;

// A compiled program (checked: its blocks match, its labels exist) to the VM's flat code (ELCL_SPEC.md 6-8):
//  IF COND THEN(x) ELSE CMD(y)    JUMP_IF_NOT cond -> else; x; JUMP end; else: y; end:
//  DOWHILE COND(c) ... ENDDO      top: JUMP_IF_NOT c -> end; body; JUMP top; end:          (ITERATE: top)
//  DOUNTIL COND(c) ... ENDDO      top: body; next: JUMP_IF_NOT c -> top; end:              (ITERATE: next)
//  DOFOR VAR FROM TO BY ... ENDDO FOR_INIT; test: FOR_TEST -> end; body; next: FOR_STEP -> test; end:
//  FOREACH VAR IN ... ENDFOR      EACH_INIT; top: EACH_NEXT -> end; body; JUMP top; end:
//  SELECT / WHEN / OTHERWISE      each WHEN: JUMP_IF_NOT -> next WHEN; its THEN; JUMP end
//  a command, MONMSGs after it    the command; JUMP after; each EXEC's code then RESUME; after:   (a monitor over the
//                                 command's instructions)
// Program-level MONMSGs' EXECs come first, jumped over; the main body ends in RETURN; subroutines follow it, each
// ending in END_SUBR. A THEN / CMD / EXEC holding DO (or a loop) takes the statements up to its ENDDO as its body.
final class Lowerer {
    private static final Set<String> LOOPS = Set.of("DOWHILE", "DOUNTIL", "DOFOR", "FOREACH");

    private record Loop(@Nullable String label, List<Insn> breaks, List<Insn> continues) {}

    private final List<Stmt> statements;
    private int index;
    private final List<Insn> code = new ArrayList<>();
    private final List<Monitor> monitors = new ArrayList<>(), programMonitors = new ArrayList<>();
    private final Map<String, Integer> labels = new HashMap<>(), subroutines = new HashMap<>();
    private final List<Insn> gotos = new ArrayList<>(), calls = new ArrayList<>();
    private final Deque<Loop> loops = new ArrayDeque<>();
    private int slots;

    private Lowerer(List<Stmt> statements) {
        this.statements = statements;
    }

    static VmProgram lower(CompiledProgram compiled) {
        Lowerer lowerer = new Lowerer(compiled.statements());
        lowerer.program();
        return new VmProgram(compiled.params(), compiled.variables(), List.copyOf(lowerer.code), List.copyOf(lowerer.monitors),
                List.copyOf(lowerer.programMonitors));
    }

    private @Nullable Stmt peek() {
        return index < statements.size() ? statements.get(index) : null;
    }

    private boolean at(String name) {
        Stmt s = peek();
        return s != null && s.is(name);
    }

    private Insn emit(Op op, int line) {
        Insn insn = new Insn(op, line);
        code.add(insn);
        return insn;
    }

    private void program() {
        if (at("PGM")) {
            index++;
        }
        while (at("DCL")) {
            index++;
        }
        Insn skip = null;
        while (at("MONMSG")) {
            Stmt monitor = statements.get(index++);
            if (skip == null) {
                skip = emit(Op.JUMP, monitor.firstLine());
            }
            int start = code.size();
            int handler = handler(monitor);
            fillResume(start, -1);
            programMonitors.add(monitor(monitor, 0, Integer.MAX_VALUE, handler));
        }
        if (skip != null) {
            skip.target = code.size();
        }
        statementsUntil(Set.of());
        int main = code.size();
        emit(Op.RETURN, peek() != null ? peek().firstLine() : 0);
        // Only subroutines follow the main body (the compiler reports anything else).
        while (at("SUBR")) {
            Stmt subr = statements.get(index++);
            Expr name = subr.value("SUBR");
            label(subr, code.size());
            subroutines.put(name != null ? name.toString() : "", code.size());
            statementsUntil(Set.of("ENDSUBR"));
            Stmt end = at("ENDSUBR") ? statements.get(index++) : subr;
            if (end != subr) {
                label(end, code.size());
            }
            Insn ret = emit(Op.END_SUBR, end.firstLine());
            ret.expr = end.is("ENDSUBR") ? end.value("RTNVAL") : null;
        }
        // A label on ENDPGM: the main body's end.
        if (at("ENDPGM")) {
            label(statements.get(index), main);
        }
        for (Insn jump : gotos) {
            jump.target = labels.getOrDefault(jump.var, code.size() - 1);
        }
        for (Insn call : calls) {
            call.target = subroutines.getOrDefault(call.list, code.size() - 1);
        }
    }

    // Statements up to one of the closers (not taken), ENDPGM, a SUBR or the end.
    private void statementsUntil(Set<String> closers) {
        Stmt s;
        while ((s = peek()) != null && !closers.contains(s.name()) && !s.is("ENDPGM") && !s.is("SUBR")) {
            statement();
        }
    }

    // A statement's labels are at `at`.
    private void label(Stmt s, int at) {
        for (String name : s.labels()) {
            labels.put(name, at);
        }
    }

    // ENDDO / ENDFOR / ENDSELECT: taken (a label on it marks where it is).
    private void closer() {
        Stmt s = peek();
        if (s != null && (s.is("ENDDO") || s.is("ENDFOR") || s.is("ENDSELECT"))) {
            index++;
            label(s, code.size());
        }
    }

    private void statement() {
        Stmt s = statements.get(index++);
        int start = code.size();
        label(s, start);
        switch (s.name()) {
            case "IF" -> ifStatement(s);
            case "DO" -> {
                statementsUntil(Set.of("ENDDO"));
                closer();
            }
            case "DOWHILE", "DOUNTIL", "DOFOR", "FOREACH" -> loop(s, s.label());
            case "SELECT" -> select(s);
            // Out of place (the compiler reports them): nothing to run.
            case "ELSE", "WHEN", "OTHERWISE", "ENDDO", "ENDFOR", "ENDSELECT", "ENDSUBR", "MONMSG", "DCL", "PGM" -> {}
            default -> simple(s);
        }
        int end = code.size();
        if (at("MONMSG")) {
            Insn skip = emit(Op.JUMP, s.firstLine());
            while (at("MONMSG")) {
                Stmt monitor = statements.get(index++);
                monitors.add(monitor(monitor, start, end, handler(monitor)));
            }
            skip.target = code.size();
        }
        fillResume(start, code.size());
    }

    // Instructions from start that don't know their next statement yet: it's at `after` (-1: none, program level).
    private void fillResume(int start, int after) {
        for (int pc = start; pc < code.size(); pc++) {
            if (code.get(pc).resume < 0) {
                code.get(pc).resume = after;
            }
        }
    }

    private Monitor monitor(Stmt monitor, int from, int to, int handler) {
        List<String> ids = new ArrayList<>();
        Stmt.Param msgid = monitor.param("MSGID");
        if (msgid != null) {
            msgid.values().forEach(value -> ids.add(value.toString()));
        }
        Expr cmpdta = monitor.value("CMPDTA");
        String compare = cmpdta instanceof Expr.Str str ? str.value() : null;
        return new Monitor(from, to, List.copyOf(ids), compare, handler);
    }

    // A MONMSG's EXEC as code ending in RESUME; -1 when it has none.
    private int handler(Stmt monitor) {
        Stmt exec = monitor.nested("EXEC");
        if (exec == null) {
            return -1;
        }
        int start = code.size();
        nested(exec);
        emit(Op.RESUME, monitor.firstLine());
        return start;
    }

    // The command inside THEN / CMD / EXEC: a block opener takes its body from the statements that follow.
    private void nested(@Nullable Stmt s) {
        if (s == null) {
            return;
        }
        switch (s.name()) {
            case "DO" -> {
                statementsUntil(Set.of("ENDDO"));
                closer();
            }
            case "DOWHILE", "DOUNTIL", "DOFOR", "FOREACH" -> loop(s, null);
            case "IF" -> ifStatement(s);
            default -> simple(s);
        }
    }

    private void ifStatement(Stmt s) {
        Insn test = emit(Op.JUMP_IF_NOT, s.firstLine());
        test.expr = s.value("COND");
        nested(s.nested("THEN"));
        if (at("ELSE")) {
            Insn skip = emit(Op.JUMP, s.firstLine());
            test.target = code.size();
            Stmt otherwise = statements.get(index++);
            label(otherwise, code.size());
            nested(otherwise.nested("CMD"));
            skip.target = code.size();
        } else {
            test.target = code.size();
        }
    }

    private void loop(Stmt s, @Nullable String label) {
        Loop loop = new Loop(label, new ArrayList<>(), new ArrayList<>());
        String closer = s.is("FOREACH") ? "ENDFOR" : "ENDDO";
        int line = s.firstLine(), continueAt;
        Insn exit = null;
        switch (s.name()) {
            case "DOWHILE" -> {
                continueAt = code.size();
                exit = emit(Op.JUMP_IF_NOT, line);
                exit.expr = s.value("COND");
                body(loop, closer);
                emit(Op.JUMP, line).target = continueAt;
            }
            case "DOUNTIL" -> {
                int top = code.size();
                body(loop, closer);
                continueAt = code.size();
                Insn again = emit(Op.JUMP_IF_NOT, line);
                again.expr = s.value("COND");
                again.target = top;
            }
            case "DOFOR" -> {
                Insn init = emit(Op.FOR_INIT, line);
                init.var = variable(s, "VAR");
                init.expr = s.value("FROM");
                int test = code.size();
                exit = emit(Op.FOR_TEST, line);
                exit.var = init.var;
                exit.expr = s.value("TO");
                exit.expr2 = s.value("BY");
                body(loop, closer);
                continueAt = code.size();
                Insn step = emit(Op.FOR_STEP, line);
                step.var = init.var;
                step.expr2 = s.value("BY");
                step.target = test;
            }
            default -> {
                int slot = slots++;
                emit(Op.EACH_INIT, line).slot = slot;
                continueAt = code.size();
                exit = emit(Op.EACH_NEXT, line);
                exit.slot = slot;
                exit.var = variable(s, "VAR");
                exit.list = variable(s, "IN");
                body(loop, closer);
                emit(Op.JUMP, line).target = continueAt;
            }
        }
        int end = code.size();
        if (exit != null) {
            exit.target = end;
        }
        loop.breaks().forEach(jump -> jump.target = end);
        loop.continues().forEach(jump -> jump.target = continueAt);
    }

    private void body(Loop loop, String closer) {
        loops.push(loop);
        statementsUntil(Set.of(closer));
        loops.pop();
        closer();
    }

    private static @Nullable String variable(Stmt s, String keyword) {
        return s.value(keyword) instanceof Expr.Var var ? var.name() : null;
    }

    private void select(Stmt s) {
        List<Insn> ends = new ArrayList<>();
        while (at("WHEN") || at("OTHERWISE")) {
            Stmt branch = statements.get(index++);
            label(branch, code.size());
            if (branch.is("WHEN")) {
                Insn test = emit(Op.JUMP_IF_NOT, branch.firstLine());
                test.expr = branch.value("COND");
                nested(branch.nested("THEN"));
                ends.add(emit(Op.JUMP, branch.firstLine()));
                test.target = code.size();
            } else {
                nested(branch.nested("CMD"));
            }
        }
        closer();
        ends.forEach(jump -> jump.target = code.size());
    }

    // One command, or a jump: GOTO, LEAVE, ITERATE, RETURN, CALLSUBR.
    private void simple(Stmt s) {
        int line = s.firstLine();
        switch (s.name()) {
            case "GOTO" -> {
                Insn jump = emit(Op.JUMP, line);
                Expr label = s.value("CMDLBL");
                jump.var = label != null ? label.toString() : "";
                gotos.add(jump);
            }
            case "LEAVE", "ITERATE" -> {
                Expr label = s.value("CMDLBL");
                String target = label == null || label.toString().equals("*CURRENT") ? null : label.toString();
                Loop loop = loops.peek();
                for (Loop candidate : loops) {
                    if (target == null || target.equals(candidate.label())) {
                        loop = candidate;
                        break;
                    }
                }
                Insn jump = emit(Op.JUMP, line);
                if (loop != null) {
                    (s.is("LEAVE") ? loop.breaks() : loop.continues()).add(jump);
                }
            }
            case "RETURN" -> emit(Op.RETURN, line);
            case "CALLSUBR" -> {
                Insn call = emit(Op.CALL_SUBR, line);
                Expr subr = s.value("SUBR");
                call.list = subr != null ? subr.toString() : "";
                call.var = variable(s, "RTNVAL");
                calls.add(call);
            }
            default -> emit(Op.COMMAND, line).stmt = s;
        }
    }
}
