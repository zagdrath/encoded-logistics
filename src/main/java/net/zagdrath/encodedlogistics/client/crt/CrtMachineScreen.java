/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.client.DeskSounds;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;
import net.zagdrath.encodedlogistics.net.MachinePayloads;

// A Midrange machine's green screen (HANDOFF 3; docs/midrange/layouts): the Terminal Desk's frame (CrtDisplay: the panel
// titled with the machine's name) and the
// Terminal OS frame - the panel's id and title, the system's name, the date and time, its body, "Selection or command"
// and the command line (on the screens that have one), the message line and its function keys. Text only: no
// buttons, no slots. Fields are underlined: values (sent when they change), options beside a list's rows (sent on
// Enter, then cleared) and the command line (run on Enter). Keys: F1 help, F3 / F12 / Esc close, F4 the list for the
// focused field (when it has one), F5 refreshes, the machine's own (F6 its action, ...); Tab and Up / Down move between
// fields. Clicking a function key's label presses it; clicking a field focuses it.
public abstract class CrtMachineScreen<M extends PeripheralMenu> extends Screen implements MenuAccess<M> {
    private static final Pattern FKEY = Pattern.compile("F(\\d+)=");
    static final int COMMAND_ROW = 21, COMMAND_COL = 5, COMMAND_LENGTH = 72;
    private static final int LIST_ROWS = 12;

    enum Kind {
        VALUE, OPTION, COMMAND
    }

    protected final M menu;
    private final CrtDisplay display = new CrtDisplay();
    private final CrtGrid grid = new CrtGrid();
    private final FunctionKeys functionKeys = new FunctionKeys();
    final List<Field> fields = new ArrayList<>();
    private @Nullable Field focused;
    private @Nullable ValueList list;
    private int ticks;

    protected CrtMachineScreen(M menu, Component title) {
        super(title);
        this.menu = menu;
        if (commandLine()) {
            fields.add(new Field(COMMAND_ROW, COMMAND_COL, COMMAND_LENGTH, PeripheralMenu.COMMAND, Kind.COMMAND, ""));
        }
    }

    @Override
    public M getMenu() {
        return menu;
    }

    // --- What a machine's screen says ---

    // Its panel id (row 0: "MRCTL") and title's lang key.
    abstract String panelId();

    abstract String titleKey();

    // Its function keys' line ("F1=Help F3=Exit F5=Refresh F6=Read F12=Cancel").
    abstract String keys();

    // Rows 2-19: its text, and where its fields go.
    abstract void body(CrtGrid grid);

    // Its own function keys (true when it had one).
    boolean machineKey(int key) {
        return false;
    }

    // F12: back from a view of its own (true), or it closes.
    boolean back() {
        return false;
    }

    // What the message line says while the machine's offline and there's no message ("" for nothing).
    String offlineMessage() {
        return tr("crt.encodedlogistics.machine.no_host");
    }

    // Whether it has "Selection or command" and the command line.
    boolean commandLine() {
        return false;
    }

