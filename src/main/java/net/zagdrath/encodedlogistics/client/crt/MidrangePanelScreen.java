/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.menu.MidrangePanelMenu;
import net.zagdrath.encodedlogistics.midrange.DisketteData;
import net.zagdrath.encodedlogistics.midrange.DisketteStack;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;

// MIDRANGE CONTROL PANEL / INTEGRATED SYSTEM CONTROL PANEL (HANDOFF 2, 4; previews/gui_midrange_panel,
// gui_integrated_panel): the status code and what it means; threads, max job and batch jobs; the library diskettes
// (slot A, B with an Expansion Cabinet) or the magazine and its four diskettes; the current job (jobs) with their
// progress; the job queue; IPL (F7), Hold queue (F10), Release (F11).
public class MidrangePanelScreen extends CrtMachineScreen<MidrangePanelMenu> {
    private static final int BAR = 10, QUEUE_ROW = 12, QUEUE_ROWS = 6;

    public MidrangePanelScreen(MidrangePanelMenu menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    @Override
    String titleKey() {
        return menu.integrated() ? "crt.encodedlogistics.imctl.title" : "crt.encodedlogistics.mrctl.title";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.mrctl.keys");
    }

    @Override
    List<Action> actions() {
        return List.of(new Action(tr("crt.encodedlogistics.mrctl.action.ipl"), 7), new Action(tr("crt.encodedlogistics.mrctl.action.hold"), 10),
                new Action(tr("crt.encodedlogistics.mrctl.action.release"), 11));
    }

    // The server's lines of a kind (S, C, Q), split at the tabs.
    private List<String[]> lines(String kind) {
        List<String[]> found = new ArrayList<>();
        for (String line : menu.lines()) {
            String[] fields = line.split("\t", -1);
            if (fields[0].equals(kind)) {
                found.add(fields);
            }
        }
        return found;
    }

    private static String cut(String text, int width) {
        return text.length() > width ? text.substring(0, width) : text;
    }

    @Override
    void body(CrtGrid grid) {
        status(grid);
        diskettes(grid);
        current(grid);
        queue(grid);
        grid.put(11, 51, tr("crt.encodedlogistics.machine.inventory"), CrtGrid.BRIGHT);
    }

    private void status(CrtGrid grid) {
        grid.put(3, 2, tr("crt.encodedlogistics.mrctl.status_label"), CrtGrid.NORMAL);
        List<String[]> status = lines("S");
        if (!status.isEmpty()) {
            String code = " " + status.getFirst()[1] + " ";
            grid.put(3, 22, code, CrtGrid.NORMAL);
            grid.reverse(3, 22, code.length());
            grid.put(3, 23 + code.length(), tr(status.getFirst()[2]), CrtGrid.BRIGHT);
        }
        grid.put(4, 2, tr("crt.encodedlogistics.mrctl.threads_label"), CrtGrid.NORMAL);
        grid.put(4, 22, Integer.toString(menu.threads()), CrtGrid.BRIGHT);
        grid.put(4, 28, tr("crt.encodedlogistics.mrctl.max_job_label"), CrtGrid.NORMAL);
        grid.put(4, 42, Integer.toString(menu.memory()), CrtGrid.BRIGHT);
        grid.put(4, 50, tr("crt.encodedlogistics.mrctl.batch_label"), CrtGrid.NORMAL);
        grid.put(4, 65, Integer.toString(menu.batchJobs()), CrtGrid.BRIGHT);
    }

    private void diskettes(CrtGrid grid) {
        if (menu.integrated()) {
            int count = 0, recipes = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack diskette = menu.getSlot(MidrangePanelMenu.SHOWN + i).getItem();
                String label = tr("crt.encodedlogistics.mrctl.empty");
                if (!diskette.isEmpty()) {
                    DisketteData data = DisketteStack.data(diskette);
                    count++;
                    recipes += data.recipes().size();
                    label = data.label().isEmpty() ? DisketteStack.DEFAULT_LABEL : data.label();
                }
                grid.put(8, 9 + i * 8, cut(label, 7), diskette.isEmpty() ? CrtGrid.DIM : CrtGrid.NORMAL);
            }
            grid.put(5, 2, tr("crt.encodedlogistics.mrctl.magazine_count", count, recipes), CrtGrid.BRIGHT);
            return;
        }
        grid.put(5, 2, tr("crt.encodedlogistics.mrctl.libraries"), CrtGrid.BRIGHT);
        int slots = menu.expanded() ? 2 : 1;
        for (int i = 0; i < slots; i++) {
            ItemStack diskette = menu.getSlot(MidrangeSystemBlockEntity.SLOT_A + i).getItem();
            if (diskette.isEmpty()) {
                grid.put(6 + i, 14, tr("crt.encodedlogistics.mrctl.empty"), CrtGrid.DIM);
            } else {
                DisketteData data = DisketteStack.data(diskette);
                grid.put(6 + i, 14, cut(data.label().isEmpty() ? DisketteStack.DEFAULT_LABEL : data.label(), 10), CrtGrid.NORMAL);
                grid.put(6 + i, 26, tr("crt.encodedlogistics.reader.recipes", data.recipes().size()), CrtGrid.NORMAL);
            }
        }
    }

