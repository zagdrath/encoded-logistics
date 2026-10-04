/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.rack;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;

// The models the rack's renderer draws on top of its static frame, baked as standalone models: the three door leaves
// and every device's off, on and fault looks.
public final class RackModels {
    public static final Identifier DOOR_FRONT = EncodedLogistics.id("block/rack/rack_door_front");
    public static final Identifier DOOR_REAR_LEFT = EncodedLogistics.id("block/rack/rack_door_rear_left");
    public static final Identifier DOOR_REAR_RIGHT = EncodedLogistics.id("block/rack/rack_door_rear_right");

    private static final Map<Identifier, StandaloneModelKey<BlockStateModelPart>> KEYS = new HashMap<>();

    private RackModels() {}

    public static void register(ModelEvent.RegisterStandalone event) {
        register(event, DOOR_FRONT);
        register(event, DOOR_REAR_LEFT);
        register(event, DOOR_REAR_RIGHT);
        for (RackDeviceType type : RackDeviceType.all()) {
            for (RackDeviceInfo.Status status : RackDeviceInfo.Status.values()) {
                register(event, type.model(status));
            }
        }
    }

    private static void register(ModelEvent.RegisterStandalone event, Identifier model) {
        StandaloneModelKey<BlockStateModelPart> key = new StandaloneModelKey<>(model::toString);
        KEYS.put(model, key);
        event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(model));
    }

    public static @Nullable BlockStateModelPart get(Identifier model) {
        StandaloneModelKey<BlockStateModelPart> key = KEYS.get(model);
        return key != null ? Minecraft.getInstance().getModelManager().getStandaloneModel(key) : null;
    }
}
