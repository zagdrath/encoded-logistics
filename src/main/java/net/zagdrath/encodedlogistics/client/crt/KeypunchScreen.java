/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.KeypunchMenu;

// KEYPUNCH (HANDOFF 3; docs/midrange/layouts/keypunch.txt): an item name in each of the grid's nine positions (F4: the
// item list), the result, the blank and punched cards, the card image the recipe punches (columns 1-40, two rows);
// F6=Punch, F13 clears the grid.
public class KeypunchScreen extends CrtMachineScreen<KeypunchMenu> {
    private static final int[] ROWS = { 5, 7, 9 }, COLS = { 4, 28, 52 };
    private static final int CELL = 22;
    private int seen = -1;

    public KeypunchScreen(KeypunchMenu menu, Inventory inventory, Component title) {
        super(menu, title);
        for (int i = 0; i < 9; i++) {
            fields.add(new Field(ROWS[i / 3], COLS[i % 3], CELL, i, Kind.VALUE, "").keepCase());
        }
    }

    @Override
    String panelId() {
        return "CARDPUNCH";
    }

    @Override
    String titleKey() {
        return "crt.encodedlogistics.keypunch.title";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.keypunch.keys");
    }

    // F4: every item, those with the typed text in their name first.
    @Override
    @Nullable List<String> listFor(Field field) {
        if (field.key < 0 || field.key >= 9) {
            return null;
        }
        String typed = field.text().trim().toLowerCase(Locale.ROOT);
        List<String> matching = new ArrayList<>(), rest = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            String name = id.getNamespace().equals(EncodedLogistics.MODID) || id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
            if (name.equals("air")) {
                continue;
            }
            (typed.isEmpty() || name.contains(typed) ? matching : rest).add(name);
        }
        matching.sort(null);
        rest.sort(null);
        matching.addAll(rest);
        return matching;
    }

    // The grid from the server, except a cell that's being typed in.
    private void sync() {
        if (menu.received() == seen) {
            return;
        }
        seen = menu.received();
        for (String[] cell : lines("G")) {
            int i = Integer.parseInt(cell[1]);
            for (Field field : fields) {
                if (field.key == i && field.clean()) {
                    field.set(cell[2]);
                }
            }
        }
    }

    @Override
    void body(CrtGrid grid) {
        sync();
        grid.put(2, 2, tr("crt.encodedlogistics.keypunch.instructions"), CrtGrid.NORMAL);
        List<String[]> output = lines("O");
        grid.put(12, 2, tr("crt.encodedlogistics.keypunch.result"), CrtGrid.NORMAL);
        grid.put(12, 22, output.isEmpty() ? tr("crt.encodedlogistics.keypunch.nothing") : name(output.getFirst()[1]) + " x" + output.getFirst()[2],
                output.isEmpty() ? CrtGrid.DIM : CrtGrid.BRIGHT);
        List<String[]> cards = lines("C");
        String blank = cards.isEmpty() ? "0" : cards.getFirst()[1], punched = cards.isEmpty() ? "0" : cards.getFirst()[2];
        grid.put(14, 2, tr("crt.encodedlogistics.keypunch.blank_cards"), CrtGrid.NORMAL);
        grid.put(14, 22, blank, CrtGrid.BRIGHT);
        grid.put(14, 27, tr("crt.encodedlogistics.keypunch.blank_hint"), CrtGrid.DIM);
        grid.put(15, 2, tr("crt.encodedlogistics.keypunch.punched_cards"), CrtGrid.NORMAL);
        grid.put(15, 22, punched, CrtGrid.BRIGHT);
        grid.put(15, 27, tr("crt.encodedlogistics.keypunch.punched_hint"), CrtGrid.DIM);
        cardImage(grid, !output.isEmpty());
    }

    // The holes the recipe punches: two rows of 40 columns, from its items (dots where there's none).
    private void cardImage(CrtGrid grid, boolean crafts) {
        grid.put(17, 2, tr("crt.encodedlogistics.keypunch.card_image"), CrtGrid.NORMAL);
        long bits = 0;
        if (crafts) {
            for (String[] cell : lines("G")) {
                bits = bits * 31 + cell[2].hashCode() + Integer.parseInt(cell[1]);
            }
            bits ^= bits >>> 29;
            bits *= 0x9E3779B97F4A7C15L;
        }
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 40; col++) {
                boolean hole = bits != 0 && (Long.rotateLeft(bits, col + row * 23) & 1) != 0;
                grid.put(18 + row, 3 + col, hole ? " " : ".", CrtGrid.DIM);
                if (hole) {
                    grid.reverse(18 + row, 3 + col, 1);
                }
            }
        }
    }

    @Override
    boolean machineKey(int key) {
        switch (key) {
            case 6 -> {
                submit();
                button(KeypunchMenu.BUTTON_PUNCH);
            }
            case 13 -> {
                for (Field field : fields) {
                    field.set("");
                }
                button(KeypunchMenu.BUTTON_CLEAR);
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
