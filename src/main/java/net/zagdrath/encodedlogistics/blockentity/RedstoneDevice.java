/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;

// A block whose faces RTVRSIN reads and CHGRSOUT drives (RedstoneCommands): a Control Interface, or a PLC (DEV(*SELF) in
// its own program, or by name on its network).
public interface RedstoneDevice {
    boolean isOnline();

    int input(Direction face);

    // The highest level arriving on any face (SIDE(*MAX)).
    int maxInput();

    // A face's output (null: every face); line: the program line that set it (0 when it wasn't a program's).
    void setOutput(@Nullable Direction face, int level, int line);
}
