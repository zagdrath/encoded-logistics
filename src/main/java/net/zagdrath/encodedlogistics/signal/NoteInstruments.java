/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

// The instruments PLYNOTE names (*HARP ... *PLING), in SpeakerBlockEntity.INSTRUMENTS' order, and how many notes a
// sequence holds. Plain values, so the command schemas (BuiltinCommands) load without the game.
public final class NoteInstruments {
    public static final String[] NAMES = { "*HARP", "*BASS", "*SNARE", "*HAT", "*BASSDRUM", "*BELL", "*FLUTE", "*CHIME", "*GUITAR", "*XYLOPHONE",
            "*IRONXYLO", "*COWBELL", "*DIDGERIDOO", "*BIT", "*BANJO", "*PLING" };
    public static final int MAX_NOTES = 32;

    private NoteInstruments() {}

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
