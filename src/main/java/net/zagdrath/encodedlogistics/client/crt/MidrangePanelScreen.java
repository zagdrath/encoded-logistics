/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.menu.MidrangePanelMenu;

// MRCTL / IMCTL (HANDOFF 3; docs/midrange/layouts/mrctl.txt, imctl.txt): the system's name, status code and word,
// threads, max job and batch jobs; its drives (tier 1: 1, or 2 with an Expansion Cabinet) or its magazine's four
// positions - 4=Eject (Remove from magazine), 5=Display recipes, 8=Make default library; its jobs - 3=Hold, 4=End,
// 6=Release; F7=IPL, F10=Hold queue, F11=Release. 5 shows the diskette's recipes instead of the lists (F12 back).
public class MidrangePanelScreen extends CrtMachineScreen<MidrangePanelMenu> {
    private static final int DRIVES_ROW = 7;

    public MidrangePanelScreen(MidrangePanelMenu menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    @Override
    String panelId() {
        return menu.integrated() ? "IMCTL" : "MRCTL";
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
    boolean commandLine() {
        return true;
    }

    private boolean recipesShown() {
        return !lines("V").isEmpty();
    }

    @Override
    void body(CrtGrid grid) {
        List<String[]> status = lines("S");
        String code = status.isEmpty() ? "" : status.getFirst()[1] + " " + tr(status.getFirst()[2]);
        grid.put(2, 2, tr("crt.encodedlogistics.mrctl.status_line", menu.opening().device(), code, menu.threads(), menu.memory(), menu.batchJobs()),
                CrtGrid.NORMAL);
        if (recipesShown()) {
            recipes(grid);
            options(List.of());
            return;
        }
        grid.put(3, 2, tr("crt.encodedlogistics.mrctl.type_options"), CrtGrid.NORMAL);
        grid.put(4, 4, tr(menu.integrated() ? "crt.encodedlogistics.imctl.drive_options" : "crt.encodedlogistics.mrctl.drive_options"), CrtGrid.NORMAL);
        List<int[]> at = new ArrayList<>();
        grid.put(6, 0, tr(menu.integrated() ? "crt.encodedlogistics.imctl.drive_head" : "crt.encodedlogistics.mrctl.drive_head"), CrtGrid.BRIGHT);
        List<String[]> drives = lines("D");
        int row = DRIVES_ROW;
        int labelCol = menu.integrated() ? 10 : 12;
        for (int i = 0; i < drives.size(); i++, row++) {
            String[] drive = drives.get(i);
            boolean empty = drive[2].isEmpty();
            grid.put(row, 5, drive[1], CrtGrid.NORMAL);
            grid.put(row, labelCol, empty ? tr("crt.encodedlogistics.mrctl.empty") : drive[2], empty ? CrtGrid.DIM : CrtGrid.NORMAL);
            if (!empty) {
                grid.put(row, labelCol + 12, tr("crt.encodedlogistics.mrctl.recipes", drive[3]), CrtGrid.NORMAL);
            }
            grid.put(row, labelCol + 21, drive[4], empty ? CrtGrid.DIM : CrtGrid.NORMAL);
            at.add(new int[] { row, 0, i });
        }
        grid.put(row, 5, tr(menu.integrated() ? "crt.encodedlogistics.hint.magazine" : "crt.encodedlogistics.hint.diskette"), CrtGrid.DIM);
        row += 2;
        grid.put(row++, 1, tr("crt.encodedlogistics.mrctl.job_head"), CrtGrid.BRIGHT);
        List<String[]> jobs = lines("J");
        if (jobs.isEmpty()) {
            grid.put(row++, 6, tr("crt.encodedlogistics.mrctl.no_jobs"), CrtGrid.DIM);
        }
        for (int i = 0; i < jobs.size() && row < 19; i++, row++) {
            String[] job = jobs.get(i);
            grid.put(row, 6, job[1], CrtGrid.NORMAL);
            grid.put(row, 12, cut(name(job[2]), 20), CrtGrid.NORMAL);
            grid.put(row, 36 - job[3].length(), job[3], CrtGrid.NORMAL);
            grid.put(row, 38, job[4], job[4].equals("*HELD") ? CrtGrid.DIM : CrtGrid.NORMAL);
            String progress = !job[6].isEmpty() ? tr("crt.encodedlogistics.mrctl.missing", name(job[6])) : job[5].isEmpty() ? "" : job[5] + "%";
            grid.put(row, 49, cut(progress, 30), job[6].isEmpty() ? CrtGrid.NORMAL : CrtGrid.BRIGHT);
            at.add(new int[] { row, 0, MidrangePanelMenu.JOB_ROW + i });
        }
        if (row < 19) {
            grid.put(row + 1, 4, tr("crt.encodedlogistics.mrctl.job_options"), CrtGrid.NORMAL);
        }
        options(at);
    }

    // 5=Display recipes: the diskette's recipes, two columns.
    private void recipes(CrtGrid grid) {
        grid.put(4, 2, tr("crt.encodedlogistics.mrctl.recipes_of", lines("V").getFirst()[1]), CrtGrid.BRIGHT);
        List<String[]> recipes = lines("R");
        if (recipes.isEmpty()) {
            grid.put(6, 4, tr("crt.encodedlogistics.mrctl.no_recipes"), CrtGrid.DIM);
        }
        for (int i = 0; i < recipes.size(); i++) {
            String[] recipe = recipes.get(i);
            grid.put(6 + i, 4, String.format("%2d", i + 1), CrtGrid.NORMAL);
            grid.put(6 + i, 8, cut(name(recipe[1]), 40) + " x" + recipe[2], CrtGrid.NORMAL);
        }
        grid.put(18, 2, tr("crt.encodedlogistics.mrctl.recipes_back"), CrtGrid.DIM);
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

    // F12 on the recipes: back to the lists.
    @Override
    boolean back() {
        if (recipesShown()) {
            button(MidrangePanelMenu.BUTTON_BACK);
            return true;
        }
        return false;
    }
}
