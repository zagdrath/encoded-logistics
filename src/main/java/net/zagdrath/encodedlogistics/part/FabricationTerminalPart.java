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
import net.zagdrath.encodedlogistics.menu.FabricationTerminalMenu;

// The Fabrication Terminal: an Access Terminal with a 3x3 crafting grid of real items, kept with the part and dropped
// with it.
public class FabricationTerminalPart extends TerminalPart {
    private final NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);

    public FabricationTerminalPart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    public NonNullList<ItemStack> grid() {
        return grid;
    }

    public void gridChanged() {
        changed();
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        FabricationTerminalMenu.open(player, host.getBlockPos(), side);
        return true;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack stack : grid) {
            if (!stack.isEmpty()) {
                contents.add(stack.copy());
            }
        }
        return contents;
    }

    @Override
    public void load(ValueInput input) {
        for (int i = 0; i < grid.size(); i++) {
            grid.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input.childOrEmpty("grid"), grid);
    }

    @Override
    public void save(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child("grid"), grid);
    }
}
