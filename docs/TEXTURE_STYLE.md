# Encoded Logistics — Texture Style Guide

How every Encoded Logistics texture is shaded, coloured and textured so it reads as a real Minecraft texture.
This replaces the earlier "flat faces, no gradients, no dithering" rules: Encoded Logistics textures have
**stepped shading and material grain**, in the way vanilla, Create and AE2 do it.

The rules come from measuring the actual texture files of vanilla (iron block, crafter, observer, hopper,
lodestone, copper, wool), Create (andesite, brass, copper and railway casings, gearbox) and AE2 (controller,
drive, interface, covered/smart/dense cables). Those mods are studied for technique only. Never trace,
recolour or use their textures as a base; AE2's art is CC BY-NC-SA.

---

## 1. The five rules

1. **Mid tones carry the texture.** Most pixels sit in the middle of the steel ramp. Near-black is reserved
   for real holes and cast shadows; pure white is reserved for glints.
2. **Stepped tones, never gradients.** Colour changes in whole ramp steps. No lerped or per-pixel ramps.
3. **Every material has grain.** Large areas are broken up by one-step variation, never left as flat fills.
4. **Hue shifts with value.** Shadows lean cool (toward blue/purple) and gain saturation; highlights lean warm
   (toward yellow) and soften.
5. **Light comes from the top-left.** Top and left edges catch light, bottom and right edges fall into
   shadow, and raised detail casts its shadow down-right.

---

## 2. Palette

### 2.1 Steel ramp (all greys, casings, frames, traces, recesses)

One 11-step ramp. Shadows are cool, highlights warm-neutral. Pick tones by index; do not invent in-between greys.

| Step | Hex | Typical use |
| --- | --- | --- |
| 0 | `#1F2228` | Cast shadows, true holes |
| 1 | `#2B2F36` | Field grain (dark specks), deepest recess |
| 2 | `#373C44` | Controller field, inner lip under lit rails |
| 3 | `#454B54` | Field grain (light specks), dark steel |
| 4 | `#555B65` | Dividers, shaded steel |
| 5 | `#666D77` | Inner lip on shaded rails, bottom-right frame corner |
| 6 | `#79808A` | Bottom/right frame rail |
| 7 | `#8D949D` | Raised traces (runs); top-right/bottom-left frame corners |
| 8 | `#A3A9B1` | Trace ends and bends; worn top/left rail |
| 9 | `#BBC0C6` | Top/left frame rail |
| 10 | `#D3D7DB` | Top-left corner, glints |

Measured brightness to aim for (0–255): a block face should span roughly **35–215**, with most pixels
between **55 and 150**. Vanilla machine blocks and Create casings sit in that band. Our old textures sat at
20–45, which reads as netherite rather than a machine.

### 2.2 Dye ramps (cables and anything dye-coloured)

Seven steps (−3…+3) around a base colour:
- **Value:** each step is ±16% of the base value.
- **Shadows** (−1…−3): hue moves toward blue/purple by 4.5% of the gap per step, and saturation rises by 0.05 per step.
- **Highlights** (+1…+3): hue moves toward yellow by 4.5% of the gap per step, and saturation falls by 0.10 per step.

The tones below are the ramps the shipped cables use (base = the median dye pixel of each cable texture).

| Dye | Base | −3 … +3 |
| --- | --- | --- |
| orange | `#EC7F27` | `#7B0F02` `#A02C0A` `#C65217` `#EC7F27` `#FF9B44` `#FFAB5D` `#FFBB77` |
| magenta | `#D85BCC` | `#6C1E70` `#922F93` `#B543B0` `#D85BCC` `#FB83E6` `#FF9EE7` `#FFB8E7` |
| light_blue | `#50C2EC` | `#17577B` `#2678A0` `#399CC6` `#50C2EC` `#70E8FF` `#89F9FF` `#A3FFFA` |
| yellow | `#ECC138` | `#7B360B` `#A05D16` `#C68B25` `#ECC138` `#FFD756` `#FFDE70` `#FFE489` |
| lime | `#87D32E` | `#1C6E07` `#398F11` `#5DB11E` `#87D32E` `#ADF54E` `#C3FF6B` `#D1FF84` |
| pink | `#EC95B1` | `#7B3B5E` `#A05579` `#C67394` `#EC95B1` `#FFBACD` `#FFD4DD` `#FFEDF0` |
| cyan | `#27A2AA` | `#074A58` `#0F6574` `#1A838F` `#27A2AA` `#41C5C0` `#60E0CF` `#85FCDF` |
| purple | `#913FC6` | `#3E1167` `#571D87` `#732DA6` `#913FC6` `#BF60E6` `#E784FF` `#F69EFF` |
| blue | `#494EB6` | `#181B5F` `#25297C` `#363A99` `#494EB6` `#736AD3` `#A590F0` `#CDB3FF` |
| green | `#6A852B` | `#1F450C` `#355A14` `#4E701F` `#6A852B` `#819A41` `#9AB05C` `#B3C57B` |
| red | `#BA3B37` | `#610E23` `#7E1929` `#9C262E` `#BA3B37` `#D85E55` `#F6877A` `#FFA698` |

