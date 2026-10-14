---
title: Networks
section: networks
order: 0
summary: What a network is, which blocks are devices and which just carry the network on, and how to tell whether it's online.
---
Everything in Encoded Logistics happens on a **network**: storage, terminals, ports, autocrafting, racks and scripts.
This section explains how networks are built and how they share out their two resources, **lanes** and **energy**.

## What a network is {#what}

A network is everything joined to one **controller structure**: a group of {{item network_controller}} blocks, a
{{item server_rack}} holding rack Network Controllers, or a {{item midrange_system}}. From the controller, the network
reaches every block that connects toward its neighbour, through cables and through devices, and on through remote
links such as a paired {{item network_bridge}} or a lanes {{item point_to_point_link}}. Two blocks are linked only
when both connect on the touching faces: a cable side and the neighbour's face.

A network has **one** controller structure. Blocks with no controller structure on their network are simply
offline: a Drive Bay or a terminal cabled to nothing but other devices does nothing until a controller joins them.
Two controller structures that end up on one network shut it down until one goes; see
[conflicts](page:networks/controllers#conflicts).

The network is worked out again whenever something on it is placed, broken or reconnected, from the next tick. A
controller structure that's partly in unloaded chunks waits, with its devices offline, until all of it is loaded
again.

## Devices and pass-through blocks {#devices}

The blocks on a network come in three kinds:

| Kind | What it does | Examples |
|---|---|---|
| **Device** | Needs lanes to work; switched online and offline every tick. | {{item drive_bay}}, terminals and other parts, {{item fabricator}}, {{item relay_antenna}}, a {{item server_rack}} |
| **Pass-through** | Uses no lanes; carries the network on through it. | Cables, {{item power_inlet}}, {{item capacitor_bank}}, {{item network_bridge}} |
| **End point** | On the network, but the network stops there. | {{item segment_isolator}}, {{item access_point}} |

Most devices pass the network on as well: a Drive Bay with cables on two sides joins them, just as a cable would. A
cable with parts mounted on it is both: still a cable carrying its lanes, and a device needing a lane for each part.

A device is **online** when the network is running (valid, powered and not in conflict) and the device got its
lanes. Offline devices keep their contents; a Drive Bay's drives just can't be reached until it's back.

## The pages in this section

Read them in this order:

1. **{{page networks/controllers}}**: the Network Controller's shapes, energy and lanes, its screen, the rack and
   Midrange controllers, and conflicts.
2. **{{page networks/cables}}**: the three cable tiers, their 13 colours, and the Cable Anchor and Cable Facade.
3. **{{page networks/lanes}}**: how lanes are handed out, nearest first, and what to do when they run out.
4. **{{page networks/power}}**: getting FE in and storing it: the Power Inlet, Capacitor Banks and the energy pool.
5. **{{page networks/segments}}**: splitting one cable run into separate networks with a Segment Isolator.
6. **{{page networks/cable-parts}}**: ports, taps, sensors, planes and links, and how parts mount on cables and block
   faces.

Terminals are parts too, but they have their own section: {{page terminals/index}}. Joining networks over distance
(bridges, fibre, wireless, routing between them) is in {{page networking/index}}.

## Seeing what's going on {#status}

- **The Network screen.** Right-click any block of a Network Controller structure: status, energy, lanes and every
  device on the network. See [the controller screen](page:networks/controllers#screen).
- **Jade.** With [Jade](page:integrations/jade) installed, looking at a cable shows `Lanes used / carried` (red when
  it's full), and a controller shows its status, lanes and structure.
- **The Terminal OS.** {{cmd WRKDEV}} lists every device on the network with its lanes and status, and
  {{cmd DSPNETSTS}} shows the network's status at a glance. See {{page elcl/index}}.
