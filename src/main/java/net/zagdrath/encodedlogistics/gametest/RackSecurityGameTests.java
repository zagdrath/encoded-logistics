/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.Optional;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.NetworkBridgeBlock;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.block.WirelessPortBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.WirelessPortBlockEntity;
import net.zagdrath.encodedlogistics.item.LinkCardItem;
import net.zagdrath.encodedlogistics.menu.RackMenu;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.part.PointToPointPart;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceItem;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.rack.device.SwitchDevice;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;

// Physical security on Server Racks and permissions on Link Cards, on RackGameTests' networked rack with a Firewall
// (default policy: no access, no owner). Players: a stranger (not listed), a builder (build on, rack access off), a
// rack hand (rack access on, build off) and a trusted player (both on).
final class RackSecurityGameTests {
    private RackSecurityGameTests() {}

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    private static FirewallDevice firewall(GameTestHelper helper, BlockPos master) {
        FirewallDevice firewall = RackGameTests.install(helper, master, RackDeviceType.FIREWALL, 1, FirewallDevice.class);
        firewall.setPolicy(FirewallDevice.Policy.DENY);
        return firewall;
    }

    private static void grant(FirewallDevice firewall, ServerPlayer player, RackPermission permission, byte value) {
        firewall.setPermission(player.getUUID(), player.getGameProfile().name(), permission, value);
    }

