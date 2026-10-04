/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// WRKCRFJOB (Main Menu option 2; WRKJOB until the Terminal OS took that name for its Work with Job): the network's
// crafting jobs, active and queued - Opt, Job, Item, Qty, Status (Active, Waiting, Recall), Progress (bar
// and %), Scheduler - refreshed every two seconds. Options: 4=Cancel (Enter again, with no other option typed, to
// confirm; F5 or any other option forgets it), 5=Display; several are done in turn, an unknown one put back.
//
// F10 switches to the jobs that ended (the network's history, CraftLog) and back: Opt, Job, Item, Qty, Status (Done,
// Failed, Cancelled), Ended, Duration, Requested by - newest first, F11 sorting by ended time, item or status, and
// "Position to" keeping those whose item starts with it (as Work with Inventory). Options: 4=Remove (confirmed, all
// together), 5=Display (the record: what it made, why it failed, who asked, the ingredients used), 7=Craft again (the
// Craft Item screen with the same item and quantity).
final class JobsPanel extends ListPanel<TerminalLine> {
    private static final int REFRESH = 40;
    // A history row's cells (TerminalService.jobHistory).
    private static final int NUMBER = 0, ITEM_ID = 1, ITEM = 2, REQUESTED = 3, STATUS = 5, ENDED = 6, ENDED_AT = 7, DURATION = 8, REQUESTED_BY = 9;

    private enum Sort {
        ENDED, ITEM, STATUS
    }

    private int timer;
    private final List<String> cancelling = new ArrayList<>();
    private boolean history;
    private final CrtField position = new CrtField(3, 27, 30, "");
    private List<TerminalLine> ended = List.of();
    private String filtered = "";
    private Sort sort = Sort.ENDED;

    JobsPanel(CrtTerminal screen) {
        super(screen);
    }

    boolean history() {
        return history;
    }

    @Override
    String id() {
        return "WRKCRFJOB";
    }

    @Override
    String title() {
        return tr(history ? "crt.encodedlogistics.jobs.title_history" : "crt.encodedlogistics.jobs.title");
    }

    @Override
    String keys() {
        return tr(history ? "crt.encodedlogistics.fkeys.wrkcrfjob_history" : "crt.encodedlogistics.fkeys.wrkcrfjob");
    }

    @Override
    int firstRow() {
        return history ? 9 : 7;
    }

    @Override
    int pageSize() {
        return history ? 11 : 12;
    }

    // A row's job number.
    @Override
    Object key(TerminalLine row) {
        return number(row);
    }

    private static String number(TerminalLine row) {
        return row.cells().isEmpty() ? "" : row.cells().getFirst().text().getString().trim();
    }

    @Override
    String defaultOption() {
        return "5";
    }

    @Override
    void shown() {
        reload();
        next();
    }

    private void reload() {
        screen.send(TerminalService.QUERY, history ? "jobhistory" : "jobs");
    }

    @Override
    void tick() {
        if (history) {
            // Position to: the list again as it's typed.
            if (!position.trimmed().equals(filtered)) {
                filtered = position.trimmed();
                show();
            }
            return;
        }
        if (++timer >= REFRESH) {
            timer = 0;
            reload();
        }
    }

    // F5: the list again, any cancel waiting for Enter forgotten.
    @Override
    void refresh() {
        cancelling.clear();
        reload();
    }

    // F10: active jobs to the history and back.
    @Override
    boolean functionKey(int f) {
        if (f != 10) {
            return false;
        }
        history = !history;
        cancelling.clear();
        pending.clear();
        clearOptions();
        top = 0;
        rows = List.of();
        if (history) {
            fields.addFirst(position);
        } else {
            fields.remove(position);
        }
        rebuild();
        screen.focusFirst();
        reload();
        return true;
    }

    // F11 (the history): ended time, item, status.
    @Override
    void sort() {
        if (!history) {
            return;
        }
        sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
        screen.message(tr("crt.encodedlogistics.history.sorted", tr("crt.encodedlogistics.history.sort." + sort.name().toLowerCase(Locale.ROOT))));
        show();
    }

    // The history as filtered and sorted.
    private void show() {
        String start = filtered.equalsIgnoreCase("*all") ? "" : filtered.toLowerCase(Locale.ROOT);
        List<TerminalLine> shown = new ArrayList<>();
        for (TerminalLine line : ended) {
            if (start.isEmpty() || cell(line, ITEM).toLowerCase(Locale.ROOT).startsWith(start)) {
                shown.add(line);
            }
        }
        Comparator<TerminalLine> newest = Comparator.comparingLong((TerminalLine line) -> endedAt(line)).reversed();
        shown.sort(switch (sort) {
            case ENDED -> newest;
            case ITEM -> Comparator.comparing((TerminalLine line) -> cell(line, ITEM), String.CASE_INSENSITIVE_ORDER).thenComparing(newest);
            case STATUS -> Comparator.comparing((TerminalLine line) -> cell(line, STATUS)).thenComparing(newest);
        });
        setRows(shown);
    }

    private static long endedAt(TerminalLine line) {
        try {
            return Long.parseLong(cell(line, ENDED_AT));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() != TerminalService.QUERY) {
            if (response.kind() == TerminalService.COMMAND) {
                shown();
            }
            return;
        }
        switch (response.topic()) {
            case "jobs" -> {
                if (!history) {
                    setRows(response.lines());
                }
            }
            case "jobhistory" -> {
                if (history) {
                    ended = response.lines();
                    show();
                }
            }
            case "canceljob" -> reload();
            case "removejobrecord" -> {
                response.message().ifPresent(screen::message);
                reload();
                next();
            }
            default -> {}
        }
    }

