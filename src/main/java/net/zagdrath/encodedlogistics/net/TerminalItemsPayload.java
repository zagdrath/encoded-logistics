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
// update (a count of 0 means gone) - whether the terminal is online (or its network is failing over, which pauses it),
// what the network can craft (when that changed), and the recalls the player is waiting on (every update).
public record TerminalItemsPayload(int containerId, boolean online, boolean failover, boolean full, List<Entry> entries, Optional<List<ItemKey>> craftables,
        List<Recall> recalls) implements CustomPacketPayload {
    public static final Type<TerminalItemsPayload> TYPE = new Type<>(EncodedLogistics.id("terminal_items"));

    // An item: how many there are, hot and cold; of them how many are on tape; for those, the ticks a recall would take
    // (-1: it can't, no drive) and whether the last one found hot storage full.
    public record Entry(ItemKey key, long count, long cold, int eta, boolean hotFull) {
        public Entry(ItemKey key, long count) {
            this(key, count, 0, -1, false);
        }

        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemKey.STREAM_CODEC, Entry::key,
                ByteBufCodecs.VAR_LONG, Entry::count,
                ByteBufCodecs.VAR_LONG, Entry::cold,
                ByteBufCodecs.VAR_INT, Entry::eta,
                ByteBufCodecs.BOOL, Entry::hotFull,
                Entry::new);
    }

    // A recall the player asked for (taking an item that was on tape), and how far along it is (0-100).
    public record Recall(ItemKey key, int percent) {
        static final StreamCodec<RegistryFriendlyByteBuf, Recall> STREAM_CODEC = StreamCodec.composite(
                ItemKey.STREAM_CODEC, Recall::key,
                ByteBufCodecs.VAR_INT, Recall::percent,
                Recall::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalItemsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalItemsPayload::containerId,
            ByteBufCodecs.BOOL, TerminalItemsPayload::online,
            ByteBufCodecs.BOOL, TerminalItemsPayload::failover,
            ByteBufCodecs.BOOL, TerminalItemsPayload::full,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), TerminalItemsPayload::entries,
            ByteBufCodecs.optional(ItemKey.STREAM_CODEC.apply(ByteBufCodecs.list())), TerminalItemsPayload::craftables,
            Recall.STREAM_CODEC.apply(ByteBufCodecs.list()), TerminalItemsPayload::recalls,
            TerminalItemsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(TerminalItemsPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof AccessTerminalMenu menu && menu.containerId == payload.containerId()) {
            menu.applyUpdate(payload.online(), payload.failover(), payload.full(), payload.entries(), payload.craftables().orElse(null), payload.recalls());
        }
    }
}
