---
title: Terminals and access
section: terminals
order: 0
summary: The ways into a network's storage - terminals on cables, consoles in racks, a terminal you carry, and the Terminal Desk.
---
A network's storage is only useful if you can get at it. Encoded Logistics gives you four ways in, and they all show
the same thing: every item, fluid and gas the network holds (on hot storage and on tape), what it can craft, and the
energy on its Energy Storage Drives.

| Way in | What it is | Where it works | Page |
|---|---|---|---|
| {{item access_terminal}} and {{item fabrication_terminal}} | Cable parts with a storage grid (the Fabrication Terminal adds a 3x3 crafting grid) | Where you mount them | {{page terminals/block-terminals}} |
| {{item rack_console}}, {{item rack_fabrication_console}}, {{item rack_schematic_console}} | The same terminals in a 1U rack drawer | At a Server Rack | {{page terminals/rack-consoles}} |
| {{item handheld_terminal}} | An Access Terminal you carry, with its own battery | In range of a Relay Antenna, or anywhere through a Wireless Controller | {{page terminals/handheld}} |
| {{item terminal_desk}} | A green-screen terminal running the Terminal OS | At the desk | {{page terminals/terminal-desk}} |

The first three use the same terminal screen: a search field, a sortable grid, tabs for items, fluids, gases and
energy, and crafting requests. Learn it once on the {{page terminals/block-terminals}} page and it works the same
everywhere. The Terminal Desk is different: it runs the Terminal OS, a command-driven system you can program in ELCL
(see {{page elcl/index}}).

## Reading order

1. {{page terminals/block-terminals}}: the terminal screen itself, and the Access and Fabrication Terminals.
2. {{page terminals/rack-consoles}}: the rack versions, once you have a [Server Rack](page:racks/server-rack).
3. {{page terminals/handheld}}: taking the terminal with you.
4. {{page terminals/terminal-desk}}: the desk, its drawer and the Swivel Chair.

## Who may use a terminal

On a network with a [Firewall](page:racks/security), opening a terminal needs the **View** permission, putting items
in needs **Insert**, taking them out **Extract**, and asking for a craft **Craft**. A network without a Firewall lets
everyone do everything. The Schematic Terminal, which lists the network's schematics and crafting jobs, is covered with
crafting on the [jobs page](page:crafting/jobs).
