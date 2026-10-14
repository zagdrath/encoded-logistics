---
title: Server racks
section: racks
order: 0
summary: The Server Rack and the rack devices that go in it - what a rack is for, and where to read about each part of it.
---
Most of the network's advanced hardware doesn't go in the world as blocks: it goes in a **Server Rack**. A rack is a
42U enclosure two blocks deep and three tall. You fill it with **rack devices** - switches, routers, servers, storage
arrays, tape libraries, power and security - each taking 1 to 10 units of height. The rack is one device on the
network, and the devices in it share its connection.

{{screenshot file="racks/rack-row.png" alt="A row of Server Racks with their front doors open and devices installed" show="Three or four Server Racks side by side, front doors open, filled with a mix of devices (switches near the top, servers, a NAS and SAN, a tape library, a UPS at the bottom); lights on" angle="Standing a few blocks back from the row, slightly above, so the fronts of all racks are visible" size="1600x900"}}

## What goes where

| Page | What it covers |
|---|---|
| {{page racks/server-rack}} | The rack itself: placing it, doors, connecting it to the network, power, the popup, the rack screen, breaking it safely |
| {{page racks/devices}} | Every rack device, what it does, its panel and its config options |
| {{page racks/chassis}} | The three modular chassis (Chassis Switch, Blade Enclosure, Optical Transport Shelf) and their 18 cards, blades and modules |
| {{page racks/security}} | The Firewall: who may use the network, its six permissions, and what each one guards |
| {{page racks/rack-elevations}} | The Rack Elevation: writing a plan for a rack and building it in one go |

Read {{page racks/server-rack}} first, then pick devices from {{page racks/devices}}. If anyone else plays on your
server, put a [Firewall](page:racks/security) in your first rack.

## Related pages

- Networking devices in racks (switches, routers, wireless and optical gear) are explained in depth in the
  {{page networking/index}} section.
- The rack Network Controllers are covered with the block controller on {{page networks/controllers}}.
- The NAS, SAN and Tape Libraries are storage: see {{page storage/nas-san}} and {{page storage/tapes}}.
- The Compute, Memory and Fabrication Servers are crafting hardware: see {{page crafting/schedulers}} and
  {{page crafting/fabricators-and-gateways}}.
- Rack Consoles are terminals: see {{page terminals/rack-consoles}}.
