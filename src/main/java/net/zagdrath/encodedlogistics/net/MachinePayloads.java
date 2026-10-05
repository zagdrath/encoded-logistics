/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;

// The Midrange peripherals' green screens and their menus: a field typed on the screen (the Line Printer's report and
// file), and back from the server the message line, and the lines and numbers the screen shows (the printer's preview,
// pages and paper needed).
public final class MachinePayloads {
    private MachinePayloads() {}

    // Client to server: a field's value.
    public record Text(int containerId, int key, String text) implements CustomPacketPayload {
        public static final Type<Text> TYPE = new Type<>(EncodedLogistics.id("machine_text"));
        public static final int MAX_TEXT = 64;

        public static final StreamCodec<RegistryFriendlyByteBuf, Text> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Text::containerId,
                ByteBufCodecs.VAR_INT, Text::key,
                ByteBufCodecs.stringUtf8(MAX_TEXT), Text::text,
                Text::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Text payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof PeripheralMenu menu
                    && menu.containerId == payload.containerId() && menu.stillValid(player)) {
                menu.setText(payload.key(), payload.text());
            }
        }
    }

    // Server to client: the message line (empty: none), and the screen's lines and numbers.
    public record Info(int containerId, Component message, List<String> lines, List<Integer> numbers) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(EncodedLogistics.id("machine_info"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Info::containerId,
                ComponentSerialization.STREAM_CODEC, Info::message,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), Info::lines,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)), Info::numbers,
                Info::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Info payload, IPayloadContext context) {
            if (context.player().containerMenu instanceof PeripheralMenu menu && menu.containerId == payload.containerId()) {
                menu.receive(payload);
            }
        }
    }
}
