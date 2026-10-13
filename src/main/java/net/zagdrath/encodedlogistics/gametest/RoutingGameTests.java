/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.routing.RoutingDomain;
import net.zagdrath.encodedlogistics.block.ExchangeSwitchBlock;
import net.zagdrath.encodedlogistics.blockentity.ExchangeSwitchBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.part.TrunkPortPart;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.device.SwitchDevice;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RouterAcl;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// Dynamic routing between networks: Routers finding each other, learning paths beyond their own links, and moving items
// along them only where transit is allowed.
final class RoutingGameTests {
    private static final StorageKey COBBLESTONE = StorageKey.of(new ItemStack(Items.COBBLESTONE));

    private RoutingGameTests() {}

    // A powered controller and a rack cabled to it as RackGameTests.networkedRack builds them, dx blocks east. Returns
    // the rack's master.
    static BlockPos networkedRackAt(GameTestHelper helper, int dx) {
        RackGameTests.controller(helper, new BlockPos(dx, 1, 0), 20_000);
        BlockPos master = RackGameTests.rack(helper, new BlockPos(dx + 3, 1, 0), Direction.NORTH);
        for (BlockPos pos : new BlockPos[] { new BlockPos(dx + 1, 1, 0), new BlockPos(dx + 1, 1, 1), new BlockPos(dx + 1, 1, 2),
                new BlockPos(dx + 2, 1, 2), new BlockPos(dx + 3, 1, 2) }) {
            RackGameTests.cable(helper, pos);
        }
        return master;
    }

