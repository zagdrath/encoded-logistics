/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.zagdrath.encodedlogistics.block.ControlInterfaceBlock;
import net.zagdrath.encodedlogistics.block.ControlInterfaceBlock.Led;
import net.zagdrath.encodedlogistics.blockentity.ControlInterfaceBlockEntity;
import net.zagdrath.encodedlogistics.elcl.exec.CommandRunner;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;

// The Control Interface: named CTLIF01, CTLIF02 on its network; RTVRSIN reads a face and *MAX; CHGRSOUT on a face and
// *ALL powers a lamp and dust; its own output never reads back; the LEDs follow max(in, out); *RSCHANGE fires once per
// change; ELC1301 / ELC1302 / ELC1303 / ELC0004; offline it gives out nothing, keeps its levels and gives them out again.
final class ControlInterfaceGameTests {
    // On the networked rack's cables: CI above (1, 1, 1), the other above (2, 1, 2). Round the first: a redstone block
    // to the west, a lamp to the east, dust on top.
    private static final BlockPos CI = new BlockPos(1, 2, 1), OTHER = new BlockPos(2, 2, 2), WEST = new BlockPos(0, 2, 1), LAMP = new BlockPos(2, 2, 1),
            DUST = new BlockPos(1, 3, 1);

    private ControlInterfaceGameTests() {}

    private static ControlInterfaceBlockEntity ci(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, ControlInterfaceBlockEntity.class);
    }

    // The network, as the terminal knows it (the Control Interface itself says none while it's offline).
    private static NetworkRef network;

    private static CommandRunner.Result run(GameTestHelper helper, ServerPlayer player, String line) {
        TerminalContext context = new TerminalContext(helper.getLevel().getServer(), network, null, player);
        return CommandRunner.run(context, line);
    }

    private static String level(GameTestHelper helper, ServerPlayer player, String name, String side) {
        CommandRunner.Result result = run(helper, player, "RTVRSIN DEV(" + name + ") SIDE(" + side + ")");
        helper.assertTrue(result.ok(), "RTVRSIN " + side + ": " + result.escape());
        return result.returns().get("RTNLVL");
    }

    private static void fails(GameTestHelper helper, ServerPlayer player, String line, String id) {
        CommandRunner.Result result = run(helper, player, line);
        helper.assertTrue(result.escape() != null && result.escape().id().equals(id), line + ": " + result.escape() + ", wanted " + id);
    }

    private static Led led(GameTestHelper helper, Direction face) {
        return helper.getBlockState(CI).getValue(ControlInterfaceBlock.LEDS.get(face));
    }

    @SuppressWarnings("removal")
    static void controlInterface(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 1, UpsDevice.class);
        helper.setBlock(CI, ModBlocks.CONTROL_INTERFACE.get());
        helper.setBlock(OTHER, ModBlocks.CONTROL_INTERFACE.get());
        helper.setBlock(LAMP, Blocks.REDSTONE_LAMP);
        helper.setBlock(DUST, Blocks.REDSTONE_WIRE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String[] name = new String[1];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(ci(helper, CI).isOnline(), "Control Interface offline");
                    network = ci(helper, CI).network();
                    Set<String> names = Set.of(ci(helper, CI).name(), ci(helper, OTHER).name());
                    helper.assertTrue(names.equals(Set.of("CTLIF01", "CTLIF02")), "Names " + names);
                    name[0] = ci(helper, CI).name();
                    helper.assertTrue(level(helper, player, name[0], "*WEST").equals("0"), "West before");
                    helper.setBlock(WEST, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(level(helper, player, name[0], "*WEST").equals("15"), "West");
                    helper.assertTrue(level(helper, player, name[0], "*MAX").equals("15"), "Max");
                    helper.assertTrue(level(helper, player, name[0], "*EAST").equals("0"), "East");
                    long fired = ElclEvents.recent().stream().filter(event -> event.network().equals(network)
                            && event.event().equals("*RSCHANGE") && event.device().equals(name[0]) && event.data().equals("*WEST 15")).count();
                    helper.assertTrue(fired == 1, "*RSCHANGE fired " + fired + " times");
                    helper.assertTrue(led(helper, Direction.WEST) == Led.BRIGHT && led(helper, Direction.EAST) == Led.OFF, "LEDs before output");
                    helper.assertTrue(run(helper, player, "CHGRSOUT DEV(" + name[0] + ") SIDE(*EAST) LVL(15)").ok(), "CHGRSOUT east");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(LAMP).getValue(BlockStateProperties.LIT), "Lamp not lit");
                    helper.assertTrue(led(helper, Direction.EAST) == Led.BRIGHT, "East LED");
                    helper.assertTrue(run(helper, player, "CHGRSOUT " + name[0] + " *ALL 7").ok(), "CHGRSOUT *ALL");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    int dust = helper.getBlockState(DUST).getValue(BlockStateProperties.POWER);
                    helper.assertTrue(dust == 7, "Dust power " + dust);
                    helper.assertTrue(level(helper, player, name[0], "*UP").equals("0"), "Its own output read back");
                    helper.assertTrue(led(helper, Direction.UP) == Led.DIM && led(helper, Direction.WEST) == Led.BRIGHT, "LEDs after output");
                    fails(helper, player, "CHGRSOUT DEV(" + name[0] + ") SIDE(*UP) LVL(20)", "ELC0004");
                    fails(helper, player, "RTVRSIN DEV(NOPE01) SIDE(*UP)", "ELC1301");
                    fails(helper, player, "RTVRSIN DEV(UPS01) SIDE(*UP)", "ELC1303");
                    // Offline: both commands refuse, nothing is stored, nothing is given out - until it's back.
                    ControlInterfaceBlockEntity ci = ci(helper, CI);
                    ci.setNetworkOnline(false);
                    fails(helper, player, "RTVRSIN DEV(" + name[0] + ") SIDE(*UP)", "ELC1302");
                    fails(helper, player, "CHGRSOUT DEV(" + name[0] + ") SIDE(*EAST) LVL(3)", "ELC1302");
                    helper.assertTrue(ci.output(Direction.EAST) == 7 && ci.emitted(Direction.EAST) == 0, "Offline output");
                    ci.setNetworkOnline(true);
                    helper.assertTrue(ci.emitted(Direction.EAST) == 7, "Output not given out again");
                })
                .thenSucceed();
    }
}
