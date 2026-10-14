---
title: Power
section: networks
order: 40
summary: Getting FE into a network, storing it in Capacitor Banks, the order the energy pool fills and drains in, and what a network spends.
---
A network runs on FE (Forge Energy). Encoded Logistics doesn't make any: you bring it in from another mod's
generators, through a {{item power_inlet}}, the controller's own faces or a few other ways in, and store it in the
controller, {{item capacitor_bank}}s and Energy Storage Drives. While the network has any energy stored it runs; when
the store is empty it shows **No power** and every device goes offline.

## The energy pool {#pool}

A network's energy is one pool made of:

- its **controller's buffer**: {{config controller.energyPerBlock default}} FE per Network Controller block, or the
  buffer of the working rack Network Controller or Midrange System;
- every **{{item capacitor_bank}}** on the network;
- every **Energy Storage Drive** in the network's drive holders (Drive Bays, Disk Drives and rack storage), whether or
  not the holder has its lane. See {{page storage/drives}}.

The controller's Network screen shows the whole pool as **Stored**, and a comparator against a controller block reads
it.

### Fill and drain order {#order}

FE coming into the network fills, in order:

1. Energy Storage Drives,
2. the controller,
3. Capacitor Banks,
4. a standby rack Network Controller's own buffer, with whatever is left.

The network's drain, and FE taken for work, comes out in this order:

1. Capacitor Banks,
2. Energy Storage Drives,
3. the controller.

So the controller stays topped up while the banks and drives take the swings, and the controller's buffer is the last
thing to run dry.

## Getting FE in {#input}

| Way in | Limit |
|---|---|
| A {{item power_inlet}}'s port | {{config power.inletMaxInput default}} FE/t per inlet |
| Any face of a Network Controller block | {{config controller.maxReceivePerBlock default}} FE/t per block |
| Any face of a Capacitor Bank, with direct IO on | {{config power.capacitorMaxTransfer default}} FE/t per bank |
| An {{item ingress_port}} in Energy mode, from the block it faces | Its energy rate ({{page networks/cable-parts}}) |
| A {{item server_rack}}'s connection points | {{config rack.rackPowerMaxInput default}} FE/t across them all; -1 means the same as a Power Inlet |

A Power Inlet (and an Ingress Port or a rack's connection points) accepts nothing while it isn't on a controller's
network, or while the network is in an error state: an invalid controller structure, or a conflict.

> [!TIP]
> An Ingress Port is a device, so it only works while the network is online: it can't restart a network that has run
> dry. Use Power Inlets (or the controller's faces) for the main supply.

## Power Inlet {#inlet}

{{recipe power_inlet}}

The Power Inlet passes FE straight into its network's pool. It takes FE **only through its front**, the port; cables
connect on its other five faces. Like a dispenser, its port faces you when you place it.

- It takes up to {{config power.inletMaxInput default}} FE per tick.
- It uses no lanes and drains nothing.
- With {{config power.inletBuffer}} above 0 (it's {{default power.inletBuffer}} by default), it also holds that much FE
  while the network is full, and passes it on as soon as there's room.
- It lights up while FE is coming in (it has received some in the last second). Jade shows `Receiving N FE/t`.

You can place it straight against a controller block or anywhere on the network's cables. Use several for more
input.

## Capacitor Bank {#bank}

{{recipe capacitor_bank}}

A Capacitor Bank stores {{config power.capacitorCapacity default}} FE as part of its network's pool. Cables connect on
all six faces; it uses no lanes and drains nothing. It moves at most {{config power.capacitorMaxTransfer default}} FE
per tick in and out, both to the network and through its faces.

- **Direct IO.** With {{config power.capacitorDirectIO default}}, every face also takes and gives FE directly, so a
  generator can push into it and another mod's cable can pull from it. Pulling from it takes from the network's pool.
- **The screen.** Right-click it: an energy bar, the fill percentage, and Stored, Input, Output and Capacity, with
  input and output averaged over the last second. It has no slots.
- **The gauge.** The block shows its fill in four segments. A comparator reads 0 when it's empty and 1 to 15 as it
  fills.
- **Charging a Handheld Terminal.** Use and hold a {{item handheld_terminal}} against the bank to charge it at
  {{config reach.handheldChargeRate default}} FE per tick. See {{page terminals/handheld}}.

{{screenshot file="networks/capacitor-bank.png" alt="A row of Capacitor Banks on a network, and the bank's screen" show="Three Capacitor Banks in a row cabled to a network, their front gauges at different fill levels, with the Capacitor Bank screen open showing the energy bar, fill percentage and the Stored, Input, Output and Capacity figures" angle="GUI scale 3 for the screen; for the world part, stand two blocks in front of the banks at eye level" size="1280x720"}}

## Creative Capacitor Bank {#creative}

The {{item creative_capacitor_bank}} is for creative mode and testing: it has no recipe and can't be broken in
survival. It's always full, gives its network (and any face) all the FE asked of it, and takes and voids everything
it's offered, with no per-tick limit. Its screen shows **Infinite**. Because the pool fills drives and the controller
before banks, it only voids what they can't take.

## What a network spends {#drain}

Every tick the network runs, it drains:

- {{config controller.drainPerBlock default}} FE/t for each Network Controller block (a rack controller's drain is
  part of its rack's instead);
- the passive drain of every other block on it: each cable block, each device, each part. Fractions of an FE carry
  over from tick to tick.

The Network screen shows the total as **Usage**, and hovering over a device in its grid shows that kind's passive
drain. Common drains:

| Block | Drain (FE/t) |
|---|---|
| {{item network_cable}}, {{item dense_network_cable}}, {{item fiber_cable}} (per block) | {{default lanes.cableDrain}}, {{default lanes.denseCableDrain}}, {{default lanes.fiberCableDrain}} |
| {{item drive_bay}} | {{default storage.driveBayDrain}}, plus {{default storage.driveBayDrainPerDrive}} per drive |
| A terminal | {{config storage.terminalDrain default}} |
| Any other cable part | {{config storage.partDrain default}} |
| A {{item point_to_point_link}} endpoint | {{config reach.p2pDrain default}} |
| A {{item segment_isolator}} (paid by each side) | {{config power.isolatorDrain default}} |

Work costs extra, taken from the pool as it's done: ports pay per item, bucket or FE moved, Fabricators per craft, and
so on. Rack devices have their own drains: {{page racks/devices}}. A rack's {{item ups}} covers a shortfall before the
pool is touched; see {{page racks/devices}}.

## Getting FE out {#output}

Besides a Capacitor Bank's faces, a network can give energy away through:

- an {{item egress_port}} in Energy mode, which pushes FE into the block it faces;
- a {{item small_wireless_bridge}} with power from network on, which tops up an Arcforge machine
  ({{page integrations/arcforge}}).

An energy {{item point_to_point_link}} moves FE too, but straight from the block its input faces to the blocks its
outputs face, without going through the pool.

Egress Ports and Small Wireless Bridges never take the pool below {{config rack.machinePowerReserve default}} of its
capacity, so the network keeps running.
