/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.vm;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.compile.VarDecl;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;

// A compiled program as the VM runs it (Lowerer): a flat list of instructions - commands, jumps, the loops' steps,
// subroutine calls and returns - and its monitors. A monitor covers a range of instructions (a command and anything
// nested in it) and, when one of its message IDs escapes from there, sends execution to its handler (the code its
// EXEC lowered to, ending in RESUME) or, without one, on to the next statement. Program-level monitors cover every
// instruction. Every instruction knows where "the next statement" is (resume), for a monitor to carry on there.
public record VmProgram(List<String> params, Map<String, VarDecl> variables, List<Insn> code, List<Monitor> monitors, List<Monitor> programMonitors) {
    public enum Op {
        COMMAND,    // run stmt (CHGVAR, CALL, ... or a registered command)
        JUMP,       // to target
        JUMP_IF_NOT,// to target unless expr is true
        FOR_INIT,   // var = expr (DOFOR FROM)
        FOR_TEST,   // to target once var is past expr (TO), going by expr2 (BY)
        FOR_STEP,   // var += expr2 (BY), then to target (the test)
        EACH_INIT,  // the loop's counter (slot) to 0
        EACH_NEXT,  // var = the counter's element of list (and the counter on), or to target past the end
        CALL_SUBR,  // to the subroutine at target, coming back after; var: CALLSUBR RTNVAL
        END_SUBR,   // back to after the CALLSUBR (expr: ENDSUBR RTNVAL)
        RESUME,     // a monitor's handler done: on with the statement after the one that failed
        RETURN,     // the program ends (RETURN, or reaching its end)
    }

    public static final class Insn {
        public final Op op;
        public final int line;
        public @Nullable Stmt stmt;
        public @Nullable Expr expr, expr2;
        public @Nullable String var, list;
        public int target = -1, slot = -1, resume = -1;

        Insn(Op op, int line) {
            this.op = op;
            this.line = line;
        }

        @Override
        public String toString() {
            return op + (stmt != null ? " " + stmt.toSource() : "") + (target >= 0 ? " ->" + target : "") + (resume >= 0 ? " r" + resume : "");
        }
    }

    // ids: the message IDs (ELC1200 covers ELC12xx, ELC0000 all); cmpdta: the message data must start with it;
    // handler: where its EXEC starts (-1: none, carry on).
    public record Monitor(int from, int to, List<String> ids, @Nullable String cmpdta, int handler) {
        public boolean covers(int pc) {
            return pc >= from && pc < to;
        }
    }

    // Whether a monitored ID takes a message: ELCnn00 a range, ELC0000 (or any ...0000) everything of that prefix.
    public static boolean matches(String monitored, String id) {
        String m = monitored.toUpperCase(java.util.Locale.ROOT), i = id.toUpperCase(java.util.Locale.ROOT);
        if (m.length() != 7 || i.length() != 7) {
            return m.equals(i);
        }
        if (m.equals("ELC0000")) {
            return true;
        }
        if (m.endsWith("0000")) {
            return m.regionMatches(0, i, 0, 3);
        }
        if (m.endsWith("00")) {
            return m.regionMatches(0, i, 0, 5);
        }
        return m.equals(i);
    }
}
