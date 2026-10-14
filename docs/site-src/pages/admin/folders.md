---
title: Folders on disk
section: admin
order: 30
summary: The world's encodedlogistics folder - library folder sync, CSV import and export, and the audio and image folders - and the options that switch each one.
---
Some of the mod's features read and write files in the world's save, under `<world>/encodedlogistics/`. Each system
(network) has its own folder in each, named after its **SYSNAME** system value (`ELNETnn` unless someone changes it):

```text
<world>/encodedlogistics/
  libraries/<SYSNAME>/<LIB>/<MEMBER>.elclp   program source, synced with the library
  libraries/<SYSNAME>/<LIB>/<MEMBER>.pf      physical file definitions, synced too
  libraries/<SYSNAME>/<FILE>.csv             CSV files for CPYTOIMPF / CPYFRMIMPF
  audio/<SYSNAME>/                           Speaker audio: OGG, MP3, MID / MIDI
  audio/                                     the same, for Speakers on no network
  images/<SYSNAME>/                          Display Panel images: PNG
```

Changing a system's SYSNAME changes which folder it uses.

| Folder | Switched by | On a dedicated server, by default |
|---|---|---|
| `libraries/` | {{config elcl.allowFolderSync default}} | Off |
| `audio/` | Always on (within the audio limits) | On |
| `images/` | {{config rack.allowImages default}} | Off |

`AUTO` means on in single-player (and on a LAN world's host) and off on a dedicated server. Set `TRUE` to turn it on
there, or `FALSE` to turn it off everywhere.

## Library folder sync {#folder-sync}

With folder sync on, every library's members are also files in `libraries/<SYSNAME>/<LIB>/`, so you can edit programs
in a text editor outside the game, keep them in version control, or copy them between worlds.

- **Out:** saving a member in the game (or creating, copying, renaming or restoring one) writes its file.
- **In:** the folder is read every two seconds. A new or changed file becomes the member; lines you didn't change keep
  their sequence numbers. A new folder becomes a new library.
- **Conflicts:** when a file and its member both changed since they were last in step, the last write wins, and an
  in-game save always counts as the later one. The losing version is kept beside it as `<MEMBER>.elclp.bak` (or
  `.pf.bak`).
- **Deleting:** deleting a file never deletes its member. Deleting a member moves its file to `<LIB>/.deleted/`.
- **ELSYS**, the system library, is never synced either way.
- A file's **records** don't sync; copy them with the CSV commands below.

See {{page elcl/libraries-and-editor}} for libraries and members.

## CSV import and export {#csv}

{{cmd CPYTOIMPF}} writes a file's records to a CSV file in `libraries/<SYSNAME>/`, and {{cmd CPYFRMIMPF}} reads one
back (`MBROPT(*ADD)` or `*REPLACE`). The name must be a plain file name ending `.csv` (letters, digits, `_`, `.` and
`-`, up to 64 characters, no folders), or it is {{msg ELC2245}}. `CPYTOIMPF` writes to a temporary file
first and then moves it into place, so nothing else ever sees a half-written file. Both commands need folder sync on ({{msg ELC2241}} otherwise).
See {{page elcl/database-files}}.

## Audio {#audio}

Speakers play OGG Vorbis and MP3 files, and MIDI files (`.mid`, `.midi`), from `audio/<SYSNAME>/`. A Speaker on no
network plays from `audio/` itself. Players can't upload files: whoever has access to the server's disk puts them
there. The server checks every file before it plays:

{{table config keys=signals.audioMaxBytes,signals.audioMaxSeconds,signals.midiMaxKilobytes,signals.midiMaxSeconds}}

Audio files are sent to the players in range, who keep them in memory only (never on their disk). MIDI files are
played on the server as note-block notes and never sent at all. Web audio (a Speaker playing a URL) is separate: see
{{page control/speakers}} and {{config signals.allowWebAudio}}.

## Images {#images}

Display Panels show PNG files from `images/<SYSNAME>/`, while {{config rack.allowImages}} allows it. Players are sent
only the rendered picture at the size of its region, never the file. The size limits are checked from the file's size
and its header before it is decoded:

{{table config keys=rack.displayMaxImageBytes,rack.displayMaxImageSize,rack.displayMaxImagePixels,rack.displayImageColors}}

See {{page control/display-panels}}.

## What players' games keep {#clients}

Nothing the mod gets from a server is written to a player's disk. Audio is kept in memory (up to 64 MB, the least
recently played dropped first) and cleared when they leave the world; images arrive only as rendered regions.
