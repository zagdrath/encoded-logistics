/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.zagdrath.encodedlogistics.gametest.EncodedLogisticsGameTests;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.net.ModNetwork;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModCapabilities;
import net.zagdrath.encodedlogistics.registry.ModCreativeTabs;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(EncodedLogistics.MODID)
public class EncodedLogistics {
    public static final String MODID = "encodedlogistics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EncodedLogistics(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModCapabilities.register(modEventBus);
        ModNetwork.register(modEventBus);
        EncodedLogisticsGameTests.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(EncodedLogistics::onLevelTick);

        modContainer.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    // Controller structures revalidate and tick once per level tick.
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ControllerStructures.get(level).tick(level);
        }
    }
}
