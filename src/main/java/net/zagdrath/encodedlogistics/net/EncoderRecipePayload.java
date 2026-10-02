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
import net.zagdrath.encodedlogistics.menu.SchematicEncoderMenu;

// Client to server: JEI moved a recipe into an open Schematic Encoder - crafting (the nine grid slots, row by row) or
// processing (up to nine inputs and three outputs, with amounts). Only ghost items are set; nothing is taken.
public record EncoderRecipePayload(int containerId, boolean crafting, List<ItemStack> inputs, List<ItemStack> outputs) implements CustomPacketPayload {
    public static final Type<EncoderRecipePayload> TYPE = new Type<>(EncodedLogistics.id("encoder_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncoderRecipePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EncoderRecipePayload::containerId,
            ByteBufCodecs.BOOL, EncoderRecipePayload::crafting,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(9)), EncoderRecipePayload::inputs,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(3)), EncoderRecipePayload::outputs,
            EncoderRecipePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(EncoderRecipePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof SchematicEncoderMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.setRecipe(payload.crafting(), payload.inputs(), payload.outputs());
        }
    }
}
