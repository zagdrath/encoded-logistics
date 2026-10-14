---
title: Arcforge
section: integrations
order: 10
summary: With Arcforge 2.5.0 or later installed, a Small Wireless Bridge puts an Arcforge machine on a network to read, control, power and feed it; Pressurized drives store Arcforge's gases; and the Arc Crusher grinds the mod's ores into dusts.
---
Arcforge is an optional companion mod with machines, gases and a crusher. With it
installed, Encoded Logistics adds three things:

1. **Machines on the network** through the {{item small_wireless_bridge}}: read them, switch them, set them up, power
   them and feed them from the network, all from the Terminal OS.
2. **Gas storage:** Arcforge's gases go into Pressurized Storage Drives.
3. **Dusts:** the Arc Crusher grinds the mod's ores, raw metals and ingots into dusts.

Without Arcforge none of this is there, and nothing else in the mod changes.

## Which Arcforge works {#versions}

Encoded Logistics talks to Arcforge only through Arcforge's published API, which came in **Arcforge 2.5.0**. At startup
it reads the API's version from the Arcforge that is installed and turns on only what that version supports:

| Arcforge API | Machines (Small Wireless Bridge) | Gas storage |
|---|---|---|
| None (Arcforge before 2.5.0) | Off | Off |
| 1.0 | On | Off |
| 1.1 or a later 1.x | On | On |
| 2.0 or later | Off | Off |

Whatever it turns off, the server log says why: look for "Arcforge integration disabled" or "Arcforge gas storage
disabled", or "Arcforge integration enabled" when all is well. With the integration off, Small Wireless Bridges already
placed are kept but do nothing, and every machine command answers {{msg ELC1318}}. Nothing is lost: installing a
matching Arcforge brings them back as they were.

## Machines through the Small Wireless Bridge {#machines legacy="arcforge"}

{{recipe small_wireless_bridge}}

### Putting one on {#attach}

Use the bridge on a face of any block that is an Arcforge machine: a single-block machine, any block of a formed
multiblock, or an unformed multiblock's controller. It goes on as a small 10 x 10 module in front of that face, so the
space there has to be free.

- One per machine: a whole multiblock takes one.
- Only the machine's owner (when Arcforge records one) or a server operator may put one on.
- To take it off, mine it (with a pickaxe), or sneak-use the machine's face with an empty hand (this needs the
  Firewall's **BUILD** permission while it is linked). Breaking the machine drops it.

### Linking it to a network {#linking}

It joins a network over the air, as a [Wireless Bridge or Wireless Port](page:networking/wireless) does. Take the
network's Wireless Controller with a Link Card, then use the card on the bridge (or the machine). That needs the
Firewall's **BUILD** permission on the network. Linked, it uses one of the Access Points' client slots, is a one-lane
device on the network, and drains {{config rack.smallWirelessBridgeDrain default}} FE/t over the air. Unlinking it at the
controller (its Linked tab) takes it off the network.

| LED | Means |
|---|---|
| Yellow, blinking | Not linked, or not connected |
| Light blue | Online |
| Amber | A fault: no Access Points, no free client slot, or the machine missing, unformed or faulted |

Looking at the bridge shows its own popup (its controller, the machine, its lane and drain, its Gateway and power from
network); looking at the machine shows the machine's status, progress and time left, energy, heat and what it is
making. The popups need the Firewall's **VIEW** permission.

{{screenshot file="integrations/small-wireless-bridge.png" alt="A Small Wireless Bridge on an Arc Crusher, with its light blue LED lit and its popup showing" show="An Arcforge Arc Crusher with a Small Wireless Bridge on one side face, linked and online (light blue LED), the player looking at the bridge so its popup shows" angle="Three-quarter view, 2 blocks away, crosshair on the bridge" size="1600x900"}}

### Device names {#names}

A bridged machine is a device named after its type: the first three letters of each of its first two words, or the
first six letters of a one-word type, then a number. An Arc Crusher (`arcforge:arc_crusher`) is `ARCCRU01`; a type of
one word such as `arcforge:electrolyzer` is `ELECTR01`; a type with no name is `MCH01`. Work with Devices lists it as a
Machine.

### What the network can do with a machine {#control}

- **Read it:** its status and the reason for it, the current operation and its outputs, energy, heat, tanks,
  statistics and settings.
- **Switch it on or off**, and change its **redstone mode** (ignore, high, low, pulse, throttle), each **side's mode**
  (top, bottom, left, right, back, front) and **auto-eject**. Every change goes through Arcforge's own rules: a setting
  the machine doesn't have is {{msg ELC1320}}, one it refuses is {{msg ELC1321}}, and an unformed multiblock is
  {{msg ELC1319}}.