    @Override
    void drawHead(CrtGrid grid) {
        if (history) {
            grid.put(3, 0, tr("crt.encodedlogistics.inv.position"));
            grid.put(3, 59, tr("crt.encodedlogistics.inv.start"));
            grid.put(5, 0, tr("crt.encodedlogistics.type_options"));
            grid.put(6, 0, tr("crt.encodedlogistics.history.opts"));
            // Over the rows' columns: the quantity asked for right-aligned under Qty.
            grid.put(8, 0, "Opt  Job   Item                Qty Status    Ended         Duration Requested by", CrtGrid.BRIGHT);
            if (rows.isEmpty()) {
                grid.put(10, 5, tr(ended.isEmpty() ? "crt.encodedlogistics.history.empty" : "crt.encodedlogistics.history.none_match"), CrtGrid.DIM);
            }
            return;
        }
        grid.put(3, 0, tr("crt.encodedlogistics.type_options"));
        grid.put(4, 0, tr("crt.encodedlogistics.jobs.opts"));
        // Over the rows' columns (drawn from 5): Progress the bar and %, Scheduler its number and kind.
        grid.put(6, 0, "Opt  Job   Item                        Qty  Status    Progress      Scheduler", CrtGrid.BRIGHT);
        if (rows.isEmpty()) {
            grid.put(8, 5, tr("crt.encodedlogistics.msg.no_jobs"), CrtGrid.DIM);
        }
    }

    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        if (!history) {
            grid.put(screenRow, 5, row.text(), (byte) row.attr());
            return;
        }
        String status = cell(row, STATUS);
        byte attr = status.equals("Failed") ? CrtGrid.BRIGHT : CrtGrid.NORMAL;
        grid.put(screenRow, 5, cell(row, NUMBER), attr);
        grid.put(screenRow, 11, CrtGrid.pad(cell(row, ITEM), 17), attr);
        grid.put(screenRow, 28, CrtGrid.padLeft(cell(row, REQUESTED), 6), attr);
        grid.put(screenRow, 35, CrtGrid.pad(status, 9), attr);
        grid.put(screenRow, 45, CrtGrid.pad(cell(row, ENDED), 14), attr);
        grid.put(screenRow, 59, CrtGrid.pad(cell(row, DURATION), 8), attr);
        grid.put(screenRow, 68, CrtGrid.pad(cell(row, REQUESTED_BY), 12), attr);
    }

    @Override
    @Nullable String helpField(@Nullable CrtField field) {
        return history && field == position ? "position" : super.helpField(field);
    }

    @Override
    boolean enter() {
        if (!cancelling.isEmpty() && !anyOptions()) {
            for (String job : cancelling) {
                screen.send(TerminalService.QUERY, "canceljob " + job);
            }
            cancelling.clear();
            return true;
        }
        cancelling.clear();
        return super.enter();
    }

    @Override
    boolean process(List<Option<TerminalLine>> chosen) {
        return history ? processHistory(chosen) : processActive(chosen);
    }

    private boolean processActive(List<Option<TerminalLine>> chosen) {
        pending.clear();
        List<String> cancel = new ArrayList<>();
        for (Option<TerminalLine> option : chosen) {
            String job = number(option.row());
            switch (option.option().trim()) {
                case "4" -> cancel.add(job);
                case "5" -> then(() -> screen.push(new TextPanel(screen, "DSPJOB", tr("crt.encodedlogistics.job.title"), "job " + job)));
                default -> {
                    pending.clear();
                    return invalid(option);
                }
            }
        }
        // The cancels last: Enter again confirms them.
        if (!cancel.isEmpty()) {
            then(() -> {
                cancelling.addAll(cancel);
                screen.message(tr("crt.encodedlogistics.jobs.confirm", String.join(", ", cancel)));
            });
        }
        next();
        return true;
    }

    // The history's options in turn; the removals last, confirmed together.
    private boolean processHistory(List<Option<TerminalLine>> chosen) {
        pending.clear();
        List<TerminalLine> removing = new ArrayList<>();
        for (Option<TerminalLine> option : chosen) {
            TerminalLine row = option.row();
            String job = number(row);
            switch (option.option().trim()) {
                case "4" -> removing.add(row);
                case "5" -> then(() -> screen.push(new TextPanel(screen, "DSPJOB", tr("crt.encodedlogistics.history.title"), "jobrecord " + job)));
                case "7" -> then(() -> craftAgain(row));
                default -> {
                    pending.clear();
                    return invalid(option);
                }
            }
        }
        if (!removing.isEmpty()) {
            StringBuilder text = new StringBuilder(tr("crt.encodedlogistics.confirm.option", tr("crt.encodedlogistics.confirm.remove"))).append("\n");
            for (TerminalLine row : removing) {
                text.append("\n   ").append(number(row)).append("  ").append(cell(row, ITEM));
            }
            then(() -> screen.confirm(text.toString(), () -> {
                for (TerminalLine row : removing) {
                    screen.send(TerminalService.QUERY, "removejobrecord " + number(row));
                }
            }, null));
        }
        next();
        return true;
    }

    // 7=Craft again: the Craft Item screen with the item and quantity, while the network can still make it.
    private void craftAgain(TerminalLine row) {
        String id = cell(row, ITEM_ID);
        if (screen.getMenu() != null && !craftable(id)) {
            screen.message(tr("crt.encodedlogistics.history.not_craftable", cell(row, ITEM)));
            next();
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(cell(row, REQUESTED));
        } catch (NumberFormatException e) {
            amount = 1;
        }
        screen.push(new CraftPanel(screen, id, amount));
    }

    // Whether the desk's terminal sync lists the item (by id) as one the network can craft.
    private boolean craftable(String id) {
        for (ItemKey craftable : screen.getMenu().craftables()) {
            if (BuiltInRegistries.ITEM.getKey(craftable.stack().getItem()).toString().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