    // A running job: tier 1 shows one over two lines (the job, then its step and bar); tier 2 up to two, a line each.
    private void current(CrtGrid grid) {
        grid.put(5, 49, tr(menu.integrated() ? "crt.encodedlogistics.mrctl.current_jobs" : "crt.encodedlogistics.mrctl.current"), CrtGrid.BRIGHT);
        List<String[]> running = lines("C");
        if (running.isEmpty()) {
            grid.put(6, 49, tr("crt.encodedlogistics.mrctl.none"), CrtGrid.DIM);
            return;
        }
        if (!menu.integrated()) {
            String[] job = running.getFirst();
            grid.put(6, 49, cut(tr("crt.encodedlogistics.mrctl.job", job[1], tr(job[2]), job[3]), 30), CrtGrid.NORMAL);
            if (!job[7].isEmpty()) {
                grid.put(7, 49, cut(tr("crt.encodedlogistics.mrctl.waiting", tr(job[7])), 30), CrtGrid.DIM);
            } else {
                int percent = Integer.parseInt(job[6]), filled = percent * BAR / 100;
                String bar = "[" + "#".repeat(filled) + ".".repeat(BAR - filled) + "]";
                grid.put(7, 49, tr("crt.encodedlogistics.mrctl.step", job[4], job[5]) + " " + bar + " " + percent + "%", CrtGrid.NORMAL);
            }
            return;
        }
        for (int i = 0; i < Math.min(2, running.size()); i++) {
            String[] job = running.get(i);
            grid.put(6 + i, 49, cut(job[1] + " " + tr(job[2]) + " x" + job[3], 24), CrtGrid.NORMAL);
            grid.right(6 + i, job[7].isEmpty() ? job[6] + "%" : tr("crt.encodedlogistics.mrctl.wait"), job[7].isEmpty() ? CrtGrid.NORMAL : CrtGrid.DIM);
        }
    }

    private void queue(CrtGrid grid) {
        grid.put(10, 2, tr("crt.encodedlogistics.mrctl.queue"), CrtGrid.BRIGHT);
        grid.put(11, 2, tr("crt.encodedlogistics.mrctl.queue.job"), CrtGrid.BRIGHT);
        grid.put(11, 8, tr("crt.encodedlogistics.mrctl.queue.item"), CrtGrid.BRIGHT);
        grid.put(11, 31, tr("crt.encodedlogistics.mrctl.queue.qty"), CrtGrid.BRIGHT);
        grid.put(11, 37, tr("crt.encodedlogistics.mrctl.queue.status"), CrtGrid.BRIGHT);
        List<String[]> queued = lines("Q");
        if (queued.isEmpty()) {
            grid.put(QUEUE_ROW, 2, tr("crt.encodedlogistics.mrctl.queue.none"), CrtGrid.DIM);
            return;
        }
        String status = tr(menu.held() ? "crt.encodedlogistics.mrctl.queue.held" : "crt.encodedlogistics.mrctl.queue.queued");
        for (int i = 0; i < Math.min(QUEUE_ROWS, queued.size()); i++) {
            String[] job = queued.get(i);
            int row = QUEUE_ROW + i;
            grid.put(row, 2, job[1], CrtGrid.NORMAL);
            grid.put(row, 8, cut(tr(job[2]), 20), CrtGrid.NORMAL);
            grid.put(row, 34 - job[3].length(), job[3], CrtGrid.NORMAL);
            grid.put(row, 37, cut(status, 13), menu.held() ? CrtGrid.DIM : CrtGrid.NORMAL);
        }
    }

    @Override
    boolean machineKey(int key) {
        switch (key) {
            case 7 -> button(MidrangePanelMenu.BUTTON_IPL);
            case 10 -> button(MidrangePanelMenu.BUTTON_HOLD);
            case 11 -> button(MidrangePanelMenu.BUTTON_RELEASE);
            default -> {
                return false;
            }
        }
        return true;
    }
}
