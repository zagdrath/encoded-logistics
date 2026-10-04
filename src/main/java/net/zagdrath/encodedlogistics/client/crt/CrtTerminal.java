/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.client.screen.TerminalSettings;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// The green-screen terminal itself, apart from how it's drawn (CrtScreen): its stack of screens (panels) and the
// pop-up windows over the current one, the fields and which has focus, the command line, the message line, Command
// Entry's history, what the server said about the session (network, user, Firewall, SECLVL, PHOSPHOR, unread
// messages), and what keys and clicks do. Each frame it composes the current screen into the grid: id, title and
// system name, the date and time, its body, a prompt and the command line ("===> "), the message line ("MW" at its end
// while messages wait) and its function keys, the windows on top. Its host sends its requests to the server, closes
// it and supplies the clock, so it runs without a game (the layout tests compose screens with it).
final class CrtTerminal {
    private static final int HISTORY = 500;

    // What the terminal needs from where it runs.
    interface Host {
        void send(int kind, String text);

        void close();

        // The phosphor in use changed (the setting, or the system's value).
        void phosphorChanged();

        // The game's day and time ("Day 12  14:32:07"), or "".
        String clock();

        @Nullable TerminalDeskMenu menu();
    }

    // A line of Command Entry's history.
    record HistoryLine(String text, byte attr) {}

    private final Host host;
    private final Deque<CrtPanel> panels = new ArrayDeque<>();
    // Pop-up windows over the current screen, the top one first.
    private final Deque<CrtWindow> windows = new ArrayDeque<>();
    private final CrtGrid grid = new CrtGrid();
    final CrtField command = new CrtField(21, 5, 72, "");
    private @Nullable CrtField focused;
    private @Nullable Component message;
    final List<HistoryLine> history = new ArrayList<>();
    final List<String> commands = new ArrayList<>();
    String network = "", user = "";
    boolean firewall, signedOn;
    // Insert mode (the Insert key) for every field; the user's unread messages ("MW"); the system's PHOSPHOR.
    boolean insert;
    int unread;
    String systemPhosphor = "*GREEN";
    // The system's SECLVL (10: no sign-on) and the session's current library (Sign On; *CURLIB).
    String securityLevel = "30", currentLibrary = "ELGPL";
    private int ticks;
    // Opened and waiting for the session's details: the glass stays blank (and takes no input) until they say whether
    // it's Sign On or the main menu, or CONNECT_TICKS pass without an answer.
    boolean connecting;
    private static final java.util.regex.Pattern FKEY = java.util.regex.Pattern.compile("F(\\d+)=");
    // Commands sent and not answered yet (a screen's queued options wait for them).
    int outstanding;
    private static final int CONNECT_TICKS = 40;

    CrtTerminal(Host host) {
        this.host = host;
    }

    // Opened: the main menu (behind a blank glass until the session's details come), and those details from the server.
    void start() {
        if (panels.isEmpty()) {
            push(new MainMenuPanel(this));
            connecting = true;
            send(TerminalService.QUERY, "info");
        }
    }

    boolean connecting() {
        return connecting;
    }

    @Nullable TerminalDeskMenu getMenu() {
        return host.menu();
    }

    void onClose() {
        host.close();
    }

    // --- Panels ---

    CrtPanel current() {
        return panels.peek();
    }

    void push(CrtPanel panel) {
        windows.clear();
        panels.push(panel);
        focusFirst();
        panel.shown();
    }

    // Back one screen (F12, Esc); off the main menu, or from Sign On (it guards the rest), exit.
    void back() {
        if (panels.size() <= 1 || current() instanceof SignOnPanel) {
            onClose();
            return;
        }
        windows.clear();
        panels.pop();
        focusFirst();
        current().shown();
    }

    // Off this screen to the one under it, never exiting (Sign On, done).
    void leave() {
        if (panels.size() > 1) {
            panels.pop();
            focusFirst();
            current().shown();
        }
    }

    // SIGNOFF (Main Menu option 90): where sign-on is needed, the session signs off on the server, the history of what
    // was typed goes, and Sign On shows again for the next user; elsewhere the terminal just closes.
    void signOff() {
        if (!firewall || securityLevel.equals("10")) {
            onClose();
            return;
        }
        query("signoff");
        signedOn = false;
        currentLibrary = "ELGPL";
        history.clear();
        commands.clear();
        command.set("");
        home();
        push(new SignOnPanel(this));
        message(Component.translatable("crt.encodedlogistics.signon.signed_off"));
    }

    // Back to the main menu (GO MAIN).
    void home() {
        windows.clear();
        while (panels.size() > 1) {
            panels.pop();
        }
        focusFirst();
        current().shown();
    }

