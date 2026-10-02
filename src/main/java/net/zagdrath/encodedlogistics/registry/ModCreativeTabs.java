/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.storage.StorageTier;

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
                output.accept(ModItems.LITHOGRAPHY_PRESS.get());
                output.accept(ModItems.DRIVE_BAY.get());
                for (var block : List.of(ModItems.FABRICATOR, ModItems.GATEWAY, ModItems.SCHEDULER_CORE, ModItems.JOB_BUFFER, ModItems.THREAD_UNIT)) {
                    output.accept(block.get());
                }
                for (PartType part : PartType.values()) {
                    output.accept(ModItems.part(part).get());
                }
                output.accept(ModItems.FILTER_MODULE.get());
                output.accept(ModItems.THROUGHPUT_MODULE.get());
                output.accept(ModItems.SCHEMATIC_CARD.get());
                for (StorageTier tier : StorageTier.REGISTERED) {
                    output.accept(ModItems.storageDrive(tier).get());
                }
                for (var material : List.of(ModItems.SILICA, ModItems.SILICA_BLEND, ModItems.SILICON_BOULE, ModItems.SILICON_WAFER,
                        ModItems.FERRITE, ModItems.COPPER_FOIL, ModItems.FIBERGLASS, ModItems.SOLDER_PASTE, ModItems.CIRCUIT_SUBSTRATE,
                        ModItems.LOGIC_PHOTOMASK, ModItems.STORAGE_PHOTOMASK, ModItems.LOGIC_DIE, ModItems.RAW_NEODYMIUM,
                        ModItems.NEODYMIUM_INGOT, ModItems.RAW_TANTALUM, ModItems.TANTALUM_INGOT, ModItems.DOPED_SILICON,
                        ModItems.MEMORY_PHOTOMASK, ModItems.MEMORY_DIE, ModItems.TANTALUM_CAPACITOR, ModItems.RAW_GALLIUM, ModItems.GALLIUM_INGOT,
                        ModItems.PROCESSOR_PHOTOMASK, ModItems.PROCESSOR_DIE, ModItems.HEATSINK)) {
                    output.accept(material.get());
                }
                for (StorageTier tier : StorageTier.REGISTERED) {
                    output.accept(ModItems.storageDie(tier).get());
                }
                for (var ore : List.of(ModItems.NEODYMIUM_ORE, ModItems.DEEPSLATE_NEODYMIUM_ORE, ModItems.TANTALUM_ORE,
                        ModItems.DEEPSLATE_TANTALUM_ORE, ModItems.DEEPSLATE_GALLIUM_ORE)) {
                    output.accept(ore.get());
                }
            })
            .build());

    private ModCreativeTabs() {}
}
