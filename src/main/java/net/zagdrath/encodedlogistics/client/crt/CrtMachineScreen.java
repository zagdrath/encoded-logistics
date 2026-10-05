/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.client.DeskSounds;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;
import net.zagdrath.encodedlogistics.net.MachinePayloads;

// A Midrange peripheral's green screen (HANDOFF 5: the Keypunch's, Card Reader's, Line Printer's): the same CRT as the
// Terminal Desk's (CrtDisplay) and the same frame as every Terminal OS screen - its id (the device's name) and title,
// the system's name, the date and time, its body (rows 3-20), the message line and its function keys - with the
// machine's slots, and the player's inventory at the lower right, drawn on the glass as phosphor frames. Slots work as
// in any inventory screen (click, right-click, shift-click; dropped outside the screen). Keys: F3 / F12 / Esc close,
// F5 refreshes, the machine's own (F6 its action - also its reversed label on row 20 - and others); typing goes to the
// focused field, Tab moves between fields, Enter submits them. Clicking a function key presses it.
public abstract class CrtMachineScreen<M extends PeripheralMenu> extends Screen implements MenuAccess<M> {
    private static final Pattern FKEY = Pattern.compile("F(\\d+)=");
    static final int ACTION_ROW = 20, ACTION_COL = 2;
    // The slot index that means off the screen (the vanilla screens' too).
    private static final int OUTSIDE = -999;

    protected final M menu;
    private final CrtDisplay display = new CrtDisplay();
    private final CrtGrid grid = new CrtGrid();
    private final FunctionKeys functionKeys = new FunctionKeys();
    final List<Field> fields = new ArrayList<>();
    private @Nullable Field focused;
    private int ticks;

    protected CrtMachineScreen(M menu, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public M getMenu() {
        return menu;
    }

    // --- What a machine's screen says ---

    abstract String titleKey();

    // Its function keys' line ("F3=Exit  F5=Refresh  F6=Punch  F12=Cancel").
    abstract String keys();

    // Its action's label on row 20 ("PUNCH"; F6), and what it'll do (dim, after it).
    abstract String action();

    abstract String actionHint();

    // Rows 3-19 (and 21): text, labels for the slots.
    abstract void body(CrtGrid grid);

    // Its own function keys (true when it had one); F6 is its action.
    abstract boolean machineKey(int key);

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    void button(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    // A field's value to the server.
    void sendText(int key, String text) {
        ClientPacketDistributor.sendToServer(new MachinePayloads.Text(menu.containerId, key, text));
    }

    // --- Lifecycle ---

    @Override
    protected void init() {
        // Typed characters only arrive while something has text input focus.
        minecraft.onTextInputFocusChange(this, true);
        functionKeys.borrow(minecraft);
        display.loadPalette(minecraft, menu.opening().phosphor());
        if (focused == null && !fields.isEmpty()) {
            focused = fields.getFirst();
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (minecraft.player != null && !menu.stillValid(minecraft.player)) {
            onClose();
        }
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

    @Override
    public void onClose() {
        if (minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }

    // --- Drawing ---

    private CrtGrid compose() {
        grid.clear();
        grid.put(0, 1, menu.opening().device(), CrtGrid.NORMAL);
        grid.center(0, tr(titleKey()), CrtGrid.BRIGHT);
        String system = menu.opening().system();
        grid.right(0, tr("crt.encodedlogistics.system", system.isEmpty() ? "*OFFLINE" : system), CrtGrid.NORMAL);
        grid.right(1, FunctionKeys.clock(minecraft), CrtGrid.NORMAL);
        body(grid);
        String action = " " + action() + " ";
        grid.put(ACTION_ROW, ACTION_COL, action, CrtGrid.NORMAL);
        grid.reverse(ACTION_ROW, ACTION_COL, action.length());
        grid.put(ACTION_ROW, ACTION_COL + action.length() + 2, actionHint(), CrtGrid.DIM);
        String message = menu.message().getString();
        if (message.isEmpty() && !menu.online()) {
            message = tr("crt.encodedlogistics.machine.no_host");
        }
        grid.put(22, 1, message, CrtGrid.BRIGHT);
        grid.put(23, 1, keys(), CrtGrid.BRIGHT);
        for (Field field : fields) {
            field.draw(grid);
        }
        return grid;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Slot hovered = slotAt(mouseX, mouseY);
        int[] cursor = focused != null && ticks / 10 % 2 == 0 ? new int[] { focused.row, focused.col + Math.min(focused.cursor, focused.length - 1) } : null;
        CrtGrid composed = compose();
        display.draw(graphics, minecraft, composed, cursor, slots(hovered));
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            graphics.nextStratum();
            graphics.item(carried, mouseX - 8, mouseY - 8);
            graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8);
        } else if (hovered != null && hovered.hasItem()) {
            graphics.setTooltipForNextFrame(font, hovered.getItem(), mouseX, mouseY);
        }
    }

    // The slots on the glass (virtual pixels): a frame each, lit while the mouse is on it, and its item - drawn square
    // (the CRT's pixels are taller than wide).
    private Consumer<GuiGraphicsExtractor> slots(@Nullable Slot hovered) {
        return graphics -> {
            int frame = display.palette().normal(), lit = (0x50 << 24) | (display.palette().normal() & 0xFFFFFF);
            for (Slot slot : menu.slots) {
                int x = CrtDisplay.MARGIN_X + slot.x, y = CrtDisplay.MARGIN_Y + slot.y;
                int w = PeripheralMenu.SLOT_W, h = PeripheralMenu.SLOT_H;
                graphics.fill(x, y, x + w, y + 1, frame);
                graphics.fill(x, y + h - 1, x + w, y + h, frame);
                graphics.fill(x, y + 1, x + 1, y + h - 1, frame);
                graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, frame);
                if (slot == hovered) {
                    graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, lit);
                }
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(x + 1, y + 1);
                    graphics.pose().scale(1, 1 / CrtDisplay.PIXEL_ASPECT);
                    graphics.item(stack, 0, 0);
                    graphics.itemDecorations(font, stack, 0, 0);
                    graphics.pose().popMatrix();
                }
            }
        };
    }

