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
import net.zagdrath.encodedlogistics.menu.DriveBayMenu;
import net.zagdrath.encodedlogistics.menu.FabricationTerminalMenu;
import net.zagdrath.encodedlogistics.menu.FabricatorMenu;
import net.zagdrath.encodedlogistics.menu.GatewayMenu;
import net.zagdrath.encodedlogistics.menu.InventoryTapMenu;
import net.zagdrath.encodedlogistics.menu.LithographyPressMenu;
import net.zagdrath.encodedlogistics.menu.NetworkControllerMenu;
import net.zagdrath.encodedlogistics.menu.PortMenu;
import net.zagdrath.encodedlogistics.menu.SchedulerCoreMenu;
import net.zagdrath.encodedlogistics.menu.SchematicEncoderMenu;
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

    private ModMenuTypes() {}
}
