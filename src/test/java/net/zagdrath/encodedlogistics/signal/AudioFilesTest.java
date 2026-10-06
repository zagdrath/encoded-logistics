/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

// Speakers' audio checks: the siren tones read as OGG Vorbis of their own lengths (which SirenSound loops by); MP3
// frames are added up; anything else isn't audio; web hosts are allowed only as listed (and their subdomains), over
// http(s) only.
class AudioFilesTest {
    static final Path SOUNDS = Path.of("src/main/resources/assets/encodedlogistics/sounds/block/siren");

    // count MPEG-1 Layer III frames, 128 kbit/s, 44.1 kHz, joint stereo, silent: 1152 samples (26.12 ms) each.
    static byte[] mp3(int count) {
        int size = 417;
        byte[] bytes = new byte[size * count];
        for (int frame = 0; frame < count; frame++) {
            bytes[frame * size] = (byte) 0xFF;
            bytes[frame * size + 1] = (byte) 0xFB;
            bytes[frame * size + 2] = (byte) 0x90;
            bytes[frame * size + 3] = (byte) 0x64;
        }
        return bytes;
    }

    @Test
    void sirenTonesAreTheirLengths() throws IOException {
        for (SirenSound sound : SirenSound.values()) {
            if (sound == SirenSound.NONE) {
                continue;
            }
            byte[] bytes = Files.readAllBytes(SOUNDS.resolve(sound.name().toLowerCase() + ".ogg"));
            assertEquals(AudioFiles.Format.OGG, AudioFiles.format(bytes), sound.name());
            assertEquals(sound.ticks() / 20.0, AudioFiles.seconds(bytes, AudioFiles.Format.OGG), 0.001, sound.name());
        }
    }

    @Test
    void mp3FramesAddUp() {
        byte[] bytes = mp3(100);
        assertEquals(AudioFiles.Format.MP3, AudioFiles.format(bytes));
        assertEquals(100 * 1152 / 44100.0, AudioFiles.seconds(bytes, AudioFiles.Format.MP3), 0.01);
    }

    @Test
    void otherBytesAreNotAudio() {
        assertNull(AudioFiles.format(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 }));
        assertNull(AudioFiles.format("RIFF....WAVEfmt ".getBytes()));
        // An Ogg stream that isn't Vorbis (Opus).
        byte[] opus = new byte[100];
        System.arraycopy("OggS".getBytes(), 0, opus, 0, 4);
        System.arraycopy("OpusHead".getBytes(), 0, opus, 28, 8);
        assertNull(AudioFiles.format(opus));
    }

    @Test
    void hostsAsListed() {
        List<String> allowed = List.of("example.com", " Sounds.Example.org ");
        assertTrue(AudioFiles.hostAllowed("example.com", allowed));
        assertTrue(AudioFiles.hostAllowed("cdn.example.com", allowed));
        assertTrue(AudioFiles.hostAllowed("sounds.example.org", allowed));
        assertFalse(AudioFiles.hostAllowed("example.org", allowed));
        assertFalse(AudioFiles.hostAllowed("badexample.com", allowed));
        assertFalse(AudioFiles.hostAllowed("example.com", List.of()));
    }

    @Test
    void urlsAreHttpOnly() {
        assertEquals("example.com", AudioFiles.host("https://Example.com/a.ogg"));
        assertEquals("example.com", AudioFiles.host("http://example.com:8080/a.mp3"));
        assertNull(AudioFiles.host("ftp://example.com/a.ogg"));
        assertNull(AudioFiles.host("file:///etc/passwd"));
        assertNull(AudioFiles.host("https://user@example.com/a.ogg"));
        assertNull(AudioFiles.host("not a url"));
        assertTrue(AudioFiles.isUrl("HTTPS://example.com/a.ogg"));
        assertFalse(AudioFiles.isUrl("alarm.ogg"));
    }
}
