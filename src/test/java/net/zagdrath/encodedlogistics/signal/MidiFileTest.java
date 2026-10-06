/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// MIDI files as Speakers play them: format 0 and 1 timed by their tempo maps (a change in any track counts for all) or
// SMPTE frames, to the game tick; General MIDI programs and channel 10's drums to note block instruments; keys moved by
// octaves into range; velocity kept and packed into the block event; format 2 and anything else refused; the parts map
// read and written; at most so many notes a tick, the loudest.
class MidiFileTest {
    // --- Writing test files ---

    // A track's events: each a delta time (MIDI ticks) and its bytes.
    static final class Track {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Track event(int delta, int... data) {
            varLength(bytes, delta);
            for (int b : data) {
                bytes.write(b);
            }
            return this;
        }

        Track tempo(int delta, int microsPerQuarter) {
            return event(delta, 0xFF, 0x51, 3, microsPerQuarter >> 16 & 0xFF, microsPerQuarter >> 8 & 0xFF, microsPerQuarter & 0xFF);
        }

        Track program(int delta, int channel, int program) {
            return event(delta, 0xC0 | channel - 1, program);
        }

        Track on(int delta, int channel, int key, int velocity) {
            return event(delta, 0x90 | channel - 1, key, velocity);
        }

        Track off(int delta, int channel, int key) {
            return event(delta, 0x80 | channel - 1, key, 0);
        }

        Track end(int delta) {
            return event(delta, 0xFF, 0x2F, 0);
        }
    }

    static void varLength(ByteArrayOutputStream out, int value) {
        int buffer = value & 0x7F;
        while ((value >>= 7) > 0) {
            buffer <<= 8;
            buffer |= value & 0x7F | 0x80;
        }
        while (true) {
            out.write(buffer & 0xFF);
            if ((buffer & 0x80) == 0) {
                break;
            }
            buffer >>= 8;
        }
    }

    static byte[] file(int format, int division, Track... tracks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("MThd".getBytes());
        out.writeBytes(new byte[] { 0, 0, 0, 6, 0, (byte) format, 0, (byte) tracks.length, (byte) (division >> 8), (byte) division });
        for (Track track : tracks) {
            byte[] data = track.bytes.toByteArray();
            out.writeBytes("MTrk".getBytes());
            out.writeBytes(new byte[] { (byte) (data.length >> 24), (byte) (data.length >> 16), (byte) (data.length >> 8), (byte) data.length });
            out.writeBytes(data);
        }
        return out.toByteArray();
    }

    static List<Integer> ticks(MidiFile.Song song) {
        List<Integer> ticks = new ArrayList<>();
        for (MidiFile.Note note : song.notes()) {
            ticks.add(note.tick());
        }
        return ticks;
    }

    static MidiFile.Song read(byte[] bytes) throws ElclException {
        return MidiFile.read("test.mid", bytes);
    }

    // --- Timing ---

    @Test
    void formatZeroAtTheDefaultTempo() throws ElclException {
        // 480 ticks a quarter note at 120 bpm (the default): a quarter note is 0.5 s, 10 game ticks.
        MidiFile.Song song = read(file(0, 480, new Track()
                .on(0, 1, 66, 100).off(240, 1, 66)
                .on(240, 1, 66, 100).off(480, 1, 66)
                .on(0, 1, 66, 100).on(120, 1, 70, 90).end(360)));
        assertEquals(0, song.format());
        assertEquals(1, song.tracks());
        assertEquals(List.of(0, 10, 20, 23), ticks(song));
        // To the end of the track: 1440 ticks, 1.5 s.
        assertEquals(30, song.lengthTicks());
        assertEquals(1.5, song.seconds(), 0.001);
        assertTrue(song.hasChannel(1));
        assertFalse(song.hasChannel(2));
    }