**White** cables are too desaturated to count as a dye, so they use the top of the steel ramp (steps 8–10).
**Neutral** cables have no fixed dye: their glow cycles the controller's hue wheel (see 6).
Dyes not offered: black, brown, gray, light gray.

### 2.3 Storage tier colours (drives)

Shown only as a stripe or label band, never a whole body.

| Tier | Name | Light | Base | Shade | Deep |
| --- | --- | --- | --- | --- | --- |
| 1K | Ember | `#FFB48A` | `#F07A3C` | `#B8521F` | `#7A3212` |
| 4K | Amber | `#FFE08A` | `#F0C030` | `#B88E14` | `#7A5C08` |
| 16K | Verdant | `#B5FFE3` | `#00D992` | `#00A06B` | `#00663F` |
| 64K | Azure | `#A8D8FF` | `#3FA3F5` | `#2474C2` | `#164A80` |
| 256K | Violet | `#DDC4FF` | `#A66BF5` | `#7A44C8` | `#4C2685` |

### 2.4 Status lights (drive bays, GUI fill bars)

| State | Lit | Base | Shade |
| --- | --- | --- | --- |
| < 50% green | `#B5FFB0` | `#3CE05A` | `#22A03C` |
| 50–75% yellow | `#FFF3A0` | `#F0D030` | `#B89A14` |
| 75–99% orange | `#FFC890` | `#F08A2A` | `#B05E14` |
| full red | `#FF9C90` | `#E5483C` | `#8E231C` |
| off | `#2B3A33` | `#1C2622` | — |

---

## 3. Grain

Grain is **one ramp step** up or down, applied **inside a run of the same material only**. Never put grain on
1px lines, outlines, edges or the ends of a feature; those stay crisp.

| Kind | Where | Pattern |
| --- | --- | --- |
| Brushed | Cable sleeves, any long metal part | Runs of 2–4 px **along the part's length**. Offsets: −1 20%, 0 68%, +1 12% (mostly darker wear, a few glints). |
| Wear specks | Flat faces: cube, junction and flange faces, drive array front, casing panels | About 13% of pixels: 1–2 px specks, 70% darker. |
| Field speckle | The controller field | Base step 2; 10% step 1, 7% step 3. |

Grain is seeded, so a regenerated texture is identical. Base textures and their `_glow` overlays share the same
grain; animated textures use **one grain pattern for every frame**, so nothing flickers.

---

## 4. Edges, frames and bevels

Frames read as a **recessed bevel**, not a flat line.

- **Outer rail (1 px):**
  - Top and left: step 9, with wear specks at step 8.
  - Bottom and right: step 6, with specks at step 5.
  - Corners: top-left step 10, top-right and bottom-left step 7, bottom-right step 5.
  - A 2px glint (+1 step) at positions 6–7 along every edge. It sits at the same spot on every piece, so seams line up.
- **Inner lip (1 px):** step 2 under the lit rails (top and left, the shadow the rail casts) and step 5 on the shaded rails (bottom and right, a lit inner edge).
- **Dividers** (where structure pieces join): stepped steel at step 4 with step-3 wear and step-3 ends. They read as a seam, never as a bright line.
- **No near-black grooves.** The old `#15181C` groove is retired.

---

## 5. Raised detail and recesses

- **Raised features** (controller traces, ribs):
  - Runs are step 7; ends and bends are step 8, where the light catches.
  - About 10% wear at −1.
  - Every raised pixel casts a shadow one pixel down-right, at step 0, onto the field.
