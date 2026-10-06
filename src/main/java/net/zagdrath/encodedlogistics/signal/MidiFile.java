/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// A Standard MIDI File (format 0 or 1; RIFF RMID-wrapped too) read into note block notes for a Speaker. Every note-on is
// timed by the file's tempo map (its tempo changes, from any track) - or its SMPTE frames - and rounded to the game tick
// it falls on; its instrument is the vanilla note block one closest to its channel's General MIDI program (channel 10:
// the drum kit, by drum note), its key moved by octaves into that instrument's two octaves, and its velocity kept for the
// volume. Note-offs are dropped: a note block sound rings out on its own. Not a MIDI file: ELC2409; format 2: ELC2410.
// Plain Java (no game classes), so it's tested on its own.
public final class MidiFile {
    // The instruments, as indexes into NoteInstruments.NAMES / SpeakerBlockEntity.INSTRUMENTS.
    static final int HARP = 0, BASS = 1, SNARE = 2, HAT = 3, BASSDRUM = 4, BELL = 5, FLUTE = 6, CHIME = 7, GUITAR = 8, XYLOPHONE = 9,
            IRON_XYLOPHONE = 10, COW_BELL = 11, DIDGERIDOO = 12, BIT = 13, BANJO = 14, PLING = 15;
    // The MIDI key each instrument's note 0 sounds (its lowest; note 24 is two octaves up): harp F#3 (54), bass and
    // didgeridoo two octaves down, guitar one down, flute and cow bell one up, bell, chime and xylophone two up.
    private static final int[] LOWEST = { 54, 30, 54, 54, 54, 78, 66, 78, 42, 78, 54, 66, 30, 54, 54, 54 };
    // General MIDI programs 0-127, eight to a family, to the closest instrument.
    private static final int[] PROGRAMS = {
            // Piano: grand, bright, electric grand, honky-tonk, electric pianos 1-2 (pling is an electric piano),
            // harpsichord, clavinet.
            HARP, HARP, PLING, HARP, PLING, PLING, HARP, HARP,
            // Chromatic percussion: celesta, glockenspiel, music box, vibraphone, marimba, xylophone, tubular bells,
            // dulcimer.
            BELL, BELL, BELL, IRON_XYLOPHONE, XYLOPHONE, XYLOPHONE, CHIME, BANJO,
            // Organs, accordion, harmonica, tango accordion: held reeds and pipes.
            FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, FLUTE,
            // Guitars.
            GUITAR, GUITAR, GUITAR, GUITAR, GUITAR, GUITAR, GUITAR, GUITAR,
            // Basses.
            BASS, BASS, BASS, BASS, BASS, BASS, BASS, BASS,
            // Strings: violin, viola, cello, contrabass, tremolo, pizzicato, harp, timpani.
            FLUTE, FLUTE, BASS, BASS, FLUTE, HARP, HARP, BASS,
            // Ensembles and voices; the orchestra hit.
            FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, BIT,
            // Brass: trumpet, trombone, tuba (didgeridoo), muted trumpet, French horn, sections.
            BIT, BIT, DIDGERIDOO, BIT, BIT, BIT, BIT, BIT,
            // Reeds: saxophones, oboe, English horn, bassoon (didgeridoo), clarinet.
            FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, DIDGERIDOO, FLUTE,
            // Pipes.
            FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, FLUTE, FLUTE,
            // Synth leads.
            BIT, BIT, BIT, BIT, BIT, BIT, BIT, BIT,
            // Synth pads.
            PLING, PLING, PLING, PLING, PLING, PLING, PLING, PLING,
            // Synth effects.
            PLING, PLING, PLING, CHIME, PLING, PLING, PLING, PLING,
            // Ethnic: sitar, banjo, shamisen, koto, kalimba, bagpipe (didgeridoo), fiddle, shanai.
            BANJO, BANJO, BANJO, HARP, XYLOPHONE, DIDGERIDOO, FLUTE, FLUTE,
            // Percussive: tinkle bell, agogo, steel drums, woodblock, taiko, melodic tom, synth drum, reverse cymbal.
            BELL, COW_BELL, IRON_XYLOPHONE, HAT, BASSDRUM, BASSDRUM, BASSDRUM, SNARE,
            // Sound effects: fret noise, breath, seashore, bird, telephone, helicopter, applause, gunshot.
            HAT, HAT, SNARE, CHIME, BELL, SNARE, SNARE, BASSDRUM };
    private static final int DRUMS = 9;
    // A tick is 50 ms.
    private static final double MICROS_PER_TICK = 50_000.0;

    // A note to play: the game tick it falls on (from the start), the track (from 1) and channel (1-16) it's on, the
    // note block instrument and note (0-24) it plays as, and its velocity (1-127).
    public record Note(int tick, int track, int channel, int instrument, int pitch, int velocity) {}

