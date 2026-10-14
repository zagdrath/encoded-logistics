`RTVDSPSIZ` tells a program how big a Display Panel screen is, so it can lay out regions and text that fit whatever
screen it's given. Each panel is 32 x 32 canvas pixels.

| Variable | Receives |
|---|---|
| `RTNW` | The screen's width in panels (1 to {{config rack.displayMaxWidth}}) |
| `RTNH` | Its height in panels (1 to {{config rack.displayMaxHeight}}) |
| `RTNPXW` | Its width in canvas pixels: `RTNW` x 32 |
| `RTNPXH` | Its height in canvas pixels: `RTNH` x 32 |

Declare them `*INT`, and give only the ones you need. The screen doesn't have to be online. A `DEV` that isn't on the
network is {{msg ELC1301}}, and a device that isn't a Display Panel screen {{msg ELC1303}}. It sends no completion
message.

Region positions and sizes ({{cmd CHGDSPRGN}}) are in canvas pixels. How much text fits at each size is on
{{cmd SNDDSPTXT}}.

```elcl
DCL VAR(&W) TYPE(*INT)
DCL VAR(&H) TYPE(*INT)
DCL VAR(&HALF) TYPE(*INT)
RTVDSPSIZ DEV(DSP01) RTNPXW(&W) RTNPXH(&H)
CHGVAR VAR(&HALF) VALUE(&W / 2)
CHGDSPRGN DEV(DSP01) RGN(A) W(&HALF)              /* left half  */
CHGDSPRGN DEV(DSP01) RGN(B) X(&HALF)              /* right half */
SNDDSPGPH DEV(DSP01) RGN(A) STAT(*ENERGY)
SNDDSPWDG DEV(DSP01) RGN(B) WDG(*UPS)
```
