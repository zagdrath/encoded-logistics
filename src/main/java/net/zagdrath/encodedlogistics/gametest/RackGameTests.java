/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.RackTargeting;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// The Server Rack: placing it (and failing without room), breaking it whole with its devices dropping, the unit rules,
// its local geometry, and its devices on a network: the Firewall's permissions, the UPS covering and recharging, the
// Router moving items across a Segment Isolator.
final class RackGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 0);
    private static final ItemKey COBBLESTONE = ItemKey.of(new ItemStack(Items.COBBLESTONE));

    private RackGameTests() {}

    // --- Helpers ---

    // The six blocks of a rack, as placing it does; returns the master.
    private static BlockPos rack(GameTestHelper helper, BlockPos bottomFront, Direction facing) {
        BlockPos master = RackGeometry.masterPos(bottomFront, facing, RackGeometry.BOTTOM_FRONT);
        for (int index = 0; index < RackGeometry.PARTS; index++) {
            helper.setBlock(RackGeometry.partPos(master, facing, index), part(facing, index));
        }
        return master;
    }

    private static BlockState part(Direction facing, int index) {
        return ModBlocks.SERVER_RACK.get().defaultBlockState().setValue(ServerRackBlock.FACING, facing).setValue(ServerRackBlock.PART_INDEX, index)
                .setValue(ServerRackBlock.PART, index == RackGeometry.MASTER ? ServerRackBlock.Part.MASTER : ServerRackBlock.Part.DUMMY);
    }

    private static <T extends RackDevice> T install(GameTestHelper helper, BlockPos master, RackDeviceType type, int u, Class<T> kind) {
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        RackDevice device = type.create();
        rack.install(device, u, null);
        return kind.cast(device);
    }

    private static void controller(GameTestHelper helper, BlockPos pos, int energy) {
        helper.setBlock(pos, ModBlocks.NETWORK_CONTROLLER.get());
        insert(helper, pos, energy);
    }

    private static int insert(GameTestHelper helper, BlockPos pos, int amount) {
        EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), Direction.UP);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = energy.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static void cable(GameTestHelper helper, BlockPos pos) {
        NetworkCableBlock block = ModBlocks.cable(CableTier.NORMAL, CableColor.NEUTRAL).get();
        helper.setBlock(pos, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
    }

    private static void driveBay(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.DRIVE_BAY.get().defaultBlockState().setValue(DriveBayBlock.FACING, Direction.EAST));
        helper.getBlockEntity(pos, DriveBayBlockEntity.class).setItem(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
    }

    // A powered controller and a rack facing north at (3, 1, 0), cabled to it at its back (its back blocks are at z 1,
    // their back faces south). Returns the master.
    private static BlockPos networkedRack(GameTestHelper helper) {
        controller(helper, CONTROLLER, 20_000);
        BlockPos master = rack(helper, new BlockPos(3, 1, 0), Direction.NORTH);
        for (BlockPos pos : new BlockPos[] { new BlockPos(1, 1, 0), new BlockPos(1, 1, 1), new BlockPos(1, 1, 2), new BlockPos(2, 1, 2),
                new BlockPos(3, 1, 2) }) {
            cable(helper, pos);
        }
        return master;
    }

    private static NetworkStorage storage(GameTestHelper helper, BlockPos device) {
        NetworkStorage storage = ControllerStructures.get(helper.getLevel()).storageAt(helper.getLevel(), helper.absolutePos(device));
        helper.assertTrue(storage != null, "Network offline at " + device);
        return storage;
    }

    private static int itemsAround(GameTestHelper helper, BlockPos pos, Item item) {
        AABB box = new AABB(helper.absolutePos(pos)).inflate(4);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, entity -> entity.getItem().is(item)).stream()
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    // --- Tests ---

    // The item places all six blocks from the targeted block up and back, master in the middle of the front; with any of
    // them taken it places nothing. Breaking one block breaks all six and drops one rack and the devices in it.
    static void placesAndBreaks(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 0, 2), bottomFront = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Direction facing = player.getDirection().getOpposite();
        BlockPos master = RackGeometry.masterPos(bottomFront, facing, RackGeometry.BOTTOM_FRONT);
        BlockPos blocked = RackGeometry.partPos(master, facing, RackGeometry.TOP_BACK);

        helper.setBlock(blocked, Blocks.STONE);
        helper.assertFalse(place(helper, player, floor).consumesAction(), "Placed without room");
        helper.assertBlockNotPresent(ModBlocks.SERVER_RACK.get(), bottomFront);
        helper.setBlock(blocked, Blocks.AIR);

        helper.assertTrue(place(helper, player, floor).consumesAction(), "Rack not placed");
        for (int index = 0; index < RackGeometry.PARTS; index++) {
            BlockState state = helper.getBlockState(RackGeometry.partPos(master, facing, index));
            helper.assertTrue(state.is(ModBlocks.SERVER_RACK.get()) && state.getValue(ServerRackBlock.PART_INDEX) == index
                    && state.getValue(ServerRackBlock.FACING) == facing, "Part " + index + " is " + state);
        }
        helper.assertTrue(ServerRackBlock.isMaster(helper.getBlockState(master)), "Master not in the middle front");
        install(helper, master, RackDeviceType.FIREWALL, 1, FirewallDevice.class);
        install(helper, master, RackDeviceType.UPS, 10, UpsDevice.class);

        helper.getLevel().destroyBlock(helper.absolutePos(blocked), true);
        for (int index = 0; index < RackGeometry.PARTS; index++) {
            helper.assertBlockNotPresent(ModBlocks.SERVER_RACK.get(), RackGeometry.partPos(master, facing, index));
        }
        helper.assertTrue(itemsAround(helper, master, ModItems.SERVER_RACK.get()) == 1, "Not exactly one rack dropped");
        helper.assertTrue(itemsAround(helper, master, ModItems.FIREWALL.get()) == 1, "Firewall not dropped");
        helper.assertTrue(itemsAround(helper, master, ModItems.UPS.get()) == 1, "UPS not dropped");
        helper.succeed();
    }

    private static InteractionResult place(GameTestHelper helper, Player player, BlockPos floor) {
        ItemStack stack = new ItemStack(ModItems.SERVER_RACK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(floor);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false);
        return ((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
    }

    // 1U and 2U devices need their units free and inside U1-U42; the lowest fit skips taken units.
    static void unitRules(GameTestHelper helper) {
        BlockPos master = rack(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        helper.assertTrue(rack.freeUnits() == 42, "Empty rack has " + rack.freeUnits() + "U free");
        helper.assertFalse(rack.fits(42, 2), "2U fits at U42");
        helper.assertTrue(rack.fits(41, 2), "2U doesn't fit at U41");
        install(helper, master, RackDeviceType.FIREWALL, 2, FirewallDevice.class);
        helper.assertFalse(rack.fits(1, 2), "2U fits over a device");
        helper.assertTrue(rack.lowestFit(1) == 1, "Lowest 1U fit is " + rack.lowestFit(1));
        helper.assertTrue(rack.lowestFit(2) == 3, "Lowest 2U fit is " + rack.lowestFit(2));
        UpsDevice ups = install(helper, master, RackDeviceType.UPS, 3, UpsDevice.class);
        helper.assertTrue(rack.deviceAt(4) == ups && rack.deviceAt(3) == ups, "UPS doesn't take U3-U4");
        helper.assertTrue(rack.freeUnits() == 39, "Rack has " + rack.freeUnits() + "U free");
        helper.assertTrue(rack.remove(4) == ups && rack.deviceAt(3) == null, "UPS not removed from its upper unit");
        helper.succeed();
    }

    // The master's local space: units by height, and boxes turned with the facing.
    static void geometry(GameTestHelper helper) {
        helper.assertTrue(RackGeometry.unitAt(-13) == 1 && RackGeometry.unitAt(28.5) == 42 && RackGeometry.unitAt(-13.5) == 0
                && RackGeometry.unitAt(29) == 0, "Units by height are off");
        BlockPos master = new BlockPos(10, 64, 10);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // The front centre is a block's half-width in front of the master, toward the facing.
            Vec3 front = Vec3.atCenterOf(master).add(facing.getStepX() * 0.5, 0, facing.getStepZ() * 0.5);
            Vec3 local = RackGeometry.toLocal(front, master, facing);
            helper.assertTrue(Math.abs(local.z) < 1.0E-6 && Math.abs(local.x - 8) < 1.0E-6, facing + ": front at " + local);
            AABB rack = RackGeometry.bounds(master, facing);
            helper.assertTrue(rack.getXsize() * rack.getZsize() == 2 && rack.getYsize() == 3, facing + ": bounds " + rack);
            helper.assertTrue(rack.contains(Vec3.atCenterOf(master.relative(facing.getOpposite()))), facing + ": back block outside " + rack);
        }
        helper.succeed();
    }

    // Looking down into the open front at the lower of two UPSes picks it (not the one above, where the line meets the
    // rack's outer face); an empty unit is found where the line crosses the devices' front plane; a closed door picks
    // nothing.
    static void targeting(GameTestHelper helper) {
        BlockPos master = rack(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        UpsDevice lower = install(helper, master, RackDeviceType.UPS, 1, UpsDevice.class);
        install(helper, master, RackDeviceType.UPS, 3, UpsDevice.class);
        BlockPos absolute = helper.absolutePos(master);
        // Local pixels to the world (facing north, local and world axes agree).
        Function<Vec3, Vec3> world = local -> Vec3.atLowerCornerOf(absolute).add(local.scale(1.0 / 16));
        Vec3 eye = world.apply(new Vec3(8, 20, -24));
        Vec3 lowerFront = world.apply(new Vec3(8, RackGeometry.unitBottom(1) + 1, RackGeometry.DEVICE_Z0));
        Vec3 look = lowerFront.subtract(eye);
        helper.assertTrue(RackTargeting.pick(rack, Direction.NORTH, eye, look) == null, "Picked through a closed door");
        rack.setFrontOpen(true);
        // Where the line meets the outer face (z 0) it's already in a higher unit.
        double t = (0 - (-24)) / (RackGeometry.DEVICE_Z0 - (-24));
        double outerY = 20 + (RackGeometry.unitBottom(1) + 1 - 20) * t;
        helper.assertTrue(RackGeometry.unitAt(outerY) >= 3, "Test line doesn't cross the outer face higher up (U" + RackGeometry.unitAt(outerY) + ")");
        RackTargeting.Target target = RackTargeting.pick(rack, Direction.NORTH, eye, look);
        helper.assertTrue(target != null && target.device() == lower, "Picked " + (target == null ? "nothing" : "U" + target.u()));
        Vec3 emptyFront = world.apply(new Vec3(8, RackGeometry.unitBottom(20) + 0.5, RackGeometry.DEVICE_Z0));
        RackTargeting.Target empty = RackTargeting.pick(rack, Direction.NORTH, eye, emptyFront.subtract(eye));
        helper.assertTrue(empty != null && empty.u() == 20 && empty.device() == null, "Empty unit picked as " + (empty == null ? "nothing" : "U" + empty.u()));
        helper.succeed();
    }

    // Cabled at its back, the rack joins the network: each device takes a lane and comes online, and the network lists
    // the rack and its devices.
    static void onNetwork(GameTestHelper helper) {
        BlockPos master = networkedRack(helper);
        FirewallDevice firewall = install(helper, master, RackDeviceType.FIREWALL, 1, FirewallDevice.class);
        RouterDevice router = install(helper, master, RackDeviceType.ROUTER, 2, RouterDevice.class);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    helper.assertTrue(rack.isOnline(), "Rack offline");
                    helper.assertTrue(firewall.isOnline() && router.isOnline(), "Devices offline");
                    long id = helper.getBlockEntity(CONTROLLER, NetworkControllerBlockEntity.class).getStructureId();
                    NetworkSnapshot snapshot = ControllerStructures.get(helper.getLevel()).snapshot(id);
                    helper.assertTrue(snapshot.lanesUsed() == 2, "Rack uses " + snapshot.lanesUsed() + " lanes");
                    for (String item : new String[] { "server_rack", "firewall", "router" }) {
                        helper.assertTrue(snapshot.devices().stream().anyMatch(entry -> entry.item().getPath().equals(item)), item + " not listed");
                    }
                    rack.remove(1);
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertFalse(firewall.isOnline(), "Removed Firewall still online"))
                .thenSucceed();
    }

    // With a Firewall, its owner may do everything; others get the default policy unless their own setting says
    // otherwise. Without one, everyone may.
    static void firewall(GameTestHelper helper) {
        BlockPos master = networkedRack(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL), guest = helper.makeMockPlayer(GameType.SURVIVAL),
                stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(NetworkAccess.allowed(helper.getLevel(), helper.absolutePos(CONTROLLER), stranger,
                        RackPermission.BUILD), "Denied without a Firewall"))
                .thenExecute(() -> {
                    // No access by default; the guest may view and insert.
                    FirewallDevice firewall = (FirewallDevice) RackDeviceType.FIREWALL.create();
                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
                    output.store("owner", UUIDUtil.CODEC, owner.getUUID());
                    output.putString("owner_name", "Owner");
                    output.putInt("policy", FirewallDevice.Policy.DENY.ordinal());
                    ValueOutput entry = output.childrenList("players").addChild();
                    entry.store("id", UUIDUtil.CODEC, guest.getUUID());
                    entry.putString("name", "Guest");
                    entry.putIntArray("permissions", new int[] { FirewallDevice.ON, FirewallDevice.ON, FirewallDevice.INHERIT, FirewallDevice.OFF,
                            FirewallDevice.INHERIT });
                    firewall.loadSettings(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
                    helper.getBlockEntity(master, RackBlockEntity.class).install(firewall, 1, null);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockPos node = helper.absolutePos(CONTROLLER.east());
                    BlockPos rackPos = helper.absolutePos(master);
                    for (RackPermission permission : RackPermission.values()) {
                        helper.assertTrue(NetworkAccess.allowed(helper.getLevel(), rackPos, owner, permission), "Owner denied " + permission);
                        helper.assertFalse(NetworkAccess.allowed(helper.getLevel(), rackPos, stranger, permission), "Stranger allowed " + permission);
                    }
                    helper.assertTrue(NetworkAccess.allowed(helper.getLevel(), rackPos, guest, RackPermission.VIEW), "Guest can't view");
                    helper.assertTrue(NetworkAccess.allowed(helper.getLevel(), rackPos, guest, RackPermission.INSERT), "Guest can't insert");
                    helper.assertFalse(NetworkAccess.allowed(helper.getLevel(), rackPos, guest, RackPermission.EXTRACT), "Guest can extract");
                    helper.assertFalse(NetworkAccess.allowed(helper.getLevel(), rackPos, guest, RackPermission.CRAFT), "Guest can craft");
                    helper.assertTrue(NetworkAccess.allowedToBuild(helper.getLevel(), node, owner), "Owner can't build next to the network");
                    helper.assertFalse(NetworkAccess.allowedToBuild(helper.getLevel(), node, guest), "Guest can build next to the network");
                })
                .thenSucceed();
    }

    // With nothing coming in, a UPS in Online mode covers the network's drain from its battery and the controller keeps
    // its energy; while FE comes in again it's back on mains and recharges from the buffers.
    static void upsCoversAndRecharges(GameTestHelper helper) {
        BlockPos master = networkedRack(helper);
        UpsDevice ups = install(helper, master, RackDeviceType.UPS, 1, UpsDevice.class);
        long[] before = new long[2];
        helper.startSequence()
                .thenExecute(() -> ups.loadSettings(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
                        upsState(helper, 100_000))))
                .thenIdle(5)
                .thenExecute(() -> {
                    before[0] = helper.getBlockEntity(CONTROLLER, NetworkControllerBlockEntity.class).getEnergy();
                    before[1] = ups.stored();
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    long energy = helper.getBlockEntity(CONTROLLER, NetworkControllerBlockEntity.class).getEnergy();
                    helper.assertTrue(energy == before[0], "Controller drained from " + before[0] + " to " + energy + " with a UPS");
                    helper.assertTrue(ups.stored() < before[1], "UPS didn't discharge: " + ups.stored());
                    helper.assertTrue(ups.onBattery(), "UPS not on battery");
                    helper.assertTrue(ups.log().size() == 1 && ups.log().getFirst().toBattery(), "Switchover not logged");
                    before[1] = ups.stored();
                })
                // Supply for a few ticks, enough to refill the buffers past half: the UPS goes back to mains and recharges
                // from them (and back to battery when it stops).
                .thenExecuteFor(5, () -> insert(helper, CONTROLLER, 4_096))
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(ups.log().stream().anyMatch(event -> !event.toBattery()), "UPS never went back to mains");
                    helper.assertTrue(ups.stored() > before[1], "UPS didn't recharge: " + ups.stored() + " <= " + before[1]);
                })
                .thenSucceed();
    }

    // With the network's buffers full and its supply still pushing (so almost nothing gets in), the UPS stays on mains
    // and charges from the buffers; when the supply stops it goes on battery; cut off the network it's off battery.
    static void upsOnFullBuffers(GameTestHelper helper) {
        BlockPos master = networkedRack(helper);
        UpsDevice ups = install(helper, master, RackDeviceType.UPS, 1, UpsDevice.class);
        helper.startSequence()
                .thenExecuteFor(12, () -> insert(helper, CONTROLLER, 4_096))
                .thenExecuteFor(40, () -> insert(helper, CONTROLLER, 200))
                .thenExecute(() -> {
                    helper.assertFalse(ups.onBattery(), "On battery with the supply pushing into full buffers");
                    helper.assertTrue(ups.log().stream().noneMatch(UpsDevice.Event::toBattery), "Switched to battery with mains present");
                    helper.assertTrue(ups.stored() > 0, "UPS didn't charge");
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(ups.onBattery(), "Not on battery with the supply gone"))
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(3, 1, 2)), false))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertFalse(ups.onBattery(), "Still on battery off the network");
                    helper.assertTrue(ups.load() == 0, "Still loaded off the network");
                })
                .thenSucceed();
    }

    private static CompoundTag upsState(GameTestHelper helper, long stored) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        output.putInt("mode", UpsDevice.Mode.ONLINE.ordinal());
        output.putLong("stored", stored);
        return output.buildResult();
    }

    // Two networks split by a Segment Isolator, each with a Drive Bay: a Router on the first, with the second linked
    // and a route from the first to the second, moves the first's cobblestone across at its rate.
    static void routerMovesItems(GameTestHelper helper) {
        BlockPos bayA = new BlockPos(1, 1, 1), isolator = new BlockPos(2, 1, 0), cableB = new BlockPos(3, 1, 0), controllerB = new BlockPos(4, 1, 0),
                bayB = new BlockPos(3, 1, 1);
        controller(helper, CONTROLLER, 20_000);
        controller(helper, controllerB, 20_000);
        driveBay(helper, bayA);
        driveBay(helper, bayB);
        helper.setBlock(isolator, ModBlocks.SEGMENT_ISOLATOR.get().defaultBlockState().setValue(SegmentIsolatorBlock.AXIS, Direction.Axis.X));
        // Facing south at (1, 1, 3): its back blocks are at z 2, their back faces against the first bay.
        BlockPos master = rack(helper, new BlockPos(1, 1, 3), Direction.SOUTH);
        cable(helper, CONTROLLER.east());
        cable(helper, cableB);
        RouterDevice router = install(helper, master, RackDeviceType.ROUTER, 1, RouterDevice.class);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(router.isOnline(), "Router offline");
                    storage(helper, bayA).insert(COBBLESTONE, 40, false);
                    helper.assertTrue(router.link(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(cableB))) == RouterDevice.LinkResult.LINKED,
                            "Segment not linked");
                    helper.assertTrue(router.addRoute(0, 1, ItemStack.EMPTY), "Route not added");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    long moved = storage(helper, bayB).count(COBBLESTONE);
                    helper.assertTrue(moved > 0 && moved <= router.rate(), "Moved " + moved + " in the first second (rate " + router.rate() + ")");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper, bayB).count(COBBLESTONE) == 40, "Not all moved: " + storage(helper, bayB).count(COBBLESTONE));
                    helper.assertTrue(storage(helper, bayA).count(COBBLESTONE) == 0, "Some left behind");
                })
                .thenSucceed();
    }
}