    @Test
    void formatOneTempoChangesCountForEveryTrack() throws ElclException {
        // The tempo track: 120 bpm, then 60 bpm from the third beat. The notes, a beat apart, in another track.
        Track tempo = new Track().tempo(0, 500_000).tempo(960, 1_000_000).end(0);
        Track tune = new Track().on(0, 1, 60, 100).on(480, 1, 62, 100).on(480, 1, 64, 100).on(480, 1, 65, 100).on(480, 1, 67, 100).end(480);
        MidiFile.Song song = read(file(1, 480, tempo, tune));
        assertEquals(1, song.format());
        assertEquals(2, song.tracks());
        // 0, 0.5 s, 1 s, then a second a beat: 2 s, 3 s.
        assertEquals(List.of(0, 10, 20, 40, 60), ticks(song));
        assertEquals(80, song.lengthTicks());
        for (MidiFile.Note note : song.notes()) {
            assertEquals(2, note.track());
        }
        // A tempo change in a later track, as some files have it.
        MidiFile.Song late = read(file(1, 480, new Track().on(0, 1, 60, 100).on(960, 1, 60, 100).end(0), new Track().tempo(480, 250_000).end(0)));
        // A beat at 0.5 s, then one at 0.25 s: 0.75 s.
        assertEquals(List.of(0, 15), ticks(late));
    }

    @Test
    void timingRoundsToTheNearestTick() throws ElclException {
        // 96 ticks a beat at 100 bpm (600,000 us), 6,250 us a tick: notes at 0, 24, 56 and 96 ticks are at 0, 0.15,
        // 0.35 and 0.6 s - game ticks 0, 3, 7 and 12.
        MidiFile.Song song = read(file(0, 96, new Track().tempo(0, 600_000).on(0, 1, 60, 80).on(24, 1, 60, 80).on(32, 1, 60, 80).on(40, 1, 60, 80).end(0)));
        assertEquals(List.of(0, 3, 7, 12), ticks(song));
    }

    @Test
    void smpteFramesTimeNotes() throws ElclException {
        // 25 fps, 40 ticks a frame: 1000 ticks a second; tempo changes don't count.
        int division = (-25 & 0xFF) << 8 | 40;
        MidiFile.Song song = read(file(0, division, new Track().tempo(0, 1_000_000).on(0, 1, 60, 80).on(500, 1, 60, 80).on(1000, 1, 60, 80).end(500)));
        assertEquals(List.of(0, 10, 30), ticks(song));
        assertEquals(40, song.lengthTicks());
    }

    @Test
    void runningStatusAndNoteOnsAtVelocityZero() throws ElclException {
        // 0x90 once, then key / velocity pairs; velocity 0 is a note-off.
        MidiFile.Song song = read(file(0, 480, new Track().event(0, 0x90, 60, 100).event(480, 60, 0).event(0, 64, 50).event(480, 67, 1).end(0)));
        assertEquals(List.of(0, 10, 20), ticks(song));
        assertEquals(List.of(100, 50, 1), song.notes().stream().map(MidiFile.Note::velocity).toList());
    }

    @Test
    void sysExAndOtherMetaEventsAreSkipped() throws ElclException {
        MidiFile.Song song = read(file(0, 480, new Track()
                .event(0, 0xFF, 0x03, 4, 'S', 'o', 'n', 'g')
                .event(0, 0xF0, 3, 0x7E, 0x7F, 0xF7)
                .on(0, 1, 60, 100).end(480)));
        assertEquals(List.of(0), ticks(song));
    }

    // --- Instruments ---

