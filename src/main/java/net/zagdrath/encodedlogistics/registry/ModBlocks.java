/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.CapacitorBankBlock;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;
import net.zagdrath.encodedlogistics.block.PowerInletBlock;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EncodedLogistics.MODID);

    // TODO: the recipes (data/encodedlogistics/recipe/) are placeholders until the mod's own materials exist.
    public static final DeferredBlock<NetworkControllerBlock> NETWORK_CONTROLLER = BLOCKS.registerBlock("network_controller",
            NetworkControllerBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(NetworkControllerBlock::lightLevel));

    public static final DeferredBlock<PowerInletBlock> POWER_INLET = BLOCKS.registerBlock("power_inlet", PowerInletBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL));

    public static final DeferredBlock<CapacitorBankBlock> CAPACITOR_BANK = BLOCKS.registerBlock("capacitor_bank", CapacitorBankBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL));

    public static final DeferredBlock<SegmentIsolatorBlock> SEGMENT_ISOLATOR = BLOCKS.registerBlock("segment_isolator", SegmentIsolatorBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion());

    // Network, Dense Network and Fiber Cables in every colour: network_cable, white_network_cable, ...,
    // dense_network_cable, ..., fiber_cable, white_fiber_cable, .... Their shape depends on their attachments (block
    // entity), so it isn't cached per state.
    private static final Map<CableTier, Map<CableColor, DeferredBlock<NetworkCableBlock>>> CABLES = new EnumMap<>(CableTier.class);

    static {
        for (CableTier tier : CableTier.values()) {
            Map<CableColor, DeferredBlock<NetworkCableBlock>> colours = new EnumMap<>(CableColor.class);
            for (CableColor color : CableColor.values()) {
                colours.put(color, BLOCKS.registerBlock(color.prefix() + tier.baseName(), p -> new NetworkCableBlock(p, tier, color),
                        p -> p.mapColor(MapColor.METAL).strength(0.5F, 1.0F).sound(SoundType.METAL).noOcclusion().dynamicShape()
                                .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false)));
            }
            CABLES.put(tier, colours);
        }
    }

    private ModBlocks() {}

    public static DeferredBlock<NetworkCableBlock> cable(CableTier tier, CableColor color) {
        return CABLES.get(tier).get(color);
    }

    // Every cable: Network Cables first, then Dense, then Fiber, each tier neutral then the dyes in CableColor order.
    public static List<DeferredBlock<NetworkCableBlock>> allCables() {
        List<DeferredBlock<NetworkCableBlock>> cables = new ArrayList<>();
        CABLES.values().forEach(colours -> cables.addAll(colours.values()));
        return cables;
    }
}
