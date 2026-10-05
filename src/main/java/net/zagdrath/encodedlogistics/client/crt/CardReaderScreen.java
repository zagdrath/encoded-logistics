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
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.menu.CardReaderMenu;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlockEntity;
import net.zagdrath.encodedlogistics.midrange.DisketteData;
import net.zagdrath.encodedlogistics.midrange.DisketteStack;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// CARD READER (HANDOFF 5, previews/gui_card_reader): the 8-card hopper and what's punched on each card, the diskette
// (its library, recipes used now and after reading), READ (F6).
public class CardReaderScreen extends CrtMachineScreen<CardReaderMenu> {
    private static final int NAME = 18;

    public CardReaderScreen(CardReaderMenu menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    @Override
    String titleKey() {
        return "crt.encodedlogistics.reader.title";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.reader.keys");
    }

    @Override
    String action() {
        return tr("crt.encodedlogistics.reader.action");
    }

    private List<ItemStack> hopper() {
        List<ItemStack> cards = new ArrayList<>();
        for (int i = 0; i < CardReaderBlockEntity.HOPPER; i++) {
            cards.add(menu.getSlot(i).getItem());
        }
        return cards;
    }

    private int cards() {
        return (int) hopper().stream().filter(CardReaderBlockEntity::punched).count();
    }

    @Override
    String actionHint() {
        boolean diskette = menu.getSlot(CardReaderBlockEntity.DISKETTE).hasItem();
        return cards() == 0 || !diskette ? tr("crt.encodedlogistics.reader.hint_none") : tr("crt.encodedlogistics.reader.hint", cards());
    }

    @Override
    void body(CrtGrid grid) {
        grid.put(3, 2, tr("crt.encodedlogistics.reader.instructions"), CrtGrid.NORMAL);
        grid.put(5, 2, tr("crt.encodedlogistics.reader.hopper"), CrtGrid.BRIGHT);
        grid.put(8, 2, tr("crt.encodedlogistics.reader.cards"), CrtGrid.BRIGHT);
        List<ItemStack> hopper = hopper();
        for (int i = 0; i < hopper.size(); i++) {
            int row = 9 + i % 4, col = i < 4 ? 3 : 26;
            Schematic recipe = hopper.get(i).get(ModDataComponents.PUNCHED_RECIPE.get());
            String text;
            if (recipe == null) {
                text = tr("crt.encodedlogistics.reader.empty");
            } else {
                ItemStack output = recipe.output();
                String name = output.getHoverName().getString();
                text = (name.length() > NAME ? name.substring(0, NAME) : name) + " x" + output.getCount();
            }
            grid.put(row, col, Integer.toString(i + 1), CrtGrid.NORMAL);
            grid.put(row, col + 3, text, recipe == null ? CrtGrid.DIM : CrtGrid.NORMAL);
        }
        grid.put(11, 51, tr("crt.encodedlogistics.machine.inventory"), CrtGrid.BRIGHT);
        grid.put(14, 2, tr("crt.encodedlogistics.reader.diskette"), CrtGrid.BRIGHT);
        ItemStack diskette = menu.getSlot(CardReaderBlockEntity.DISKETTE).getItem();
        if (!diskette.isEmpty()) {
            DisketteData data = DisketteStack.data(diskette);
            DisketteData after = CardReaderBlockEntity.afterRead(data, hopper);
            grid.put(15, 10, tr("crt.encodedlogistics.reader.library"), CrtGrid.NORMAL);
            grid.put(15, 25, data.label().isEmpty() ? DisketteStack.DEFAULT_LABEL : data.label(), CrtGrid.BRIGHT);
            grid.put(16, 10, tr("crt.encodedlogistics.reader.used"), CrtGrid.NORMAL);
            grid.put(16, 25, tr("crt.encodedlogistics.reader.recipes", data.recipes().size()), CrtGrid.BRIGHT);
            grid.put(17, 10, tr("crt.encodedlogistics.reader.after"), CrtGrid.NORMAL);
            grid.put(17, 25, tr("crt.encodedlogistics.reader.recipes", after.recipes().size()), CrtGrid.BRIGHT);
        }
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
