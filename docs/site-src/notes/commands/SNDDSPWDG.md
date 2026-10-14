`SNDDSPWDG` puts a widget in a region of a Display Panel screen: a live gauge, a list, a clock, the screen's text lines
or a table of a file's records. It replaces whatever the region showed (a graph or an image too). A Text screen given a
widget becomes a Dashboard. The region must exist (set it up with {{cmd CHGDSPRGN}}; on a screen with none set up,
`A` is the whole screen); one that doesn't is {{msg ELC1314}}.

## The widgets

| `WDG` | Shows | Needs |
|---|---|---|
| `*STORAGE` | Hot storage: how full the network's drives are, as a percentage and a bar | |
| `*COLD` | Cold storage: how full the online Tape Libraries are | |
| `*ENERGY` | The network's energy draw in FE/t, with a bar of its stored energy | |
| `*LANES` | Lanes in use out of the network's total | |
| `*JOBS` | The active crafting jobs: how many, and a row each with its number, item and how far along it is | |
| `*ITEM` | One item's count in the network's storage | `ITEM()` |
| `*CLOCK` | The game time and day | |
| `*DEVICES` | The network's devices with their status, all of them or one type | `DEVTYPE()` (optional) |
| `*UPS` | The UPSes: mains or battery, their average charge, and a row each | |
| `*TEXT` | The screen's text lines, written with {{cmd SNDDSPTXT}} | |
| `*TABLE` | A file's records, selected and sorted | `FILE()`, `QRYSLT()`, `SORT()` |
| `*NONE` | Nothing: the region is empty | |

Live widgets (all but `*CLOCK`, `*TEXT` and `*NONE`) are worked out on the server once a second, for the players near
the screen. For graphs use {{cmd SNDDSPGPH}}, and for pictures {{cmd SNDDSPIMG}}.

## Data sources

Each source parameter belongs to one widget. Giving it to another widget is {{msg ELC1316}}, and so is leaving out one
a widget needs.

- `ITEM` names the item for `*ITEM`, as other commands take it (`LOGIC_DIE`, `minecraft:iron_ingot`). An item the
  game doesn't have is {{msg ELC1201}}, and a name more than one mod uses {{msg ELC1205}}.
- `DEVTYPE` limits `*DEVICES` to one device type, as Work with Devices shows it (`LGT`, `SPK`, `DSP`...); `*ALL`
  (the default) lists every device.
- `FILE`, `QRYSLT` and `SORT` make a `*TABLE`, and work as {{cmd RUNQRY}}'s: the file (`*LIBL` searched when it isn't
  qualified; not found: {{msg ELC2205}}), a selection expression over its fields (`*ALL`: every record) and the sort
  fields, each optionally followed by `*DESCEND` (up to 8 values in all). The selection and sort are checked against the file now: a
  name that isn't a field is {{msg ELC2242}}. The table shows the headings, then a row a record, as many as fit and at
  most 64. If the file goes away later, the region shows `!` and the message instead. `QRYSLT` and `SORT` are ignored by
  the other widgets.

`COLOR` is the widget's accent colour: `*DFT` (its own), a palette name such as `*MINT`, or `'#RRGGBB'` in quotes.

The screen doesn't have to be online. A name that isn't on the network is {{msg ELC1301}}, and a device that isn't a
Display Panel screen {{msg ELC1303}}. `SNDDSPWDG` sends no completion message.

## Examples

```elcl
SNDDSPWDG DEV(DSP01) RGN(A) WDG(*STORAGE) COLOR(*MINT)
SNDDSPWDG DEV(DSP01) RGN(B) WDG(*ITEM) ITEM(minecraft:iron_ingot)
SNDDSPWDG DEV(DSP01) RGN(C) WDG(*DEVICES) DEVTYPE(UPS)
SNDDSPWDG DEV(DSP02) RGN(A) WDG(*TABLE) FILE(ELSYS/DEVICES) +
          QRYSLT('STATUS *NE "*ONLINE"') SORT(TYPE NAME)
SNDDSPWDG DEV(DSP02) RGN(B) WDG(*TEXT)
```

See [Widgets](page:control/display-panels#widgets).