    // F4 on a field: the values to pick from (null: no list for it).
    @Nullable List<String> listFor(Field field) {
        return null;
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    static String cut(String text, int width) {
        return text.length() > width ? text.substring(0, width) : text;
    }

    // An item's name from a lang key the server sent ("" stays "").
    // A lang key's text, or text sent as is ("=" and the text).
    static String name(String key) {
        return key.isEmpty() ? "" : key.startsWith("=") ? key.substring(1) : tr(key);
    }

    // The server's lines of a kind (their first field), split at the tabs.
    List<String[]> lines(String kind) {
        List<String[]> found = new ArrayList<>();
        for (String line : menu.lines()) {
            String[] parts = line.split("\t", -1);
            if (parts[0].equals(kind)) {
                found.add(parts);
            }
        }
        return found;
    }

    void button(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    // A field's value to the server.
    void sendText(int key, String text) {
        ClientPacketDistributor.sendToServer(new MachinePayloads.Text(menu.containerId, key, text));
    }

    // The option fields beside a list's rows: one at each (row, col) given, key OPTION + its index; a field's typing
    // stays while its row does.
    void options(List<int[]> at) {
        List<Field> keep = new ArrayList<>();
        for (int i = 0; i < at.size(); i++) {
            int key = PeripheralMenu.OPTION + at.get(i)[2];
            Field field = fields.stream().filter(f -> f.kind == Kind.OPTION && f.key == key).findFirst().orElse(null);
            if (field == null || field.row != at.get(i)[0] || field.col != at.get(i)[1]) {
                field = new Field(at.get(i)[0], at.get(i)[1], 3, key, Kind.OPTION, "");
            }
            keep.add(field);
        }
        fields.removeIf(f -> f.kind == Kind.OPTION);
        fields.addAll(keep);
        fields.sort(Comparator.comparingInt((Field f) -> f.row).thenComparingInt(f -> f.col));
        if (focused != null && !fields.contains(focused)) {
            focused = fields.isEmpty() ? null : fields.getFirst();
        }
    }

    // The value fields (Kind.VALUE) a view has now: those given, a field already there with the same key and place kept
    // (with what's typed in it); the focus moves to the first when the one it was in is gone.
    void valueFields(List<Field> wanted) {
        List<Field> keep = new ArrayList<>();
        for (Field want : wanted) {
            Field have = fields.stream().filter(f -> f.kind == Kind.VALUE && f.key == want.key && f.row == want.row && f.col == want.col).findFirst()
                    .orElse(null);
            keep.add(have != null ? have : want);
        }
        fields.removeIf(f -> f.kind == Kind.VALUE);
        fields.addAll(keep);
        fields.sort(Comparator.comparingInt((Field f) -> f.row).thenComparingInt(f -> f.col));
        if (focused == null || !fields.contains(focused)) {
            focused = fields.stream().filter(f -> f.kind != Kind.COMMAND).findFirst().orElse(fields.isEmpty() ? null : fields.getFirst());
        }
    }

    // --- Lifecycle ---

    @Override
    protected void init() {
        // Typed characters only arrive while something has text input focus.
        minecraft.onTextInputFocusChange(this, true);
        functionKeys.borrow(minecraft);
        display.loadPalette(minecraft, menu.opening().phosphor());
        if (focused == null && !fields.isEmpty()) {
            focused = fields.stream().filter(f -> f.kind != Kind.COMMAND).findFirst().orElse(fields.getFirst());
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
        grid.put(0, 2, panelId(), CrtGrid.NORMAL);
        grid.center(0, tr(titleKey()), CrtGrid.BRIGHT);
        String system = menu.opening().system();
        grid.right(0, tr("crt.encodedlogistics.system", system.isEmpty() ? "*OFFLINE" : system), CrtGrid.NORMAL);
        grid.right(1, FunctionKeys.clock(minecraft), CrtGrid.NORMAL);
        body(grid);
        if (commandLine()) {
            grid.put(20, 1, tr("crt.encodedlogistics.machine.selection"), CrtGrid.NORMAL);
            grid.put(COMMAND_ROW, 1, "===>", CrtGrid.NORMAL);
        }
        String message = menu.message().getString();
        if (message.isEmpty() && !menu.online()) {
            message = offlineMessage();
        }
        grid.put(22, 1, message, CrtGrid.BRIGHT);
        grid.put(23, 1, keys(), CrtGrid.BRIGHT);
        for (Field field : fields) {
            field.draw(grid);
        }
        if (list != null) {
            list.draw(grid);
        }
        return grid;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        CrtGrid composed = compose();
        int[] cursor = null;
        if (ticks / 10 % 2 == 0) {
            if (list != null) {
                cursor = new int[] { list.cursorRow(), list.col + 1 };
            } else if (focused != null) {
                cursor = new int[] { focused.row, focused.col + Math.min(focused.cursor, focused.length - 1) };
            }
        }
        display.draw(graphics, minecraft, composed, cursor, null, getTitle());
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int[] cell = display.cell(minecraft, event.x(), event.y());
        if (cell == null) {
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
        if (list != null) {
            list.click(row, col);
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
        if (list != null) {
            if (key == 3 || key == 12) {
                list = null;
            }
            return;
        }
        switch (key) {
            case 1 -> menu.receive(new MachinePayloads.Info(menu.containerId, Component.translatable("crt.encodedlogistics." + panelId().toLowerCase(Locale.ROOT)
                    + ".help"), List.of(), List.of()));
            case 3 -> onClose();
            case 12 -> {
                if (!back()) {
                    onClose();
                }
            }
            case 4 -> prompt();
            case 5 -> {
                menu.clearMessage();
                submit();
            }
            default -> machineKey(key);
        }
    }

    // F4: the focused field's list.
    private void prompt() {
        List<String> values = focused != null ? listFor(focused) : null;
        if (values == null) {
            menu.receive(new MachinePayloads.Info(menu.containerId, Component.translatable("crt.encodedlogistics.machine.no_list"), List.of(), List.of()));
            return;
        }
        if (values.isEmpty()) {
            menu.receive(new MachinePayloads.Info(menu.containerId, Component.translatable("crt.encodedlogistics.machine.list_empty"), List.of(), List.of()));
            return;
        }
        Field field = focused;
        list = new ValueList(values, picked -> {
            field.set(picked);
            field.submit();
        });
    }

    // Enter (and F5): the values that changed, the options typed (then cleared), the command line (then cleared).
    void submit() {
        for (Field field : fields) {
            field.submit();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        DeskSounds.keyClack();
        int key = event.key();
        if (event.isEscape()) {
            if (list != null) {
                list = null;
            } else {
                onClose();
            }
            return true;
        }
        // Function keys act on release (keyReleased): F3's debug overlay toggles on release.
        if (FunctionKeys.of(event) > 0) {
            return true;
        }
        if (list != null) {
            list.key(event);
            return true;
        }
        if (event.isConfirmation()) {
            submit();
        } else if (key == InputConstants.KEY_TAB && !fields.isEmpty()) {
            int at = fields.indexOf(focused);
            focused = fields.get(Math.floorMod(at + (event.hasShiftDown() ? -1 : 1), fields.size()));
        } else if ((event.isUp() || event.isDown()) && !fields.isEmpty()) {
            moveVertical(event.isUp());
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

    // Up / Down: the nearest row with fields that way (wrapping), the field there nearest the cursor's column.
    private void moveVertical(boolean up) {
        int row = focused != null ? focused.row : up ? 24 : -1;
        int col = focused != null ? focused.col + focused.cursor : 0;
        Field best = null;
        for (int step = 1; step <= 24 && best == null; step++) {
            int target = Math.floorMod(row + (up ? -step : step), 24);
            for (Field field : fields) {
                if (field.row == target && (best == null || Math.abs(field.col - col) < Math.abs(best.col - col))) {
                    best = field;
                }
            }
        }
        if (best != null) {
            focused = best;
            best.cursor = Math.min(Math.max(0, col - best.col), best.value.length());
        }
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
        if (c >= 32 && c < 127) {
            if (list != null) {
                list.type((char) c);
            } else if (focused != null) {
                focused.type((char) c);
            }
        }
        return true;
    }

    // An input field (underlined).
    final class Field {
        final int row, col, length, key;
        final Kind kind;
        final StringBuilder value;
        // Values typed as they are (item ids), not upper-cased.
        boolean keepCase;
        int cursor;
        private String sent;

        Field(int row, int col, int length, int key, Kind kind, String value) {
            this.row = row;
            this.col = col;
            this.length = length;
            this.key = key;
            this.kind = kind;
            this.value = new StringBuilder(value);
            this.sent = value;
        }

        // Not typed in since it was last sent or set.
        boolean clean() {
            return text().trim().equals(sent);
        }

        Field keepCase() {
            keepCase = true;
            return this;
        }

        String text() {
            return value.toString();
        }

        // Its value from the server (not while it's being typed in).
        void set(String text) {
            value.setLength(0);
            value.append(cut(text, length));
            sent = text().trim();
            cursor = Math.min(cursor, value.length());
        }

        void type(char c) {
            if (cursor >= length) {
                return;
            }
            char typed = keepCase ? c : Character.toUpperCase(c);
            if (cursor < value.length()) {
                value.setCharAt(cursor, typed);
            } else {
                value.append(typed);
            }
            cursor++;
            if (cursor >= length && kind == Kind.VALUE) {
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
            if (kind == Kind.VALUE) {
                if (!text.equals(sent)) {
                    sent = text;
                    sendText(key, text);
                }
            } else if (!text.isEmpty()) {
                sendText(key, text);
                value.setLength(0);
                cursor = 0;
            }
        }

        void draw(CrtGrid grid) {
            grid.put(row, col, text(), CrtGrid.BRIGHT);
            grid.underline(row, col, length);
        }
    }

    // F4's list window over the body: the values (type to jump, Up / Down / Page Up / Page Down move, Enter or a click
    // picks one, F12 / Esc closes).
    private final class ValueList {
        final int row = 4, col = 20, width = 44;
        final List<String> values;
        final Consumer<String> picked;
        int at, top;

        ValueList(List<String> values, Consumer<String> picked) {
            this.values = values;
            this.picked = picked;
        }

        int cursorRow() {
            return row + 2 + at - top;
        }

        void draw(CrtGrid grid) {
            for (int r = row; r < row + LIST_ROWS + 4; r++) {
                grid.put(r, col, " ".repeat(width), CrtGrid.NORMAL);
            }
            grid.box(row, col, LIST_ROWS + 4, width, -1);
            grid.put(row + 1, col + 2, tr("crt.encodedlogistics.machine.list_title", values.size()), CrtGrid.BRIGHT);
            for (int i = 0; i < LIST_ROWS && top + i < values.size(); i++) {
                int r = row + 2 + i;
                grid.put(r, col + 2, cut(values.get(top + i), width - 4), top + i == at ? CrtGrid.BRIGHT : CrtGrid.NORMAL);
                if (top + i == at) {
                    grid.reverse(r, col + 2, width - 4);
                }
            }
            grid.put(row + LIST_ROWS + 2, col + 2, tr("crt.encodedlogistics.machine.list_keys"), CrtGrid.DIM);
        }

        void move(int by) {
            at = Math.clamp(at + by, 0, values.size() - 1);
            if (at < top) {
                top = at;
            } else if (at >= top + LIST_ROWS) {
                top = at - LIST_ROWS + 1;
            }
        }

        void key(KeyEvent event) {
            int key = event.key();
            if (event.isConfirmation()) {
                pick();
            } else if (event.isUp()) {
                move(-1);
            } else if (event.isDown()) {
                move(1);
            } else if (key == InputConstants.KEY_PAGEUP) {
                move(-LIST_ROWS);
            } else if (key == InputConstants.KEY_PAGEDOWN) {
                move(LIST_ROWS);
            }
        }

        // A typed letter: the next value starting with it.
        void type(char c) {
            String start = String.valueOf(c).toLowerCase(Locale.ROOT);
            for (int i = 1; i <= values.size(); i++) {
                int index = (at + i) % values.size();
                String value = values.get(index).toLowerCase(Locale.ROOT);
                if (value.startsWith(start) || value.contains(":" + start)) {
                    move(index - at);
                    return;
                }
            }
        }

        void click(int r, int c) {
            int index = top + r - row - 2;
            if (c > col && c < col + width && r >= row + 2 && r < row + 2 + LIST_ROWS && index < values.size()) {
                at = index;
                pick();
            }
        }

        void pick() {
            list = null;
            picked.accept(values.get(at));
        }
    }
}