    @Test
    void programsMapToTheClosestInstrument() throws ElclException {
        assertEquals(MidiFile.HARP, MidiFile.instrument(0));
        assertEquals(MidiFile.PLING, MidiFile.instrument(4));
        assertEquals(MidiFile.BELL, MidiFile.instrument(9));
        assertEquals(MidiFile.IRON_XYLOPHONE, MidiFile.instrument(11));
        assertEquals(MidiFile.XYLOPHONE, MidiFile.instrument(13));
        assertEquals(MidiFile.CHIME, MidiFile.instrument(14));
        assertEquals(MidiFile.GUITAR, MidiFile.instrument(25));
        assertEquals(MidiFile.BASS, MidiFile.instrument(33));
        assertEquals(MidiFile.FLUTE, MidiFile.instrument(73));
        assertEquals(MidiFile.FLUTE, MidiFile.instrument(65));
        assertEquals(MidiFile.BIT, MidiFile.instrument(56));
        assertEquals(MidiFile.BIT, MidiFile.instrument(81));
        assertEquals(MidiFile.BANJO, MidiFile.instrument(105));
        assertEquals(MidiFile.COW_BELL, MidiFile.instrument(113));
        // Program changes in the file pick the instrument, per channel, from where they come.
        MidiFile.Song song = read(file(0, 480, new Track()
                .on(0, 1, 66, 100)
                .program(0, 1, 33).program(0, 2, 73)
                .on(480, 1, 42, 100).on(0, 2, 78, 100)
                .end(0)));
        assertEquals(List.of(MidiFile.HARP, MidiFile.BASS, MidiFile.FLUTE), song.notes().stream().map(MidiFile.Note::instrument).toList());
        assertEquals(List.of(12, 12, 12), song.notes().stream().map(MidiFile.Note::pitch).toList());
        assertEquals("*FLUTE", NoteInstruments.NAMES[MidiFile.FLUTE]);
        assertEquals("*IRONXYLO", NoteInstruments.NAMES[MidiFile.IRON_XYLOPHONE]);
    }

    @Test
    void channelTenIsTheDrumKit() throws ElclException {
        MidiFile.Song song = read(file(0, 480, new Track()
                .program(0, 10, 40)
                .on(0, 10, 36, 120).on(0, 10, 38, 110).on(0, 10, 42, 90).on(0, 10, 56, 80).on(0, 10, 45, 80).on(0, 10, 50, 80)
                .end(0)));
        assertEquals(List.of(MidiFile.BASSDRUM, MidiFile.SNARE, MidiFile.HAT, MidiFile.COW_BELL, MidiFile.BASSDRUM, MidiFile.BASSDRUM),
                song.notes().stream().map(MidiFile.Note::instrument).toList());
        // Toms rise with the drum.
        assertTrue(song.notes().get(5).pitch() > song.notes().get(4).pitch());
        assertTrue(song.hasChannel(10));
        assertEquals(10, song.notes().getFirst().channel());
    }

    @Test
    void keysAreMovedByOctavesIntoRange() {
        // Harp: F#3 (54) is note 0, F#5 (78) note 24.
        assertEquals(0, MidiFile.pitch(MidiFile.HARP, 54));
        assertEquals(24, MidiFile.pitch(MidiFile.HARP, 78));
        assertEquals(12, MidiFile.pitch(MidiFile.HARP, 66));
        // Below and above: whole octaves up or down, the pitch class kept.
        assertEquals(0, MidiFile.pitch(MidiFile.HARP, 30));
        assertEquals(1, MidiFile.pitch(MidiFile.HARP, 19));
        assertEquals(22, MidiFile.pitch(MidiFile.HARP, 100));
        assertEquals(13, MidiFile.pitch(MidiFile.HARP, 127));
        // Bass two octaves lower, flute one higher, bell two.
        assertEquals(12, MidiFile.pitch(MidiFile.BASS, 42));
        assertEquals(24, MidiFile.pitch(MidiFile.BASS, 90));
        assertEquals(0, MidiFile.pitch(MidiFile.FLUTE, 66));
        assertEquals(12, MidiFile.pitch(MidiFile.BELL, 90));
        assertEquals(0, MidiFile.pitch(MidiFile.BELL, 18));
        for (int instrument = 0; instrument < 16; instrument++) {
            for (int key = 0; key < 128; key++) {
                int pitch = MidiFile.pitch(instrument, key);
                assertTrue(pitch >= 0 && pitch <= 24, instrument + " " + key);
            }
        }
    }

    // --- Velocity ---

