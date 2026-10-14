---
title: Switches and VLANs
section: networking
order: 10
summary: L2 Switches' rack lane pools, the L3 Switch's segment routes and QoS, and VLAN trunking with access and trunk ports, native segments and the Trunk Port.
covers: [encodedlogistics:trunk_port]
---
Switches are 1U [rack devices](page:racks/devices) that do two jobs. They save the network's lanes, by giving the other
devices in their rack lanes of their own. And they let a rack's devices work for more than one network segment, which is
what VLANs are about. The L3 Switch also moves items between those segments.

| Switch | Lane pool | Routes between segments |
|---|---|---|
| {{item l2_switch_24}} | {{config rack.l2Switch24Lanes default}} | No |
| {{item l2_switch_48}} | {{config rack.l2Switch48Lanes default}} | No |
| {{item l3_switch}} | {{config rack.l3SwitchLanes default}} | Yes |
| {{item chassis_switch}} | {{config optical.accessCardLanes}} per Access Card | Yes, with Routing Cards (see {{page racks/chassis#chassis-switch}}) |

{{recipe l2_switch_24}}

{{recipe l2_switch_48}}

{{recipe l3_switch}}

## Lane pools {#lane-pools}

Without a switch, every device in a rack takes its lanes from the network, one lane each for most devices. A busy rack
can use a lot of them (see {{page networks/lanes}}).

A switch changes that. Its pool gives lanes to the other devices in its rack, in unit order, until the pool is used up.
Those **pooled** devices cost the network nothing; the network only carries the switch's own one-lane **uplink**.
Devices beyond the pool take their lanes from the network as before. Two switches in a rack add their pools together.

- An L2 Switch drains {{config rack.switchDrain default}} FE/t, an L3 Switch {{config rack.l3SwitchDrain default}}.
- Switches have **High** lane priority by default, so the rack keeps their uplinks before anything else.
- A switch's popup shows its pool's use ("used / capacity"), how many devices it pools, and its uplink.

The switch panel lists the rack's devices: each one's unit, whether it's pooled, its lanes and the segment it serves.

## Segments and VLANs {#segments}

A [Segment Isolator](page:networks/segments) cuts a cable run into two separate networks, called **segments**. Normally
a rack's devices all work for the rack's own network. With a switch, any **pooled** device can be put on another
segment instead: a NAS on segment 2 is storage for that segment's network, not the rack's.

Each segment a rack knows has an ID. The rack's own network is **1**.

### Linking a segment to a rack {#link-segment}

1. Sneak-use a {{item link_card}} on the half of a Segment Isolator facing the segment you want. The action bar says
   "Segment on this side stored".
2. With the rack's front door open, use the card on a switch's unit. The action bar says "Segment linked to the rack".

The rack gives it the next free ID (2, 3, ...). A rack can link up to 5 segments of its own. Using the card on the switch
again unlinks the segment: devices on it go back to the rack's own network, and L3 routes through it are removed.

Linking needs **Rack access** at the rack and **Build** on the segment's network.

### Access and trunk ports {#ports}

Each pooled device is a **port** on the switch, and the panel's **Ports** view sets how it works:

| Port mode | Serves |
|---|---|
| **Access** (the default) | One segment: the one picked for it |
| **Trunk** | Its **native** segment, plus the **allowed** segments: all the segments the rack knows, or a list you pick |

- **Picking a device's segment:** in the device list, click the device's segment to step to the next segment the rack
  knows. For a trunk, this is its native segment.
- **Switching mode, allowed segments:** in the Ports view. These need **Build** on the rack's network, as trunk
  settings do. Adding to a trunk's list steps through the segments it doesn't carry yet; "all" goes back to carrying
  every segment.

A device on a segment works for that segment's network. If that network can't be reached (unloaded, or down), the device
has no network: it never falls back to another one. A device that's no longer pooled (the pool shrank) goes back to the
rack's own network.

On a [Chassis Switch](page:racks/chassis#chassis-switch), a port can be set to trunk mode only with a Trunk Card
installed.

### The Trunk Port {#trunk-port}

Linking segments rack by rack gets tedious when you have many racks. A {{item trunk_port}} carries segments into a whole
network instead: every Server Rack on that network can put its pooled devices on them, as if it had linked them itself.

{{recipe trunk_port}}

The Trunk Port is a [cable part](page:networks/cable-parts). Mount it on a cable of the network your racks are on.

- **Adding a segment:** store a segment (or a network) on a Link Card, then use the card on the Trunk Port without
  sneaking. Again takes it off. It carries up to 8 segments, given the IDs 10, 20, 30 and so on (up to 4094).
- **Modes**, in its panel: **Trunk** (the default) carries the allowed segments (all of them, or those you tick) plus
  the native segment; **Access** carries only the native segment.
- **The panel** has the mode, an allow toggle for each segment, the native segment, and unlink buttons. Opening it and
  changing it need **Build**.
- A Trunk Port only carries segments while it's online. If two Trunk Ports on a network carry the same ID, the first
  one found has it, and a rack's own linked segment wins over a Trunk Port's with the same ID.
- Racks list a Trunk Port's segments after their own, so their own segments keep their places in L3 routes.

The panel also shows the lanes that devices on its segments use, against {{config rack.trunkPortLanes default}}. That
figure is a guide only: each device's lanes are granted by its own rack, and nothing limits a Trunk Port's segments to it.

Segments on a trunk still can't see each other's storage. Only an L3 Switch or a Router moves items between them.

{{screenshot file="networking/switch-ports.png" alt="The L2 Switch panel's Ports view with access and trunk ports" show="The rack screen with an L2 Switch picked, Ports view: several pooled devices, one a trunk with a native segment and two allowed segments, the others access ports on different segments" angle="GUI scale 3" size="1280x720"}}

## The L3 Switch {#l3-switch}

The L3 Switch is an L2 Switch with a {{config rack.l3SwitchLanes default}}-lane pool that also routes items between
the segments its rack knows: the rack's own network, the segments it has linked, and those Trunk Ports carry to it.
Its panel has three tabs: **Devices**, **Routes** and **QoS**.

### Routes {#l3-routes}

An L3 Switch holds up to 8 routes. Each route goes from one segment to another (by ID, so segments coming and going don't
point it elsewhere) and is one of two kinds:

- **Move:** once a second it moves the items its filter passes from the source segment's storage into the
  destination's.
- **Share:** nothing moves. Instead, the source segment's items that the filter passes become part of the destination's
  storage, as its terminals and crafting see them, while the switch is online. A Share route's options:
  - **Read** (see and take out) or **Read/Write** (put in too, once the destination's own storage is full),
  - whether crafting may use the shared items,
  - one way, or both ways (bidirectional).

**Filters.** Each route has a 9-entry filter, as a port's is, with its deny, tag and component options. An empty allow
list passes nothing, so a new route moves nothing until you set it up; an empty deny list passes everything. Drag items
from JEI onto a route's filter box to set it.

**Rate.** The switch moves up to {{config rack.l3SwitchRate default}} items a second in all, shared between its active
Move routes in turn.

### QoS {#qos}

QoS rules decide which items a switch's Move routes deliver first when there's more to move than its rate allows. Up to
8 rules, each an item (or empty, for any item) and a level: **High**, **Normal** or **Low**.

- An item's level is the highest level of the rules it matches; items no rule matches are Normal.
- Each Move route moves its High items first, then Normal, then Low.

QoS only orders the items within this switch's own routes. It doesn't affect crafting jobs, ports, other devices or the
network's lanes, and Share routes have nothing to order.

### Example {#example}

A rack on your main network (segment 1) has an L3 Switch, and you've linked your farm's segment as segment 2. A Move
route from 2 to 1 with Wheat and Carrots in its filter brings the harvest home; a QoS rule marking Wheat High makes sure
the wheat comes first when the farm outpaces the switch. A Share route from 1 to 2, Read only, lets the farm's terminals
see your main storage's seeds without being able to put things in.

## Switches and the Terminal OS {#terminal-os}

Switches appear in Work with Devices as `SWITCH01`, `L3SWITCH01` and so on, and the Trunk Port as `TRK01`.
{{cmd RTVLANES}} reads a network's lane use.