- **Recesses** (bays, pockets) are the darkest part of a face: back walls at steps 1–2, with a step-0 shadow line directly under the overhang.
- **3D geometry over painted depth:** if something should look recessed or raised (drive bays, shelves, the spine), model it. A 2px rib with mid-steel tops, step-0 undersides and dark-steel sides reads better than any painted bevel.

---

## 6. Glow (emissive) textures

- Glow lives on a separate overlay: `*_glow.png` for cables and the drive array, `*_emissive.png` for the controller. It renders full-bright with `"neoforge_data": {"block_light": 15, "sky_light": 15}` and `"shade": false`.
- **The glow has texture too.** Use three tones:
  - saturated base on runs;
  - whiter, brighter glints at ends and bends (saturation ×0.6, value +0.06);
  - about 12% dimmer wear pixels (value ×0.86).
- Never whiten every exposed pixel. On 1px features that washes the whole glow out.
- A glow overlay copies its base texture pixel for pixel where it is lit, so the glowing strip keeps the jacket's grain.
- **Controller:** hue gradient rotating round the wheel, 16 frames, `frametime 6`, `interpolate: true`. Error state is static red with the same texture.
- **Neutral cable:** the same hue cycle, so the backbone pulses in step with the controller.

---

## 7. Block notes

**Network Controller** (`tools/ctrl.py`)
- 2px recessed bevel frame; field at step 2 with speckle; maze traces raised.
- 8 variants per piece, with column pieces and dividers. Traces cross a joined seam only at pixel 8.
- Traces never cross a framed side.

**Network Cables** (normal and dense)
- Texture sheets: `_h` (strip along u), `_v` (strip along v), `_j` (junction face), `_f` (flange face).
- Brushed grain runs along the cable: along u on `_h` sheets, along v on `_v` sheets. Wear specks go on `_j` and `_f`.
- Dye bands use the dye ramps in 2.2; the steel uses the steel ramp.

**Drive Array**
- Casing: the controller's frame with vent slats in trace steps and screws as raised features.
- Front: modelled 3D ribs (frame, shelves, spine) with 2px pockets. Rib fronts carry the frame steel.
- Drives in the bays: slate body, silver handle, a thin tier stripe at the outer end, and a 2×2 status light. The status light must stay the dominant colour in each bay.

**Items** (to follow these rules next)
- Outline in the darkest step of the item's own material, never pure black.
- 4–7 tones per material, top-left light, one-step grain on any face larger than about 4×4 px.

---

## 8. Don'ts (what made the old textures look wrong)

- Large fields in the 20–45 brightness range (near-black faces).
- Lerped "sheen" gradients that change colour every pixel.
- Perfectly flat fills on faces, bands or bays.
- Brightness-only ramps with no hue shift.
- Grain on outlines or 1px lines, or grain that changes between animation frames.
- Whole-body tier or dye colour fighting with status lights.

---

## 9. Workflow and checklist

1. **Draw or generate the texture with a script.** Every texture comes from a seeded generator, never edited by hand:

   | Script | Makes |
   | --- | --- |
   | `tools/ctrl.py` | Controller textures |
   | `tools/export_cables.py` | Cable models and base textures |
   | `tools/export_drives.py`, then `export_drive_array_v2.py` | Drive array assets (run in that order) |
   | `tools/run_pass.py` (with `mcpass.py`) | The shading pass. `export_cables.py` runs it on the cable textures; never run it twice over the same files |

2. **Check against this guide:**
   - [ ] Every grey is a step of the steel ramp; every dye pixel is a step of its dye ramp.
   - [ ] Brightness spans about 35–215 with mid tones dominant; near-black only in holes and shadows.
   - [ ] No gradient runs; grain is one step, inside materials only, with crisp edges and 1px lines.
   - [ ] Light is top-left; raised detail casts down-right; frames read as a recessed bevel.
   - [ ] The glow has base, glint and wear tones, matches its base pixels, and doesn't flicker.
   - [ ] The layout is unchanged when replacing a texture (size, UVs, opaque pixels, frame count).

3. **Preview from the files.** Render with `tools/mcrender.py`, which loads the blockstates, models and PNGs the way the game does. Check at 8× flat and in a scene next to the controller, cables and a drive array, and show the before and after.
