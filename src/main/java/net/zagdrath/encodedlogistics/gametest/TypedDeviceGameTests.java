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
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;
import net.zagdrath.encodedlogistics.menu.PartMenus;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.part.DeployerPlanePart;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.part.PortPart;
import net.zagdrath.encodedlogistics.part.ThresholdSensorPart;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Devices with fluids, gases and energy: ports in Fluid and Energy mode, the Inventory Tap on a tank, the Threshold
// Sensor on a fluid, the Collector and Deployer Planes on source blocks, and fluid filter entries. The rig: controller
// (0,1,0) - cable (1,1,0) - Drive Bay (2,1,0) with an item and a fluid drive; the device's block south of the cable at
// (1,1,1).
final class TypedDeviceGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 0), CABLE = new BlockPos(1, 1, 0), BAY = new BlockPos(2, 1, 0),
            TARGET = new BlockPos(1, 1, 1);
    private static final StorageKey WATER = StorageKey.fluid(Fluids.WATER);

    private TypedDeviceGameTests() {}

    private static void rig(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.NETWORK_CONTROLLER.get());
        helper.setBlock(BAY, ModBlocks.DRIVE_BAY.get().defaultBlockState().setValue(DriveBayBlock.FACING, Direction.EAST));
        NetworkCableBlock block = ModBlocks.cable(CableTier.NORMAL, CableColor.NEUTRAL).get();
        helper.setBlock(CABLE, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(CABLE)));
        DriveBayBlockEntity bay = helper.getBlockEntity(BAY, DriveBayBlockEntity.class);
        bay.setItem(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        bay.setItem(1, new ItemStack(ModItems.storageDrive(ResourceType.FLUID, StorageTier.K8).get()));
        insertEnergy(helper, CONTROLLER, Direction.UP, 4_000);
    }

    private static int insertEnergy(GameTestHelper helper, BlockPos pos, Direction side, int amount) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), side);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static long energyAt(GameTestHelper helper, BlockPos pos) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), null);
        return handler == null ? -1 : handler.getAmountAsLong();
    }

    // Mounts a part on the cable's south side, facing the target.
    private static void mount(GameTestHelper helper, PartType type) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(type.item());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(CABLE);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0, 0.3), Direction.SOUTH, absolute, false);
        helper.getBlockState(CABLE).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static <T> T part(GameTestHelper helper, Class<T> type) {
        return type.cast(helper.getBlockEntity(CABLE, CableBlockEntity.class).part(Direction.SOUTH));
    }

    private static NetworkStorage storage(GameTestHelper helper) {
        NetworkStorage storage = ControllerStructures.get(helper.getLevel()).storageAt(helper.getLevel(), helper.absolutePos(BAY));
        helper.assertTrue(storage != null, "Network offline");
        return storage;
    }

    private static void waterCauldron(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
    }

    // A fluid Ingress Port empties a water cauldron into the network's fluid drive; a filled bucket sets a water entry
    // in a ghost slot, and a fluid port won't take a bucket as an item entry.
    static void fluidIngress(GameTestHelper helper) {
        rig(helper);
        waterCauldron(helper);
        helper.startSequence()
                .thenExecute(() -> mount(helper, PartType.INGRESS_PORT))
                .thenIdle(2)
                .thenExecute(() -> {
                    part(helper, PortPart.class).setResourceType(ResourceType.FLUID);
                    // Ghost entries: a water bucket is water for a fluid port, and a bucket for an item one.
                    helper.assertTrue(water(PartMenus.ghost(new ItemStack(Items.WATER_BUCKET), ResourceType.FLUID)), "Bucket didn't set water");
                    helper.assertTrue(PartMenus.ghost(new ItemStack(Items.WATER_BUCKET), ResourceType.ITEM).is(Items.WATER_BUCKET), "Item port took water");
                    helper.assertTrue(water(PartMenus.ghost(new ItemStack(Items.WATER_BUCKET), null)), "Tap-style ghost didn't set water");
                })
                .thenIdle(22)
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper).count(WATER) == 1_000, "Network has " + storage(helper).count(WATER) + " mB water");
                    helper.assertTrue(helper.getBlockState(TARGET).is(Blocks.CAULDRON), "Cauldron is " + helper.getBlockState(TARGET));
                })
                .thenSucceed();
    }

    private static boolean water(ItemStack entry) {
        return WATER.equals(ResourceEntryItem.key(entry));
    }

    // A fluid Egress Port sends nothing with an empty filter; with a water entry it fills an empty cauldron from the
    // network, a bucket at a time.
    static void fluidEgress(GameTestHelper helper) {
        rig(helper);
        helper.setBlock(TARGET, Blocks.CAULDRON);
        helper.startSequence()
                .thenExecute(() -> mount(helper, PartType.EGRESS_PORT))
                .thenIdle(2)
                .thenExecute(() -> {
                    part(helper, PortPart.class).setResourceType(ResourceType.FLUID);
                    storage(helper).insert(WATER, 3_000, false);
                })
                .thenIdle(22)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(TARGET).is(Blocks.CAULDRON), "Empty filter sent water");
                    part(helper, PortPart.class).filter().set(0, ResourceEntryItem.of(WATER, 0));
                })
                .thenIdle(22)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(TARGET).is(Blocks.WATER_CAULDRON), "Cauldron is " + helper.getBlockState(TARGET));
                    helper.assertTrue(storage(helper).count(WATER) == 2_000, "Network has " + storage(helper).count(WATER) + " mB water");
                })
                .thenSucceed();
    }

    // An Inventory Tap on a water cauldron shows its water as network storage, and gives it out.
    static void tapTank(GameTestHelper helper) {
        rig(helper);
        waterCauldron(helper);
        helper.startSequence()
                .thenExecute(() -> mount(helper, PartType.INVENTORY_TAP))
                .thenIdle(3)
                .thenExecute(() -> {
                    NetworkStorage storage = storage(helper);
                    helper.assertTrue(storage.count(WATER) == 1_000, "Tap shows " + storage.count(WATER) + " mB");
                    helper.assertTrue(storage.list(ResourceType.FLUID).containsKey(WATER), "Water not listed");
                    helper.assertTrue(storage.extract(WATER, 1_000, false) == 1_000, "Couldn't take the water");
                    helper.assertTrue(helper.getBlockState(TARGET).is(Blocks.CAULDRON), "Cauldron is " + helper.getBlockState(TARGET));
                })
                .thenSucceed();
    }

    // Energy ports: Ingress takes FE from a Capacitor Bank behind it (not on the network: the port's side doesn't
    // connect) into the pool; Egress gives FE from the pool to a Lithography Press, every tick.
    static void energyPorts(GameTestHelper helper) {
        rig(helper);
        helper.setBlock(TARGET, ModBlocks.CAPACITOR_BANK.get());
        long[] before = new long[2];
        helper.startSequence()
                .thenExecute(() -> mount(helper, PartType.INGRESS_PORT))
                .thenIdle(2)
                .thenExecute(() -> {
                    part(helper, PortPart.class).setResourceType(ResourceType.ENERGY);
                    insertEnergy(helper, TARGET, Direction.UP, 16_000);
                    before[0] = energyAt(helper, TARGET);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    long now = energyAt(helper, TARGET);
                    // 1,024 FE a tick without Throughput Modules.
                    helper.assertTrue(now < before[0] && before[0] - now <= 4 * 1_024 + 1_024, "Bank went " + before[0] + " -> " + now);
                    helper.getBlockEntity(CABLE, CableBlockEntity.class).setAttachment(Direction.SOUTH, CableAttachments.Attachment.NONE);
                    helper.setBlock(TARGET, ModBlocks.LITHOGRAPHY_PRESS.get());
                    mount(helper, PartType.EGRESS_PORT);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    part(helper, PortPart.class).setResourceType(ResourceType.ENERGY);
                    before[1] = energyAt(helper, TARGET);
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(energyAt(helper, TARGET) > before[1], "Press has " + energyAt(helper, TARGET) + " FE"))
                .thenSucceed();
    }

    // The Threshold Sensor watches a fluid (a water entry) by its amount in mB.
    static void sensorFluid(GameTestHelper helper) {
        rig(helper);
        helper.startSequence()
                .thenExecute(() -> mount(helper, PartType.THRESHOLD_SENSOR))
                .thenIdle(2)
                .thenExecute(() -> {
                    ThresholdSensorPart sensor = part(helper, ThresholdSensorPart.class);
                    sensor.setItem(ResourceEntryItem.of(WATER, 0));
                    sensor.setThreshold(1_500);
                    storage(helper).insert(WATER, 1_000, false);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertFalse(part(helper, ThresholdSensorPart.class).emitting(), "Emitting at 1,000 mB");
                    storage(helper).insert(WATER, 1_000, false);
                })
                .thenIdle(6)
                .thenExecute(() -> helper.assertTrue(part(helper, ThresholdSensorPart.class).emitting(), "Not emitting at 2,000 mB"))
                .thenSucceed();
    }

    // A Collector Plane picks up a water source in front into the network; a Deployer Plane with a water entry puts
    // one back.
    static void planesFluid(GameTestHelper helper) {
        rig(helper);
        helper.setBlock(TARGET, Blocks.WATER);
        helper.startSequence()
                .thenExecute(() -> mount(helper, PartType.COLLECTOR_PLANE))
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper).count(WATER) == 1_000, "Network has " + storage(helper).count(WATER) + " mB water");
                    helper.assertTrue(helper.getBlockState(TARGET).isAir(), "Front is " + helper.getBlockState(TARGET));
                    helper.getBlockEntity(CABLE, CableBlockEntity.class).setAttachment(Direction.SOUTH, CableAttachments.Attachment.NONE);
                    mount(helper, PartType.DEPLOYER_PLANE);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    PartFilter filter = part(helper, DeployerPlanePart.class).filter();
                    filter.set(0, ResourceEntryItem.of(WATER, 0));
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(TARGET).getFluidState().isSource(), "Front is " + helper.getBlockState(TARGET));
                    helper.assertTrue(storage(helper).count(WATER) == 0, "Network still has " + storage(helper).count(WATER) + " mB water");
                })
                .thenSucceed();
    }
}
