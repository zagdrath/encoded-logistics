/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.menu.CapacitorBankMenu;
import net.zagdrath.encodedlogistics.menu.CollectorPlaneMenu;
import net.zagdrath.encodedlogistics.menu.DeployerPlaneMenu;
import net.zagdrath.encodedlogistics.menu.DriveBayMenu;
import net.zagdrath.encodedlogistics.menu.FabricationTerminalMenu;
import net.zagdrath.encodedlogistics.menu.FabricatorMenu;
import net.zagdrath.encodedlogistics.menu.GatewayMenu;
import net.zagdrath.encodedlogistics.menu.HandheldTerminalMenu;
import net.zagdrath.encodedlogistics.menu.InventoryTapMenu;
import net.zagdrath.encodedlogistics.menu.LithographyPressMenu;
import net.zagdrath.encodedlogistics.menu.NetworkBridgeMenu;
import net.zagdrath.encodedlogistics.menu.NetworkControllerMenu;
import net.zagdrath.encodedlogistics.menu.PointToPointMenu;
import net.zagdrath.encodedlogistics.menu.PortMenu;
import net.zagdrath.encodedlogistics.menu.RackConsoleFabricationMenu;
import net.zagdrath.encodedlogistics.menu.RackConsoleMenu;
import net.zagdrath.encodedlogistics.menu.RackMenu;
import net.zagdrath.encodedlogistics.menu.RelayAntennaMenu;
import net.zagdrath.encodedlogistics.menu.SchedulerCoreMenu;
import net.zagdrath.encodedlogistics.menu.SchematicEncoderMenu;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.menu.ThresholdSensorMenu;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, EncodedLogistics.MODID);

    public static final Supplier<MenuType<NetworkControllerMenu>> NETWORK_CONTROLLER = MENU_TYPES.register("network_controller",
            () -> IMenuTypeExtension.create(NetworkControllerMenu::new));

    public static final Supplier<MenuType<CapacitorBankMenu>> CAPACITOR_BANK = MENU_TYPES.register("capacitor_bank",
            () -> IMenuTypeExtension.create(CapacitorBankMenu::new));

    public static final Supplier<MenuType<LithographyPressMenu>> LITHOGRAPHY_PRESS = MENU_TYPES.register("lithography_press",
            () -> new MenuType<>(LithographyPressMenu::new, FeatureFlags.VANILLA_SET));

    public static final Supplier<MenuType<DriveBayMenu>> DRIVE_BAY = MENU_TYPES.register("drive_bay",
            () -> new MenuType<>(DriveBayMenu::new, FeatureFlags.VANILLA_SET));

    public static final Supplier<MenuType<AccessTerminalMenu>> ACCESS_TERMINAL = MENU_TYPES.register("access_terminal",
            () -> IMenuTypeExtension.create(AccessTerminalMenu::new));

    public static final Supplier<MenuType<FabricationTerminalMenu>> FABRICATION_TERMINAL = MENU_TYPES.register("fabrication_terminal",
            () -> IMenuTypeExtension.create(FabricationTerminalMenu::new));

    // The Terminal Desk's green screen (CrtScreen).
    public static final Supplier<MenuType<TerminalDeskMenu>> TERMINAL_DESK = MENU_TYPES.register("terminal_desk",
            () -> IMenuTypeExtension.create(TerminalDeskMenu::new));

    // The Rack Console's terminals: the Access and Fabrication Terminal screens, laid out as the console's.
    public static final Supplier<MenuType<AccessTerminalMenu>> RACK_CONSOLE = MENU_TYPES.register("rack_console",
            () -> IMenuTypeExtension.<AccessTerminalMenu>create(RackConsoleMenu::new));
    public static final Supplier<MenuType<FabricationTerminalMenu>> RACK_CONSOLE_FABRICATION = MENU_TYPES.register("rack_console_fabrication",
            () -> IMenuTypeExtension.<FabricationTerminalMenu>create(RackConsoleFabricationMenu::new));

    public static final Supplier<MenuType<PortMenu>> PORT = MENU_TYPES.register("port", () -> IMenuTypeExtension.create(PortMenu::new));

    public static final Supplier<MenuType<InventoryTapMenu>> INVENTORY_TAP = MENU_TYPES.register("inventory_tap",
            () -> IMenuTypeExtension.create(InventoryTapMenu::new));

    public static final Supplier<MenuType<ThresholdSensorMenu>> THRESHOLD_SENSOR = MENU_TYPES.register("threshold_sensor",
            () -> IMenuTypeExtension.create(ThresholdSensorMenu::new));

    public static final Supplier<MenuType<SchematicEncoderMenu>> SCHEMATIC_ENCODER = MENU_TYPES.register("schematic_encoder",
            () -> IMenuTypeExtension.create(SchematicEncoderMenu::new));

    public static final Supplier<MenuType<FabricatorMenu>> FABRICATOR = MENU_TYPES.register("fabricator",
            () -> new MenuType<>(FabricatorMenu::new, FeatureFlags.VANILLA_SET));

    public static final Supplier<MenuType<GatewayMenu>> GATEWAY = MENU_TYPES.register("gateway",
            () -> new MenuType<>(GatewayMenu::new, FeatureFlags.VANILLA_SET));

    public static final Supplier<MenuType<SchedulerCoreMenu>> SCHEDULER_CORE = MENU_TYPES.register("scheduler_core",
            () -> IMenuTypeExtension.create(SchedulerCoreMenu::new));

    public static final Supplier<MenuType<RelayAntennaMenu>> RELAY_ANTENNA = MENU_TYPES.register("relay_antenna",
            () -> new MenuType<>(RelayAntennaMenu::new, FeatureFlags.VANILLA_SET));

    public static final Supplier<MenuType<NetworkBridgeMenu>> NETWORK_BRIDGE = MENU_TYPES.register("network_bridge",
            () -> IMenuTypeExtension.create(NetworkBridgeMenu::new));

    public static final Supplier<MenuType<HandheldTerminalMenu>> HANDHELD_TERMINAL = MENU_TYPES.register("handheld_terminal",
            () -> IMenuTypeExtension.create(HandheldTerminalMenu::new));

    public static final Supplier<MenuType<PointToPointMenu>> POINT_TO_POINT_LINK = MENU_TYPES.register("point_to_point_link",
            () -> IMenuTypeExtension.create(PointToPointMenu::new));

    public static final Supplier<MenuType<CollectorPlaneMenu>> COLLECTOR_PLANE = MENU_TYPES.register("collector_plane",
            () -> IMenuTypeExtension.create(CollectorPlaneMenu::new));

    public static final Supplier<MenuType<DeployerPlaneMenu>> DEPLOYER_PLANE = MENU_TYPES.register("deployer_plane",
            () -> IMenuTypeExtension.create(DeployerPlaneMenu::new));

    public static final Supplier<MenuType<RackMenu>> SERVER_RACK = MENU_TYPES.register("server_rack",
            () -> IMenuTypeExtension.create(RackMenu::new));

    private ModMenuTypes() {}
}
