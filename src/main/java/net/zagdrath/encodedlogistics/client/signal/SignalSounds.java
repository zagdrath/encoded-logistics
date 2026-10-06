/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.signal;

import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.client.screen.TerminalSettings;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.net.AudioPayloads;
import net.zagdrath.encodedlogistics.signal.AudioFiles;
import net.zagdrath.encodedlogistics.signal.SignalBlock;
import net.zagdrath.encodedlogistics.signal.SignalBlockEntity;
import net.zagdrath.encodedlogistics.signal.SignalClientHooks;
import net.zagdrath.encodedlogistics.signal.SirenBlockEntity;
import net.zagdrath.encodedlogistics.signal.SirenSound;
import net.zagdrath.encodedlogistics.signal.SpeakerBlockEntity;

// The signal devices' sounds on the client (SignalClientHooks):
// - an active Alarm Strobe plays its tone at the start of each sample, counted from its shared start tick (a sample
//   missed by a few ticks is still played; later, it waits for the next), from its sounder;
// - a Speaker's note (a block event) plays at once, at the instrument's pitch for it;
// - a Speaker playing a file asks the server for it (AudioCache) and, once it's here, starts it where the play should be
//   by now; a URL is fetched by this game (unless neverPlayWebAudio) with the server's limits, and its length - or why
//   it failed - reported back (AudioPayloads.Report). Each device's sound stops when it does, or its block goes.
public final class SignalSounds implements SignalClientHooks {
    private static final int LATE_TICKS = 10;

    // What a device is playing here (gone with the block entity).
    private static final class Playing {
        @Nullable DeviceSound sound;
        long start = Long.MIN_VALUE, cycle = -1;
        // A Speaker's play this is (or is waiting) for, and whether it's been started / reported.
        int play = -1;
        boolean started, reported, fetching;
    }

    private final Map<SignalBlockEntity, Playing> playing = new WeakHashMap<>();

    private SignalSounds() {}

    public static void register() {
        SignalClientHooks.set(new SignalSounds());
    }

    public static void clear() {
        AudioCache.clear();
    }

    private Playing state(SignalBlockEntity device) {
        return playing.computeIfAbsent(device, d -> new Playing());
    }

