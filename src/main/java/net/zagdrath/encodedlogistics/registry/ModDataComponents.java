/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.storage.DriveStats;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE,
            EncodedLogistics.MODID);

    // The block a Cable Facade copies the look of; a facade without one is blank.
    public static final Supplier<DataComponentType<BlockState>> FACADE_TARGET = DATA_COMPONENTS.registerComponentType("facade_target",
            builder -> builder.persistent(BlockState.CODEC).networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)));

    // A Storage Drive's id: its contents live in DriveStorage under it. Given when the drive first goes into a Drive Bay.
    public static final Supplier<DataComponentType<UUID>> DRIVE_ID = DATA_COMPONENTS.registerComponentType("drive_id",
            builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    // What a Storage Drive holds, cached on the item for its tooltip and the Drive Bay.
    public static final Supplier<DataComponentType<DriveStats>> DRIVE_STATS = DATA_COMPONENTS.registerComponentType("drive_stats",
            builder -> builder.persistent(DriveStats.CODEC).networkSynchronized(DriveStats.STREAM_CODEC));

    // An Encoded Schematic's recipe (crafting) or inputs and outputs (processing), written by the Schematic Encoder.
    public static final Supplier<DataComponentType<Schematic>> SCHEMATIC = DATA_COMPONENTS.registerComponentType("schematic",
            builder -> builder.persistent(Schematic.CODEC).networkSynchronized(Schematic.STREAM_CODEC));

    private ModDataComponents() {}
}
