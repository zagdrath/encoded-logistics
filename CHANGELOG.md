# Changelog

All notable changes to Encoded Logistics are listed here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

Versions are `MAJOR.MINOR.PATCH+MINECRAFT` (for example `1.2.0+26.3`), set by `mod_version` in
`gradle.properties`; the Minecraft version after the `+` changes only when the mod moves to a new one.

- **PATCH** (1.2.0 → 1.2.1): bug fixes and small tweaks. No new content, and worlds and configs carry
  over untouched.
- **MINOR** (1.2.0 → 1.3.0): new content or features, and balance changes. Worlds still load; anything a
  player has to redo (settings that reset, blocks that need re-placing, ELCL programs that need changing) is
  listed under *Upgrading*.
- **MAJOR** (1.x → 2.0.0): changes that break existing worlds or remove content: registry names that
  change or go away, items and blocks that disappear on load, or stored network contents that don't carry over.

Changes land under **Unreleased** as they're made; when a version is released, that section gets its
number and date. How to cut a release is in [docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

### Added

- Support for Minecraft 26.1.2, on NeoForge 26.1.2.114 or later.

## [1.0.0] - 2026-10-07

The first release. Needs Minecraft 26.3 and NeoForge 26.3.0.0-beta or later. JEI, Jade and Arcforge are optional.

### Added

- **Networks.** The Network Controller multiblock powers a network, shares its energy buffer across its structure
  and hands out lanes. Network Cables (normal and dense, in 13 colours) and Fiber Cable carry the lanes, with cable
  anchors, facades on any cable side (with a ghost preview), and a Segment Isolator. Power comes in through the Power
  Inlet and is stored in Capacitor Banks. Jade shows each cable's lanes as used / carried.
- **Storage.** Storage Drives in a Drive Bay, in item, fluid, Pressurized (gases) and energy types. The Disk Drive
  keeps a drive as hot storage that spins up and down; the Tape Drive archives to and recalls from Tape Reels as cold
  storage.
- **Terminals.** The Access Terminal, the Fabrication Terminal and the Handheld Terminal (through a Relay Antenna).
  They have type tabs (All / Items / Fluids / Pressurized / Energy), `@mod` and `#tag` search, JEI search sync,
  missing-ingredient highlighting in JEI, double-click to move every matching stack in, container fill and pour
  clicks, and sort and view settings that are saved.
- **Moving things.** Ingress and Egress Ports, the Inventory Tap, the Threshold Sensor, Collector and Deployer Planes,
  Point-to-Point Links and the Network Bridge (across dimensions). There are Fuzzy Match and Redstone Control
  modules, and allow / deny filters on every filter slot that take JEI ghost drags. Items, fluids, gases and energy
  all move through ports, taps, planes and links.
- **Autocrafting.** The Schematic Encoder, the Fabricator, the Gateway and the Scheduler multiblock, with processing
  schematics that can include fluids and gases. Crafting job history, and toasts for finished, failed and cancelled
  jobs, which go to a message queue when you're offline.
- **Server Rack.** A multiblock of rack devices:
  - the Firewall (rack access and Link Card permissions), Router, UPS (battery, alarms you can mute) and L2 / L3
    Switches with lane pools and segments;
  - Compute, Memory, Fabrication and Monitoring Servers, and the Rack Scheduler;
  - NAS and SAN storage, and 4U / 6U Tape Libraries with hot / cold tiering;
  - the Rack Console, the Wireless Controller, and 2U / 4U rack Network Controllers in redundant pairs with
    failover.

  Racks also have power ports and per-device priorities.
- **Wireless.** Access Points, the Wireless Bridge, Wireless Ingress and Egress Ports, and Link Cards to pair them.
- **The Terminal Desk and Swivel Chair.** A green-screen Terminal OS with sign-on and user profiles, menus, Work
  with screens for devices, jobs, libraries, members, files, inventory and machines, and help on every screen.
- **ELCL**, the network's command language:
  - a source editor, compiler and VM with budgeted, resumable jobs;
  - batch jobs, job schedules and event triggers;
  - commands for inventory, crafting, devices, power, displays, printing, signals and machines;
  - database files with queries and CSV import / export;
  - libraries that sync with the world folder, and SAVLIB / RSTLIB to diskettes.

  The full reference is in [docs/elcl](docs/elcl) and [docs/terminal-os-elcl.html](docs/terminal-os-elcl.html).
- **The Midrange line.** The Midrange System and the Integrated Midrange System (networks' controllers and batch
  hosts), the Expansion Cabinet, the Keypunch, the Card Reader and the Line Printer. They use Punch Cards, 8"
  Diskettes, Diskette Magazines and Printouts (green-bar pages you can read, hold like a map or put in item frames).
- **Automation hardware.** The Control Interface (six-face redstone in and out), and the PLC with its modules and
  EEPROM Cartridge.
- **Display Panels.** Panels merge into screens. They show text or dashboards with widgets, live graphs, tables and
  dithered images, and can run touch triggers. You configure them in-game or from ELCL.
- **Signals.** Cage Lights, Alarm Strobes and Speakers. Speakers play OGG / MP3 files from the world folder,
  allowlisted web audio, note sequences and MIDI across several speakers on one clock.
- **Materials.** Neodymium, tantalum and gallium ores, the Lithography Press, Processor Dies and two material
  tiers. Metal dusts can be made with Arcforge's crushing when Arcforge is installed.
- **Arcforge integration (optional, Arcforge 2.5.0 or later).** The Small Wireless Bridge links Arcforge machines
  to a network through a Wireless Controller, and Gateways feed bridged machines and collect their outputs for
  crafting jobs. Work with Machines and the machine commands read and change machine status and settings.
  Monitoring Servers and Display Panels graph machines, and Pressurized drives store Arcforge gases. This uses
  Arcforge's machine control and gas API (arcforge-api 1.1); with an older Arcforge the integration is off and the
  log says why.
- **JEI and Jade** support throughout.

[Unreleased]: https://github.com/zagdrath/encoded-logistics/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/zagdrath/encoded-logistics/commits/v1.0.0
