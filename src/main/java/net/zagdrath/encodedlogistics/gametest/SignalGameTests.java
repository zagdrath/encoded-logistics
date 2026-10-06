/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.zagdrath.encodedlogistics.Config;
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
// MIDI: PLYMID's refusals (ELC2402, ELC2403 for each limit, ELC2408-2412, ELC1301, ELC0103) start nothing; a MAP play
// across three speakers sends each its parts' notes at the file's times from one clock (across a tempo change), the
// clock itself silent; STPAUD on one of them stops all three; LOOP starts over, once ends; VOL sets them all;
// midiMaxNotes keeps the loudest.
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

    // Each test's network (the tests run side by side).
    private static final Map<GameTestHelper, NetworkRef> NETWORKS = new WeakHashMap<>();

    private static CommandRunner.Result run(GameTestHelper helper, ServerPlayer player, String line) {
        return CommandRunner.run(new TerminalContext(helper.getLevel().getServer(), NETWORKS.get(helper), null, player), line);
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
                    NETWORKS.put(helper, lamp.network());
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
                    Path folder = AudioFiles.folder(helper.getLevel().getServer(), new ElclSystem(helper.getLevel().getServer(), NETWORKS.get(helper)).name());
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

    // --- MIDI ---

    // A Standard MIDI File: a header and its tracks (bytes as ints).
    private static byte[] midi(int format, int division, int[]... tracks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("MThd".getBytes());
        out.writeBytes(new byte[] { 0, 0, 0, 6, 0, (byte) format, 0, (byte) tracks.length, (byte) (division >> 8), (byte) division });
        for (int[] track : tracks) {
            out.writeBytes("MTrk".getBytes());
            out.writeBytes(new byte[] { (byte) (track.length >> 24), (byte) (track.length >> 16), (byte) (track.length >> 8), (byte) track.length });
            for (int b : track) {
                out.write(b);
            }
        }
        return out.toByteArray();
    }

    private static final int[] END = { 0xFF, 0x2F, 0 };

    // A track's events (each a delta time and its bytes) run together.
    private static int[] track(int[]... events) {
        List<Integer> bytes = new ArrayList<>();
        for (int[] event : events) {
            for (int b : event) {
                bytes.add(b);
            }
        }
        return bytes.stream().mapToInt(Integer::intValue).toArray();
    }

    // The notes sent to a speaker, by the game tick from its conductor's start.
    private static List<Long> times(List<SpeakerBlockEntity.Sent> sent, BlockPos speaker, long start) {
        return sent.stream().filter(note -> note.speaker().equals(speaker)).map(note -> note.tick() - start).toList();
    }

    @SuppressWarnings("removal")
    static void midi(GameTestHelper helper) {
        RackGameTests.networkedRack(helper);
        // Each on a cable of the rack's, none next to another.
        BlockPos a = new BlockPos(1, 2, 0), b = new BlockPos(1, 2, 2), c = new BlockPos(3, 2, 2);
        for (BlockPos pos : new BlockPos[] { a, b, c }) {
            helper.setBlock(pos, ModBlocks.SPEAKER.get().defaultBlockState().setValue(SignalBlock.FACING, Direction.UP));
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<SpeakerBlockEntity.Sent> sent = new ArrayList<>();
        Consumer<SpeakerBlockEntity.Sent> listener = sent::add;
        String[] names = new String[3];
        long[] start = new long[1];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    NETWORKS.put(helper, helper.getBlockEntity(a, SpeakerBlockEntity.class).network());
                    helper.assertTrue(NETWORKS.get(helper) != null, "Speaker offline");
                    // Naming happens when the system lists its devices.
                    fails(helper, player, "STPAUD DEV(NOPE01)", "ELC1301");
                    names[0] = helper.getBlockEntity(a, SpeakerBlockEntity.class).deviceName();
                    names[1] = helper.getBlockEntity(b, SpeakerBlockEntity.class).deviceName();
                    names[2] = helper.getBlockEntity(c, SpeakerBlockEntity.class).deviceName();
                    // Two MIDI ticks a beat. band.mid: a tempo track (120 bpm, 60 from the third beat), a melody on track 2
                    // (beats 0-3: game ticks 0, 10, 20, 40) and a kick drum on track 3, channel 10 (beats 0 and 2: 0 and
                    // 20); 3 s.
                    byte[] band = midi(1, 2,
                            track(new int[] { 0, 0xFF, 0x51, 3, 0x07, 0xA1, 0x20 }, new int[] { 4, 0xFF, 0x51, 3, 0x0F, 0x42, 0x40 }, new int[] { 4 }, END),
                            track(new int[] { 0, 0x90, 60, 100 }, new int[] { 2, 0x90, 62, 100 }, new int[] { 2, 0x90, 64, 100 }, new int[] { 2, 0x90, 65, 100 },
                                    new int[] { 2 }, END),
                            track(new int[] { 0, 0x99, 36, 120 }, new int[] { 4, 0x99, 36, 120 }, new int[] { 4 }, END));
                    // short.mid: one note, a beat (10 ticks) long. chord.midi: five notes at once, of different velocities.
                    byte[] tune = midi(0, 2, track(new int[] { 0, 0x90, 60, 100 }, new int[] { 2 }, END));
                    byte[] chord = midi(0, 2, track(new int[] { 0, 0x90, 60, 40 }, new int[] { 0, 0x90, 62, 120 }, new int[] { 0, 0x90, 64, 80 },
                            new int[] { 0, 0x90, 65, 10 }, new int[] { 0, 0x90, 67, 100 }, new int[] { 2 }, END));
                    // big.mid: over 1 KB of notes.
                    int[][] many = new int[402][];
                    for (int i = 0; i < 400; i++) {
                        many[i] = new int[] { 0, 0x90, 60, 100 };
                    }
                    many[400] = new int[] { 2 };
                    many[401] = END;
                    Path folder = AudioFiles.folder(helper.getLevel().getServer(), new ElclSystem(helper.getLevel().getServer(), NETWORKS.get(helper)).name());
                    try {
                        Files.createDirectories(folder);
                        Files.write(folder.resolve("band.mid"), band);
                        Files.write(folder.resolve("short.mid"), tune);
                        Files.write(folder.resolve("chord.midi"), chord);
                        Files.write(folder.resolve("big.mid"), midi(0, 2, track(many)));
                        Files.write(folder.resolve("junk.mid"), "not a MIDI file".getBytes());
                        Files.write(folder.resolve("two.mid"), midi(2, 2, track(new int[] { 0 }, END)));
                    } catch (IOException e) {
                        helper.fail("Writing the MIDI files: " + e);
                    }
                    String play = "PLYMID DEV(" + names[0] + ") ";
                    fails(helper, player, play + "FILE('missing.mid')", "ELC2402");
                    fails(helper, player, play + "FILE('evac.ogg')", "ELC2409");
                    fails(helper, player, play + "FILE('junk.mid')", "ELC2409");
                    fails(helper, player, play + "FILE('two.mid')", "ELC2410");
                    fails(helper, player, play + "FILE('band.mid') MAP((9 " + names[1] + "))", "ELC2411");
                    fails(helper, player, play + "FILE('band.mid') MAPBY(*CHANNEL) MAP((5 " + names[1] + "))", "ELC2412");
                    fails(helper, player, play + "FILE('band.mid') MAP((2 NOPE01))", "ELC1301");
                    fails(helper, player, play + "FILE('band.mid') MAP((2 " + names[1] + " 3))", "ELC0103");
                    SpeakerBlockEntity drums = helper.getBlockEntity(c, SpeakerBlockEntity.class);
                    apply(helper, drums, player, "trigger", 1);
                    fails(helper, player, play + "FILE('band.mid') MAP((3 " + names[2] + "))", "ELC2408");
                    apply(helper, drums, player, "trigger", -1);
                    // The limits.
                    int seconds = Config.MIDI_MAX_SECONDS.getAsInt(), kilobytes = Config.MIDI_MAX_KILOBYTES.getAsInt();
                    Config.MIDI_MAX_SECONDS.set(2);
                    fails(helper, player, play + "FILE('band.mid')", "ELC2403");
                    Config.MIDI_MAX_SECONDS.set(seconds);
                    Config.MIDI_MAX_KILOBYTES.set(1);
                    fails(helper, player, play + "FILE('big.mid')", "ELC2403");
                    Config.MIDI_MAX_KILOBYTES.set(kilobytes);
                    for (BlockPos pos : new BlockPos[] { a, b, c }) {
                        helper.assertFalse(helper.getBlockEntity(pos, SpeakerBlockEntity.class).playing(), "A refused PLYMID started " + pos);
                    }

                    // The melody (track 2) on the second speaker, the drums (track 3) on the third; the first the clock.
                    SpeakerBlockEntity.listen(listener);
                    ok(helper, player, play + "FILE('band.mid') VOL(40) MAP((2 " + names[1] + ") (3 " + names[2] + "))");
                    SpeakerBlockEntity clock = helper.getBlockEntity(a, SpeakerBlockEntity.class);
                    start[0] = clock.started();
                    helper.assertTrue(clock.conducting() && clock.playingSource() == SpeakerBlockEntity.Source.MIDI, "PLYMID: no clock");
                    helper.assertTrue(clock.lengthTicks() == 60, "Length " + clock.lengthTicks());
                    helper.assertTrue(clock.playingParts().length == 0, "The clock plays a part");
                    for (BlockPos pos : new BlockPos[] { a, b, c }) {
                        SpeakerBlockEntity speaker = helper.getBlockEntity(pos, SpeakerBlockEntity.class);
                        helper.assertTrue(speaker.follows(helper.absolutePos(a), clock.play()), pos + " isn't in the play");
                        helper.assertTrue(speaker.volume() == 40, pos + " volume " + speaker.volume());
                        helper.assertTrue(helper.getBlockState(pos).getValue(SpeakerBlock.STATE) == SpeakerBlock.State.PLAYING, pos + " LED");
                    }
                    helper.assertTrue(Arrays.equals(helper.getBlockEntity(b, SpeakerBlockEntity.class).playingParts(), new int[] { 2 }), "Melody's parts");
                    helper.assertTrue(Arrays.equals(drums.playingParts(), new int[] { 3 }), "Drums' parts");
                    // The first notes at once.
                    helper.assertTrue(times(sent, helper.absolutePos(b), start[0]).equals(List.of(0L)), "First melody note " + sent);
                    helper.assertTrue(times(sent, helper.absolutePos(c), start[0]).equals(List.of(0L)), "First drum " + sent);
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    // Every note at its time from the one clock (the tempo change too); none on the clock itself.
                    List<Long> melody = times(sent, helper.absolutePos(b), start[0]), drums = times(sent, helper.absolutePos(c), start[0]);
                    helper.assertTrue(melody.equals(List.of(0L, 10L, 20L, 40L)), "Melody " + melody);
                    helper.assertTrue(drums.equals(List.of(0L, 20L)), "Drums " + drums);
                    helper.assertTrue(times(sent, helper.absolutePos(a), start[0]).isEmpty(), "The clock played");
                    helper.assertTrue(sent.stream().filter(note -> note.speaker().equals(helper.absolutePos(c))).allMatch(note -> note.note().instrument() == 4),
                            "The kick isn't a bass drum");
                    // STPAUD on the drums stops all three.
                    ok(helper, player, "STPAUD DEV(" + names[2] + ")");
                    for (BlockPos pos : new BlockPos[] { a, b, c }) {
                        helper.assertFalse(helper.getBlockEntity(pos, SpeakerBlockEntity.class).playing(), pos + " still playing after STPAUD");
                        helper.assertTrue(helper.getBlockState(pos).getValue(SpeakerBlock.STATE) == SpeakerBlock.State.IDLE, pos + " LED after STPAUD");
                    }
                    sent.clear();
                    // Looped, on the one speaker (no MAP).
                    ok(helper, player, "PLYMID " + names[0] + " 'short.mid' LOOP(*YES)");
                    start[0] = helper.getBlockEntity(a, SpeakerBlockEntity.class).started();
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(a, SpeakerBlockEntity.class).playing(), "A loop ended");
                    List<Long> loop = times(sent, helper.absolutePos(a), start[0]);
                    helper.assertTrue(loop.equals(List.of(0L, 10L, 20L)), "Loop " + loop);
                    sent.clear();
                    // Once: it ends after its length.
                    ok(helper, player, "PLYMID " + names[0] + " 'short.mid'");
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertFalse(helper.getBlockEntity(a, SpeakerBlockEntity.class).playing(), "Once didn't end");
                    helper.assertTrue(sent.size() == 1, "Once sent " + sent.size());
                    sent.clear();
                    // At most midiMaxNotes a tick: the loudest.
                    int most = Config.MIDI_MAX_NOTES.getAsInt();
                    Config.MIDI_MAX_NOTES.set(2);
                    ok(helper, player, "PLYMID " + names[0] + " 'chord.midi'");
                    Config.MIDI_MAX_NOTES.set(most);
                    List<Integer> velocities = sent.stream().map(note -> note.note().velocity()).toList();
                    helper.assertTrue(velocities.equals(List.of(120, 100)), "Chord " + velocities);
                    ok(helper, player, "STPAUD " + names[0]);
                    SpeakerBlockEntity.unlisten(listener);
                })
                .thenSucceed();
    }
}