    void replace(CrtPanel panel) {
        panels.pop();
        push(panel);
    }

    // EDTMBR / WRKMBR 2=Edit, 5=Display: the source editor on a member.
    void editMember(String library, String member, boolean readOnly) {
        push(new EditorPanel(this, library, member, readOnly));
    }

    // What takes focus: the top window's fields while one's open, else the screen's (protected ones never) and the
    // command line.
    private List<CrtField> focusable() {
        List<CrtField> list = new ArrayList<>();
        CrtWindow window = windows.peek();
        for (CrtField field : window != null ? window.fields : current().fields) {
            if (!field.isProtected) {
                list.add(field);
            }
        }
        if (window == null) {
            list.add(command);
        }
        return list;
    }

    void focusFirst() {
        List<CrtField> list = focusable();
        focused = list.isEmpty() ? null : list.getFirst();
    }

    void focus(CrtField field) {
        focused = field;
    }

    @Nullable CrtField focused() {
        return focused;
    }

    void message(@Nullable Component message) {
        this.message = message;
    }

    // A message as text (null clears the line).
    void message(@Nullable String text) {
        message = text != null ? Component.literal(text) : null;
    }

    // The game's day and time, from the host.
    String clock() {
        return host.clock();
    }

    // --- Windows ---

    @Nullable CrtWindow window() {
        return windows.peek();
    }

    void openWindow(CrtWindow window) {
        windows.push(window);
        focusFirst();
    }

    void closeWindow() {
        windows.poll();
        focusFirst();
    }

    // Esc / F3 / F12 on a window: it goes, the screen under it stays.
    private void cancelWindow() {
        CrtWindow window = windows.poll();
        if (window != null) {
            window.cancelled();
        }
        focusFirst();
    }

    // F1: help for the field under the cursor, else the screen, in a window over it (screen 15).
    void help() {
        CrtPanel panel = current();
        String text = panel.help(panel.helpField(focused));
        openWindow(new CrtWindow(this, 5, 12, 13, 56, CrtPanel.tr("crt.encodedlogistics.help.title", panel.title())).text(text)
                .keys(CrtPanel.tr("crt.encodedlogistics.help.keys")));
    }

    // A confirmation (list deletes, ENDJOB): Enter does it, F12 goes back. f11, if any, is offered as another choice.
    void confirm(String text, Runnable confirmed, @Nullable Runnable f11) {
        confirm(text, confirmed, f11, f11 != null ? "crt.encodedlogistics.confirm.keys_f11" : "crt.encodedlogistics.confirm.keys");
    }

    // keys: the lang key of its keys line.
    void confirm(String text, Runnable confirmed, @Nullable Runnable f11, String keys) {
        openWindow(new CrtWindow(this, 7, 10, 10, 60, CrtPanel.tr("crt.encodedlogistics.confirm.title")) {
            @Override
            boolean enter() {
                screen.closeWindow();
                confirmed.run();
                return true;
            }

            @Override
            boolean functionKey(int f) {
                if (f == 11 && f11 != null) {
                    screen.closeWindow();
                    f11.run();
                    return true;
                }
                return false;
            }
        }.text(text).keys(CrtPanel.tr(keys)));
    }

    // --- Server ---

    // A Terminal OS screen's request (ScreenQueries).
    void query(String text) {
        send(TerminalService.SCREEN, text);
    }

    void send(int kind, String text) {
        host.send(kind, text);
    }

    // A command line: run it, and keep it and what comes back in Command Entry's history.
    void runCommand(String line) {
        commands.add(line);
        addHistory("> " + line, CrtGrid.NORMAL);
        if (line.trim().equalsIgnoreCase("clear")) {
            history.clear();
            message(Component.translatable("crt.encodedlogistics.msg.cleared"));
            return;
        }
        // A screen's command (WRKLIB, WRKMBR LIB(), ...) opens it here.
        if (ScreenCommands.open(this, line)) {
            return;
        }
        outstanding++;
        send(TerminalService.COMMAND, line);
    }

    void addHistory(String text, byte attr) {
        history.add(new HistoryLine(text, attr));
        while (history.size() > HISTORY) {
            history.removeFirst();
        }
    }