    // A right-click with an empty hand on the rack's front or back.
    private static void use(GameTestHelper helper, BlockPos master, ServerPlayer player, Direction side) {
        BlockPos at = helper.absolutePos(master);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.getLevel().getBlockState(at).useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(at).relative(side, 0.5), side, at, false));
    }

    private static RackBlockEntity rack(GameTestHelper helper, BlockPos master) {
        return helper.getBlockEntity(master, RackBlockEntity.class);
    }

    // Whether a player gets in: both doors, the screen, a device in and out. Leaves everything closed.
    private static boolean[] tryRack(GameTestHelper helper, BlockPos master, ServerPlayer player) {
        RackBlockEntity rack = rack(helper, master);
        Direction facing = rack.facing();
        use(helper, master, player, facing);
        boolean front = rack.isFrontOpen();
        use(helper, master, player, facing.getOpposite());
        boolean rear = rack.isRearOpen();
        rack.setFrontOpen(false);
        rack.setRearOpen(false);
        // The screen: a mock player can't be sent one, so only a refusal is opened for real (it stops before sending).
        boolean menu = NetworkAccess.allowed(helper.getLevel(), rack.getBlockPos(), player, RackPermission.RACK);
        if (!menu) {
            rack.openMenu(player);
            helper.assertFalse(player.containerMenu instanceof RackMenu, "Refused, yet the rack's screen opened");
        }
        ItemStack ups = RackDeviceItem.toStack(RackDeviceType.UPS.create(), helper.getLevel().registryAccess());
        boolean installed = rack.installFromHand(player, ups, 20);
        if (!installed) {
            rack.install(RackDeviceType.UPS.create(), 20, null);
        }
        boolean removed = rack.takeOut(player, 20);
        if (!removed) {
            rack.remove(20);
        }
        return new boolean[] { front, rear, menu, installed, removed };
    }

    private static void assertAll(GameTestHelper helper, boolean[] results, boolean expected, String who) {
        String[] what = { "front door", "rear door", "screen", "install", "remove" };
        for (int i = 0; i < results.length; i++) {
            helper.assertTrue(results[i] == expected, who + (expected ? " couldn't use the " : " could use the ") + what[i]);
        }
    }

    private static boolean breakRack(GameTestHelper helper, BlockPos master, ServerPlayer player) {
        player.gameMode.destroyBlock(helper.absolutePos(master));
        return helper.getLevel().getBlockState(helper.absolutePos(master)).isAir();
    }

    // The stranger and the builder are kept out (doors, screen, devices, breaking); the rack hand and the trusted player
    // get in; an operator bypasses. Breaking needs rack access (and build) unless rackBreakProtection is off. Nothing
    // can take a device or drive out through automation.
    static void rackAccess(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        ServerPlayer stranger = player(helper), builder = player(helper), hand = player(helper), trusted = player(helper), op = player(helper);
        FirewallDevice firewall = firewall(helper, master);
        grant(firewall, builder, RackPermission.BUILD, FirewallDevice.ON);
        grant(firewall, builder, RackPermission.RACK, FirewallDevice.OFF);
        grant(firewall, hand, RackPermission.RACK, FirewallDevice.ON);
        grant(firewall, trusted, RackPermission.BUILD, FirewallDevice.ON);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    assertAll(helper, tryRack(helper, master, stranger), false, "A stranger");
                    assertAll(helper, tryRack(helper, master, builder), false, "Build without rack access");
                    assertAll(helper, tryRack(helper, master, hand), true, "Rack access without build");
                    assertAll(helper, tryRack(helper, master, trusted), true, "Build (rack access inherited)");
                    // At owner level (the game test server ops at its lowest otherwise).
                    helper.getLevel().getServer().getPlayerList().op(new NameAndId(op.getGameProfile()), Optional.of(LevelBasedPermissionSet.OWNER), Optional.empty());
                    try {
                        assertAll(helper, tryRack(helper, master, op), true, "An operator");
                    } finally {
                        helper.getLevel().getServer().getPlayerList().deop(new NameAndId(op.getGameProfile()));
                    }
                    // Automation: no item handler or container anywhere on the rack.
                    RackBlockEntity rack = rack(helper, master);
                    for (int index = 0; index < RackGeometry.PARTS; index++) {
                        BlockPos part = RackGeometry.partPos(rack.getBlockPos(), rack.facing(), index);
                        helper.assertTrue(HopperBlockEntity.getContainerAt(helper.getLevel(), part) == null, "A hopper can reach the rack");
                        for (Direction side : Direction.values()) {
                            helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, part, side) == null, "Automation can reach the rack");
                        }
                    }
                    // Breaking.
                    helper.assertFalse(breakRack(helper, master, stranger), "A stranger broke the rack");
                    helper.assertFalse(breakRack(helper, master, builder), "Build without rack access broke the rack");
                    helper.assertFalse(breakRack(helper, master, hand), "Rack access without build broke the rack");
                    Config.RACK_BREAK_PROTECTION.set(false);
                    try {
                        helper.assertTrue(breakRack(helper, master, builder), "With protection off, build couldn't break the rack");
                    } finally {
                        Config.RACK_BREAK_PROTECTION.set(true);
                    }
                })
                .thenSucceed();
    }

    // Without a Firewall everyone gets in; an offline one follows firewallFailClosed; a rack on no network is open.
    static void openAndOffline(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        BlockPos lone = RackGameTests.rack(helper, new BlockPos(7, 1, 5), Direction.NORTH);
        ServerPlayer stranger = player(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    assertAll(helper, tryRack(helper, master, stranger), true, "With no Firewall, a stranger");
                    assertAll(helper, tryRack(helper, lone, stranger), true, "Off any network, a stranger");
                    firewall(helper, master);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockPos rackPos = helper.absolutePos(master);
                    helper.assertFalse(NetworkAccess.allowed(helper.getLevel(), rackPos, stranger, RackPermission.RACK), "Online Firewall let a stranger in");
                    FirewallDevice firewall = (FirewallDevice) rack(helper, master).devices().stream().filter(FirewallDevice.class::isInstance).findFirst()
                            .orElseThrow();
                    firewall.setOnline(false);
                    helper.assertFalse(NetworkAccess.allowed(helper.getLevel(), rackPos, stranger, RackPermission.RACK), "Offline Firewall failed open");
                    Config.FIREWALL_FAIL_CLOSED.set(false);
                    try {
                        helper.assertTrue(NetworkAccess.allowed(helper.getLevel(), rackPos, stranger, RackPermission.RACK),
                                "Offline Firewall with firewallFailClosed off still closed");
                    } finally {
                        Config.FIREWALL_FAIL_CLOSED.set(true);
                    }
                })
                .thenSucceed();
    }

    // --- Link Cards ---

    // Network A: the networked rack (with the Firewall, a Router, a switch and a Wireless Controller), a Bridge and a
    // Point-to-Point Link input. Network B (no Firewall): a controller, a Bridge, a Point-to-Point Link output, a cable
    // behind a Segment Isolator and a Wireless Port.
    private static final BlockPos BRIDGE_A = new BlockPos(1, 1, 3), P2P_A = new BlockPos(0, 1, 1), CONTROLLER_B = new BlockPos(6, 1, 3),
            BRIDGE_B = new BlockPos(6, 1, 4), P2P_B = new BlockPos(5, 1, 3), CABLE_B = new BlockPos(7, 1, 3), ISOLATOR = new BlockPos(7, 1, 4),
            PORT = new BlockPos(6, 2, 3);

    private static void click(GameTestHelper helper, ServerPlayer player, ItemStack card, BlockPos relative, Direction face, Vec3 offset, boolean sneak) {
        BlockPos at = helper.absolutePos(relative);
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        player.setShiftKeyDown(sneak);
        card.getItem().onItemUseFirst(card, new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at).add(offset), face,
                at, false)));
        player.setShiftKeyDown(false);
    }

    // A use on a device's unit through the rack's open front, aimed at it.
    private static void clickUnit(GameTestHelper helper, ServerPlayer player, ItemStack card, BlockPos master, RackDevice device, boolean sneak) {
        RackBlockEntity rack = rack(helper, master);
        rack.setFrontOpen(true);
        Direction facing = rack.facing();
        Vec3 center = RackGeometry.toWorld(RackGeometry.deviceBox(device.u(), device.size()), rack.getBlockPos(), facing).getCenter();
        Vec3 eye = center.relative(facing, 1.5);
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, center);
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        player.setShiftKeyDown(sneak);
        card.getItem().onItemUseFirst(card, new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(center.relative(facing, 0.4), facing,
                rack.getBlockPos(), false)));
        player.setShiftKeyDown(false);
    }

    private static ItemStack card() {
        return new ItemStack(ModItems.LINK_CARD.get());
    }

    private static PointToPointPart p2p(GameTestHelper helper, BlockPos cable) {
        return (PointToPointPart) helper.getBlockEntity(cable, CableBlockEntity.class).part(Direction.UP);
    }

    private static final Vec3 TOP = new Vec3(0, 0.5, 0), CABLE_TOP = new Vec3(0, 0.3, 0);

    // Every link needs build (rack access for a rack device) on both networks, checked when the card is written and when
    // it's applied; the trusted player links each; the links stay when the Firewall later denies.
    static void linkCards(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        ServerPlayer stranger = player(helper), trusted = player(helper);
        FirewallDevice firewall = firewall(helper, master);
        grant(firewall, trusted, RackPermission.BUILD, FirewallDevice.ON);
        RouterDevice router = RackGameTests.install(helper, master, RackDeviceType.ROUTER, 3, RouterDevice.class);
        SwitchDevice switchDevice = RackGameTests.install(helper, master, RackDeviceType.L2_SWITCH_24, 5, SwitchDevice.class);
        WirelessControllerDevice controller = RackGameTests.install(helper, master, RackDeviceType.WIRELESS_CONTROLLER, 7, WirelessControllerDevice.class);
        helper.setBlock(BRIDGE_A, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState().setValue(NetworkBridgeBlock.FACING, Direction.UP));
        RackGameTests.cable(helper, P2P_A);
        RackGameTests.controller(helper, CONTROLLER_B, 20_000);
        helper.setBlock(BRIDGE_B, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState().setValue(NetworkBridgeBlock.FACING, Direction.UP));
        RackGameTests.cable(helper, P2P_B);
        RackGameTests.cable(helper, CABLE_B);
        helper.setBlock(ISOLATOR, ModBlocks.SEGMENT_ISOLATOR.get().defaultBlockState().setValue(SegmentIsolatorBlock.AXIS, Direction.Axis.Z));
        helper.setBlock(PORT, ModBlocks.WIRELESS_INGRESS_PORT.get().defaultBlockState().setValue(WirelessPortBlock.FACING, Direction.UP));
        helper.startSequence()
                .thenExecute(() -> {
                    Phase4GameTests.mount(helper, P2P_A, Direction.UP, PartType.POINT_TO_POINT_LINK);
                    Phase4GameTests.mount(helper, P2P_B, Direction.UP, PartType.POINT_TO_POINT_LINK);
                    p2p(helper, P2P_B).toggleDirection();
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    NetworkBridgeBlockEntity bridgeA = helper.getBlockEntity(BRIDGE_A, NetworkBridgeBlockEntity.class),
                            bridgeB = helper.getBlockEntity(BRIDGE_B, NetworkBridgeBlockEntity.class);

                    // Network Bridges. Copy at A: refused; at B: written; applied at A: refused.
                    ItemStack card = card();
                    click(helper, stranger, card, BRIDGE_A, Direction.UP, TOP, true);
                    helper.assertTrue(LinkCardItem.address(card) == null, "Stranger wrote a card at the denying Bridge");
                    click(helper, stranger, card, BRIDGE_B, Direction.UP, TOP, true);
                    helper.assertTrue(LinkCardItem.address(card) != null, "Stranger couldn't write a card at the open Bridge");
                    click(helper, stranger, card, BRIDGE_A, Direction.UP, TOP, false);
                    helper.assertTrue(bridgeA.partner() == null && bridgeB.partner() == null, "Stranger paired into the denying network");
                    // Written at A by the trusted player, applied at B by the stranger: A still denies.
                    ItemStack fromA = card();
                    click(helper, trusted, fromA, BRIDGE_A, Direction.UP, TOP, true);
                    click(helper, stranger, fromA, BRIDGE_B, Direction.UP, TOP, false);
                    helper.assertTrue(bridgeB.partner() == null, "Stranger paired with a card from the denying network");
                    click(helper, trusted, card, BRIDGE_A, Direction.UP, TOP, false);
                    helper.assertTrue(bridgeA.partner() != null, "Trusted player couldn't pair the Bridges");

                    // Point-to-Point Links.
                    ItemStack p2pCard = card();
                    click(helper, stranger, p2pCard, P2P_A, Direction.UP, CABLE_TOP, true);
                    helper.assertTrue(LinkCardItem.address(p2pCard) == null, "Stranger wrote a card at the denying Point-to-Point Link");
                    click(helper, stranger, p2pCard, P2P_B, Direction.UP, CABLE_TOP, true);
                    click(helper, stranger, p2pCard, P2P_A, Direction.UP, CABLE_TOP, false);
                    helper.assertFalse(p2p(helper, P2P_A).paired(), "Stranger paired a Point-to-Point Link into the denying network");
                    ItemStack p2pFromA = card();
                    click(helper, trusted, p2pFromA, P2P_A, Direction.UP, CABLE_TOP, true);
                    click(helper, stranger, p2pFromA, P2P_B, Direction.UP, CABLE_TOP, false);
                    helper.assertFalse(p2p(helper, P2P_B).paired(), "Stranger paired with a Point-to-Point card from the denying network");
                    click(helper, trusted, p2pCard, P2P_A, Direction.UP, CABLE_TOP, false);
                    helper.assertTrue(p2p(helper, P2P_A).paired(), "Trusted player couldn't pair the Point-to-Point Links");

                    // Router: a network stored at B, applied at the rack in A.
                    ItemStack network = card();
                    click(helper, stranger, network, P2P_A, Direction.DOWN, Vec3.ZERO, true);
                    helper.assertTrue(LinkCardItem.address(network) == null, "Stranger stored the denying network");
                    click(helper, stranger, network, CABLE_B, Direction.UP, TOP, true);
                    helper.assertTrue(LinkCardItem.address(network) != null, "Stranger couldn't store the open network");
                    clickUnit(helper, stranger, network, master, router, false);
                    helper.assertTrue(router.networks().isEmpty(), "Stranger linked the Router");
                    clickUnit(helper, trusted, network, master, router, false);
                    helper.assertTrue(router.networks().size() == 1, "Trusted player couldn't link the Router");

                    // Switch: a segment behind the Isolator.
                    ItemStack segment = card();
                    click(helper, stranger, segment, ISOLATOR, Direction.UP, new Vec3(0, 0.5, -0.3), true);
                    helper.assertTrue(LinkCardItem.address(segment) != null, "Stranger couldn't store the open segment");
                    clickUnit(helper, stranger, segment, master, switchDevice, false);
                    helper.assertTrue(rack(helper, master).segmentCount() == 1, "Stranger linked a segment");
                    clickUnit(helper, trusted, segment, master, switchDevice, false);
                    helper.assertTrue(rack(helper, master).segmentCount() == 2, "Trusted player couldn't link the segment");

                    // Wireless Controller: taking it needs rack access; adopting the port, build on its network.
                    ItemStack wireless = card();
                    clickUnit(helper, stranger, wireless, master, controller, true);
                    helper.assertTrue(LinkCardItem.address(wireless) == null, "Stranger took the Wireless Controller");
                    clickUnit(helper, trusted, wireless, master, controller, true);
                    helper.assertTrue(LinkCardItem.address(wireless) != null, "Trusted player couldn't take the Wireless Controller");
                    WirelessPortBlockEntity port = helper.getBlockEntity(PORT, WirelessPortBlockEntity.class);
                    click(helper, stranger, wireless, PORT, Direction.UP, TOP, false);
                    helper.assertTrue(port.link() == null, "Stranger adopted the port into the denying network");
                    click(helper, trusted, wireless, PORT, Direction.UP, TOP, false);
                    helper.assertTrue(port.link() != null, "Trusted player couldn't adopt the port");

                    // Later the Firewall denies the trusted player too: the links stay.
                    grant(firewall, trusted, RackPermission.BUILD, FirewallDevice.OFF);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(BRIDGE_A, NetworkBridgeBlockEntity.class).partner() != null, "Bridge link lost");
                    helper.assertTrue(p2p(helper, P2P_A).paired(), "Point-to-Point link lost");
                    helper.assertTrue(router.networks().size() == 1 && rack(helper, master).segmentCount() == 2, "Router or segment link lost");
                    helper.assertTrue(helper.getBlockEntity(PORT, WirelessPortBlockEntity.class).link() != null, "Wireless link lost");
                })
                .thenSucceed();
    }
}
