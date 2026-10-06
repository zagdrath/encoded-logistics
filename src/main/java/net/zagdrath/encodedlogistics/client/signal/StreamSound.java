/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.signal;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// A Speaker playing a file or URL: streamed from its bytes (AudioDecoding.MonoStream) rather than a sound in the
// resource packs - a sound of its own is made up for it, marked to stream.
final class StreamSound extends DeviceSound {
    private static final Identifier ID = EncodedLogistics.id("speaker_audio");

    private final byte[] bytes;
    private final double skipSeconds, maxSeconds;
    private final boolean loop;

    StreamSound(byte[] bytes, double skipSeconds, boolean loop, double maxSeconds, Vec3 at, float loudness, int range, BooleanSupplier alive) {
        super(SoundEvent.createVariableRangeEvent(ID), SoundSource.RECORDS, at, loudness, range, 1.0F, alive);
        this.bytes = bytes;
        this.skipSeconds = skipSeconds;
        this.loop = loop;
        this.maxSeconds = maxSeconds;
    }

    @Override
    public @Nullable WeighedSoundEvents getOrResolve(SoundManager soundManager) {
        sound = new Sound(ID, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, true, false, 16);
        soundEvent = new WeighedSoundEvents(ID, null);
        return soundEvent;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new AudioDecoding.MonoStream(bytes, skipSeconds, loop, maxSeconds);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool());
    }
}
