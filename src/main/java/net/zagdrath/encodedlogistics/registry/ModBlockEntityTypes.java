/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;

public final class ModBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,
            EncodedLogistics.MODID);

    public static final Supplier<BlockEntityType<NetworkControllerBlockEntity>> NETWORK_CONTROLLER = BLOCK_ENTITY_TYPES.register(
            "network_controller", () -> new BlockEntityType<>(NetworkControllerBlockEntity::new, ModBlocks.NETWORK_CONTROLLER.get()));

    private ModBlockEntityTypes() {}
}
