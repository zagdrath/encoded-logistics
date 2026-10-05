/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.client.DeskSounds;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.menu.PlcMenu;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.net.CrtRequestPayload;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;

// A PLC's program in the Terminal OS source editor (docs/plc HANDOFF 5, PLCSTS option 1): the same EditorPanel as
// EDTMBR, on the PLC's source as member *PLC/<program> - its syntax check (for a PLC: on its network, or on its own,
// where what needs a network is ELC1502), F4 prompter and command line. Saving sends it to the PLC (PlcMenu answers
// the editor's requests), which compiles it; an error shows on its line. Leaving the editor goes back to PLCSTS, the
// PLC's menu still open.
public class PlcEditorScreen extends Screen implements CrtTerminal.Host {
    private final PlcMenu menu;
    private final Screen back;
    private final CrtTerminal terminal = new CrtTerminal(this);
    private final CrtDisplay display = new CrtDisplay();
    private final FunctionKeys functionKeys = new FunctionKeys();
    private final String program;
    private boolean leaving;

    public PlcEditorScreen(PlcMenu menu, Screen back, String program) {
        super(Component.translatable("block.encodedlogistics.plc"));
        this.menu = menu;
        this.back = back;
        this.program = program;
    }

    public int containerId() {
        return menu.containerId;
    }

    public void receive(CrtResponsePayload response) {
        terminal.receive(response);
    }

    @Override
    protected void init() {
        minecraft.onTextInputFocusChange(this, true);
        functionKeys.borrow(minecraft);
        Compiler.Target target = menu.online() ? Compiler.Target.PLC : Compiler.Target.PLC_LOCAL;
        String user = minecraft.player != null ? minecraft.player.getName().getString().toUpperCase(java.util.Locale.ROOT) : "";
        terminal.start(new EditorPanel(terminal, PlcMenu.LIBRARY, program, false, target), menu.opening().system(), user, menu.opening().phosphor());
        display.loadPalette(minecraft, terminal.phosphor());
    }

    // --- The terminal's host ---

    @Override
    public void send(int kind, String text) {
        String cut = text.length() > CrtRequestPayload.MAX_TEXT ? text.substring(0, CrtRequestPayload.MAX_TEXT) : text;
        ClientPacketDistributor.sendToServer(new CrtRequestPayload(menu.containerId, kind, cut));
    }

    // Off the editor: back to the PLC's status screen (its menu stays open).
    @Override
    public void close() {
        if (!leaving) {
            leaving = true;
            minecraft.gui.setScreen(back);
        }
    }

    @Override
    public void phosphorChanged() {
        display.loadPalette(minecraft, terminal.phosphor());
    }

    @Override
    public String clock() {
        return minecraft == null ? "" : FunctionKeys.clock(minecraft);
    }

    @Override
    public @Nullable TerminalDeskMenu menu() {
        return null;
    }

    @Override
    public void tick() {
        terminal.tick();
        if (minecraft.player != null && !menu.stillValid(minecraft.player)) {
            minecraft.player.closeContainer();
        }
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        CrtField focused = terminal.focused();
        int[] cursor = focused != null && terminal.ticks() / 10 % 2 == 0 ? new int[] { focused.row, focused.cursorColumn() } : null;
        display.draw(graphics, minecraft, terminal.compose(), cursor, null);
    }

    // --- Input (as the Terminal Desk's CrtScreen) ---

    @Override
    public boolean keyPressed(KeyEvent event) {
        DeskSounds.keyClack();
        int key = event.key();
        if (event.isEscape()) {
            terminal.escape();
            return true;
        }
        if (FunctionKeys.of(event) > 0) {
            return true;
        }
        if (key == InputConstants.KEY_NUMPADENTER || event.isConfirmation() && event.hasControlDown()) {
            terminal.fieldExit();
        } else if (event.isConfirmation()) {
            terminal.submit();
        } else if (key == InputConstants.KEY_TAB) {
            terminal.tab(event.hasShiftDown());
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
    public boolean keyReleased(KeyEvent event) {
        int f = FunctionKeys.of(event);
        if (f > 0) {
            terminal.functionKey(f);
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int c = event.codepoint();
        if (c >= 32 && c < 127) {
            terminal.type((char) c);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int[] cell = display.cell(minecraft, event.x(), event.y());
        if (cell != null) {
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

    @Override
    public void removed() {
        functionKeys.giveBack();
        minecraft.onTextInputFocusChange(this, false);
        super.removed();
    }

    // Esc on the editor itself goes back through the terminal (it asks about unsaved changes); closing the game's
    // screen closes the PLC's menu too.
    @Override
    public void onClose() {
        terminal.escape();
    }
}
