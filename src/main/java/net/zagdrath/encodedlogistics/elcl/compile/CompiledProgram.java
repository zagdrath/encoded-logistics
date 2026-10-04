/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.compile;

import java.util.List;
import java.util.Map;

import net.zagdrath.encodedlogistics.elcl.parse.Stmt;

// A program the compiler accepted: its parameters (PGM PARM, in order), variables, statements (checked, keywords
// resolved), where its labels and subroutines are (statement indices) and its program-level MONMSGs. What the VM runs
// (elcl.vm, when it lands).
public record CompiledProgram(List<String> params, Map<String, VarDecl> variables, List<Stmt> statements, Map<String, Integer> labels,
        Map<String, Integer> subroutines, List<Stmt> programMonitors) {}
