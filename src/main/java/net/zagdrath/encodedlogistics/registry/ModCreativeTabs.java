/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            EncodedLogistics.MODID);

    public static final Supplier<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.encodedlogistics"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.NETWORK_CONTROLLER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.NETWORK_CONTROLLER.get());
                output.accept(ModItems.POWER_INLET.get());
                output.accept(ModItems.CAPACITOR_BANK.get());
                output.accept(ModItems.SEGMENT_ISOLATOR.get());
                ModItems.allCables().forEach(cable -> output.accept(cable.get()));
                output.accept(ModItems.CABLE_ANCHOR.get());
                output.accept(ModItems.CABLE_FACADE.get());
            })
            .build());

    private ModCreativeTabs() {}
}
