/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.device.ComputeServerDevice;
import net.zagdrath.encodedlogistics.rack.device.FabricationServerDevice;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.rack.device.L3SwitchDevice;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;
import net.zagdrath.encodedlogistics.rack.device.MonitoringServerDevice;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.rack.device.SanDevice;
import net.zagdrath.encodedlogistics.rack.device.SwitchDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Rack devices, batch 2: switches pooling lanes, the NAS and SAN as storage, the rack's Scheduler crafting through a
// Fabrication Server, a device serving a segment and an L3 Switch routing to it, the Router linking two unconnected
// networks, and the Monitoring Server sampling.
final class Rack2GameTests {
    private static final ItemKey COBBLESTONE = ItemKey.of(new ItemStack(Items.COBBLESTONE)), LOG = ItemKey.of(new ItemStack(Items.OAK_LOG)),
            PLANKS = ItemKey.of(new ItemStack(Items.OAK_PLANKS));

    private Rack2GameTests() {}

    // --- Helpers ---

    private static ItemStack drive() {
        return new ItemStack(ModItems.storageDrive(StorageTier.K8).get());
    }

    // A storage device with a drive in its first slot (and settings, if any).
    private static <T extends StorageDevice> T storageDevice(GameTestHelper helper, BlockPos master, RackDeviceType type, int u, Class<T> kind,
            int priority, int access) {
        T device = RackGameTests.install(helper, master, type, u, kind);
        TagValueOutput settings = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        settings.putInt("priority", priority);
        settings.putInt("access", access);
        device.loadSettings(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), settings.buildResult()));
        device.items().set(0, drive());
        device.itemsChanged();
        return device;
    }

    private static NetworkRef network(GameTestHelper helper, BlockPos node) {
        NetworkRef ref = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(node));
        helper.assertTrue(ref != null, "No network at " + node);
        return ref;
    }

    private static NetworkStorage storageOf(GameTestHelper helper, NetworkRef network) {
        NetworkStorage storage = ControllerStructures.storageOf(helper.getLevel().getServer(), network);
        helper.assertTrue(storage != null, "Network " + network + " down");
        return storage;
    }

    private static int lanesUsed(GameTestHelper helper) {
        long id = helper.getBlockEntity(RackGameTests.CONTROLLER, NetworkControllerBlockEntity.class).getStructureId();
        NetworkSnapshot snapshot = ControllerStructures.get(helper.getLevel()).snapshot(id);
        return snapshot.lanesUsed();
    }

    // --- Tests ---

    // A switch pools its lanes for the rack's other devices: the network carries only its uplink. Without it, each
    // device takes a network lane again.
    static void lanePool(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.L2_SWITCH_24, 1, SwitchDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.FIREWALL, 2, FirewallDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.ROUTER, 3, RouterDevice.class);
        NasDevice nas = RackGameTests.install(helper, master, RackDeviceType.NAS, 4, NasDevice.class);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    helper.assertTrue(rack.lanes().pooled() == 3, "Pooled " + rack.lanes().pooled());
                    helper.assertTrue(lanesUsed(helper) == 1, "Network lanes " + lanesUsed(helper));
                    helper.assertTrue(nas.isOnline(), "Pooled NAS offline");
                    rack.remove(1);
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(lanesUsed(helper) == 3, "Network lanes without the switch " + lanesUsed(helper)))
                .thenSucceed();
    }

    // A NAS's drives are network storage; read-only, nothing goes in. A SAN without a transceiver is a fault and its
    // drives aren't reachable; with one they are.
    static void storageDevices(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        StorageDevice nas = storageDevice(helper, master, RackDeviceType.NAS, 1, NasDevice.class, 0, StorageDevice.READ_WRITE);
        SanDevice san = storageDevice(helper, master, RackDeviceType.SAN, 3, SanDevice.class, 0, StorageDevice.READ_WRITE);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    NetworkStorage storage = storageOf(helper, network(helper, master));
                    helper.assertTrue(storage.insert(COBBLESTONE, 64, false) == 64, "NAS didn't take cobblestone");
                    helper.assertTrue(nas.capacity()[0] > 0, "NAS drive still empty");
                    helper.assertTrue(san.status() == RackDeviceInfo.Status.FAULT, "SAN without uplink is " + san.status());
                    helper.assertTrue(san.views(helper.getLevel().getServer()).isEmpty(), "SAN drives reachable without uplink");
                    san.items().set(SanDevice.DRIVES, new ItemStack(ModItems.OPTICAL_TRANSCEIVER.get()));
                    san.itemsChanged();
                    helper.assertTrue(san.status() == RackDeviceInfo.Status.ONLINE, "SAN with uplink is " + san.status());
                    helper.assertTrue(san.views(helper.getLevel().getServer()).size() == 1, "SAN drive not reachable with uplink");
                    TagValueOutput readOnly = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
                    readOnly.putInt("access", StorageDevice.READ);
                    nas.loadSettings(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), readOnly.buildResult()));
                    san.loadSettings(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), readOnly.buildResult()));
                    helper.assertTrue(storageOf(helper, network(helper, master)).insert(COBBLESTONE, 1, true) == 0, "Read-only storage took items");
                    helper.assertTrue(storageOf(helper, network(helper, master)).count(COBBLESTONE) == 64, "Read-only storage hid its items");
                })
                .thenSucceed();
    }

    // A rack with a Compute and a Memory Server is a Scheduler on its network: a job started on it runs its crafts on the
    // rack's Fabrication Server and puts the result in the network.
    static void rackScheduler(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        ComputeServerDevice compute = RackGameTests.install(helper, master, RackDeviceType.COMPUTE_SERVER, 1, ComputeServerDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.MEMORY_SERVER, 3, MemoryServerDevice.class);
        FabricationServerDevice fabrication = RackGameTests.install(helper, master, RackDeviceType.FABRICATION_SERVER, 4, FabricationServerDevice.class);
        storageDevice(helper, master, RackDeviceType.NAS, 6, NasDevice.class, 0, StorageDevice.READ_WRITE);
        ItemStack card = new ItemStack(ModItems.ENCODED_SCHEMATIC_CRAFTING.get());
        card.set(ModDataComponents.SCHEMATIC.get(), Schematic.of(Schematic.Kind.CRAFTING, List.of(new ItemStack(Items.OAK_LOG)),
                List.of(new ItemStack(Items.OAK_PLANKS, 4))));
        fabrication.items().set(0, card);
        fabrication.itemsChanged();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    BlockPos device = helper.absolutePos(master);
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    helper.assertTrue(rack.scheduler().active(), "Rack isn't a Scheduler");
                    helper.assertTrue(rack.scheduler().badges(compute) && rack.scheduler().badges(fabrication), "Servers without the badge");
                    RackGameTests.storage(helper, master).insert(LOG, 2, false);
                    List<JobHost> schedulers = CraftRequests.schedulers(level, device);
                    helper.assertTrue(schedulers.contains(rack.scheduler()), "Rack Scheduler not offered: " + schedulers.size());
                    CraftPlanner.Plan plan = CraftRequests.plan(level, device, PLANKS, 8);
                    helper.assertTrue(plan != null && plan.complete(), "Plan: " + plan);
                    helper.assertTrue(CraftRequests.start(level, device, plan, rack.scheduler()) != null, "Job didn't start");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    helper.assertTrue(RackGameTests.storage(helper, master).count(PLANKS) == 8,
                            "Network has " + RackGameTests.storage(helper, master).count(PLANKS) + " planks");
                    helper.assertTrue(rack.scheduler().jobs().isEmpty(), "Job not finished");
                })
                .thenSucceed();
    }

    // Two networks split by a Segment Isolator. A rack on the first with an L3 Switch learns the second as a segment; a
    // NAS set to that segment is the second network's storage (not the first's); and a route moves the first network's
    // cobblestone into it.
    static void segments(GameTestHelper helper) {
        BlockPos bayA = new BlockPos(1, 1, 1), isolator = new BlockPos(2, 1, 0), cableB = new BlockPos(3, 1, 0), controllerB = new BlockPos(4, 1, 0);
        RackGameTests.controller(helper, RackGameTests.CONTROLLER, 20_000);
        RackGameTests.controller(helper, controllerB, 20_000);
        RackGameTests.driveBay(helper, bayA);
        helper.setBlock(isolator, ModBlocks.SEGMENT_ISOLATOR.get().defaultBlockState().setValue(SegmentIsolatorBlock.AXIS, Direction.Axis.X));
        // Facing south at (1, 1, 3): its back blocks are at z 2, their back faces against the first network's bay.
        BlockPos master = RackGameTests.rack(helper, new BlockPos(1, 1, 3), Direction.SOUTH);
        RackGameTests.cable(helper, RackGameTests.CONTROLLER.east());
        RackGameTests.cable(helper, cableB);
        L3SwitchDevice l3 = RackGameTests.install(helper, master, RackDeviceType.L3_SWITCH, 1, L3SwitchDevice.class);
        NasDevice nas = storageDevice(helper, master, RackDeviceType.NAS, 2, NasDevice.class, 10, StorageDevice.READ_WRITE);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    helper.assertTrue(rack.toggleSegment(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(cableB)))
                            == RackBlockEntity.SegmentResult.LINKED, "Segment not linked");
                    rack.cycleSegment(nas.u(), 1);
                    helper.assertTrue(nas.segment() == 1, "NAS still on segment " + nas.segment());
                    NetworkRef b = network(helper, cableB);
                    helper.assertTrue(b.equals(rack.network(nas)), "NAS serves " + rack.network(nas));
                    helper.assertTrue(ControllerStructures.rackDevicesServing(helper.getLevel().getServer(), network(helper, bayA)).stream()
                            .noneMatch(device -> device == nas), "NAS still serves the first network");
                    RackGameTests.storage(helper, bayA).insert(COBBLESTONE, 20, false);
                    helper.assertTrue(l3.addRoute(0, 1, ItemRouting.everything()), "Route not added");
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(storageOf(helper, network(helper, cableB)).count(COBBLESTONE) == 20,
                            "Second network has " + storageOf(helper, network(helper, cableB)).count(COBBLESTONE));
                    helper.assertTrue(nas.capacity()[0] > 0, "Routed items not in the NAS");
                })
                .thenSucceed();
    }

    // The Router links a network nothing joins to its own (by its controller): a route moves items across.
    static void routerWan(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        BlockPos controllerB = new BlockPos(7, 1, 4), bayB = new BlockPos(7, 1, 5);
        RackGameTests.controller(helper, controllerB, 20_000);
        RackGameTests.driveBay(helper, bayB);
        storageDevice(helper, master, RackDeviceType.NAS, 1, NasDevice.class, 0, StorageDevice.READ_WRITE);
        RouterDevice router = RackGameTests.install(helper, master, RackDeviceType.ROUTER, 3, RouterDevice.class);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(router.link(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(controllerB))) == RouterDevice.LinkResult.LINKED,
                            "Network not linked");
                    helper.assertTrue(router.network(helper.getLevel().getServer(), 1) != null, "Linked network not found");
                    RackGameTests.storage(helper, master).insert(COBBLESTONE, 10, false);
                    helper.assertTrue(router.addRoute(0, 1, ItemRouting.everything()), "Route not added");
                })
                .thenIdle(25)
                .thenExecute(() -> helper.assertTrue(RackGameTests.storage(helper, bayB).count(COBBLESTONE) == 10,
                        "Linked network has " + RackGameTests.storage(helper, bayB).count(COBBLESTONE)))
                .thenSucceed();
    }

    // A route's filter: a new route (an empty allow list) moves nothing; an allow list moves only its items; a deny list
    // everything but its items. Routes saved with the old single filter load as an allow list of it, or (none) as
    // everything.
    static void routeFilters(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        BlockPos controllerB = new BlockPos(7, 1, 4), bayB = new BlockPos(7, 1, 5);
        RackGameTests.controller(helper, controllerB, 20_000);
        RackGameTests.driveBay(helper, bayB);
        storageDevice(helper, master, RackDeviceType.NAS, 1, NasDevice.class, 0, StorageDevice.READ_WRITE);
        RouterDevice router = RackGameTests.install(helper, master, RackDeviceType.ROUTER, 3, RouterDevice.class);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(router.link(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(controllerB))) == RouterDevice.LinkResult.LINKED,
                            "Network not linked");
                    NetworkStorage a = RackGameTests.storage(helper, master);
                    a.insert(COBBLESTONE, 10, false);
                    a.insert(LOG, 10, false);
                    a.insert(PLANKS, 10, false);
                    helper.assertTrue(router.addRoute(0, 1, new PartFilter()), "Route not added");
                    helper.assertTrue(router.routes().getFirst().idle(), "New route not idle");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    NetworkStorage b = RackGameTests.storage(helper, bayB);
                    helper.assertTrue(b.count(COBBLESTONE) + b.count(LOG) + b.count(PLANKS) == 0, "A new route moved items");
                    helper.assertTrue(ItemRouting.setEntry(router.routes(), 0, new ItemStack(Items.OAK_LOG)), "Entry not set");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    NetworkStorage b = RackGameTests.storage(helper, bayB);
                    helper.assertTrue(b.count(LOG) == 10 && b.count(COBBLESTONE) == 0 && b.count(PLANKS) == 0,
                            "Allow list moved logs " + b.count(LOG) + ", cobblestone " + b.count(COBBLESTONE) + ", planks " + b.count(PLANKS));
                    ItemRouting.setEntry(router.routes(), 0, new ItemStack(Items.COBBLESTONE));
                    ItemRouting.toggleOption(router.routes(), ItemRouting.OPTION_DENY);
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    NetworkStorage b = RackGameTests.storage(helper, bayB);
                    helper.assertTrue(b.count(PLANKS) == 10 && b.count(COBBLESTONE) == 0,
                            "Deny list moved planks " + b.count(PLANKS) + ", cobblestone " + b.count(COBBLESTONE));

                    TagValueOutput old = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
                    ValueOutput.ValueOutputList list = old.childrenList("routes");
                    ValueOutput filtered = list.addChild();
                    filtered.putInt("source", 0);
                    filtered.putInt("dest", 1);
                    filtered.store("filter", ItemStack.CODEC, new ItemStack(Items.OAK_LOG));
                    ValueOutput any = list.addChild();
                    any.putInt("source", 1);
                    any.putInt("dest", 0);
                    List<ItemRouting.Route> loaded = ItemRouting.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
                            old.buildResult()), "routes", 2, 8);
                    helper.assertTrue(loaded.size() == 2, "Old routes: " + loaded.size());
                    helper.assertTrue(loaded.get(0).matches(LOG) && !loaded.get(0).matches(COBBLESTONE), "Old filtered route");
                    helper.assertTrue(loaded.get(1).matches(LOG) && loaded.get(1).matches(COBBLESTONE), "Old any-item route");

                    TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
                    ItemRouting.save(saved, "routes", router.routes());
                    ItemRouting.Route reloaded = ItemRouting.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
                            saved.buildResult()), "routes", 2, 8).getFirst();
                    helper.assertTrue(reloaded.filter().deny() && !reloaded.matches(COBBLESTONE) && reloaded.matches(PLANKS), "Saved deny list");
                })
                .thenSucceed();
    }

    // Copies of one drive (creative pick-block copies share the drive's id, so its contents) count once on a network,
    // whether they're in a NAS, a SAN or a Drive Bay.
    static void copiedDrives(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        BlockPos bay = new BlockPos(1, 2, 1);
        ItemStack original = drive();
        original.set(ModDataComponents.DRIVE_ID.get(), UUID.randomUUID());
        NasDevice nas = RackGameTests.install(helper, master, RackDeviceType.NAS, 1, NasDevice.class);
        for (int slot = 0; slot < 3; slot++) {
            nas.items().set(slot, original.copy());
        }
        nas.itemsChanged();
        RackGameTests.driveBay(helper, bay);
        helper.getBlockEntity(bay, DriveBayBlockEntity.class).setItem(0, original.copy());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    NetworkStorage storage = storageOf(helper, network(helper, master));
                    helper.assertTrue(storage.insert(COBBLESTONE, 192, false) == 192, "Not all inserted");
                    helper.assertTrue(storageOf(helper, network(helper, master)).count(COBBLESTONE) == 192,
                            "Network shows " + storageOf(helper, network(helper, master)).count(COBBLESTONE) + " of 192");
                    helper.assertTrue(storageOf(helper, network(helper, master)).list().get(COBBLESTONE) == 192, "Listed more than 192");
                })
                .thenSucceed();
    }

    // The Monitoring Server samples its network once a second: items moved and the drain.
    static void monitoring(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        storageDevice(helper, master, RackDeviceType.NAS, 1, NasDevice.class, 0, StorageDevice.READ_WRITE);
        MonitoringServerDevice monitor = RackGameTests.install(helper, master, RackDeviceType.MONITORING_SERVER, 3, MonitoringServerDevice.class);
        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> RackGameTests.storage(helper, master).insert(COBBLESTONE, 30, false))
                .thenIdle(21)
                .thenExecute(() -> {
                    // The sample after the insert has it (a later one may already show none).
                    float[] minute = monitor.series(MonitoringServerDevice.ITEMS, MonitoringServerDevice.MINUTE);
                    float peak = 0;
                    for (float value : minute) {
                        peak = Math.max(peak, value);
                    }
                    helper.assertTrue(minute.length == 60 && peak == 30 * 60, "Item flow peak " + peak + " items/min");
                    helper.assertTrue(minute[59] == monitor.now(MonitoringServerDevice.ITEMS), "Series doesn't end with now");
                    helper.assertTrue(monitor.now(MonitoringServerDevice.ENERGY) > 0, "No energy use sampled");
                })
                .thenSucceed();
    }
}
