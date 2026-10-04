/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Four tabs, one after another: the network's blocks and machines, cables (every tier and colour, with anchors and
// facades), parts and tools (cable parts, modules, drives, cards, the Handheld Terminal), and materials (ores, raw
// metals, dusts, ingots and the components made from them).
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            EncodedLogistics.MODID);

    public static final Supplier<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.encodedlogistics"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.NETWORK_CONTROLLER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                for (var block : List.of(ModItems.NETWORK_CONTROLLER, ModItems.POWER_INLET, ModItems.CAPACITOR_BANK, ModItems.SEGMENT_ISOLATOR,
                        ModItems.LITHOGRAPHY_PRESS, ModItems.DRIVE_BAY, ModItems.FABRICATOR, ModItems.GATEWAY, ModItems.SCHEDULER_CORE,
                        ModItems.JOB_BUFFER, ModItems.THREAD_UNIT, ModItems.RELAY_ANTENNA, ModItems.NETWORK_BRIDGE, ModItems.SERVER_RACK)) {
                    output.accept(block.get());
                }
            })
            .build());

    public static final Supplier<CreativeModeTab> CABLES = CREATIVE_MODE_TABS.register("cables", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.encodedlogistics.cables"))
            .withTabsBefore(key("main"))
            .icon(() -> ModItems.allCables().getFirst().get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                ModItems.allCables().forEach(cable -> output.accept(cable.get()));
                output.accept(ModItems.CABLE_ANCHOR.get());
                output.accept(ModItems.CABLE_FACADE.get());
            })
            .build());

    public static final Supplier<CreativeModeTab> PARTS = CREATIVE_MODE_TABS.register("parts", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.encodedlogistics.parts"))
            .withTabsBefore(key("cables"))
            .icon(() -> ModItems.ACCESS_TERMINAL.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                for (PartType part : PartType.values()) {
                    output.accept(ModItems.part(part).get());
                }
                for (var item : List.of(ModItems.FILTER_MODULE, ModItems.THROUGHPUT_MODULE, ModItems.FUZZY_MATCH_MODULE,
                        ModItems.REDSTONE_CONTROL_MODULE)) {
                    output.accept(item.get());
                }
                for (var device : List.of(ModItems.FIREWALL, ModItems.ROUTER, ModItems.UPS, ModItems.L2_SWITCH_24, ModItems.L2_SWITCH_48,
                        ModItems.L3_SWITCH, ModItems.COMPUTE_SERVER, ModItems.MEMORY_SERVER, ModItems.FABRICATION_SERVER, ModItems.MONITORING_SERVER,
                        ModItems.NAS, ModItems.SAN)) {
                    output.accept(device.get());
                }
                output.accept(ModItems.HANDHELD_TERMINAL.get());
                output.accept(ModItems.LINK_CARD.get());
                output.accept(ModItems.SCHEMATIC_CARD.get());
                for (StorageTier tier : StorageTier.REGISTERED) {
                    output.accept(ModItems.storageDrive(tier).get());
                }
            })
            .build());

    public static final Supplier<CreativeModeTab> MATERIALS = CREATIVE_MODE_TABS.register("materials", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.encodedlogistics.materials"))
            .withTabsBefore(key("parts"))
            .icon(() -> ModItems.SILICON_WAFER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // Ores, then each metal's raw, dust and ingot.
                for (var item : List.of(ModItems.NEODYMIUM_ORE, ModItems.DEEPSLATE_NEODYMIUM_ORE, ModItems.TANTALUM_ORE,
                        ModItems.DEEPSLATE_TANTALUM_ORE, ModItems.DEEPSLATE_GALLIUM_ORE, ModItems.RAW_NEODYMIUM, ModItems.NEODYMIUM_DUST,
                        ModItems.NEODYMIUM_INGOT, ModItems.RAW_TANTALUM, ModItems.TANTALUM_DUST, ModItems.TANTALUM_INGOT, ModItems.RAW_GALLIUM,
                        ModItems.GALLIUM_DUST, ModItems.GALLIUM_INGOT)) {
                    output.accept(item.get());
                }
                for (var material : List.of(ModItems.SILICA, ModItems.SILICA_BLEND, ModItems.SILICON_BOULE, ModItems.SILICON_WAFER,
                        ModItems.DOPED_SILICON, ModItems.FERRITE, ModItems.COPPER_FOIL, ModItems.FIBERGLASS, ModItems.SOLDER_PASTE,
                        ModItems.CIRCUIT_SUBSTRATE, ModItems.TANTALUM_CAPACITOR, ModItems.HEATSINK, ModItems.OPTICAL_TRANSCEIVER,
                        ModItems.LOGIC_PHOTOMASK, ModItems.STORAGE_PHOTOMASK, ModItems.MEMORY_PHOTOMASK, ModItems.PROCESSOR_PHOTOMASK,
                        ModItems.LOGIC_DIE, ModItems.MEMORY_DIE, ModItems.PROCESSOR_DIE)) {
                    output.accept(material.get());
                }
                for (StorageTier tier : StorageTier.REGISTERED) {
                    output.accept(ModItems.storageDie(tier).get());
                }
            })
            .build());

    private ModCreativeTabs() {}

    private static ResourceKey<CreativeModeTab> key(String name) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, EncodedLogistics.id(name));
    }
}
