/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.device.RackConsoleDevice;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// A Rack Console's terminal once a Memory Die has been applied (screens/rack_console_fabrication.json): the
// Fabrication Terminal's, its crafting grid kept in the console. As RackConsoleMenu otherwise.
public class RackConsoleFabricationMenu extends FabricationTerminalMenu {
    private final int u;

    public RackConsoleFabricationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), extraData.readVarInt(), clientRows.applyAsInt(SECTION));
    }

    public RackConsoleFabricationMenu(int containerId, Inventory inventory, BlockPos rack, int u, int rows) {
        super(ModMenuTypes.RACK_CONSOLE_FABRICATION.get(), containerId, inventory, rack, Direction.NORTH, rows);
        this.u = u;
        loadGrid();
    }

    @Override
    protected @Nullable NonNullList<ItemStack> savedGrid() {
        RackConsoleDevice console = player.level().isClientSide() ? null : RackConsoleMenu.console(player, pos, u);
        return console != null ? console.grid() : null;
    }

    @Override
    protected void savedGridChanged() {
        RackConsoleDevice console = RackConsoleMenu.console(player, pos, u);
        if (console != null) {
            console.gridChanged();
        }
    }

    @Override
    public @Nullable NetworkRef homeNetwork() {
        return RackConsoleMenu.homeNetwork(player, pos, u);
    }

    @Override
    public @Nullable NetworkRef network() {
        return RackConsoleMenu.network(player, pos, u);
    }

    @Override
    public boolean stillValid(Player player) {
        return RackConsoleMenu.stillValid(player, pos, u);
    }
}
