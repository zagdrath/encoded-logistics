---
title: Display Panels
section: control
order: 30
summary: Wall screens built from Display Panels - how they merge, their Text and Dashboard modes, regions, widgets, graphs, images and touch, and driving them from ELCL.
legacy: [display]
---
A {{item display_panel}} is an 8 px slab you put on a wall. Panels side by side merge into one screen, which shows
text, live gauges, graphs, tables of a file's records or a picture, set up on its configuration screen or from ELCL. A
screen is a device on its network, and touching it can run a program.

{{recipe display_panel}}

The recipe makes four.

{{screenshot file="control/display-wall.png" alt="A 4 x 2 Display Panel screen in Dashboard mode on a control room wall" show="A screen of 4 x 2 panels in Dashboard mode: an energy graph in the left region, a storage gauge and an item counter on the right, the status LED lit on its bottom-right block" angle="Straight on, centred, 5-6 blocks back, so the whole screen and its bezel fill most of the frame" size="1600x900"}}

## Building a screen {#merging}

Panels facing the same way that sit side by side or stacked in one plane merge into one screen, up to
{{config rack.displayMaxWidth default}} panels wide and {{config rack.displayMaxHeight default}} high. A bigger wall is
split into the largest rectangles that fit, largest first (on a tie, the top-most, then the left-most, as seen from the
front). Each rectangle is a screen of its own.

- A screen has one bezel round its outside edge, and its status LED on its bottom-right block.
- It holds its content on its bottom-left block. Adding or removing panels re-merges the wall; the new screen keeps
  the content of a screen that was inside it (one of the same size first, else the largest), and its regions are
  scaled to the new size.
- It joins the network through a cable on its back or bottom, or the device or cable behind it, or through the panels
  beside it.
- Each screen is **one device with one lane**, named `DSP01`, `DSP02`, ..., and draws
  {{config rack.displayPanelDrain default}} FE/t for each of its panels. Work with Devices shows its size, for example
  "Display Panel 3 x 2 (96 x 64)".

### States {#states}

| State | What you see |
|---|---|
| No signal | Yellow LED, `NO SIGNAL` on the screen: no network, or its network isn't running |
| Off | Plain glass: its network runs but it has no lane |
| Booting | A test pattern for 2 seconds as it comes on |
| Online | Its content |

## The canvas {#canvas}

Each panel is 32 x 32 canvas pixels, so a 3 x 2 screen is 96 x 64 and an 8 x 6 screen is 256 x 192. Regions,
positions and touch points are all in canvas pixels, from the top left. Text uses the terminal font, 6 x 10 pixels a
character at the normal size.

Use {{cmd RTVDSPSIZ}} in a program to read a screen's size in panels and in pixels.

## Modes {#modes}

A screen has one of two modes:

- **Text:** its text lines fill the whole screen, each line with its own size, colour and alignment. {{cmd SNDDSPTXT}}
  writes them.
- **Dashboard:** the screen is divided into **regions**, each showing a **widget**. A `*TEXT` widget shows the
  screen's text lines in its region.

A Text screen that a program gives regions or widgets becomes a Dashboard.

### Text sizes {#text-sizes}

| Size | Font |
|---|---|
| `*SMALL` | 4 x 6 |
| `*NORMAL` | 6 x 10 (the terminal font) |
| `*LARGE` | Twice normal |
| `*HUGE` | Three times normal |

Colours are `*DFT`, one of the panel palette's names (`*DARK`, `*STEEL`, `*GREY`, `*SILVER`, `*LIGHT`, `*WHITE`,
`*NAVY`, `*BLUE`, `*CYAN`, `*RUST`, `*ORANGE`, `*YELLOW`, `*GREEN`, `*FOREST`, `*RED`, `*BROWN`, `*MINT`) or `#RRGGBB`.
A `#RRGGBB` colour goes in quotes (`COLOR('#3080FF')`), and so does a palette name given to a region's `BG`
(`BG('*NAVY')`): `BG` takes only `*SAME` and `*DFT` bare.
Alignment is `*LEFT`, `*CENTER` or `*RIGHT`.

## Regions {#regions}

Regions are named rectangles (A, B, C, ...) that never overlap, at most 16 on a screen. With none set up, region A is
the whole screen. Each has a background colour and a widget. Set them up on the configuration screen's Layout tab or
with {{cmd CHGDSPRGN}} (`X`, `Y`, `W`, `H` in canvas pixels); a region off the screen or over another is
{{msg ELC1314}}.

## Widgets {#widgets}

| Widget | Shows |
|---|---|
| `*STORAGE` | Hot storage: how full the network's drives are |
| `*COLD` | Cold storage (the Tape Libraries) |
| `*ENERGY` | The network's stored energy |
| `*LANES` | Lanes in use |
| `*JOBS` | Active crafting jobs |
| `*ITEM` | One item's count (`ITEM()`) |
| `*CLOCK` | The game time and day |
| `*DEVICES` | The network's devices, all or one type (`DEVTYPE()`) |
| `*UPS` | The UPSes' status |
| `*TEXT` | The screen's text lines |
| `*TABLE` | A file's records: `FILE(LIB/NAME)`, selected and sorted as {{cmd RUNQRY}} does with `QRYSLT()` and `SORT()`; headings on a bar, then a row a record, as many as fit (at most 64 rows) |
| A graph | {{cmd SNDDSPGPH}}, below |
| An image | {{cmd SNDDSPIMG}}, below |
| `*NONE` | Nothing |

