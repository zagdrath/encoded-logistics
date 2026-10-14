---
title: Progression
section: getting-started
order: 20
summary: The tech path read off the recipes, from vanilla materials to a full rack of servers, and what each new material unlocks.
---
Encoded Logistics has no research tree or unlock gates: what you can build depends only on what you can craft. The
recipes fall into tiers, set by the four dies the {{item lithography_press}} makes and the three metals you mine for
them. This page walks through those tiers in order and points to where each thing is documented.

| Tier | You need | The die it gives | What it opens up |
|---|---|---|---|
| 1. Vanilla | Iron, copper, redstone, sand, glass, string, slime, coal | (none yet) | Circuit Substrate, silicon, the Lithography Press |
| 2. Logic | The press and FE | {{item logic_die}}, {{item storage_die_8k}} | Your first network, the Midrange line, the Server Rack, signals and displays |
| 3. Memory | Neodymium and tantalum | {{item memory_die}} | Bigger drives, ports, the Capacitor Bank, schematics, Gateways |
| 4. Processor | Gallium | {{item processor_die}} | Fabricators, Schedulers, servers, fibre and wireless |
| 5. Autocrafting | All of the above | | Schematics, jobs, Schedulers |
| 6. The full rack | All of the above | | Firewalls, Routers, NAS and SAN, Tape Libraries, chassis |

## 1. Vanilla materials {#vanilla}

Everything starts from the Overworld's basics. {{item ferrite}} (iron and redstone), {{item copper_foil}},
{{item fiberglass}} and {{item solder_paste}} make the {{item circuit_substrate}} that nearly every device needs, and
sand goes through {{item silica}}, {{item silica_blend}} and the {{item silicon_boule}} to {{item silicon_wafer}}s.
{{page getting-started/first-network}} has the steps, and {{page crafting/materials}} the whole fabrication chain.

## 2. The press and the Logic Die {#logic}

The {{item lithography_press}} needs only iron, glass, Ferrite, redstone and a substrate, plus FE from another mod.
Its first two photomasks, the {{item logic_photomask}} and the {{item storage_photomask}}, need nothing more, so the
{{item logic_die}} and the {{item storage_die_8k}} are the first dies you make. See {{page crafting/lithography}}.

Logic Dies alone are enough for a lot:

- **A first network.** The {{item network_controller}} (it also takes a diamond), {{item power_inlet}},
  {{item network_cable}}, {{item drive_bay}}, {{item access_terminal}}, and 8K and 32K Storage Drives for items and
  fluids. {{page getting-started/first-network}} builds one.
- **Early autocrafting with the Midrange line.** The {{item midrange_system}}, {{item expansion_cabinet}},
  {{item keypunch}}, {{item card_reader}}, {{item line_printer}}, {{item disk_drive}} and {{item tape_drive}} need only
  Logic Dies, Ferrite and vanilla materials. You punch crafting recipes onto {{item punch_card}}s, read them onto an
  {{item diskette_8in}}, and the Midrange System crafts them itself, long before you have a Processor Die. A Midrange
  System is also its own network's controller, so it can stand in for a Network Controller. See
  {{page crafting/midrange}}.
- **The Server Rack and the Terminal Desk.** The {{item server_rack}} itself is iron and Ferrite, and the
  {{item terminal_desk}} (which runs the [Terminal OS](page:elcl/index)) is built round an Access Terminal. A few rack
  devices are Logic Die tier too: the {{item network_controller_2u}} and the {{item l2_switch_24}}.
- **Signals and displays.** The {{item control_interface}}, {{item plc}} and {{item display_panel}}. See
  {{page control/index}}.
- **Cable tools and simple parts.** {{item dense_network_cable}}, the {{item segment_isolator}}, the
  {{item cable_anchor}} and {{item cable_facade}}, the {{item threshold_sensor}}, the {{item collector_plane}} and
  {{item deployer_plane}}, and the Filter and Redstone Control Modules. See {{page networks/index}}.

## 3. Neodymium and tantalum: the Memory Die {#memory}

Next you mine. {{item neodymium_ore}} is common from y -32 up to y 48; {{item tantalum_ore}} lies deeper, below
y -8, so you'll mostly find it as {{item deepslate_tantalum_ore}}. Both need an iron pickaxe. Smelt the raw metals
into ingots. Heights and vein sizes are on {{page crafting/ores-and-metals}}.

