# Documentation inventory

What the documentation site covers, the page that covers each feature, and what was left out because the code doesn't
implement it. It was written from a review of the code at `24c1f57` (mod version 1.2.1, Minecraft 26.3). The detailed
review notes, with file references for every claim, are in [review-notes/](review-notes/):

| Notes | Area |
|---|---|
| [networks.md](review-notes/networks.md) | Controllers, cables, parts, power, lanes, segments, bridges, optical, wireless |
| [storage-crafting.md](review-notes/storage-crafting.md) | Ores, materials, Lithography Press, drives, schematics, autocrafting, the Midrange line, data components |
| [racks-terminals.md](review-notes/racks-terminals.md) | Server Rack, every rack device and chassis part, terminals, the desk, menus, sounds, device names |
| [control-admin-compat.md](review-notes/control-admin-compat.md) | Control Interface, PLC, Display Panels, signals, every config option, Firewall, folders, Arcforge/JEI/Jade, tests |
| [elcl.md](review-notes/elcl.md) | Terminal OS and ELCL: commands, messages, screens, system values, the old page's anchors, the spec-vs-code audit |

Paths below are page paths in the built site (`docs/site/`). "(gen)" marks a page, or part of one, generated from the
exported data.

## Counts (from the code)

| Thing | Count | Source |
|---|---|---|
| Blocks | 106 (104 with a block item; `part_host` has no item, and the Small Wireless Bridge's item isn't a block item) | `ModBlocks` |
| Items | 247 (104 block items and 143 others) | `ModItems`, `docs/site-src/data/registry.json` |
| Recipes | 286 files: shapeless 130, shaped 114, smelting 10, blasting 10, `arcforge:crushing` 9 (Arcforge only), `encodedlogistics:lithography` 4, `encodedlogistics:tape_upgrade` 4, stonecutting 2, `crafting_dye` 1, `rack_elevation_copy` 1, `rack_elevation_clear` 1 | `data/encodedlogistics/recipe` |
| Recipe types | 1 custom (`lithography`); 3 custom special serializers (`tape_upgrade`, `rack_elevation_copy`, `rack_elevation_clear`) | `ModRecipeTypes`, `ModRecipeSerializers` |
| Block entity types | 33 | `ModBlockEntityTypes` |
| Menu types | 34 | `ModMenuTypes` |
| Data components | 19 | `ModDataComponents` |
| Entity types | 1 (`seat`, internal) | `ModEntityTypes` |
| Creative tabs | 4 | `ModCreativeTabs` |
| Sound events | 25 | `ModSounds`, `sounds.json` |
| Config options | 212 local (13 sections) + 16 client (3 sections) | `Config.SPEC`, `TerminalSettings.SPEC` |
| ELCL commands | 167 | `CommandRegistry.all()` |
| ELCL messages | 182 | `ElclMessages.all()` |
| Trigger events | 24 | `BuiltinCommands`, `Triggers` |
| System values | 7 | `StoredSysvalService` |
| Terminal OS screens | 44 terminal screens + 10 machine and PLC screens | `client/crt` |
| Device name prefixes | about 55 | `ElclDevices`, `NamedDevice` `TYPE`s, `MachineBridge.typeCode` |
| Rack device types | 26 | `RackDeviceType` |
| Chassis parts | 18 | `ChassisPart` |
| Brigadier commands | 1 (`/encodedlogistics caches`) | `RegisterCommandsEvent` |

## Site map and coverage

Every registered block and item also gets a generated entry page under `reference/items/` (icon, description and
tooltip, recipes, "made from" and "used in", tags, the device type it becomes on a network, related guide pages). The
guide pages below are the hand-written ones each entry links back to.

### Home: `index.html`

What the mod is, the requirements (Minecraft 26.3, NeoForge 26.3.0.0-beta or later; JEI, Jade and Arcforge 2.5.0+
optional), where to start, and a map of the sections.

### Getting started: `getting-started/`

| Page | Covers |
|---|---|
| `first-network.html` | Bringing FE in from another mod (the mod has no generator), the first Silicon Wafers and Circuit Substrate, the Lithography Press, the Logic Die and 8K Storage Die, then Network Controller + Power Inlet + Network Cable + Drive Bay + Storage Drive + Access Terminal |
| `progression.html` | The tech path from the recipes: vanilla materials → press → first network (and the Midrange line as early autocrafting) → neodymium and tantalum (Memory Die, 128K/512K, ports, Gateway, Job Buffer) → gallium (Processor Die, Fabricator, Scheduler, Schematic Encoder, 2M, servers) → full rack setup |

