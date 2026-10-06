/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

// The instruments PLYNOTE names (*HARP ... *PLING), in SpeakerBlockEntity.INSTRUMENTS' order, how many notes a
// sequence holds, how many parts PLYMID's MAP hands out, and how a note travels as a block event. Plain values, so
// the command schemas (BuiltinCommands) load without the game.
public final class NoteInstruments {
    public static final String[] NAMES = { "*HARP", "*BASS", "*SNARE", "*HAT", "*BASSDRUM", "*BELL", "*FLUTE", "*CHIME", "*GUITAR", "*XYLOPHONE",
            "*IRONXYLO", "*COWBELL", "*DIDGERIDOO", "*BIT", "*BANJO", "*PLING" };
    public static final int MAX_NOTES = 32, MAX_MIDI_PARTS = 32;

    private NoteInstruments() {}

    // A note's block event (two bytes): the instrument (0-15) and note (0-24) in the low bits, how far its velocity
    // (1-127) is below 127 in the high ones - all clear, as PLYNOTE sends them: full.
    public static int eventA(int instrument, int velocity) {
        return instrument & 0x0F | (127 - Math.clamp(velocity, 1, 127)) >> 3 << 4;
    }

    public static int eventB(int note, int velocity) {
        return Math.clamp(note, 0, 24) | ((127 - Math.clamp(velocity, 1, 127)) & 7) << 5;
    }

    public static int eventInstrument(int a) {
        return a & 0x0F;
    }

    public static int eventNote(int b) {
        return b & 0x1F;
    }

    public static int eventVelocity(int a, int b) {
        return 127 - ((a >> 4 & 0x0F) << 3 | b >> 5 & 7);
    }

    // The index of *HARP ... *PLING, or -1.
    public static int of(String special) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(special)) {
                return i;
            }
        }
        return -1;
    }
}