- **Power it from the network** ([below](#power)).
- **Feed it from a Gateway** ([below](#gateway)) so crafting jobs can use it.
- **React to it:** the trigger events `*MCHIDLE`, `*MCHFAULT` and `*MCHNOPWR` fire as the machine goes idle, faults or
  runs short of power, and `*MCHDONE` for each operation it finishes, with `&DATA` such as `ARCCRU01 5 minecraft:bone_meal`.
- **Graph it:** a [Monitoring Server](page:racks/devices) samples each machine's operations a minute and FE stored
  once a second, for [Display Panel graphs](page:control/display-panels#graphs) (`*MCHOPS`, `*MCHFE`). That history
  isn't saved.

Reading (Work with Machines, the `RTVMCH*` commands, the popups) needs the Firewall's **VIEW** permission; changing a
machine needs **BUILD**.

### Work with Machines {#wrkmch}

{{cmd WRKMCH}} lists the network's bridged machines: name, type, status (bright when it needs attention), progress,
energy and operations a minute.

{{screen WRKMCH}}

| Option | Does |
|---|---|
| 2=Change | A window for its redstone mode, auto-eject, power from network and Gateway (F4 steps through each one's values); runs {{cmd CHGMCHCFG}} with what you changed |
| 5=Display | Display Machine: everything about it, its statistics and settings |
| 7=Enable/Disable | {{cmd CHGMCHSTS}}, the other way to how it is |
| 8=Sides | A single-block machine's sides, each with its mode |

The same from programs:

| Command | Does |
|---|---|
| {{cmd RTVMCHSTS}} | Status, reason, progress, FE and capacity, what it is making |
| {{cmd RTVMCHSTAT}} | Statistics: operations, items and fluids produced and consumed, uptime, operations a minute |
| {{cmd RTVMCHLST}} | The machines' names, by type and status |
| {{cmd CHGMCHSTS}} | `*ENABLE` or `*DISABLE` |
| {{cmd CHGMCHCFG}} | Redstone mode, a side's mode, auto-eject, power from network, the Gateway; applied in that order, stopping at the first one refused |

```elcl
DCL VAR(&STS) TYPE(*CHAR) LEN(10)
RTVMCHSTS MCH(ARCCRU01) RTNSTS(&STS)
IF COND(&STS *EQ '*FAULT') THEN(SNDMSG MSG('Arc Crusher ARCCRU01 has faulted') TOUSR(*SYSOPR))
```

### Power from the network {#power}

`CHGMCHCFG PWRNET(*YES)` (or 2=Change) tops the machine's FE up from the network each tick:

- up to {{config rack.machinePowerRate default}} FE/t,
- at {{config rack.machinePowerEfficiency default}}: with the default, 10 FE leave the network for every 9 that
  arrive,
- and never taking the network's stored energy below {{config rack.machinePowerReserve default}} of its capacity, so
  the network keeps running.

Energy the machine has no room for stays in the network.

### Feeding it from a Gateway {#gateway}

`CHGMCHCFG GATEWAY(GATEWAY01)` (or 2=Change) makes the machine one of that [Gateway's](page:crafting/fabricators-and-gateways)
machines, over the air, as if it stood beside it. The Gateway puts a Processing Schematic's inputs in through the
machine's own slot and tank rules (inputs, fuel and catalysts only) and takes the outputs its runs are waiting for out
of the machine (items, fluids and gases), so the crafting job they belong to completes. Machines beside the Gateway are
fed first. The Gateway and the machine must be on the same network.

## Gases {#gases}

With Arcforge's API 1.1 or later, Arcforge's gases (its fluids that are lighter than air, or tagged
`#arcforge:gases`) are stored as **pressurized** resources: they go into
[Pressurized Storage Drives](page:storage/drives), in mB, and show on the terminals' Pressurized tab. They move
through the same ports and parts as fluids, and Arcforge's Gas Cartridges fill and empty at the terminals. Without
Arcforge there is nothing for a Pressurized drive to hold.

## Arc Crusher dusts {#dusts}

With Arcforge installed, the Arc Crusher grinds the mod's three metals. These nine recipes load only when Arcforge is
there; each takes 200 ticks:

| From | Gives | Bonus |
|---|---|---|
| Neodymium, Tantalum or Gallium ore (`#c:ores/...`) | 2 dust | 1 more at a 25% chance |
| Raw Neodymium, Tantalum or Gallium (`#c:raw_materials/...`) | 1 dust | 1 more at a 25% chance |
| A Neodymium, Tantalum or Gallium Ingot (`#c:ingots/...`) | 1 dust | None |

{{recipe-id encodedlogistics:crushing/gallium_dust_from_gallium_ore}}

The dusts themselves are on {{page crafting/ores-and-metals}}.

## Server options {#config}

{{table config keys=rack.smallWirelessBridgeDrain,rack.machinePowerEfficiency,rack.machinePowerRate,rack.machinePowerReserve}}