### Networks: `networks/`

| Page | Covers (registry ids) |
|---|---|
| `index.html` | What a network is; devices vs pass-through blocks; online/offline; Jade's lane readout |
| `controllers.html` | `network_controller` structures (size up to `controller.maxSize`, energy per block, lanes), the controller screen, the rack `network_controller_2u` / `network_controller_4u` (pairs, failover, uplinks), the Midrange System as a controller; conflicts between controllers |
| `cables.html` | `network_cable`, `dense_network_cable`, `fiber_cable` in neutral + 12 colours each (39 blocks; colour rules, the `crafting_dye` recipe), `cable_anchor`, `cable_facade` (ghost preview), the internal `part_host` |
| `lanes.html` | Lane sources and budgets, nearest-first allocation, per-cable lane caps, lane costs per device |
| `power.html` | `power_inlet`, `capacitor_bank`, `creative_capacitor_bank`; the FE pool and its fill/drain order (drives → controllers → banks; drains banks and drives first); drains |
| `segments.html` | `segment_isolator`; segment IDs (VLAN trunking itself is on the switches page) |
| `cable-parts.html` | Mounting parts on cables and block faces; `ingress_port`, `egress_port`, `inventory_tap`, `threshold_sensor`, `collector_plane`, `deployer_plane`, `point_to_point_link`, `trunk_port`; port modules `filter_module`, `throughput_module`, `fuzzy_match_module`, `redstone_control_module` |

### Storage: `storage/`

| Page | Covers |
|---|---|
| `drives.html` | `storage_die_{8k,32k,128k,512k,2m}`, item/fluid/pressurized/energy drives in each tier (20 items), the byte model, the type limit (`storage.driveTypeLimit`), fill lights, copies, energy drives as battery items, gases only with Arcforge |
| `drive-bay.html` | `drive_bay` |
| `hot-and-cold.html` | Hot vs cold storage, priorities, access modes, archive and recall |
| `nas-san.html` | NAS and SAN rack devices (SAN uplink cages, priority bonus per transceiver) |
| `tapes.html` | `lto_tape_{6..10}`, `lto_tape_drive`, `tape_library_4u` / `tape_library_6u`, the tiering policy (age, trigger %, keep hot, pinned), `tape_upgrade` recipes, CHGITMTIER |
| `midrange-storage.html` | `disk_drive`, `tape_drive`, `tape_reel` |

### Crafting and automation: `crafting/`

| Page | Covers |
|---|---|
| `ores-and-metals.html` | `neodymium_ore`, `deepslate_neodymium_ore`, `tantalum_ore`, `deepslate_tantalum_ore`, `deepslate_gallium_ore` (heights, vein sizes, Overworld only), raw metals, ingots, dusts (Arcforge only), `neodymium_block`, `tantalum_block`, `gallium_block`, `ferrite_block` |
| `materials.html` | The fab chain: `silica`, `silica_blend`, `silicon_boule`, `silicon_wafer`, `ferrite`, `copper_foil`, `fiberglass`, `solder_paste`, `circuit_substrate`, `doped_silicon`, `tantalum_capacitor`, `heatsink`, `optical_transceiver`, `radio_module` |
| `lithography.html` | `lithography_press`; photomasks `logic_photomask`, `storage_photomask`, `memory_photomask`, `processor_photomask` (never used up); dies `logic_die`, `memory_die`, `processor_die`; the `lithography` recipe type (gen recipes) |
| `schematics.html` | `schematic_card`, `encoded_schematic_crafting`, `encoded_schematic_processing`, the `schematic_encoder` part (crafting and processing modes, fluids and gases), `resource_entry` (internal stand-in for fluids and gases) |
| `fabricators-and-gateways.html` | `fabricator`, `gateway`, the Fabrication Server and Fabrication Blade |
| `schedulers.html` | The Scheduler multiblock (`scheduler_core`, `job_buffer`, `thread_unit`: forming rules, threads, memory), the rack Scheduler (Compute and Memory Servers, Compute and Memory Blades, pooled across racks), least-busy spreading of scheduled runs |
| `jobs.html` | Requesting crafts from any terminal, the plan screen, Job Status, the `schematic_terminal` part and Rack Schematic Console, crafting history, job toasts (client config) |
| `midrange.html` | The Midrange line as early autocrafting: `midrange_system`, `expansion_cabinet`, `integrated_midrange`, `keypunch` (Card Punch), `card_reader`, `line_printer`, `punch_card`, `diskette_8in`, `diskette_magazine`, `printout`; their green screens (MRCTL, IMCTL, CARDPUNCH, CARDRDR, PRINTER) |

