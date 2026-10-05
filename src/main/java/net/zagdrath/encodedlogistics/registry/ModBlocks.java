/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.AccessPointBlock;
import net.zagdrath.encodedlogistics.block.CapacitorBankBlock;
import net.zagdrath.encodedlogistics.block.ControlInterfaceBlock;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.FabricatorBlock;
import net.zagdrath.encodedlogistics.block.GatewayBlock;
import net.zagdrath.encodedlogistics.block.LithographyPressBlock;
import net.zagdrath.encodedlogistics.block.NetworkBridgeBlock;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;
import net.zagdrath.encodedlogistics.block.PartHostBlock;
import net.zagdrath.encodedlogistics.block.PowerInletBlock;
import net.zagdrath.encodedlogistics.block.RelayAntennaBlock;
import net.zagdrath.encodedlogistics.block.SchedulerBlock;
import net.zagdrath.encodedlogistics.block.SchedulerCoreBlock;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.block.SwivelChairBlock;
import net.zagdrath.encodedlogistics.block.TerminalDeskBlock;
import net.zagdrath.encodedlogistics.block.ThreadUnitBlock;
import net.zagdrath.encodedlogistics.block.WirelessBridgeBlock;
import net.zagdrath.encodedlogistics.block.WirelessPortBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlock;
import net.zagdrath.encodedlogistics.midrange.ExpansionCabinetBlock;
import net.zagdrath.encodedlogistics.midrange.IntegratedMidrangeBlock;
import net.zagdrath.encodedlogistics.midrange.MidrangePeripheralBlock;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlock;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EncodedLogistics.MODID);

    // TODO: the recipes (data/encodedlogistics/recipe/) are placeholders until the mod's own materials exist.
    public static final DeferredBlock<NetworkControllerBlock> NETWORK_CONTROLLER = BLOCKS.registerBlock("network_controller",
            NetworkControllerBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(NetworkControllerBlock::lightLevel));

    public static final DeferredBlock<PowerInletBlock> POWER_INLET = BLOCKS.registerBlock("power_inlet", PowerInletBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL));

    public static final DeferredBlock<CapacitorBankBlock> CAPACITOR_BANK = BLOCKS.registerBlock("capacitor_bank", CapacitorBankBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL));

    public static final DeferredBlock<SegmentIsolatorBlock> SEGMENT_ISOLATOR = BLOCKS.registerBlock("segment_isolator", SegmentIsolatorBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<LithographyPressBlock> LITHOGRAPHY_PRESS = BLOCKS.registerBlock("lithography_press",
            LithographyPressBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(LithographyPressBlock::lightLevel));

    public static final DeferredBlock<DriveBayBlock> DRIVE_BAY = BLOCKS.registerBlock("drive_bay", DriveBayBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL));

    // Neodymium and tantalum ores (Phase 2): drop their raw metal (silk touch: the ore), need an iron pickaxe.
    public static final DeferredBlock<Block> NEODYMIUM_ORE = BLOCKS.registerSimpleBlock("neodymium_ore", ModBlocks::stoneOre);
    public static final DeferredBlock<Block> DEEPSLATE_NEODYMIUM_ORE = BLOCKS.registerSimpleBlock("deepslate_neodymium_ore", ModBlocks::deepslateOre);
    public static final DeferredBlock<Block> TANTALUM_ORE = BLOCKS.registerSimpleBlock("tantalum_ore", ModBlocks::stoneOre);
    public static final DeferredBlock<Block> DEEPSLATE_TANTALUM_ORE = BLOCKS.registerSimpleBlock("deepslate_tantalum_ore", ModBlocks::deepslateOre);

    // Gallium (Phase 3): deepslate only.
    public static final DeferredBlock<Block> DEEPSLATE_GALLIUM_ORE = BLOCKS.registerSimpleBlock("deepslate_gallium_ore", ModBlocks::deepslateOre);

    // Autocrafting (Phase 3): the Fabricator and Gateway carry out schematics; the Scheduler multiblock runs the jobs.
    public static final DeferredBlock<FabricatorBlock> FABRICATOR = BLOCKS.registerBlock("fabricator", FabricatorBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .lightLevel(FabricatorBlock::lightLevel));

    public static final DeferredBlock<GatewayBlock> GATEWAY = BLOCKS.registerBlock("gateway", GatewayBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .lightLevel(GatewayBlock::lightLevel));

    public static final DeferredBlock<SchedulerCoreBlock> SCHEDULER_CORE = BLOCKS.registerBlock("scheduler_core", SchedulerCoreBlock::new,
            ModBlocks::scheduler);
    public static final DeferredBlock<SchedulerBlock> JOB_BUFFER = BLOCKS.registerBlock("job_buffer", SchedulerBlock::new, ModBlocks::scheduler);
    public static final DeferredBlock<ThreadUnitBlock> THREAD_UNIT = BLOCKS.registerBlock("thread_unit", ThreadUnitBlock::new, ModBlocks::scheduler);

    // Reach (Phase 4): wireless coverage for Handheld Terminals, and long-range links between networks.
    public static final DeferredBlock<RelayAntennaBlock> RELAY_ANTENNA = BLOCKS.registerBlock("relay_antenna", RelayAntennaBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion());

    // Scripts (the Terminal OS): redstone in and out.
    public static final DeferredBlock<ControlInterfaceBlock> CONTROL_INTERFACE = BLOCKS.registerBlock("control_interface", ControlInterfaceBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL));

    public static final DeferredBlock<NetworkBridgeBlock> NETWORK_BRIDGE = BLOCKS.registerBlock("network_bridge", NetworkBridgeBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .lightLevel(NetworkBridgeBlock::lightLevel));

    // The Midrange line (an early autocrafting computer, before the Scheduler) and its peripherals.
    public static final DeferredBlock<MidrangeSystemBlock> MIDRANGE_SYSTEM = BLOCKS.registerBlock("midrange_system", MidrangeSystemBlock::new,
            p -> p.mapColor(MapColor.SAND).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .lightLevel(MidrangeSystemBlock::lightLevel));
    public static final DeferredBlock<ExpansionCabinetBlock> EXPANSION_CABINET = BLOCKS.registerBlock("expansion_cabinet", ExpansionCabinetBlock::new,
            p -> p.mapColor(MapColor.SAND).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<IntegratedMidrangeBlock> INTEGRATED_MIDRANGE = BLOCKS.registerBlock("integrated_midrange", IntegratedMidrangeBlock::new,
            p -> p.mapColor(MapColor.SAND).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .lightLevel(IntegratedMidrangeBlock::lightLevel));
    public static final DeferredBlock<MidrangePeripheralBlock> KEYPUNCH = BLOCKS.registerBlock("keypunch",
            p -> new MidrangePeripheralBlock("keypunch", List.of(Vec3i.ZERO, new Vec3i(0, 1, 0)), p),
            p -> p.mapColor(MapColor.SAND).strength(2.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<CardReaderBlock> CARD_READER = BLOCKS.registerBlock("card_reader", CardReaderBlock::new,
            p -> p.mapColor(MapColor.SAND).strength(2.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<MidrangePeripheralBlock> LINE_PRINTER = BLOCKS.registerBlock("line_printer",
            p -> new MidrangePeripheralBlock("line_printer", List.of(Vec3i.ZERO, new Vec3i(1, 0, 0), new Vec3i(0, 1, 0), new Vec3i(1, 1, 0)), p),
            p -> p.mapColor(MapColor.SAND).strength(2.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion());

    // Wireless: the Access Point (the radio), the Wireless Bridge and the Wireless Ingress / Egress Ports (its clients).
    public static final DeferredBlock<AccessPointBlock> ACCESS_POINT = BLOCKS.registerBlock("access_point", AccessPointBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .lightLevel(AccessPointBlock::lightLevel));
    public static final DeferredBlock<WirelessBridgeBlock> WIRELESS_BRIDGE = BLOCKS.registerBlock("wireless_bridge", WirelessBridgeBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .lightLevel(WirelessBridgeBlock::lightLevel));
    public static final DeferredBlock<WirelessPortBlock> WIRELESS_INGRESS_PORT = BLOCKS.registerBlock("wireless_ingress_port",
            p -> new WirelessPortBlock(true, p), p -> p.mapColor(MapColor.METAL).strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .noOcclusion().lightLevel(WirelessPortBlock::lightLevel));
    public static final DeferredBlock<WirelessPortBlock> WIRELESS_EGRESS_PORT = BLOCKS.registerBlock("wireless_egress_port",
            p -> new WirelessPortBlock(false, p), p -> p.mapColor(MapColor.METAL).strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .noOcclusion().lightLevel(WirelessPortBlock::lightLevel));

    // The Server Rack (a 1x3x2 multiblock: ServerRackBlock) and the devices that mount in it.
    public static final DeferredBlock<ServerRackBlock> SERVER_RACK = BLOCKS.registerBlock("server_rack", ServerRackBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false));

    // The Terminal Desk (2 wide: TerminalDeskBlock) and the Swivel Chair.
    public static final DeferredBlock<TerminalDeskBlock> TERMINAL_DESK = BLOCKS.registerBlock("terminal_desk", TerminalDeskBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false));
    public static final DeferredBlock<SwivelChairBlock> SWIVEL_CHAIR = BLOCKS.registerBlock("swivel_chair", SwivelChairBlock::new,
            p -> p.mapColor(MapColor.COLOR_BROWN).strength(1.0F).sound(SoundType.WOOL).noOcclusion());

    private static BlockBehaviour.Properties scheduler(BlockBehaviour.Properties properties) {
        return properties.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties stoneOre(BlockBehaviour.Properties properties) {
        return properties.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F);
    }

    private static BlockBehaviour.Properties deepslateOre(BlockBehaviour.Properties properties) {
        return properties.mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
                .strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE);
    }

    // Holds a part mounted on a block face (no item; the part is what drops).
    public static final DeferredBlock<PartHostBlock> PART_HOST = BLOCKS.registerBlock("part_host", PartHostBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(0.5F, 1.0F).sound(SoundType.METAL).noOcclusion().dynamicShape().noLootTable()
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false));

    // Network, Dense Network and Fiber Cables in every colour: network_cable, white_network_cable, ...,
    // dense_network_cable, ..., fiber_cable, white_fiber_cable, .... Their shape depends on their attachments (block
    // entity), so it isn't cached per state.
    private static final Map<CableTier, Map<CableColor, DeferredBlock<NetworkCableBlock>>> CABLES = new EnumMap<>(CableTier.class);

    static {
        for (CableTier tier : CableTier.values()) {
            Map<CableColor, DeferredBlock<NetworkCableBlock>> colours = new EnumMap<>(CableColor.class);
            for (CableColor color : CableColor.values()) {
                colours.put(color, BLOCKS.registerBlock(color.prefix() + tier.baseName(), p -> new NetworkCableBlock(p, tier, color),
                        p -> p.mapColor(MapColor.METAL).strength(0.5F, 1.0F).sound(SoundType.METAL).noOcclusion().dynamicShape()
                                .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false)));
            }
            CABLES.put(tier, colours);
        }
    }

    private ModBlocks() {}

    public static DeferredBlock<NetworkCableBlock> cable(CableTier tier, CableColor color) {
        return CABLES.get(tier).get(color);
    }

    // Every cable: Network Cables first, then Dense, then Fiber, each tier neutral then the dyes in CableColor order.
    public static List<DeferredBlock<NetworkCableBlock>> allCables() {
        List<DeferredBlock<NetworkCableBlock>> cables = new ArrayList<>();
        CABLES.values().forEach(colours -> cables.addAll(colours.values()));
        return cables;
    }
}