    // The slot under a point on the GUI, or null.
    private @Nullable Slot slotAt(double mouseX, double mouseY) {
        double[] v = display.virtual(minecraft, mouseX, mouseY);
        double x = v[0] - CrtDisplay.MARGIN_X, y = v[1] - CrtDisplay.MARGIN_Y;
        for (Slot slot : menu.slots) {
            if (x >= slot.x && x < slot.x + PeripheralMenu.SLOT_W && y >= slot.y && y < slot.y + PeripheralMenu.SLOT_H) {
                return slot;
            }
        }
        return null;
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        Slot slot = slotAt(event.x(), event.y());
        if (slot != null) {
            ContainerInput input = event.hasShiftDown() ? ContainerInput.QUICK_MOVE
                    : doubleClick && button == InputConstants.MOUSE_BUTTON_LEFT && !menu.getCarried().isEmpty() ? ContainerInput.PICKUP_ALL : ContainerInput.PICKUP;
            minecraft.gameMode.handleContainerInput(menu.containerId, slot.index, button, input, minecraft.player);
            return true;
        }
        int[] cell = display.cell(minecraft, event.x(), event.y());
        if (cell == null) {
            // Off the screen: drops what's in hand.
            if (!menu.getCarried().isEmpty()) {
                minecraft.gameMode.handleContainerInput(menu.containerId, OUTSIDE, button, ContainerInput.PICKUP, minecraft.player);
            }
            return true;
        }
        int row = cell[0], col = cell[1];
        if (row == 23) {
            // A function key's label: the last "Fn=" starting at or before the column.
            Matcher label = FKEY.matcher(" " + keys());
            int key = 0;
            while (label.find() && label.start() <= col + 1) {
                key = Integer.parseInt(label.group(1));
            }
            if (key > 0) {
                functionKey(key);
            }
            return true;
        }
        if (row == ACTION_ROW && col >= ACTION_COL && col < ACTION_COL + action().length() + 2) {
            functionKey(6);
            return true;
        }
        for (Field field : fields) {
            if (row == field.row && col >= field.col && col < field.col + field.length) {
                focused = field;
                field.cursor = Math.min(col - field.col, field.value.length());
            }
        }
        return true;
    }

    private void functionKey(int key) {
        switch (key) {
            case 3, 12 -> onClose();
            case 5 -> {
                menu.clearMessage();
                fields.forEach(Field::submit);
            }
            default -> machineKey(key);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        DeskSounds.keyClack();
        int key = event.key();
        if (event.isEscape()) {
            onClose();
            return true;
        }
        // Function keys act on release (keyReleased): F3's debug overlay toggles on release.
        if (FunctionKeys.of(event) > 0) {
            return true;
        }
        if (event.isConfirmation()) {
            fields.forEach(Field::submit);
        } else if (key == InputConstants.KEY_TAB && !fields.isEmpty()) {
            int at = fields.indexOf(focused);
            focused = fields.get(Math.floorMod(at + (event.hasShiftDown() ? -1 : 1), fields.size()));
        } else if (focused != null) {
            if (key == InputConstants.KEY_BACKSPACE) {
                focused.backspace();
            } else if (key == InputConstants.KEY_DELETE) {
                focused.delete();
            } else if (event.isLeft()) {
                focused.cursor = Math.max(0, focused.cursor - 1);
            } else if (event.isRight()) {
                focused.cursor = Math.min(focused.value.length(), focused.cursor + 1);
            } else if (key == InputConstants.KEY_HOME) {
                focused.cursor = 0;
            } else if (key == InputConstants.KEY_END) {
                focused.cursor = focused.value.length();
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        int f = FunctionKeys.of(event);
        if (f > 0) {
            functionKey(f);
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int c = event.codepoint();
        if (c >= 32 && c < 127 && focused != null) {
            focused.type((char) c);
        }
        return true;
    }

    // An input field (underlined): its text, upper-cased; submitted to the server on Enter, F5, or when it fills.
    final class Field {
        final int row, col, length, key;
        final StringBuilder value;
        int cursor;
        private String sent;

        Field(int row, int col, int length, int key, String value) {
            this.row = row;
            this.col = col;
            this.length = length;
            this.key = key;
            this.value = new StringBuilder(value);
            this.sent = value;
        }

        String text() {
            return value.toString();
        }

        void type(char c) {
            if (cursor >= length) {
                return;
            }
            char upper = Character.toUpperCase(c);
            if (cursor < value.length()) {
                value.setCharAt(cursor, upper);
            } else {
                value.append(upper);
            }
            cursor++;
            if (cursor >= length) {
                submit();
            }
        }

        void backspace() {
            if (cursor > 0) {
                value.deleteCharAt(--cursor);
            }
        }

        void delete() {
            if (cursor < value.length()) {
                value.deleteCharAt(cursor);
            }
        }

        void submit() {
            String text = text().trim();
            if (!text.equals(sent)) {
                sent = text;
                sendText(key, text);
            }
        }

        void draw(CrtGrid grid) {
            grid.put(row, col, text(), CrtGrid.BRIGHT);
            grid.underline(row, col, length);
        }
    }
}
