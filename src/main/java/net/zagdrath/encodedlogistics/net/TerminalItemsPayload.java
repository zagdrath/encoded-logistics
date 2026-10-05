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
import net.zagdrath.encodedlogistics.storage.StorageKey;

// Server to client: the network's items for an open terminal - everything (full) or just what changed since the last
// update (a count of 0 means gone) - whether the terminal is online (or its network is failing over, which pauses it),
// what the network can craft (when that changed), the recalls the player is waiting on (every update), and its energy
// (the Energy tab). Entries are every resource type: items, fluids and pressurized gases (StorageKey).
public record TerminalItemsPayload(int containerId, boolean online, boolean failover, boolean full, List<Entry> entries, Optional<List<StorageKey>> craftables,
        List<Recall> recalls, Energy energy) implements CustomPacketPayload {
    public static final Type<TerminalItemsPayload> TYPE = new Type<>(EncodedLogistics.id("terminal_items"));

    // An item: how many there are, hot and cold; of them how many are on tape; for those, the ticks a recall would take
    // (-1: it can't, no drive) and whether the last one found hot storage full; and how many are shared in from another
    // segment (a Share route), from where.
    public record Entry(StorageKey key, long count, long cold, int eta, boolean hotFull, long shared, String sharedFrom) {
        public Entry(StorageKey key, long count) {
            this(key, count, 0, -1, false, 0, "");
        }

        public Entry(StorageKey key, long count, long cold, int eta, boolean hotFull) {
            this(key, count, cold, eta, hotFull, 0, "");
        }

        public Entry withShared(long shared, String from) {
            return new Entry(key, count, cold, eta, hotFull, shared, from);
        }

        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                StorageKey.STREAM_CODEC, Entry::key,
                ByteBufCodecs.VAR_LONG, Entry::count,
                ByteBufCodecs.VAR_LONG, Entry::cold,
                ByteBufCodecs.VAR_INT, Entry::eta,
                ByteBufCodecs.BOOL, Entry::hotFull,
                ByteBufCodecs.VAR_LONG, Entry::shared,
                ByteBufCodecs.STRING_UTF8, Entry::sharedFrom,
                Entry::new);
    }

    // A recall the player asked for (taking an item that was on tape), and how far along it is (0-100).
    public record Recall(StorageKey key, int percent) {
        static final StreamCodec<RegistryFriendlyByteBuf, Recall> STREAM_CODEC = StreamCodec.composite(
                StorageKey.STREAM_CODEC, Recall::key,
                ByteBufCodecs.VAR_INT, Recall::percent,
                Recall::new);
    }

    // The network's energy pool, as the Energy tab shows it: stored and capacity (FE), the part in Energy Storage
    // Drives, and the flow (FE/t used and received).
    public record Energy(long stored, long capacity, long driveStored, long driveCapacity, double usage, double generation) {
        public static final Energy NONE = new Energy(0, 0, 0, 0, 0, 0);

        static final StreamCodec<RegistryFriendlyByteBuf, Energy> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Energy::stored,
                ByteBufCodecs.VAR_LONG, Energy::capacity,
                ByteBufCodecs.VAR_LONG, Energy::driveStored,
                ByteBufCodecs.VAR_LONG, Energy::driveCapacity,
                ByteBufCodecs.DOUBLE, Energy::usage,
                ByteBufCodecs.DOUBLE, Energy::generation,
                Energy::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalItemsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalItemsPayload::containerId,
            ByteBufCodecs.BOOL, TerminalItemsPayload::online,
            ByteBufCodecs.BOOL, TerminalItemsPayload::failover,
            ByteBufCodecs.BOOL, TerminalItemsPayload::full,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), TerminalItemsPayload::entries,
            ByteBufCodecs.optional(StorageKey.STREAM_CODEC.apply(ByteBufCodecs.list())), TerminalItemsPayload::craftables,
            Recall.STREAM_CODEC.apply(ByteBufCodecs.list()), TerminalItemsPayload::recalls,
            Energy.STREAM_CODEC, TerminalItemsPayload::energy,
            TerminalItemsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(TerminalItemsPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof AccessTerminalMenu menu && menu.containerId == payload.containerId()) {
            menu.applyUpdate(payload.online(), payload.failover(), payload.full(), payload.entries(), payload.craftables().orElse(null), payload.recalls(),
                    payload.energy());
        }
    }
}
