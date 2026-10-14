---
title: Encoded Logistics
section: home
summary: Storage networks and automation with server racks, autocrafting jobs and a green-screen terminal operating system. For Minecraft 26.3 on NeoForge.
toc: false
---
Encoded Logistics builds your base's storage into a network you run like a data centre. Cables carry **lanes** from a
{{item network_controller}} to drives, terminals, ports and server racks. Autocrafting runs as **jobs** on Fabricators,
Gateways and Schedulers. A {{item terminal_desk}} gives you a whole green-screen operating system, the Terminal OS,
with its own command language (ELCL) for programs, scheduled jobs and triggers.

This site documents version {{version}} of the mod.

## Requirements

| Needs | Version |
|---|---|
| Minecraft | 26.3 |
| NeoForge | 26.3.0.0-beta or later |
| Optional | [JEI](page:integrations/jei), [Jade](page:integrations/jade), and [Arcforge](page:integrations/arcforge) 2.5.0 or later |

None of the optional mods is needed to play. JEI adds recipe lookups and search syncing, Jade adds tooltips (lanes,
status), and Arcforge's machines can join a network through the {{item small_wireless_bridge}}.

The mod has no generator of its own. Its machines and networks run on FE (Forge Energy), so you'll need another mod
that makes FE.

## Where to start

1. [Your first network](page:getting-started/first-network) takes you from vanilla materials to a working storage
   network with a terminal, step by step.
2. [Progression](page:getting-started/progression) maps the rest of the tech path, from the Lithography Press to a full
   rack of servers.
3. [What a network is](page:networks/index) explains the ideas everything else rests on: controllers, cables, lanes
   and power.

## The sections

| Section | What's in it |
|---|---|
| {{page getting-started/index}} | Your first network, then the tech path in order. |
| {{page networks/index}} | Network Controllers, cables and their colours, lanes, power, segments, and the parts that mount on cables. |
| {{page storage/index}} | Storage Drives and the Drive Bay, hot and cold storage, NAS and SAN, LTO tapes and Tape Libraries. |
| {{page crafting/index}} | Ores, the fabrication materials, the Lithography Press, schematics, Fabricators, Gateways, Schedulers, jobs, and the Midrange line. |
| {{page terminals/index}} | The Access and Fabrication Terminals, rack consoles, the Handheld Terminal and the Terminal Desk. |
| {{page racks/index}} | The Server Rack, every rack device and chassis part, the Firewall, and Rack Elevations. |
| {{page networking/index}} | Switches and VLANs, Routers, the Internet Exchange and Directory Servers, bridges, optical lines and wireless. |
| {{page control/index}} | The Control Interface, the PLC, Display Panels, Cage Lights and Alarm Strobes, and Speakers. |
| {{page elcl/index}} | The Terminal OS and its command language: screens, the editor, database files, jobs and scheduling, and every command and message. |
| {{page integrations/index}} | What changes with Arcforge, JEI and Jade installed. |
| {{page admin/index}} | For server admins: the config files, permissions, the world folders, performance limits and the server command. |
| {{page developers/index}} | For add-on authors: the extension points, and how this site is built. |
| {{page reference/index}} | Generated reference: every block and item, every recipe, tags, config options, device types, sounds and data components. |
| {{page changelog}} | What changed in each version. |
