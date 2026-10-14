`SNDDSPTXT` writes a line of text on a Display Panel screen, named by its device name (`DSP01`). In Text mode the
lines fill the whole screen; in Dashboard mode they show in any region whose widget is `*TEXT` (see
{{cmd SNDDSPWDG}}). Writing text doesn't change the screen's mode.

The screen must be online: offline (no running network, or no lane) is {{msg ELC1302}}. A device that isn't a
Display Panel screen is {{msg ELC1303}}.

## Where the line goes

- `LINE(n)` writes line *n*, 1 being the top, replacing what was there; empty lines are added above it if needed. The
  highest line is the number of `*SMALL` lines the screen holds, its canvas height over 6 pixels (5 on a screen one
  panel high, 10 on two, 32 on six). Past that is {{msg ELC0004}}.
- `LINE(*NEXT)` (the default) writes the line after the last one written: the top line on a new or cleared screen,
  line 4 after a `LINE(3)`. When the lines no longer fit down the screen, the top ones roll off, so a run of `*NEXT`
  writes scrolls like a log.
- `CLEAR(*YES)` removes every text line first, and `*NEXT` starts at the top again. Regions and widgets are left alone;
  {{cmd CLRDSP}} clears those.

Text longer than fits across the screen at the line's size is cut. A line holds the screen's canvas width less 8
pixels of margin, over the character width: 4 pixels at `*SMALL`, 6 at `*NORMAL`, 12 at `*LARGE`, 18 at `*HUGE`.
A screen four panels wide (128 pixels) holds 30, 20, 10 or 6 characters; one panel wide, only 4 at `*NORMAL`.

## Size, colour and alignment

Each line keeps its own size, colour and alignment. `*SAME`, the default for all three, keeps what the line had; a new
line starts at `*NORMAL`, the default text colour and `*LEFT`.

| Parameter | Values |
|---|---|
| `SIZE` | `*SMALL` (a 4 x 6 font), `*NORMAL` (the 6 x 10 terminal font), `*LARGE` (twice that), `*HUGE` (three times) |
| `COLOR` | `*DFT` (the default text colour), a palette name such as `*MINT`, `*RED` or `*YELLOW`, or `'#RRGGBB'` in quotes. Anything else is {{msg ELC0003}} |
| `ALIGN` | `*LEFT`, `*CENTER`, `*RIGHT` |

The palette's names are listed on [Display Panels](page:control/display-panels#text-sizes). `SNDDSPTXT` sends no
completion message.

## Examples

```elcl
SNDDSPTXT DEV(DSP01) TEXT('PLANT STATUS') LINE(1) CLEAR(*YES) +
          SIZE(*LARGE) COLOR(*MINT) ALIGN(*CENTER)
SNDDSPTXT DEV(DSP01) TEXT('Power: mains') LINE(2) COLOR(*GREEN)
SNDDSPTXT DSP01 'Restock started'                   /* the next line */
SNDDSPTXT DSP02 'ALARM' LINE(1) COLOR('#FF4040') SIZE(*HUGE)
```

{{cmd RTVDSPSIZ}} tells a program how big the screen is. See {{page control/display-panels}}.
