/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlockEntity;
import net.zagdrath.encodedlogistics.midrange.PeripheralBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Card Reader's screen: the 8-card hopper, the diskette slot, then the player's inventory. Button: Read (F6).
public class CardReaderMenu extends PeripheralMenu {
    public static final int BUTTON_READ = 0;
    public static final int HOPPER_X = 18, HOPPER_Y = 60, HOPPER_PITCH = 24, DISKETTE_X = 18, DISKETTE_Y = 150;

    private final @Nullable CardReaderBlockEntity reader;

    // Client constructor.
    public CardReaderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(CardReaderBlockEntity.HOPPER + 1) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        }, null, Opening.read(buf));
    }

    public CardReaderMenu(int containerId, Inventory inventory, Container slots, @Nullable PeripheralBlockEntity peripheral, Opening opening) {
        super(ModMenuTypes.CARD_READER.get(), containerId, inventory, slots, peripheral, opening);
        this.reader = peripheral instanceof CardReaderBlockEntity r ? r : null;
        for (int i = 0; i < CardReaderBlockEntity.HOPPER; i++) {
            addSlot(new MachineSlot(slots, i, HOPPER_X + i * HOPPER_PITCH, HOPPER_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return CardReaderBlockEntity.punched(stack);
                }
            });
        }
        addSlot(new MachineSlot(slots, CardReaderBlockEntity.DISKETTE, DISKETTE_X, DISKETTE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.DISKETTE_8IN.get());
            }
        });
        addPlayerSlots(inventory);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (reader == null || id != BUTTON_READ) {
            return false;
        }
        send(reader.read());
        return true;
    }
}
