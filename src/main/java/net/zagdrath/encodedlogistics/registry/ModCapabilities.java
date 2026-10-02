/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.zagdrath.encodedlogistics.blockentity.CapacitorBankBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;

public final class ModCapabilities {
    private ModCapabilities() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModCapabilities::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.NETWORK_CONTROLLER.get(),
                NetworkControllerBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.POWER_INLET.get(), PowerInletBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.CAPACITOR_BANK.get(), CapacitorBankBlockEntity::getEnergyHandler);
    }
}
