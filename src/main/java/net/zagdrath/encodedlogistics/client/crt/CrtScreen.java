/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.client.DeskSounds;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.net.CrtRequestPayload;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;

// The Terminal Desk's green screen (HANDOFF 3), and the Integrated Midrange System console's: an 80 x 24 text terminal in
// the GUI-kit panel every text screen has (CrtDisplay: titled with the desk's or the system's name; the Midrange machines'
// screens share it, so they look the same). While it's
// open it takes text input (typing reaches it).
// What's on the glass, and what keys and clicks do, is the terminal's (CrtTerminal); this draws it and hands it the
// game's keys, characters, clicks and the server's answers.
//
// Every screen has the same frame: id, title and system name, the date and time, its body, a prompt and the command
// line ("===> "), the message line and its function keys. Keys: typing goes to the focused field; Tab / Shift+Tab move
// between fields, Up / Down the cursor a row (keeping its column); Enter submits (the command line first, when there's
// anything on it); PageUp / PageDown roll lists; F3 exits, F4 prompts, F5 refreshes, F9 opens Command Entry, F11 sorts, F12 (or Esc) goes back, Shift+F12
// (F24) shows more keys and the phosphor. Clicking a function key presses it.
public class CrtScreen extends Screen implements MenuAccess<TerminalDeskMenu>, CrtTerminal.Host {
    private final TerminalDeskMenu menu;
    private final CrtTerminal terminal = new CrtTerminal(this);
    private final CrtDisplay display = new CrtDisplay();

    public CrtScreen(TerminalDeskMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public TerminalDeskMenu getMenu() {
        return menu;
    }

    @Override
    protected void init() {
        // Typed characters only arrive while something has text input focus.
        minecraft.onTextInputFocusChange(this, true);
        functionKeys.borrow(minecraft);
        display.loadPalette(minecraft, terminal.phosphor());
        terminal.start();
    }

    public void receive(CrtResponsePayload response) {
        terminal.receive(response);
    }

    public int containerId() {
        return menu.containerId;
    }

    // --- The terminal's host ---

    @Override
    public void send(int kind, String text) {
        String cut = text.length() > CrtRequestPayload.MAX_TEXT ? text.substring(0, CrtRequestPayload.MAX_TEXT) : text;
        ClientPacketDistributor.sendToServer(new CrtRequestPayload(menu.containerId, kind, cut));
    }

    @Override
    public void close() {
        onClose();
    }

    @Override
    public void phosphorChanged() {
        display.loadPalette(minecraft, terminal.phosphor());
    }

    @Override
    public TerminalDeskMenu menu() {
        return menu;
    }

    // The game's day and time: "Day 12  14:32:07".
    @Override
    public String clock() {
        return minecraft == null ? "" : FunctionKeys.clock(minecraft);
    }

    // --- Ticking ---

    @Override
    public void tick() {
        terminal.tick();
        if (!menu.stillValid(minecraft.player)) {
            onClose();
        }
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The cursor: a block in the focused field, blinking.
        CrtField focused = terminal.focused();
        int[] cursor = focused != null && !terminal.connecting() && terminal.ticks() / 10 % 2 == 0 ? new int[] { focused.cursorRow(), focused.cursorColumn() } : null;
        display.draw(graphics, minecraft, terminal.compose(), cursor, null, getTitle());
    }

    // --- Input ---

    @Override
    public boolean keyPressed(KeyEvent event) {
        DeskSounds.keyClack();
        int key = event.key();
        boolean shift = event.hasShiftDown();
        if (event.isEscape()) {
            terminal.escape();
            return true;
        }
        if (terminal.connecting()) {
            return true;
        }
        // Function keys act on release (keyReleased), so the release can't reach the game after an exit: F3's
        // debug overlay toggles on release.
        if (FunctionKeys.of(event) > 0) {
            return true;
        }
        if (key == InputConstants.KEY_NUMPADENTER || event.isConfirmation() && event.hasControlDown()) {
            terminal.fieldExit();
        } else if (event.isConfirmation()) {
            terminal.submit();
        } else if (key == InputConstants.KEY_TAB) {
            terminal.tab(shift);
        } else if (event.isDown() || event.isUp()) {
            terminal.moveVertical(event.isUp());
        } else if (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN) {
            terminal.page(key == InputConstants.KEY_PAGEUP ? -1 : 1);
        } else if (key == InputConstants.KEY_INSERT) {
            terminal.insert = !terminal.insert;
        } else if (event.isPaste()) {
            terminal.paste(minecraft.keyboardHandler.getClipboard());
        } else {
            CrtField focused = terminal.focused();
            if (focused == null) {
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                focused.backspace();
            } else if (key == InputConstants.KEY_DELETE) {
                focused.delete();
            } else if (event.isLeft()) {
                focused.left();
            } else if (event.isRight()) {
                focused.right();
            } else if (key == InputConstants.KEY_HOME) {
                focused.cursor = 0;
            } else if (key == InputConstants.KEY_END) {
                focused.cursor = Math.min(focused.value.length(), focused.capacity - 1);
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int c = event.codepoint();
        if (c >= 32 && c < 127 && !terminal.connecting()) {
            terminal.type((char) c);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int[] cell = display.cell(minecraft, event.x(), event.y());
        if (cell != null && !terminal.connecting()) {
            terminal.click(cell[0], cell[1], doubleClick);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        terminal.page(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final FunctionKeys functionKeys = new FunctionKeys();

    // Releases too: F3's debug overlay toggles on release. A function key's release presses it.
    @Override
    public boolean keyReleased(KeyEvent event) {
        int f = FunctionKeys.of(event);
        if (f > 0 && !terminal.connecting()) {
            terminal.functionKey(f);
        }
        return true;
    }

    @Override
    public void removed() {
        functionKeys.giveBack();
        minecraft.onTextInputFocusChange(this, false);
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }
}
