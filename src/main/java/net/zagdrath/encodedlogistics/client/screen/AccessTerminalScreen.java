/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;

// The Access Terminal: the terminal kit as screens/access_terminal.json lays it out, nothing added.
public class AccessTerminalScreen extends AbstractTerminalScreen<AccessTerminalMenu> {
    public static final String LAYOUT = "access_terminal";

    public AccessTerminalScreen(AccessTerminalMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, TerminalLayout.load(LAYOUT));
    }

    private AccessTerminalScreen(AccessTerminalMenu menu, Inventory inventory, TerminalLayout layout) {
        super(menu, inventory, Component.translatable(layout.titleKey), layout);
    }
}
