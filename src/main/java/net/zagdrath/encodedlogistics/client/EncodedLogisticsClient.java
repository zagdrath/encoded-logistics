/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.model.CableParts;
import net.zagdrath.encodedlogistics.client.model.ControllerModel;
import net.zagdrath.encodedlogistics.client.model.FacadeTints;
import net.zagdrath.encodedlogistics.client.screen.CapacitorBankScreen;
import net.zagdrath.encodedlogistics.client.screen.NetworkControllerScreen;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = EncodedLogistics.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = EncodedLogistics.MODID, value = Dist.CLIENT)
public class EncodedLogisticsClient {
    public EncodedLogisticsClient(ModContainer container) {
        // Config screen is accessed via Mods screen > Encoded Logistics > Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.NETWORK_CONTROLLER.get(), NetworkControllerScreen::new);
        event.register(ModMenuTypes.CAPACITOR_BANK.get(), CapacitorBankScreen::new);
    }

    // The Network Controller's connected textures (see ControllerModel).
    @SubscribeEvent
    static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(ControllerModel.ID, ControllerModel.Unbaked.MAP_CODEC);
    }

    // Cable attachments: the parts cables are rebuilt from when they carry anchors or facades, the wrapped cable models,
    // and the facades' tints (see CableParts, CableModel, FacadeTints).
    @SubscribeEvent
    static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        CableParts.register(event);
    }

    @SubscribeEvent
    static void wrapCableModels(ModelEvent.ModifyBakingResult event) {
        CableParts.wrap(event);
    }

    @SubscribeEvent
    static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        FacadeTints.register(event);
    }
}
