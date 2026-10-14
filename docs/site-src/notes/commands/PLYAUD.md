`PLYAUD` makes Speakers play a sound file or a web URL. The sound is played by each listening player's own game: the
server tells the clients what to play and when it started, and each one starts it at the right point, so players who
arrive late hear it in step.

## The source

`SRC` is either a file name or an `http://` or `https://` URL. Quote it: `SRC('alarm.ogg')`.

- **A file** comes from the system's audio folder, `<world>/encodedlogistics/audio/<SYSNAME>/` (see
  {{page admin/folders}}). It must be OGG Vorbis or MP3 and its name may use only letters, digits, spaces, `_`, `.`
  and `-`, ending `.ogg` or `.mp3`; its case doesn't have to match. The server reads and checks the file itself:
  no such file is {{msg ELC2402}}, larger than {{config signals.audioMaxBytes default}} MB or longer than
  {{config signals.audioMaxSeconds default}} seconds is {{msg ELC2403}}, and a file it can't read as OGG Vorbis or MP3
  (or a name with another ending) is {{msg ELC2406}}. Players are sent the file by its content's hash, never its path.
- **A URL** is fetched by each player's game, not the server. The server allows it only when
  {{config signals.allowWebAudio default}} is on (`AUTO`: on in single-player, off on dedicated servers; off:
  {{msg ELC2404}}) and its host, or a domain it belongs to, is on {{config signals.webAudioHosts}}
  ({{msg ELC2405}}; the list is empty by default). Each player also has to opt in with the client option
  {{config audio.allowWebAudio default}}, because fetching the URL shows their IP address to whoever hosts it. Players
  who haven't opted in hear nothing. The same size and length limits apply, checked by each client as it downloads.

The source is checked once, before any speaker starts.

## Volume, loop and what's kept

- `VOL` sets each speaker's volume, 0-100 %; `*SAME` keeps its own.
- `LOOP(*YES)` repeats the sound until {{cmd STPAUD}}; `*NO` plays it once. `*SAME` keeps the speaker's own Loop
  setting.
- The source, volume and loop become the speaker's settings, as if you'd set them on its settings screen. A speaker
  whose **Trigger** includes redstone plays that source again on its next rising redstone edge, and a looping sound
  stops when the signal goes off.

`DEV` takes up to 32 names, or `*ALL` for every online Speaker on the network that takes network commands. Every named
speaker is checked first: not there ({{msg ELC1301}}), offline ({{msg ELC1302}}), not a Speaker ({{msg ELC1303}}), or
its Trigger is Redstone ({{msg ELC2408}}; `*ALL` leaves those out). It sends no completion message.

## Examples

```elcl
PLYAUD DEV(SPK01 SPK02) SRC('evacuate.ogg') VOL(100) LOOP(*YES)
PLYAUD DEV(*ALL) SRC('chime.mp3') LOOP(*NO)
PLYAUD DEV(SPK03) SRC('https://example.com/radio/news.mp3') VOL(60)
```

For a single note use {{cmd PLYNOTE}}, and for a tune {{cmd PLYMID}}. See {{page control/speakers}}.
