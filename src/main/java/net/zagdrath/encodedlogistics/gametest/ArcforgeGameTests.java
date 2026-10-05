/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.AccessPointBlock;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.display.SmallWirelessBridgeBlock;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.item.LinkCardItem;
import net.zagdrath.encodedlogistics.machine.MachineBridge;
import net.zagdrath.encodedlogistics.machine.MachineBridges;
import net.zagdrath.encodedlogistics.machine.MachineInfo;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.RackScheduler;
import net.zagdrath.encodedlogistics.rack.device.ComputeServerDevice;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.StorageTier;
import net.zagdrath.encodedlogistics.wireless.Wireless;

// The Small Wireless Bridge on an Arcforge Arc Crusher (registered only while the Arcforge integration is on: run with
// -Parcforge_jar=...). On RackGameTests' networked rack with a Wireless Controller (unit 10), a Firewall denying everyone
// (unit 12), an Access Point cabled at AP and the crusher at MACHINE, on no cable. For crafting, a rack Scheduler
// (Compute and Memory Servers, a NAS) and a Gateway at GATEWAY with a bone -> 5 bone meal schematic and nothing beside it;
// the crusher runs on power from the network. Uses Arcforge only through Encoded
// Logistics' own MachineAccess and the block's registry id, so this class loads without Arcforge.
final class ArcforgeGameTests {
    static final Identifier ARC_CRUSHER = Identifier.fromNamespaceAndPath("arcforge", "arc_crusher");
    static final BlockPos AP = new BlockPos(1, 2, 2), MACHINE = new BlockPos(6, 1, 4), CHEST = new BlockPos(6, 1, 6), GATEWAY = new BlockPos(0, 1, 1);
    static final int CONTROLLER_UNIT = 10;
    static final ItemKey BONE = ItemKey.of(new ItemStack(Items.BONE)), BONE_MEAL = ItemKey.of(new ItemStack(Items.BONE_MEAL));

    private ArcforgeGameTests() {}

    // --- Rig ---

    static Block block(Identifier id) {
        return BuiltInRegistries.BLOCK.getValue(id);
    }

