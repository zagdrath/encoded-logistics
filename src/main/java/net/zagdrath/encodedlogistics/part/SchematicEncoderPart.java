/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.menu.SchematicEncoderMenu;

// The Schematic Encoder: a terminal that writes schematics onto Schematic Cards. It keeps its mode (crafting or
// processing), the ghost grid and processing outputs (with amounts), and two real slots - blank cards in, the encoded
// card out - which drop with it.
public class SchematicEncoderPart extends TerminalPart {
    public static final int CRAFTING = 0, PROCESSING = 1, BLANK = 0, ENCODED = 1;

    private final NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
    private final NonNullList<ItemStack> outputs = NonNullList.withSize(3, ItemStack.EMPTY);
    private final NonNullList<ItemStack> cards = NonNullList.withSize(2, ItemStack.EMPTY);
    private int mode = CRAFTING;

    public SchematicEncoderPart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    public NonNullList<ItemStack> grid() {
        return grid;
    }

    public NonNullList<ItemStack> outputs() {
        return outputs;
    }

    public NonNullList<ItemStack> cards() {
        return cards;
    }

    public int mode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode == PROCESSING ? PROCESSING : CRAFTING;
        changed();
    }

    public void contentsChanged() {
        changed();
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        SchematicEncoderMenu.open(player, host.getBlockPos(), side);
        return true;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack stack : cards) {
            if (!stack.isEmpty()) {
                contents.add(stack.copy());
            }
        }
        return contents;
    }

    @Override
    public void load(ValueInput input) {
        for (NonNullList<ItemStack> list : List.of(grid, outputs, cards)) {
            for (int i = 0; i < list.size(); i++) {
                list.set(i, ItemStack.EMPTY);
            }
        }
        ContainerHelper.loadAllItems(input.childOrEmpty("grid"), grid);
        ContainerHelper.loadAllItems(input.childOrEmpty("outputs"), outputs);
        ContainerHelper.loadAllItems(input.childOrEmpty("cards"), cards);
        mode = input.getIntOr("mode", CRAFTING) == PROCESSING ? PROCESSING : CRAFTING;
    }

    @Override
    public void save(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child("grid"), grid);
        ContainerHelper.saveAllItems(output.child("outputs"), outputs);
        ContainerHelper.saveAllItems(output.child("cards"), cards);
        output.putInt("mode", mode);
    }
}