- {{item tantalum_capacitor}}: a tantalum ingot, Copper Foil and Solder Paste make two.
- {{item doped_silicon}}: a Silicon Wafer and redstone. It's the wafer for the two higher dies.
- The {{item memory_photomask}} (Doped Silicon in the centre) lets the press etch the **{{item memory_die}}**.

{{recipe memory_die}}

What it opens up:

| Area | New things | Guide |
|---|---|---|
| Storage | 128K and 512K dies (neodymium), item drives from 128K up (tantalum), Pressurized and Energy Storage Drives, the {{item lto_tape_drive}} and LTO tape upgrades | {{page storage/drives}}, {{page storage/tapes}} |
| Power | The {{item capacitor_bank}} | {{page networks/power}} |
| Parts | The {{item ingress_port}} and {{item egress_port}}, the {{item inventory_tap}}, the Throughput and Fuzzy Match Modules, the {{item trunk_port}} | {{page networks/cable-parts}} |
| Crafting | The {{item fabrication_terminal}}, {{item schematic_card}}, {{item schematic_terminal}}, {{item gateway}} and {{item job_buffer}} | {{page crafting/index}} |
| Racks | The {{item nas}}, {{item memory_server}} and {{item ups}} | {{page racks/devices}} |
| Midrange | The {{item integrated_midrange}} | {{page crafting/midrange}} |

## 4. Gallium: the Processor Die {#processor}

Gallium is the deepest and rarest: {{item deepslate_gallium_ore}} only, below y -32. A gallium ingot in the
{{item processor_photomask}}, and another as the press's additive with Doped Silicon, make the
**{{item processor_die}}**. Many of the same recipes also want a {{item heatsink}} (copper and Ferrite).

{{recipe processor_die}}

Gallium also goes straight into {{item fiber_cable}}, the {{item optical_transceiver}} and the {{item radio_module}},
so it opens up long-distance and wireless networking as well as the top tier of devices:

| Area | New things | Guide |
|---|---|---|
| Autocrafting | The {{item fabricator}}, {{item schematic_encoder}}, {{item scheduler_core}} and {{item thread_unit}} | {{page crafting/index}} |
| Storage | The 2M die and drives, LTO-10, the {{item san}} and Tape Libraries | {{page storage/index}} |
| Racks | The {{item firewall}}, {{item router}}, {{item compute_server}}, {{item l3_switch}}, the 4U Network Controller and the chassis | {{page racks/devices}} |
| Networking | {{item fiber_cable}}, the {{item network_bridge}}, {{item point_to_point_link}}, {{item relay_antenna}}, Access Points and the Wireless Controller | {{page networking/index}} |
| Terminals | The {{item handheld_terminal}} | {{page terminals/handheld}} |

## 5. Autocrafting {#autocrafting}

With Processor Dies you can build the full autocrafting chain:

1. A {{item schematic_encoder}} writes recipes onto Schematic Cards: crafting recipes, and processing recipes for
   machines, with fluids and gases. See {{page crafting/schematics}}.
2. **Fabricators** craft the crafting schematics, and **Gateways** next to machines run the processing ones. See
   {{page crafting/fabricators-and-gateways}}.
3. A **Scheduler** (a Scheduler Core with Job Buffers and Thread Units) plans and runs jobs, or Compute and Memory
   Servers do it in a rack. See {{page crafting/schedulers}}.
4. You ask for an item from any terminal and follow the job on Job Status. See {{page crafting/jobs}}.

If you've been crafting on a Midrange System, its diskettes' recipes keep working alongside: they're network recipes
like any other.

## 6. A full rack setup {#racks}

The last step is to move the network's core into {{item server_rack}}s: a redundant pair of rack Network Controllers,
a Firewall guarding who may do what, UPSes riding out power cuts, switches pooling lanes, servers running the jobs,
NAS and SAN storage, and Tape Libraries archiving what you rarely use. Routers, Exchange Switches and optical lines
then join separate networks together. Start at {{page racks/index}} and {{page networking/index}}, and use the
[Terminal OS](page:elcl/index) to watch and script all of it.
