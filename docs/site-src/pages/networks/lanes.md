---
title: Lanes
section: networks
order: 30
summary: Where a network's lanes come from, what each link can carry, how lanes are handed out nearest first, and what to do when a device is missing one.
---
**Lanes** are a network's bandwidth. Every device needs lanes to work (most need one), and every lane a device uses has
to come from a lane source, along a path whose every link has room for it. A device that can't get its lanes is
**missing a lane**: it stays connected, but it's offline.

## Where lanes come from {#sources}

| Source | Lanes it gives |
|---|---|
| {{item network_controller}} | {{config lanes.perControllerFace default}} for every controller face touching something on the network (not faces between controller blocks) |
| {{item network_controller_2u}} | One budget of {{config rack.rackController2uLaneFaces default}} controller faces' worth, for the whole rack; a {{item network_controller_4u}} has twice that. A redundant pair shares one budget. |
| {{item midrange_system}} | One budget of {{config rack.midrangeLaneFaces default}} controller faces' worth |
| {{item integrated_midrange}} | One budget of {{config rack.integratedMidrangeLaneFaces default}} controller faces' worth |

The network's **capacity**, shown on the controller's Network screen, is the sum. Capacity alone doesn't guarantee a
device its lane: the lanes have to reach it.

## What each link carries {#links}

Every link between two blocks carries at most the **smaller** capacity of its two ends:

| Block or link | Lanes |
|---|---|
| A controller face | {{config lanes.perControllerFace default}} |
| {{item network_cable}} | {{config lanes.perCable default}} |
| {{item dense_network_cable}} | {{config lanes.perDenseCable default}} |
| {{item fiber_cable}} | {{config lanes.perFiberCable default}}, less any span loss on long runs ({{page networking/optical}}) |
| Devices, Power Inlets, Capacitor Banks and other blocks | No limit of their own |
| A paired {{item network_bridge}} | {{config reach.bridgeLanes default}} |
| A lanes {{item point_to_point_link}} | {{config reach.p2pLanes default}}, split evenly between its outputs (at least 1 each) |
| A {{item wireless_bridge}} | {{config rack.wirelessBridgeLanes default}} |
| An Optical Multiplexer channel | A Fiber Cable's lanes, less the line's span loss |

So a link between a Dense Network Cable and a Network Cable carries only the Network Cable's lanes, and all the
devices beyond a Network Cable share that cable's lanes between them.

## How lanes are handed out {#allocation}

Each time the network changes, it works out who gets lanes, the same way every time:

1. **Every device gets one path.** Starting from all the lane sources at once, the network finds each block's
   shortest path back to a source. Where two paths are equally short, it prefers the neighbour below, then above,
   then north, south, west and east, then remote links.
2. **Nearest devices go first.** Devices are taken in order of how far they are from a source (ties by position).
3. **A device gets its lanes only if every link on its path has room** (and the source budget, for a rack or a
   Midrange System). Otherwise it's missing a lane.

Two things follow from this:

- **Far devices lose out first.** When a cable is full, the devices furthest from the controller are the ones left
  without lanes.
- **A device doesn't go round a full cable.** Its path is its shortest one. A second, longer cable running alongside
  doesn't help the devices whose shortest path goes through the full one. Replace the full cable with a higher tier,
  or give the far devices a shorter path of their own (a second controller face, a separate cable from the
  controller).

The result is stable: reloading the world gives the same devices their lanes.

## Server Racks {#racks}

A {{item server_rack}} is one device to the network, but it takes its lanes demand by demand: its devices one at a
time, by priority (High first, then from the highest unit down, so the lowest units are the first to go without),
each over whichever of the rack's uplinks has room on its path to a source. A rack that can't place every demand, or
has an uplink that leads nowhere, shows as **degraded**. Switches in the rack pool lanes for the other devices, so the
network only carries their uplinks. See {{page racks/server-rack}} and {{page racks/devices}}.

## What each device costs {#costs}

| Device | Lanes |
|---|---|
| Terminals (Access, Fabrication and Schematic Terminals, the Schematic Encoder) | {{config storage.terminalLanes default}} each |
| Other cable parts (ports, taps, sensors, planes, the Trunk Port) | {{config storage.partLanes default}} each |
| {{item point_to_point_link}} endpoints | None |
| Most block devices: {{item drive_bay}}, {{item fabricator}}, {{item gateway}}, {{item relay_antenna}}, {{item access_point}} and others | 1 |
| Cables, {{item power_inlet}}, {{item capacitor_bank}}, {{item segment_isolator}}, {{item network_bridge}}, {{item wireless_bridge}} | None |
| Rack devices | Their own cost each (see {{page racks/devices}}); rack Network Controllers use none |

A cable with parts on it needs the sum of its parts' lanes, and drains for them as well as for itself.

## When a device is missing a lane {#missing}

You'll notice it as a device that's dark, a terminal that won't open, or a red count on the controller's Network
screen. To find where:

- **The Network screen** counts, for each kind of device, how many are missing a lane.
- **Jade** shows `Lanes used / carried` on any cable; it turns red on a full one. Follow the red cables back
  toward the controller to find the bottleneck.
- **The Terminal OS**: {{cmd WRKDEV}} lists every device with its lanes and status.

Then:

- Upgrade the full cable: Network Cable to Dense or Fiber Cable.
- Connect more controller faces: each face touching the network adds lanes, and a larger controller frame has more
  faces to run cables from.
- Split the devices across several cables from different controller faces, so no single cable carries them all.
- Put devices in a Server Rack behind a switch, which pools lanes for them.
- In the config, {{config storage.terminalLanes}} and {{config storage.partLanes}} can be set to 0 to make terminals or
  parts free.

> [!NOTE]
> A network with no controller structure isn't a network at all: its devices are offline, whatever their lanes.
