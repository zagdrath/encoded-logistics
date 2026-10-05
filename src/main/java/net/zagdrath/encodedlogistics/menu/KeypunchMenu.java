/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.midrange.KeypunchBlockEntity;
import net.zagdrath.encodedlogistics.midrange.PeripheralBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// KEYPUNCH (HANDOFF 3; layout keypunch): the 3 x 3 grid as item names (fields 0-8), what it crafts, the blank and
// punched cards. Lines, tab-separated: G cell name (each cell), O itemKey count (the output, when there is one),
// C blank punched. Buttons: Punch (F6), Clear the grid (F13).
public class KeypunchMenu extends PeripheralMenu {
    public static final int BUTTON_PUNCH = 0, BUTTON_CLEAR = 1;

    private final @Nullable KeypunchBlockEntity keypunch;

    // Client constructor.
    public KeypunchMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(2), null, Opening.read(buf));
    }

    public KeypunchMenu(int containerId, Inventory inventory, Container cards, @Nullable PeripheralBlockEntity peripheral, Opening opening) {
        super(ModMenuTypes.KEYPUNCH.get(), containerId, inventory, cards, peripheral, opening);
        this.keypunch = peripheral instanceof KeypunchBlockEntity k ? k : null;
    }

    @Override
    protected void refresh() {
        if (keypunch == null) {
            return;
        }
        keypunch.updateResult();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            lines.add("G\t" + i + "\t" + KeypunchBlockEntity.shortName(keypunch.grid().getItem(i)));
        }
        ItemStack output = keypunch.result().getItem(0);
        if (!output.isEmpty()) {
            lines.add("O\t" + output.getItem().getDescriptionId() + "\t" + output.getCount());
        }
        lines.add("C\t" + keypunch.getItem(KeypunchBlockEntity.BLANK).getCount() + "\t" + keypunch.getItem(KeypunchBlockEntity.PUNCHED).getCount());
        send(Component.empty(), lines, List.of(lines.size()));
    }

    @Override
    protected void field(int key, String text) {
        if (keypunch != null) {
            Component problem = keypunch.setCell(key, text);
            if (problem != null) {
                send(problem);
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (keypunch == null) {
            return false;
        }
        switch (id) {
            case BUTTON_PUNCH -> send(keypunch.punch());
            case BUTTON_CLEAR -> keypunch.clearGrid();
            default -> {
                return false;
            }
        }
        refresh();
        return true;
    }
}