### Terminals and access: `terminals/`

| Page | Covers |
|---|---|
| `block-terminals.html` | `access_terminal`, `fabrication_terminal` (cable parts): search, sorting, the item/fluid/pressurized/energy tabs, JEI search sync, crafting requests |
| `rack-consoles.html` | `rack_console`, `rack_fabrication_console`, `rack_schematic_console` (drawer and lid, migration from the old Memory Die upgrade) |
| `handheld.html` | `handheld_terminal`: linking (Relay Antenna or Wireless Controller), range, battery, signal |
| `terminal-desk.html` | `terminal_desk`, `swivel_chair` (and the internal `seat` entity), opening the Terminal OS |

### Server racks: `racks/`

| Page | Covers |
|---|---|
| `server-rack.html` | `server_rack`: the 1×3×2 multiblock, 42U, doors, connection points (top, bottom, rear), power, the HUD popup, break confirmation and protection, the rack screen |
| `devices.html` | Catalogue of all 26 rack device types (gen table: U, drain, lanes, ELCL prefix) with a section per device: `firewall`, `router`, `ups`, `network_controller_2u`, `network_controller_4u`, `l2_switch_24`, `l2_switch_48`, `l3_switch`, `compute_server`, `memory_server`, `fabrication_server`, `directory_server_rack`, `monitoring_server`, `nas`, `san`, `rack_console`, `rack_fabrication_console`, `rack_schematic_console`, `wireless_controller`, `tape_library_4u`, `tape_library_6u`, `optical_multiplexer_rack`, `optical_amplifier_rack`, `optical_transport_shelf`, `chassis_switch`, `blade_enclosure` |
| `chassis.html` | Chassis Switch, Blade Enclosure and Optical Transport Shelf bays, and all 18 parts: `supervisor_card`, `access_card`, `uplink_card`, `routing_card`, `trunk_card`, `chassis_psu`, `controller_card`, `transponder_card`, `mux_card`, `amplifier_card`, `protection_card`, `compute_blade`, `memory_blade`, `fabrication_blade`, `storage_blade`, `interconnect_module`, `blade_psu`, `management_module` |
| `security.html` | The Firewall: six permissions (VIEW, INSERT, EXTRACT, CRAFT, BUILD, RACK), default policy, operators, fail-closed, what each permission guards |
| `rack-elevations.html` | `rack_elevation`: writing a plan, the editor and materials screens, copy and clear recipes |

### Networking: `networking/`

| Page | Covers |
|---|---|
| `switches.html` | L2 Switches (lane pools), the L3 Switch (segment routes, QoS rules), VLAN trunking (access and trunk ports, native segment), the `trunk_port` part |
| `routers.html` | Routers: Link Card links, routes (Move/Share, filters), static and dynamic routing, transit (None/Listed/All), route table and neighbours, ACLs |
| `exchange-and-directory.html` | `exchange_switch` and `cross_connect` (members, peers, route server, chunk loading), `directory_server` block and rack form (publishing devices as `SYSNAME/DEVICE`) |
| `bridges-and-links.html` | `network_bridge` (across dimensions), `link_card`, `point_to_point_link`, `relay_antenna` |
| `optical.html` | Fiber Cable span loss, `optical_transceiver`, `optical_amplifier` and `optical_multiplexer` (block and rack), the Optical Transport Shelf and 1+1 protection |
| `wireless.html` | `access_point` (8 clients each), `wireless_bridge`, `wireless_ingress_port`, `wireless_egress_port`, the Wireless Controller, `radio_module`, cross-dimension options |

### Control and facility: `control/`