    void receive(CrtResponsePayload response) {
        if (response.kind() == TerminalService.COMMAND && outstanding > 0) {
            outstanding--;
        }
        if (response.unread() >= 0) {
            unread = response.unread();
        }
        if (response.kind() == TerminalService.QUERY && response.topic().equals("info")) {
            List<TerminalLine> lines = response.lines();
            network = lines.size() > 0 ? lines.get(0).text() : "";
            firewall = lines.size() > 1 && lines.get(1).text().equals("1");
            user = lines.size() > 2 ? lines.get(2).text() : "";
            if (lines.size() > 3 && !lines.get(3).text().equals(systemPhosphor)) {
                systemPhosphor = lines.get(3).text();
                host.phosphorChanged();
            }
            securityLevel = lines.size() > 4 ? lines.get(4).text() : "30";
            // SECLVL 10: no sign-on (OS.md 7).
            if (firewall && !signedOn && !securityLevel.equals("10")) {
                push(new SignOnPanel(this));
            }
            connecting = false;
            return;
        }
        response.message().ifPresent(this::message);
        if (response.kind() == TerminalService.COMMAND) {
            for (TerminalLine line : response.lines()) {
                addHistory(line.text(), (byte) line.attr());
            }
            response.message().ifPresent(text -> addHistory("    " + text.getString(), CrtGrid.BRIGHT));
            if (!response.lines().isEmpty() && !(current() instanceof CommandEntryPanel)) {
                push(new CommandEntryPanel(this));
            }
        }
        current().receive(response);
    }

    // --- Ticking ---

    void tick() {
        ticks++;
        if (connecting && ticks >= CONNECT_TICKS) {
            connecting = false;
        }
        current().tick();
    }

    int ticks() {
        return ticks;
    }

    // --- Composing ---

    // The frame and the current screen (and its windows), into the grid.
    CrtGrid compose() {
        CrtPanel panel = current();
        grid.clear();
        if (connecting) {
            return grid;
        }
        grid.put(0, 1, panel.id(), CrtGrid.NORMAL);
        grid.center(0, panel.title(), CrtGrid.BRIGHT);
        grid.right(0, CrtPanel.tr("crt.encodedlogistics.system", network.isEmpty() ? "*OFFLINE" : network), CrtGrid.NORMAL);
        grid.right(1, host.clock(), CrtGrid.NORMAL);
        panel.draw(grid);
        String prompt = panel.prompt();
        if (!prompt.isEmpty()) {
            grid.put(20, 1, prompt, CrtGrid.NORMAL);
            grid.put(21, 1, "===>", CrtGrid.NORMAL);
            command.draw(grid);
        }
        if (message != null) {
            String text = message.getString();
            // "MW" takes the line's end while messages wait.
            grid.put(22, 1, unread > 0 && text.length() > 73 ? text.substring(0, 73) : text, CrtGrid.BRIGHT);
        }
        if (unread > 0) {
            grid.put(22, 76, "MW", CrtGrid.BRIGHT);
        }
        grid.put(23, 1, panel.keys(), CrtGrid.BRIGHT);
        for (CrtField field : panel.fields) {
            field.draw(grid);
        }
        windows.descendingIterator().forEachRemaining(window -> window.draw(grid));
        return grid;
    }

    // --- Phosphor ---

    // The player's choice (green, amber, white), or *SYSVAL: the system's PHOSPHOR value for every terminal on it.
    void setPhosphor(String name) {
        TerminalSettings.phosphor(name);
        host.phosphorChanged();
    }

    // The phosphor in use: the player's own choice when they made one, else the system value's.
    String phosphor() {
        String chosen = TerminalSettings.phosphor();
        if (!chosen.equalsIgnoreCase(TerminalSettings.SYSVAL)) {
            return chosen;
        }
        return systemPhosphor.startsWith("*") ? systemPhosphor.substring(1).toLowerCase(Locale.ROOT) : systemPhosphor.toLowerCase(Locale.ROOT);
    }

    // --- Input ---

    // Esc: the top window goes, else back a screen.
    void escape() {
        if (window() != null) {
            cancelWindow();
        } else {
            back();
        }
    }

    // Field Exit (Ctrl+Enter, keypad Enter): the rest of the field goes, and on to the next.
    void fieldExit() {
        if (focused != null) {
            focused.fieldExit();
            nextField(false);
        }
    }

    // Tab (Shift: back): the screen's own use first (Command Entry's completion), else the next field.
    void tab(boolean back) {
        if (!back && window() == null && current().tab()) {
            return;
        }
        nextField(back);
    }

    // Tab / Shift+Tab, Up / Down, Field Exit and Field Advance: the next (or previous) field, the cursor at its end.
    void nextField(boolean back) {
        List<CrtField> fields = focusable();
        if (fields.isEmpty()) {
            return;
        }
        int at = focused != null ? fields.indexOf(focused) : -1;
        focused = fields.get(Math.floorMod(at + (back ? -1 : 1), fields.size()));
        focused.cursor = Math.min(focused.value.length(), focused.capacity - 1);
    }

