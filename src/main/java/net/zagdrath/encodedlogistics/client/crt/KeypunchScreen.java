/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.menu.KeypunchMenu;

// KEYPUNCH (HANDOFF 5, previews/gui_keypunch): the recipe grid, the output it crafts, the blank and punched cards, the
// card image the recipe punches (columns 1-40, two rows), PUNCH (F6); F13 clears the grid.
public class KeypunchScreen extends CrtMachineScreen<KeypunchMenu> {
    public KeypunchScreen(KeypunchMenu menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    @Override
    String titleKey() {
        return "crt.encodedlogistics.keypunch.title";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.keypunch.keys");
    }

    @Override
    String action() {
        return tr("crt.encodedlogistics.keypunch.action");
    }

    @Override
    String actionHint() {
        ItemStack output = menu.getSlot(KeypunchMenu.RESULT).getItem();
        return output.isEmpty() ? tr("crt.encodedlogistics.keypunch.hint_none")
                : tr("crt.encodedlogistics.keypunch.hint", output.getHoverName().getString(), output.getCount());
    }

    @Override
    void body(CrtGrid grid) {
        grid.put(3, 2, tr("crt.encodedlogistics.keypunch.instructions"), CrtGrid.NORMAL);
        grid.put(5, 2, tr("crt.encodedlogistics.keypunch.recipe"), CrtGrid.BRIGHT);
        grid.put(5, 28, tr("crt.encodedlogistics.keypunch.output"), CrtGrid.BRIGHT);
        grid.put(5, 45, tr("crt.encodedlogistics.keypunch.cards"), CrtGrid.BRIGHT);
        grid.put(6, 45, tr("crt.encodedlogistics.keypunch.blank"), CrtGrid.NORMAL);
        grid.put(6, 56, tr("crt.encodedlogistics.keypunch.punched_label"), CrtGrid.NORMAL);
        grid.put(11, 51, tr("crt.encodedlogistics.machine.inventory"), CrtGrid.BRIGHT);
        cardImage(grid);
    }

    // The holes the recipe punches: two rows of 40 columns, from its items (dots where there's none).
    private void cardImage(CrtGrid grid) {
        grid.put(13, 2, tr("crt.encodedlogistics.keypunch.card_image"), CrtGrid.DIM);
        long bits = 0;
        if (!menu.getSlot(KeypunchMenu.RESULT).getItem().isEmpty()) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = menu.getSlot(KeypunchMenu.GRID + i).getItem();
                long id = stack.isEmpty() ? 0 : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().hashCode();
                bits = bits * 31 + id + i;
            }
            bits ^= bits >>> 29;
            bits *= 0x9E3779B97F4A7C15L;
        }
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 40; col++) {
                boolean hole = bits != 0 && (Long.rotateLeft(bits, col + row * 23) & 1) != 0;
                grid.put(14 + row, 2 + col, hole ? " " : ".", CrtGrid.DIM);
                if (hole) {
                    grid.reverse(14 + row, 2 + col, 1);
                }
            }
        }
    }

    @Override
    boolean machineKey(int key) {
        switch (key) {
            case 6 -> button(KeypunchMenu.BUTTON_PUNCH);
            case 13 -> button(KeypunchMenu.BUTTON_CLEAR);
            default -> {
                return false;
            }
        }
        return true;
    }
}
