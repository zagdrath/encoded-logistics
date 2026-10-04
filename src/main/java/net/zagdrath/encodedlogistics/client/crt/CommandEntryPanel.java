/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// CMDENT: the commands typed at this desk and what they said (newest at the bottom, the last 500 lines; PageUp rolls
// back), and the command line - ELCL commands (each message "ID  text", RTN* values shown). F4 prompts the command
// line's command (the result comes back to the command line), Tab completes its last word (an ELCL command, then
// items), F9 brings back earlier commands, F13 (Shift+F1) clears the history.
final class CommandEntryPanel extends CrtPanel {
    private static final int FIRST = 3, ROWS = 17;
    private int back, recall = -1;

    CommandEntryPanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "CMDENT";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.cmd.title");
    }

    @Override
    String prompt() {
        return tr("crt.encodedlogistics.cmd.type");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.cmdent");
    }

    @Override
    void shown() {
        screen.focus(screen.command);
        back = 0;
        recall = -1;
    }

    @Override
    void draw(CrtGrid grid) {
        grid.put(2, 2, tr("crt.encodedlogistics.cmd.history"), CrtGrid.DIM);
        List<CrtTerminal.HistoryLine> history = screen.history;
        int end = Math.max(0, history.size() - back);
        int start = Math.max(0, end - ROWS);
        for (int i = start; i < end; i++) {
            CrtTerminal.HistoryLine line = history.get(i);
            grid.put(FIRST + i - start, 2, line.text(), line.attr());
        }
    }

    @Override
    void page(int direction) {
        back = Math.max(0, Math.min(Math.max(0, screen.history.size() - ROWS), back - direction * ROWS));
    }

    // F9: the command before the one retrieved last.
    void retrieve() {
        if (screen.commands.isEmpty()) {
            return;
        }
        recall = recall < 0 ? screen.commands.size() - 1 : Math.max(0, recall - 1);
        screen.command.set(screen.commands.get(recall));
        screen.focus(screen.command);
    }

    // Tab: completes the command line's last word (F4 prompts it).
    @Override
    boolean tab() {
        if (screen.focused() == screen.command && !screen.command.trimmed().isEmpty()) {
            screen.send(TerminalService.COMPLETE, screen.command.value);
            return true;
        }
        return false;
    }

    // Completions: one fills in the last word, several are listed.
    @Override
    void receive(CrtResponsePayload response) {
        back = 0;
        if (response.kind() != TerminalService.COMPLETE) {
            return;
        }
        List<TerminalLine> options = response.lines();
        if (options.size() == 1) {
            String line = screen.command.value;
            int cut = line.lastIndexOf(' ') + 1;
            screen.command.set(line.substring(0, cut) + options.getFirst().text() + " ");
        } else if (!options.isEmpty()) {
            StringBuilder list = new StringBuilder();
            for (TerminalLine option : options) {
                if (list.length() + option.text().length() > 76) {
                    list.append(" ...");
                    break;
                }
                list.append(list.isEmpty() ? "" : "  ").append(option.text());
            }
            screen.message(list.toString());
        }
    }
}
