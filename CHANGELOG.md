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

## [1.3.1] - 2026-10-14

### Changed

- **L2 Switch lanes match their ports:** a 24-Port L2 Switch now pools 24 lanes (was 16) and a 48-Port 48 (was 32)
  (`l2Switch24Lanes`, `l2Switch48Lanes`).
- **Craft larger amounts:** a single crafting request can now be up to 2,147,483,647 (was 999,999), from the Craft
  amount screen, the terminal's Craft panel and ELCL `STRCRAFT QTY`.
- **Fiber Cable popup** shows the network the cable is on (or *Not on a network*), its span, and the lanes running
  through it out of those it carries after span loss, instead of only the span's capacity. It's shown only to players
  who can view that network.
- **Tooltips wrap:** long hover hints across the mod's screens wrap onto several short lines instead of running across
  the screen.
- **New art:** the Optical Amplifier is now an in-line fibre housing whose ports line up with the Fiber Cable, with a
  fault light. The Directory Server is a full block. The Chassis Switch, Blade Enclosure and Optical Transport Shelf
  (and the rack Optical Multiplexer, Optical Amplifier and Directory Server) have their own item icons and detailed
  rears in the rack. Chassis parts come in three families that are easy to tell apart: silver blades for the Blade
  Enclosure, navy line cards for the Chassis Switch, and teal optical cards for the Transport Shelf.
- GUI titles use the blocks' full names: Exchange Switch, Directory Server, Optical Multiplexer.

### Fixed

- **A rack Network Controller could show no devices** on a network that crossed a chunk border. If a network was
  worked out while part of it was unloaded (at server start or login, for example), it stayed cut short until a block
  on it changed. A network is now worked out again when a chunk at its unloaded edge loads.
- Racks reached through a cross-dimension Bridge could be taken as a rack controller's redundant partner.
- Fiber Cables kept showing the span of a network they were no longer on, even when connected to nothing.
- The Trunk Port's item and placed part showed the missing texture.
- **Network Controller panel:** it showed 8 uplink chips on the 4U (and only 4 on the 2U) instead of one for each of
  the rack's 7 connection points. *Switch over* didn't fit its button, and the partner's role was cut off (a cut-off
  value now ends in "..." and shows in full on hover).
- **Overlapping text** in the Chassis Switch, Blade Enclosure and Optical Transport Shelf panels (every tab), the L2/L3
  Switch Ports view, the Directory Server, Exchange Switch, Optical Multiplexer, Trunk Port and Router panels, and the
  Job Status screen with large amounts. The Chassis Switch's Cards button is now a tab.

### Upgrading

- Worlds that already have a server config keep the old L2 Switch lane counts (16 and 32). To get the new ones, set
  `rack.l2Switch24Lanes` to 24 and `rack.l2Switch48Lanes` to 48 (`/encodedlogistics config` or the config file).

## [1.3.0] - 2026-10-14

### Added

- **Storage blocks** for Neodymium, Tantalum, Gallium and Ferrite, for compact storage and shipping, as Arcforge's
  metal blocks: nine ingots (or nine Ferrite) craft a block, and a block crafts back into nine. They need an iron
  pickaxe, are in `#c:storage_blocks/<name>`, and the three metal blocks can build a beacon's base.
