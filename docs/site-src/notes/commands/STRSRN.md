`STRSRN` turns on the network's half of an Alarm Strobe's trigger: the lens lights in its light mode and its tone
plays, looping, until {{cmd ENDSRN}} turns it off again.

- `SOUND` changes the strobe's tone, and it stays changed: the strobe's settings screen shows the new one.
  `*WAIL`, `*YELP`, `*KLAXON`, `*BELL`, `*HORN` and `*BEEP` are the six siren tones; `*NONE` makes it a silent,
  light-only beacon. `*SAME` (the default) keeps the tone it has.
- `MODE` changes its light mode the same way: `*SOLID`, or flashing `*SLOW`, `*MEDIUM` or `*FAST`. `*SAME` keeps it.
- Starting a strobe that's already on doesn't restart its tone, unless `SOUND` gives it a different one.

Strobes on one network that sound the same tone count their samples from the same moment, so a strobe you start joins
the others in step. Its volume and range are set on its settings screen (the range at most
{{config signals.sirenMaxRange default}} blocks).

A strobe's **Trigger** setting decides whether it listens to the network:

| Trigger | `STRSRN` / `ENDSRN` |
|---|---|
| Both (the default) | Work; redstone at its block sets it off too |
| Network | Work; redstone is ignored |
| Redstone | Named in `DEV`, the command stops with {{msg ELC2408}}; `DEV(*ALL)` leaves it out |

`DEV` takes up to 32 names, or `*ALL` for every online strobe on the network that takes network commands. Every named
strobe is checked before any changes: not there ({{msg ELC1301}}), offline ({{msg ELC1302}}), not an Alarm Strobe
({{msg ELC1303}}) or Redstone-triggered ({{msg ELC2408}}). It sends no completion message.

```elcl
STRSRN DEV(SRN01 SRN02) SOUND(*KLAXON) MODE(*FAST)
STRSRN DEV(*ALL) SOUND(*NONE) MODE(*SLOW)   /* every strobe, light only */
```

See [Alarm Strobes](page:control/lights-and-sirens#alarm-strobes) for the tones and settings.
