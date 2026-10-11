/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.DriveCopies;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.EnergyDrives;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Storage Drives stacking, and copies of drives (DriveCopies): new drives stack to 64 and a drive with an id to 1; a
// copy gets an id and contents of its own; an emptied drive goes back to blank.
final class DriveCopyGameTests {
    private DriveCopyGameTests() {}

    // Every type and tier of new drive stacks to 64, and stacks with another new one.
    static void newDrivesStack(GameTestHelper helper) {
        for (ResourceType type : ResourceType.values()) {
            for (StorageTier tier : StorageTier.values()) {
                ItemStack a = new ItemStack(ModItems.storageDrive(type, tier).get()), b = a.copy();
                helper.assertTrue(a.getMaxStackSize() == 64, type + " " + tier + " drive stacks to " + a.getMaxStackSize());
                helper.assertTrue(ItemStack.isSameItemSameComponents(a, b), type + " " + tier + " drives don't stack together");
            }
        }
        helper.succeed();
    }

    // A drive in a bay stacks to 1; a copy of it put in the next slot gets its own id holding a copy of the contents,
    // so the network holds both drives' stone, and taking from one leaves the other alone.
    static void copiedDriveInBay(GameTestHelper helper) {
        BlockPos controller = new BlockPos(0, 1, 0), bayPos = new BlockPos(2, 1, 0);
        helper.setBlock(controller, ModBlocks.NETWORK_CONTROLLER.get());
        helper.setBlock(bayPos, ModBlocks.DRIVE_BAY.get().defaultBlockState().setValue(DriveBayBlock.FACING, Direction.EAST));
        cable(helper, new BlockPos(1, 1, 0));
        StorageKey stone = StorageKey.of(new ItemStack(Items.STONE));
        helper.startSequence()
                .thenExecute(() -> bay(helper, bayPos).setItem(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get())))
                .thenExecute(() -> insertEnergy(helper, controller, 4_000))
                .thenIdle(3)
                .thenExecute(() -> {
                    ItemStack drive = bay(helper, bayPos).getItem(0);
                    helper.assertTrue(drive.getMaxStackSize() == 1, "A drive with an id stacks to " + drive.getMaxStackSize());
                    helper.assertTrue(storage(helper, bayPos).insert(stone, 100, false) == 100, "Stone didn't fit");
                })
                .thenExecute(() -> bay(helper, bayPos).setItem(1, bay(helper, bayPos).getItem(0).copy()))
                .thenIdle(3)
                .thenExecute(() -> {
                    UUID original = StorageDriveItem.id(bay(helper, bayPos).getItem(0)), copy = StorageDriveItem.id(bay(helper, bayPos).getItem(1));
                    helper.assertTrue(original != null && copy != null && !original.equals(copy), "Copy kept the id " + copy);
                    DriveStorage drives = DriveStorage.get(helper.getLevel().getServer());
                    helper.assertTrue(drives.count(copy, stone) == 100, "Copy holds " + drives.contents(copy));
                    NetworkStorage storage = storage(helper, bayPos);
                    helper.assertTrue(storage.list().getOrDefault(stone, 0L) == 200, "Network holds " + storage.list());
                    helper.assertTrue(drives.extract(copy, stone, 100, false) == 100, "Couldn't empty the copy");
                    helper.assertTrue(drives.count(original, stone) == 100, "Emptying the copy took from the original");
                })
                .thenSucceed();
    }

    // A creative player's copy (a middle-click puts it on the cursor, beside the original) gets its own id and the
    // contents; a stack of copies is one drive; an Energy Storage Drive's copy keeps its charge and gets its own id.
    static void creativeCopy(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        DriveStorage drives = DriveStorage.get(helper.getLevel().getServer());
        StorageKey stone = StorageKey.of(new ItemStack(Items.STONE));
        UUID id = UUID.randomUUID();
        drives.insert(id, StorageTier.K8, stone, 50, false);
        ItemStack original = new ItemStack(ModItems.storageDrive(StorageTier.K8).get());
        original.set(ModDataComponents.DRIVE_ID.get(), id);
        ItemStack copy = original.copyWithCount(5);
        ItemStack energy = new ItemStack(ModItems.storageDrive(ResourceType.ENERGY, StorageTier.K8).get());
        energy.set(ModDataComponents.DRIVE_ID.get(), UUID.randomUUID());
        EnergyDrives.set(energy, 1_000);
        ItemStack energyCopy = energy.copy();
        player.getInventory().setItem(0, original);
        player.getInventory().setItem(1, energy);
        player.getInventory().setItem(2, energyCopy);
        player.containerMenu.setCarried(copy);

        DriveCopies.creativeTick(player);

        ItemStack carried = player.containerMenu.getCarried();
        UUID copyId = StorageDriveItem.id(carried);
        helper.assertTrue(id.equals(StorageDriveItem.id(player.getInventory().getItem(0))), "The original's id changed");
        helper.assertTrue(copyId != null && !copyId.equals(id), "The copy kept the id " + copyId);
        helper.assertTrue(carried.getCount() == 1, carried.getCount() + " copies in one stack");
        helper.assertTrue(drives.count(copyId, stone) == 50, "Copy holds " + drives.contents(copyId));
        Map<StorageKey, Long> originalContents = drives.contents(id);
        helper.assertTrue(originalContents.getOrDefault(stone, 0L) == 50, "Original holds " + originalContents);
        UUID energyId = StorageDriveItem.id(player.getInventory().getItem(1)), energyCopyId = StorageDriveItem.id(player.getInventory().getItem(2));
        helper.assertTrue(energyCopyId != null && !energyCopyId.equals(energyId), "The energy drive's copy kept its id");
        helper.assertTrue(EnergyDrives.stored(player.getInventory().getItem(2)) == 1_000, "The energy drive's copy lost its charge");
        helper.succeed();
    }

    // A drive that's empty again goes back to blank, and stacks with a new one; one with something in it doesn't.
    static void emptyDriveGoesBlank(GameTestHelper helper) {
        DriveStorage drives = DriveStorage.get(helper.getLevel().getServer());
        StorageKey stone = StorageKey.of(new ItemStack(Items.STONE));
        ItemStack fresh = new ItemStack(ModItems.storageDrive(StorageTier.K8).get());
        ItemStack used = fresh.copy(), full = fresh.copy();
        UUID usedId = UUID.randomUUID(), fullId = UUID.randomUUID();
        used.set(ModDataComponents.DRIVE_ID.get(), usedId);
        DriveCopies.unique(used);
        drives.insert(usedId, StorageTier.K8, stone, 10, false);
        drives.extract(usedId, stone, 10, false);
        full.set(ModDataComponents.DRIVE_ID.get(), fullId);
        DriveCopies.unique(full);
        drives.insert(fullId, StorageTier.K8, stone, 10, false);
        ItemStack energy = new ItemStack(ModItems.storageDrive(ResourceType.ENERGY, StorageTier.K8).get());
        ItemStack drained = energy.copy();
        drained.set(ModDataComponents.DRIVE_ID.get(), UUID.randomUUID());
        EnergyDrives.set(drained, 0);

        DriveCopies.clearIfEmpty(helper.getLevel().getServer(), used);
        DriveCopies.clearIfEmpty(helper.getLevel().getServer(), full);
        DriveCopies.clearIfEmpty(helper.getLevel().getServer(), drained);

        helper.assertTrue(ItemStack.isSameItemSameComponents(used, fresh), "An emptied drive didn't go back to blank: " + used.getComponentsPatch());
        helper.assertTrue(used.getMaxStackSize() == 64, "An emptied drive stacks to " + used.getMaxStackSize());
        helper.assertTrue(fullId.equals(StorageDriveItem.id(full)) && full.getMaxStackSize() == 1, "A drive with stone in it went blank");
        helper.assertTrue(ItemStack.isSameItemSameComponents(drained, energy), "A drained energy drive didn't go back to blank");
        helper.succeed();
    }

    private static DriveBayBlockEntity bay(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, DriveBayBlockEntity.class);
    }

    private static NetworkStorage storage(GameTestHelper helper, BlockPos bayPos) {
        NetworkStorage storage = ControllerStructures.get(helper.getLevel()).storageAt(helper.getLevel(), helper.absolutePos(bayPos));
        helper.assertTrue(storage != null, "No storage");
        return storage;
    }

    private static void cable(GameTestHelper helper, BlockPos pos) {
        NetworkCableBlock block = ModBlocks.cable(CableTier.NORMAL, CableColor.NEUTRAL).get();
        helper.setBlock(pos, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
    }

    private static void insertEnergy(GameTestHelper helper, BlockPos pos, int amount) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), Direction.UP);
        try (Transaction transaction = Transaction.openRoot()) {
            handler.insert(amount, transaction);
            transaction.commit();
        }
    }
}
