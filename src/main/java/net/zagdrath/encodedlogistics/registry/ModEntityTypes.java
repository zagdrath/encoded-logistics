/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.entity.SeatEntity;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, EncodedLogistics.MODID);

    // What a player sits on in a Swivel Chair (saved like any rideable entity - one left empty goes on its next tick).
    public static final Supplier<EntityType<SeatEntity>> SEAT = ENTITY_TYPES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.01F, 0.01F).noSummon()
                    .clientTrackingRange(8).build(ResourceKey.create(Registries.ENTITY_TYPE, EncodedLogistics.id("seat"))));

    private ModEntityTypes() {}
}