{{cmd SNDDSPWDG}} sets a region's widget. Live widgets are worked out on the server once a second for players near
the screen. A widget whose data isn't there is {{msg ELC1316}}.

### Graphs {#graphs}

A graph shows one of `*ITEMFLOW`, `*ENERGY`, `*LANES`, `*STORAGE`, `*CRAFTING` or `*ITEM` (one item's count) over
`RANGE(*1M)`, `*10M` (the default), `*1H` or `*1D`, as a `*LINE` or `*BAR` chart. With [Arcforge](page:integrations/arcforge),
`*MCHOPS` (a machine's operations a minute) and `*MCHFE` (its FE stored) graph one machine, named by its device name
in `ITEM()`.

The data comes from a [Monitoring Server](page:racks/devices) on the network when there is one. Otherwise, and
always for `*STORAGE` and `*ITEM`, the screen keeps its own history: one sample a second while it is loaded, for the
last {{config rack.displayHistorySeconds default}} seconds.

### Images {#images}

A region can show a PNG from the system's image folder, `<world>/encodedlogistics/images/<SYSNAME>/` (see
{{page admin/folders}}), scaled to the region. Images are decoded off the server thread, and players are sent only the
rendered region, never the file.

| Colours | What you get |
|---|---|
| `16` | The fixed panel palette |
| `64`, `256` | A palette picked for the image |
| `*FULL` | Full 24-bit colour |

The palette modes use a stable ordered dither; `*FULL` and `SCALE(*NEAREST)` aren't dithered.

Images are a server choice:

- {{config rack.allowImages default}} switches them: `AUTO` is on in single-player and off on dedicated servers. Off,
  `SNDDSPIMG` is {{msg ELC1315}}.
- A file is limited to {{config rack.displayMaxImageBytes default}} MB, {{config rack.displayMaxImageSize default}}
  pixels each way and {{config rack.displayMaxImagePixels default}} pixels in all ({{msg ELC1313}}). The limits are
  read from the file's size and its header before anything is decoded.
- {{config rack.displayImageColors default}} is the most colours a player may pick. Asking for more is
  {{msg ELC1317}}, and the image shows at the limit.
- A missing file, or one that isn't a PNG, is {{msg ELC1312}}.

## The configuration screen {#configuration}

Right-click a screen with an empty hand to open its configuration screen; it needs the Firewall's **BUILD**
permission. Once the screen has `*DSPTOUCH` triggers, a plain click is a touch, so sneak to open it.

| Tab | What you do there |
|---|---|
| Mode | Pick Text or Dashboard, rename the device, pick the background |
| Layout | The canvas with its regions (shown at up to 1.5x). Drag an edge between two regions to move it; drag an edge on the screen's border inward to split a region there; right-click a region to remove it. Edges snap to a 4 px grid |
| Widgets | A row per region (its letter, widget, data source and colour). "Change widget" picks the widget, its source (an item, a device type; a graph's stat, range and type; an image's file, scaling and colours; a table's file) and colour. A table's selection and sort come from `SNDDSPWDG` |

Every change is checked again on the server.

{{screenshot file="control/display-panel-layout.png" alt="The Display Panel configuration screen's Layout tab with three regions" show="The configuration screen of a 4 x 2 screen on its Layout tab, with regions A, B and C drawn on the canvas preview" angle="GUI scale 2, the whole window" size="1280x720"}}

## Touch {#touch}

Right-clicking a screen's face (not sneaking), with an empty hand once it has touch triggers or with any item in hand,
fires the `*DSPTOUCH` trigger event with `&DATA` such as `DSP01 A 40 12`: the screen, the region under the point
(`*NONE` outside them all) and the point in canvas pixels. It needs no permission (the program decides what a touch may
do) and is limited to four a second for each player. See {{page elcl/jobs-and-scheduling}} for triggers.

## Driving a screen from ELCL {#elcl}

| Command | Does |
|---|---|
| {{cmd SNDDSPTXT}} | Writes a text line (`LINE()` 1-64, or `*NEXT`), with `SIZE`, `COLOR`, `ALIGN`, `CLEAR` |
| {{cmd CLRDSP}} | Clears a region or the whole screen |
| {{cmd CHGDSPRGN}} | Sets a region's position, size and background |
| {{cmd SNDDSPWDG}} | Sets a region's widget |
| {{cmd SNDDSPGPH}} | Puts a graph in a region |
| {{cmd SNDDSPIMG}} | Puts an image in a region |
| {{cmd RTVDSPSIZ}} | Returns the screen's size |

The display commands need no Firewall permission.

```elcl
CHGDSPRGN DEV(DSP01) RGN(A) W(64)                       /* A: the left 64 px          */
CHGDSPRGN DEV(DSP01) RGN(B) X(64)                       /* B: the rest                */
SNDDSPGPH DEV(DSP01) RGN(A) STAT(*ENERGY) RANGE(*1H)   /* energy draw, the last hour */
SNDDSPWDG DEV(DSP01) RGN(B) WDG(*ITEM) ITEM(LOGIC_DIE) COLOR(*MINT)
SNDDSPIMG DEV(DSP02) RGN(A) FILE('logo.png') COLORS(64) /* a file name is quoted      */
SNDDSPWDG DEV(DSP02) RGN(B) WDG(*TABLE) FILE(ELSYS/DEVICES) +
          QRYSLT('STATUS *NE "*ONLINE"') SORT(TYPE NAME)  /* devices that aren't online */
```

More examples are on {{page elcl/control-and-plcs}} and {{page elcl/examples}}.
