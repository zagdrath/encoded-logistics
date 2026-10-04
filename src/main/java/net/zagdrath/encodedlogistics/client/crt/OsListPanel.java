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

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// A Terminal OS "Work with" screen (screens handoff C.3, screens 3, 4, 8, 10-14): its rows from a screen query (a
// line of cells each, the first cell its key), " Type options, press Enter." on row 3, the option legend on row 4 at
// column 3, the column headings bright on row 6, the list from row 7. The options chosen are done one after another:
// one that opens a window or a screen waits for it to finish (next()); 4=Delete-style ones are confirmed together.
// Commands it runs come back as their message, and the list refreshes.
abstract class OsListPanel extends ListPanel<TerminalLine> {
    private final Deque<Runnable> pending = new ArrayDeque<>();

    OsListPanel(CrtTerminal screen) {
        super(screen);
    }

    // The screen query that lists the rows ("libraries", "members ZAGLIB"), and its topic.
    abstract String query();

    String topic() {
        return query().split(" ", 2)[0];
    }

    // The legend (after its leading blanks: "2=Change   4=Delete ...") and the heading row.
    abstract String legend();

    abstract String heading();

    @Override
    int firstRow() {
        return 7;
    }

    @Override
    int pageSize() {
        return 13;
    }

    @Override
    Object key(TerminalLine row) {
        return cell(row, 0);
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.list");
    }

    @Override
    void shown() {
        screen.query(query());
        next();
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, topic())) {
            setRows(response.lines());
            response.message().ifPresent(screen::message);
        } else if (response.kind() == TerminalService.COMMAND) {
            screen.query(query());
            next();
        }
    }

    // Rows 2-6: anything of its own on row 2 (drawTop), the options line, the legend, the headings.
    @Override
    void drawHead(CrtGrid grid) {
        drawTop(grid);
        grid.put(3, 1, tr("crt.encodedlogistics.type_options"));
        grid.put(4, 3, legend());
        grid.put(6, 0, heading(), CrtGrid.BRIGHT);
    }

    void drawTop(CrtGrid grid) {}

    // --- Doing the options ---

    // Queues what an option does; the queue runs as each step finishes.
    void then(Runnable step) {
        pending.add(step);
    }

    // The next queued step (after a window or a screen it opened is done with).
    void next() {
        Runnable step = pending.poll();
        if (step != null) {
            step.run();
        }
    }

    // A window for one option: the queue goes on when it closes, either way.
    void window(CrtWindow window) {
        screen.openWindow(new Chained(window));
    }

    // The confirmation for every row given a deleting option: Enter runs each row's command.
    void confirmAll(String option, List<TerminalLine> rows, java.util.function.Function<TerminalLine, String> command) {
        if (rows.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder(tr("crt.encodedlogistics.confirm.option", option)).append("\n");
        for (TerminalLine row : rows) {
            text.append("\n   ").append(cell(row, 0));
        }
        then(() -> screen.confirm(text.toString(), () -> {
            for (TerminalLine row : rows) {
                delete(row);
            }
            next();
        }, null));
    }

    // Runs the rows' options in order: each option code to a step (false: not an option this screen has).
    abstract boolean option(String code, TerminalLine row);

    // Options that delete (confirmed together, after the others).
    String deleteOption() {
        return "4";
    }

    abstract String deleteCommand(TerminalLine row);

    // Deletes a row's object (confirmed): its delete command, by default.
    void delete(TerminalLine row) {
        screen.runCommand(deleteCommand(row));
    }

    @Override
    boolean process(List<Option<TerminalLine>> chosen) {
        pending.clear();
        List<TerminalLine> deleting = new ArrayList<>();
        for (Option<TerminalLine> option : chosen) {
            String code = option.option().trim().toUpperCase(Locale.ROOT);
            if (code.equals(deleteOption())) {
                deleting.add(option.row());
            } else if (!option(code, option.row())) {
                pending.clear();
                return invalid(option);
            }
        }
        confirmAll(deleteOption(), deleting, this::deleteCommand);
        next();
        return true;
    }

    // A window that carries the queue on when it's done.
    private final class Chained extends CrtWindow {
        private final CrtWindow inner;

        Chained(CrtWindow inner) {
            super(inner.screen, inner.row, inner.col, inner.height, inner.width, inner.title);
            this.inner = inner;
            fields.addAll(inner.fields);
        }

        @Override
        void draw(CrtGrid grid) {
            inner.draw(grid);
        }

        @Override
        void page(int direction) {
            inner.page(direction);
        }

        @Override
        boolean enter() {
            boolean done = inner.enter();
            if (screen.window() != this) {
                next();
            }
            return done;
        }

        @Override
        boolean functionKey(int f) {
            return inner.functionKey(f);
        }

        @Override
        void cancelled() {
            inner.cancelled();
            next();
        }
    }
}