| Page | Covers |
|---|---|
| `control-interface.html` | `control_interface`: redstone channels in and out |
| `plc.html` | `plc`, `eeprom_cartridge`, `presence_sensor`, `inventory_sensor`, `fluid_sensor`, `light_sensor`, `timer_module` (game clock); scan model, fault outputs, the PLC screens (PLCSTS, PLCIO, PLCMOD, PLCRTN) and editor |
| `display-panels.html` | `display_panel`: merging into screens, Text and Dashboard modes, regions, text sizes, widgets, graphs, images, touch |
| `lights-and-sirens.html` | `cage_light_<colour>` (16 dye colours), `siren_green`, `siren_amber`, `siren_red` (Alarm Strobes, six tones) |
| `speakers.html` | `speaker`: file, URL, note and MIDI sources, MP3 through JLayer, the web-audio settings (server and client) |

### Terminal OS and ELCL: `elcl/`

| Page | Covers |
|---|---|
| `index.html` | Overview: the desk, the OS, ELCL, the Control Interface; systems and networks; what is saved; signing on; users, SECLVL and the Firewall |
| `using-the-terminal.html` | Screen layout, keys, mouse, the command line, the prompter (F4), help windows (F1), phosphor colours |
| `screens.html` | Every screen (44 + machine screens) with how it opens, its options and keys |
| `libraries-and-editor.html` | Libraries and members, the source editor (line commands, editor commands, keys), syntax check, compiling, the compile listing, storage cost, folder sync, SAVLIB/RSTLIB to diskette |
| `database-files.html` | Files, records, system files, DDS definitions, the file commands and screens, RUNQRY, copy/export/import, limits and authority |
| `language.html` | Lexical rules, program structure, grammar, variables and types, expressions and operators, the 17 built-in functions, control flow, subroutines, MONMSG, CALL and parameters, RTN* parameters, limits and runtime model |
| `jobs-and-scheduling.html` | Jobs and job hosts, job logs, schedule entries, the 24 trigger events, message queues, spooled output, printing, system values (gen table), the Midrange line, Display Panels and Arcforge machines from ELCL |
| `control-and-plcs.html` | Programming the Control Interface and PLCs from ELCL |
| `status-and-limits.html` | Known limits and the server options that govern the OS |
| `examples.html` | Worked examples: the shipped ELSYS programs (ARCHIVE, ITEMSETUP, LOGITEMS, NOCWALL, RESTOCK, SORTDEMO, UPSALERT, ITEMHIST.pf) plus short new ones, every one compile-checked |
| `commands/index.html` (gen) | All 167 commands by category, then one generated page per command (`commands/<NAME>.html`): description, context, Firewall authority, full parameter table, prompter hints, examples |
| `messages.html` (gen) | All 182 messages by range, with severity and text |

### Integrations: `integrations/`

| Page | Covers |
|---|---|
| `arcforge.html` | `small_wireless_bridge` on Arcforge machines (read, control and power them; WRKMCH; device codes), gas storage in Pressurized drives, the 9 Arc Crusher dust recipes, the API version check |
| `jei.html` | Lithography recipe category, catalysts, transfer, ghost-slot drag, search sync |
| `jade.html` | Jade tooltips (lanes, status, ...) and the rack opt-out |

The requested section list had no Jade page. Jade compat is in the code (`compat/jade`), so it gets one.

### Server admins: `admin/`

| Page | Covers |
|---|---|
| `config.html` | Where the files are (`encodedlogistics-local.toml`, or `-common.toml` on older betas, and `encodedlogistics-client.toml`), that the main file is per installation rather than per world, and a guided tour of the options that matter most. Links to the full generated table |
| `permissions.html` | The Firewall from an admin's view, operators, SECLVL, *SECOFR, `firewallFailClosed` |
| `folders.html` | `<world>/encodedlogistics/`: library folder sync (`allowFolderSync`), CSV import and export, audio and image folders, `allowImages`, `allowWebAudio` |
| `performance.html` | Every limit and budget (ELCL budgets, list and source limits, display image limits, speaker limits, lane and controller limits, chunk loading) and the options that set them |
| `commands.html` | `/encodedlogistics caches [log true\|false]` |

### Developers and add-ons: `developers/`

| Page | Covers |
|---|---|
| `index.html` | What exists for add-ons, and that the hooks below are public but not a stable API |
| `extension-points.html` | `CommandRegistry.register` / `bind` (new ELCL commands), `JobHosts`, `Displays`, `PressurizedSources.register`, `MachineBridges.setAccess` (a single slot, which Arcforge takes), the capabilities the mod registers on its blocks (`ModCapabilities`), with examples |
| `arcforge-integration.html` | How the Arcforge integration uses the Arcforge API (version check by reflection, gases, machine access): a worked example of an integration |
| `building-the-docs.html` | How this site is generated (links to PUBLISHING.md) |

