/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.item.CableFacadeItem;
import net.zagdrath.encodedlogistics.item.PartItem;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.item.StorageTierItem;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.storage.StorageTier;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EncodedLogistics.MODID);

    public static final DeferredItem<BlockItem> NETWORK_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.NETWORK_CONTROLLER);
    public static final DeferredItem<BlockItem> POWER_INLET = ITEMS.registerSimpleBlockItem(ModBlocks.POWER_INLET);
    public static final DeferredItem<BlockItem> CAPACITOR_BANK = ITEMS.registerSimpleBlockItem(ModBlocks.CAPACITOR_BANK);
    public static final DeferredItem<BlockItem> SEGMENT_ISOLATOR = ITEMS.registerSimpleBlockItem(ModBlocks.SEGMENT_ISOLATOR);

    // Cable attachments: mounted on a cable's side (see NetworkCableBlock).
    public static final DeferredItem<Item> CABLE_ANCHOR = ITEMS.registerSimpleItem("cable_anchor");
    public static final DeferredItem<CableFacadeItem> CABLE_FACADE = ITEMS.registerItem("cable_facade", CableFacadeItem::new);

    // Phase 1: materials, components, drives and their machines. TODO: the recipes are a first pass.
    public static final DeferredItem<BlockItem> LITHOGRAPHY_PRESS = ITEMS.registerSimpleBlockItem(ModBlocks.LITHOGRAPHY_PRESS);
    public static final DeferredItem<BlockItem> DRIVE_BAY = ITEMS.registerSimpleBlockItem(ModBlocks.DRIVE_BAY);

    public static final DeferredItem<Item> SILICA = ITEMS.registerSimpleItem("silica");
    public static final DeferredItem<Item> SILICA_BLEND = ITEMS.registerSimpleItem("silica_blend");
    public static final DeferredItem<Item> SILICON_BOULE = ITEMS.registerSimpleItem("silicon_boule");
    public static final DeferredItem<Item> SILICON_WAFER = ITEMS.registerSimpleItem("silicon_wafer");
    public static final DeferredItem<Item> FERRITE = ITEMS.registerSimpleItem("ferrite");
    public static final DeferredItem<Item> COPPER_FOIL = ITEMS.registerSimpleItem("copper_foil");
    public static final DeferredItem<Item> FIBERGLASS = ITEMS.registerSimpleItem("fiberglass");
    public static final DeferredItem<Item> SOLDER_PASTE = ITEMS.registerSimpleItem("solder_paste");
    public static final DeferredItem<Item> CIRCUIT_SUBSTRATE = ITEMS.registerSimpleItem("circuit_substrate");
    public static final DeferredItem<Item> LOGIC_DIE = ITEMS.registerSimpleItem("logic_die");
    // Photomasks are reusable: the press never uses them up.
    public static final DeferredItem<Item> LOGIC_PHOTOMASK = ITEMS.registerSimpleItem("logic_photomask", p -> p.stacksTo(1));
    public static final DeferredItem<Item> STORAGE_PHOTOMASK = ITEMS.registerSimpleItem("storage_photomask", p -> p.stacksTo(1));

    // Phase 2: ores and metals, second-tier components, port modules.
    public static final DeferredItem<BlockItem> NEODYMIUM_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.NEODYMIUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_NEODYMIUM_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_NEODYMIUM_ORE);
    public static final DeferredItem<BlockItem> TANTALUM_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.TANTALUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_TANTALUM_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_TANTALUM_ORE);
    public static final DeferredItem<Item> RAW_NEODYMIUM = ITEMS.registerSimpleItem("raw_neodymium");
    public static final DeferredItem<Item> RAW_TANTALUM = ITEMS.registerSimpleItem("raw_tantalum");
    public static final DeferredItem<Item> NEODYMIUM_INGOT = ITEMS.registerSimpleItem("neodymium_ingot");
    public static final DeferredItem<Item> TANTALUM_INGOT = ITEMS.registerSimpleItem("tantalum_ingot");
    public static final DeferredItem<Item> DOPED_SILICON = ITEMS.registerSimpleItem("doped_silicon");
    public static final DeferredItem<Item> MEMORY_DIE = ITEMS.registerSimpleItem("memory_die");
    public static final DeferredItem<Item> MEMORY_PHOTOMASK = ITEMS.registerSimpleItem("memory_photomask", p -> p.stacksTo(1));
    public static final DeferredItem<Item> TANTALUM_CAPACITOR = ITEMS.registerSimpleItem("tantalum_capacitor");
    // Port modules (#encodedlogistics:port_modules). Fuzzy Match and Redstone Control come in Phase 4.
    public static final DeferredItem<Item> FILTER_MODULE = ITEMS.registerSimpleItem("filter_module");
    public static final DeferredItem<Item> THROUGHPUT_MODULE = ITEMS.registerSimpleItem("throughput_module");

    // Cable parts: terminals, ports, the tap and the sensor (PartType).
    private static final Map<PartType, DeferredItem<PartItem>> PARTS = new EnumMap<>(PartType.class);

    static {
        for (PartType type : PartType.values()) {
            PARTS.put(type, ITEMS.registerItem(type.getSerializedName(), p -> new PartItem(p, type)));
        }
    }

    public static final DeferredItem<PartItem> ACCESS_TERMINAL = PARTS.get(PartType.ACCESS_TERMINAL);

    // Storage Dies and Drives of the registered tiers (StorageTier.REGISTERED).
    private static final Map<StorageTier, DeferredItem<StorageTierItem>> DIES = new EnumMap<>(StorageTier.class);
    private static final Map<StorageTier, DeferredItem<StorageDriveItem>> DRIVES = new EnumMap<>(StorageTier.class);

    static {
        for (StorageTier tier : StorageTier.REGISTERED) {
            DIES.put(tier, ITEMS.registerItem("storage_die_" + tier.id(), p -> new StorageTierItem(p, tier)));
            DRIVES.put(tier, ITEMS.registerItem("storage_drive_" + tier.id(), p -> new StorageDriveItem(p, tier), p -> p.stacksTo(1)));
        }
    }

    // In the same order as ModBlocks.allCables().
    private static final List<DeferredItem<BlockItem>> CABLES = new ArrayList<>();

    static {
        for (DeferredBlock<NetworkCableBlock> block : ModBlocks.allCables()) {
            CABLES.add(ITEMS.registerSimpleBlockItem(block));
        }
    }

    private ModItems() {}

    public static List<DeferredItem<BlockItem>> allCables() {
        return CABLES;
    }

    public static DeferredItem<PartItem> part(PartType type) {
        return PARTS.get(type);
    }

    public static DeferredItem<StorageTierItem> storageDie(StorageTier tier) {
        return DIES.get(tier);
    }

    public static DeferredItem<StorageDriveItem> storageDrive(StorageTier tier) {
        return DRIVES.get(tier);
    }
}