    // A file read: its format, its tracks, every note in tick order, its length in ticks (to its last event, at least
    // one tick past its last note) and in seconds, and the channels (bit 0: channel 1) with notes on them.
    public record Song(int format, int tracks, List<Note> notes, int lengthTicks, double seconds, int channels) {
        public boolean hasChannel(int channel) {
            return channel >= 1 && channel <= 16 && (channels & 1 << channel - 1) != 0;
        }
    }

    private MidiFile() {}

    // A channel event or tempo change found in a track, at its MIDI tick: kind 0 tempo (value: microseconds per
    // quarter note), 1 program change, 2 note-on; order: the order they were read in (track by track).
    private record Event(long tick, int kind, int track, int channel, int value, int velocity, int order) {}

    // Reads a file (name: for its messages).
    public static Song read(String name, byte[] bytes) throws ElclException {
        try {
            return parse(unwrap(bytes), name);
        } catch (IndexOutOfBoundsException e) {
            throw new ElclException("ELC2409", name);
        }
    }

    // An RMID file (RIFF....RMID, its "data" chunk the MIDI file) or the file itself.
    private static byte[] unwrap(byte[] bytes) {
        if (bytes.length < 20 || !text(bytes, 0, "RIFF") || !text(bytes, 8, "RMID")) {
            return bytes;
        }
        int at = 12;
        while (at + 8 <= bytes.length) {
            int size = (bytes[at + 4] & 0xFF) | (bytes[at + 5] & 0xFF) << 8 | (bytes[at + 6] & 0xFF) << 16 | (bytes[at + 7] & 0xFF) << 24;
            if (text(bytes, at, "data")) {
                int from = at + 8, to = (int) Math.min(bytes.length, from + (size & 0xFFFFFFFFL));
                byte[] data = new byte[to - from];
                System.arraycopy(bytes, from, data, 0, data.length);
                return data;
            }
            at += 8 + size + (size & 1);
            if (size < 0) {
                break;
            }
        }
        return bytes;
    }

    private static Song parse(byte[] bytes, String name) throws ElclException {
        if (bytes.length < 14 || !text(bytes, 0, "MThd") || int32(bytes, 4) < 6) {
            throw new ElclException("ELC2409", name);
        }
        int format = int16(bytes, 8), declared = int16(bytes, 10), division = int16(bytes, 12);
        if (format == 2) {
            throw new ElclException("ELC2410", name, "2");
        }
        if (format > 2 || division == 0) {
            throw new ElclException("ELC2409", name);
        }
        List<Event> events = new ArrayList<>();
        int at = 8 + int32(bytes, 4), tracks = 0;
        long lastTick = 0;
        while (at + 8 <= bytes.length && tracks < declared) {
            int size = int32(bytes, at + 4);
            if (size < 0) {
                throw new ElclException("ELC2409", name);
            }
            if (!text(bytes, at, "MTrk")) {
                // Another kind of chunk: skipped.
                at += 8 + size;
                continue;
            }
            tracks++;
            int end = (int) Math.min(bytes.length, (long) at + 8 + size);
            long tick = track(bytes, at + 8, end, tracks, events);
            lastTick = Math.max(lastTick, tick);
            at = end;
        }
        if (tracks == 0 || format == 0 && tracks != 1) {
            throw new ElclException("ELC2409", name);
        }
        // By tick; at a tick, tempo changes first, then the rest as they were read (a program change after a note at the
        // same tick doesn't change that note).
        events.sort(Comparator.comparingLong(Event::tick).thenComparingInt(event -> event.kind() == 0 ? 0 : 1).thenComparingInt(Event::order));
        // MIDI ticks to microseconds: by the tempo map (500,000 us per quarter note until the first change), or SMPTE.
        boolean smpte = (division & 0x8000) != 0;
        double smpteMicros = 0;
        if (smpte) {
            int fps = -(byte) (division >> 8), perFrame = division & 0xFF;
            if (fps <= 0 || perFrame == 0) {
                throw new ElclException("ELC2409", name);
            }
            smpteMicros = 1_000_000.0 / ((fps == 29 ? 29.97 : fps) * perFrame);
        }
        double micros = 0, perTick = smpte ? smpteMicros : 500_000.0 / division;
        long at0 = 0;
        int[] programs = new int[16];
        List<Note> notes = new ArrayList<>();
        int channels = 0;
        for (Event event : events) {
            micros += (event.tick() - at0) * perTick;
            at0 = event.tick();
            switch (event.kind()) {
                case 0 -> {
                    if (!smpte && event.value() > 0) {
                        perTick = (double) event.value() / division;
                    }
                }
                case 1 -> programs[event.channel()] = event.value();
                default -> {
                    int instrument = event.channel() == DRUMS ? drum(event.value()) : PROGRAMS[programs[event.channel()] & 0x7F];
                    int pitch = event.channel() == DRUMS ? drumPitch(event.value()) : pitch(instrument, event.value());
                    notes.add(new Note((int) Math.min(Integer.MAX_VALUE, Math.round(micros / MICROS_PER_TICK)), event.track(), event.channel() + 1,
                            instrument, pitch, event.velocity()));
                    channels |= 1 << event.channel();
                }
            }
        }
        // To the last event (an end of track, usually).
        micros += (lastTick - at0) * perTick;
        notes.sort(Comparator.comparingInt(Note::tick));
        int length = (int) Math.min(Integer.MAX_VALUE, Math.round(micros / MICROS_PER_TICK));
        if (!notes.isEmpty()) {
            length = Math.max(length, notes.getLast().tick() + 1);
        }
        return new Song(format, tracks, List.copyOf(notes), Math.max(1, length), Math.max(micros, length * MICROS_PER_TICK) / 1_000_000.0, channels);
    }

