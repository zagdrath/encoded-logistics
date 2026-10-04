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
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.elcl.exec.ElclSetup;
import net.zagdrath.encodedlogistics.elcl.job.JobManager;
import net.zagdrath.encodedlogistics.elcl.job.Schedules;
import net.zagdrath.encodedlogistics.elcl.job.StoredJobService;
import net.zagdrath.encodedlogistics.elcl.job.Triggers;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.sync.FolderSync;
import net.zagdrath.encodedlogistics.gametest.EncodedLogisticsGameTests;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.SchedulerStructures;
import net.zagdrath.encodedlogistics.net.ModNetwork;
import net.zagdrath.encodedlogistics.rack.FirewallEvents;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModCapabilities;
import net.zagdrath.encodedlogistics.registry.ModCreativeTabs;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.registry.ModRecipeSerializers;
import net.zagdrath.encodedlogistics.registry.ModRecipeTypes;
import net.zagdrath.encodedlogistics.registry.ModSounds;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(EncodedLogistics.MODID)
public class EncodedLogistics {
    public static final String MODID = "encodedlogistics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EncodedLogistics(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ModBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModCapabilities.register(modEventBus);
        ModNetwork.register(modEventBus);
        EncodedLogisticsGameTests.register(modEventBus);

        modEventBus.addListener(EncodedLogistics::registerTicketControllers);
        NeoForge.EVENT_BUS.addListener(EncodedLogistics::onLevelTick);
        NeoForge.EVENT_BUS.addListener(EncodedLogistics::onServerTick);
        NeoForge.EVENT_BUS.addListener(EncodedLogistics::onDatapackSync);
        FirewallEvents.register();
        ElclSetup.init();

        modContainer.registerConfig(localConfigType(), Config.SPEC);
    }

    // FML 12.0.8 (NeoForge 26.3.0.37-beta) renamed COMMON to LOCAL. Looked up by name so the mod runs on both sides of
    // the rename while 26.3 is in beta.
    private static ModConfig.Type localConfigType() {
        for (ModConfig.Type type : ModConfig.Type.values()) {
            if (type.name().equals("LOCAL")) {
                return type;
            }
        }
        return ModConfig.Type.valueOf("COMMON");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    // Linked Network Bridges may keep their chunks loaded (bridgeChunkLoading).
    private static void registerTicketControllers(RegisterTicketControllersEvent event) {
        event.register(NetworkBridgeBlockEntity.CHUNKS);
    }

    // Clients get the lithography recipes (the press's slots and JEI need them).
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(ModRecipeTypes.LITHOGRAPHY.get());
    }

    // ELCL jobs run their budgets once per server tick, after the levels.
    private static void onServerTick(ServerTickEvent.Post event) {
        FolderSync.tick(event.getServer());
        Schedules.tick(event.getServer());
        Triggers.tick(event.getServer());
        if (ElclServices.jobs() instanceof StoredJobService jobs) {
            jobs.tick(event.getServer());
        }
        JobManager.of(event.getServer()).tick(event.getServer());
    }

    // Scheduler and controller structures revalidate (and controllers tick) once per level tick.
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SchedulerStructures.get(level).tick(level);
            ControllerStructures.get(level).tick(level);
        }
    }
}