    void page(int direction) {
        if (window() != null) {
            window().page(direction);
        } else {
            current().page(direction);
        }
    }

    // A typed character; Field Advance when it filled the field's last position.
    void type(char c) {
        if (focused != null && c >= 32 && c < 127 && focused.type(c, insert)) {
            nextField(false);
        }
    }

    void paste(String text) {
        if (focused != null) {
            for (char c : text.toCharArray()) {
                if (c >= 32 && c < 127) {
                    focused.type(c, true);
                }
            }
        }
    }

    // While a window is open: F3 / F12 close it, others are its own. Else the screen's own keys first (F6 Create,
    // F10, F19 / F20...), then F1 help, F3 exit, F4 prompt, F5 refresh, F9 Command Entry, F11 sort, F12 back, F13
    // clear, F24 more keys.
    void functionKey(int f) {
        CrtWindow window = window();
        if (window != null) {
            if (f == 3 || f == 12) {
                cancelWindow();
            } else {
                window.functionKey(f);
            }
            return;
        }
        if (current().functionKey(f)) {
            return;
        }
        switch (f) {
            case 1 -> help();
            case 3 -> onClose();
            case 4 -> {
                // A command on the command line: its prompter (screen 6); back here it runs (Command Entry: it's
                // placed on the command line). Anything else: the field's hint.
                if (focused == command && prompter(command.trimmed(), false, this::prompted)) {
                    return;
                }
                Component prompt = focused != null ? current().prompt(focused) : null;
                if (prompt == null && focused == command) {
                    // Not a command: what F4 does here.
                    prompt = command.trimmed().isEmpty() ? Component.translatable("crt.encodedlogistics.msg.f4_empty")
                            : Component.translatable("crt.encodedlogistics.msg.f4_not_command", command.trimmed().split("[\\s(]", 2)[0]);
                }
                if (prompt != null) {
                    message(prompt);
                }
            }
            case 5 -> {
                message((Component) null);
                current().refresh();
            }
            case 9 -> {
                if (current() instanceof SignOnPanel) {
                    return;
                }
                if (current() instanceof CommandEntryPanel entry) {
                    entry.retrieve();
                } else {
                    push(new CommandEntryPanel(this));
                }
            }
            case 11 -> current().sort();
            case 12 -> back();
            case 13 -> {
                if (current() instanceof CommandEntryPanel) {
                    history.clear();
                    message(Component.translatable("crt.encodedlogistics.msg.cleared"));
                }
            }
            case 24 -> {
                if (!(current() instanceof MoreKeysPanel) && !(current() instanceof SignOnPanel)) {
                    push(new MoreKeysPanel(this));
                }
            }
            default -> {}
        }
    }

    // The prompter for the command a text starts (its values filled in from the text): false when it isn't a command.
    // variables: the editor's (variables allowed in the values).
    boolean prompter(String text, boolean variables, Consumer<String> done) {
        CommandDefinition definition = PrompterPanel.definition(text);
        if (definition == null) {
            return false;
        }
        push(new PrompterPanel(this, definition, text, variables, done));
        return true;
    }

    private void prompted(String text) {
        if (current() instanceof CommandEntryPanel) {
            command.set(text);
            focus(command);
        } else {
            runCommand(text);
        }
    }

    // Enter: the top window's, else the command line first (a menu's option, or a command), else the screen.
    void submit() {
        if (window() != null) {
            window().enter();
            return;
        }
        String text = command.trimmed();
        if (!text.isEmpty() && !current().prompt().isEmpty()) {
            command.set("");
            if (!current().option(text)) {
                runCommand(text);
            }
            return;
        }
        if (!current().enter()) {
            message((Component) null);
        }
    }

    // A click on a cell: a function key's label presses it; a field takes focus (the cursor where it was clicked);
    // then the window or screen has it.
    void click(int row, int col, boolean doubleClick) {
        if (row == 23 && window() == null) {
            // A function key's label: "F3=Exit" (or anywhere in "F11=Full screen") pressed - the last "Fn=" starting at
            // or before the column.
            String keys = " " + current().keys();
            java.util.regex.Matcher label = FKEY.matcher(keys);
            int key = 0;
            while (label.find() && label.start() <= col + 1) {
                key = Integer.parseInt(label.group(1));
            }
            if (key > 0) {
                functionKey(key);
            }
            return;
        }
        for (CrtField field : focusable()) {
            if (field.contains(row, col)) {
                focused = field;
                field.clickAt(col);
            }
        }
        if (window() != null) {
            window().click(row, col, doubleClick);
        } else {
            current().click(row, col, doubleClick);
        }
    }
}
