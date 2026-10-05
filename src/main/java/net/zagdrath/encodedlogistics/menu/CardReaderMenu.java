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
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlockEntity;
import net.zagdrath.encodedlogistics.midrange.DisketteData;
import net.zagdrath.encodedlogistics.midrange.DisketteStack;
import net.zagdrath.encodedlogistics.midrange.PeripheralBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// CARDRDR (HANDOFF 3; layout cardrdr): the deck in the hopper and what reading does with each card, the diskette before
// and after. Lines, tab-separated: K seq itemKey count plan (a card: CardReaderBlockEntity.plan's "new", "replaces n",
// "full"), D label before after (the diskette's recipes). Button: Read (F6).
public class CardReaderMenu extends PeripheralMenu {
    public static final int BUTTON_READ = 0;

    private final @Nullable CardReaderBlockEntity reader;

    // Client constructor.
    public CardReaderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(CardReaderBlockEntity.HOPPER + 1), null, Opening.read(buf));
    }

    public CardReaderMenu(int containerId, Inventory inventory, Container slots, @Nullable PeripheralBlockEntity peripheral, Opening opening) {
        super(ModMenuTypes.CARD_READER.get(), containerId, inventory, slots, peripheral, opening);
        this.reader = peripheral instanceof CardReaderBlockEntity r ? r : null;
    }

    @Override
    protected void refresh() {
        if (reader == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        List<ItemStack> hopper = reader.hopper();
        ItemStack diskette = reader.getItem(CardReaderBlockEntity.DISKETTE);
        DisketteData data = diskette.isEmpty() ? null : DisketteStack.data(diskette);
        List<String> plan = CardReaderBlockEntity.plan(data, hopper);
        for (int i = 0; i < hopper.size(); i++) {
            Schematic recipe = hopper.get(i).get(ModDataComponents.PUNCHED_RECIPE.get());
            ItemStack output = recipe != null ? recipe.output() : ItemStack.EMPTY;
            lines.add(String.join("\t", "K", Integer.toString(i + 1), output.isEmpty() ? "" : output.getItem().getDescriptionId(),
                    Integer.toString(output.getCount()), plan.get(i)));
        }
        if (data != null) {
            DisketteData after = CardReaderBlockEntity.afterRead(data, hopper);
            lines.add(String.join("\t", "D", data.label().isEmpty() ? DisketteStack.DEFAULT_LABEL : data.label(), Integer.toString(data.recipes().size()),
                    Integer.toString(after.recipes().size())));
        }
        send(Component.empty(), lines, List.of(lines.size()));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (reader == null || id != BUTTON_READ) {
            return false;
        }
        send(reader.read());
        refresh();
        return true;
    }
}
