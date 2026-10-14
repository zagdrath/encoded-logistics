---
title: Wireless
section: networking
order: 60
summary: The Wireless Controller and its Access Points, Wireless Bridges, Wireless Ingress and Egress Ports, the Radio Module, and the cross-dimension options.
---
Wireless lets you put parts of a network where no cable goes: a Wireless Bridge brings a whole remote area onto your
network, and a Wireless Port works an inventory with no cable at all. Range is unlimited within a dimension, and other
dimensions can be reached too.

There are two separate wireless systems in the mod, and it's worth knowing which is which:

| System | Serves | Range |
|---|---|---|
| [Relay Antennas](page:networking/bridges-and-links#relay-antenna) | Handheld Terminals only | A sphere around each antenna, same dimension |
| The **Wireless Controller** with **Access Points** (this page) | Wireless Bridges, Wireless Ports and Small Wireless Bridges; also Handheld Terminals | Unlimited in the dimension; other dimensions while {{config rack.wirelessCrossDimension default}} |

## The parts {#parts}

| Part | What it is |
|---|---|
| {{item wireless_controller}} | A 1U rack device: the network's wireless brain. Clients link to it |
| {{item access_point}} | A radio on the network. Each one online gives the controller room for {{config rack.wirelessApClients default}} clients |
| {{item wireless_bridge}} | A client: whatever is cabled to it joins the controller's network |
| {{item wireless_ingress_port}}, {{item wireless_egress_port}} | Clients: an Ingress or Egress Port with no cable |
| {{item radio_module}} | A crafting component for all of the above |

{{recipe radio_module}}

A typical setup: a Wireless Controller in a rack on your main network, two or three Access Points cabled to that network,
and Wireless Bridges and Ports out in the world, each linked to the controller with a Link Card.

## Wireless Controller {#controller}

{{recipe wireless_controller}}

The Wireless Controller is a 1U device for a [Server Rack](page:racks/server-rack). It drains
{{config rack.wirelessControllerDrain default}} FE/t and uses one lane.

- **Clients** (Wireless Bridges, Wireless Ports, Small Wireless Bridges) link to it with a Link Card, below.
- **Handheld Terminals** link to it by being used on its unit or anywhere on its rack; they then work anywhere. See
  {{page terminals/handheld}}.
- **Admission:** the controller admits clients up to the slots its network's online Access Points give
  ({{config rack.wirelessApClients}} each), oldest link first. Clients past the slots wait, "Over capacity", until slots
  free up.
- **Lanes:** each admitted client is a remote link from the controller's rack: a Wireless Bridge carries
  {{config rack.wirelessBridgeLanes default}} lanes, a Wireless Port or Small Wireless Bridge one.
- **Fault:** with clients linked and no Access Point online, it faults: "No access points".
- **Popup:** Access Points online, clients admitted against slots, and how many are linked.

**Its panel** has two tabs:

- **APs:** each Access Point with its online dot and clients ("6 / 8 clients", "8 / 8 - full", or "no uplink").
- **Linked:** its bridges, ports and machines, then the players whose Handheld Terminals it serves, each with an **X** to
  unlink it. Unlinking a client here cuts it off.

The controller keeps its list of linked clients and players with it when you take it out of the rack.

While {{config rack.wirelessAnyController default}}, a Handheld Terminal linked through one controller can use any
online controller on the network. Clients always belong to the controller they were linked to.

{{screenshot file="networking/wireless-controller-panel.png" alt="The Wireless Controller panel's APs tab" show="The rack screen with a Wireless Controller picked, APs tab: three Access Points, two online with client counts, one with no uplink" angle="GUI scale 3" size="1280x720"}}

## Access Points {#access-point}

{{recipe access_point}}

An Access Point is a disc you mount against a block face, usually the side of a cable. It faces away from the face you
click and connects **only on that one side**: it's a device at the end of a cable, not something the network passes
through.

- It uses one lane and drains {{config rack.accessPointDrain default}} FE/t.
- Each one online gives its network's Wireless Controller {{config rack.wirelessApClients default}} client slots. Admitted
  clients are shared out among the online Access Points in turn.
- **States:** off (no uplink: not on a running network), **linking** (on a network with no Wireless Controller: the ring
  blinks yellow), **online** (light blue).
- **Popup:** its clients (n / 8) and its uplink. Its device name is `AP01` and so on.

Access Points don't need to be anywhere near the clients: range is unlimited. They only set how many clients the
controller can take. Handheld Terminals don't need them at all.

## Linking clients {#clients}

1. Open the controller's rack front door and use a {{item link_card}} on the Wireless Controller's unit. The card takes
   the controller.
2. Use the card on a Wireless Bridge, a Wireless Port, or a [Small Wireless Bridge](page:integrations/arcforge) on a
   machine. It becomes the controller's client. A card from another controller moves it to that one.

Linking a client needs **Build** on the controller's network. A client whose controller no longer lists it (unlinked in
the panel, say) unlinks itself.

