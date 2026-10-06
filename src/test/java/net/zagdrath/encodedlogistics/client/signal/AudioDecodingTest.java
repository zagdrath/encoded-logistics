/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

// A Speaker's audio as the sound engine gets it: mono 16-bit at the file's rate, all of it; started part way in, the
// rest; looped, on past its end; cut at the longest allowed; MP3 decoded too; anything else refused.
class AudioDecodingTest {
    private static final Path WAIL = Path.of("src/main/resources/assets/encodedlogistics/sounds/block/siren/wail.ogg");

    // Frames read until the stream ends, or limit.
    private static long frames(AudioDecoding.MonoStream stream, long limit) throws IOException {
        long frames = 0;
        while (frames < limit) {
            ByteBuffer buffer = stream.read(16384);
            if (buffer.remaining() == 0) {
                break;
            }
            frames += buffer.remaining() / 2;
        }
        return frames;
    }

    @Test
    void wholeFileMono() throws IOException {
        try (AudioDecoding.MonoStream stream = new AudioDecoding.MonoStream(Files.readAllBytes(WAIL), 0, false, 3_600)) {
            assertEquals(1, stream.getFormat().getChannels());
            assertEquals(16, stream.getFormat().getSampleSizeInBits());
            assertEquals(44_100, (int) stream.getFormat().getSampleRate());
            assertEquals(4.0 * 44_100, frames(stream, Long.MAX_VALUE), 1);
        }
    }

    @Test
    void startsPartWay() throws IOException {
        try (AudioDecoding.MonoStream stream = new AudioDecoding.MonoStream(Files.readAllBytes(WAIL), 1.5, false, 3_600)) {
            assertEquals(2.5 * 44_100, frames(stream, Long.MAX_VALUE), 1);
        }
    }

    @Test
    void loopsAndCuts() throws IOException {
        try (AudioDecoding.MonoStream looped = new AudioDecoding.MonoStream(Files.readAllBytes(WAIL), 0, true, 3_600)) {
            assertTrue(frames(looped, 10 * 44_100) >= 10 * 44_100, "Loop ended");
        }
        try (AudioDecoding.MonoStream cut = new AudioDecoding.MonoStream(Files.readAllBytes(WAIL), 0, false, 1.0)) {
            assertEquals(44_100, frames(cut, Long.MAX_VALUE), 1);
        }
    }

    @Test
    void mp3Decodes() throws IOException {
        // 40 silent MPEG-1 Layer III frames, 128 kbit/s, 44.1 kHz: 1152 samples each.
        byte[] bytes = new byte[417 * 40];
        for (int frame = 0; frame < 40; frame++) {
            bytes[frame * 417] = (byte) 0xFF;
            bytes[frame * 417 + 1] = (byte) 0xFB;
            bytes[frame * 417 + 2] = (byte) 0x90;
            bytes[frame * 417 + 3] = (byte) 0x64;
        }
        try (AudioDecoding.MonoStream stream = new AudioDecoding.MonoStream(bytes, 0, false, 3_600)) {
            assertEquals(44_100, (int) stream.getFormat().getSampleRate());
            assertEquals(40 * 1152, frames(stream, Long.MAX_VALUE));
        }
    }

    @Test
    void refusesOtherBytes() {
        assertThrows(IOException.class, () -> new AudioDecoding.MonoStream(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 }, 0, false, 10));
    }
}
