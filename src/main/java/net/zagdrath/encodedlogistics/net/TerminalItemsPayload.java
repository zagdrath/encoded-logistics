/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// Server to client: the network's items for an open terminal - everything (full) or just what changed since the last
// update (a count of 0 means gone) - whether the terminal is online, and what the network can craft (when that changed).
public record TerminalItemsPayload(int containerId, boolean online, boolean full, List<Entry> entries, Optional<List<ItemKey>> craftables)
        implements CustomPacketPayload {
    public static final Type<TerminalItemsPayload> TYPE = new Type<>(EncodedLogistics.id("terminal_items"));

    public record Entry(ItemKey key, long count) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemKey.STREAM_CODEC, Entry::key,
                ByteBufCodecs.VAR_LONG, Entry::count,
                Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalItemsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalItemsPayload::containerId,
            ByteBufCodecs.BOOL, TerminalItemsPayload::online,
            ByteBufCodecs.BOOL, TerminalItemsPayload::full,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), TerminalItemsPayload::entries,
            ByteBufCodecs.optional(ItemKey.STREAM_CODEC.apply(ByteBufCodecs.list())), TerminalItemsPayload::craftables,
            TerminalItemsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(TerminalItemsPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof AccessTerminalMenu menu && menu.containerId == payload.containerId()) {
            menu.applyUpdate(payload.online(), payload.full(), payload.entries(), payload.craftables().orElse(null));
        }
    }
}
