/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.NetworkControllerMenu;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, EncodedLogistics.MODID);

    public static final Supplier<MenuType<NetworkControllerMenu>> NETWORK_CONTROLLER = MENU_TYPES.register("network_controller",
            () -> IMenuTypeExtension.create(NetworkControllerMenu::new));

    private ModMenuTypes() {}
}