- **Dynamic routing.** A Router can now run in **Dynamic** mode (its panel's new Mode tab; every existing Router loads
  as Static and works exactly as before). Dynamic Routers whose networks are linked (a Link Card) find each other,
  share what they can reach, and work out the cheapest path to every network beyond (more Optical Transceivers make a
  hop cheaper). A route can then go to any network in the Router's route table, not only one it links to: its items
  travel Router to Router along the best path, each Router on the way carrying them with its own throughput, and never
  land in a network in between. If a Router on the path goes down, the others find a new path within a few seconds
  (`reconvergeSeconds`); until there is one, the route pauses and nothing is lost.
  - **Transit:** a network lets other networks' traffic cross it only if its Routers allow it: None (the default),
    Listed (click networks on the Table tab) or All.
  - The Router's panel has tabs: **Routes** (as before), **Mode** (routing mode, transit, neighbours, routes, limits,
    throughput) and **Table** (the route table: network, next hop, cost, hops; click the heading for the neighbours
    and their costs, which can be overridden). Its popup shows its mode, neighbours and known networks.
  - Config: `routingBaseCost`, `reconvergeSeconds`, `maxHops` (16).
  - Terminal OS: `DSPROUTE` and `DSPNBR` screens, `RTVRTE`, `RTVNBRSTS` and `CHGNBR`, and the trigger events
    `*NBRUP`, `*NBRDOWN` and `*RTECHG`.
- **Router ACLs.** Each Router can hold named ACLs (its panel's new ACLs tab): ordered rules matching an item, a
  #tag, a resource type or anything, going into or out of its network, that permit or deny, optionally only so many a
  minute or a game day. Apply one to a route or to a network the Router reaches directly: a Move moves only what it
  permits, a Share shows only that. Each rule counts its hits. What no rule matches is permitted (`aclDefaultPermit`).
  Terminal OS: `ADDACLE`, `CHGACLE`, `RMVACLE`, the `WRKACL` screen, and the `*ACLDENY` trigger event.
- **Internet Exchange.** The **Exchange Switch** (two blocks tall) is where Routers on any networks meet. Use a
  **Cross-Connect** on a Router's unit, then on the exchange: the Router joins (or asks to, when its owner approves
  members). Members peer through the route server (on by default, members opt in) or with each other (both sides
  accept), and each side sets per peer whether to exchange routes and allow transit; ACLs apply to peers as to any
  directly reached network. Its screen has Members, Peers and Setup tabs; its popup shows members, sessions and owner.
  It keeps its chunk loaded while it has members (`exchangeChunkLoading`). Config: `exchangePorts` (32),
  `exchangePortLanes`, `exchangeDrain`. Terminal OS: the `WRKIX` screen, `APVIXMBR` and `RMVIXMBR`.
- **VLAN trunking.** A rack's segments now have IDs (its own network is 1). Each device a switch pools is a port: an
  **access** port on one segment, as before, or a **trunk** that serves several segments at once (all of them, or the
  ones picked) with a native one; segments on a trunk still can't see each other's storage. The L2 and L3 Switch
  panels have a **Ports** view (the L3 Switch's first tab). The new **Trunk Port** cable part carries segments (added
  with a Link Card) into the network it's on, so every rack there can use them without a Link Card of its own; its
  panel shows the lanes its segments' devices use (`trunkPortLanes`).
- **Directory Servers and qualified names.** The **Directory Server** (a block, or a 1U rack device) publishes chosen
  devices of its network. Any Terminal OS command that takes a device then takes `SYSNAME/DEVICE` for a device on
  another network, when a Router has a route there with the new **Control** policy (a third route mode beside Move
  and Share; ACLs match it with `*CONTROL`) and the caller has the Firewall's permission on that network. Lookups are
  cached for `directoryCacheSeconds` (30). Its screen has Published, Remote and Lookups tabs. Terminal OS: the
  `WRKDIR` screen, `RTVDIRENT` and `CHGDIRENT`.
- **Fiber span loss.** A Fiber Cable run longer than `fiberSpanThreshold` (256) blocks loses a lane for every further
  `fiberSpanLossStep` (32), down to 1 (`fiberSpanLoss` to turn it off). Network and Dense Cable, bridges and links are
  unchanged. Looking at a Fiber Cable shows its span and the lanes it carries.
- **Optical Amplifier** (an inline housing on a fibre run, or a 1U rack device): powered, it starts the span count
  again; unpowered it just passes the light. The rack form lets optical lines pass through its rack. Its popup shows
  its input and output lanes and the spans either side.
