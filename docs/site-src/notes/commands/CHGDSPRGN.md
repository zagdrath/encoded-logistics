`CHGDSPRGN` defines a region on a Display Panel screen, or moves, resizes or recolours one. A region is a named
rectangle of the screen's canvas (32 pixels a panel each way, from the top left) that shows one widget. A Text screen
that a program gives a region becomes a Dashboard.

## A new region or an old one

`RGN` names the region: 1-8 letters and digits (anything else is {{msg ELC0003}}), in any case.

- **An existing region** keeps whatever you leave as `*SAME`, and keeps its widget.
- **A new region** starts at `X(0) Y(0)` and runs to the right and bottom edges of the screen, unless you give its
  position and size. It has no widget until {{cmd SNDDSPWDG}}, {{cmd SNDDSPGPH}} or {{cmd SNDDSPIMG}} gives it one.

The region must lie on the screen, and must not overlap any other region; either way it's {{msg ELC1314}} and
nothing changes.

> [!NOTE]
> A screen with no regions set up has one region, `A`, covering all of it. A new region would overlap it, so make `A`
> smaller first, then add the others in the space you freed.

## Background

`BG` is the region's background colour: `*SAME` (the default) keeps it (none, on a new region), `*DFT` is no
background of its own, and a palette name or `#RRGGBB` sets one. Palette names and hex colours go in quotes here:
`BG('*NAVY')`, `BG('#1F2228')` (an unquoted `BG(*NAVY)` is refused with {{msg ELC0103}}). A value that isn't a colour
is {{msg ELC0003}}.

A region that changes size has its image, if it shows one, drawn again at the new size. The screen doesn't have to be
online. A name that isn't on the network is {{msg ELC1301}}, and a device that isn't a Display Panel screen
{{msg ELC1303}}. `CHGDSPRGN` sends no completion message.

## Examples

A 4 x 2 screen (128 x 64 pixels), split into a left half and two quarters on the right:

```elcl
CHGDSPRGN DEV(DSP01) RGN(A) W(64)                       /* A: the left half      */
CHGDSPRGN DEV(DSP01) RGN(B) X(64) H(32) BG('*NAVY')    /* B: top right          */
CHGDSPRGN DEV(DSP01) RGN(C) X(64) Y(32)                 /* C: the rest, to the edges */
```

Use {{cmd RTVDSPSIZ}} to work the sizes out from the screen's real size. See
[Regions](page:control/display-panels#regions).
