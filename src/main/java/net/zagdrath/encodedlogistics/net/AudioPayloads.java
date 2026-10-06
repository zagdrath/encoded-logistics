/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.signal.AudioFiles;
import net.zagdrath.encodedlogistics.signal.SignalClientHooks;
import net.zagdrath.encodedlogistics.signal.SpeakerBlockEntity;

// A Speaker's audio files reaching the players who hear it (signals handoff 6): a client that hasn't got a file asks for
// it by hash (Request); the server sends it in chunks (Chunk), CHUNKS_PER_TICK a tick for each player, one file at a
// time; the client keeps it by hash. The client reports on each play (Report): a URL's length, or why it failed.
public final class AudioPayloads {
    private static final int CHUNK = 24 * 1024, CHUNKS_PER_TICK = 4, QUEUED = 4;

    // Server side: each player's files still to send, the first one partly sent.
    private record Transfer(String hash, byte[] bytes, int[] next) {}

    private static final Map<UUID, Deque<Transfer>> SENDING = new HashMap<>();

    private AudioPayloads() {}

    // Sends the next chunks to each player (once a server tick).
    public static void tick(MinecraftServer server) {
        synchronized (SENDING) {
            SENDING.entrySet().removeIf(entry -> {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                Deque<Transfer> queue = entry.getValue();
                if (player == null) {
                    return true;
                }
                for (int sent = 0; sent < CHUNKS_PER_TICK && !queue.isEmpty(); sent++) {
                    Transfer transfer = queue.peekFirst();
                    int total = Math.max(1, (transfer.bytes().length + CHUNK - 1) / CHUNK), index = transfer.next()[0]++;
                    int from = index * CHUNK, to = Math.min(transfer.bytes().length, from + CHUNK);
                    byte[] part = new byte[Math.max(0, to - from)];
                    System.arraycopy(transfer.bytes(), from, part, 0, part.length);
                    PacketDistributor.sendToPlayer(player, new Chunk(transfer.hash(), index, total, part));
                    if (index + 1 >= total) {
                        queue.pollFirst();
                    }
                }
                return queue.isEmpty();
            });
        }
    }

    public record Request(String hash) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(EncodedLogistics.id("audio_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Request::hash, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        // Only a file a Speaker played (AudioFiles knows its hash), once per player while it's on its way.
        static void handle(Request request, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            synchronized (SENDING) {
                Deque<Transfer> queue = SENDING.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
                if (queue.size() >= QUEUED || queue.stream().anyMatch(transfer -> transfer.hash().equals(request.hash()))) {
                    return;
                }
                byte[] bytes = AudioFiles.bytes(request.hash());
                if (bytes == null) {
                    // Gone or changed: an empty answer, so the client stops waiting.
                    PacketDistributor.sendToPlayer(player, new Chunk(request.hash(), 0, 0, new byte[0]));
                    return;
                }
                queue.addLast(new Transfer(request.hash(), bytes, new int[1]));
            }
        }
    }

    // total 0: the server hasn't got the file.
    public record Chunk(String hash, int index, int total, byte[] bytes) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(EncodedLogistics.id("audio_chunk"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Chunk> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Chunk::hash,
                ByteBufCodecs.VAR_INT, Chunk::index,
                ByteBufCodecs.VAR_INT, Chunk::total,
                ByteBufCodecs.byteArray(CHUNK), Chunk::bytes,
                Chunk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Chunk chunk, IPayloadContext context) {
            SignalClientHooks.get().audioChunk(chunk.hash(), chunk.index(), chunk.total(), chunk.bytes());
        }
    }

    // problem: why it couldn't play ("" if it could); seconds: its length (a URL's, once decoded).
    public record Report(BlockPos pos, int play, float seconds, String problem) implements CustomPacketPayload {
        public static final Type<Report> TYPE = new Type<>(EncodedLogistics.id("audio_report"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Report> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Report::pos,
                ByteBufCodecs.VAR_INT, Report::play,
                ByteBufCodecs.FLOAT, Report::seconds,
                ByteBufCodecs.stringUtf8(256), Report::problem,
                Report::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Report report, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && player.level().isLoaded(report.pos())
                    && player.distanceToSqr(Vec3.atCenterOf(report.pos())) < 300 * 300
                    && player.level().getBlockEntity(report.pos()) instanceof SpeakerBlockEntity speaker) {
                speaker.report(player, report.play(), report.seconds(), report.problem());
            }
        }
    }
}