    @Test
    void velocityTravelsWithTheNote() {
        for (int instrument = 0; instrument < 16; instrument++) {
            for (int note = 0; note <= 24; note++) {
                for (int velocity = 1; velocity <= 127; velocity++) {
                    int a = NoteInstruments.eventA(instrument, velocity), b = NoteInstruments.eventB(note, velocity);
                    assertTrue(a >= 0 && a <= 255 && b >= 0 && b <= 255);
                    assertEquals(instrument, NoteInstruments.eventInstrument(a));
                    assertEquals(note, NoteInstruments.eventNote(b));
                    assertEquals(velocity, NoteInstruments.eventVelocity(a, b));
                }
            }
        }
        // PLYNOTE's events (instrument, note): full.
        assertEquals(127, NoteInstruments.eventVelocity(5, 12));
    }

    // --- Refused ---

    @Test
    void formatTwoAndOtherFilesAreRefused() {
        ElclException two = assertThrows(ElclException.class, () -> read(file(2, 480, new Track().end(0), new Track().end(0))));
        assertEquals("ELC2410", two.elclMessage().id());
        for (byte[] bytes : List.of("OggS not a midi file at all".getBytes(), new byte[0], "MThd".getBytes(),
                // A header and a track cut short.
                java.util.Arrays.copyOf(file(0, 480, new Track().on(0, 1, 60, 100).end(0)), 25),
                // No division.
                file(0, 0, new Track().end(0)))) {
            ElclException e = assertThrows(ElclException.class, () -> read(bytes));
            assertEquals("ELC2409", e.elclMessage().id());
        }
    }

    @Test
    void rmidFilesAreRead() throws ElclException {
        byte[] midi = file(0, 480, new Track().on(0, 1, 60, 100).end(480));
        ByteArrayOutputStream riff = new ByteArrayOutputStream();
        riff.writeBytes("RIFF".getBytes());
        int size = 4 + 8 + midi.length;
        riff.writeBytes(new byte[] { (byte) size, (byte) (size >> 8), (byte) (size >> 16), (byte) (size >> 24) });
        riff.writeBytes("RMIDdata".getBytes());
        riff.writeBytes(new byte[] { (byte) midi.length, (byte) (midi.length >> 8), 0, 0 });
        riff.writeBytes(midi);
        assertEquals(List.of(0), ticks(read(riff.toByteArray())));
    }

    // --- Parts map ---

    @Test
    void partsMapReadsAndWrites() throws ElclException {
        List<MidiParts.Part> parts = MidiParts.parse("(1 spk01) (10 SPK04), *ALL SPK02", "MAP");
        assertEquals(List.of(new MidiParts.Part(1, "SPK01"), new MidiParts.Part(10, "SPK04"), new MidiParts.Part(0, "SPK02")), parts);
        assertEquals("1 SPK01, 10 SPK04, *ALL SPK02", MidiParts.text(parts));
        assertEquals(parts, MidiParts.parse(MidiParts.text(parts), "MAP"));
        assertEquals(List.of(), MidiParts.parse("  ", "MAP"));
        for (String bad : List.of("1", "0 SPK01", "X SPK01", "1 9SPK", "1000 SPK01", "1 SPK01 2")) {
            ElclException e = assertThrows(ElclException.class, () -> MidiParts.parse(bad, "MAP"), bad);
            assertEquals("ELC0103", e.elclMessage().id(), bad);
        }
        StringBuilder many = new StringBuilder();
        for (int i = 1; i <= MidiParts.MAX + 1; i++) {
            many.append(i).append(" SPK01 ");
        }
        assertThrows(ElclException.class, () -> MidiParts.parse(many.toString(), "MAP"));
    }

    @Test
    void theLoudestNotesAreKept() {
        List<MidiFile.Note> notes = new ArrayList<>();
        for (int velocity : new int[] { 40, 120, 80, 10, 100 }) {
            notes.add(new MidiFile.Note(0, 1, 1, 0, 12, velocity));
        }
        assertEquals(List.of(120, 100, 80), MidiParts.loudest(notes, 3).stream().map(MidiFile.Note::velocity).toList());
        assertEquals(notes, MidiParts.loudest(notes, 5));
    }
}
