/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.EnergyDrives;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Fluid, Pressurized and Energy Storage Drives and the typed storage key: each drive holds only its own type, by the
// drives' byte model; keys save as before for items; Energy Storage Drives are part of the energy pool and keep their
// charge out of a holder.
final class TypedStorageGameTests {
    private TypedStorageGameTests() {}

    private static void cable(GameTestHelper helper, BlockPos pos) {
        NetworkCableBlock block = ModBlocks.cable(CableTier.NORMAL, CableColor.NEUTRAL).get();
        helper.setBlock(pos, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
    }

    private static int insertEnergy(GameTestHelper helper, BlockPos pos, Direction side, int amount) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), side);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static ItemStack drive(ResourceType type, StorageTier tier) {
        return new ItemStack(ModItems.storageDrive(type, tier).get());
    }

    // A powered controller with a Drive Bay (facing east) next to it, through a cable.
    private static void bay(GameTestHelper helper, BlockPos controller, BlockPos bayPos) {
        helper.setBlock(controller, ModBlocks.NETWORK_CONTROLLER.get());
        helper.setBlock(bayPos, ModBlocks.DRIVE_BAY.get().defaultBlockState().setValue(DriveBayBlock.FACING, Direction.EAST));
        cable(helper, controller.east());
    }

    // An item, a fluid and an energy drive in one bay: water goes only to the fluid drive, stone only to the item drive;
    // an 8K fluid drive holds (8,192 - 64) buckets of one fluid; its stats count a byte a bucket; Resource Entries are
    // never stored as items.
    static void typedDrives(GameTestHelper helper) {
        BlockPos controller = new BlockPos(0, 1, 0), bayPos = new BlockPos(2, 1, 0);
        bay(helper, controller, bayPos);
        StorageTier tier = StorageTier.K8;
        StorageKey water = StorageKey.fluid(Fluids.WATER), lava = StorageKey.fluid(Fluids.LAVA), stone = StorageKey.of(new ItemStack(Items.STONE));
        long[] took = new long[1];
        helper.startSequence()
                .thenExecute(() -> {
                    DriveBayBlockEntity bay = helper.getBlockEntity(bayPos, DriveBayBlockEntity.class);
                    bay.setItem(0, drive(ResourceType.ITEM, tier));
                    bay.setItem(1, drive(ResourceType.FLUID, tier));
                    bay.setItem(2, drive(ResourceType.ENERGY, tier));
                })
                .thenExecute(() -> insertEnergy(helper, controller, Direction.UP, 4_000))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(water.type() == ResourceType.FLUID && water.id().equals(Identifier.withDefaultNamespace("water")), "Water key " + water);
                    NetworkStorage storage = ControllerStructures.get(helper.getLevel()).storageAt(helper.getLevel(), helper.absolutePos(bayPos));
                    helper.assertTrue(storage != null, "No storage");
                    helper.assertTrue(storage.insert(water, 5_500, false) == 5_500, "Water didn't fit");
                    helper.assertTrue(storage.insert(stone, 100, false) == 100, "Stone didn't fit");
                    DriveBayBlockEntity bay = helper.getBlockEntity(bayPos, DriveBayBlockEntity.class);
                    DriveStorage data = DriveStorage.get(helper.getLevel().getServer());
                    helper.assertTrue(data.count(StorageDriveItem.id(bay.getItem(1)), water) == 5_500, "Water not in the fluid drive");
                    helper.assertTrue(data.count(StorageDriveItem.id(bay.getItem(0)), water) == 0, "Water in the item drive");
                    helper.assertTrue(data.count(StorageDriveItem.id(bay.getItem(1)), stone) == 0, "Stone in the fluid drive");
                    helper.assertTrue(storage.list(ResourceType.FLUID).equals(Map.of(water, 5_500L)), "Fluids: " + storage.list(ResourceType.FLUID));
                    helper.assertTrue(storage.extract(water, 500, false) == 500, "Couldn't take water out");
                    // A Resource Entry is a stand-in, never an item to store.
                    helper.assertTrue(storage.insert(StorageKey.of(ResourceEntryItem.of(water, 1_000)), 1, false) == 0, "Stored a Resource Entry");
                    took[0] = storage.insert(lava, Long.MAX_VALUE / 2, false);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    // Two types (water and lava) each reserve 64 bytes; the rest holds a bucket a byte.
                    long room = (tier.bytes() - 2 * tier.bytesPerType()) * 1_000 - 5_000;
                    helper.assertTrue(took[0] == room, "Took " + took[0] + " mB of lava, expected " + room);
                    DriveStats stats = StorageDriveItem.stats(helper.getBlockEntity(bayPos, DriveBayBlockEntity.class).getItem(1));
                    helper.assertTrue(stats.bytesUsed() == tier.bytes() && stats.typesUsed() == 2 && stats.light() == 3, "Fluid drive stats " + stats);
                    // The drive stores only fluids: the item drive still holds the stone, and the energy drive no keys.
                    NetworkStorage storage = ControllerStructures.get(helper.getLevel()).storageAt(helper.getLevel(), helper.absolutePos(bayPos));
                    helper.assertTrue(storage.count(stone) == 100, "Stone: " + storage.count(stone));
                    helper.assertTrue(storage.hotBytes(ResourceType.FLUID)[1] == tier.bytes(), "Fluid bytes " + storage.hotBytes(ResourceType.FLUID)[1]);
                    helper.assertTrue(storage.hotBytes()[1] == tier.bytes(), "Item bytes " + storage.hotBytes()[1]);
                    // The sled code carries the drive's type for the model.
                    int code = StorageDriveItem.sledCode(helper.getBlockEntity(bayPos, DriveBayBlockEntity.class).getItem(1), true);
                    helper.assertTrue(StorageDriveItem.sledType(code) == ResourceType.FLUID && StorageDriveItem.sledTier(code) == 0
                            && StorageDriveItem.sledLight(code) == 3, "Sled code " + code);
                })
                .thenSucceed();
    }

    // Keys save as before for items (an item stack template) and as {resource_type, ...} for fluids and gases; a world
    // saved with item-only drives loads the same and saves back unchanged; a gas from a mod that isn't loaded keeps its key.
    static void keyCodecs(GameTestHelper helper) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        StorageKey stone = StorageKey.of(new ItemStack(Items.STONE));
        JsonElement stoneJson = StorageKey.CODEC.encodeStart(ops, stone).getOrThrow();
        JsonElement template = net.minecraft.world.item.ItemStackTemplate.CODEC
                .encodeStart(ops, net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.STONE))).getOrThrow();
        helper.assertTrue(stoneJson.equals(template), "Item key saved as " + stoneJson + ", not " + template);
        for (StorageKey key : new StorageKey[] { stone, StorageKey.fluid(Fluids.WATER), StorageKey.pressurized("othermod", Identifier.parse("othermod:argon")) }) {
            JsonElement json = StorageKey.CODEC.encodeStart(ops, key).getOrThrow();
            StorageKey back = StorageKey.CODEC.parse(ops, json).getOrThrow();
            helper.assertTrue(back.equals(key) && back.type() == key.type(), key + " came back as " + back + " from " + json);
        }
        // A drive as an item-only world saved it.
        UUID id = UUID.fromString("00000000-0000-0001-0000-000000000002");
        String old = "{\"drives\":[{\"id\":[0,1,0,2],\"items\":[{\"item\":{\"id\":\"minecraft:stone\",\"count\":1},\"count\":600,\"touched\":5}]}]}";
        JsonElement saved = JsonParser.parseString(old);
        DriveStorage loaded = DriveStorage.TYPE.codec().parse(ops, saved).getOrThrow();
        helper.assertTrue(loaded.count(id, stone) == 600, "Loaded " + loaded.contents(id));
        JsonElement again = DriveStorage.TYPE.codec().encodeStart(ops, loaded).getOrThrow();
        helper.assertTrue(again.equals(saved), "Saved back as " + again);
        helper.succeed();
    }

    // An Energy Storage Drive in a bay is energy pool capacity, drained before the controller (like a Capacitor Bank);
    // taken out, it keeps its charge, and works as a battery item.
    static void energyDrives(GameTestHelper helper) {
        BlockPos controller = new BlockPos(0, 1, 0), bayPos = new BlockPos(2, 1, 0);
        bay(helper, controller, bayPos);
        long capacity = EnergyDrives.capacity(StorageTier.K8);
        long[] before = new long[2];
        helper.startSequence()
                .thenExecute(() -> {
                    ItemStack charged = drive(ResourceType.ENERGY, StorageTier.K8);
                    EnergyDrives.set(charged, 1_000_000);
                    helper.getBlockEntity(bayPos, DriveBayBlockEntity.class).setItem(0, charged);
                })
                // A controller block takes up to its receive rate a tick.
                .thenExecute(() -> insertEnergy(helper, controller, Direction.UP, 4_000))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(capacity == 8_192_000, "8K energy drive holds " + capacity);
                    long id = helper.getBlockEntity(controller, NetworkControllerBlockEntity.class).getStructureId();
                    NetworkSnapshot snapshot = ControllerStructures.get(helper.getLevel()).snapshot(id);
                    helper.assertTrue(snapshot.capacity() == 25_000 + capacity, "Pool capacity " + snapshot.capacity());
                    helper.assertTrue(snapshot.driveCapacity() == capacity, "Drive capacity " + snapshot.driveCapacity());
                    helper.assertTrue(snapshot.driveStored() > 900_000 && snapshot.driveStored() <= 1_000_000, "Drive stored " + snapshot.driveStored());
                    before[0] = EnergyDrives.stored(helper.getBlockEntity(bayPos, DriveBayBlockEntity.class).getItem(0));
                    before[1] = helper.getBlockEntity(controller, NetworkControllerBlockEntity.class).getEnergy();
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    long now = EnergyDrives.stored(helper.getBlockEntity(bayPos, DriveBayBlockEntity.class).getItem(0));
                    helper.assertTrue(now < before[0], "Drive not drained: " + now);
                    long controllerNow = helper.getBlockEntity(controller, NetworkControllerBlockEntity.class).getEnergy();
                    helper.assertTrue(controllerNow == before[1], "The controller paid the drain (" + before[1] + " -> " + controllerNow + ") before the drive");
                })
                .thenExecute(() -> {
                    DriveBayBlockEntity bay = helper.getBlockEntity(bayPos, DriveBayBlockEntity.class);
                    ItemStack out = bay.removeItemNoUpdate(0);
                    bay.setChanged();
                    long kept = EnergyDrives.stored(out);
                    helper.assertTrue(kept > 990_000, "Drive kept " + kept);
                    // As a battery item.
                    EnergyHandler battery = ItemAccess.forStack(out).getCapability(Capabilities.Energy.ITEM);
                    helper.assertTrue(battery != null && battery.getAmountAsLong() == kept && battery.getCapacityAsLong() == capacity,
                            "Battery " + battery);
                })
                .thenSucceed();
    }
}
