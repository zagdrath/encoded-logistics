/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import net.minecraft.client.Minecraft;
import net.zagdrath.encodedlogistics.client.crt.CrtScreen;
import net.zagdrath.encodedlogistics.client.crt.PlcEditorScreen;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;

// Hands the server's answers to the Terminal Desk screen (or PLC source editor) they're for.
public final class CrtClient {
    private CrtClient() {}

    public static void receive(CrtResponsePayload payload) {
        if (Minecraft.getInstance().gui.screen() instanceof CrtScreen screen && screen.containerId() == payload.containerId()) {
            screen.receive(payload);
        } else if (Minecraft.getInstance().gui.screen() instanceof PlcEditorScreen editor && editor.containerId() == payload.containerId()) {
            editor.receive(payload);
        }
    }
}
