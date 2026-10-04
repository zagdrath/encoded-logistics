/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.zagdrath.encodedlogistics.Config;
import net.minecraft.core.BlockPos;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.block.TerminalDeskBlock;
import net.zagdrath.encodedlogistics.blockentity.CapacitorBankBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.LithographyPressBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;

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
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntityTypes.LITHOGRAPHY_PRESS.get(), LithographyPressBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.LITHOGRAPHY_PRESS.get(), LithographyPressBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntityTypes.GATEWAY.get(), GatewayBlockEntity::getItemHandler);
        // The Terminal Desk's drawer, through its pedestal half.
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) -> state.getValue(TerminalDeskBlock.PART) == TerminalDeskBlock.Part.DUMMY
                && level.getBlockEntity(TerminalDeskBlock.master(state, pos)) instanceof TerminalDeskBlockEntity desk ? VanillaContainerWrapper.of(desk) : null,
                ModBlocks.TERMINAL_DESK.get());
        // A Server Rack's power ports: FE into any connection point feeds its network.
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, blockEntity, side) -> {
            int index = state.getValue(ServerRackBlock.PART_INDEX);
            BlockPos master = RackGeometry.masterPos(pos, state.getValue(ServerRackBlock.FACING), index);
            return level.getBlockEntity(master) instanceof RackBlockEntity rack ? rack.energyHandler(index, side) : null;
        }, ModBlocks.SERVER_RACK.get());
        // The Handheld Terminal's battery charges in any FE charger.
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, ModDataComponents.ENERGY.get(),
                Config.HANDHELD_CAPACITY.getAsInt(), Config.HANDHELD_CHARGE_RATE.getAsInt(), 0), ModItems.HANDHELD_TERMINAL.get());
    }
}