    private static void play(DeviceSound sound) {
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    // Where a device sounds from: a siren's sounder, at its base; a speaker's face.
    private static Vec3 origin(SignalBlockEntity device, double fromBase) {
        Direction facing = device.getBlockState().getBlock() instanceof SignalBlock ? device.getBlockState().getValue(SignalBlock.FACING) : Direction.UP;
        return Vec3.atCenterOf(device.getBlockPos()).add(facing.getUnitVec3().scale(fromBase - 0.5));
    }

    // Whether the player is within a device's range (and a little more).
    private static boolean inRange(SignalBlockEntity device, int range) {
        return Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.getEyePosition().distanceTo(Vec3.atCenterOf(device.getBlockPos())) <= range + 4;
    }

    @Override
    public void tick(SignalBlockEntity device) {
        if (device instanceof SirenBlockEntity siren) {
            siren(siren);
        } else if (device instanceof SpeakerBlockEntity speaker) {
            speaker(speaker);
        }
    }

    // --- Alarm Strobes ---

    private void siren(SirenBlockEntity siren) {
        Playing state = state(siren);
        Level level = siren.getLevel();
        SirenSound tone = siren.sound();
        Holder<SoundEvent> event = tone.event();
        if (level == null || !siren.active() || event == null) {
            stop(state);
            return;
        }
        long elapsed = level.getGameTime() - siren.start();
        if (elapsed < 0) {
            return;
        }
        long cycle = elapsed / tone.ticks();
        if (state.start == siren.start() && state.cycle == cycle || elapsed % tone.ticks() > LATE_TICKS) {
            return;
        }
        state.start = siren.start();
        state.cycle = cycle;
        // Out of its range this sample: nothing to hear (not a silent channel).
        if (!inRange(siren, siren.range())) {
            return;
        }
        long start = siren.start();
        DeviceSound sound = new DeviceSound(event.value(), SoundSource.BLOCKS, origin(siren, 0.1), siren.volume() / 100.0F, siren.range(), 1.0F,
                () -> !siren.isRemoved() && siren.active() && siren.sound() == tone && siren.start() == start);
        state.sound = sound;
        play(sound);
    }

    private static void stop(Playing state) {
        if (state.sound != null) {
            state.sound.end();
            state.sound = null;
        }
        state.cycle = -1;
    }

    // --- Speakers ---

    @Override
    public void note(SpeakerBlockEntity speaker, int instrument, int note) {
        Holder<SoundEvent> event = SpeakerBlockEntity.INSTRUMENTS[Math.clamp(instrument, 0, SpeakerBlockEntity.INSTRUMENTS.length - 1)].getSoundEvent();
        play(new DeviceSound(event.value(), SoundSource.RECORDS, origin(speaker, 0.15), speaker.volume() / 100.0F, speaker.range(),
                NoteBlock.getPitchFromNote(Math.clamp(note, 0, 24)), () -> !speaker.isRemoved()));
    }

    @Override
    public void audioChunk(String hash, int index, int total, byte[] bytes) {
        AudioCache.chunk(hash, index, total, bytes);
    }

    private void speaker(SpeakerBlockEntity speaker) {
        Playing state = state(speaker);
        Level level = speaker.getLevel();
        if (level == null || !speaker.playing() || speaker.playingSource() == SpeakerBlockEntity.Source.NOTE) {
            stop(state);
            state.play = -1;
            return;
        }
        if (state.play != speaker.play()) {
            stop(state);
            state.play = speaker.play();
            state.started = false;
            state.reported = false;
            state.fetching = false;
        }
        if (state.started) {
            return;
        }
        // Out of its range: nothing fetched or played (it starts where it should be if the player comes closer).
        if (!inRange(speaker, speaker.range())) {
            return;
        }
        if (speaker.playingSource() == SpeakerBlockEntity.Source.FILE) {
            byte[] bytes = AudioCache.file(speaker.hash());
            if (bytes == null) {
                if (AudioCache.missing(speaker.hash())) {
                    state.started = true;
                    report(speaker, state, 0, ElclMessage.of("ELC2402", speaker.playingName()).text());
                } else {
                    AudioCache.request(speaker.hash());
                }
                return;
            }
            start(speaker, state, bytes, speaker.lengthTicks() / 20.0);
            return;
        }
        // A URL.
        if (TerminalSettings.neverPlayWebAudio()) {
            state.started = true;
            return;
        }
        if (state.fetching) {
            return;
        }
        state.fetching = true;
        int play = speaker.play();
        String url = speaker.playingUrl();
        AudioCache.url(url, speaker.maxBytes()).whenComplete((bytes, failure) -> Minecraft.getInstance().execute(() -> {
            if (speaker.isRemoved() || speaker.play() != play || state.play != play || state.started) {
                return;
            }
            if (failure != null) {
                state.started = true;
                Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                report(speaker, state, 0, cause.getMessage() != null ? cause.getMessage() : "web: no answer");
                return;
            }
            AudioFiles.Format format = AudioFiles.format(bytes);
            double seconds = format != null ? AudioFiles.seconds(bytes, format) : 0;
            if (seconds <= 0) {
                state.started = true;
                report(speaker, state, 0, ElclMessage.of("ELC2406", url).text());
                return;
            }
            if (seconds > speaker.maxSeconds()) {
                state.started = true;
                report(speaker, state, 0, ElclMessage.of("ELC2403", url, speaker.maxSeconds() + " s").text());
                return;
            }
            report(speaker, state, (float) seconds, "");
            start(speaker, state, bytes, seconds);
        }));
    }

    // Starts a file or URL where the play should be by now (looped: within its length), unless a play that isn't looped
    // is already over.
    private void start(SpeakerBlockEntity speaker, Playing state, byte[] bytes, double seconds) {
        Level level = speaker.getLevel();
        if (level == null) {
            return;
        }
        state.started = true;
        double elapsed = Math.max(0, level.getGameTime() - speaker.started()) / 20.0;
        if (seconds > 0) {
            if (speaker.loop()) {
                elapsed %= seconds;
            } else if (elapsed >= seconds) {
                return;
            }
        }
        int play = speaker.play();
        double max = speaker.playingSource() == SpeakerBlockEntity.Source.URL ? speaker.maxSeconds() : seconds > 0 ? seconds + 1 : 3_600;
        StreamSound sound = new StreamSound(bytes, elapsed, speaker.loop(), max, origin(speaker, 0.15), speaker.volume() / 100.0F, speaker.range(),
                () -> !speaker.isRemoved() && speaker.playing() && speaker.play() == play);
        state.sound = sound;
        play(sound);
    }

    private static void report(SpeakerBlockEntity speaker, Playing state, float seconds, String problem) {
        if (state.reported) {
            return;
        }
        state.reported = true;
        ClientPacketDistributor.sendToServer(new AudioPayloads.Report(speaker.getBlockPos(), speaker.play(), seconds,
                problem.length() > 200 ? problem.substring(0, 200) : problem));
    }
}
