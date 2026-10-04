/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.model.CableParts;
import net.zagdrath.encodedlogistics.client.model.ControllerModel;
import net.zagdrath.encodedlogistics.client.model.FacadeTints;
import net.zagdrath.encodedlogistics.client.model.SchedulerModel;
import net.zagdrath.encodedlogistics.client.model.SchematicOutputModel;
import net.zagdrath.encodedlogistics.client.rack.RackHud;
import net.zagdrath.encodedlogistics.client.rack.RackModels;
import net.zagdrath.encodedlogistics.client.rack.RackRenderer;
import net.zagdrath.encodedlogistics.client.screen.AccessTerminalScreen;
import net.zagdrath.encodedlogistics.client.screen.CapacitorBankScreen;
import net.zagdrath.encodedlogistics.client.screen.CollectorPlaneScreen;
import net.zagdrath.encodedlogistics.client.screen.DeployerPlaneScreen;
import net.zagdrath.encodedlogistics.client.screen.DriveBayScreen;
import net.zagdrath.encodedlogistics.client.screen.FabricationTerminalScreen;
import net.zagdrath.encodedlogistics.client.screen.FabricatorScreen;
import net.zagdrath.encodedlogistics.client.screen.GatewayScreen;
import net.zagdrath.encodedlogistics.client.screen.HandheldTerminalScreen;
import net.zagdrath.encodedlogistics.client.screen.InventoryTapScreen;
import net.zagdrath.encodedlogistics.client.screen.LithographyPressScreen;
import net.zagdrath.encodedlogistics.client.screen.NetworkBridgeScreen;
import net.zagdrath.encodedlogistics.client.screen.NetworkControllerScreen;
import net.zagdrath.encodedlogistics.client.screen.PointToPointScreen;
import net.zagdrath.encodedlogistics.client.screen.PortScreen;
import net.zagdrath.encodedlogistics.client.screen.RackScreen;
import net.zagdrath.encodedlogistics.client.screen.RelayAntennaScreen;
import net.zagdrath.encodedlogistics.client.screen.SchedulerCoreScreen;
import net.zagdrath.encodedlogistics.client.screen.SchematicEncoderScreen;
import net.zagdrath.encodedlogistics.client.screen.TerminalLayout;
import net.zagdrath.encodedlogistics.client.screen.TerminalSettings;
import net.zagdrath.encodedlogistics.client.screen.ThresholdSensorScreen;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.recipe.LithographyRecipes;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.registry.ModRecipeTypes;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = EncodedLogistics.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = EncodedLogistics.MODID, value = Dist.CLIENT)
public class EncodedLogisticsClient {
    public EncodedLogisticsClient(ModContainer container) {
        // Config screen is accessed via Mods screen > Encoded Logistics > Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // The terminals' toolbar settings (encodedlogistics-client.toml).
        container.registerConfig(ModConfig.Type.CLIENT, TerminalSettings.SPEC);
        // A terminal's grid gets as many rows as fit the window when it opens.
        AccessTerminalMenu.clientRows = section -> TerminalLayout.load(AccessTerminalScreen.LAYOUT)
                .rowsFor(Minecraft.getInstance().getWindow().getGuiScaledHeight() - section);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.NETWORK_CONTROLLER.get(), NetworkControllerScreen::new);
        event.register(ModMenuTypes.CAPACITOR_BANK.get(), CapacitorBankScreen::new);
        event.register(ModMenuTypes.LITHOGRAPHY_PRESS.get(), LithographyPressScreen::new);
        event.register(ModMenuTypes.DRIVE_BAY.get(), DriveBayScreen::new);
        event.register(ModMenuTypes.ACCESS_TERMINAL.get(), AccessTerminalScreen::new);
        event.register(ModMenuTypes.FABRICATION_TERMINAL.get(), FabricationTerminalScreen::new);
        event.register(ModMenuTypes.PORT.get(), PortScreen::new);
        event.register(ModMenuTypes.INVENTORY_TAP.get(), InventoryTapScreen::new);
        event.register(ModMenuTypes.THRESHOLD_SENSOR.get(), ThresholdSensorScreen::new);
        event.register(ModMenuTypes.SCHEMATIC_ENCODER.get(), SchematicEncoderScreen::new);
        event.register(ModMenuTypes.FABRICATOR.get(), FabricatorScreen::new);
        event.register(ModMenuTypes.GATEWAY.get(), GatewayScreen::new);
        event.register(ModMenuTypes.SCHEDULER_CORE.get(), SchedulerCoreScreen::new);
        event.register(ModMenuTypes.RELAY_ANTENNA.get(), RelayAntennaScreen::new);
        event.register(ModMenuTypes.NETWORK_BRIDGE.get(), NetworkBridgeScreen::new);
        event.register(ModMenuTypes.HANDHELD_TERMINAL.get(), HandheldTerminalScreen::new);
        event.register(ModMenuTypes.POINT_TO_POINT_LINK.get(), PointToPointScreen::new);
        event.register(ModMenuTypes.COLLECTOR_PLANE.get(), CollectorPlaneScreen::new);
        event.register(ModMenuTypes.DEPLOYER_PLANE.get(), DeployerPlaneScreen::new);
        event.register(ModMenuTypes.SERVER_RACK.get(), RackScreen::new);
    }

    // The Network Controller's and the Scheduler's connected textures (see ControllerModel, SchedulerModel).
    @SubscribeEvent
    static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(ControllerModel.ID, ControllerModel.Unbaked.MAP_CODEC);
        event.registerModel(SchedulerModel.ID, SchedulerModel.Unbaked.MAP_CODEC);
    }

    // Encoded Schematics drawn as what they make while Shift is held (see SchematicOutputModel).
    @SubscribeEvent
    static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(SchematicOutputModel.ID, SchematicOutputModel.Unbaked.MAP_CODEC);
    }

    // Cable attachments: the parts cables are rebuilt from when they carry anchors or facades, the wrapped cable models,
    // and the facades' tints (see CableParts, CableModel, FacadeTints).
    @SubscribeEvent
    static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        CableParts.register(event);
        RackModels.register(event);
    }

    // The Server Rack: its doors and devices (RackRenderer), and the popup by the crosshair (RackHud).
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntityTypes.SERVER_RACK.get(), RackRenderer::new);
    }

    @SubscribeEvent
    static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, RackHud.LAYER, RackHud::render);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        RackHud.tick(event);
    }

    @SubscribeEvent
    static void onBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        RackHud.outline(event);
    }

    @SubscribeEvent
    static void wrapCableModels(ModelEvent.ModifyBakingResult event) {
        CableParts.wrap(event);
    }

    @SubscribeEvent
    static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        FacadeTints.register(event);
    }

    // The lithography recipes the server sent: the press's slots and JEI use them. Early, so they're in before JEI
    // reloads on the same event.
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRecipesReceived(RecipesReceivedEvent event) {
        LithographyRecipes.setClientRecipes(event.getRecipeMap().byType(ModRecipeTypes.LITHOGRAPHY.get()));
    }
}
