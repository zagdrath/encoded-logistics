/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.display.DisplayContent;
import net.zagdrath.encodedlogistics.display.DisplayFrame;
import net.zagdrath.encodedlogistics.display.DisplayImages;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlock;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlockEntity;
import net.zagdrath.encodedlogistics.display.DisplayTouch;
import net.zagdrath.encodedlogistics.elcl.device.DisplayDevice;
import net.zagdrath.encodedlogistics.elcl.device.Displays;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.menu.DisplayPanelMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.terminal.TerminalCommands;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;

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

    private static String run(TerminalContext context, String line) {
        TerminalOutput out = TerminalCommands.execute(context, line);
        return out.message() != null ? out.message().getString() : "";
    }

    private static void ok(GameTestHelper helper, TerminalContext context, String line) {
        String message = run(context, line);
        helper.assertFalse(message.startsWith("ELC0") || message.startsWith("ELC1"), line + ": " + message);
    }

    // The display commands on a 3 x 2 screen beside ElclGameTests' desk: regions (overlapping and outside: ELC1314),
    // widgets and graphs (a missing data source: ELC1316), their live frames, images (disabled: ELC1315; missing:
    // ELC1312; above the colour limit: ELC1317, shown all the same), clearing, the wrong device (ELC1303, ELC1301); and a
    // touch firing *DSPTOUCH with the region and point, at most four a second.
    @SuppressWarnings("removal")
    static void commands(GameTestHelper helper) {
        ElclGameTests.desk(helper);
        BlockPos master = new BlockPos(3, 1, 3);
        for (int x = 3; x <= 5; x++) {
            for (int y = 1; y <= 2; y++) {
                panel(helper, new BlockPos(x, y, 3), Direction.SOUTH);
            }
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    assertScreen(helper, master, 3, 2);
                    TerminalDeskBlockEntity desk = helper.getBlockEntity(ElclGameTests.DESK, TerminalDeskBlockEntity.class);
                    TerminalContext context = new TerminalContext(helper.getLevel().getServer(), desk.network(), desk, player);
                    ElclSystem system = new ElclSystem(context.server(), context.network());
                    ElclServices.jobs().interactive(system, context.user(), "test", "ELDESK01");
                    DisplayPanelBlockEntity display = at(helper, master);
                    // Regions.
                    ok(helper, context, "CHGDSPRGN DEV(DSP01) RGN(A) W(48)");
                    ElclGameTests.expect(helper, context, "CHGDSPRGN DEV(DSP01) RGN(B) X(40)", "ELC1314");
                    ok(helper, context, "CHGDSPRGN DEV(DSP01) RGN(B) X(48)");
                    ElclGameTests.expect(helper, context, "CHGDSPRGN DEV(DSP01) RGN(C) X(200)", "ELC1314");
                    DisplayContent.Region b = display.displayContent().region("B", 96, 64);
                    helper.assertTrue(b != null && b.x() == 48 && b.w() == 48 && b.h() == 64, "Region B " + b);
                    // Widgets and graphs.
                    ok(helper, context, "SNDDSPWDG DEV(DSP01) RGN(A) WDG(*STORAGE)");
                    helper.assertTrue(display.displayContent().mode == DisplayContent.Mode.SCRIPT, "A scripted screen isn't Script-controlled");
                    ElclGameTests.expect(helper, context, "SNDDSPWDG DEV(DSP01) RGN(B) WDG(*ITEM)", "ELC1316");
                    ElclGameTests.expect(helper, context, "SNDDSPWDG DEV(DSP01) RGN(Z) WDG(*CLOCK)", "ELC1314");
                    ok(helper, context, "SNDDSPWDG DEV(DSP01) RGN(B) WDG(*ITEM) ITEM(minecraft:cobblestone)");
                    List<DisplayFrame> frames = display.liveFrames();
                    helper.assertTrue(frames.size() == 2 && frames.get(0).label().equals("STORAGE") && frames.get(0).numbers().size() == 2,
                            "Frames " + frames);
                    helper.assertTrue(frames.get(1).label().equals("minecraft:cobblestone") && frames.get(1).value().equals("0"), "Item frame " + frames.get(1));
                    ok(helper, context, "SNDDSPGPH DEV(DSP01) RGN(B) STAT(*ENERGY) RANGE(*1M) TYPE(*BAR)");
                    helper.assertTrue(display.displayContent().region("B", 96, 64).widget().graph().equals("*BAR"), "Graph not placed");
                    // The wrong device.
                    ElclGameTests.expect(helper, context, "SNDDSPWDG DEV(ELDESK01) RGN(A) WDG(*CLOCK)", "ELC1303");
                    ElclGameTests.expect(helper, context, "CLRDSP DEV(NOPE01)", "ELC1301");
                    // Images: turned off, then missing, then shown.
                    Config.ALLOW_IMAGES.set(Config.ImagesAllowed.FALSE);
                    ElclGameTests.expect(helper, context, "SNDDSPIMG DEV(DSP01) RGN(A) FILE('logo.png')", "ELC1315");
                    Config.ALLOW_IMAGES.set(Config.ImagesAllowed.TRUE);
                    ElclGameTests.expect(helper, context, "SNDDSPIMG DEV(DSP01) RGN(A) FILE('missing.png')", "ELC1312");
                    try {
                        Path folder = DisplayImages.folder(context.server(), system.name());
                        Files.createDirectories(folder);
                        BufferedImage logo = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
                        for (int i = 0; i < 64; i++) {
                            for (int j = 0; j < 64; j++) {
                                logo.setRGB(i, j, (i * 4) << 16 | (j * 4) << 8 | 0x40);
                            }
                        }
                        ImageIO.write(logo, "png", folder.resolve("logo.png").toFile());
                    } catch (IOException e) {
                        helper.fail("Couldn't write the test image: " + e);
                    }
                    ElclGameTests.expect(helper, context, "SNDDSPIMG DEV(DSP01) RGN(A) FILE('logo.png') COLORS(*FULL)", "ELC1317");
                    // Touch: the point on the top-left panel's middle-left; then the rate limit.
                    BlockPos clicked = helper.absolutePos(master.above());
                    Vec3 hit = new Vec3(clicked.getX() + 0.25, clicked.getY() + 0.5, clicked.getZ() + 0.5);
                    helper.assertTrue(DisplayTouch.touch(helper.getLevel(), display, clicked, hit, player), "Touch not fired");
                    ElclEvents.Event touched = ElclEvents.recent().getLast();
                    helper.assertTrue(touched.event().equals("*DSPTOUCH") && touched.data().equals("DSP01 A 8 16"), "Touch " + touched);
                    for (int i = 0; i < 3; i++) {
                        DisplayTouch.touch(helper.getLevel(), display, clicked, hit, player);
                    }
                    helper.assertFalse(DisplayTouch.touch(helper.getLevel(), display, clicked, hit, player), "Fifth touch in a second fired");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    DisplayPanelBlockEntity display = at(helper, master);
                    int[] pixels = display.images().get("A");
                    helper.assertTrue(pixels != null && pixels.length == 48 * 64, "Image not rendered");
                    helper.assertTrue(display.displayContent().region("A", 96, 64).widget().colors().equals("256"), "Colour mode not capped");
                    Config.ALLOW_IMAGES.set(Config.ImagesAllowed.AUTO);
                    TerminalDeskBlockEntity desk = helper.getBlockEntity(ElclGameTests.DESK, TerminalDeskBlockEntity.class);
                    TerminalContext context = new TerminalContext(helper.getLevel().getServer(), desk.network(), desk, player);
                    ok(helper, context, "CLRDSP DEV(DSP01) RGN(B)");
                    helper.assertTrue(display.displayContent().region("B", 96, 64).widget().kind().equals("*NONE"), "Region B not cleared");
                    ok(helper, context, "CLRDSP DEV(DSP01)");
                    helper.assertTrue(display.displayContent().regions.isEmpty() && display.images().isEmpty(), "Screen not cleared");
                })
                .thenSucceed();
    }

    private static CompoundTag regionsTag(List<DisplayContent.Region> regions) {
        CompoundTag data = new CompoundTag();
        data.put("regions", DisplayContent.Region.CODEC.listOf().encodeStart(NbtOps.INSTANCE, regions).getOrThrow());
        return data;
    }

    // The configuration screen's changes, as the server checks them: the mode, a layout (one overlapping refused), a
    // widget (an item one resolved to its id), the device name; and nothing at all from a player the Firewall denies.
    @SuppressWarnings("removal")
    static void configuration(GameTestHelper helper) {
        BlockPos rack = RackGameTests.networkedRack(helper), master = new BlockPos(3, 1, 3);
        for (int x = 3; x <= 5; x++) {
            for (int y = 1; y <= 2; y++) {
                panel(helper, new BlockPos(x, y, 3), Direction.SOUTH);
            }
        }
        ServerPlayer builder = helper.makeMockServerPlayerInLevel(), stranger = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    DisplayPanelBlockEntity display = at(helper, master);
                    DisplayPanelMenu menu = new DisplayPanelMenu(0, builder.getInventory(), helper.absolutePos(master));
                    CompoundTag mode = new CompoundTag();
                    mode.putString("mode", "DASHBOARD");
                    menu.apply(builder, "mode", mode);
                    helper.assertTrue(display.displayContent().mode == DisplayContent.Mode.DASHBOARD, "Mode not set");
                    DisplayContent.Widget none = DisplayContent.Widget.NONE;
                    menu.apply(builder, "regions", regionsTag(List.of(new DisplayContent.Region("A", 0, 0, 64, 64, 0, none),
                            new DisplayContent.Region("B", 60, 0, 36, 64, 0, none))));
                    helper.assertTrue(display.displayContent().regions.isEmpty(), "Overlapping regions accepted");
                    menu.apply(builder, "regions", regionsTag(List.of(new DisplayContent.Region("A", 0, 0, 64, 64, 0, none),
                            new DisplayContent.Region("B", 64, 0, 32, 64, 0, none))));
                    helper.assertTrue(display.displayContent().regions.size() == 2, "Layout not set");
                    CompoundTag widget = new CompoundTag();
                    widget.putString("region", "B");
                    widget.put("widget", DisplayContent.Widget.CODEC.encodeStart(NbtOps.INSTANCE,
                            new DisplayContent.Widget("*ITEM", "cobblestone", "*ALL", 0xFF50C2EC, "", "*10M", "*LINE", "", "*DITHER", "*DFT")).getOrThrow());
                    menu.apply(builder, "widget", widget);
                    DisplayContent.Region b = display.displayContent().region("B", 96, 64);
                    helper.assertTrue(b.widget().kind().equals("*ITEM") && b.widget().item().equals("minecraft:cobblestone") && b.widget().color() == 0xFF50C2EC,
                            "Widget " + b.widget());
                    CompoundTag name = new CompoundTag();
                    name.putString("name", "lobby");
                    menu.apply(builder, "name", name);
                    helper.assertTrue(display.name().equals("LOBBY"), "Renamed to " + display.name());
                    // A Firewall that denies the stranger: nothing changes.
                    FirewallDevice firewall = RackGameTests.install(helper, rack, RackDeviceType.FIREWALL, 1, FirewallDevice.class);
                    firewall.setPolicy(FirewallDevice.Policy.DENY);
                    firewall.setPermission(builder.getUUID(), "builder", RackPermission.BUILD, FirewallDevice.ON);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    DisplayPanelBlockEntity display = at(helper, master);
                    DisplayPanelMenu menu = new DisplayPanelMenu(0, stranger.getInventory(), helper.absolutePos(master));
                    CompoundTag mode = new CompoundTag();
                    mode.putString("mode", "TEXT");
                    menu.apply(stranger, "mode", mode);
                    helper.assertTrue(display.displayContent().mode == DisplayContent.Mode.DASHBOARD, "A denied player changed the mode");
                })
                .thenSucceed();
    }
}
