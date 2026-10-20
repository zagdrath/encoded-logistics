/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.FabricationTerminalMenu;

// Client to server: JEI moved a recipe into an open Fabrication Terminal - the options for each of the nine grid slots,
// and whether to start crafting what the grid is left without (Ctrl-click on JEI's "+").
public record TerminalRecipePayload(int containerId, List<List<ItemStack>> inputs, boolean craftMissing) implements CustomPacketPayload {
    public static final Type<TerminalRecipePayload> TYPE = new Type<>(EncodedLogistics.id("terminal_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalRecipePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalRecipePayload::containerId,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.apply(ByteBufCodecs.list(9)), TerminalRecipePayload::inputs,
            ByteBufCodecs.BOOL, TerminalRecipePayload::craftMissing,
            TerminalRecipePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(TerminalRecipePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof FabricationTerminalMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.fillGrid(payload.inputs());
            if (payload.craftMissing()) {
                menu.craftMissing(payload.inputs());
            }
        }
    }
}
