/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.elcl.device.Diskette;
import net.zagdrath.encodedlogistics.elcl.device.DisketteDevice;
import net.zagdrath.encodedlogistics.menu.CardReaderMenu;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModSounds;

// The Card Reader (HANDOFF 5): punched cards in its 8-card hopper are read onto the 8" Diskette in its slot (Read, F6):
// each card's recipe is added, or replaces the one on the diskette making the same thing, up to 8 recipes. The cards
// stay in the hopper. It's a diskette drive to ELCL too (SAVLIB / RSTLIB DEV(CARDRDR01)). It works while a Midrange
// System on its network is online.
public class CardReaderBlockEntity extends PeripheralBlockEntity implements DisketteDevice {
    public static final String TYPE = "CARDRDR";
    public static final int HOPPER = 8, DISKETTE = 8;
    private static final int ACTIVE_TICKS = 30;

    public CardReaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CARD_READER.get(), pos, state, HOPPER + 1);
    }

    @Override
    public String deviceType() {
        return TYPE;
    }

    public static boolean punched(ItemStack stack) {
        return stack.is(ModItems.PUNCH_CARD.get()) && stack.has(ModDataComponents.PUNCHED_RECIPE.get());
    }

    // One card or diskette a slot.
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == DISKETTE ? stack.is(ModItems.DISKETTE_8IN.get()) : slot < HOPPER && punched(stack);
    }

    // Whether a recipe can go on the diskette: there's room, or it replaces one making the same thing.
    public static boolean fits(DisketteData data, Schematic recipe) {
        return data.recipes().size() < DisketteData.MAX_RECIPES
                || data.recipes().stream().anyMatch(on -> ItemStack.isSameItemSameComponents(on.output(), recipe.output()));
    }

    // The diskette as it would be after reading the hopper (the screen's "After read" too).
    public static DisketteData afterRead(DisketteData data, List<ItemStack> hopper) {
        for (ItemStack card : hopper) {
            Schematic recipe = card.get(ModDataComponents.PUNCHED_RECIPE.get());
            if (recipe != null && fits(data, recipe)) {
                data = data.withRecipe(recipe);
            }
        }
        return data;
    }

    // Reads the hopper onto the diskette; the message line says how it went.
    public Component read() {
        if (!isOnline()) {
            return Component.translatable("crt.encodedlogistics.machine.no_host");
        }
        ItemStack diskette = getItem(DISKETTE);
        if (diskette.isEmpty()) {
            return Component.translatable("crt.encodedlogistics.reader.no_diskette");
        }
        DisketteData data = DisketteStack.data(diskette);
        int read = 0, skipped = 0;
        for (int i = 0; i < HOPPER; i++) {
            Schematic recipe = getItem(i).get(ModDataComponents.PUNCHED_RECIPE.get());
            if (recipe == null) {
                continue;
            }
            if (fits(data, recipe)) {
                data = data.withRecipe(recipe);
                read++;
            } else {
                skipped++;
            }
        }
        if (read == 0 && skipped == 0) {
            return Component.translatable("crt.encodedlogistics.reader.no_cards");
        }
        String label = data.label();
        if (label.isEmpty()) {
            label = diskette.getCustomName() != null ? label(diskette.getCustomName().getString()) : DisketteStack.DEFAULT_LABEL;
        }
        diskette.set(ModDataComponents.DISKETTE_RECIPES.get(), new DisketteData(label, data.recipes(), data.libraries()));
        setChanged();
        activate(ACTIVE_TICKS, ModSounds.CARD_READER_WHIRR);
        return skipped > 0 ? Component.translatable("crt.encodedlogistics.reader.read_full", read, label, data.recipes().size(), skipped)
                : Component.translatable("crt.encodedlogistics.reader.read", read, label, data.recipes().size());
    }

    // A diskette's name as a label: letters and digits, upper case, at most 10.
    private static String label(String name) {
        String label = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        label = label.length() > 10 ? label.substring(0, 10) : label;
        return label.isEmpty() ? DisketteStack.DEFAULT_LABEL : label;
    }

    // --- DisketteDevice ---

    @Override
    public String name() {
        return deviceName().isEmpty() ? TYPE + "01" : deviceName();
    }

    @Override
    public boolean online() {
        return isOnline();
    }

    @Override
    public List<Diskette> mounted() {
        return getItem(DISKETTE).isEmpty() ? List.of() : List.of(new DisketteStack(() -> getItem(DISKETTE), stack -> setChanged()));
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CardReaderMenu(containerId, inventory, this, this, PeripheralMenu.Opening.SERVER);
    }
}
