---
title: Modular chassis
nav: Chassis
section: racks
order: 30
summary: The Chassis Switch, Blade Enclosure and Optical Transport Shelf, their bays, and all 18 cards, blades and modules that go in them.
---
Three rack devices are **modular chassis**: big enclosures you build up from parts. Each has bays, and each bay takes one
kind of part. The chassis works with whatever is fitted, and you can add or pull parts while it runs: taking one out
affects only what that part gave.

| Chassis | Height | Bays |
|---|---|---|
| {{item chassis_switch}} | 10U | 2 supervisor bays (A, B), 8 line card bays (L1-L8), 4 power bays (P1-P4), plus 4 transceiver cages behind each Uplink Card |
| {{item blade_enclosure}} | 10U | 16 blade bays in front (F1-F16); at the back 2 interconnect bays (R1, R2), 2 management bays (M1, M2), 6 power bays (P1-P6); plus 2 drive bays behind each blade bay and 9 schematic slots |
| {{item optical_transport_shelf}} | 6U | 2 controller bays (C1, C2) and 8 line card bays (L1-L8) |

{{recipe chassis_switch}}

{{recipe blade_enclosure}}

{{recipe optical_transport_shelf}}

## Working with parts {#parts}

{{table chassis-parts}}

- **Putting a part in:** with the rack's front door open, use the part on its bay. Or open the chassis in the
  [rack screen](page:racks/server-rack#screen) and click a bay while holding the part, or shift-click the part in your
  inventory to put it in the first bay that takes it.
- **Taking a part out:** sneak-click its bay in the chassis panel.
- **Settings:** some parts have settings (a Transponder Card's channel, a Protection Card's revertive switch). Click
  the part in the panel to change them. A part keeps its settings on its item when you take it out.
- **Looking at it:** each part's faceplate is drawn in its bay, lit while it works. Look at one through the open door
  and the popup shows that part: its status and what it gives (or why it's down).
- **Status:** each bay has a status word that the Terminal OS's {{cmd WRKHW}} screen and {{cmd RTVCHSSTS}} report:
  `*ONLINE`, `*ACTIVE`, `*STANDBY`, `*FAILED`, `*EMPTY`, `*OFFLINE` or `*PROTECT`.
- **Permissions:** putting parts in and taking them out needs **Rack access**; changing a part's settings needs
  **Build**; looking needs **View**.
- **Power:** a chassis drains {{config optical.chassisDrain default}} FE/t, plus {{config optical.chassisCardDrain default}}
  FE/t for each part fitted.

The panel's first tab, **Slots**, draws the chassis's face at full size; you click bays there. Each chassis adds tabs of
its own, below.

{{screenshot file="racks/chassis-panel.png" alt="The Chassis Switch settings panel showing its card bays" show="The rack screen with a Chassis Switch picked: the Slots tab showing two supervisors, a mix of Access, Uplink, Routing and Trunk cards, and four power supplies" angle="GUI scale 3" size="1280x720"}}

## Chassis Switch {#chassis-switch}

The Chassis Switch is an [L3 Switch](page:networking/switches#l3-switch) built from cards, for racks that need more
lanes, more routing throughput, or redundancy.

- **It runs** with at least one Supervisor Card and one Chassis Power Supply. It is **degraded** (amber, "Degraded")
  with fewer than two of either.
- **Supervisors** (bays A and B): the active one runs the switch. If it's taken out, the standby takes over in the same
  tick, traffic and all, and the Terminal OS gets a `*SUPFAILOVER` event. With no supervisor left it gets `*CARDFAULT`.
- **Lanes:** each Access Card adds {{config optical.accessCardLanes default}} lanes to the rack's pool, like an
  [L2 Switch](page:networking/switches). The pool only works while the switch runs and at least one Uplink Card cage
  holds an Optical Transceiver; with none, the pool is 0.
- **Uplinks:** each Uplink Card has four Optical Transceiver cages (the **Cages** tab). They are the pool's uplinks,
  aggregated: any one keeps the pool up.
- **Routing:** with at least one Routing Card the switch has the L3 Switch's routes and QoS (the **Switch** tab, the
  same as an L3 Switch's panel). Its rate is the number of Routing Cards times {{config optical.routingCardRate default}}
  items a second.
- **VLAN trunks:** a port can be set to trunk mode only with a Trunk Card installed. Without one the switch says so
  and refuses.
- Taking out a power supply also sends `*CARDFAULT`.

## Blade Enclosure {#blade-enclosure}

The Blade Enclosure packs compute, memory, fabrication and storage into one 10U chassis.

- **It runs** with at least one Interconnect Module, one Management Module and one Blade Power Supply. It is degraded
  with fewer than two interconnects or two power supplies.
- **Lanes:** the blades take none of the rack's lanes. The Interconnect Module gives them
  {{config optical.interconnectLanes default}}; a second one is a spare. The Management Module's second is a standby.
- **Compute Blades** give the network's rack Scheduler threads, as a Compute Server does but packed denser: each blade
  is worth half a Compute Server's {{config rack.computeServerThreads}} threads for its 10/16 U of height, plus
  {{config optical.bladeBonusPercent default}} % more. The blades' threads are added up and rounded up; at the
  defaults each blade is worth 0.78 threads, so eight blades give 7.
- **Memory Blades** give job memory the same way: {{config rack.memoryServerMemory}} times 10/16 times the bonus, 25,600
  each at the defaults.
- **Fabrication Blades** craft from the enclosure's nine schematic slots (the **Fab** tab) as a Fabrication Server
  does, {{config autocrafting.fabricatorEnergyPerCraft}} FE a craft. More blades craft faster: a second blade counts as
  one Throughput Module, a third as two.
- **Storage Blades** each open the two drive bays behind them (the **Drives** tab) as network storage, with a priority
  and access mode like a NAS.

The panel's tabs: **Front**, **Rear**, **Drives** and **Fab**. The scheduler pooling is explained on
{{page crafting/schedulers}}.

## Optical Transport Shelf {#transport-shelf}

The Optical Transport Shelf carries several networks between two shelves in different racks, over long Fiber Cable
runs, with protection against a cut fibre. Pair two shelves with a Link Card (sneak-use it on one shelf's unit, then use
it on the other's); they must be in the same dimension and in different racks.

- **It runs** with a Controller Card. The second Controller Card takes over if the first is taken out.
- **Transponder Cards** each carry one client network onto one long-haul channel, 1 to 40 (the card's setting). Give a
  transponder its network with a Link Card: store the network by sneak-using the card on a cable or device of that
  network, then use it on the transponder's bay. Channel *n* at one shelf joins its network with channel *n*'s at the
  other, carrying up to a Fiber Cable's lanes less the line's span loss.
- **Mux Cards:** without one the line carries a single channel. Each Mux Card lets it carry 8, up to
  {{config optical.shelfChannels default}} in all. In practice the eight line bays are the limit: every channel needs
  its own Transponder Card, and the Mux, Amplifier and Protection Cards share the same bays.
- **Amplifier Cards** launch the line amplified: a shelf end with one (one counts per end) adds
  {{config optical.fiberSpanThreshold}} blocks of reach before the span starts losing lanes.
- **Protection Cards** give 1+1 protection: a working path (the shortest fibre route) and a protect path, which leaves
  the rack from a different connection point by a different fibre (a Network Bridge pair on the way also counts). When
  the working path fails the shelf switches to protect within a tick (`*PROTSWITCH`). With the card's **revertive**
  setting on (the default), it switches back once the working path is restored. {{cmd CHGPROT}} switches by hand, for
  testing. Without a Protection Card the shelf uses the working path only.
- **Performance data** for each path: its length, loss, lanes delivered, signal quality (100 % less 4 % for each lane
  lost, 0 when down) and protection state, in the **Spans** tab and the Terminal OS's {{cmd DSPOPT}} and
  {{cmd RTVOPTSTS}}.
- It shows degraded while it's short of redundancy.

The panel's tabs: **Slots**, **Channels** and **Spans**. The optical model (span loss, amplifiers, lines) is explained
on {{page networking/optical}}.

## The Terminal OS {#terminal-os}

Work with Hardware ({{cmd WRKHW}}) lists every chassis and bay on the network with its status. Display Optical
({{cmd DSPOPT}}) shows the shelves' and multiplexers' spans and channels. Programs can read them with
{{cmd RTVCHSSTS}} and {{cmd RTVOPTSTS}}, and react to `*CARDFAULT`, `*SUPFAILOVER`, `*PROTSWITCH` and `*SPANLOSS`
trigger events (see {{page elcl/jobs-and-scheduling}}).

{{layout 28_wrkhw}}
