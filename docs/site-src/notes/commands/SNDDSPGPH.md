`SNDDSPGPH` puts a graph in a region of a Display Panel screen: one statistic over a time range, as a line or bar
chart. It replaces whatever the region showed. A Text screen given a graph becomes a Dashboard. The region must exist
({{cmd CHGDSPRGN}}; on a screen with none set up, `A` is the whole screen), or it's {{msg ELC1314}}.

| `STAT` | Graphs | Labelled |
|---|---|---|
| `*ITEMFLOW` | Items the network moves a minute | `ITEM FLOW /MIN` |
| `*ENERGY` | The network's energy draw, FE/t | `ENERGY DRAW FE/T` |
| `*LANES` | Lanes in use | `LANES USED` |
| `*STORAGE` | Hot storage used, % | `STORAGE %` |
| `*CRAFTING` | Crafts finished a minute | `CRAFTS /MIN` |
| `*ITEM` | One item's count; name it in `ITEM()` | The item |
| `*MCHOPS` | A machine's operations a minute | The machine and `OPS /MIN` |
| `*MCHFE` | A machine's stored FE | The machine and `FE` |

`*MCHOPS` and `*MCHFE` graph a machine bridged through [Arcforge](page:integrations/arcforge): give the machine's
device name in `ITEM`, for example `ITEM(FURN01)`. A name that isn't on the network is {{msg ELC1301}}, and a device
that isn't such a machine {{msg ELC1318}}. `*ITEM`, `*MCHOPS` and `*MCHFE` without an `ITEM` are {{msg ELC1316}},
and so is an `ITEM` given to any other statistic.

## Where the numbers come from

- With a [Monitoring Server](page:racks/devices) online on the network, `*ITEMFLOW`, `*ENERGY`, `*LANES`,
  `*CRAFTING`, `*MCHOPS` and `*MCHFE` come from its records, over the whole `RANGE`.
- Otherwise, and always for `*STORAGE` and `*ITEM`, the screen keeps its own history: one sample a second while it's
  loaded, for the last {{config rack.displayHistorySeconds default}} seconds. `RANGE(*1M)` shows the last minute of it;
  the longer ranges show all of it there is so far.

`RANGE` is `*1M`, `*10M` (the default), `*1H` or `*1D`, and `TYPE` is `*LINE` (the default) or `*BAR`. A graph has at
most 256 points; a longer series is averaged down to fit. `COLOR` is the line or bar colour: `*DFT`, a palette name
such as `*CYAN`, or `'#RRGGBB'` in quotes.

The screen doesn't have to be online. A `DEV` that isn't on the network is {{msg ELC1301}}, and a device that isn't a
Display Panel screen {{msg ELC1303}}. `SNDDSPGPH` sends no completion message.

## Examples

```elcl
SNDDSPGPH DEV(DSP01) RGN(A) STAT(*ENERGY) RANGE(*1H)
SNDDSPGPH DEV(DSP01) RGN(B) STAT(*ITEM) ITEM(minecraft:iron_ingot) TYPE(*BAR) COLOR(*ORANGE)
SNDDSPGPH DEV(DSP01) RGN(C) STAT(*MCHOPS) ITEM(FURN01) RANGE(*10M)
```

See [Graphs](page:control/display-panels#graphs).