    private static GlobalPos at(GameTestHelper helper, BlockPos pos) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos));
    }

    // Three networks: A's Router links B, B's links C, both Dynamic. A route on A's Router to C (two hops away) waits
    // while B lets no transit through, then moves A's items straight into C once it does, none landing in B.
    static void multiHop(GameTestHelper helper) {
        BlockPos masterA = networkedRackAt(helper, 0), masterB = networkedRackAt(helper, 9);
        BlockPos controllerB = new BlockPos(9, 1, 0), controllerC = new BlockPos(6, 1, 6), bayC = new BlockPos(6, 1, 7);
        RackGameTests.controller(helper, controllerC, 20_000);
        RackGameTests.driveBay(helper, bayC);
        Rack2GameTests.storageDevice(helper, masterA, RackDeviceType.NAS, 1, NasDevice.class, 0, StorageDevice.READ_WRITE);
        Rack2GameTests.storageDevice(helper, masterB, RackDeviceType.NAS, 1, NasDevice.class, 0, StorageDevice.READ_WRITE);
        RouterDevice routerA = RackGameTests.install(helper, masterA, RackDeviceType.ROUTER, 3, RouterDevice.class);
        RouterDevice routerB = RackGameTests.install(helper, masterB, RackDeviceType.ROUTER, 3, RouterDevice.class);
        NetworkRef[] networkC = new NetworkRef[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(routerA.link(at(helper, controllerB)) == RouterDevice.LinkResult.LINKED, "A didn't link B");
                    helper.assertTrue(routerB.link(at(helper, controllerC)) == RouterDevice.LinkResult.LINKED, "B didn't link C");
                    routerA.setMode(RouterDevice.Mode.DYNAMIC);
                    routerB.setMode(RouterDevice.Mode.DYNAMIC);
                    networkC[0] = RouterDevice.networkAt(helper.getLevel().getServer(), at(helper, controllerC));
                    helper.assertTrue(networkC[0] != null, "C isn't a network");
                    RackGameTests.storage(helper, masterA).insert(COBBLESTONE, 10, false);
                    helper.assertTrue(routerA.addRoute(0, 1, ItemRouting.everything()), "Route not added");
                    routerA.routes().set(0, routerA.routes().getFirst().toRemote(networkC[0]));
                })
                .thenWaitUntil(() -> helper.assertTrue(routerA.upNeighbours() == 1 && routerA.neighbourTable().getFirst().state() == RoutingDomain.State.FULL,
                        "A's neighbours: " + routerA.neighbourTable()))
                .thenIdle(40)
                .thenExecute(() -> {
                    // B allows no transit: C isn't reachable from A, the route pauses and nothing moves.
                    helper.assertTrue(routerA.routeTo(networkC[0]) == null, "C reachable without transit: " + routerA.table());
                    helper.assertTrue(routerA.paused(0), "Route not paused");
                    helper.assertTrue(RackGameTests.storage(helper, masterA).count(COBBLESTONE) == 10, "Items left A without a path");
                    routerB.setTransit(RoutingDomain.Transit.ALL);
                })
                .thenWaitUntil(() -> helper.assertTrue(RackGameTests.storage(helper, bayC).count(COBBLESTONE) == 10,
                        "C has " + RackGameTests.storage(helper, bayC).count(COBBLESTONE)))
                .thenExecute(() -> {
                    RouterDevice.TableEntry entry = routerA.routeTo(networkC[0]);
                    helper.assertTrue(entry != null && entry.hops() == 2 && entry.source() == RouterDevice.Source.DYNAMIC, "Route table: " + routerA.table());
                    helper.assertTrue(RackGameTests.storage(helper, masterB).count(COBBLESTONE) == 0, "Items landed in B");
                    helper.assertTrue(!routerA.paused(0), "Route still paused");
                })
                .thenSucceed();
    }

    // An ACL on a route: its deny rule keeps coal home (counted, *ACLDENY fired), its rate lets only so many dirt a
    // minute through, and what no rule matches (cobblestone) goes by the default.
    static void aclOnRoute(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        BlockPos controllerB = new BlockPos(7, 1, 4), bayB = new BlockPos(7, 1, 5);
        RackGameTests.controller(helper, controllerB, 20_000);
        RackGameTests.driveBay(helper, bayB);
        Rack2GameTests.storageDevice(helper, master, RackDeviceType.NAS, 1, NasDevice.class, 0, StorageDevice.READ_WRITE);
        RouterDevice router = RackGameTests.install(helper, master, RackDeviceType.ROUTER, 3, RouterDevice.class);
        StorageKey coal = StorageKey.of(new ItemStack(Items.COAL)), dirt = StorageKey.of(new ItemStack(Items.DIRT));
        int events = ElclEvents.recent().size();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(router.link(at(helper, controllerB)) == RouterDevice.LinkResult.LINKED, "Not linked");
                    helper.assertTrue(router.createAcl("NOFUEL"), "ACL not made");
                    RouterAcl acl = router.acl("NOFUEL");
                    helper.assertTrue(acl.add(-1, "COAL", RouterAcl.Dir.BOTH, false, 0, 0), "Coal rule");
                    helper.assertTrue(acl.add(-1, "DIRT", RouterAcl.Dir.OUT, true, 5, 0), "Dirt rule");
                    helper.assertTrue(acl.rules().get(1).seq() == 20, "Seq " + acl.rules().get(1).seq());
                    helper.assertTrue(router.addRoute(0, 1, ItemRouting.everything()), "Route not added");
                    helper.assertTrue(router.toggleAcl(0, "NOFUEL"), "ACL not applied");
                    RackGameTests.storage(helper, master).insert(coal, 10, false);
                    RackGameTests.storage(helper, master).insert(dirt, 20, false);
                    RackGameTests.storage(helper, master).insert(COBBLESTONE, 3, false);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    long coalB = RackGameTests.storage(helper, bayB).count(coal), dirtB = RackGameTests.storage(helper, bayB).count(dirt),
                            cobbleB = RackGameTests.storage(helper, bayB).count(COBBLESTONE);
                    helper.assertTrue(coalB == 0, "Coal crossed: " + coalB);
                    helper.assertTrue(dirtB == 5, "Dirt over its rate: " + dirtB);
                    helper.assertTrue(cobbleB == 3, "Default didn't permit: " + cobbleB);
                    RouterAcl acl = router.acl("NOFUEL");
                    helper.assertTrue(acl.rules().getFirst().hits() >= 10 && acl.rules().get(1).hits() == 5, "Hits " + acl.rules().getFirst().hits()
                            + " / " + acl.rules().get(1).hits());
                    helper.assertTrue(ElclEvents.recent().stream().skip(events).anyMatch(event -> event.event().equals("*ACLDENY")
                            && event.data().endsWith("NOFUEL 10 COAL")), "No *ACLDENY");
                })
                .thenSucceed();
    }

    // Two networks nothing links, their Dynamic Routers both members of an Exchange Switch (open membership, the route
    // server on): they peer there, and a route from one reaches the other through the exchange (IX:name).
    static void exchangePeering(GameTestHelper helper) {
        BlockPos masterA = networkedRackAt(helper, 0), masterB = networkedRackAt(helper, 9);
        BlockPos exchangePos = new BlockPos(2, 1, 1);
        helper.setBlock(exchangePos, ModBlocks.EXCHANGE_SWITCH.get().defaultBlockState());
        helper.setBlock(exchangePos.above(), ModBlocks.EXCHANGE_SWITCH.get().defaultBlockState().setValue(ExchangeSwitchBlock.HALF, DoubleBlockHalf.UPPER));
        Rack2GameTests.storageDevice(helper, masterA, RackDeviceType.NAS, 1, NasDevice.class, 0, StorageDevice.READ_WRITE);
        Rack2GameTests.storageDevice(helper, masterB, RackDeviceType.NAS, 1, NasDevice.class, 0, StorageDevice.READ_WRITE);
        RouterDevice routerA = RackGameTests.install(helper, masterA, RackDeviceType.ROUTER, 3, RouterDevice.class);
        RouterDevice routerB = RackGameTests.install(helper, masterB, RackDeviceType.ROUTER, 3, RouterDevice.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        NetworkRef[] networkB = new NetworkRef[1];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    ExchangeSwitchBlockEntity exchange = helper.getBlockEntity(exchangePos, ExchangeSwitchBlockEntity.class);
                    exchange.setMembership(ExchangeSwitchBlockEntity.Membership.OPEN);
                    routerA.setMode(RouterDevice.Mode.DYNAMIC);
                    routerB.setMode(RouterDevice.Mode.DYNAMIC);
                    helper.assertTrue(exchange.join(routerA, helper.getLevel().getServer(), player) == ExchangeSwitchBlockEntity.JoinResult.JOINED, "A didn't join");
                    helper.assertTrue(exchange.join(routerB, helper.getLevel().getServer(), player) == ExchangeSwitchBlockEntity.JoinResult.JOINED, "B didn't join");
                    networkB[0] = routerB.network(helper.getLevel().getServer(), 0);
                    helper.assertTrue(networkB[0] != null, "B has no network");
                    RackGameTests.storage(helper, masterA).insert(COBBLESTONE, 10, false);
                    helper.assertTrue(routerA.addRoute(0, 0, ItemRouting.everything()), "Route not added");
                    routerA.routes().set(0, routerA.routes().getFirst().toRemote(networkB[0]));
                })
                .thenWaitUntil(() -> {
                    ExchangeSwitchBlockEntity exchange = helper.getBlockEntity(exchangePos, ExchangeSwitchBlockEntity.class);
                    helper.assertTrue(exchange.online() && exchange.sessionsUp() == 1, "Sessions up: " + exchange.sessionsUp() + ", online " + exchange.online());
                })
                .thenWaitUntil(() -> helper.assertTrue(RackGameTests.storage(helper, masterB).count(COBBLESTONE) == 10,
                        "B has " + RackGameTests.storage(helper, masterB).count(COBBLESTONE) + "; A's table " + routerA.table()))
                .thenExecute(() -> {
                    RouterDevice.TableEntry entry = routerA.routeTo(networkB[0]);
                    helper.assertTrue(entry != null && entry.next().startsWith("IX:") && entry.source() == RouterDevice.Source.CONNECTED,
                            "Route table: " + routerA.table());
                    helper.assertTrue(routerA.routesVia(networkB[0]) == 1, "Routes via B: " + routerA.routesVia(networkB[0]));
                })
                .thenSucceed();
    }

    // VLAN trunking, on two networks split by a Segment Isolator (as Rack2GameTests.segments). A rack on the first,
    // pooling a NAS through an L2 Switch: the NAS as a trunk serves both networks (each segment sees it), and only its
    // native one once its allowed segments are just that. Then a Trunk Port on the first network's cable carries the
    // second network (ID 10) instead of the rack's own link: the rack knows it, and an access port on it serves it.
    static void vlanTrunking(GameTestHelper helper) {
        BlockPos bayA = new BlockPos(1, 1, 1), isolator = new BlockPos(2, 1, 0), cableA = RackGameTests.CONTROLLER.east(), cableB = new BlockPos(3, 1, 0),
                controllerB = new BlockPos(4, 1, 0);
        RackGameTests.controller(helper, RackGameTests.CONTROLLER, 20_000);
        RackGameTests.controller(helper, controllerB, 20_000);
        RackGameTests.driveBay(helper, bayA);
        helper.setBlock(isolator, ModBlocks.SEGMENT_ISOLATOR.get().defaultBlockState().setValue(SegmentIsolatorBlock.AXIS, Direction.Axis.X));
        BlockPos master = RackGameTests.rack(helper, new BlockPos(1, 1, 3), Direction.SOUTH);
        RackGameTests.cable(helper, cableA);
        RackGameTests.cable(helper, cableB);
        RackGameTests.install(helper, master, RackDeviceType.L2_SWITCH_24, 1, SwitchDevice.class);
        NasDevice nas = Rack2GameTests.storageDevice(helper, master, RackDeviceType.NAS, 2, NasDevice.class, 10, StorageDevice.READ_WRITE);
        GlobalPos nodeB = at(helper, cableB);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    MinecraftServer server = helper.getLevel().getServer();
                    NetworkRef a = RouterDevice.networkAt(server, at(helper, bayA)), b = RouterDevice.networkAt(server, nodeB);
                    helper.assertTrue(rack.toggleSegment(nodeB) == RackBlockEntity.SegmentResult.LINKED, "Segment not linked");
                    helper.assertTrue(rack.knownSegments().get(1).id() == 2, "Segment ID " + rack.knownSegments().get(1).id());
                    rack.togglePortMode(nas.u());
                    helper.assertTrue(nas.trunk(), "Not a trunk");
                    helper.assertTrue(serving(server, a, nas) && serving(server, b, nas), "A trunk carrying all serves both");
                    rack.togglePortAllowed(nas.u(), RackBlockEntity.OWN_ID);
                    helper.assertTrue(serving(server, a, nas) && !serving(server, b, nas), "Allowed only its native segment, it still serves B");
                    // Back to an access port; the rack's own link goes.
                    rack.togglePortMode(nas.u());
                    rack.togglePortAllowed(nas.u(), -1);
                    helper.assertTrue(rack.toggleSegment(nodeB) == RackBlockEntity.SegmentResult.UNLINKED, "Segment not unlinked");
                    Phase4GameTests.mount(helper, cableA, Direction.NORTH, PartType.TRUNK_PORT);
                    TrunkPortPart trunk = (TrunkPortPart) helper.getBlockEntity(cableA, CableBlockEntity.class).part(Direction.NORTH);
                    helper.assertTrue(trunk != null, "No Trunk Port");
                    helper.assertTrue(trunk.toggle(nodeB) == TrunkPortPart.LinkResult.LINKED && trunk.segments().getFirst().id() == 10, "Trunk segment");
                })
                .thenWaitUntil(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    helper.assertTrue(rack.knownSegments().stream().anyMatch(segment -> segment.id() == 10 && segment.trunk()),
                            "The rack doesn't know the trunk's segment: " + rack.knownSegments());
                })
                .thenExecute(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    MinecraftServer server = helper.getLevel().getServer();
                    rack.cycleSegment(nas.u(), 1);
                    helper.assertTrue(nas.segment() == 10, "NAS on " + nas.segment());
                    helper.assertTrue(RouterDevice.networkAt(server, nodeB).equals(rack.network(nas)), "NAS serves " + rack.network(nas));
                })
                .thenSucceed();
    }

    private static boolean serving(MinecraftServer server, NetworkRef network, RackDevice device) {
        return ControllerStructures.rackDevicesServing(server, network).contains(device);
    }
}
