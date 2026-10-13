/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import org.jspecify.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.BlockPanelMenu;

// A block panel's traffic (BlockPanelMenu): its state to the client, the player's actions to the server.
public final class BlockPanelPayloads {
    private BlockPanelPayloads() {}

    // Server to client: the panel's state (PanelHost#writePanel).
    public record Data(int containerId, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Data> TYPE = new Type<>(EncodedLogistics.id("block_panel"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::containerId,
                ByteBufCodecs.TRUSTED_COMPOUND_TAG, Data::data,
                Data::new);

        // Client side: the last state received.
        private static volatile @Nullable Data latest;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static @Nullable CompoundTag forMenu(int containerId) {
            Data last = latest;
            return last != null && last.containerId() == containerId ? last.data() : null;
        }

        static void handle(Data payload, IPayloadContext context) {
            latest = payload;
        }
    }

    // Client to server: an action on the open panel.
    public record Action(int containerId, int action, int value, String text) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(EncodedLogistics.id("block_panel_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::containerId,
                ByteBufCodecs.VAR_INT, Action::action,
                ByteBufCodecs.INT, Action::value,
                ByteBufCodecs.stringUtf8(64), Action::text,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Action payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof BlockPanelMenu menu
                    && menu.containerId == payload.containerId() && menu.stillValid(player)) {
                menu.handleAction(player, payload.action(), payload.value(), payload.text());
            }
        }
    }
}
