`PLYNOTE` makes Speakers play a note block note: the instrument `INST` at pitch `NOTE`, 0-24 (two octaves, as a note
block's 25 pitches; 12 is the middle one). A note outside 0-24 stops the command with {{msg ELC2407}} before any speaker
plays.

The instruments are the note block's 16, by their sounds rather than the block under them:

| `INST` | Note block sound | `INST` | Note block sound |
|---|---|---|---|
| `*HARP` (default) | Harp | `*GUITAR` | Guitar |
| `*BASS` | Bass | `*XYLOPHONE` | Xylophone |
| `*SNARE` | Snare drum | `*IRONXYLO` | Iron xylophone |
| `*HAT` | Clicks and sticks | `*COWBELL` | Cow bell |
| `*BASSDRUM` | Bass drum | `*DIDGERIDOO` | Didgeridoo |
| `*BELL` | Bell | `*BIT` | Bit |
| `*FLUTE` | Flute | `*BANJO` | Banjo |
| `*CHIME` | Chime | `*PLING` | Pling |

## A sequence

Give `NOTE` a list (up to 32) and the speaker keeps it as a sequence: the first note plays now, and each rising
redstone edge at the speaker plays the next, starting over after the last. That needs the speaker's **Trigger** to
include redstone; a speaker set to Network plays only the first note. Another `PLYNOTE` starts a new sequence from its
first note.

The instrument and notes become the speaker's settings, like a source chosen on its settings screen, so a single note
is played again on each rising redstone edge too.

`DEV` takes up to 32 names, or `*ALL` for every online Speaker on the network that takes network commands. Every named
speaker is checked first: not there ({{msg ELC1301}}), offline ({{msg ELC1302}}), not a Speaker ({{msg ELC1303}}), or
its Trigger is Redstone ({{msg ELC2408}}; `*ALL` leaves those out). It sends no completion message.

## Examples

```elcl
PLYNOTE DEV(SPK01) INST(*BELL) NOTE(18)
PLYNOTE DEV(*ALL) INST(*PLING) NOTE(6 10 13 18)   /* one note a redstone pulse */
```

A note sounds at once and rings out on its own, so a program that wants a tune plays notes with a {{cmd DLYTICK}}
between them, or uses a MIDI file with {{cmd PLYMID}}.
