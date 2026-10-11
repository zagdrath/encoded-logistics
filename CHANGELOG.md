# Changelog

All notable changes to Encoded Logistics are listed here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

Versions are `MAJOR.MINOR.PATCH+MINECRAFT` (for example `1.2.0+26.3`), set by `mod_version` and
`minecraft_version` in `gradle.properties`, and each is released as the tag `vMAJOR.MINOR.PATCH+MINECRAFT` (1.0.0,
the first release, was tagged `v1.0.0`). Each Minecraft version has its own branch and its own changelog; this one is
Minecraft 26.3's (`main`). The same version number on two Minecraft versions has the same changes.

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

- **Rack Elevation**: a clipboard that plans a Server Rack. Sneak-use it on a rack to record every device, and
  optionally what each holds and its settings. Use it in the air to open the editor: the rack screen's own
  elevation with the planned devices as ghosts, a searchable device list to drag from, and a materials list of
  what you have and need. Use it on a rack to install the plan into empty units from your inventory, shulker boxes
  included, and optionally the network's storage. It never removes or replaces a device, and it reports what it
  skipped or was missing, with a [show] link to the list. Holding a written one shows the plan on the rack in green,
  amber and red, with a summary popup. In creative it needs no items (`rackElevationCreativeFree`). Copy one by
  crafting it with a blank one, and clear one by crafting it alone. Rack Elevation needs Rack access, and network
  storage needs Extract.

### Security

- Speakers' web audio is now off for each player until they turn on *Allow Web Audio* (`allowWebAudio` in
  `encodedlogistics-client.toml`, under Sounds). It replaces the client option *Never Play Web Audio*. A Speaker
  playing web audio you haven't opted in to says so on its popup.
- Before fetching web audio, your game checks the URL itself instead of trusting the server: http or https only, no
  user name or password, and the host must resolve only to public addresses (not loopback, private, link-local,
  multicast or unspecified). The host is looked up once, and the game connects to that checked address only.
  Redirects stay off, the size limit stays, and the audio is only kept in memory.
- Display Panel images: the server reads an image's width and height from its header before decoding it, and refuses
  images over the new `displayMaxImagePixels` (16,777,216 pixels by default), as well as over `displayMaxImageSize`
  and `displayMaxImageBytes`. The image is decoded by the same reader that checked it.

### Changed

- The mod's jar carries JLayer's licence (GNU LGPL 2.1) at `META-INF/licenses/jlayer-LGPL-2.1.txt`, and the mod list
  credits JLayer. The mod list links to the issue tracker on GitHub and the mod's CurseForge page.
- New Storage Drives of every type (item, fluid, pressurized and energy) stack to 64, as cells do in AE2. A drive
  that has been in a holder is one of a kind and stacks to 1, until it is empty again in your inventory: then it
  goes back to a new drive and stacks with the others.

### Fixed

- A copied Storage Drive (a creative middle-click, or Ctrl+pick-block of a Drive Bay, NAS or SAN with drives in)
  shared its original's contents: taking items out of one took them out of both, and a network with both counted
  them once. A copy now becomes a drive of its own, holding a copy of what the original had.
- The Server Rack screen drew a NAS's or SAN's drive sleds squeezed into the left of their bays (a full SAN looked
  like 9 drives a row with 3 empty bays); they now sit in their bays as the rack in the world shows them.
- Turning off UPS alarms in your own config did nothing on a server, since only the server's `upsAlarms` was
  checked. A new client option, *Hear UPS Alarms* (`upsAlarmSounds` in `encodedlogistics-client.toml`, under
  Sounds), silences every UPS's alarm and beeps for you alone. The server's `upsAlarms` still turns them off for
  everyone.

## [1.0.1] - 2026-10-07

### Fixed

- Glowing parts (LEDs, status lights, screens, lamps and the lit parts of cables) were shaded darker on their sides
  and undersides like ordinary block faces. They're now evenly bright from every side.
- Breaking a Swivel Chair with someone in it could leave them sitting in mid-air for a moment, or for good where
  entities weren't ticking. They now stand up as soon as the chair is gone.

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

[Unreleased]: https://github.com/zagdrath/encoded-logistics/compare/v1.0.1+26.3...HEAD
[1.0.1]: https://github.com/zagdrath/encoded-logistics/compare/v1.0.0...v1.0.1+26.3
[1.0.0]: https://github.com/zagdrath/encoded-logistics/commits/v1.0.0
