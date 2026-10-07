/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.menu.PlcMenu;
import net.zagdrath.encodedlogistics.plc.PlcSensors;

// A PLC's green screens (docs/plc HANDOFF 5; layouts plcsts, plcio, plcmod): PLCSTS (status and menu; F6 run, F7 stop,
// F9 clear fault, F10 I/O, F11 modules), PLCIO (the I/O table, live), PLCMOD (modules: 2=Change setting, 4=Remove
// module), the setting prompter (F4 lists its values) and the retained variables with the PLC's log. F12 goes back to
// PLCSTS (from a setting: to PLCMOD). Option 1 opens the source editor on the program (PlcEditorScreen).
public class PlcScreen extends CrtMachineScreen<PlcMenu> {
    // The answer whose X (open the editor) has been acted on.
    private int editorAt = -1;

    public PlcScreen(PlcMenu menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    private String view() {
        List<String[]> v = lines("V");
        return v.isEmpty() ? "STATUS" : v.getFirst()[1];
    }

    // The status line's field i (S's fields from 1), "" before it's come.
    private String status(int i) {
        List<String[]> s = lines("S");
        return s.isEmpty() || i >= s.getFirst().length ? "" : s.getFirst()[i];
    }

    @Override
    String panelId() {
        return switch (view()) {
            case "IO" -> "PLCIO";
            case "MODULES", "SETTING" -> "PLCMOD";
            case "RETAINED" -> "PLCRTN";
            default -> "PLCSTS";
        };
    }

    @Override
    String titleKey() {
        return "crt.encodedlogistics." + panelId().toLowerCase(Locale.ROOT) + ".title";
    }

    @Override
    String keys() {
        return tr(switch (view()) {
            case "STATUS" -> "crt.encodedlogistics.plcsts.keys";
            case "SETTING" -> "crt.encodedlogistics.plcmod.setting_keys";
            default -> "crt.encodedlogistics.plcio.keys";
        });
    }

    @Override
    boolean commandLine() {
        return true;
    }

    @Override
    String offlineMessage() {
        return "";
    }

    @Override
    boolean machineKey(int key) {
        if (key == PlcMenu.BUTTON_RUN || key == PlcMenu.BUTTON_STOP || key == PlcMenu.BUTTON_CLEAR || key == PlcMenu.BUTTON_IO
                || key == PlcMenu.BUTTON_MODULES) {
            button(key);
            return true;
        }
        return false;
    }

    @Override
    boolean back() {
        if (view().equals("STATUS")) {
            return false;
        }
        button(PlcMenu.BUTTON_BACK);
        return true;
    }

    @Override
    @Nullable List<String> listFor(Field field) {
        return switch (field.key) {
            case PlcMenu.FIELD_COUNT -> PlcSensors.COUNTS;
            case PlcMenu.FIELD_FACE -> List.of("*NORTH", "*SOUTH", "*EAST", "*WEST", "*UP", "*DOWN");
            case PlcMenu.FIELD_RADIUS -> {
                List<String> radii = new ArrayList<>();
                for (int r = 1; r <= 16; r++) {
                    radii.add(Integer.toString(r));
                }
                yield radii;
            }
            default -> null;
        };
    }

    private String mode() {
        String mode = status(1);
        return mode.isEmpty() ? "" : tr("crt.encodedlogistics.plc.mode." + mode);
    }

    private String device() {
        return menu.opening().device();
    }

    @Override
    void body(CrtGrid grid) {
        // Option 1: the editor, once per answer that asks for it.
        List<String[]> edit = lines("X");
        if (!edit.isEmpty() && menu.received() != editorAt && menu.received() != menu.editorOpenedAt) {
            editorAt = menu.received();
            menu.editorOpenedAt = menu.received();
            String program = edit.getFirst()[1];
            minecraft.execute(() -> minecraft.setScreen(new PlcEditorScreen(menu, this, program)));
        }
        switch (view()) {
            case "IO" -> io(grid);
            case "MODULES" -> modules(grid);
            case "SETTING" -> setting(grid);
            case "RETAINED" -> retained(grid);
            default -> status(grid);
        }
        if (!view().equals("MODULES")) {
            options(List.of());
        }
        if (!view().equals("SETTING")) {
            valueFields(List.of());
        }
    }

    // --- PLCSTS ---

    private void status(CrtGrid grid) {
        boolean loaded = !status(2).isEmpty();
        grid.put(2, 2, tr("crt.encodedlogistics.plcsts.plc"), CrtGrid.NORMAL);
        grid.put(2, 21, device(), CrtGrid.BRIGHT);
        grid.put(2, 35, tr("crt.encodedlogistics.plcsts.network"), CrtGrid.NORMAL);
        String network = status(11);
        grid.put(2, 51, network.isEmpty() ? tr("crt.encodedlogistics.plcsts.no_network_short") : tr("crt.encodedlogistics.plcsts.cabled", network), CrtGrid.BRIGHT);
        grid.put(3, 2, tr("crt.encodedlogistics.plcsts.program"), CrtGrid.NORMAL);
        grid.put(3, 21, loaded ? status(2) : tr("crt.encodedlogistics.plcsts.none"), loaded ? CrtGrid.BRIGHT : CrtGrid.DIM);
        if (loaded) {
            grid.put(3, 35, tr("crt.encodedlogistics.plcsts.size"), CrtGrid.NORMAL);
            grid.put(3, 49, tr("crt.encodedlogistics.plcsts.bytes", DiskDriveScreen.number(status(3)), status(4)), CrtGrid.BRIGHT);
        }
        grid.put(5, 2, tr("crt.encodedlogistics.plcsts.mode"), CrtGrid.NORMAL);
        String note = status(15);
        grid.put(5, 21, mode() + (note.isEmpty() ? "" : "  (" + note + ")"), CrtGrid.BRIGHT);
        if (status(1).equals("run")) {
            grid.put(5, 36, tr("crt.encodedlogistics.plcsts.scan"), CrtGrid.NORMAL);
            String scan = status(6);
            grid.put(5, 52, scan.equals("0") ? "-" : tr("crt.encodedlogistics.plcsts.ticks", scan), CrtGrid.BRIGHT);
        }
        grid.put(6, 2, tr("crt.encodedlogistics.plcsts.instructions"), CrtGrid.NORMAL);
        int ipt = parse(status(7)), budget = Math.max(1, parse(status(8)));
        grid.put(6, 21, tr("crt.encodedlogistics.plcsts.per_tick", ipt, budget, ipt * 100 / budget), CrtGrid.BRIGHT);
        grid.put(7, 2, tr("crt.encodedlogistics.plcsts.loaded_by"), CrtGrid.NORMAL);
        if (loaded && !status(9).isEmpty()) {
            grid.put(7, 21, tr("crt.encodedlogistics.plcsts.authority", status(9), status(10)), CrtGrid.BRIGHT);
        }
        grid.put(8, 2, tr("crt.encodedlogistics.plcsts.power"), CrtGrid.NORMAL);
        grid.put(8, 21, network.isEmpty() ? tr("crt.encodedlogistics.plcsts.buffer", DiskDriveScreen.number(status(16))) : tr("crt.encodedlogistics.plcsts.from_network"),
                CrtGrid.BRIGHT);
        grid.put(9, 2, tr("crt.encodedlogistics.plcsts.last_error"), CrtGrid.NORMAL);
        if (!status(12).isEmpty()) {
            int line = parse(status(13));
            grid.put(9, 21, status(12) + (line > 0 ? "  " + tr("crt.encodedlogistics.plcsts.line", String.format(Locale.ROOT, "%04d", line)) : ""),
                    CrtGrid.BRIGHT);
            grid.put(10, 4, cut(status(14), 74), CrtGrid.NORMAL);
        } else {
            grid.put(9, 21, tr("crt.encodedlogistics.plcsts.none"), CrtGrid.DIM);
        }
        grid.put(12, 2, tr("crt.encodedlogistics.plcsts.select"), CrtGrid.NORMAL);
        for (int i = 1; i <= 6; i++) {
            grid.put(13 + i, 6, i + ". " + tr("crt.encodedlogistics.plcsts.option." + i), CrtGrid.NORMAL);
        }
    }

    // The header line of the other views: "PLC01   DOORCTL   RUN   scan 3 ticks".
    private void header(CrtGrid grid) {
        String line = device() + "   " + (status(2).isEmpty() ? tr("crt.encodedlogistics.plcsts.none") : status(2)) + "   " + mode();
        if (status(1).equals("run") && !status(6).equals("0")) {
            line += "   " + tr("crt.encodedlogistics.plcio.scan", status(6));
        }
        grid.put(2, 2, line, CrtGrid.BRIGHT);
    }

    // --- PLCIO ---

    private void io(CrtGrid grid) {
        header(grid);
        grid.put(4, 2, tr("crt.encodedlogistics.plcio.columns"), CrtGrid.NORMAL);
        List<String[]> faces = lines("F");
        List<String[]> bars = new ArrayList<>();
        for (int i = 0; i < faces.size(); i++) {
            String[] f = faces.get(i);
            int row = 5 + i;
            grid.put(row, 2, f[1], CrtGrid.NORMAL);
            grid.put(row, 17 - f[2].length(), f[2], CrtGrid.BRIGHT);
            grid.put(row, 25 - f[3].length(), f[3], CrtGrid.BRIGHT);
            String by = f.length > 4 ? f[4] : "";
            grid.put(row, 28, by.startsWith("#") ? cut(tr(by.substring(1)), 50).toLowerCase(Locale.ROOT) : by, CrtGrid.NORMAL);
            if (parse(f[2]) > 0) {
                bars.add(new String[] { f[1], "in", f[2] });
            }
            if (parse(f[3]) > 0) {
                bars.add(new String[] { f[1], "out", f[3] });
            }
        }
        grid.put(12, 2, tr("crt.encodedlogistics.plcio.bars"), CrtGrid.NORMAL);
        for (int i = 0; i < Math.min(bars.size(), 9); i++) {
            String[] bar = bars.get(i);
            grid.put(13 + i, 2, bar[0], CrtGrid.NORMAL);
            grid.put(13 + i, 10, bar[1], CrtGrid.NORMAL);
            grid.put(13 + i, 14, "#".repeat(Math.clamp(parse(bar[2]), 0, 15)), CrtGrid.BRIGHT);
        }
        grid.put(23, 44, tr("crt.encodedlogistics.plcio.live"), CrtGrid.DIM);
    }

    // --- PLCMOD ---

    private void modulesHeader(CrtGrid grid) {
        grid.put(2, 2, tr("crt.encodedlogistics.plcmod.header", device()), CrtGrid.BRIGHT);
    }

    private void modules(CrtGrid grid) {
        modulesHeader(grid);
        grid.put(4, 2, tr("crt.encodedlogistics.plcmod.columns"), CrtGrid.NORMAL);
        List<int[]> at = new ArrayList<>();
        List<String[]> slots = lines("M");
        for (int i = 0; i < slots.size(); i++) {
            String[] m = slots.get(i);
            int row = 5 + i;
            at.add(new int[] { row, 2, i });
            // Under "Opt  Slot  Module  Setting  Value  Status" (from col 2).
            grid.put(row, 8, m[1], CrtGrid.NORMAL);
            grid.put(row, 13, m[2].isEmpty() ? tr("crt.encodedlogistics.plcmod.empty_slot") : cut(name(m[2]), 19), m[2].isEmpty() ? CrtGrid.DIM : CrtGrid.BRIGHT);
            grid.put(row, 33, cut(m[3], 16), CrtGrid.NORMAL);
            grid.put(row, 50, cut(m[4], 14), CrtGrid.BRIGHT);
            grid.put(row, 65, m[5], CrtGrid.NORMAL);
        }
        options(at);
        grid.put(10, 2, tr("crt.encodedlogistics.plcmod.options"), CrtGrid.NORMAL);
    }

    // 2=Change setting: the slot's settings, typed or picked (F4).
    private void setting(CrtGrid grid) {
        modulesHeader(grid);
        List<String[]> e = lines("E");
        if (e.isEmpty()) {
            return;
        }
        String[] s = e.getFirst();
        grid.put(4, 2, tr("crt.encodedlogistics.plcmod.change", s[1], name(s[2])), CrtGrid.BRIGHT);
        List<Field> wanted = new ArrayList<>();
        if (s[6].equals("presence_sensor")) {
            grid.put(6, 2, tr("crt.encodedlogistics.plcmod.radius"), CrtGrid.NORMAL);
            grid.put(6, 30, "1-16", CrtGrid.DIM);
            grid.put(7, 2, tr("crt.encodedlogistics.plcmod.count"), CrtGrid.NORMAL);
            grid.put(7, 30, String.join(" ", PlcSensors.COUNTS), CrtGrid.DIM);
            wanted.add(field(6, 21, 2, PlcMenu.FIELD_RADIUS, s[3]));
            wanted.add(field(7, 21, 8, PlcMenu.FIELD_COUNT, s[4]));
        } else {
            grid.put(6, 2, tr("crt.encodedlogistics.plcmod.face"), CrtGrid.NORMAL);
            grid.put(6, 30, "*NORTH *SOUTH *EAST *WEST *UP *DOWN", CrtGrid.DIM);
            wanted.add(field(6, 21, 6, PlcMenu.FIELD_FACE, s[5]));
        }
        valueFields(wanted);
        grid.put(9, 2, tr("crt.encodedlogistics.plcmod.setting_hint"), CrtGrid.DIM);
    }

    // A value field showing the server's value while it isn't being typed in.
    private Field field(int row, int col, int length, int key, String value) {
        Field existing = fields.stream().filter(f -> f.kind == Kind.VALUE && f.key == key && f.row == row && f.col == col).findFirst().orElse(null);
        if (existing != null) {
            if (existing.clean() && !existing.text().trim().equals(value)) {
                existing.set(value);
            }
            return existing;
        }
        return new Field(row, col, length, key, Kind.VALUE, value);
    }

    // --- Retained variables and the log ---

    private void retained(CrtGrid grid) {
        header(grid);
        grid.put(4, 2, tr("crt.encodedlogistics.plcrtn.columns"), CrtGrid.NORMAL);
        List<String[]> vars = lines("R");
        int row = 5;
        if (vars.isEmpty()) {
            grid.put(row++, 4, tr("crt.encodedlogistics.plcrtn.none"), CrtGrid.DIM);
        }
        for (String[] v : vars) {
            if (row > 11) {
                break;
            }
            grid.put(row, 2, v[1], CrtGrid.NORMAL);
            grid.put(row, 16, v[2], CrtGrid.NORMAL);
            grid.put(row, 24, cut(v.length > 3 ? v[3] : "", 54), CrtGrid.BRIGHT);
            row++;
        }
        grid.put(13, 2, tr("crt.encodedlogistics.plcrtn.log"), CrtGrid.NORMAL);
        List<String[]> log = lines("L");
        if (log.isEmpty()) {
            grid.put(14, 4, tr("crt.encodedlogistics.plcrtn.log_empty"), CrtGrid.DIM);
        }
        int first = Math.max(0, log.size() - 7);
        for (int i = first; i < log.size(); i++) {
            grid.put(14 + i - first, 4, cut(log.get(i)[1], 74), CrtGrid.NORMAL);
        }
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
