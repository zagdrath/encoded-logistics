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
import net.zagdrath.encodedlogistics.blockentity.AccessPointBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.CapacitorBankBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.ControlInterfaceBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.FabricatorBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.LithographyPressBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.SwivelChairBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.WirelessBridgeBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.WirelessPortBlockEntity;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlockEntity;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlockEntity;
import net.zagdrath.encodedlogistics.midrange.KeypunchBlockEntity;
import net.zagdrath.encodedlogistics.midrange.LinePrinterBlockEntity;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;

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

    public static final Supplier<BlockEntityType<TerminalDeskBlockEntity>> TERMINAL_DESK = BLOCK_ENTITY_TYPES.register(
            "terminal_desk", () -> new BlockEntityType<>(TerminalDeskBlockEntity::new, ModBlocks.TERMINAL_DESK.get()));

    public static final Supplier<BlockEntityType<ControlInterfaceBlockEntity>> CONTROL_INTERFACE = BLOCK_ENTITY_TYPES.register(
            "control_interface", () -> new BlockEntityType<>(ControlInterfaceBlockEntity::new, ModBlocks.CONTROL_INTERFACE.get()));

    public static final Supplier<BlockEntityType<SwivelChairBlockEntity>> SWIVEL_CHAIR = BLOCK_ENTITY_TYPES.register(
            "swivel_chair", () -> new BlockEntityType<>(SwivelChairBlockEntity::new, ModBlocks.SWIVEL_CHAIR.get()));

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

    public static final Supplier<BlockEntityType<AccessPointBlockEntity>> ACCESS_POINT = BLOCK_ENTITY_TYPES.register(
            "access_point", () -> new BlockEntityType<>(AccessPointBlockEntity::new, ModBlocks.ACCESS_POINT.get()));

    public static final Supplier<BlockEntityType<WirelessBridgeBlockEntity>> WIRELESS_BRIDGE = BLOCK_ENTITY_TYPES.register(
            "wireless_bridge", () -> new BlockEntityType<>(WirelessBridgeBlockEntity::new, ModBlocks.WIRELESS_BRIDGE.get()));

    public static final Supplier<BlockEntityType<DisplayPanelBlockEntity>> DISPLAY_PANEL = BLOCK_ENTITY_TYPES.register(
            "display_panel", () -> new BlockEntityType<>(DisplayPanelBlockEntity::new, ModBlocks.DISPLAY_PANEL.get()));

    public static final Supplier<BlockEntityType<WirelessPortBlockEntity>> WIRELESS_PORT = BLOCK_ENTITY_TYPES.register(
            "wireless_port", () -> new BlockEntityType<>(WirelessPortBlockEntity::new, ModBlocks.WIRELESS_INGRESS_PORT.get(),
                    ModBlocks.WIRELESS_EGRESS_PORT.get()));

    // The Midrange line, on a footprint's master: a Midrange System's or Integrated Midrange System's, the peripherals'.
    public static final Supplier<BlockEntityType<MidrangeSystemBlockEntity>> MIDRANGE_SYSTEM = BLOCK_ENTITY_TYPES.register(
            "midrange_system", () -> new BlockEntityType<>(MidrangeSystemBlockEntity::new, ModBlocks.MIDRANGE_SYSTEM.get(), ModBlocks.INTEGRATED_MIDRANGE.get()));

    public static final Supplier<BlockEntityType<KeypunchBlockEntity>> KEYPUNCH = BLOCK_ENTITY_TYPES.register(
            "keypunch", () -> new BlockEntityType<>(KeypunchBlockEntity::new, ModBlocks.KEYPUNCH.get()));

    public static final Supplier<BlockEntityType<CardReaderBlockEntity>> CARD_READER = BLOCK_ENTITY_TYPES.register(
            "card_reader", () -> new BlockEntityType<>(CardReaderBlockEntity::new, ModBlocks.CARD_READER.get()));

    public static final Supplier<BlockEntityType<LinePrinterBlockEntity>> LINE_PRINTER = BLOCK_ENTITY_TYPES.register(
            "line_printer", () -> new BlockEntityType<>(LinePrinterBlockEntity::new, ModBlocks.LINE_PRINTER.get()));

    // On the rack's master block only.
    public static final Supplier<BlockEntityType<RackBlockEntity>> SERVER_RACK = BLOCK_ENTITY_TYPES.register(
            "server_rack", () -> new BlockEntityType<>(RackBlockEntity::new, ModBlocks.SERVER_RACK.get()));

    private ModBlockEntityTypes() {}
}
