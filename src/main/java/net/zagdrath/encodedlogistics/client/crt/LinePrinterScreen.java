/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.menu.LinePrinterMenu;

// LINE PRINTER (HANDOFF 5, previews/gui_line_printer): the report (1-4) and, for 4, the spooled file; its pages and the
// paper they need; the paper and the printed output; the report's first lines between dotted rules; PRINT (F6).
public class LinePrinterScreen extends CrtMachineScreen<LinePrinterMenu> {
    private static final int PREVIEW_WIDTH = 47;
    private static final String[] REPORTS = { "inventory", "joblog", "devices", "splf" };

    public LinePrinterScreen(LinePrinterMenu menu, Inventory inventory, Component title) {
        super(menu, title);
        fields.add(new Field(5, 22, 1, LinePrinterMenu.FIELD_REPORT, "1"));
        fields.add(new Field(9, 22, 10, LinePrinterMenu.FIELD_FILE, "*LAST"));
    }

    @Override
    String titleKey() {
        return "crt.encodedlogistics.printer.title";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.printer.keys");
    }

    @Override
    List<Action> actions() {
        return List.of(new Action(tr("crt.encodedlogistics.printer.action"), 6));
    }

    private String pages() {
        return menu.received() == 0 ? "-" : Integer.toString(menu.number(0));
    }

    @Override
    String actionHint() {
        return tr("crt.encodedlogistics.printer.hint", pages());
    }

    @Override
    void body(CrtGrid grid) {
        grid.put(3, 2, tr("crt.encodedlogistics.printer.instructions"), CrtGrid.NORMAL);
        grid.put(5, 2, tr("crt.encodedlogistics.printer.report"), CrtGrid.NORMAL);
        for (int i = 0; i < REPORTS.length; i++) {
            grid.put(5 + i, 26, (i + 1) + "=" + tr("crt.encodedlogistics.printer.report." + REPORTS[i]), CrtGrid.NORMAL);
        }
        grid.put(9, 2, tr("crt.encodedlogistics.printer.file"), CrtGrid.NORMAL);
        grid.put(10, 2, tr("crt.encodedlogistics.printer.pages"), CrtGrid.NORMAL);
        grid.put(10, 22, pages(), CrtGrid.BRIGHT);
        grid.put(10, 28, tr("crt.encodedlogistics.printer.paper_needed"), CrtGrid.NORMAL);
        grid.put(10, 44, pages(), CrtGrid.BRIGHT);
        grid.put(12, 2, tr("crt.encodedlogistics.printer.paper"), CrtGrid.BRIGHT);
        grid.put(12, 20, tr("crt.encodedlogistics.printer.output"), CrtGrid.BRIGHT);
        grid.put(11, 51, tr("crt.encodedlogistics.machine.inventory"), CrtGrid.BRIGHT);
        String rule = ".".repeat(PREVIEW_WIDTH);
        grid.put(15, 2, rule, CrtGrid.DIM);
        List<String> lines = menu.lines();
        for (int i = 0; i < Math.min(LinePrinterMenu.PREVIEW_LINES, lines.size()); i++) {
            String line = lines.get(i);
            grid.put(16 + i, 2, line.length() > PREVIEW_WIDTH ? line.substring(0, PREVIEW_WIDTH) : line, CrtGrid.DIM);
        }
        grid.put(19, 2, rule, CrtGrid.DIM);
    }

    @Override
    boolean machineKey(int key) {
        if (key != 6) {
            return false;
        }
        fields.forEach(Field::submit);
        button(LinePrinterMenu.BUTTON_PRINT);
        return true;
    }
}