- **Optical Multiplexer** (a block, or a 1U rack device): carries `multiplexerChannels` (8) client networks over one
  Fiber Cable line to its partner (paired with a Link Card). Give a channel a network with a Link Card from any block on
  it; channel n at one end joins its network with channel n's at the other, each up to a Fiber Cable's lanes less the
  line's loss, and channels never mix. The block's line port is its front, and the networks on the line itself stay
  apart. Its screen has Channels and Line tabs.
- **Modular chassis**, rack devices built from cards that are hot-swappable: put parts in through the rack's open door
  or the chassis's panel (its face at 1:1), sneak-click to take them out. Each part is drawn in its bay, lit while it
  works, and has a popup of its own. Rack Elevations record and apply them with their chassis (with contents).
  - **Optical Transport Shelf** (6U): two Controller Cards and eight line cards - Transponder (a client network onto a
    channel, 1-40), Mux (eight channels on the line), Amplifier (more reach) and Protection (1+1 over two separate
    paths, fibre or fibre and a Network Bridge pair, switching within a tick, revertive or not). Paired end to end with
    a Link Card. Its panel shows its slots, channels and spans (length, loss, lanes, quality).
  - **Chassis Switch** (10U): an L3 Switch made of cards. Two supervisors (the standby takes over at once), eight line
    cards - Access (48 rack-local lanes), Uplink (four Optical Transceiver cages), Routing (routes and QoS, each card
    adding throughput), Trunk (VLAN trunk ports) - and four power supplies (it runs on one; degraded below two).
  - **Blade Enclosure** (10U): sixteen blades - Compute and Memory (threads and job memory for the rack Scheduler, a
    quarter more per rack unit than their servers: `bladeBonusPercent`), Fabrication (crafts from its schematic slots),
    Storage (two drive bays each, as network storage) - and behind, two Interconnect Modules (its blades' lanes; they
    take none of the rack's), two Management Modules and six power supplies.
  - The rack takes devices up to 10U tall.
- Terminal OS: the `WRKHW` (Work with Hardware) and `DSPOPT` (Display Optical Status) screens, `RTVCHSSTS`,
  `RTVOPTSTS` and `CHGPROT`, messages ELC27xx, and the trigger events `*CARDFAULT`, `*SUPFAILOVER`, `*PROTSWITCH` and
  `*SPANLOSS`. Config (section `optical`): `fiberSpanLoss`, `fiberSpanThreshold`, `fiberSpanLossStep`,
  `multiplexerChannels`, `shelfChannels`, `accessCardLanes`, `routingCardRate`, `bladeBonusPercent`,
  `interconnectLanes`, and the drains.

- **Admin commands** (operators):
  - `/encodedlogistics config get|set <option>` and `/encodedlogistics config reset all|<section>|<option>`, with tab
    completion of every section and option, as Arcforge's `/arcforge config`. Changes are saved to
    `encodedlogistics-local.toml` and take effect at once.
  - `/encodedlogistics debug solves` (each network's solves over the last minute: how many, what they cost and what
    caused them), `debug ticks` (each level's and the costliest networks' tick time), `debug networks` (every
    network's status, size, lanes and energy) and `debug watch solves [ms]` / `debug watch off` (slow solves shown in
    your chat). They answer whoever asks; nothing goes to the server log.

### Fixed

- **Server lag from networks being worked out again.** Any change anywhere - a block placed, or just a chunk with one
  end of a Network Bridge, Point-to-Point Link, Wireless Bridge or Wireless Port loading as a player walked by - made
  every network in every dimension rediscover itself and work out its lanes again, and a network with many Server
  Racks did so slowly. Now:
  - only the networks a change touches are worked out again (the ones holding the block or next to it, and those at
    the far ends of its remote links);
  - a remote link's end loading or unloading costs nothing unless the link really came up or went down (its other end
    is loaded);
  - a network works itself out at most once every `resolveInterval` ticks (20; 0 for every change), so a burst of
    changes costs one solve; a lone change is still worked out on the next tick;
  - a redundant pair of rack Network Controllers works the network out once, not once each;
  - working out a rack's uplinks no longer searches the whole network once per rack: a network of 50 racks with seven
    uplinks each takes about a sixth of the time it did, with exactly the same lanes.
  A solve slower than `slowSolveMillis` (50 ms) is logged with its network and cause.
