/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlock;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlockEntity;
import net.zagdrath.encodedlogistics.elcl.device.DisplayDevice;
import net.zagdrath.encodedlogistics.elcl.device.Displays;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// Display Panels: merging into screens (the largest rectangles, at most displayMaxWidth wide; bezels and the LED from
// the rectangle; content kept through a re-merge), and a screen on a network: booting, online, named DSP01, written to
// as SNDDSPTXT does; one on no network shows no signal.
final class DisplayGameTests {
    private DisplayGameTests() {}

    private static void panel(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.DISPLAY_PANEL.get().defaultBlockState().setValue(DisplayPanelBlock.FACING, facing));
    }

    private static DisplayPanelBlockEntity at(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, DisplayPanelBlockEntity.class);
    }

    private static void assertScreen(GameTestHelper helper, BlockPos master, int width, int height) {
        DisplayPanelBlockEntity panel = at(helper, master);
        helper.assertTrue(panel.isMaster() && panel.width() == width && panel.height() == height,
                "Screen at " + master + ": master " + panel.isMaster() + ", " + panel.width() + " x " + panel.height() + ", wanted " + width + " x " + height);
    }

    // Facing north the screen's right is west (-x), so a screen's master (its bottom-left) is its east end.
    static void merging(GameTestHelper helper) {
        // A 3 x 2 screen.
        for (int x = 2; x <= 4; x++) {
            for (int y = 1; y <= 2; y++) {
                panel(helper, new BlockPos(x, y, 2), Direction.NORTH);
            }
        }
        BlockPos master = new BlockPos(4, 1, 2);
        assertScreen(helper, master, 3, 2);
        helper.assertTrue(at(helper, new BlockPos(2, 2, 2)).masterPos().equals(helper.absolutePos(master)), "A panel doesn't know its master");
        helper.assertBlockProperty(new BlockPos(2, 1, 2), DisplayPanelBlock.LED, true);
        helper.assertBlockProperty(master, DisplayPanelBlock.LED, false);
        helper.assertBlockProperty(master, DisplayPanelBlock.LEFT, false);
        helper.assertBlockProperty(master, DisplayPanelBlock.RIGHT, true);
        helper.assertBlockProperty(new BlockPos(4, 2, 2), DisplayPanelBlock.TOP, false);
        helper.assertBlockProperty(new BlockPos(4, 2, 2), DisplayPanelBlock.BOTTOM, true);
        helper.assertBlockProperty(new BlockPos(3, 1, 2), DisplayPanelBlock.LEFT, true);
        at(helper, master).write(1, "HELLO", true);

        // One more beside the bottom row: the 3 x 2 stays, the new one is its own screen.
        panel(helper, new BlockPos(1, 1, 2), Direction.NORTH);
        assertScreen(helper, master, 3, 2);
        assertScreen(helper, new BlockPos(1, 1, 2), 1, 1);

        // The bottom middle broken: the top row is the largest screen left; the old master, a screen of its own now, keeps
        // the content.
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.AIR);
        assertScreen(helper, new BlockPos(4, 2, 2), 3, 1);
        assertScreen(helper, master, 1, 1);
        helper.assertTrue(at(helper, master).textLines().equals(List.of("HELLO")), "Content lost in the re-merge");
        assertScreen(helper, new BlockPos(2, 1, 2), 2, 1);

        // Nine in a row: eight wide at most.
        for (int x = 0; x <= 8; x++) {
            panel(helper, new BlockPos(x, 5, 4), Direction.NORTH);
        }
        assertScreen(helper, new BlockPos(8, 5, 4), 8, 1);
        assertScreen(helper, new BlockPos(0, 5, 4), 1, 1);
        // A panel facing another way doesn't join.
        panel(helper, new BlockPos(5, 6, 4), Direction.SOUTH);
        assertScreen(helper, new BlockPos(5, 6, 4), 1, 1);
        helper.succeed();
    }

    // A 1 x 2 screen against the controller: boots, then online; DSP01 with 6 lines of 5 characters, written to. A lone
    // panel: no signal.
    static void onNetwork(GameTestHelper helper) {
        BlockPos controller = new BlockPos(0, 1, 0), master = new BlockPos(1, 1, 0), lone = new BlockPos(5, 1, 5);
        RackGameTests.controller(helper, controller, 20_000);
        panel(helper, master, Direction.EAST);
        panel(helper, master.above(), Direction.EAST);
        panel(helper, lone, Direction.NORTH);
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    assertScreen(helper, master, 1, 2);
                    helper.assertBlockProperty(master, DisplayPanelBlock.STATE, DisplayPanelBlock.Shown.BOOT);
                    helper.assertBlockProperty(lone, DisplayPanelBlock.STATE, DisplayPanelBlock.Shown.NO_SIGNAL);
                })
                .thenIdle(DisplayPanelBlockEntity.BOOT_TICKS + 2)
                .thenExecute(() -> {
                    helper.assertBlockProperty(master, DisplayPanelBlock.STATE, DisplayPanelBlock.Shown.ONLINE);
                    helper.assertBlockProperty(master.above(), DisplayPanelBlock.STATE, DisplayPanelBlock.Shown.ONLINE);
                    NetworkRef network = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(master));
                    helper.assertTrue(network != null, "Screen not on the network");
                    ElclSystem system = new ElclSystem(helper.getLevel().getServer(), network);
                    List<String> names = ElclDevices.list(system.server(), network).stream().map(ElclDevices.Device::name).toList();
                    helper.assertTrue(names.contains("DSP01"), "Names " + names);
                    DisplayDevice display = Displays.find(system, "DSP01");
                    helper.assertTrue(display != null && display.online() && display.lines() == 6, "Not a display of 6 lines");
                    // One panel wide: 5 characters a line.
                    display.write(0, "ONE", true);
                    display.write(0, "TWO", false);
                    display.write(5, "TOO LONG", false);
                    helper.assertTrue(at(helper, master).textLines().equals(List.of("ONE", "TWO", "", "", "TOO L")),
                            "Lines " + at(helper, master).textLines());
                })
                .thenSucceed();
    }
}
