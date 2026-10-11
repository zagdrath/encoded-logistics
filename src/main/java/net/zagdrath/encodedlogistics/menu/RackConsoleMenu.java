/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.device.RackConsoleDevice;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// A Rack Console's terminal (screens/rack_console.json): the Access Terminal's, reaching the network the console serves
// while it's online and its drawer is out. pos is the rack's master; u the console's unit. The other consoles' menus
// (RackConsoleFabricationMenu, SchematicTerminalMenu) use the helpers here.
public class RackConsoleMenu extends AccessTerminalMenu {
    private final int u;

    // Client constructor, with the rack and unit written by the server.
    public RackConsoleMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), extraData.readVarInt(), clientRows.applyAsInt(0));
    }

    public RackConsoleMenu(int containerId, Inventory inventory, BlockPos rack, int u, int rows) {
        super(ModMenuTypes.RACK_CONSOLE.get(), containerId, inventory, rack, Direction.NORTH, rows, 0);
        this.u = u;
    }

    // Opens a console's terminal: the Access Terminal's, the Fabrication Terminal's or the Schematic Terminal's, by kind.
    public static void open(ServerPlayer player, BlockPos rack, int u, RackConsoleDevice.Kind kind) {
        if (kind == RackConsoleDevice.Kind.SCHEMATIC) {
            SchematicTerminalMenu.openConsole(player, rack, u);
            return;
        }
        Component title = Component.translatable(kind == RackConsoleDevice.Kind.FABRICATION ? "item.encodedlogistics.rack_fabrication_console"
                : "item.encodedlogistics.rack_console");
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> kind == RackConsoleDevice.Kind.FABRICATION
                ? new RackConsoleFabricationMenu(id, inventory, rack, u, DEFAULT_ROWS) : new RackConsoleMenu(id, inventory, rack, u, DEFAULT_ROWS), title),
                buf -> {
                    buf.writeBlockPos(rack);
                    buf.writeVarInt(u);
                });
    }

    // The console at a rack's unit, or null.
    public static @Nullable RackConsoleDevice console(Player player, BlockPos rack, int u) {
        return player.level().getBlockEntity(rack) instanceof RackBlockEntity at && at.deviceAt(u) instanceof RackConsoleDevice console ? console : null;
    }

    public static @Nullable NetworkRef homeNetwork(Player player, BlockPos rack, int u) {
        RackConsoleDevice console = console(player, rack, u);
        return console != null && console.rack() != null ? console.rack().network(console) : null;
    }

    public static @Nullable NetworkRef network(Player player, BlockPos rack, int u) {
        RackConsoleDevice console = console(player, rack, u);
        return console != null && console.isOnline() && console.isOpen() ? homeNetwork(player, rack, u) : null;
    }

    // Open while the console is there with its drawer out, and the player near it.
    public static boolean stillValid(Player player, BlockPos rack, int u) {
        RackConsoleDevice console = console(player, rack, u);
        return console != null && console.isOpen() && player.isWithinBlockInteractionRange(rack, 4.0);
    }

    @Override
    public @Nullable NetworkRef homeNetwork() {
        return homeNetwork(player, pos, u);
    }

    @Override
    public @Nullable NetworkRef network() {
        return network(player, pos, u);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(player, pos, u);
    }
}
