/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.menu.CardReaderMenu;

// CARDRDR (HANDOFF 3; docs/midrange/layouts/cardrdr.txt): the deck in the hopper - each card's recipe and whether it's
// new on the diskette or replaces one -, the diskette's recipes before and after reading; F6=Read.
public class CardReaderScreen extends CrtMachineScreen<CardReaderMenu> {
    public CardReaderScreen(CardReaderMenu menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    @Override
    String panelId() {
        return "CARDRDR";
    }

    @Override
    String titleKey() {
        return "crt.encodedlogistics.reader.title";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.reader.keys");
    }

    private static String plan(String plan) {
        if (plan.startsWith("replaces ")) {
            return tr("crt.encodedlogistics.reader.replaces", plan.substring("replaces ".length()));
        }
        return plan.isEmpty() ? "" : tr("crt.encodedlogistics.reader.plan." + plan);
    }

    @Override
    void body(CrtGrid grid) {
        grid.put(2, 2, tr("crt.encodedlogistics.reader.instructions"), CrtGrid.NORMAL);
        grid.put(4, 2, tr("crt.encodedlogistics.reader.head"), CrtGrid.BRIGHT);
        List<String[]> cards = lines("K");
        if (cards.isEmpty()) {
            grid.put(5, 3, tr("crt.encodedlogistics.reader.no_deck"), CrtGrid.DIM);
        }
        for (int i = 0; i < cards.size(); i++) {
            String[] card = cards.get(i);
            int row = 5 + i;
            grid.put(row, 2, String.format("%2s", card[1]), CrtGrid.NORMAL);
            grid.put(row, 7, card[2].isEmpty() ? tr("crt.encodedlogistics.reader.blank_card") : cut(name(card[2]), 28) + " x" + card[3],
                    card[2].isEmpty() ? CrtGrid.DIM : CrtGrid.NORMAL);
            grid.put(row, 40, plan(card[4]), card[4].equals("full") ? CrtGrid.BRIGHT : CrtGrid.NORMAL);
        }
        int row = 5 + Math.max(1, cards.size()) + 1;
        grid.put(row, 2, tr("crt.encodedlogistics.reader.diskette"), CrtGrid.NORMAL);
        List<String[]> diskette = lines("D");
        if (diskette.isEmpty()) {
            grid.put(row, 22, tr("crt.encodedlogistics.reader.no_diskette_in"), CrtGrid.DIM);
        } else {
            String[] d = diskette.getFirst();
            grid.put(row, 22, tr("crt.encodedlogistics.reader.before_after", d[1], d[2], d[3]), CrtGrid.BRIGHT);
        }
        grid.put(row + 2, 2, tr("crt.encodedlogistics.hint.cards"), CrtGrid.DIM);
    }

    @Override
    boolean machineKey(int key) {
        if (key != 6) {
            return false;
        }
        button(CardReaderMenu.BUTTON_READ);
        return true;
    }
}
