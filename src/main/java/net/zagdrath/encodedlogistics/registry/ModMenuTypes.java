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
import net.zagdrath.encodedlogistics.menu.LithographyPressMenu;
import net.zagdrath.encodedlogistics.menu.NetworkControllerMenu;

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

    private ModMenuTypes() {}
}