A client's status shows what's wrong, if anything:

| Status | Means |
|---|---|
| Not linked, No controller | Waiting: not linked yet, or its controller can't be found (shown as linking) |
| No access points | Fault: its controller's network has no Access Point online |
| Over capacity | Fault: the controller has more clients than its Access Points' slots |

Clients in another dimension from the controller are only admitted while {{config rack.wirelessCrossDimension}} is on.

## Wireless Bridge {#wireless-bridge}

{{recipe wireless_bridge}}

Once linked, whatever is cabled to a Wireless Bridge (on its sides and bottom; its mast is on top) is on the
controller's network, as if a {{config rack.wirelessBridgeLanes}}-lane cable ran from the controller's rack to it. That
makes it a wireless Network Bridge with no partner to place: put it at the outpost, cable the outpost to it, link it.

- It uses no lanes itself and counts as **one client**.
- It drains a Network Bridge's {{config reach.bridgeDrain}} FE/t times {{config rack.wirelessEnergyMultiplier default}}.
- **States:** off, linking, online, **active** (lanes crossing: its window pulses), fault.
- It has no screen. Its popup shows its controller, the devices behind it, and its lanes. Its device name is
  `WBRIDGE01` and so on.

Don't put a second controller structure behind a Wireless Bridge: it joins the controller's network, and a network has
one controller structure.

{{screenshot file="networking/wireless-bridge.png" alt="A Wireless Bridge at an outpost with cables running into it" show="A Wireless Bridge on the ground at a small outpost, active (window lit), a cable into its side running to a Drive Bay and an Access Terminal; the popup open showing its controller and lanes" angle="Three blocks away, crosshair on the bridge" size="1600x900"}}

## Wireless Ingress and Egress Ports {#wireless-ports}

{{recipe wireless_ingress_port}}

{{recipe wireless_egress_port}}

A Wireless Port is an [Ingress or Egress Port](page:networks/cable-parts) without a cable: a small panel you place against
the inventory it works (it faces into it). It pulls items, fluids, gases or energy from that inventory into its
controller's network (Ingress) or pushes them out of the network into it (Egress), with the same filter, modules,
redstone modes and screen as a cabled port.

- Its screen's title is the port's name, and a link light and signal bars sit under the redstone button.
- It connects to no cable. It takes one lane over its link to the controller's rack and counts as **one client**.
- It drains {{config storage.partDrain}} FE/t times {{config rack.wirelessEnergyMultiplier}}.
- **States:** off, linking, online, fault. Device names `WINGRESS01` and `WEGRESS01`.

Handy for a single chest or machine far from any cable: one port, linked, and it's on the network.

## Across dimensions {#cross-dimension}

| Option | Default | Controls |
|---|---|---|
| {{config rack.wirelessCrossDimension}} | {{default rack.wirelessCrossDimension}} | Wireless clients, and Handheld Terminals linked to a controller, in another dimension from the Wireless Controller |
| {{config rack.wirelessAnyController}} | {{default rack.wirelessAnyController}} | A Handheld Terminal using any online controller on its network, not just the one it was linked to |
| {{config reach.bridgeCrossDimension}} | {{default reach.bridgeCrossDimension}} | Pairing Network Bridges across dimensions (see {{page networking/bridges-and-links}}) |

Relay Antennas always cover only their own dimension.

## In the Terminal OS {#terminal-os}

Access Points, Wireless Bridges and Wireless Ports appear in Work with Devices with their device names, and the Wireless
Controller as `WLC01`. Like any device they can be renamed with {{cmd RNMDEV}}.
