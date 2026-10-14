`STPAUD` stops whatever Speakers are playing: a file or URL from {{cmd PLYAUD}}, a note from {{cmd PLYNOTE}}, or a MIDI
tune from {{cmd PLYMID}}. It also clears a speaker's error (its amber LED), if the last thing it tried couldn't play.

A MIDI play stops on every speaker in it, whichever one you name: the clock speaker or any speaker playing a part.

Stopping doesn't change a speaker's settings. If its Trigger includes redstone, the next rising edge plays its source
again. A speaker that isn't playing is left as it is.

`DEV` takes up to 32 names, or `*ALL` for every online Speaker on the network that takes network commands. Every named
speaker is checked first: not there ({{msg ELC1301}}), offline ({{msg ELC1302}}), not a Speaker ({{msg ELC1303}}), or
its Trigger is Redstone ({{msg ELC2408}}; `*ALL` leaves those out). It sends no completion message.

```elcl
STPAUD DEV(SPK01 SPK02)
STPAUD DEV(*ALL)
```

See {{page control/speakers}}.
