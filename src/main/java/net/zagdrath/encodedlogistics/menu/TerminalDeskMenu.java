/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// The Terminal Desk's green screen (client: CrtScreen): the Access Terminal's item sync (the network's items, hot and
// cold) for Work with Inventory, and requests - commands, screen queries, completions - answered by TerminalService.
// Open while the desk is there and the player near it.
public class TerminalDeskMenu extends AccessTerminalMenu {
    public TerminalDeskMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public TerminalDeskMenu(int containerId, Inventory inventory, BlockPos desk) {
        super(ModMenuTypes.TERMINAL_DESK.get(), containerId, inventory, desk, Direction.NORTH, DEFAULT_ROWS, 0);
    }

    public static void open(ServerPlayer player, BlockPos desk) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TerminalDeskMenu(id, inventory, desk),
                Component.translatable("block.encodedlogistics.terminal_desk")), buf -> buf.writeBlockPos(desk));
    }

    public @Nullable TerminalDeskBlockEntity desk() {
        return player.level().getBlockEntity(pos) instanceof TerminalDeskBlockEntity desk ? desk : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return desk() != null && player.isWithinBlockInteractionRange(pos, 4.0);
    }

    // A request from the screen: a command line, a screen's query or a completion.
    public void handle(ServerPlayer player, int kind, String text) {
        TerminalContext context = new TerminalContext(player.level().getServer(), network(), desk(), player);
        TerminalOutput output = TerminalService.handle(context, kind, text);
        PacketDistributor.sendToPlayer(player, new CrtResponsePayload(containerId, kind, TerminalService.topic(kind, text), output.lines(),
                Optional.ofNullable(output.message())));
    }
}
