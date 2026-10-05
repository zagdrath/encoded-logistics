/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// DEPOSIT (from WRKINV, F6): what's in your inventory - Opt, Item, Quantity, where it is (the hotbar's slots, then the
// rest) - eleven a page. 1=Deposit puts that stack into the network; F6 puts in everything outside the hotbar. What
// doesn't fit stays with you.
final class DepositPanel extends ListPanel<Integer> {
    private static final int HOTBAR = Inventory.getSelectionSize(), SLOTS = 36;
    private int seen = -1;

    DepositPanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "DEPOSIT";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.deposit.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.deposit");
    }

    @Override
    int firstRow() {
        return 7;
    }

    @Override
    int pageSize() {
        return 11;
    }

    @Override
    Object key(Integer slot) {
        return slot;
    }

    @Override
    String defaultOption() {
        return "1";
    }

    private static Inventory inventory() {
        return Minecraft.getInstance().player.getInventory();
    }

    // The inventory's non-empty slots, again whenever it changes.
    @Override
    void tick() {
        int hash = 1;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = inventory().getItem(slot);
            hash = 31 * hash + (stack.isEmpty() ? 0 : stack.getItem().hashCode() * 64 + stack.getCount());
        }
        if (hash != seen) {
            seen = hash;
            List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < SLOTS; slot++) {
                if (!inventory().getItem(slot).isEmpty()) {
                    slots.add(slot);
                }
            }
            setRows(slots);
        }
    }

    @Override
    void refresh() {
        seen = -1;
    }

    @Override
    void drawHead(CrtGrid grid) {
        grid.put(3, 0, tr("crt.encodedlogistics.type_options"));
        grid.put(4, 0, tr("crt.encodedlogistics.deposit.opts"));
        grid.put(6, 0, tr("crt.encodedlogistics.deposit.cols"), CrtGrid.BRIGHT);
    }

    @Override
    void drawRow(CrtGrid grid, int screenRow, Integer slot) {
        ItemStack stack = inventory().getItem(slot);
        grid.put(screenRow, 5, CrtGrid.pad(stack.getHoverName().getString(), 40));
        grid.put(screenRow, 45, CrtGrid.padLeft(String.format(Locale.ROOT, "%,d", stack.getCount()), 11));
        grid.put(screenRow, 59, slot < HOTBAR ? tr("crt.encodedlogistics.deposit.hotbar", slot + 1) : tr("crt.encodedlogistics.deposit.slot", slot - HOTBAR + 1),
                slot < HOTBAR ? CrtGrid.BRIGHT : CrtGrid.NORMAL);
    }

    @Override
    boolean process(List<Option<Integer>> chosen) {
        List<String> slots = new ArrayList<>();
        for (Option<Integer> option : chosen) {
            if (!option.option().equals("1")) {
                screen.message(tr("crt.encodedlogistics.msg.invalid_option", option.option()));
                return true;
            }
            slots.add(Integer.toString(option.row()));
        }
        if (!slots.isEmpty()) {
            screen.send(TerminalService.QUERY, "deposit " + String.join(" ", slots));
        }
        return true;
    }

    // F6: everything outside the hotbar.
    @Override
    boolean functionKey(int f) {
        if (f != 6) {
            return false;
        }
        screen.send(TerminalService.QUERY, "deposit *all");
        return true;
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() == TerminalService.QUERY && response.topic().equals("deposit")) {
            seen = -1;
        }
    }
}
