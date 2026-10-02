/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

// Everything the Network screen shows, as the server last saw it.
public record NetworkSnapshot(NetworkStatus status, long stored, long capacity, double usage, double generation,
        int lanesUsed, int laneCapacity, int sizeX, int sizeY, int sizeZ, int blocks, List<DeviceEntry> devices) {
    public static final NetworkSnapshot EMPTY = new NetworkSnapshot(NetworkStatus.NO_POWER, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, List.of());

    // One type of device on the network (grouped by item): how many, their total drain, and how many of them are
    // missing a lane. unpowered: the whole network is offline.
    public record DeviceEntry(Identifier item, int count, double drain, int missingLane, boolean unpowered) {
        public boolean hasError() {
            return missingLane > 0 || unpowered;
        }

        static final StreamCodec<RegistryFriendlyByteBuf, DeviceEntry> STREAM_CODEC = StreamCodec.of(
                (buf, entry) -> {
                    buf.writeIdentifier(entry.item);
                    buf.writeVarInt(entry.count);
                    buf.writeDouble(entry.drain);
                    buf.writeVarInt(entry.missingLane);
                    buf.writeBoolean(entry.unpowered);
                },
                buf -> new DeviceEntry(buf.readIdentifier(), buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readBoolean()));
    }

    public boolean single() {
        return blocks == 1;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, snapshot) -> {
                buf.writeVarInt(snapshot.status.ordinal());
                buf.writeVarLong(snapshot.stored);
                buf.writeVarLong(snapshot.capacity);
                buf.writeDouble(snapshot.usage);
                buf.writeDouble(snapshot.generation);
                buf.writeVarInt(snapshot.lanesUsed);
                buf.writeVarInt(snapshot.laneCapacity);
                buf.writeVarInt(snapshot.sizeX);
                buf.writeVarInt(snapshot.sizeY);
                buf.writeVarInt(snapshot.sizeZ);
                buf.writeVarInt(snapshot.blocks);
                buf.writeVarInt(snapshot.devices.size());
                snapshot.devices.forEach(entry -> DeviceEntry.STREAM_CODEC.encode(buf, entry));
            },
            buf -> {
                NetworkStatus status = NetworkStatus.byId(buf.readVarInt());
                long stored = buf.readVarLong();
                long capacity = buf.readVarLong();
                double usage = buf.readDouble();
                double generation = buf.readDouble();
                int used = buf.readVarInt();
                int laneCapacity = buf.readVarInt();
                int sizeX = buf.readVarInt();
                int sizeY = buf.readVarInt();
                int sizeZ = buf.readVarInt();
                int blocks = buf.readVarInt();
                int count = buf.readVarInt();
                List<DeviceEntry> devices = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    devices.add(DeviceEntry.STREAM_CODEC.decode(buf));
                }
                return new NetworkSnapshot(status, stored, capacity, usage, generation, used, laneCapacity, sizeX, sizeY, sizeZ,
                        blocks, devices);
            });
}