There is no ServiceLoader, IMC, custom event or custom capability, so none is documented.

### Reference (generated): `reference/`

| Page | From |
|---|---|
| `items/index.html` + `items/<id>.html` | Every block and item (registry, display names, tooltips, tags, icons, recipes, device types) |
| `recipes.html` | Every recipe by type, rendered as crafting grids, lithography, smelting, blasting, stonecutting, crushing and the special recipes |
| `tags.html` | The mod's item and block tags and their members |
| `config.html` | All 228 options: section, key, type, default, range, comment, config-screen label |
| `device-types.html` | Every device type and default name prefix |
| `sounds.html` | All 25 sound events with their subtitles |
| `screens.html` | The menu types and the Terminal OS screen IDs |
| `data-components.html` | The 19 data components (for pack makers and add-ons) |

The ELCL command and message references are generated into `elcl/` (above).

### Changelog: `changelog.html` (gen, from `CHANGELOG.md`)

## Left out: described in older docs but not implemented

Each was checked against the code. The review notes give the file or the grep that settled it.

**Networks and racks**
- The Cable Certifier tool (`docs/optical/HANDOFF.txt`): no item, class, lang key or recipe.
- Access Points connecting on their full sides and bottom, and an Access Point fault state: they connect only on the
  side they're mounted on, and show only off, linking or online.
- Wireless Remote and Handheld Fabrication Terminal: dropped, not registered.
- A Trunk Port's lanes being shared out among its segments: `rack.trunkPortLanes` is shown in its panel only.
- Lit cable strands showing lane use.
- Ad-hoc (controller-less) networks: the solver and `lanes.adHocMaxDevices` exist, but nothing discovers a network
  without a controller structure, so devices without a controller are simply offline. The docs say that and leave out the
  ad-hoc mode and its option description beyond the generated config table.
