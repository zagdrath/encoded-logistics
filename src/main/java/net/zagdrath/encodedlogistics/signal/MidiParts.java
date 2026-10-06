/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// A MIDI play's parts map (PLYMID's MAP, a Speaker's Parts row): which speaker plays which track or channel, and how
// many notes one speaker starts in a tick. Plain Java (no game classes), so it's tested on its own.
public final class MidiParts {
    public static final int MAX = NoteInstruments.MAX_MIDI_PARTS;

    // An entry: a track or channel (0: all of them) and the speaker that plays it.
    public record Part(int number, String device) {}

    private MidiParts() {}

    // "1 SPK01, 10 SPK04" (commas, spaces or brackets between): pairs of a track or channel (1-999, or *ALL) and a
    // speaker's name; anything else is ELC0103 for the keyword given, as are more than MAX pairs. "": none.
    public static List<Part> parse(String text, String keyword) throws ElclException {
        List<String> words = new ArrayList<>();
        for (String word : text.trim().toUpperCase(Locale.ROOT).split("[\\s,()]+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        if (words.size() % 2 != 0) {
            throw new ElclException("ELC0103", words.getLast(), keyword);
        }
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < words.size(); i += 2) {
            String number = words.get(i), device = words.get(i + 1);
            int part;
            if (number.equals("*ALL")) {
                part = 0;
            } else {
                try {
                    part = Integer.parseInt(number);
                } catch (NumberFormatException e) {
                    throw new ElclException("ELC0103", number, keyword);
                }
                if (part < 1 || part > 999) {
                    throw new ElclException("ELC0103", number, keyword);
                }
            }
            if (!device.matches("[A-Z][A-Z0-9_@#$]{0,9}")) {
                throw new ElclException("ELC0103", device, keyword);
            }
            if (parts.size() == MAX) {
                throw new ElclException("ELC0103", number, keyword);
            }
            parts.add(new Part(part, device));
        }
        return List.copyOf(parts);
    }

    // The map as parse reads it: "1 SPK01, 10 SPK04", "*ALL SPK02".
    public static String text(List<Part> parts) {
        StringBuilder text = new StringBuilder();
        for (Part part : parts) {
            text.append(text.isEmpty() ? "" : ", ").append(part.number() == 0 ? "*ALL" : Integer.toString(part.number())).append(' ').append(part.device());
        }
        return text.toString();
    }

    // At most the given number of notes: the loudest (of equals, the first).
    public static List<MidiFile.Note> loudest(List<MidiFile.Note> notes, int most) {
        if (notes.size() <= most) {
            return notes;
        }
        List<MidiFile.Note> sorted = new ArrayList<>(notes);
        sorted.sort(Comparator.comparingInt(MidiFile.Note::velocity).reversed());
        return sorted.subList(0, most);
    }
}
