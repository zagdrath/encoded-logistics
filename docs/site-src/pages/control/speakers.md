---
title: Speakers
section: control
order: 50
summary: The Speaker - its four sources (an audio file, a web URL, a note, a MIDI file), OGG and MP3 playback, MIDI across several speakers, and the server and client settings for web audio.
---
A {{item speaker}} is a flat panel that plays sound: an audio file from the world's audio folder, a note-block note
or sequence, a MIDI file spread across several speakers, or, where the server allows it, audio from a web address. Like
the [Cage Lights and Alarm Strobes](page:control/lights-and-sirens#shared) it mounts on any face, works from redstone on
its own, and becomes a device (`SPK01`, ...) on a network.

{{recipe speaker}}

Its LED shows idle, playing (green) or an error (amber). Look at it for its popup, which also gives the reason for an
error.

## Settings {#settings}

Right-click it for its settings screen (on a network, with the Firewall's **BUILD** permission). The Play and Stop
button at the bottom right plays or stops it there and then.

| Setting | Values |
|---|---|
| Source | **File** (the default), **URL**, **Note** or **MIDI** |
| File / URL / Notes | What to play, in the bottom field |
| Volume | 0-100 % (default 70) |
| Range | How far it carries, in blocks (default 32, at most {{config signals.speakerMaxRange default}}) |
| Loop | No (the default) or Yes |
| Trigger | **Redstone**, **Network** (ELCL only) or **Both** (the default) |

A rising redstone edge plays the source (or the next note of a sequence). The signal going off stops a looping source.

## The sources {#sources}

### Audio files {#files}

A file source plays a file from the system's audio folder, `<world>/encodedlogistics/audio/<SYSNAME>/`. A Speaker on
no network uses the `audio/` folder itself. See {{page admin/folders}}.

- **Formats:** OGG Vorbis and MP3. OGG is decoded by Minecraft's own decoder; MP3 by JavaZOOM's JLayer (LGPL 2.1),
  which ships inside the mod's jar as its own unmodified jar, with its licence at
  `META-INF/licenses/jlayer-LGPL-2.1.txt`.
- **Checked on the server:** a missing file is {{msg ELC2402}}; one over {{config signals.audioMaxBytes default}} MB or
  {{config signals.audioMaxSeconds default}} seconds is {{msg ELC2403}}; one that can't be decoded is {{msg ELC2406}}.
- **How it reaches players:** the server tells the players in range only the file's content hash. Each one's game asks
  for the bytes it doesn't have yet, which the server sends in small chunks, and keeps them in memory by hash (up to
  64 MB, the least recently played dropped first, cleared when you leave the world). Nothing is written to a player's
  disk.
- A play starts where it should be by now, so a player who walks into range hears it in step with everyone else.

### Notes {#notes}

A note source plays a note-block instrument at a pitch from 0 to 24 (two octaves, as a note block). {{cmd PLYNOTE}}
takes any of the 16 instruments (`*HARP` to `*PLING`) and up to 32 notes. More than one note makes a **sequence**:
each rising redstone edge plays the next one. A note outside 0-24 is {{msg ELC2407}}.

### MIDI files {#midi}

A MIDI source plays a Standard MIDI File (`.mid` or `.midi`, format 0 or 1, RIFF-wrapped too) from the same audio
folder, within {{config signals.midiMaxKilobytes default}} KB and {{config signals.midiMaxSeconds default}} seconds. A
file that isn't MIDI is {{msg ELC2409}}; a format 2 file is {{msg ELC2410}}.

It plays **on the server**, as note-block notes, so the file never goes to players:

- Each note is timed to the game tick by the file's tempo changes, and its velocity becomes its volume.
- Each channel's General MIDI program plays on the closest note-block instrument, and channel 10's drums on the
  drum sounds. Keys outside an instrument's two octaves are moved by octaves into them. Note-offs are dropped: a
  note-block sound rings out on its own.
- One speaker starts at most {{config signals.midiMaxNotes default}} notes a tick; the loudest are kept.

**Several speakers.** A MIDI play can be spread over up to 32 parts, each a track or a channel played by a speaker of
your choice. The speaker that starts the play keeps time for all of them, so they stay together:

| Setting | What it does |
|---|---|
| Map by | **Auto** (the default: channels in a format 0 file, tracks in format 1), **Track** or **Channel** |
| Parts | Pairs of a track or channel and a speaker, such as `1 SPK01, 10 SPK04` (or `*ALL SPK02`); blank plays every part on this speaker |

A speaker given no part stays silent. Stopping any speaker in the play (its Stop, or {{cmd STPAUD}}) stops them all.
A track or channel that isn't in the file is {{msg ELC2411}} or {{msg ELC2412}}.

### Web audio {#web}

A URL source plays MP3 or OGG audio from an `http://` or `https://` address. Each listening player's own game fetches
it, so it is off unless both the server and the player turn it on:

1. **The server:** {{config signals.allowWebAudio default}} must allow it. `AUTO` means on in single-player and off on
   dedicated servers; off, setting a URL is {{msg ELC2404}}.
2. **The host** must be on {{config signals.webAudioHosts}} (an entry also allows its subdomains). The list is empty by
   default, which allows none; another host is {{msg ELC2405}}.
3. **The player:** the client option {{config audio.allowWebAudio default}} (under Sounds in the mod's client
   settings). Fetching a URL shows that player's IP address to whoever hosts it, so it is off until each player turns
   it on. For a player who hasn't, the speaker's popup says the play was skipped.

Before fetching, the player's game checks the address again for itself: http or https only, no user name or password in
it, and every address the host resolves to must be public (never loopback, private, link-local, multicast or
unspecified; IPv4-mapped and NAT64 addresses are judged by the IPv4 address in them). It connects only to the address it
checked, follows no redirects, takes only a `200` answer, stops at the server's size limit, and gives up after 30
seconds. Web audio is kept in memory only. A failed fetch puts the speaker in error, with a reason starting `web:` on
its popup.

## From ELCL {#elcl}

| Command | Does |
|---|---|
| {{cmd PLYAUD}} | Plays a file or URL (`SRC()`), with `VOL` and `LOOP` |
| {{cmd PLYNOTE}} | Sets a note or sequence (`INST`, `NOTE`) |
| {{cmd PLYMID}} | Plays a MIDI file, with `MAP((part speaker) ...)` and `MAPBY` |
| {{cmd STPAUD}} | Stops a speaker (and every speaker in its MIDI play) |

They take a list of speakers or `*ALL`, and need **BUILD**. A speaker whose trigger is Redstone takes no network
commands ({{msg ELC2408}}).

```elcl
PLYAUD DEV(SPK01) SRC('evacuate.ogg') LOOP(*YES)
DLYJOB DLY(30)
STPAUD DEV(SPK01)
PLYMID DEV(SPK01) FILE('all-clear.mid') VOL(60) +
       MAP((1 SPK01) (2 SPK02) (10 SPK04)) MAPBY(*CHANNEL)   /* melody, bass, drums */
```

## Server and client options {#config}

{{table config keys=signals.speakerMaxRange,signals.audioMaxBytes,signals.audioMaxSeconds,signals.midiMaxKilobytes,signals.midiMaxSeconds,signals.midiMaxNotes,signals.allowWebAudio,signals.webAudioHosts,audio.allowWebAudio}}

The `signals` options are in the server's `encodedlogistics-local.toml`; `audio.allowWebAudio` is in each player's
`encodedlogistics-client.toml` (see {{page admin/config}}).
