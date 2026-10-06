/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.exec.CommandRunner;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.signal.AudioFiles;
import net.zagdrath.encodedlogistics.signal.CageLightBlock;
import net.zagdrath.encodedlogistics.signal.CageLightBlockEntity;
import net.zagdrath.encodedlogistics.signal.SignalBlock;
import net.zagdrath.encodedlogistics.signal.SignalBlockEntity;
import net.zagdrath.encodedlogistics.signal.SirenBlock;
import net.zagdrath.encodedlogistics.signal.SirenBlockEntity;
import net.zagdrath.encodedlogistics.signal.SirenSound;
import net.zagdrath.encodedlogistics.signal.SpeakerBlock;
import net.zagdrath.encodedlogistics.signal.SpeakerBlockEntity;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;

// Cage Lights, Alarm Strobes and Speakers (docs/signals). Standalone: a light follows the redstone at its block (and
// inverted, the other way), a strobe comes on and goes off with it. Networked: named LGT01, SRN01, SPK01; CHGLGT,
// STRSRN / ENDSRN, PLYNOTE (a sequence, the next note on a rising edge), PLYAUD with a file from the system's audio
// folder (its length known) and STPAUD; ELC1301, ELC1303, ELC2401, ELC2402, ELC2404 / ELC2405, ELC2407, ELC2408.
final class SignalGameTests {
    private SignalGameTests() {}

    // --- Standalone ---

