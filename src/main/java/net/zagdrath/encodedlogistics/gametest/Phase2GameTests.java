/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableConnection;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.part.InventoryTapPart;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.part.PortPart;
import net.zagdrath.encodedlogistics.part.ThresholdSensorPart;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Phase 2 parts on a powered network with an 8K drive: Ingress and Egress Ports, the Inventory Tap and the Threshold
// Sensor. The rig: controller (0,1,0) - cable (1,1,0) - Drive Bay (2,1,0); a chest south of the cable at (1,1,1).
final class Phase2GameTests {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 0), CABLE = new BlockPos(1, 1, 0), BAY = new BlockPos(2, 1, 0),
            CHEST = new BlockPos(1, 1, 1);
    private static final ItemKey COBBLESTONE = ItemKey.of(new ItemStack(Items.COBBLESTONE)), STONE = ItemKey.of(new ItemStack(Items.STONE));

    private Phase2GameTests() {}

    private static void rig(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.NETWORK_CONTROLLER.get());
        helper.setBlock(BAY, ModBlocks.DRIVE_BAY.get().defaultBlockState().setValue(DriveBayBlock.FACING, Direction.EAST));
        NetworkCableBlock block = ModBlocks.cable(CableTier.NORMAL, CableColor.NEUTRAL).get();
        helper.setBlock(CABLE, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(CABLE)));
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.getBlockEntity(BAY, DriveBayBlockEntity.class).setItem(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(CONTROLLER), Direction.UP);
        try (Transaction transaction = Transaction.openRoot()) {
            energy.insert(20_000, transaction);
            transaction.commit();
        }
    }

    // Mounts a part on the cable's side facing the chest (south).
    private static void mount(GameTestHelper helper, PartType type) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(type.item());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(CABLE);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0, 0.3), Direction.SOUTH, absolute, false);
        helper.getBlockState(CABLE).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static NetworkStorage storage(GameTestHelper helper) {
        NetworkStorage storage = ControllerStructures.get(helper.getLevel()).storageAt(helper.getLevel(), helper.absolutePos(BAY));
        helper.assertTrue(storage != null, "Network offline");
        return storage;
    }

    private static ChestBlockEntity chest(GameTestHelper helper) {
        return helper.getBlockEntity(CHEST, ChestBlockEntity.class);
    }

    private static <T> T part(GameTestHelper helper, Class<T> type) {
        return type.cast(helper.getBlockEntity(CABLE, CableBlockEntity.class).part(Direction.SOUTH));
    }

    // An Ingress Port pulls the chest's items into the network, 4 per operation without modules, 16 with one Throughput
    // Module; it lights while moving.
    static void ingressPort(GameTestHelper helper) {
        rig(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    mount(helper, PartType.INGRESS_PORT);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(NetworkCableBlock.connection(helper.getBlockState(CABLE), Direction.SOUTH) == CableConnection.NONE,
                            "Port side connects");
                    helper.assertTrue(helper.getBlockEntity(CABLE, CableBlockEntity.class).isOnline(), "Port offline");
                })
                .thenIdle(21)
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper).count(COBBLESTONE) == 4, "Network has " + storage(helper).count(COBBLESTONE));
                    helper.assertTrue(chest(helper).getItem(0).getCount() == 60, "Chest has " + chest(helper).getItem(0));
                    part(helper, PortPart.class).modules().set(0, new ItemStack(ModItems.THROUGHPUT_MODULE.get()));
                })
                .thenIdle(21)
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper).count(COBBLESTONE) == 20, "Network has " + storage(helper).count(COBBLESTONE));
                    helper.assertTrue(part(helper, PortPart.class).lit(), "Port not lit while moving");
                })
                .thenSucceed();
    }

    // An Egress Port with an empty filter sends nothing; filtered to stone it pushes stone (only) into the chest.
    static void egressPort(GameTestHelper helper) {
        rig(helper);
        helper.startSequence()
                .thenExecute(() -> mount(helper, PartType.EGRESS_PORT))
                .thenIdle(3)
                .thenExecute(() -> {
                    storage(helper).insert(STONE, 32, false);
                    storage(helper).insert(COBBLESTONE, 32, false);
                })
                .thenIdle(21)
                .thenExecute(() -> {
                    helper.assertTrue(chest(helper).isEmpty(), "Empty filter sent items");
                    part(helper, PortPart.class).filter().set(0, new ItemStack(Items.STONE));
                })
                .thenIdle(21)
                .thenExecute(() -> {
                    helper.assertTrue(chest(helper).countItem(Items.STONE) == 4, "Chest has " + chest(helper).countItem(Items.STONE) + " stone");
                    helper.assertTrue(chest(helper).countItem(Items.COBBLESTONE) == 0, "Cobblestone went out");
                    helper.assertTrue(storage(helper).count(STONE) == 28, "Network has " + storage(helper).count(STONE) + " stone");
                })
                .thenSucceed();
    }

    // An Inventory Tap shows the chest in the network's storage; with a higher priority than the drive it's filled
    // first; read-only stops items going in.
    static void inventoryTap(GameTestHelper helper) {
        rig(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
                    mount(helper, PartType.INVENTORY_TAP);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    NetworkStorage storage = storage(helper);
                    helper.assertTrue(storage.list().getOrDefault(COBBLESTONE, 0L) == 10, "Tap doesn't show the chest: " + storage.list());
                    part(helper, InventoryTapPart.class).setPriority(5);
                })
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper).insert(STONE, 16, false) == 16, "Stone didn't go in");
                    helper.assertTrue(chest(helper).countItem(Items.STONE) == 16, "Higher-priority tap wasn't filled first");
                    part(helper, InventoryTapPart.class).cycleAccess();
                })
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper).insert(STONE, 16, false) == 16, "Stone didn't go to the drive");
                    helper.assertTrue(chest(helper).countItem(Items.STONE) == 16, "Read-only tap took items");
                    helper.assertTrue(storage(helper).count(STONE) == 32, "Network counts " + storage(helper).count(STONE));
                })
                .thenIdle(21)
                .thenExecute(() -> helper.assertTrue(part(helper, InventoryTapPart.class).lit(), "Tap light off while attached"))
                .thenSucceed();
    }

    // A Threshold Sensor set to stone above 10 emits once the network holds more, powering the block its face is against.
    static void thresholdSensor(GameTestHelper helper) {
        rig(helper);
        helper.setBlock(CHEST, Blocks.AIR);
        BlockPos lamp = CHEST;
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
                    mount(helper, PartType.THRESHOLD_SENSOR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    ThresholdSensorPart sensor = part(helper, ThresholdSensorPart.class);
                    sensor.setItem(new ItemStack(Items.STONE));
                    sensor.setThreshold(10);
                    storage(helper).insert(STONE, 8, false);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertFalse(part(helper, ThresholdSensorPart.class).emitting(), "Emitting at 8");
                    storage(helper).insert(STONE, 8, false);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertTrue(part(helper, ThresholdSensorPart.class).emitting(), "Not emitting at 16");
                    helper.assertTrue(helper.getLevel().hasNeighborSignal(helper.absolutePos(lamp)), "Lamp not powered");
                })
                .thenSucceed();
    }
}
