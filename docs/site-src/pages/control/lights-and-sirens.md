---
title: Cage Lights and Alarm Strobes
nav: Lights and strobes
section: control
order: 40
summary: The 16 Cage Lights and the green, amber and red Alarm Strobes - mounting, the settings screen, light modes, the six siren tones, and switching them from redstone or ELCL.
---
Cage Lights and Alarm Strobes are signal devices: lamps and beacons for a control room, a warehouse floor or an alarm
system. They work from plain redstone on their own, and on a network ELCL can switch them too. The
{{page control/speakers}} are the third kind of signal device and share everything in [What they share](#shared).

## What they share {#shared}

- **Mounting.** They go on any face of a block (floor, ceiling or wall), pointing away from it.
- **On their own** they work from the redstone at their block.
- **On a network.** Cabled to a network, or placed against a networked block, each is also a device there: one lane,
  {{config signals.signalDeviceDrain default}} FE/t, named `LGT01` (lights), `SRN01` (strobes) or `SPK01` (speakers),
  and listed in Work with Devices.
- **The settings screen.** Right-click one to open it (on a network, with the Firewall's **BUILD** permission). Click a
  row to step its value, right-click to step back, hold Shift for steps of one. Text rows (the device name, a Speaker's
  URL or notes) are edited in the field at the bottom: Enter sets it, Esc leaves it.
- **The popup.** Look at one for its state, mode and device name.
- **ELCL.** Their commands take `DEV()` as a list of up to 32 devices, or `*ALL` for every one of that kind on the
  network, and need the Firewall's **BUILD** permission.

## Cage Lights {#cage-lights}

A Cage Light is a caged lamp, one for each of the 16 dye colours:

{{items encodedlogistics:cage_light_*}}

Four come from iron bars, glass, glowstone dust, iron nuggets and the dye:

{{recipe cage_light_red}}

| Setting | Values |
|---|---|
| Control | **Redstone** (the default: on with a signal), **Inverted** (on without one), **Always on**, or **Network only** |
| Level | The light level it gives while lit, 1-15 (default 15) |

The network's state, set by {{cmd CHGLGT}}, adds to the redstone's: either one turns the light on. **Always on**
ignores both; **Network only** ignores the redstone.

```elcl
CHGLGT DEV(LGT01 LGT02) STATUS(*ON) LVL(15)
CHGLGT DEV(*ALL) STATUS(*TOGGLE)
```

A level outside 1-15 is {{msg ELC2401}}.

## Alarm Strobes {#alarm-strobes}

An Alarm Strobe is a flashing beacon with a sounder, in three colours whose lenses match the lime, orange and red Cage
Lights: {{item siren_green}}, {{item siren_amber}} and {{item siren_red}}. Each is made from redstone, a note block, an
iron ingot, glass and the dye:

{{recipe siren_red}}

| Setting | Values |
|---|---|
| Light mode | Solid, Slow flash, Medium flash or **Fast flash** (the default) |
| Sound | **Wail** (the default), Yelp, Klaxon, Bell, Horn, Beep, or Silent (light only) |
| Volume | 0-100 % (default 80) |
| Range | How far the sound carries, in blocks (default 48, at most {{config signals.sirenMaxRange default}}) |
| Trigger | **Redstone**, **Network** (ELCL only) or **Both** (the default) |

While its trigger says so, the strobe is active: the lens lights in its light mode and the tone plays, looping. The
tone starts again at the end of each sample, and strobes on one network sounding the same tone count their samples from
the same moment, so a whole hall of them wails together.

| Tone | Sample length |
|---|---|
| Wail | 80 ticks |
| Yelp | 40 ticks |
| Klaxon | 32 ticks |
| Bell | 40 ticks |
| Horn | 40 ticks |
| Beep | 24 ticks |

{{cmd STRSRN}} starts strobes from the network (optionally changing their sound and mode) and {{cmd ENDSRN}} stops
them. A strobe whose trigger is Redstone takes no network commands ({{msg ELC2408}}).

```elcl
STRSRN DEV(*ALL) SOUND(*KLAXON) MODE(*FAST)   /* every strobe on the network */
DLYJOB DLY(30)
ENDSRN DEV(*ALL)
```

{{screenshot file="control/lights-and-strobes.png" alt="Cage Lights in several colours and a red Alarm Strobe flashing" show="A ceiling row of Cage Lights in white, yellow and cyan, lit, and a red Alarm Strobe on the wall mid-flash, in a dark room" angle="From the floor looking up and along the row, night or a dark room so the light shows" size="1600x900"}}

The sounds' subtitles are listed on {{page reference/sounds}}. Programs that raise alarms from storage levels, power
or device faults are on {{page elcl/control-and-plcs}} and {{page elcl/examples}}.
