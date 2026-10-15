---
title: Cables
section: networks
order: 20
summary: Network Cable, Dense Network Cable and Fiber Cable in 13 colours, how they connect and recolour, and the Cable Anchor and Cable Facade.
covers: [encodedlogistics:part_host]
---
Cables join network blocks together and carry lanes between them. There are three tiers, each in a neutral colour and
twelve dyes, and two attachments that change how a cable joins or looks: the {{item cable_anchor}} and the
{{item cable_facade}}.

## The three tiers {#tiers}

| Tier | Lanes | Drain per block | Look |
|---|---|---|---|
| {{item network_cable}} | {{config lanes.perCable default}} | {{config lanes.cableDrain default}} FE/t | Slim |
| {{item dense_network_cable}} | {{config lanes.perDenseCable default}} | {{config lanes.denseCableDrain default}} FE/t | Thick |
| {{item fiber_cable}} | {{config lanes.perFiberCable default}} | {{config lanes.fiberCableDrain default}} FE/t | Slim |

A cable uses no lanes itself; it only carries them. Its drain is paid while its network runs.

- **Network Cable** is the everyday cable. Eight lanes is enough for a few devices.
- **Dense Network Cable** carries four times as many lanes. Use it for trunks: the runs from the controller that
  everything else hangs off.
- **Fiber Cable** carries as many lanes as Dense in the slim profile, and it's the only cable that carries optical
  lines (Optical Multiplexer channels). Very long continuous fibre runs lose lanes to span loss, which Optical
  Amplifiers fix. Looking at a Fiber Cable shows a popup with its network, its span length, and its lanes in use out of those left after loss. See
  {{page networking/optical}}.

{{recipe network_cable}}

{{recipe dense_network_cable}}

{{recipe fiber_cable}}

All three tiers join each other. A link between two blocks carries the smaller of their two capacities, so a Network
Cable spliced into a Dense run limits that run to the Network Cable's lanes. See {{page networks/lanes}}.

## Connections {#connections}

A cable connects on each side to:

- another cable of a compatible colour (see below), of any tier;
- a network block that connects on that face: a controller, a Power Inlet (not its port), a Drive Bay (not its front),
  a device;
- nothing, otherwise.

Cables connect by themselves when you place them. A side with a part or a Cable Anchor on it never connects.

## Colours {#colours}

Every tier comes in **neutral** and twelve dyes. The rule is simple:

- **Neutral joins every colour.**
- **A dyed cable joins only its own colour and neutral.**

So two parallel runs in different colours never merge, and a neutral cable can tap into either. Black, brown, grey and
light grey cables don't exist. A cable's tooltip says which colours it joins.

{{screenshot file="networks/cable-colours.png" alt="Red and blue cables running side by side without joining, with a neutral cable joining both" show="A red Network Cable run and a blue Network Cable run side by side and touching without connecting, a neutral cable joining each of them to a Drive Bay, and a Dense and a Fiber Cable of the same red joined into the red run" angle="Standing three blocks away, eye level just above the cables, looking along the runs at a slight angle" size="1600x900"}}

| Colour | Network Cable | Dense Network Cable | Fiber Cable |
|---|---|---|---|
| Neutral (connects to every colour) | {{item network_cable}} | {{item dense_network_cable}} | {{item fiber_cable}} |
| White | {{item white_network_cable}} | {{item white_dense_network_cable}} | {{item white_fiber_cable}} |
| Orange | {{item orange_network_cable}} | {{item orange_dense_network_cable}} | {{item orange_fiber_cable}} |
| Magenta | {{item magenta_network_cable}} | {{item magenta_dense_network_cable}} | {{item magenta_fiber_cable}} |
| Light blue | {{item light_blue_network_cable}} | {{item light_blue_dense_network_cable}} | {{item light_blue_fiber_cable}} |
| Yellow | {{item yellow_network_cable}} | {{item yellow_dense_network_cable}} | {{item yellow_fiber_cable}} |
| Lime | {{item lime_network_cable}} | {{item lime_dense_network_cable}} | {{item lime_fiber_cable}} |
| Pink | {{item pink_network_cable}} | {{item pink_dense_network_cable}} | {{item pink_fiber_cable}} |
| Cyan | {{item cyan_network_cable}} | {{item cyan_dense_network_cable}} | {{item cyan_fiber_cable}} |
| Purple | {{item purple_network_cable}} | {{item purple_dense_network_cable}} | {{item purple_fiber_cable}} |
| Blue | {{item blue_network_cable}} | {{item blue_dense_network_cable}} | {{item blue_fiber_cable}} |
| Green | {{item green_network_cable}} | {{item green_dense_network_cable}} | {{item green_fiber_cable}} |
| Red | {{item red_network_cable}} | {{item red_dense_network_cable}} | {{item red_fiber_cable}} |