- Jobs share identical machines instead of piling onto the first ones. Each run now goes to the least busy Gateway,
  Fabricator, Fabrication Server or Blade Enclosure that can make it, so a second job using the same schematic
  uses idle Gateways (and the machines behind them) rather than queueing behind the first job on the same ones.

### Upgrading

- Routers load in Static mode with their routes unchanged; racks' linked segments get IDs in order (2, 3, ...) and
  every pooled device loads as an access port on the segment it was on; L3 Switch routes keep the segments they
  had (now by ID). Nothing needs redoing.
- Fiber Cable runs longer than 256 blocks now lose lanes (span loss). A network that relied on a longer run at full
  width needs an Optical Amplifier in it, or `fiberSpanLoss = false`.

## [1.2.1] - 2026-10-12

### Changed

- **Much less server time on busy networks.** What a network works out from its layout (its storage and the order
  items go in and out, its Energy Storage Drives, the rack devices serving it, the racks in order, each rack
  Scheduler's pool) is kept until something it depends on changes, instead of being worked out again every tick. Idle
  Tape Libraries no longer look at the network's storage, rack Schedulers re-check their pool only when something
  changes (and every 40 ticks), and FE pushed into a Server Rack's connection points is taken without walking the
  network, reaching its energy when the network ticks. Controllers and racks no longer update comparators every
  tick, and racks only resync to clients when something they show changed. Priority order, routing and energy
  totals are unchanged.
- **New LTO tapes stack** to 64 and copy freely, as new Storage Drives do, so libraries are quick to fill. A tape that
  has been in a library is one of a kind and stacks to 1; one that's empty again goes back to blank in a player's
  inventory.
- `/encodedlogistics caches` (operators) shows how often each network cache was rebuilt over the last minute, and
  `/encodedlogistics caches log true` writes it to the server log every minute.

### Fixed

- Gateways couldn't run stonecutting schematics: a Stonecutter has no inventory to push into. A Gateway beside a
  Stonecutter now cuts the inputs itself and hands the outputs to the job; anything cut beyond what the job expects
  goes to the network.

## [1.2.0] - 2026-10-11

### Added

- **Schematic fallback.** When an item has more than one schematic, the planner tries them in priority order and uses
  the first whose whole ingredient tree the network has or can craft: a chest is made from birch planks when the
  oak-plank schematic's planks can't be had. When none can, the plan uses the first, so it still shows what that one
  is missing. Very deep trees with many recipes stop trying alternatives past a limit, so planning can't hang.
- **EDTMBR paste.** Pasting a whole script into the Terminal OS editor puts each clipboard line in as a member line at
  the cursor, and says how many lines went in (and how many were cut at column 80).
- **Server Rack break confirmation** (`rackBreakConfirm`, on by default). A stray left click no longer starts taking a
  rack down: sneak while breaking it, or left-click it a second time within a second. In creative, the first break
  arms it and the second breaks it.

### Upgrading

- `threadRuns` is replaced by `threadCrafts` (default 1). A server config that changed `threadRuns` needs the value
  set again under `threadCrafts`; the old key is ignored.

### Changed

- **Fabricators and Fabrication Servers run crafts side by side.** Each takes a craft from every job thread that offers
  one, and works them all at once, paying its FE per craft. A job's threads now keep `threadCrafts` crafts (1) in
  flight each, so a job's parallelism follows the threads its Scheduler gives it. 0 still means a thread runs any
  number of crafts.
- Display Panel regions scale with their screen when panels are added or removed, and their content keeps 4 px inside
  the bezel.

### Fixed

- Ingress Ports, Collector Planes, Gateways and crafting job outputs didn't overflow into Read/Write Share routes when
  local storage was full.
- Opening the Rack Elevation editor with JEI installed could crash ("screenWidth must be greater than 1"), as JEI was
  given the screen's bounds before it was laid out.
- The Rack Elevation editor's panels were open along their bottom edge.
- Splitting a Display Panel region too thin to split crashed the game.
- The Schematic Terminal drew its empty list space and a holder's unused cells as inventory slots; they're plain rows.
- The Creative Capacitor Bank's title ran into the "Creative" tag on its screen; long titles are now cut short with
  "...".

## [1.1.0] - 2026-10-11

### Added

- **Schematic Terminal**: a cable-face terminal (one lane, lit while online) for the network's schematics and crafting
  jobs. Its **Schematics** tab lists every Fabricator, Gateway, Fabrication Server and Midrange System with its
  schematics: click a slot to take one out or put the carried one in, select a holder and Sneak-click a schematic in
  your inventory to put it into a free slot, and right-click a holder to locate it (an outline for ten seconds, and
  its coordinates in chat) or rename it. Badges mark an output another holder also makes, different recipes for the
  same output (naming the one the planner uses), and a Gateway whose machine is missing or rejects the inputs. A
  Midrange System's diskette recipes show read-only. Its **Jobs** tab lists the network's crafting jobs with
  progress, scheduler and time; click one for its step tree, right-click to cancel (confirmed), hold or release it.
  It shows active jobs, or also the last 20 that ended, filtered by scheduler and requester. Moving schematics and
  renaming need Build; cancelling, holding and releasing need Craft unless it's your own job.
- **Rack Fabrication Console** and **Rack Schematic Console**: 1U consoles with the Fabrication Terminal's screen
  (crafting grid included) and the Schematic Terminal's, with amber and light-blue accents. All three consoles keep
  8U apart from each other.
- Crafting jobs that make no progress for `jobStallSeconds` (30) show as **stalled**, with a reason where one can be
  told: items still coming back from tape, no online holder of the step's schematic, a Gateway's machine missing,
  unpowered or with its output blocked, a Gateway unable to push its inputs, or no ingredients to run anything. The
  `*CRFSTALL` trigger event fires when a job stalls. Jobs on Scheduler Cores and the rack Scheduler can now be held
  and released too, and stay held across a restart.
- Terminal OS: **WRKSCH** (Work with Schematics: 4=Remove into the desk drawer, 5=Display, 8=Locate), WRKCRFJOB's
  **8=Display steps**, and the ELCL commands **RTVSCHLST** and **RTVCRFSTP** (messages ELC2501-ELC2504).
- **Creative Capacitor Bank**, in the creative menu only (it has no recipe). It is always full and has no transfer
  limit: it gives its network, and anything on any face, all the FE asked of it, and takes (voids) all it's offered.
  The network fills its controllers and other storage first, so it only voids what they can't take. Its screen shows
  Infinite, and its gauge glows round the colour wheel. It can't be broken in survival.
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

### Upgrading

- A Rack Console that had the Memory Die upgrade becomes a Rack Fabrication Console in the same unit when its rack
  loads, keeping its crafting grid. The Memory Die drops out of the rack, and the next player to open the console is
  told what happened. Nothing needs redoing.

### Changed

- The Rack Console no longer takes a Memory Die for a crafting grid: the Rack Fabrication Console replaces that
  upgrade.
- Compute, Memory and Fabrication Servers show their lights solid while they're online and idle, and blink only
  while they're working: a Compute Server while its pool runs jobs, a Memory Server while jobs hold memory, and a
  Fabrication Server while it crafts. They keep blinking for a second after work stops, so the lights don't flicker
  between jobs. The rack screen's elevation shows the same.
- Rack Consoles have to be at least 8U apart (7U free between them), so one's open screen can't clip through the
  drawer of the one above. This applies when installing one and in Rack Elevation plans, which skip a console that
  is too close and say which one is in the way. Other devices can still go between them. Racks that already have
  consoles closer than that keep them.
- The Server Rack screen's sneak-clicks follow the vanilla Sneak key instead of Shift: taking a device out,
  stepping a storage priority by 10, and moving a stack between slots. With Sneak on Shift (the default) either Shift
  still works. The screen's hint names the key Sneak is bound to. Sneak-using a rack in the world already followed it.
- **Rack Schedulers pool across racks.** Compute and Memory Servers on the same network now form one Scheduler
  however many racks they are spread over, with every server's threads and job memory added together. One rack heads
  the pool (the first, by position, with an online Compute Server serving the network) and holds its jobs; when the
  head changes, the jobs move with it. Fabrication Servers in any of the pool's racks are offered its crafts first.
  Before, each rack needed a Compute and a Memory Server of its own to be a Scheduler. The Compute and Memory Server
  panels show the pool.
- **Large jobs run on several threads.** A thread now keeps `threadRuns` crafts (8) in flight at once. A job with
  more crafts left takes more of its Scheduler's free threads, one at a time round the running jobs, like nodes in a
  cluster, and gives them back as it nears its end. Every queued job still gets a thread first, taking one back from
  the job with most if none is free. So a big job spreads across every Compute Server, Thread Unit or Midrange
  cabinet its Scheduler has, and a Scheduler's "Threads x / y" counts the threads its jobs use. Set `threadRuns` to 0
  for the old behaviour (one thread a job, any number of crafts at once).
- The mod's jar carries JLayer's licence (GNU LGPL 2.1) at `META-INF/licenses/jlayer-LGPL-2.1.txt`, and the mod list
  credits JLayer. The mod list links to the issue tracker on GitHub and the mod's CurseForge page.
- New Storage Drives of every type (item, fluid, pressurized and energy) stack to 64, as cells do in AE2. A drive
  that has been in a holder is one of a kind and stacks to 1, until it is empty again in your inventory: then it
  goes back to a new drive and stacks with the others.

### Fixed

- The game crashed with Graphics set to Fabulous when aiming at a Server Rack's devices through its open doors ("Render
  type lines_translucent does not have OIT pipelines set up"). The rack's unit outline, the facade preview, located
  devices and Small Wireless Bridge targets now draw their outlines with the line type vanilla uses on Fabulous.
- Display Panels had no back round their back plate, so you could see into them from behind, and their bezels
  overlapped at the corners and flickered. The back is closed and the bezels meet at corner pieces.
- PLCs had no back on their backplate, DIN rail, CPU or modules, so you could see through them from behind.
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

[Unreleased]: https://github.com/zagdrath/encoded-logistics/compare/v1.3.1+26.3...HEAD
[1.3.1]: https://github.com/zagdrath/encoded-logistics/compare/v1.3.0+26.3...v1.3.1+26.3
[1.3.0]: https://github.com/zagdrath/encoded-logistics/compare/v1.2.1+26.3...v1.3.0+26.3
[1.2.1]: https://github.com/zagdrath/encoded-logistics/compare/v1.2.0+26.3...v1.2.1+26.3
[1.2.0]: https://github.com/zagdrath/encoded-logistics/compare/v1.1.0+26.3...v1.2.0+26.3
[1.1.0]: https://github.com/zagdrath/encoded-logistics/compare/v1.0.1+26.3...v1.1.0+26.3
[1.0.1]: https://github.com/zagdrath/encoded-logistics/compare/v1.0.0...v1.0.1+26.3
[1.0.0]: https://github.com/zagdrath/encoded-logistics/commits/v1.0.0
