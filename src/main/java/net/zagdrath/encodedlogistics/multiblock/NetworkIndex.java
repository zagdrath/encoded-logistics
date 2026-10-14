/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.RackScheduler;

// Which network every node on a controller's network is on, across all dimensions (a Network Bridge can carry a
// network into another one): kept with the overworld, rebuilt as networks are solved, never saved. Also every node of
// every network's last discovered graph, by where it is (graphNodes: which networks a change there can affect), the
// networks waiting to be worked out again with why (dirty), and a counter (generation) that goes up with any change to
// any network's topology, for the caches built from it.
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
    // Something changed since generation last went up (it goes up once a tick at most).
    boolean changed;
    // Every node of every structure's last discovered graph (controllers, conflicting and paired networks' included).
    final Map<NodePos, Set<NetworkRef>> graphNodes = new HashMap<>();
    // Networks to work out again, each with the first cause since it was last solved; everything goes up when every
    // network is.
    final Map<NetworkRef, TopologyCause> dirty = new HashMap<>();
    int everything;
    // Cached (ControllerStructures, "Caches"): racks in NetworkGraph.ORDER (sorted again only when racks changes:
    // racksVersion), the loaded ones' block entities, and each network's rack Scheduler pool head.
    int racksVersion;
    int sortedVersion = -1;
    List<NodePos> sortedRacks = List.of();
    long loadedKey = -1;
    List<RackBlockEntity> loadedRacks = List.of();
    long headsKey = -1;
    Map<NetworkRef, RackScheduler> heads = Map.of();
    final CacheStats stats = new CacheStats();

    public NetworkIndex() {}

    void addRacks(Iterable<NodePos> added) {
        boolean changed = false;
        for (NodePos pos : added) {
            changed |= racks.add(pos);
        }
        if (changed) {
            racksChanged();
        }
    }

    void removeRacks(Iterable<NodePos> gone) {
        boolean changed = false;
        for (NodePos pos : gone) {
            changed |= racks.remove(pos);
        }
        if (changed) {
            racksChanged();
        }
    }

    private void racksChanged() {
        racksVersion++;
        ControllerStructures.contentChanged();
    }

    static NetworkIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
