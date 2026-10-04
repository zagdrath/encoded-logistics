/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// Every system's data (elcl.store), saved with the overworld: one SystemData per network, keyed by the network's
// controller structure (its dimension and id, which the structure keeps for as long as it stands).
public final class ElclStore extends SavedData {
    static final SavedDataType<ElclStore> TYPE = new SavedDataType<>(EncodedLogistics.id("elcl_systems"), ElclStore::new,
            CompoundTag.CODEC.xmap(ElclStore::load, ElclStore::save));

    private final Map<NetworkRef, SystemData> systems = new HashMap<>();

    public ElclStore() {}

    public static ElclStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static SystemData of(ElclSystem system) {
        return get(system.server()).system(system.network());
    }

    public synchronized SystemData system(NetworkRef network) {
        SystemData data = systems.get(network);
        if (data == null) {
            data = new SystemData();
            data.onChange(this::setDirty);
            systems.put(network, data);
            setDirty();
        }
        return data;
    }

    public synchronized Map<NetworkRef, SystemData> systems() {
        return Map.copyOf(systems);
    }

    // A save and a load of one system, as a restart does them: its data replaced by what it saves as (the game tests'
    // way of checking what survives one). Whatever isn't saved (edit locks, compiled programs) is gone after it.
    public synchronized void reload(NetworkRef network) {
        system(network);
        SystemData data = load(save(this)).systems.get(network);
        data.onChange(this::setDirty);
        systems.put(network, data);
        setDirty();
    }

    private static CompoundTag save(ElclStore store) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        synchronized (store) {
            store.systems.forEach((network, data) -> {
                CompoundTag system = data.save();
                system.putString("dimension", network.dimension().identifier().toString());
                system.putLong("network", network.id());
                list.add(system);
            });
        }
        tag.put("systems", list);
        return tag;
    }

    private static ElclStore load(CompoundTag tag) {
        ElclStore store = new ElclStore();
        ListTag list = tag.getListOrEmpty("systems");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag system = list.getCompoundOrEmpty(i);
            Identifier dimension = Identifier.tryParse(system.getStringOr("dimension", ""));
            if (dimension == null) {
                continue;
            }
            NetworkRef network = new NetworkRef(ResourceKey.create(Registries.DIMENSION, dimension), system.getLongOr("network", 0));
            SystemData data = SystemData.load(system);
            data.onChange(store::setDirty);
            store.systems.put(network, data);
        }
        return store;
    }
}