    // One track's events from..to: tempo changes, program changes and note-ons (with running status); its last tick.
    private static long track(byte[] bytes, int from, int to, int track, List<Event> events) {
        int at = from, status = 0;
        long tick = 0;
        while (at < to) {
            long delta = 0;
            int read;
            do {
                read = bytes[at++] & 0xFF;
                delta = delta << 7 | read & 0x7F;
            } while ((read & 0x80) != 0 && at < to);
            tick += delta;
            if (at >= to) {
                break;
            }
            int next = bytes[at] & 0xFF;
            if (next >= 0x80) {
                at++;
                if (next < 0xF0) {
                    status = next;
                }
            } else {
                // Running status: the last channel event's.
                if (status == 0) {
                    throw new IndexOutOfBoundsException("No running status");
                }
                next = status;
            }
            if (next == 0xFF) {
                int type = bytes[at++] & 0xFF;
                long length = 0;
                do {
                    read = bytes[at++] & 0xFF;
                    length = length << 7 | read & 0x7F;
                } while ((read & 0x80) != 0);
                if (type == 0x51 && length == 3) {
                    int tempo = (bytes[at] & 0xFF) << 16 | (bytes[at + 1] & 0xFF) << 8 | bytes[at + 2] & 0xFF;
                    events.add(new Event(tick, 0, track, 0, tempo, 0, events.size()));
                }
                if (type == 0x2F) {
                    break;
                }
                at += (int) length;
                continue;
            }
            if (next == 0xF0 || next == 0xF7) {
                long length = 0;
                do {
                    read = bytes[at++] & 0xFF;
                    length = length << 7 | read & 0x7F;
                } while ((read & 0x80) != 0);
                at += (int) length;
                continue;
            }
            if (next > 0xF0) {
                // A system message out of place: nothing to read past it.
                throw new IndexOutOfBoundsException("System message in a track");
            }
            int channel = next & 0x0F, data1 = bytes[at++] & 0x7F;
            switch (next & 0xF0) {
                case 0xC0 -> events.add(new Event(tick, 1, track, channel, data1, 0, events.size()));
                case 0xD0 -> {}
                default -> {
                    int data2 = bytes[at++] & 0x7F;
                    if ((next & 0xF0) == 0x90 && data2 > 0) {
                        events.add(new Event(tick, 2, track, channel, data1, data2, events.size()));
                    }
                }
            }
        }
        return tick;
    }

    // A key moved by octaves into an instrument's two octaves: 0-24.
    public static int pitch(int instrument, int key) {
        int note = key - LOWEST[instrument];
        while (note < 0) {
            note += 12;
        }
        while (note > 24) {
            note -= 12;
        }
        return note;
    }

    // The instrument for a General MIDI program (0-127).
    public static int instrument(int program) {
        return PROGRAMS[program & 0x7F];
    }

    // The drum kit (channel 10), by drum note: kicks and toms the bass drum, snares, claps and cymbals the snare,
    // hi-hats, rides and small shakers the hat, the cowbell its own; anything else the hat.
    public static int drum(int key) {
        return switch (key) {
            case 35, 36, 41, 43, 45, 47, 48, 50, 60, 61, 62, 63, 64, 65, 66, 86, 87 -> BASSDRUM;
            case 37, 38, 39, 40, 49, 52, 55, 57, 58, 28, 31 -> SNARE;
            case 56 -> COW_BELL;
            case 81 -> BELL;
            default -> HAT;
        };
    }

    // A drum's note: toms and congas rising with the drum, the rest at their natural pitch.
    public static int drumPitch(int key) {
        return switch (key) {
            case 35 -> 4;
            case 36 -> 6;
            case 41 -> 8;
            case 43 -> 10;
            case 45 -> 12;
            case 47 -> 14;
            case 48 -> 16;
            case 50 -> 18;
            case 60, 62, 65 -> 20;
            case 61, 63, 64, 66 -> 16;
            case 42, 44 -> 14;
            case 46 -> 10;
            case 49, 52, 55, 57 -> 18;
            case 81 -> 24;
            default -> 12;
        };
    }

    private static boolean text(byte[] bytes, int at, String wanted) {
        if (at + wanted.length() > bytes.length) {
            return false;
        }
        for (int i = 0; i < wanted.length(); i++) {
            if (bytes[at + i] != wanted.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int int16(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) << 8 | bytes[at + 1] & 0xFF;
    }

    private static int int32(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16 | (bytes[at + 2] & 0xFF) << 8 | bytes[at + 3] & 0xFF;
    }
}