- The Router routing between Segment Isolator segments (that's the L3 Switch), devices limited to 1U/2U, the rack
  connecting only at rear and top, a five-permission Firewall, the `#encodedlogistics:rack_devices` tag gating the rack
  GUI (the tag exists but no code reads it), a per-device energy capability.
- One Rack Scheduler per rack needing its own Compute and Memory Server (they pool network-wide across racks).
- L3 QoS by crafting job or by port (QoS only orders items within the switch's own routes).
- The SAN's +25% speed per extra transceiver (it adds +1 priority each).
- The Tape Library mail slot, and archiving skipping items a crafting job has reserved.
- The Rack Console's Memory Die "fabrication mode" upgrade (replaced by the Rack Fabrication Console).
- Chassis power of 200 FE/t per card and 1,000 per chassis, 8 threads per Compute Blade and 32-lane transponders (the
  code's values are documented instead: `optical.chassisCardDrain` 2.0, `optical.chassisDrain` 10.0, derived blade
  threads, a Fiber Cable's lanes less span loss).
- The desk's original lowercase CLI (`help`, `show ...`, `withdraw`, `craft`, `cancel job`) and its Java
  `TerminalCommand` API; the F24 graphic item view; a `/settings` command. WTHIT compat.

**Storage, crafting and the Midrange line**
- The Tape Reel recipe making 2 (it makes 1); the Disk Drive limited to 8K–128K drives (it takes any); Tape Libraries
  taking Tape Reels (they take LTO tapes only).
- Spinning disk packs and turning tape reels (no renderer).
- "PRINTER01" as the printer's name (the code numbers printers PRT01, PRT02, ...). (The Card Punch's screen is
  CARDPUNCH, as the Midrange handoff says; its device type is KEYPUNCH.)
- The CRT frame's glow and scanlines.
- `threadRuns` (replaced by `autocrafting.threadCrafts`).

**Control and facility**
- `CRTPLCCTG` and writing cartridges from the Terminal Desk (cartridges are written from PLCSTS option 4 only); a
  ladder-logic editor; a real-time clock in the Timer Module (it reads the game clock).
- The Display Panel's "Script-controlled" mode (removed; old saves load as Dashboard).
- The client option `neverPlayWebAudio` (replaced by the opt-in `audio.allowWebAudio`, default off).
- Coloured light through a lighting mod.

**Terminal OS and ELCL**
- The Mainframe job host and jobs that resume after a restart: no real job host resumes, so a job on a host that goes
  down ends with ELC0310.
- Status Display, NOC Video Wall and Rack Console screen as display devices (only Display Panels are).
- The Label Maker (devices are named with RNMDEV or WRKDEV 2).
- `*MSGW` and messages that wait for a reply.
- `SNDMSG TOTRM()` and `WRKINV FILTER()` doing anything (both are accepted and ignored; the command pages say so).
- DATFMT changing the header clock.
- Completion messages for CHGJOB, HLDJOBSCDE, RLSJOBSCDE, HLDTRGEVT, RLSTRGEVT (they succeed silently).
- Spooled job logs (PRTRPT `*JOBLOG` prints straight to paper; nothing spools a job log).
- Second-level message help (cause and recovery text): never existed.
- A real per-item storage tier (CHGITMTIER works through the Tape Libraries' lists).

## Spec-vs-code differences resolved (document what the code does)

From the old page's "Where the spec and the code differ", its status section and spot checks:

- **The screen header shows SYSNAME** (the old page says the network name is shown and SYSNAME isn't).
- **`DSPMSG USR()` works:** `*SYSOPR` (QSYSOPR) or another user's queue, for *SECOFR or full authority. The old page says
  no screen shows QSYSOPR.
- **Counts:** 167 commands and 182 messages, not "132", "141" or "all 99". The command and message references now cover
  the 23 commands and 27 messages (ELC25xx–27xx) the old page never had.
- **Trigger events:** 24, including `*CRFSTALL`, `*CARDFAULT`, `*SUPFAILOVER`, `*PROTSWITCH`, `*SPANLOSS`.
- **`&EVENT` length:** the event name arrives whole and is cut only by the program's own declaration, so `*SUPFAILOVER`
  (12 characters) needs `LEN(12)`. The docs recommend `LEN(12)`. The shipped UPSALERT still declares `LEN(10)`, which is
  fine for the events it handles.
- **Screens added since the old page:** WRKSCH, DSPSCH, DSPROUTE, DSPNBR, WRKACL, WRKIX, DSPIXMBR, WRKDIR, WRKHW, DSPHW,
  DSPOPT, DSPJOBSTP, and the PLC screens in the screen list. Work with Jobs has option 8 (Display steps).
- **Spooled output sources:** program and PF compile listings, printed members, PRTTXT, and `RUNQRY OUTPUT(*PRINT)`
  (file QPQUPRFIL); 60 lines a page.
- **Display Panels** have two modes (Text, Dashboard) and four text sizes, and the widget list includes `*TEXT`,
  `*TABLE` and `*NONE`.
- **Firewall wording:** the ELCL authority CONFIGURE is the Firewall's BUILD permission. Display, print and OS commands
  need no Firewall permission (the OS checks its own library, job and system value authority).
- **Config placement:** the main config is a local/common file (`encodedlogistics-local.toml`), one per installation, not
  a per-world server config, and it isn't synced to clients. Many display, wireless, Midrange and tape options sit in
  the `rack` section. Feature pages group them by feature, with the real TOML path.
- `wirelessCrossDimension` and `bridgeCrossDimension` default to true.
- The old page's developer bookkeeping sections ("Commands in the code but not in COMMANDS.md", "The desk's old words")
  become a one-line note. Their old anchors redirect to the command reference.

## Still to confirm before the pages are final

- The exact config file name under NeoForge's LOCAL type (`encodedlogistics-local.toml` is inferred from lang keys). It
  will be checked by running the game.
- Whether joining two networks that each have a controller (bridge, P2P, optical or wireless) always shows a controller
  conflict. The pages will state what's confirmed in a game test, or say only that one network has one controller
  structure.
- That a single non-fiber block resets fiber span loss (from reading `SpanLoss.apply`). It will be checked against the
  code again before it goes in a page.
- The rack 2U/4U Network Controllers' auto-names (`NETWORK_CONTROLLER_2U01`) are longer than RNMDEV allows. They're
  documented as they are; it may be worth giving them a short prefix in the code.

## Generated vs hand-written

| Generated from exported data | Hand-written |
|---|---|
| Item and block entries, recipes (all types), tags, the device type table, the sounds, menus, data components and config tables, the ELCL command pages and command index, the message reference, the system value and trigger tables, the rack device catalogue's numbers, the changelog, the search index, the old-anchor redirect map | Home, Getting started, every guide page in Networks through Developers, the ELCL guide pages and worked examples |
