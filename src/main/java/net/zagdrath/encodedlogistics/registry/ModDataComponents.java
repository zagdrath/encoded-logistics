/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.HandheldLinkState;
import net.zagdrath.encodedlogistics.item.LinkAddress;
import net.zagdrath.encodedlogistics.midrange.DisketteData;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex;
import net.zagdrath.encodedlogistics.storage.DriveStats;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE,
            EncodedLogistics.MODID);

    // The block a Cable Facade copies the look of; a facade without one is blank.
    public static final Supplier<DataComponentType<BlockState>> FACADE_TARGET = DATA_COMPONENTS.registerComponentType("facade_target",
            builder -> builder.persistent(BlockState.CODEC).networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)));

    // The name scripts know a Terminal Desk or Control Interface by (ELDESK01, CTLIF01), kept on the item when it's broken.
    public static final Supplier<DataComponentType<String>> DEVICE_NAME = DATA_COMPONENTS.registerComponentType("device_name",
            builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // A Storage Drive's id: its contents live in DriveStorage under it. Given when the drive first goes into a Drive Bay.
    public static final Supplier<DataComponentType<UUID>> DRIVE_ID = DATA_COMPONENTS.registerComponentType("drive_id",
            builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    // What a Storage Drive holds, cached on the item for its tooltip and the Drive Bay.
    public static final Supplier<DataComponentType<DriveStats>> DRIVE_STATS = DATA_COMPONENTS.registerComponentType("drive_stats",
            builder -> builder.persistent(DriveStats.CODEC).networkSynchronized(DriveStats.STREAM_CODEC));

    // An Encoded Schematic's recipe (crafting) or inputs and outputs (processing), written by the Schematic Encoder.
    public static final Supplier<DataComponentType<Schematic>> SCHEMATIC = DATA_COMPONENTS.registerComponentType("schematic",
            builder -> builder.persistent(Schematic.CODEC).networkSynchronized(Schematic.STREAM_CODEC));

    // A punched Punch Card's crafting recipe (Keypunch); a blank card has none.
    public static final Supplier<DataComponentType<Schematic>> PUNCHED_RECIPE = DATA_COMPONENTS.registerComponentType("punched_recipe",
            builder -> builder.persistent(Schematic.CODEC).networkSynchronized(Schematic.STREAM_CODEC));

    // What's written on an 8" Diskette (Card Reader, SAVLIB); a blank diskette has none.
    public static final Supplier<DataComponentType<DisketteData>> DISKETTE_RECIPES = DATA_COMPONENTS.registerComponentType("diskette_recipes",
            builder -> builder.persistent(DisketteData.CODEC).networkSynchronized(DisketteData.STREAM_CODEC));

    // The diskettes in a Diskette Magazine (up to 4).
    public static final Supplier<DataComponentType<ItemContainerContents>> MAGAZINE_CONTENTS = DATA_COMPONENTS.registerComponentType("magazine_contents",
            builder -> builder.persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC));

    // A written Link Card's address: the Network Bridge or Point-to-Point Link endpoint it was sneak-used on.
    public static final Supplier<DataComponentType<LinkAddress>> LINK_ADDRESS = DATA_COMPONENTS.registerComponentType("link_address",
            builder -> builder.persistent(LinkAddress.CODEC).networkSynchronized(LinkAddress.STREAM_CODEC));

    // The network a Handheld Terminal is linked to.
    public static final Supplier<DataComponentType<NetworkIndex.NetworkRef>> HANDHELD_NETWORK = DATA_COMPONENTS.registerComponentType(
            "handheld_network", builder -> builder.persistent(NetworkIndex.NetworkRef.CODEC).networkSynchronized(NetworkIndex.NetworkRef.STREAM_CODEC));

    // Whether a Handheld Terminal is in range of its network (its icon); kept up to date while it's in an inventory.
    public static final Supplier<DataComponentType<HandheldLinkState>> HANDHELD_LINK_STATE = DATA_COMPONENTS.registerComponentType(
            "handheld_link_state", builder -> builder.persistent(HandheldLinkState.CODEC).networkSynchronized(HandheldLinkState.STREAM_CODEC));

    // The Wireless Controller a Handheld Terminal was linked to (its id): it works anywhere through it.
    public static final Supplier<DataComponentType<UUID>> WIRELESS_LINK = DATA_COMPONENTS.registerComponentType("wireless_link",
            builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    // FE in a Handheld Terminal's battery.
    public static final Supplier<DataComponentType<Integer>> ENERGY = DATA_COMPONENTS.registerComponentType("energy",
            builder -> builder.persistent(Codec.intRange(0, Integer.MAX_VALUE)).networkSynchronized(ByteBufCodecs.VAR_INT));

    // A rack device's settings while it's out of a rack (RackDeviceItem).
    public static final Supplier<DataComponentType<CustomData>> RACK_DEVICE_STATE = DATA_COMPONENTS.registerComponentType("rack_device_state",
            builder -> builder.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC));

    private ModDataComponents() {}
}
