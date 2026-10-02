/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.NetworkBridgeBlock;
import net.zagdrath.encodedlogistics.block.RelayAntennaBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.item.HandheldLinkState;
import net.zagdrath.encodedlogistics.item.HandheldTerminalItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex;
import net.zagdrath.encodedlogistics.part.CollectorPlanePart;
import net.zagdrath.encodedlogistics.part.DeployerPlanePart;
import net.zagdrath.encodedlogistics.part.LinkType;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.part.PointToPointPart;
import net.zagdrath.encodedlogistics.part.PortPart;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Phase 4: Network Bridges (in one dimension and across two), the Relay Antenna's coverage for Handheld Terminals,
// Point-to-Point Links of each type, the Collector and Deployer Planes, and the Fuzzy Match and Redstone Control modules.
// Most tests use a powered controller at (0,1,0) and build the rest of their network from it.
final class Phase4GameTests {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 0);
    private static final ItemKey COBBLESTONE = ItemKey.of(new ItemStack(Items.COBBLESTONE)), STONE = ItemKey.of(new ItemStack(Items.STONE));

    private Phase4GameTests() {}

    // A powered controller.
    private static void controller(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.NETWORK_CONTROLLER.get());
        EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(CONTROLLER), Direction.UP);
        try (Transaction transaction = Transaction.openRoot()) {
            energy.insert(20_000, transaction);
            transaction.commit();
        }
    }

    // A cable joined to whatever is already round it.
    private static void cable(GameTestHelper helper, BlockPos pos) {
        NetworkCableBlock block = ModBlocks.cable(CableTier.NORMAL, CableColor.NEUTRAL).get();
        helper.setBlock(pos, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
    }

    private static void driveBay(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.DRIVE_BAY.get().defaultBlockState().setValue(DriveBayBlock.FACING, Direction.EAST));
        helper.getBlockEntity(pos, DriveBayBlockEntity.class).setItem(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
    }

    // Mounts a part on a side of a cable, as a player using its item on that side would.
    private static void mount(GameTestHelper helper, BlockPos cable, Direction side, PartType type) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(type.item());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(cable);
        Vec3 hit = Vec3.atCenterOf(absolute).add(side.getStepX() * 0.3, side.getStepY() * 0.3, side.getStepZ() * 0.3);
        helper.getBlockState(cable).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, side, absolute, false));
    }

    private static <T> T part(GameTestHelper helper, BlockPos cable, Direction side, Class<T> type) {
        return type.cast(helper.getBlockEntity(cable, CableBlockEntity.class).part(side));
    }

    private static NetworkStorage storage(GameTestHelper helper, BlockPos device) {
        NetworkStorage storage = ControllerStructures.get(helper.getLevel()).storageAt(helper.getLevel(), helper.absolutePos(device));
        helper.assertTrue(storage != null, "Network offline at " + device);
        return storage;
    }

    private static boolean online(GameTestHelper helper, BlockPos device) {
        return ControllerStructures.get(helper.getLevel()).isDeviceOnline(helper.getLevel(), helper.absolutePos(device));
    }

    // Two Bridges paired with each other carry the controller's network to a Drive Bay with no cable between them (the
    // bay comes online, a lane crosses, the lens pulses); breaking one end unlinks the other and the bay goes offline.
    static void bridgeJoinsNetworks(GameTestHelper helper) {
        BlockPos bridgeA = new BlockPos(2, 1, 0), bridgeB = new BlockPos(2, 1, 3), bay = new BlockPos(4, 1, 3);
        controller(helper);
        helper.setBlock(bridgeA, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState().setValue(NetworkBridgeBlock.FACING, Direction.UP));
        helper.setBlock(bridgeB, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState().setValue(NetworkBridgeBlock.FACING, Direction.UP));
        driveBay(helper, bay);
        cable(helper, new BlockPos(1, 1, 0));
        cable(helper, new BlockPos(3, 1, 3));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertFalse(online(helper, bay), "Bay online before pairing");
                    helper.assertTrue(NetworkBridgeBlockEntity.pair(helper.getBlockEntity(bridgeA, NetworkBridgeBlockEntity.class),
                            helper.getBlockEntity(bridgeB, NetworkBridgeBlockEntity.class)), "Pairing failed");
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(online(helper, bay), "Bay not online through the bridges");
                    storage(helper, bay).insert(COBBLESTONE, 5, false);
                    NetworkBridgeBlockEntity a = helper.getBlockEntity(bridgeA, NetworkBridgeBlockEntity.class);
                    helper.assertTrue(a.lanesUsed(helper.getLevel().getServer()) == 1, "Lanes crossing " + a.lanesUsed(helper.getLevel().getServer()));
                    helper.assertTrue(helper.getBlockState(bridgeB).getValue(NetworkBridgeBlock.STATUS) == NetworkBridgeBlock.Status.LINKED_ACTIVE,
                            "Status " + helper.getBlockState(bridgeB).getValue(NetworkBridgeBlock.STATUS));
                    helper.setBlock(bridgeA, Blocks.AIR);
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertFalse(online(helper, bay), "Bay still online with one bridge gone");
                    helper.assertTrue(helper.getBlockEntity(bridgeB, NetworkBridgeBlockEntity.class).partner() == null, "Partner kept");
                    helper.assertTrue(helper.getBlockState(bridgeB).getValue(NetworkBridgeBlock.STATUS) == NetworkBridgeBlock.Status.UNLINKED,
                            "Status " + helper.getBlockState(bridgeB).getValue(NetworkBridgeBlock.STATUS));
                })
                .thenSucceed();
    }

    // A Bridge in the Nether paired with one here carries the network there: a Drive Bay beside it comes online, and its
    // storage is the network's.
    static void bridgeCrossDimension(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "No Nether");
        BlockPos base = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() / 8, 100, helper.absolutePos(BlockPos.ZERO).getZ() / 8);
        BlockPos farBridge = base, farCable = base.east(), farBay = base.east(2);
        BlockPos bridge = new BlockPos(2, 1, 0);
        int chunkX = farBridge.getX() >> 4, chunkZ = farBridge.getZ() >> 4;
        controller(helper);
        helper.setBlock(bridge, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState().setValue(NetworkBridgeBlock.FACING, Direction.UP));
        cable(helper, new BlockPos(1, 1, 0));
        nether.setChunkForced(chunkX, chunkZ, true);
        nether.setBlockAndUpdate(farBridge, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState().setValue(NetworkBridgeBlock.FACING, Direction.UP));
        nether.setBlockAndUpdate(farBay, ModBlocks.DRIVE_BAY.get().defaultBlockState().setValue(DriveBayBlock.FACING, Direction.EAST));
        ((DriveBayBlockEntity) nether.getBlockEntity(farBay)).setItem(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        NetworkCableBlock block = ModBlocks.cable(CableTier.NORMAL, CableColor.NEUTRAL).get();
        nether.setBlockAndUpdate(farCable, block.withConnections(block.defaultBlockState(), nether, farCable));
        Runnable cleanUp = () -> {
            for (BlockPos pos : List.of(farBridge, farCable, farBay)) {
                nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            for (ItemEntity item : nether.getEntitiesOfClass(ItemEntity.class, new AABB(farBridge).inflate(4))) {
                item.discard();
            }
            nether.setChunkForced(chunkX, chunkZ, false);
        };
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(NetworkBridgeBlockEntity.pair(helper.getBlockEntity(bridge, NetworkBridgeBlockEntity.class),
                        (NetworkBridgeBlockEntity) nether.getBlockEntity(farBridge)), "Pairing across dimensions failed"))
                .thenIdle(12)
                .thenExecute(() -> {
                    boolean online = ControllerStructures.get(nether).isDeviceOnline(nether, farBay);
                    NetworkStorage there = ControllerStructures.get(nether).storageAt(nether, farBay);
                    if (there != null) {
                        there.insert(STONE, 3, false);
                    }
                    NetworkIndex.NetworkRef network = ControllerStructures.networkOf(nether, farBay);
                    cleanUp.run();
                    helper.assertTrue(online && there != null, "Nether bay not online");
                    helper.assertTrue(network != null && network.dimension() == helper.getLevel().dimension(), "Nether bay on network " + network);
                })
                .thenSucceed();
    }

    // A Relay Antenna on the network goes online; a Handheld Terminal linked to the network is in range within 32 blocks
    // of it and out of range beyond, until an Optical Transceiver adds 32 more.
    static void relayRange(GameTestHelper helper) {
        BlockPos relay = new BlockPos(2, 1, 0);
        controller(helper);
        helper.setBlock(relay, ModBlocks.RELAY_ANTENNA.get());
        cable(helper, new BlockPos(1, 1, 0));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(relay).getValue(RelayAntennaBlock.ONLINE), "Relay not online");
                    ServerLevel level = helper.getLevel();
                    Vec3 center = Vec3.atCenterOf(helper.absolutePos(relay));
                    ItemStack handheld = new ItemStack(ModItems.HANDHELD_TERMINAL.get());
                    helper.assertTrue(HandheldTerminalItem.state(level, center, handheld) == HandheldLinkState.UNLINKED, "Unlinked handheld linked");
                    handheld.set(ModDataComponents.HANDHELD_NETWORK.get(), ControllerStructures.networkOf(level, helper.absolutePos(relay)));
                    helper.assertTrue(HandheldTerminalItem.state(level, center.add(20, 0, 0), handheld) == HandheldLinkState.LINKED, "Not in range at 20");
                    helper.assertTrue(HandheldTerminalItem.state(level, center.add(50, 0, 0), handheld) == HandheldLinkState.OUT_OF_RANGE,
                            "In range at 50 without a transceiver");
                    helper.assertTrue(HandheldTerminalItem.signal(HandheldTerminalItem.access(level, center.add(4, 0, 0),
                            ControllerStructures.networkOf(level, helper.absolutePos(relay))), center.add(4, 0, 0)) == 4, "Signal near the relay");
                    helper.getBlockEntity(relay, RelayAntennaBlockEntity.class).setItem(0, new ItemStack(ModItems.OPTICAL_TRANSCEIVER.get()));
                    helper.assertTrue(helper.getBlockEntity(relay, RelayAntennaBlockEntity.class).range() == 64, "Range with a transceiver");
                    helper.assertTrue(HandheldTerminalItem.state(level, center.add(50, 0, 0), handheld) == HandheldLinkState.LINKED,
                            "Out of range at 50 with a transceiver");
                })
                .thenSucceed();
    }

    // An items Point-to-Point Link moves the input chest's items into the output chest; an energy one moves FE from a
    // Capacitor Bank into another; a redstone one sends out the signal its input reads.
    static void pointToPoint(GameTestHelper helper) {
        BlockPos cableIn = new BlockPos(1, 1, 0), cableOut = new BlockPos(1, 2, 0), chestIn = new BlockPos(1, 1, 1), chestOut = new BlockPos(1, 2, 1);
        controller(helper);
        helper.setBlock(chestIn, Blocks.CHEST);
        helper.setBlock(chestOut, Blocks.CHEST);
        cable(helper, cableIn);
        cable(helper, cableOut);
        helper.startSequence()
                .thenExecute(() -> {
                    mount(helper, cableIn, Direction.SOUTH, PartType.POINT_TO_POINT_LINK);
                    mount(helper, cableOut, Direction.SOUTH, PartType.POINT_TO_POINT_LINK);
                    part(helper, cableOut, Direction.SOUTH, PointToPointPart.class).toggleDirection();
                    helper.assertTrue(PointToPointPart.pair(part(helper, cableIn, Direction.SOUTH, PointToPointPart.class),
                            part(helper, cableOut, Direction.SOUTH, PointToPointPart.class)) == PointToPointPart.PairResult.PAIRED, "Pairing failed");
                    helper.getBlockEntity(chestIn, ChestBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 40));
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(chestOut, ChestBlockEntity.class).countItem(Items.COBBLESTONE) == 40,
                            "Output chest has " + helper.getBlockEntity(chestOut, ChestBlockEntity.class).countItem(Items.COBBLESTONE));
                    PointToPointPart in = part(helper, cableIn, Direction.SOUTH, PointToPointPart.class);
                    helper.assertTrue(in.lit() && in.look() == 0, "Input look " + in.look());
                    // Unpaired, the type can change; redstone next.
                    in.unpairAll();
                    helper.assertFalse(part(helper, cableOut, Direction.SOUTH, PointToPointPart.class).paired(), "Output still paired");
                    in.setLinkType(LinkType.REDSTONE);
                    part(helper, cableOut, Direction.SOUTH, PointToPointPart.class).setLinkType(LinkType.REDSTONE);
                    PointToPointPart.pair(in, part(helper, cableOut, Direction.SOUTH, PointToPointPart.class));
                    helper.setBlock(chestIn, Blocks.REDSTONE_BLOCK);
                    helper.setBlock(chestOut, Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    PointToPointPart out = part(helper, cableOut, Direction.SOUTH, PointToPointPart.class);
                    helper.assertTrue(out.signal() == 15, "Output signal " + out.signal());
                    helper.assertTrue(helper.getLevel().hasNeighborSignal(helper.absolutePos(cableOut).south()), "Nothing powered");
                    helper.setBlock(chestIn, Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(part(helper, cableOut, Direction.SOUTH, PointToPointPart.class).signal() == 0, "Signal stuck on"))
                .thenSucceed();
    }

    // A lanes Point-to-Point Link carries lanes to a cable branch not joined to the network: the Drive Bay on it comes
    // online once the two ends are paired.
    static void pointToPointLanes(GameTestHelper helper) {
        BlockPos cableIn = new BlockPos(1, 1, 0), farCable = new BlockPos(3, 1, 3), bay = new BlockPos(4, 1, 3);
        controller(helper);
        driveBay(helper, bay);
        cable(helper, cableIn);
        cable(helper, farCable);
        helper.startSequence()
                .thenExecute(() -> {
                    mount(helper, cableIn, Direction.SOUTH, PartType.POINT_TO_POINT_LINK);
                    mount(helper, farCable, Direction.NORTH, PartType.POINT_TO_POINT_LINK);
                    PointToPointPart in = part(helper, cableIn, Direction.SOUTH, PointToPointPart.class);
                    PointToPointPart out = part(helper, farCable, Direction.NORTH, PointToPointPart.class);
                    in.setLinkType(LinkType.LANES);
                    out.setLinkType(LinkType.LANES);
                    out.toggleDirection();
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertFalse(online(helper, bay), "Bay online before pairing");
                    PointToPointPart.pair(part(helper, cableIn, Direction.SOUTH, PointToPointPart.class),
                            part(helper, farCable, Direction.NORTH, PointToPointPart.class));
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(online(helper, bay), "Bay not online through the lanes link"))
                .thenSucceed();
    }

    // A Collector Plane breaks the block in front (cobblestone: 60 ticks) and collects a dropped item there into the
    // network; a Deployer Plane places a filtered block from the network, then drops one in drop mode.
    static void planes(GameTestHelper helper) {
        BlockPos cable = new BlockPos(1, 1, 0), upper = new BlockPos(1, 2, 0), bay = new BlockPos(2, 1, 0);
        BlockPos front = new BlockPos(1, 1, 1), deployFront = new BlockPos(1, 2, 1);
        controller(helper);
        driveBay(helper, bay);
        helper.setBlock(front, Blocks.COBBLESTONE);
        cable(helper, cable);
        cable(helper, upper);
        helper.startSequence()
                .thenExecute(() -> {
                    mount(helper, cable, Direction.SOUTH, PartType.COLLECTOR_PLANE);
                    mount(helper, upper, Direction.SOUTH, PartType.DEPLOYER_PLANE);
                })
                .thenIdle(70)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(front).isAir(), "Cobblestone not broken");
                    helper.assertTrue(storage(helper, bay).count(COBBLESTONE) == 1, "Network has " + storage(helper, bay).count(COBBLESTONE));
                    helper.spawnItem(Items.STONE, Vec3.atCenterOf(front));
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper, bay).count(STONE) == 1, "Dropped stone not collected");
                    storage(helper, bay).insert(COBBLESTONE, 4, false);
                    part(helper, upper, Direction.SOUTH, DeployerPlanePart.class).filter().set(0, new ItemStack(Items.COBBLESTONE));
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(deployFront).is(Blocks.COBBLESTONE), "Deployer didn't place");
                    helper.assertTrue(storage(helper, bay).count(COBBLESTONE) == 4, "Network has " + storage(helper, bay).count(COBBLESTONE));
                    helper.setBlock(deployFront, Blocks.AIR);
                    part(helper, upper, Direction.SOUTH, DeployerPlanePart.class).toggleMode();
                    // The collector below would take the dropped item: take its filter out of the way (stone only).
                    CollectorPlanePart collector = part(helper, cable, Direction.SOUTH, CollectorPlanePart.class);
                    collector.module().set(0, new ItemStack(ModItems.FILTER_MODULE.get()));
                    collector.filter().set(0, new ItemStack(Items.STONE));
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper, bay).count(COBBLESTONE) == 3, "Network has " + storage(helper, bay).count(COBBLESTONE));
                    boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(helper.absolutePos(deployFront)).inflate(2), item -> item.getItem().is(Items.COBBLESTONE)).isEmpty();
                    helper.assertTrue(dropped, "Nothing dropped");
                })
                .thenSucceed();
    }

    // Fuzzy matching: an entry matches its tag's other items, or its own item by how worn it is.
    static void fuzzyFilter(GameTestHelper helper) {
        PartFilter filter = new PartFilter();
        filter.set(0, new ItemStack(Items.IRON_INGOT));
        int ingots = PartFilter.tags(new ItemStack(Items.IRON_INGOT)).indexOf(Tags.Items.INGOTS);
        helper.assertTrue(ingots >= 0, "Iron has no c:ingots tag");
        filter.setFuzzy(0, PartFilter.FUZZY_TAG + ingots);
        helper.assertTrue(filter.test(new ItemStack(Items.GOLD_INGOT), false, false, true), "Gold doesn't match #c:ingots");
        helper.assertFalse(filter.test(new ItemStack(Items.GOLD_INGOT), false, false, false), "Gold matched without the module");
        helper.assertFalse(filter.test(new ItemStack(Items.COBBLESTONE), false, false, true), "Cobblestone matched #c:ingots");
        filter.set(1, new ItemStack(Items.DIAMOND_PICKAXE));
        filter.setFuzzy(1, PartFilter.FUZZY_QUARTER + 2);
        ItemStack worn = new ItemStack(Items.DIAMOND_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() * 6 / 10);
        ItemStack fresh = new ItemStack(Items.DIAMOND_PICKAXE);
        helper.assertTrue(filter.test(worn, false, false, true), "60% worn pickaxe doesn't match 50-75%");
        helper.assertFalse(filter.test(fresh, false, false, true), "New pickaxe matched 50-75%");
        helper.succeed();
    }

    // An Ingress Port with a Redstone Control Module set to "active with signal" only pulls while its cable is powered.
    static void redstoneControl(GameTestHelper helper) {
        BlockPos cable = new BlockPos(1, 1, 0), bay = new BlockPos(2, 1, 0), chest = new BlockPos(1, 1, 1), power = new BlockPos(1, 2, 0);
        controller(helper);
        driveBay(helper, bay);
        helper.setBlock(chest, Blocks.CHEST);
        cable(helper, cable);
        helper.startSequence()
                .thenExecute(() -> {
                    mount(helper, cable, Direction.SOUTH, PartType.INGRESS_PORT);
                    PortPart port = part(helper, cable, Direction.SOUTH, PortPart.class);
                    port.modules().set(0, new ItemStack(ModItems.REDSTONE_CONTROL_MODULE.get()));
                    port.cycleRedstoneMode();
                    helper.assertTrue(port.redstoneMode() == PortPart.REDSTONE_HIGH, "Mode " + port.redstoneMode());
                    helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper, bay).count(COBBLESTONE) == 0, "Pulled without a signal");
                    helper.setBlock(power, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(45)
                .thenExecute(() -> helper.assertTrue(storage(helper, bay).count(COBBLESTONE) >= 4, "Didn't pull with a signal"))
                .thenSucceed();
    }
}
