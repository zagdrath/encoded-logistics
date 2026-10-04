/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.network.NodePos;

// Which network every node on a controller's network is on, across all dimensions (a Network Bridge can carry a
// network into another one): kept with the overworld, rebuilt as networks are solved, never saved. Also a counter that
// goes up whenever a network changes anywhere, so every level's networks are solved again.
public final class NetworkIndex extends SavedData {
    // A network: its controller structure, by the dimension it's in and its id there.
    public record NetworkRef(ResourceKey<Level> dimension, long id) {
        public static final Codec<NetworkRef> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(NetworkRef::dimension),
                Codec.LONG.fieldOf("id").forGetter(NetworkRef::id))
                .apply(i, NetworkRef::new));

        public static final StreamCodec<ByteBuf, NetworkRef> STREAM_CODEC = StreamCodec.composite(
                ResourceKey.streamCodec(Registries.DIMENSION), NetworkRef::dimension,
                ByteBufCodecs.VAR_LONG, NetworkRef::id,
                NetworkRef::new);
    }

    static final SavedDataType<NetworkIndex> TYPE = new SavedDataType<>(EncodedLogistics.id("network_index"), NetworkIndex::new,
            MapCodec.unit(NetworkIndex::new).codec());

    final Map<NodePos, NetworkRef> members = new HashMap<>();
    // Every Server Rack on any network (their devices may serve another network than the rack's: a segment).
    final Set<NodePos> racks = new HashSet<>();
    int generation;

    public NetworkIndex() {}

    static NetworkIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
