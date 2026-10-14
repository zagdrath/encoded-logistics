---
title: Bridges and links
section: networking
order: 40
summary: The Network Bridge that joins two places at any distance or across dimensions, the Link Card and everything it pairs, the Point-to-Point Link, and the Relay Antenna.
---
These are the ways to reach further than a cable goes. A Network Bridge pair joins two places into one network, however
far apart. A Point-to-Point Link passes one thing straight across. A Relay Antenna gives Handheld Terminals coverage.
And the Link Card is the tool that pairs nearly everything.

## Network Bridge {#network-bridge}

Two paired {{item network_bridge}}s join their networks as if a cable of {{config reach.bridgeLanes default}} lanes ran
between them: at any distance, and across dimensions while {{config reach.bridgeCrossDimension default}}. Whatever is
cabled to the far Bridge becomes part of the near Bridge's network: the same storage, lanes and power.

{{recipe network_bridge}}

- **Placing:** it faces you. Cables connect on every side except the front, which is its optical port.
- **Cost:** it uses no lanes itself and drains {{config reach.bridgeDrain default}} FE/t.
- **Pairing:** sneak-use a {{item link_card}} on one Bridge, then use the card on the other. Pairing replaces any old
  partners. It fails on the same Bridge, or across dimensions while cross-dimension bridging is off.
- **Both ends must be loaded** for the link to carry anything. With {{config reach.bridgeChunkLoading default}} turned
  on, a linked Bridge keeps its own chunk loaded.
- **Upkeep:** every half second a Bridge checks its partner still points back to it. One whose partner was broken or
  paired elsewhere unlinks once its chunk is loaded.

Its look shows its state: **unlinked** (amber), **linked and idle**, or **linked and active** (lanes crossing; the lens
pulses). Right-click it for its screen: a status light and line (linked, not linked, partner offline), the partner's
position and dimension, and a 32-segment meter of the lanes crossing.

A network has one controller structure. A Bridge joins places into one network, so put the controller on one side
only.

{{screenshot file="networking/network-bridge.png" alt="A Network Bridge with its lens lit and a cable on its back" show="A Network Bridge in its linked and active state with the lens glowing, a network cable into its side, the front optical port facing the camera" angle="Two blocks in front, slightly to one side, eye level" size="1600x900"}}

## The Link Card {#link-card}

The {{item link_card}} stores one end of a connection so you can carry it to the other end.

{{recipe link_card}}

The pattern is always the same: **sneak-use** the card on the first thing to store it, then **use** (without sneaking)
on the second to pair, link or assign. Blank cards stack; writing one takes a single card off the stack, and a written
card doesn't stack. Sneak-use a card in the air to clear it. Its tooltip says what it holds.

| Store it on (sneak-use) | Then use it on | Result |
|---|---|---|
| A Network Bridge | Another Network Bridge | The two are paired |
| A Point-to-Point Link | A Point-to-Point Link of the same type and opposite direction | They're paired ([below](#p2p)) |
| A Segment Isolator half | A switch's unit in an open rack | The rack links that segment ([Switches](page:networking/switches#link-segment)) |
| A Segment Isolator half, or any block of a network | A Router's unit in an open rack | The Router links that network ([Routers](page:networking/routers#linking)) |
| A Segment Isolator half, or any block of a network | A Trunk Port | It carries that segment ([Trunk Port](page:networking/switches#trunk-port)) |
| An Optical Multiplexer (block or rack unit) | Another Optical Multiplexer | They're paired ([Optical](page:networking/optical#multiplexer)) |
| An Optical Transport Shelf's unit | Another shelf's unit | They're paired ([Chassis](page:racks/chassis#transport-shelf)) |
| Any block of a network | An Optical Multiplexer | It gets a channel for that network |
| Any block of a network | A Transponder Card in a shelf (aim at it) | It carries that network |
| A Wireless Controller's unit (use, door open) | A Wireless Bridge, Wireless Port, or a Small Wireless Bridge on a machine | It becomes the controller's client ([Wireless](page:networking/wireless#clients)) |

To store a network, use the card on a cable or device of that network rather than its controller.

**Permissions:** writing a card needs **Build** on the network of the thing you click (**Rack access** for a device in a
Server Rack), and applying it needs the same at both ends. Denied, nothing is written or linked, and the action bar says
"Access denied: SYSNAME Firewall". See {{page racks/security#rack-security}}.

## Point-to-Point Link {#p2p}

A {{item point_to_point_link}} is a cable part that passes one thing straight from an input to one or more outputs,
without going through storage. It's covered in full on {{page networks/cable-parts}}; here is how pairing works.

{{recipe point_to_point_link}}

1. Mount the two ends. In each one's screen, pick the **type** (items, fluids, pressurized, energy, redstone or lanes) and
   set one end to **in** and the other to **out**. Both are locked once it's paired.
2. Sneak-use a Link Card on one end, then use it on the other.

They must be the same type, opposite directions, and in the same dimension. An output has one input; an input can have
many outputs, because the card keeps the input's address: use it on more outputs to add them. Every two seconds an end
drops partners that no longer point back. The **Unpair** button in its screen undoes it.

The **lanes** type is the one that joins networks: the outputs' cables get {{config reach.p2pLanes default}} lanes from
the input's network, split evenly between the outputs (at least 1 each). The others move items, fluids, energy or a
redstone signal while both ends are on the same running network. A Point-to-Point Link uses no lanes and drains
{{config reach.p2pDrain default}} FE/t.

## Relay Antenna {#relay-antenna}

A {{item relay_antenna}} gives wireless coverage to the [Handheld Terminals](page:terminals/handheld) linked to its
network.

{{recipe relay_antenna}}

- **Range:** a sphere of {{config reach.relayBaseRange default}} blocks around it, plus
  {{config reach.relayRangePerTransceiver default}} blocks for each {{item optical_transceiver}} in its four slots. It
  covers only its own dimension, and only while it's online.
- **Placing:** it faces you. Cables connect on every side except the top, where the mast is.
- **Cost:** one lane, and {{config reach.relayDrain default}} FE/t. The tip of its mast blinks while it's online.
- **Linking terminals:** use a Handheld Terminal on the antenna to link it to the antenna's network.
- **Screen:** right-click it to see its range, its four transceiver slots, and the players in range who carry a terminal
  linked to its network, with their distance.

For coverage without a range limit, use a [Wireless Controller](page:networking/wireless) instead.

{{screenshot file="networking/relay-antenna-screen.png" alt="The Relay Antenna screen with transceiver slots and players in range" show="The Relay Antenna screen with two Optical Transceivers in its slots, the range figure, and one or two players listed with distances" angle="GUI scale 3" size="1280x720"}}

## The Optical Transceiver {#optical-transceiver}

The {{item optical_transceiver}} is a component used in many recipes and in several devices' slots: it extends a Relay
Antenna's range, raises a Router's rate, gives a SAN its uplinks and fills a Chassis Switch Uplink Card's cages.

{{recipe optical_transceiver}}
