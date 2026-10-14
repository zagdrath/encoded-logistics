`PLYMID` plays a MIDI file on Speakers as note block notes. The server reads the file and sends the notes itself, tick
by tick, so nothing is downloaded and every player hears the same thing. The speaker in `DEV` is the play's clock:
it keeps time and hands the notes out to the speakers `MAP` names.

## The file

`FILE` is a `.mid` or `.midi` file in the system's audio folder, `<world>/encodedlogistics/audio/<SYSNAME>/` (see
{{page admin/folders}}); quote it, `FILE('theme.mid')`, and its case doesn't have to match. It must be a Standard MIDI
File of format 0 or 1 (an RMID-wrapped one works too).

| Problem | Message |
|---|---|
| No such file | {{msg ELC2402}} |
| Larger than {{config signals.midiMaxKilobytes default}} KB, or longer than {{config signals.midiMaxSeconds default}} seconds | {{msg ELC2403}} |
| Not a MIDI file (or a name not ending `.mid` or `.midi`) | {{msg ELC2409}} |
| Format 2 | {{msg ELC2410}} |

How the notes are turned into note block notes:

- Each note is timed by the file's tempo map and rounded to the game tick it falls on (a tick is 50 ms).
- Its instrument is the note block instrument closest to its channel's General MIDI program; channel 10 is the drum
  kit, played on the bass drum, snare, hat and cow bell by drum.
- Its key is moved by octaves into that instrument's two octaves, and its velocity is kept for the volume.
- Note-offs are dropped: a note block sound rings out on its own.
- One speaker starts at most {{config signals.midiMaxNotes default}} notes in a tick; the quietest beyond that are left
  out.

## Parts and speakers

Without `MAP`, every part plays on `DEV`. With `MAP`, give pairs of a part number and a speaker, up to 32 pairs:
`MAP((1 SPK01) (2 SPK02) (10 SPK04))`. `*ALL` as the number gives that speaker every part. A speaker can be given
several parts, and `DEV` plays only what `MAP` gives it (nothing, if `MAP` doesn't name it: it stays silent, keeping
time).

`MAPBY` says what the numbers are: `*TRACK` (1 for the first track), `*CHANNEL` (1-16), or `*AUTO` (the default):
channels in a format 0 file, which has only one track, and tracks in a format 1 file. Everything is checked before any
speaker starts:

- A track the file doesn't have is {{msg ELC2411}}; a channel with no notes in it is {{msg ELC2412}}.
- A number outside 1-999, a speaker name that isn't a valid name, an odd number of values or more than 32 pairs is
  {{msg ELC0103}} for `MAP`.
- Each speaker `MAP` names must be on the network ({{msg ELC1301}}), a Speaker in the same dimension
  ({{msg ELC1303}}), online ({{msg ELC1302}}) and take network commands ({{msg ELC2408}}).

`VOL` sets the volume of every speaker in the play, 0-100 %; `*SAME` leaves each speaker its own. `LOOP(*YES)` starts
the tune over at its end until it's stopped; `*NO` (the default, unlike {{cmd PLYAUD}}) plays it once.

## Stopping

{{cmd STPAUD}} on any speaker in the play stops it on all of them. Starting something else on the clock speaker
(another `PLYMID`, {{cmd PLYAUD}} or {{cmd PLYNOTE}}) ends the play everywhere; starting something else on one of the
others takes only that one out of it. The other speakers also stop if the clock speaker is removed or its chunk
unloads.

The file, parts and map become the clock speaker's settings, so if its Trigger includes redstone, a rising edge
starts the tune again. It sends no completion message.

## Examples

```elcl
PLYMID DEV(SPK01) FILE('theme.mid')
PLYMID DEV(SPK01) FILE('march.mid') LOOP(*YES) VOL(80) +
       MAP((1 SPK01) (2 SPK02) (3 SPK03)) MAPBY(*TRACK)
PLYMID DEV(SPK05) FILE('band.mid') MAPBY(*CHANNEL) +
       MAP((10 SPK06) (*ALL SPK05))      /* drums on SPK06 as well */
```

See {{page control/speakers}}.
