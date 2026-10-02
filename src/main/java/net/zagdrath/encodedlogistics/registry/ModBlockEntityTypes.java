/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.CapacitorBankBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;

public final class ModBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,
            EncodedLogistics.MODID);

    public static final Supplier<BlockEntityType<NetworkControllerBlockEntity>> NETWORK_CONTROLLER = BLOCK_ENTITY_TYPES.register(
            "network_controller", () -> new BlockEntityType<>(NetworkControllerBlockEntity::new, ModBlocks.NETWORK_CONTROLLER.get()));

    // One type for every cable, of every tier and colour.
    public static final Supplier<BlockEntityType<CableBlockEntity>> CABLE = BLOCK_ENTITY_TYPES.register("cable",
            () -> new BlockEntityType<>(CableBlockEntity::new, ModBlocks.allCables().stream().map(cable -> (Block) cable.get()).toArray(Block[]::new)));

    public static final Supplier<BlockEntityType<PowerInletBlockEntity>> POWER_INLET = BLOCK_ENTITY_TYPES.register(
            "power_inlet", () -> new BlockEntityType<>(PowerInletBlockEntity::new, ModBlocks.POWER_INLET.get()));

    public static final Supplier<BlockEntityType<CapacitorBankBlockEntity>> CAPACITOR_BANK = BLOCK_ENTITY_TYPES.register(
            "capacitor_bank", () -> new BlockEntityType<>(CapacitorBankBlockEntity::new, ModBlocks.CAPACITOR_BANK.get()));

    private ModBlockEntityTypes() {}
}