### Dyeing cables {#dyeing}

**In the world.** Use a dye on a placed cable to recolour it; it uses up one dye. Anything mounted on the cable
(parts, anchors, facades) stays where it is. Use a **water bucket** on a dyed cable to wash it back to neutral (the
bucket stays full). A water bucket on a neutral cable waterlogs it, as on any waterloggable block.

**In a crafting grid.** Eight cables of one tier, in any mix of colours, around a dye make eight cables of that
colour. For example, for red Network Cable:

{{recipe-id encodedlogistics:red_network_cable_from_dye}}

The same works for Dense and Fiber Cable with eight of that tier. There's no grid recipe back to neutral: wash a
placed cable with a water bucket instead.

**Making a coloured cable of a higher tier.** A coloured Dense or Fiber Cable can also be made straight from cables of
the same colour, with the same recipe as the neutral one (four coloured Network Cables and redstone make one coloured
Dense Network Cable; four coloured Dense Network Cables, Fiberglass and a gallium ingot make four coloured Fiber
Cables):

{{recipe-id encodedlogistics:red_dense_network_cable}}

## Cable Anchor {#anchor}

The {{item cable_anchor}} stops one side of a cable connecting, for this cable and its neighbour alike. Use it to
keep two parallel cables of the same colour from joining, or to stop a cable linking into a block next to it.

{{recipe cable_anchor}}

Use it on a cable: it goes on the face you click. Since a connected side is covered by the cable's arm, the easiest
way is to anchor the side first and then place the neighbouring cable or block. To take an anchor off, use a wrench
on it, or sneak-use it with an empty hand.

## Cable Facade {#facade}

A {{item cable_facade}} is a panel that covers one side of a cable, and it can be dressed to look like a block, so
cables can run hidden in walls and floors.

{{recipe cable_facade}}

- **Mounting.** Use a facade on a cable. It goes on the side your crosshair points toward from the cable's centre
  (connected sides included); if that side is taken, it goes on the face you clicked. While you hold a facade and look
  at a cable, a ghost preview shows where it will go. A facade doesn't stop the side connecting.
- **Dressing.** Use a block on a mounted facade to dress it in that block. It uses up one of the block and gives back
  any block the facade had before. The block must be a full, solid, non-translucent cube without a block entity, and
  not on the {{config facades.blocklist}} list (empty by default). An opaque facade hides the neighbouring block's
  face, just as a full block would.
- **Removing.** Sneak-use with an empty hand: the first time takes the dressing block off, the second takes the
  facade off. A wrench takes it off too. Broken, it drops a blank facade and its block.

{{screenshot file="networks/cable-facades.png" alt="Cables hidden behind facades dressed as stone bricks, with one facade being placed" show="A wall of stone bricks where a cable run is hidden behind facades dressed as stone bricks, the last section still showing the bare cable, and the ghost preview of a facade in the player's hand over it" angle="First person, facing the wall from two blocks away, the facade in the main hand" size="1600x900"}}

## Mining and pick-block {#mining}

Mining a cable takes off the facade or part you're looking at first, and the cable itself only once nothing is in the
way. Pick-block gives the part or facade you're looking at, or the cable.

## Parts on cables {#parts}

Terminals, ports, taps, sensors, planes and links mount on the faces of cables. A cable carrying parts is still a
cable (it carries its tier's lanes) and is also a device needing a lane for each part. Parts can also be mounted
straight on the face of a network block; the mod then places an internal block, the **Part Host**
(`encodedlogistics:part_host`, which has no item), to hold the part. See {{page networks/cable-parts}}.

A cable carrying a Threshold Sensor (or a redstone Point-to-Point Link output) sends out redstone, and only then does
redstone dust connect to it.

## Firewall {#firewall}

On a network with a Firewall, placing or breaking network blocks, and putting parts, anchors or facades on its
cables, needs the BUILD permission. See {{page racks/security}}.
