`SNDDSPIMG` shows a PNG picture in a region of a Display Panel screen, stretched to fill the region. It replaces whatever
the region showed. A Text screen given an image becomes a Dashboard. The region must exist ({{cmd CHGDSPRGN}}; on a
screen with none set up, `A` is the whole screen), or it's {{msg ELC1314}}.

## The file

`FILE` is a PNG in the system's images folder, `<world>/encodedlogistics/images/<SYSNAME>/` (see
{{page admin/folders}}). Quote it: `FILE('logo.png')`. The name may use letters, digits, `_`, `.` and `-` (no spaces)
and must end `.png`; its case doesn't have to match.

| Problem | Message |
|---|---|
| Images are off on this server: {{config rack.allowImages default}} (`AUTO` is on in single-player, off on dedicated servers) | {{msg ELC1315}} |
| No such file, a name it won't take, or not a PNG | {{msg ELC1312}} |
| Larger than {{config rack.displayMaxImageBytes default}} MB, wider or taller than {{config rack.displayMaxImageSize default}} pixels, or more than {{config rack.displayMaxImagePixels default}} pixels in all | {{msg ELC1313}} |

The limits are read from the file's size and its header before any pixels are decoded. The picture is then drawn at
the region's size away from the server's main thread, and shows when it's ready; players are sent the drawn region,
never the file. If the region changes size, or the world is loaded again, it's drawn again from the file; a file that
has gone, or grown past the limits, leaves the region blank.

## Scaling and colours

| `SCALE` | Does |
|---|---|
| `*DITHER` (default) | Smooth scaling, then a stable ordered dither into the colour mode's palette |
| `*NEAREST` | Nearest-pixel scaling and no dither: keeps pixel art crisp |

| `COLORS` | Palette |
|---|---|
| `*DFT` (default) | The same as `256` |
| `16` | The fixed 16-colour panel palette |
| `64`, `256` | A palette picked for the image |
| `*FULL` | Full 24-bit colour, never dithered |

Any other `COLORS` value is {{msg ELC0003}}. {{config rack.displayImageColors default}} is the most colours the server
allows: asking for more sends the diagnostic {{msg ELC1317}} (severity 10) and shows the image at the limit, so the
command still works. That diagnostic is the only message `SNDDSPIMG` sends when it succeeds.

The screen doesn't have to be online. A `DEV` that isn't on the network is {{msg ELC1301}}, and a device that isn't a
Display Panel screen {{msg ELC1303}}.

## Examples

```elcl
SNDDSPIMG DEV(DSP01) RGN(A) FILE('logo.png')
SNDDSPIMG DEV(DSP01) RGN(B) FILE('floorplan.png') SCALE(*NEAREST) COLORS(16)
SNDDSPIMG DEV(DSP02) RGN(A) FILE('photo.png') COLORS(*FULL)
```

See [Images](page:control/display-panels#images).
