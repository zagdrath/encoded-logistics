/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import net.minecraft.server.level.ServerPlayer;

// A menu that answers the Terminal OS screens' requests (CrtRequestPayload: TerminalService.COMMAND / QUERY / COMPLETE /
// SCREEN) with CrtResponsePayloads: a Terminal Desk's session, or a PLC's source editor.
public interface CrtHost {
    void handle(ServerPlayer player, int kind, String text);
}
