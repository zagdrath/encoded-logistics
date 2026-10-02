/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.function.Supplier;
import java.util.stream.Stream;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.CapacitorBankBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.FabricatorBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.LithographyPressBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;

public final class ModBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,
            EncodedLogistics.MODID);

    public static final Supplier<BlockEntityType<NetworkControllerBlockEntity>> NETWORK_CONTROLLER = BLOCK_ENTITY_TYPES.register(
            "network_controller", () -> new BlockEntityType<>(NetworkControllerBlockEntity::new, ModBlocks.NETWORK_CONTROLLER.get()));

    // One type for every cable, of every tier and colour, and for part hosts.
    public static final Supplier<BlockEntityType<CableBlockEntity>> CABLE = BLOCK_ENTITY_TYPES.register("cable",
            () -> new BlockEntityType<>(CableBlockEntity::new, Stream.concat(ModBlocks.allCables().stream().map(cable -> (Block) cable.get()),
                    Stream.of(ModBlocks.PART_HOST.get())).toArray(Block[]::new)));

    public static final Supplier<BlockEntityType<PowerInletBlockEntity>> POWER_INLET = BLOCK_ENTITY_TYPES.register(
            "power_inlet", () -> new BlockEntityType<>(PowerInletBlockEntity::new, ModBlocks.POWER_INLET.get()));

    public static final Supplier<BlockEntityType<CapacitorBankBlockEntity>> CAPACITOR_BANK = BLOCK_ENTITY_TYPES.register(
            "capacitor_bank", () -> new BlockEntityType<>(CapacitorBankBlockEntity::new, ModBlocks.CAPACITOR_BANK.get()));

    public static final Supplier<BlockEntityType<LithographyPressBlockEntity>> LITHOGRAPHY_PRESS = BLOCK_ENTITY_TYPES.register(
            "lithography_press", () -> new BlockEntityType<>(LithographyPressBlockEntity::new, ModBlocks.LITHOGRAPHY_PRESS.get()));

    public static final Supplier<BlockEntityType<DriveBayBlockEntity>> DRIVE_BAY = BLOCK_ENTITY_TYPES.register(
            "drive_bay", () -> new BlockEntityType<>(DriveBayBlockEntity::new, ModBlocks.DRIVE_BAY.get()));

    public static final Supplier<BlockEntityType<FabricatorBlockEntity>> FABRICATOR = BLOCK_ENTITY_TYPES.register(
            "fabricator", () -> new BlockEntityType<>(FabricatorBlockEntity::new, ModBlocks.FABRICATOR.get()));

    public static final Supplier<BlockEntityType<GatewayBlockEntity>> GATEWAY = BLOCK_ENTITY_TYPES.register(
            "gateway", () -> new BlockEntityType<>(GatewayBlockEntity::new, ModBlocks.GATEWAY.get()));

    public static final Supplier<BlockEntityType<SchedulerCoreBlockEntity>> SCHEDULER_CORE = BLOCK_ENTITY_TYPES.register(
            "scheduler_core", () -> new BlockEntityType<>(SchedulerCoreBlockEntity::new, ModBlocks.SCHEDULER_CORE.get()));

    public static final Supplier<BlockEntityType<RelayAntennaBlockEntity>> RELAY_ANTENNA = BLOCK_ENTITY_TYPES.register(
            "relay_antenna", () -> new BlockEntityType<>(RelayAntennaBlockEntity::new, ModBlocks.RELAY_ANTENNA.get()));

    public static final Supplier<BlockEntityType<NetworkBridgeBlockEntity>> NETWORK_BRIDGE = BLOCK_ENTITY_TYPES.register(
            "network_bridge", () -> new BlockEntityType<>(NetworkBridgeBlockEntity::new, ModBlocks.NETWORK_BRIDGE.get()));

    private ModBlockEntityTypes() {}
}