    // The rack and its devices, the Access Point and the crusher. Returns the rack's master.
    static BlockPos rig(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.WIRELESS_CONTROLLER, CONTROLLER_UNIT, WirelessControllerDevice.class);
        helper.setBlock(AP, ModBlocks.ACCESS_POINT.get().defaultBlockState().setValue(AccessPointBlock.FACING, Direction.UP));
        helper.setBlock(MACHINE, block(ARC_CRUSHER));
        return master;
    }

    static WirelessControllerDevice controller(GameTestHelper helper, BlockPos master) {
        return (WirelessControllerDevice) helper.getBlockEntity(master, RackBlockEntity.class).deviceAt(CONTROLLER_UNIT);
    }

    static FirewallDevice firewall(GameTestHelper helper, BlockPos master) {
        FirewallDevice firewall = RackGameTests.install(helper, master, RackDeviceType.FIREWALL, 12, FirewallDevice.class);
        firewall.setPolicy(FirewallDevice.Policy.DENY);
        return firewall;
    }

    static void grant(FirewallDevice firewall, ServerPlayer player, RackPermission permission, byte value) {
        firewall.setPermission(player.getUUID(), player.getGameProfile().name(), permission, value);
    }

    // A mock player in survival (so items used up are).
    @SuppressWarnings("removal")
    static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    static NetworkRef network(GameTestHelper helper, BlockPos master) {
        NetworkRef network = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(master));
        helper.assertTrue(network != null, "Rack not on a network");
        return network;
    }

    static @org.jspecify.annotations.Nullable MachineBridge bridge(GameTestHelper helper, BlockPos pos) {
        return MachineBridges.at(helper.getLevel(), helper.absolutePos(pos));
    }

    static MachineBridge requireBridge(GameTestHelper helper, BlockPos pos) {
        MachineBridge bridge = bridge(helper, pos);
        helper.assertTrue(bridge != null, "No Small Wireless Bridge at " + pos);
        return bridge;
    }

    // A use of the stack in hand on a block's face, as onItemUseFirst gets it (before the block's own use).
    static void use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos relative, Direction face) {
        BlockPos at = helper.absolutePos(relative);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getItem().onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).relative(face, 0.5), face, at, false)));
    }

    // A Link Card taken from the controller's unit through the rack's open front.
    static ItemStack takeController(GameTestHelper helper, ServerPlayer player, BlockPos master, WirelessControllerDevice controller) {
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        rack.setFrontOpen(true);
        Direction facing = rack.facing();
        Vec3 center = RackGeometry.toWorld(RackGeometry.deviceBox(controller.u(), controller.size()), rack.getBlockPos(), facing).getCenter();
        Vec3 eye = center.relative(facing, 1.5);
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, center);
        ItemStack card = new ItemStack(ModItems.LINK_CARD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        card.getItem().onItemUseFirst(card, new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(center.relative(facing, 0.4), facing,
                rack.getBlockPos(), false)));
        rack.setFrontOpen(false);
        return card;
    }

    static int bridgeItemsAround(GameTestHelper helper, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2)).stream()
                .filter(entity -> entity.getItem().is(ModItems.SMALL_WIRELESS_BRIDGE.get())).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    // --- Tests ---

    // Attaching: only on a machine, one per machine, the item used up. Adopting it with a Link Card needs build on the
    // network (a stranger is refused, a trusted player links it); then it's a named one-lane device on the network
    // (ARCCRU01, listed as a Machine), online with its LED lit, and its popup has the machine's lines. Unlinked at the
    // controller it leaves the network; the machine broken, it drops and its controller forgets it.
    static void attachAdoptAndBreak(GameTestHelper helper) {
        BlockPos master = rig(helper);
        FirewallDevice firewall = firewall(helper, master);
        ServerPlayer stranger = player(helper), trusted = player(helper);
        grant(firewall, trusted, RackPermission.BUILD, FirewallDevice.ON);
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ItemStack bridges = new ItemStack(ModItems.SMALL_WIRELESS_BRIDGE.get(), 3);
                    use(helper, stranger, bridges, CHEST, Direction.UP);
                    helper.assertTrue(bridge(helper, CHEST) == null && bridges.getCount() == 3, "A bridge went on a chest");
                    use(helper, stranger, bridges, MACHINE, Direction.NORTH);
                    MachineBridge bridge = requireBridge(helper, MACHINE);
                    helper.assertTrue(bridge.face() == Direction.NORTH && bridges.getCount() == 2, "Face " + bridge.face() + ", left " + bridges.getCount());
                    helper.assertTrue(bridge.typeCode().equals("ARCCRU") && bridge.shown().getString().equals("Arc Crusher"),
                            "Type " + bridge.typeCode() + ", name " + bridge.shown().getString());
                    use(helper, stranger, bridges, MACHINE, Direction.EAST);
                    helper.assertTrue(requireBridge(helper, MACHINE).face() == Direction.NORTH && bridges.getCount() == 2, "A second bridge went on");
                    helper.assertTrue(bridge.led() == SmallWirelessBridgeBlock.State.UNLINKED, "LED " + bridge.led());

                    WirelessControllerDevice controller = controller(helper, master);
                    ItemStack card = takeController(helper, trusted, master, controller);
                    helper.assertTrue(LinkCardItem.address(card) != null, "Trusted player couldn't take the controller");
                    use(helper, stranger, card, MACHINE, Direction.NORTH);
                    helper.assertTrue(bridge.link() == null, "A stranger adopted the bridge into the denying network");
                    use(helper, trusted, card, MACHINE, Direction.NORTH);
                    helper.assertTrue(bridge.link() != null && controller.lists(bridge.self()), "Trusted player couldn't adopt the bridge");
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    MachineBridge bridge = requireBridge(helper, MACHINE);
                    NetworkRef network = network(helper, master);
                    helper.assertTrue(Wireless.problem(helper.getLevel().getServer(), bridge) == Wireless.Problem.NONE,
                            "Problem " + Wireless.problem(helper.getLevel().getServer(), bridge));
                    helper.assertTrue(bridge.isOnline() && network.equals(ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(MACHINE))),
                            "Bridge not online on the controller's network");
                    var now = bridge.info(helper.getLevel().getServer());
                    helper.assertTrue(bridge.led() == SmallWirelessBridgeBlock.State.LINKED, "LED " + bridge.led() + ", machine "
                            + (now == null ? "missing" : now.status() + " (" + now.reason().getString() + ")"));
                    List<ElclDevices.Device> devices = ElclDevices.list(helper.getLevel().getServer(), network);
                    helper.assertTrue(devices.stream().anyMatch(device -> device.name().equals("ARCCRU01")), "Names " + devices.stream().map(ElclDevices.Device::name).toList());
                    helper.assertTrue(bridge.deviceName().equals("ARCCRU01"), "Bridge's name " + bridge.deviceName());
                    helper.assertTrue(ControllerStructures.deviceRows(helper.getLevel().getServer(), network).stream()
                            .anyMatch(row -> row.type().equals("Machine") && row.name().getString().equals("Arc Crusher") && row.lanes() == 1),
                            "No Machine row");
                    RackDeviceInfo info = bridge.describe(helper.getLevel().getServer());
                    helper.assertTrue(info.name().getString().equals("Arc Crusher") && info.lines().stream()
                            .anyMatch(line -> line.label().getString().equals("Energy")), "Popup " + info);
                    WirelessControllerDevice controller = controller(helper, master);
                    controller.unlink(controller.clients().indexOf(new WirelessControllerDevice.Client(bridge.self(), bridge.wirelessKind())));
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    MachineBridge bridge = requireBridge(helper, MACHINE);
                    helper.assertTrue(bridge.link() == null && !bridge.isOnline(), "Still linked after unlinking");
                    helper.assertTrue(ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(MACHINE)) == null, "Still on the network");
                    helper.assertTrue(bridge.led() == SmallWirelessBridgeBlock.State.UNLINKED, "LED " + bridge.led());
                    helper.assertTrue(controller(helper, master).link(bridge), "Couldn't link again");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(requireBridge(helper, MACHINE).isOnline(), "Not online after linking again");
                    helper.setBlock(MACHINE, Blocks.AIR);
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertTrue(bridge(helper, MACHINE) == null, "The bridge outlived its machine");
                    helper.assertTrue(bridgeItemsAround(helper, MACHINE) == 1, "Dropped " + bridgeItemsAround(helper, MACHINE));
                    helper.assertTrue(controller(helper, master).clients().isEmpty(), "Controller still lists it");
                })
                .thenSucceed();
    }

    // Sneak-use with an empty hand on its face takes a bridge off into the hand; on another face it doesn't.
    static void takeOff(GameTestHelper helper) {
        rig(helper);
        ServerPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    use(helper, player, new ItemStack(ModItems.SMALL_WIRELESS_BRIDGE.get()), MACHINE, Direction.UP);
                    requireBridge(helper, MACHINE);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setShiftKeyDown(true);
                    BlockPos at = helper.absolutePos(MACHINE);
                    for (Direction face : new Direction[] { Direction.SOUTH, Direction.UP }) {
                        net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, at,
                                new BlockHitResult(Vec3.atCenterOf(at).relative(face, 0.5), face, at, false));
                        helper.assertTrue((bridge(helper, MACHINE) == null) == (face == Direction.UP), "Taking off from " + face);
                    }
                    player.setShiftKeyDown(false);
                    helper.assertTrue(player.getInventory().countItem(ModItems.SMALL_WIRELESS_BRIDGE.get()) == 1, "Not back in the hand");
                })
                .thenSucceed();
    }

    // The level's bridges saved and loaded (as a reload does): the bridge is still there, on the same face, linked,
    // named, and back online on its network.
    static void survivesReload(GameTestHelper helper) {
        BlockPos master = rig(helper);
        ServerPlayer player = player(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    use(helper, player, new ItemStack(ModItems.SMALL_WIRELESS_BRIDGE.get()), MACHINE, Direction.WEST);
                    helper.assertTrue(controller(helper, master).link(requireBridge(helper, MACHINE)), "Not linked");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(requireBridge(helper, MACHINE).isOnline(), "Not online before saving");
                    reload(helper.getLevel());
                    ControllerStructures.get(helper.getLevel()).markTopologyChanged();
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    MachineBridge bridge = requireBridge(helper, MACHINE);
                    helper.assertTrue(bridge.face() == Direction.WEST && bridge.link() != null && bridge.deviceName().equals("ARCCRU01"),
                            "Loaded as face " + bridge.face() + ", link " + bridge.link() + ", name " + bridge.deviceName());
                    helper.assertTrue(controller(helper, master).lists(bridge.self()), "Controller lost it");
                    helper.assertTrue(bridge.isOnline(), "Not online after loading");
                })
                .thenSucceed();
    }

    // Keeps the network's controller full (it takes 4,096 FE a tick), as a generator would.
    static void powered(GameTestHelper helper) {
        helper.onEachTick(() -> RackGameTests.insert(helper, RackGameTests.CONTROLLER, 4_096));
    }

    // The rig with a rack Scheduler and the Gateway (and its schematic). Returns the rack's master.
    static BlockPos craftingRig(GameTestHelper helper) {
        BlockPos master = rig(helper);
        RackGameTests.install(helper, master, RackDeviceType.COMPUTE_SERVER, 1, ComputeServerDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.MEMORY_SERVER, 3, MemoryServerDevice.class);
        NasDevice nas = RackGameTests.install(helper, master, RackDeviceType.NAS, 6, NasDevice.class);
        nas.items().set(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        nas.itemsChanged();
        helper.setBlock(GATEWAY, ModBlocks.GATEWAY.get());
        ItemStack schematic = new ItemStack(ModItems.ENCODED_SCHEMATIC_PROCESSING.get());
        schematic.set(ModDataComponents.SCHEMATIC.get(), Schematic.of(Schematic.Kind.PROCESSING, List.of(new ItemStack(Items.BONE)),
                List.of(new ItemStack(Items.BONE_MEAL, 5))));
        helper.getBlockEntity(GATEWAY, GatewayBlockEntity.class).schematicSlots().set(0, schematic);
        return master;
    }

    // A bridge on the crusher's top, linked to the controller.
    static MachineBridge attachAndLink(GameTestHelper helper, BlockPos master) {
        use(helper, player(helper), new ItemStack(ModItems.SMALL_WIRELESS_BRIDGE.get()), MACHINE, Direction.UP);
        MachineBridge bridge = requireBridge(helper, MACHINE);
        helper.assertTrue(controller(helper, master).link(bridge), "Not linked");
        return bridge;
    }

    // A Processing Schematic job through a Gateway whose only machine is the crusher, over the air (the bridge's Gateway
    // setting): the Gateway puts the bone in through the crusher's input, the crusher runs, the Gateway takes the bone
    // meal out of its output, and the job finishes with the bone meal in the network.
    static void gatewayJob(GameTestHelper helper) {
        BlockPos master = craftingRig(helper);
        powered(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    MachineBridge bridge = attachAndLink(helper, master);
                    bridge.setGateway(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(GATEWAY)));
                    // The crusher runs on the network's FE (it takes 200 FE/t; an operation is 4,000).
                    bridge.setPowerFromNetwork(true);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    MachineBridge linked = requireBridge(helper, MACHINE);
                    helper.assertTrue(linked.isOnline(), "Bridge not online: " + Wireless.problem(helper.getLevel().getServer(), linked) + ", network "
                            + ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(MACHINE)) + ", rack " + network(helper, master)
                            + ", admitted " + controller(helper, master).admittedCount() + ", APs " + controller(helper, master).accessPointsOnline());
                    RackGameTests.storage(helper, master).insert(BONE, 1, false);
                    BlockPos device = helper.absolutePos(master);
                    CraftPlanner.Plan plan = CraftRequests.plan(helper.getLevel(), device, BONE_MEAL, 5);
                    helper.assertTrue(plan != null && plan.complete(), "Plan: " + plan);
                    RackScheduler scheduler = helper.getBlockEntity(master, RackBlockEntity.class).scheduler();
                    helper.assertTrue(CraftRequests.start(helper.getLevel(), device, plan, scheduler, CraftRequests.Requester.NONE) != null,
                            "Job didn't start");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    MachineInfo info = requireBridge(helper, MACHINE).info(helper.getLevel().getServer());
                    helper.assertTrue(info != null && info.status() == MachineInfo.State.RUNNING, "Crusher " + (info == null ? "missing" : info.status()));
                    helper.assertTrue(RackGameTests.storage(helper, master).count(BONE) == 0, "The bone is still in the network");
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(master, RackBlockEntity.class).scheduler().jobs().isEmpty(),
                        "Job still running"))
                .thenExecute(() -> {
                    long meal = RackGameTests.storage(helper, master).count(BONE_MEAL);
                    helper.assertTrue(meal == 5, "Network has " + meal + " bone meal");
                    MachineInfo info = requireBridge(helper, MACHINE).info(helper.getLevel().getServer());
                    helper.assertTrue(info != null && info.statistics().operations() == 1,
                            "Operations " + (info == null ? "?" : info.statistics().operations()));
                })
                .thenSucceed();
    }

    // Power from network: the empty crusher fills from the network, the network never below its reserve; turned off, it
    // stops.
    static void powerFromNetwork(GameTestHelper helper) {
        BlockPos master = rig(helper);
        powered(helper);
        long[] before = new long[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> attachAndLink(helper, master).setPowerFromNetwork(true))
                .thenIdle(40)
                .thenExecute(() -> {
                    MachineBridge bridge = requireBridge(helper, MACHINE);
                    MachineInfo info = bridge.info(helper.getLevel().getServer());
                    var handler = MachineBridges.energy(helper.getLevel(), bridge);
                    NetworkSnapshot snapshot = ControllerStructures.snapshotOf(helper.getLevel().getServer(), network(helper, master));
                    helper.assertTrue(info != null && info.energy().isPresent() && info.energy().get().stored() > 0, "Crusher has no FE: handler "
                            + (handler == null ? "none" : handler.getAmountAsLong() + "/" + handler.getCapacityAsLong()) + ", powered " + bridge.powered()
                            + ", online " + bridge.isOnline() + ", power " + bridge.powerFromNetwork() + ", network " + snapshot.stored() + "/" + snapshot.capacity()
                            + " " + snapshot.status());
                    NetworkSnapshot network = ControllerStructures.snapshotOf(helper.getLevel().getServer(), network(helper, master));
                    double reserve = Math.floor(network.capacity() * Config.MACHINE_POWER_RESERVE.getAsDouble());
                    helper.assertTrue(network.stored() >= reserve - 100, "Network down to " + network.stored() + " of " + network.capacity());
                    bridge.setPowerFromNetwork(false);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    MachineBridge bridge = requireBridge(helper, MACHINE);
                    helper.assertTrue(bridge.powered() == 0, "Still powering");
                    before[0] = bridge.info(helper.getLevel().getServer()).energy().get().stored();
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    long after = requireBridge(helper, MACHINE).info(helper.getLevel().getServer()).energy().get().stored();
                    helper.assertTrue(after == before[0], "Crusher went from " + before[0] + " to " + after + " FE with power off");
                })
                .thenSucceed();
    }

    // The level's bridges through their codec and back, replacing the live ones.
    static void reload(ServerLevel level) {
        Codec<MachineBridges> codec = MachineBridges.TYPE.codecFactory().create(level);
        Tag saved = codec.encodeStart(NbtOps.INSTANCE, MachineBridges.get(level)).getOrThrow();
        level.getDataStorage().set(MachineBridges.TYPE, codec.parse(NbtOps.INSTANCE, saved).getOrThrow());
    }
}