    @SuppressWarnings("removal")
    static void standalone(GameTestHelper helper) {
        BlockPos light = new BlockPos(1, 2, 1), lightPower = new BlockPos(0, 2, 1), siren = new BlockPos(3, 2, 1), sirenPower = new BlockPos(4, 2, 1);
        helper.setBlock(light, ModBlocks.cageLight(DyeColor.RED).get().defaultBlockState().setValue(SignalBlock.FACING, Direction.UP));
        helper.setBlock(siren, ModBlocks.siren(SirenBlock.Colour.AMBER).get().defaultBlockState().setValue(SignalBlock.FACING, Direction.NORTH));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertFalse(helper.getBlockState(light).getValue(CageLightBlock.LIT), "Lit without a signal");
                    helper.assertTrue(helper.getBlockState(siren).getValue(SirenBlock.LIGHT) == SirenBlock.Light.OFF, "Strobe on without a signal");
                    helper.setBlock(lightPower, Blocks.REDSTONE_BLOCK);
                    helper.setBlock(sirenPower, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(light).getValue(CageLightBlock.LIT), "Not lit with a signal");
                    helper.assertTrue(CageLightBlock.lightLevel(helper.getBlockState(light)) == 15, "Light level");
                    SirenBlockEntity strobe = helper.getBlockEntity(siren, SirenBlockEntity.class);
                    helper.assertTrue(strobe.active() && helper.getBlockState(siren).getValue(SirenBlock.LIGHT) == SirenBlock.Light.FAST, "Strobe not on");
                    helper.assertTrue(strobe.colour() == SirenBlock.Colour.AMBER, "Colour " + strobe.colour());
                    // Inverted: off while powered.
                    CageLightBlockEntity lamp = helper.getBlockEntity(light, CageLightBlockEntity.class);
                    apply(helper, lamp, player, "control", 1);
                    helper.assertTrue(lamp.control() == CageLightBlockEntity.Control.INVERTED, "Control " + lamp.control());
                    helper.assertFalse(helper.getBlockState(light).getValue(CageLightBlock.LIT), "Inverted, lit while powered");
                    apply(helper, lamp, player, "level", -1);
                    helper.setBlock(lightPower, Blocks.AIR);
                    helper.setBlock(sirenPower, Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(light).getValue(CageLightBlock.LIT), "Inverted, not lit unpowered");
                    helper.assertTrue(CageLightBlock.lightLevel(helper.getBlockState(light)) == 14, "Light level after a step down");
                    helper.assertTrue(helper.getBlockState(siren).getValue(SirenBlock.LIGHT) == SirenBlock.Light.OFF, "Strobe still on");
                    helper.assertTrue(helper.getBlockEntity(light, CageLightBlockEntity.class).deviceName().isEmpty(), "Named off a network");
                })
                .thenSucceed();
    }

    private static void apply(GameTestHelper helper, SignalBlockEntity device, ServerPlayer player, String key,
            int step) {
        try {
            device.apply(player, key, step, null);
        } catch (ElclException e) {
            helper.fail(key + ": " + e.elclMessage().id());
        }
    }

    // --- Networked ---

    private static NetworkRef network;

    private static CommandRunner.Result run(GameTestHelper helper, ServerPlayer player, String line) {
        return CommandRunner.run(new TerminalContext(helper.getLevel().getServer(), network, null, player), line);
    }

    private static void ok(GameTestHelper helper, ServerPlayer player, String line) {
        CommandRunner.Result result = run(helper, player, line);
        helper.assertTrue(result.ok(), line + ": " + result.escape());
    }

    private static void fails(GameTestHelper helper, ServerPlayer player, String line, String id) {
        CommandRunner.Result result = run(helper, player, line);
        helper.assertTrue(result.escape() != null && result.escape().id().equals(id), line + ": " + result.escape() + ", wanted " + id);
    }

    @SuppressWarnings("removal")
    static void networked(GameTestHelper helper) {
        RackGameTests.networkedRack(helper);
        // Each on a cable of the rack's, none next to another.
        BlockPos light = new BlockPos(1, 2, 0), siren = new BlockPos(1, 2, 2), speaker = new BlockPos(3, 2, 2), speakerPower = new BlockPos(4, 2, 2);
        helper.setBlock(light, ModBlocks.cageLight(DyeColor.LIME).get().defaultBlockState().setValue(SignalBlock.FACING, Direction.UP));
        helper.setBlock(siren, ModBlocks.siren(SirenBlock.Colour.RED).get().defaultBlockState().setValue(SignalBlock.FACING, Direction.UP));
        helper.setBlock(speaker, ModBlocks.SPEAKER.get().defaultBlockState().setValue(SignalBlock.FACING, Direction.UP));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int[] play = new int[1];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    CageLightBlockEntity lamp = helper.getBlockEntity(light, CageLightBlockEntity.class);
                    helper.assertTrue(lamp.isOnline(), "Light offline");
                    network = lamp.network();
                    // Naming happens when the system lists its devices.
                    fails(helper, player, "CHGLGT DEV(NOPE01)", "ELC1301");
                    helper.assertTrue(lamp.deviceName().equals("LGT01"), "Light named " + lamp.deviceName());
                    helper.assertTrue(helper.getBlockEntity(siren, SirenBlockEntity.class).deviceName().equals("SRN01"), "Strobe name");
                    helper.assertTrue(helper.getBlockEntity(speaker, SpeakerBlockEntity.class).deviceName().equals("SPK01"), "Speaker name");

                    ok(helper, player, "CHGLGT DEV(LGT01) STATUS(*ON) LVL(9)");
                    helper.assertTrue(helper.getBlockState(light).getValue(CageLightBlock.LIT), "CHGLGT *ON");
                    helper.assertTrue(CageLightBlock.lightLevel(helper.getBlockState(light)) == 9, "CHGLGT LVL(9)");
                    ok(helper, player, "CHGLGT LGT01 *TOGGLE");
                    helper.assertFalse(helper.getBlockState(light).getValue(CageLightBlock.LIT), "CHGLGT *TOGGLE");
                    fails(helper, player, "CHGLGT DEV(LGT01) LVL(20)", "ELC2401");
                    fails(helper, player, "CHGLGT DEV(SRN01) STATUS(*ON)", "ELC1303");

                    ok(helper, player, "STRSRN DEV(SRN01) SOUND(*YELP) MODE(*SLOW)");
                    SirenBlockEntity strobe = helper.getBlockEntity(siren, SirenBlockEntity.class);
                    helper.assertTrue(strobe.active() && strobe.sound() == SirenSound.YELP, "STRSRN");
                    helper.assertTrue(helper.getBlockState(siren).getValue(SirenBlock.LIGHT) == SirenBlock.Light.SLOW, "STRSRN light");
                    ok(helper, player, "ENDSRN DEV(*ALL)");
                    helper.assertFalse(strobe.active(), "ENDSRN");
                    helper.assertTrue(helper.getBlockState(siren).getValue(SirenBlock.LIGHT) == SirenBlock.Light.OFF, "ENDSRN light");
                    // Trigger Redstone: no network commands; *ALL leaves it out.
                    apply(helper, strobe, player, "trigger", 1);
                    helper.assertTrue(strobe.trigger() == SignalBlockEntity.Trigger.REDSTONE, "Trigger " + strobe.trigger());
                    fails(helper, player, "STRSRN DEV(SRN01)", "ELC2408");
                    ok(helper, player, "STRSRN DEV(*ALL)");
                    helper.assertFalse(strobe.active(), "*ALL started a Redstone-only strobe");

                    SpeakerBlockEntity box = helper.getBlockEntity(speaker, SpeakerBlockEntity.class);
                    ok(helper, player, "PLYNOTE DEV(SPK01) INST(*BELL) NOTE(0 12 24)");
                    helper.assertTrue(box.playing() && box.playingSource() == SpeakerBlockEntity.Source.NOTE, "PLYNOTE");
                    play[0] = box.play();
                    fails(helper, player, "PLYNOTE DEV(SPK01) NOTE(30)", "ELC2407");
                    helper.setBlock(speakerPower, Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    SpeakerBlockEntity box = helper.getBlockEntity(speaker, SpeakerBlockEntity.class);
                    helper.assertTrue(box.play() == play[0] + 1, "Rising edge played no note: play " + box.play());
                    helper.setBlock(speakerPower, Blocks.AIR);
                    fails(helper, player, "PLYAUD DEV(SPK01) SRC('missing.ogg')", "ELC2402");
                    fails(helper, player, "PLYAUD DEV(SPK01) SRC('https://example.com/alarm.ogg')",
                            AudioFiles.webAllowed(helper.getLevel().getServer()) ? "ELC2405" : "ELC2404");
                    // A file in the system's audio folder: a siren tone, 4 s.
                    Path folder = AudioFiles.folder(helper.getLevel().getServer(), new ElclSystem(helper.getLevel().getServer(), network).name());
                    try (InputStream wail = EncodedLogistics.class.getResourceAsStream("/assets/encodedlogistics/sounds/block/siren/wail.ogg")) {
                        helper.assertTrue(wail != null, "No wail.ogg");
                        Files.createDirectories(folder);
                        Files.write(folder.resolve("evac.ogg"), wail.readAllBytes());
                        Files.write(folder.resolve("junk.mp3"), new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });
                    } catch (IOException e) {
                        helper.fail("Writing the audio file: " + e);
                    }
                    ok(helper, player, "PLYAUD DEV(SPK01) SRC('evac.ogg') VOL(50) LOOP(*YES)");
                    helper.assertTrue(box.playing() && box.playingSource() == SpeakerBlockEntity.Source.FILE, "PLYAUD");
                    helper.assertTrue(box.lengthTicks() == 80 && box.volume() == 50 && box.loop(), "PLYAUD length " + box.lengthTicks());
                    helper.assertTrue(AudioFiles.bytes(box.hash()) != null, "File not kept by its hash");
                    helper.assertTrue(helper.getBlockState(speaker).getValue(SpeakerBlock.STATE) == SpeakerBlock.State.PLAYING, "LED");
                    // A refused source changes no speaker.
                    fails(helper, player, "PLYAUD DEV(SPK01) SRC('junk.mp3')", "ELC2406");
                    helper.assertTrue(box.playing() && helper.getBlockState(speaker).getValue(SpeakerBlock.STATE) == SpeakerBlock.State.PLAYING,
                            "A refused PLYAUD stopped the speaker");
                    // A listener (by the speaker) that couldn't play it: the amber LED and the reason, until it plays again.
                    BlockPos at = helper.absolutePos(speaker);
                    player.setPos(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
                    box.report(player, box.play(), 0, "web: HTTP 404");
                    helper.assertTrue(helper.getBlockState(speaker).getValue(SpeakerBlock.STATE) == SpeakerBlock.State.ERROR, "Error LED");
                    helper.assertTrue(box.error().equals("web: HTTP 404"), "Error " + box.error());
                    ok(helper, player, "PLYAUD SPK01 'evac.ogg'");
                    helper.assertTrue(box.error().isEmpty() && box.playing(), "Error kept after playing again");
                    ok(helper, player, "STPAUD DEV(SPK01)");
                    helper.assertFalse(box.playing(), "STPAUD");
                    helper.assertTrue(helper.getBlockState(speaker).getValue(SpeakerBlock.STATE) == SpeakerBlock.State.IDLE, "Idle LED");
                })
                .thenSucceed();
    }
}
