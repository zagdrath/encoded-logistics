---
title: Control Interface
section: control
order: 10
summary: A network device with six redstone channels, one per face, that ELCL reads with RTVRSIN and drives with CHGRSOUT.
---
The {{item control_interface}} gives ELCL redstone input and output. Each of its six faces is a channel of its own:
it reads the redstone level arriving on that face and can give out a level of its own there. It has no screen; you
drive it from a terminal or a program.

{{recipe control_interface}}

## Putting one on a network {#network}

Cable it to a network like any other device. It takes one lane and drains {{config reach.controlInterfaceDrain default}}
FE/t while its network runs, and it is online while its network is.

The first time it is on a network it takes the lowest free name of the form `CTLIFnn` (`CTLIF01`, `CTLIF02`, ...) and
keeps it, on its item too when you break it. {{cmd RNMDEV}} or Work with Devices ({{cmd WRKDEV}}) option 2 renames it.
It appears under that name in Work with Devices and in the prompter's device list.

## The six channels {#channels}

The faces are `*NORTH`, `*SOUTH`, `*EAST`, `*WEST`, `*UP` and `*DOWN`, and each has an input and an output level from
0 to 15.

| To | Command | Notes |
|---|---|---|
| Read a face | {{cmd RTVRSIN}} `DEV(CTLIF01) SIDE(*NORTH) RTNLVL(&L)` | `SIDE(*MAX)` gives the highest of the six |
| Drive a face | {{cmd CHGRSOUT}} `DEV(CTLIF01) SIDE(*UP) LVL(15)` | `SIDE(*ALL)` sets all six; a level outside 0-15 is {{msg ELC0004}} |

```elcl
DCL VAR(&L) TYPE(*INT)
RTVRSIN DEV(CTLIF01) SIDE(*MAX) RTNLVL(&L)       /* the strongest signal coming in */
IF COND(&L *GE 8) THEN(CHGRSOUT DEV(CTLIF01) SIDE(*ALL) LVL(15))
ELSE CMD(CHGRSOUT DEV(CTLIF01) SIDE(*UP) LVL(0)) /* top face off                   */
```

At the `===>` prompt you can leave the keywords out: `RTVRSIN CTLIF01 *MAX` shows `RTNLVL = 9` on Command Entry, and
`CHGRSOUT CTLIF01 *ALL 15` turns every face on at full strength.

### How the output behaves {#output}

- **Weak power only.** A face powers what is directly next to it (a lamp, dust, a repeater), but it never strongly
  powers a solid block there, so it doesn't reach through that block to things beyond it.
- **It never reads itself.** A face's input is only what something else gives it. Redstone dust that the interface
  powers itself only counts when something else drives that dust higher.
- **Offline.** While its network is down it gives out nothing, but it keeps its output levels (they are saved with the
  block) and gives them out again when it is back online. While it is offline both commands answer {{msg ELC1302}}, and
  `CHGRSOUT` stores nothing.

### Errors {#errors}

A name no device has is {{msg ELC1301}}; a device that isn't a Control Interface (or a PLC, which has the same faces)
is {{msg ELC1303}}.

## LEDs {#leds}

Each face has an LED showing the higher of what comes in and what goes out there: off (0), dim (1-7) or bright (8-15).

{{screenshot file="control/control-interface-leds.png" alt="A Control Interface with lit face LEDs, wired to a lamp and a lever" show="A Control Interface on a cable, a lever on one face (on, bright LED) and a redstone lamp lit by another face; a third face dim from a weak signal" angle="Three-quarter view from about 3 blocks away, eye level, daylight" size="1600x900"}}

## Reacting to changes {#events}

When the level arriving on a face changes, the interface fires a `*RSCHANGE` trigger event, once per change, with its
device name and `&DATA` such as `*NORTH 7` (the face and the new level). A trigger added with {{cmd ADDTRGEVT}}
`EVENT(*RSCHANGE)` on it (for one face only: `VALUE(*NORTH)`) runs its program then, so you don't have to poll. See
{{page elcl/jobs-and-scheduling}} for triggers.

## Permissions {#permissions}

On a network with a Firewall, `RTVRSIN` needs the **VIEW** permission and `CHGRSOUT` needs **BUILD** (the commands'
CONFIGURE authority). See {{page racks/security}}.

For a block that runs its own program next to the redstone, with no terminal involved, use a {{page control/plc}}.
