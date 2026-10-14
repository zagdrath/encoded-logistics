# Inventory: Server Racks, terminals and access, menus and screens

Scope: `rack/`, `rack/device/`, `rack/chassis/`, `terminal/`, `menu/`, `client/screen/`, `client/rack/`, `client/crt/` (briefly), `entity/`, the Server Rack, Terminal Desk and Swivel Chair blocks, the terminal parts, the Handheld Terminal, and the registries (`registry/ModMenuTypes`, `ModBlockEntityTypes`, `ModEntityTypes`, `ModCreativeTabs`, `ModSounds`) plus `assets/encodedlogistics/sounds.json`.
All paths below are relative to `src/main/java/net/zagdrath/encodedlogistics/` unless they start with `src/` or `docs/`.
Everything comes from the code (class header comments were checked against the method bodies). Config paths are in the common config (`Config.java`), and defaults are the code's defaults. Client options are in `encodedlogistics-client.toml` (`client/screen/TerminalSettings.java`).

---

## Features

### 1. Server Rack (multiblock)
Sources: `block/ServerRackBlock.java`, `blockentity/RackBlockEntity.java`, `rack/RackGeometry.java`, `rack/RackTargeting.java`, `rack/RackBreakGuard.java`, `client/rack/RackRenderer.java`, `client/rack/RackModels.java`, `client/screen/RackScreen.java`, `client/screen/RackElevationView.java`, `menu/RackMenu.java`.

- **Size:** a 42U enclosed rack, 1 wide x 3 tall x 2 deep (6 blocks). The master is the middle block of the front column. It holds the block entity (`RackBlockEntity`, BE type `server_rack`) and draws the whole frame. The other five blocks are invisible dummies with a `PART_INDEX` (0-5). Block state: `facing`, `part`, `index`.
- **Placing and breaking:** the targeted block becomes the bottom-front block, and the front faces the player. Placement fails with a message if any of the six positions isn't free. Breaking any block breaks all of them. The broken block drops the rack, and the master drops every device with its settings and contents.
- **Break guard** (`rack.rackBreakConfirm`, default true): you must sneak while breaking it, or left-click it twice within 20 ticks. A confirmed rack stays confirmed for 600 ticks. In creative the first break arms it and the second confirms. The first click shows the action-bar message "rack.break_confirm".
- **Break protection** (`rack.rackBreakProtection`, default true): breaking a rack also needs the Firewall's RACK permission (`rack/FirewallEvents.java`).
- **Doors:** right-click the front to open or close the front door, and the back to open or close both rear leaves. Sneak-right-click the front to open the rack's screen (`RackMenu` / `RackScreen`), with the door open or closed. Doors animate over 10 ticks (`DOOR_TICKS`) with smoothstep easing. The front leaf turns -110 degrees about (14.5, 0.5); the rear leaves turn +110 / -110 degrees about (14.5, 31.1) and (1.5, 31.1). Sounds: `block.rack.door_open` / `door_close`. A Rack Elevation installing through a closed front "visits" it: the door opens, then closes again after `DOOR_TICKS + 20`.
- **Culling:** with both doors shut, devices aren't drawn beyond `rack.rackCullDistance` (default 24 blocks).
- **Network connection points:** there are 7 (`RackGeometry.Point`): TOP_FRONT and TOP_REAR (up), BOTTOM_FRONT and BOTTOM_REAR (down), and REAR_TOP, REAR_MIDDLE and REAR_BOTTOM (the back face). The rack keeps the network it joined first. A cable from another network at a point is a "mismatch" and carries nothing.
- **Lanes:** the master is one network device. Each device uses `laneCost()` lanes (default 1). Switches pool rack-local lanes for the other devices (see the device catalogue). Rack Network Controllers use 0 lanes. The LaneSolver grants lanes by device priority. Each device has a lane priority of High, Normal or Low, cycled in its panel header and saved on its item. The default is Normal; Network Controllers, UPSes and switches default to High. Devices with Low priority are shed first when the rack is short of lanes.
- **Power:**
  - Each device drains its own FE/t from the network (see the catalogue).
  - Any mod's FE cable can feed a connection point. That FE goes into the network's energy, as a Power Inlet's does, up to `rack.rackPowerMaxInput` FE/t across all points. The default is -1, which means "same as `power.inletMaxInput`" (16,384).
- **Segments / VLANs:** a rack can link up to 5 segments (`MAX_SEGMENTS`) beyond its own network. These are networks beyond Segment Isolators, linked by a Link Card used on a switch's unit. Segment IDs run from 1 (the rack's own network) to 4094. A pooled device can be set to serve another segment, as an access port (one segment) or a trunk (allowed segments plus a native one). Trunk Ports elsewhere on the network can also carry segments to the rack.
- **Screen** (`RackScreen`, menu type `server_rack`):
  - The elevation shows U42 at the top and U1 at the bottom, 16 units in view, and scrolls. Each device is drawn with its real front texture. The player's inventory is below.
  - Click a device to open its settings panel in the top half (with a back button). Sneak-click a device to take it out into your inventory.
  - Click a free unit while holding a device to mount it there. The drop target is amber if it fits and red if not. Shift-clicking a device from the inventory puts it at the lowest unit that fits (`lowestFit`).
  - Devices with item slots show them as real slots while picked.
- **Installing by hand:** with the front door open, use a rack device item on the rack to mount it at the unit you're looking at (`RackTargeting` follows the line of sight in to the device faces at z 1.75 / 29.25). Any other item is offered first to the device at that unit (`RackDevice#useItem`), then to the other devices (for example, a Handheld Terminal used anywhere on the rack links to its Wireless Controller).
- **Rack Consoles** must be at least 8U apart (`RackConsoleDevice.SPACING = 8`, which leaves 7U free between them). This is checked when installing and by Rack Elevation plans.
- **Unit HUD popup and outline** (`client/rack/RackHud.java`, `screens/rack/hud.json`):
  - When the crosshair is on a rack face whose door is open, the device (or empty unit) you're looking at is outlined instead of the block. On a modular chassis, the part you're looking at is outlined.
  - A popup near the crosshair (offset +12, -8; 96 to 140 px wide; text wraps) shows the device's icon, name, units, a status dot and text, and its own info lines (`RackDeviceInfo`). Info lines can have bars, and a Scheduler badge is shown when the device is in the rack Scheduler.
  - The popup's data is asked of the server when the crosshair lands on a unit, then every 10 ticks, and is cached per unit.
  - Above the popup is the rack's header strip: lanes over working uplinks, and the uplink count. It turns amber "Degraded" while a device is shed, an uplink is down or a point is mismatched.
  - An empty unit just says it's empty. Nothing shows with the doors closed, with F1, or while a screen is open.
  - Jade's tooltip is hidden for the rack (`compat/jade/EncodedLogisticsJadePlugin.java`).
- **Device states:**
  - Status is OFFLINE, ONLINE, WARNING or FAULT. WARNING is amber, for example a controller failing over or a UPS on battery.
  - Models are `models/block/rack_device/<id>.json`, `<id>_on.json`, `<id>_fault.json`, plus variant looks (`_idle`, `_standby`, `_failover`, `_conflict`, `_battery`, `_battery_low`, `_degraded`).
  - Textures are 128 texels across. They are 128 tall for devices up to 4U, and 256 tall for 5U, 6U and 10U devices. A device can be 1 to 10U (`RackDeviceType.MAX_SIZE = 10`).
- **Settings travel with the item:** removing a device gives an item with the `rack_device_state` component: its settings, a priority other than its default, and its device name (`RackDeviceItem`).
- **Clients:** door changes sync at once. Device changes sync at most every 10 ticks (`SYNC_INTERVAL`), and only when something the client shows changed.

### 2. Rack security (Firewall, NetworkAccess)
Sources: `rack/device/FirewallDevice.java`, `rack/RackPermission.java`, `rack/NetworkAccess.java`, `rack/FirewallEvents.java`, `item/LinkCardItem.java`, `rack/chassis/Chassis.java`.

- **Six permissions per player** (`RackPermission`):
  - VIEW, INSERT, EXTRACT, CRAFT: see, put in, take out, and ask for crafts.
  - BUILD: place, break and configure network blocks and parts, including mounting parts, anchors and facades on cables.
  - RACK: open the network's racks (doors and screen) and install, remove and set up devices.
  - Each permission is ON, OFF or INHERIT. INHERIT follows the default policy: DENY ("No access"), VIEW ("View only") or FULL ("Full access"; the default for a new Firewall). RACK on INHERIT follows the player's BUILD.
- **Owner:** whoever installed the Firewall always has every permission. Players who may BUILD (or gamemaster operators) may edit its rules. A Firewall lists at most 64 players.
- **One Firewall per network:** any other shows FAULT "Conflicting firewall" and is ignored (checked every 20 ticks).
- **Where it applies:** a network without a Firewall lets everyone do everything. Operators (gamemaster permission level) are never stopped. A block on no network is open to everyone. An offline Firewall still enforces its rules while `rack.firewallFailClosed` is true (the default).
- **BUILD is checked** when breaking or placing a network block on or next to a network, and when using a cable anchor, facade or part on a cable (`FirewallEvents`).
- **Rack access denied:** the rack's latch rattles (`SoundEvents.CHEST_LOCKED`) and the player is told "Access denied: SYSNAME Firewall" (`NetworkAccess.rack` / `guard`).
- **Link Card:** writing it needs BUILD at the clicked end (RACK for a device in a rack). Applying it needs BUILD at both ends.
- **Chassis parts:** putting parts in or taking them out needs RACK; changing their settings needs BUILD; looking needs VIEW.
- **Rack Elevation:** needs RACK. Using network storage as a source also needs EXTRACT.
- **No physical locks:** the rack has no lock or key item. "Locking" is purely the Firewall's RACK permission.
- **Firewall panel** (`client/screen/FirewallPanel.java`): the default policy (click to cycle, right-click to go back); the player list with all six permission toggles (inherit -> on -> off); right-click a row to remove a player; a name field to add an online player (Tab completes). The owner heads the list. Players who can't edit see it dimmed. (The class comment says "five permissions", but the code loops over all six.)
- **Firewall popup lines:** Default policy; Custom players (players with any non-INHERIT permission).
- **SECLVL** (Terminal OS) doesn't change any of this (comment in `NetworkAccess`).

### 3. Rack Scheduler (Compute / Memory Servers, blades)
Source: `rack/RackScheduler.java`.
- Every online Compute and Memory Server serving a network, in any of its racks, plus a Blade Enclosure's Compute and Memory Blades, pools into one Scheduler for that network. Each Compute Server gives `rack.computeServerThreads` threads (2) and each Memory Server gives `rack.memoryServerMemory` job memory (32,768).
- **The head rack:** one rack heads the pool: the first rack (in `ControllerStructures.allRacks` order) whose lowest online Compute Server serves the network. Only the head's Scheduler is active. It holds the jobs and is listed among the network's Schedulers as "Rack x, y, z". When the head moves, its jobs move with it. Breaking the head rack fails its jobs.
- **Crafting order:** crafting steps are offered first to the pool's Fabrication Servers and Fabrication Blades, then to the network's other providers.
- **Server panel** (`client/screen/ServerPanel.java`): what the server provides, what the pool uses of it, whether the Scheduler is active, a usage bar, and the Scheduler's jobs with a cancel X (`JobCancelPayload`), followed by recent jobs.
- **Server lights:** a server blinks while the pool uses it and shows its `_idle` look while it's online and idle. Busy lingers for 20 ticks (`LINGER`).

### 4. Rack Elevation (rack templates)
Sources: `item/RackElevationItem.java`, `rack/RackTemplate.java`, `rack/RackElevations.java`, `client/screen/RackElevationScreen.java`, `client/screen/RackElevationMaterialsScreen.java`, `client/rack/RackElevationPreview.java`.

- **The item:** a clipboard with a rack plan (the `rack_template` component). Blank ones stack to 16; written ones stack to 1. A plan name is at most 32 characters.
- **Record:** sneak-use it on a rack (needs RACK). It records every device's unit, type and size. With the Contents toggle on it also records slot items, without drive/tape ids, fill or charge. With the Settings toggle on it also records each device's settings, including priority and device name.
- **Apply:** use it on a rack (needs RACK). There is one mode, `FILL_EMPTY`. Devices are placed from U1 up:
  - free units get the device installed, plus its contents and settings if those toggles are on;
  - the same device already at that unit is left in place, with its planned contents put into empty slots and its settings applied if toggled;
  - anything else is skipped. Nothing is ever removed or replaced.
  - Items come from the player's inventory, then shulker boxes in it, then network storage if the "Network storage" toggle is on and the player has EXTRACT.
  - In creative, nothing is used up while `rack.rackElevationCreativeFree` is true (the default).
  - The report goes to the action bar and to chat, with a [show] link to the materials list.
- **Editor:** use it in the air to open `RackElevationScreen`. The rack elevation shows planned devices as 55 % ghosts. A searchable device list lets you drag devices onto units (JEI drags work too). The side panel has the Contents, Settings and Network storage toggles, and Materials with how many items are missing. Each change is sent to the server as an Edit and checked.
- **Materials list** (`RackElevationMaterialsScreen`): have/need per device and per planned content, green when there's enough and red when short.
- **Preview:** hold a written Rack Elevation and look at a rack to see ghost quads over the rack front. Green means it will be installed or is already there, amber means skipped, and red means the unit is free but the item isn't available. The popup reads "Rack Elevation <name>" with Fill / Skip / Missing counts. It is queried every 10 ticks.
- **Plan rules:** a known type, its real size, inside the 42 units, no overlaps, and consoles 8U apart.
- **Recipes:** `rack_elevation`, `rack_elevation_copy` (copy onto a blank one), `rack_elevation_clear` (craft it alone to clear it).

### 5. Terminal Desk, Swivel Chair, seat entity
Sources: `block/TerminalDeskBlock.java`, `blockentity/TerminalDeskBlockEntity.java`, `block/SwivelChairBlock.java`, `blockentity/SwivelChairBlockEntity.java`, `entity/SeatEntity.java`, `client/DeskSounds.java`, `menu/TerminalDeskMenu.java`, `client/crt/CrtScreen.java`, `terminal/*`.

- **Terminal Desk:** 2 wide x 1 deep. The master is the left half seen from the front (the CRT and keyboard); the dummy is the right half (the pedestal). Block state: `facing`, `part`, `screen` (off/boot/on), `clutter`.
  - It's a network device on the master. Cables connect on any side but the front. It uses 1 lane.
  - Drain: `rack.terminalDeskDrain` (1.0 FE/t), plus `rack.terminalDeskScreenDrain` (2.0) while the screen is on.
  - The screen is off while the desk is offline. Coming online it boots for 60 ticks (`BOOT_TICKS`) with a degauss sound, then stays on and the CRT hums.
- **Using the desk:**
  - Use the master: the green-screen Terminal OS (`CrtScreen`, menu type `terminal_desk`), which is open while the desk is there and the player is near it.
  - Use the pedestal: the drawer, a 9-slot plain 3x3 menu where `*DRAWER` withdrawals go. Hoppers can take from it.
  - Sneak-use either half with an empty hand to toggle the clutter (purely cosmetic).
- **Desk behaviour:** withdrawals (to the drawer or the player's inventory, with cold items recalled from tape first), crafts (optionally delivered to the drawer or inventory once done), job cancel, plans and job lists (`terminal/TerminalActions.java`). The command line runs each line as an ELCL command in the user's interactive job (`terminal/TerminalCommands.java`). Tab completes command words and item names. Items can be named by registry id or display name; amounts take k/M suffixes (`terminal/TerminalItems.java`).
- **Server queries** (`terminal/TerminalService.java`): info, jobs/job, devices/device, locate, hardware (WRKHW), hwpart, locatehw, optical (DSPOPT), opticalchannels, status (DSPNETSTS), detail, plan, plus the Terminal OS screen queries.
- **CRT screen** (`client/crt/*`, brief):
  - An 80x24 text terminal in the GUI-kit frame. Every screen has an id, title and system name, the date and time, its body, a prompt, the `===> ` command line, a message line and function keys.
  - Keys: Tab / Shift+Tab, Up / Down, Enter, PageUp / PageDown, F3 exits, F4 prompts, F5 refreshes, F9 opens Command Entry, F11 sorts, F12 or Esc goes back, Shift+F12 (F24) shows more keys and the phosphor. Function-key labels can be clicked.
  - Phosphor: green, amber or white (`screens/crt/phosphor.json`). The client option `phosphor` defaults to `*SYSVAL`, the system's PHOSPHOR value.
  - Command Entry keeps 500 lines. Many panels exist (MAIN, WRKINV, WRKJOB, WRKDEV, DSPNETSTS, WRKHW, DSPOPT, SIGNON, editors and more); the Terminal OS agent should cover these.
  - An Integrated Midrange System's console opens the same session (`TerminalDeskMenu`).
- **Desk sounds** (`client/DeskSounds.java`): the CRT hum loops while the screen is lit; keys clack as the player types (at most once every 2 ticks); the degauss plays on boot (`TerminalDeskBlockEntity`).
- **Swivel Chair:**
  - Its seat and back turn with the chair's yaw (drawn by the BE renderer). It's placed facing the way the placer looks, at any angle.
  - Right-click to sit on a `SeatEntity` (entity type `encodedlogistics:seat`, 0.01x0.01, not summonable, tracking range 8). The rider's hips sit 9/16 above the block bottom. The chair turns with its sitter. Sneak to get up, which puts you a block in front of the seat. One sitter at a time.
  - The seat entity goes when nobody is on it or the chair is gone; a sitter stands up as soon as the chair is broken.
  - Dyeable in a crafting grid (`dyed_color`; recipe `swivel_chair_dyed`). Undyed, it's the brown tweed. The dye is kept when it's broken.

### 6. Block terminals (cable parts) and the Part Host
Sources: `part/TerminalPart.java`, `part/FabricationTerminalPart.java`, `part/SchematicTerminalPart.java`, `part/SchematicEncoderPart.java`, `block/PartHostBlock.java`, `menu/AccessTerminalMenu.java`, `menu/FabricationTerminalMenu.java`, `menu/SchematicTerminalMenu.java`, `client/screen/AbstractTerminalScreen.java` and subclasses.

- **No standalone terminal blocks:** the terminals are cable parts (`PartType.ACCESS_TERMINAL`, `FABRICATION_TERMINAL`, `SCHEMATIC_TERMINAL`, `SCHEMATIC_ENCODER`). They're mounted on a cable face, or on any block face through the invisible Part Host block (`part_host`, sharing the `cable` BE type). A Part Host is on that block's network when the block is a network block; otherwise the part is offline. The only "block" terminal is the Terminal Desk.
- **Access Terminal:** the network's items in a grid, plus the player's inventory. The grid isn't real slots: items sync every SYNC_INTERVAL ticks, and clicks go through `TerminalClickPayload`. Items on tape show with the rest, counted together and marked cold. Taking more of an item than is hot takes the hot items and recalls the rest, with progress shown.
  - Drain: `storage.terminalDrain` (0.5 FE/t). Lanes: `storage.terminalLanes` (1).
  - It's lit while online.
- **Terminal screen kit** (`AbstractTerminalScreen`): a search field (standard, or synced both ways with JEI's search); sort by name, amount or mod, ascending or descending; craftables shown always or only when searching; grid height small, medium, tall or fill; a type tab. These are saved in the client config (`TerminalSettings`: `sortMode`, `descending`, `craftablesAlways`, `height`, `typeTab`, `searchSynced`).
- **Fabrication Terminal:** the Access Terminal plus a 3x3 crafting grid of real items, kept with the part and dropped with it. Taking the result crafts, and grid slots that run out are refilled from the network. Shift-click crafts as many as fit. A clear button returns the grid to the network. JEI fills the grid from the network, then the player.
- **Schematic Terminal:** a Schematics tab (every schematic holder on the network, with badges) and a Jobs tab, with a 12 px summary strip. Data comes as `SchematicData` snapshots; clicks go through `SchematicTerminalActionPayload`. It's lit while online.
- **Schematic Encoder:** the item grid plus an encoding section. In crafting mode it has a 3x3 ghost grid and the result. In processing mode it has ghost outputs with amounts (1-64). It also has a blank-card slot and an encoded-card slot.

### 7. Handheld Terminal
Sources: `item/HandheldTerminalItem.java`, `item/HandheldLinkState.java`, `menu/HandheldTerminalMenu.java`, `client/screen/HandheldTerminalScreen.java`, `rack/device/WirelessControllerDevice.java`.

- **Linking:**
  - Use it on a Relay Antenna or a (block) Network Controller to link it to that network.
  - Or use it on a Wireless Controller's unit (front door open) or anywhere on that controller's rack. It then works anywhere: the whole dimension, and other dimensions while `rack.wirelessCrossDimension` is true (the default). While `rack.wirelessAnyController` is true (the default), any online Wireless Controller on the network serves it; otherwise only the one it was linked to.
  - Use it in the air to open the terminal. It must be linked, in range and charged.
- **Range:** without a Wireless Controller link, it reaches through the nearest of its network's Relay Antennas that covers the player, re-picked every 10 ticks. A Relay Antenna covers a sphere of `reach.relayBaseRange` (32) plus `reach.relayRangePerTransceiver` (32) per Optical Transceiver, with 4 transceiver slots, so up to 160 blocks. Signal bars run 0-4, from four near the antenna to one at the edge of its range. Through a Wireless Controller it has full signal.
- **Battery:** `reach.handheldCapacity` 200,000 FE. It uses `reach.handheldDrainPerSecond` (10 FE/s) while the screen is open, plus `reach.handheldEnergyPerItem` (1 FE) per item moved. It charges in any FE charger, or held against a Capacitor Bank (use and hold) at `reach.handheldChargeRate` (4,096 FE/t).
- **Icon:** shows the link state (NOT_LINKED, LINKED, OUT_OF_RANGE; `HANDHELD_LINK_STATE` component, updated every second while in an inventory).
- **Screen:** the terminal kit plus a side panel with the battery bar, a link light and signal bars. While offline the grid says why.

### 8. Link Card (rack-related uses), Cross-Connect
Source: `item/LinkCardItem.java`, `item/LinkAddress.java`, `item/CrossConnectItem.java`.
- **Segments:** sneak-use the card on a Segment Isolator half to store that segment, then use it on a switch's unit to link the segment to the rack. Using it again unlinks.
- **Routers:** sneak-use it on any block of a network to store that network, then use it on a Router's unit to link the Router to it.
- **Wireless:** use it on a Wireless Controller's unit to take the controller, then on a Wireless Bridge or Wireless Port to make it the controller's client.
- **Optical:** sneak-use it on an Optical Multiplexer or Transport Shelf unit to store it, then use it on another of the same kind to pair them. A stored network used on a multiplexer gives it a channel; on a shelf it goes to the Transponder Card you're looking at.
- Sneak-use in the air clears the card. Blank cards stack; written ones don't.
- **Cross-Connect:** used on a Router's unit, it records the Router (needs BUILD). Then used on an Exchange Switch, it joins the Router there (or asks to), using up one Cross-Connect.

### 9. Device names (ELCL) - see the table at the end
Source: `elcl/exec/ElclDevices.java`.
- **Naming:** a device is named the first time it's on a network: its type code plus the lowest free two-digit number (`%s%02d`), for example UPS01.
- **Order:** names are given rack by rack, each rack from its lowest unit up.
- **Storage:** the name is stored with the device (a rack device's goes with its item; a block's goes with its block item via `DEVICE_NAME`). The system remembers which device holds each name. A device arriving from another network keeps its name if it's free there; otherwise it gets the next number.
- **Renaming:** RNMDEV, or Work with Devices option 2. A name must match `[A-Z][A-Z0-9_@#$]{0,9}`. Errors: ELC1301 (no such device), ELC0103 (invalid name), ELC1308 (a duplicate).

---

## Rack device catalogue

Every `RackDeviceType` (`rack/RackDeviceType.java`), in registration order. "Drain" is FE/t while its network runs. "Prefix" is the ELCL type code from `ElclDevices.code(RackDevice)`. Lanes: 1 unless noted. If a switch in the rack pools a device, the device uses 0 network lanes.

| Type id (item) | Name | U | What it does | Drain (config path = default) | Prefix | Panel / extras |
|---|---|---|---|---|---|---|
| `firewall` | Firewall | 1 | Per-player network permissions (see Features 2). One per network; extras show FAULT "Conflicting firewall". | `rack.firewallDrain` = 2.0 | FIREWALL | FirewallPanel |
| `router` | Router | 1 | WAN edge: moves items between separate networks (any distance or dimension) linked by Link Card. Up to 6 networks (`MAX_NETWORKS`, its own included) and 6 routes. Each route has a filter (`ItemRouting`), Move or Share (Read or Read/Write, crafting use, one-way or bidirectional). 3 Optical Transceiver cages. Rate: `rack.routerBaseRate` 16 items/s + `rack.routerRatePerTransceiver` 16 each, shared round-robin. STATIC or DYNAMIC routing (link-state-like, `rack.routingBaseCost` 100, `rack.reconvergeSeconds` 5, `rack.maxHops` 16); transit NONE / LISTED (up to 16) / ALL. ACLs (`RouterAcl`; `rack.aclDefaultPermit` true). Exchange Switch membership via Cross-Connect. | `rack.routerDrain` 4.0 + `rack.routerDrainPerTransceiver` 1.0 per transceiver | ROUTER | RouterPanel (Routes / Mode / Table / ACLs tabs); render extra: transceivers in cages |
| `ups` | UPS | 2 | Battery for its network: covers a supply shortfall in the same tick, before the network's buffers. ONLINE mode always conditions the supply; STANDBY only steps in once supply has failed altogether. Several UPSes discharge together and recharge in parallel from the top half of the buffers. Overload beyond the rated output is a FAULT (held 20 ticks). On battery: amber LED (blinking below `rack.upsLowBatteryPercent` 20 %), and the LCD scrolls "ON BATTERY - runtime". Alarm: 4 beeps every 30 s (600 t), a rapid beep every 40 t when low, one beep when power returns. Muting in its panel lasts for this outage only. Logs the last 3 switchovers. 10 load LEDs. Default priority High. | `rack.upsDrainOnline` 2.0 / `rack.upsDrainStandby` 1.0. Capacity `rack.upsCapacity` 1,000,000 FE; output `rack.upsMaxOutput` 8,192 FE/t; recharge `rack.upsMaxInput` 8,192 FE/t; `rack.upsAlarms` true (server), `upsAlarmSounds` true (client) | UPS | UpsPanel; render extra: LCD % and load LEDs; variants `_battery`, `_battery_low` |
| `l2_switch_24` | 24-Port L2 Switch | 1 | Rack-local lane pool for the other devices in its rack (unit order until full), so the network only carries its 1-lane uplink. Pooled devices can serve other segments as access or trunk ports. Default priority High. | `rack.switchDrain` 2.0; pool `rack.l2Switch24Lanes` 16 | SWITCH | SwitchPanel (devices, Ports view) |
| `l2_switch_48` | 48-Port L2 Switch | 1 | As above. | `rack.switchDrain` 2.0; pool `rack.l2Switch48Lanes` 32 | SWITCH | SwitchPanel |
| `l3_switch` | L3 Switch | 1 | An L2 pool (`rack.l3SwitchLanes` 32) plus routing between the segments its rack knows. Up to 8 routes (Move or Share) and 8 QoS rules (`QosRule(filter, level)` marking items High / Normal / Low; each route moves high, then normal, then low). Rate `rack.l3SwitchRate` 32 items/s. | `rack.l3SwitchDrain` 3.0 | L3SWITCH | L3SwitchPanel (Devices / Routes / QoS) |
| `compute_server` | Compute Server | 2 | Gives `rack.computeServerThreads` (2) crafting threads to the network's rack Scheduler. Also runs `elcl.computeServerJobs` (4) ELCL batch jobs at once. | `rack.computeServerDrain` 6.0 | CMPSRV | ServerPanel; `_idle` look |
| `memory_server` | Memory Server | 1 | Gives `rack.memoryServerMemory` (32,768) job memory to the rack Scheduler. | `rack.memoryServerDrain` 3.0 | MEMSRV | ServerPanel; `_idle` |
| `fabrication_server` | Fabrication Server | 2 | A rack Fabricator: 9 Crafting Schematic slots and 2 Throughput Module slots. Offered crafts by any Scheduler on its network, the rack Scheduler first. Runs as many crafts side by side as the jobs' threads allow. Each craft costs `autocrafting.fabricatorEnergyPerCraft` 50 FE and takes `autocrafting.fabricatorCraftTicks` [20, 10, 5] ticks for 0/1/2 modules (paused while offline). | `rack.fabricationServerDrain` 4.0 | FABSRV | FabricationServerPanel; `_idle` |
| `directory_server` (item `directory_server_rack`) | Directory Server (Rack) | 1 | The block's work in a rack: publishes chosen devices so other networks reach them as `SYSNAME/DEVICE`, and logs lookups. Cache: `rack.directoryCacheSeconds` 30. | `rack.directoryDrain` 2.0 | DIR | BodyPanel.directory (DirectoryPanel: Published / Remote / Lookups) |
| `monitoring_server` | Monitoring Server | 1 | Samples its network once a second: item flow (items/min), energy (FE/t), lanes, crafting jobs/min. Keeps 600 one-second samples (1m/10m views), 360 at 10 s (1 h) and 720 at 2 min (1 d), saved with the device. Also keeps unsaved per-machine series (`*MCHOPS`, `*MCHFE` + machine name) for Display Panel graphs. | `rack.monitoringServerDrain` 2.0 | MONSRV | MonitoringPanel |
| `nas` | NAS | 2 | 6 Storage Drives (tag `#encodedlogistics:storage_drives`) as network storage, with priority -999..999 and an access mode (read-write / read-only / write-only). | `rack.nasDrain` 2.0 + `rack.rackDriveDrain` 0.5 per drive | NAS | StoragePanel; render extra: drive sleds and fill LEDs |
| `san` | SAN | 4 | 24 Storage Drives plus 4 rear Optical Transceiver uplink cages. With no transceiver it shows FAULT "Offline - no uplink" and its drives can't be reached. Each transceiver beyond the first adds +1 storage priority. | `rack.sanDrain` 4.0 + `rack.rackDriveDrain` 0.5 per drive | SAN | StoragePanel; render extra: sleds, LEDs, cages |
| `rack_console` | Rack Console | 1 | An Access Terminal in a drawer. Right-click it through the open front door: the drawer slides out (8 ticks), then the screen folds up (6 ticks). Right-click it again while open, with its network online, for the terminal. Sneak-right-click, closing the door, or nobody within 4 blocks folds it away. Consoles must be 8U apart. | `rack.rackConsoleDrain` 1.0 + `rack.rackConsoleOpenDrain` 2.0 while open | RACKCON | no panel; render extra RackConsoleRender (drawer, keyboard, lid) |
| `rack_fabrication_console` | Rack Fabrication Console | 1 | As above, with the Fabrication Terminal's screen. Its 3x3 grid is kept in the console. Consoles saved with the old Memory Die upgrade become this when their rack loads (the die drops out, and the next player to open it is told). | as Rack Console | RACKFAB | no panel; RackConsoleRender |
| `rack_schematic_console` | Rack Schematic Console | 1 | As above, with the Schematic Terminal's screen. | as Rack Console | RACKSCH | no panel; RackConsoleRender |
| `wireless_controller` | Wireless Controller | 1 | The network's wireless brain, with infinite range (see the Handheld Terminal). A Link Card taken from it links Wireless Bridges, Wireless Ports and Small Wireless Bridges as its clients. Clients only work while the network has Access Points online, each giving `rack.wirelessApClients` (8) slots; clients past the slots wait. Each admitted client is a remote link from its rack: a bridge carries `rack.wirelessBridgeLanes` (32) lanes, a port or Small Bridge 1. FAULT when it has clients and no Access Point is online. | `rack.wirelessControllerDrain` 4.0 | WLC | WirelessControllerPanel (APs / Linked) |
| `tape_library_4u` | 4U Tape Library | 4 | 24 LTO tape slots and 2 LTO Tape Drive bays: the network's cold tier (see LTO tapes below). | `rack.tapeLibrary4uDrain` 3.0 + `rack.tapeDriveBusyDrain` 8.0 per busy drive + `rack.tapePickerDrain` 4.0 while busy | TAPELIB | TapeLibraryPanel (Magazine / Policy); render extra TapeLibraryRender |
| `tape_library_6u` | 6U Tape Library | 6 | 48 tape slots and 4 drive bays. | `rack.tapeLibrary6uDrain` 4.0 + same extras | TAPELIB | as 4U |
| `network_controller_2u` | 2U Network Controller | 2 | The network core in rack form: a lane source and the network's energy buffer. Equal to `rack.rackController2uEnergyBlocks` (20) controller blocks for energy, receive rate and drain: 20 x `controller.energyPerBlock` 25,000 = 500,000 FE; 20 x 4,096 = 81,920 FE/t receive; 20 x 2.0 = 40 FE/t drain. Lanes: `rack.rackController2uLaneFaces` (6) x `lanes.perControllerFace` (32) = 192. Two of the same size on one network are a redundant pair (active and standby); the standby draws `rack.rackControllerStandbyFactor` (0.1) of its drain and takes over within 20 ticks (`FAILOVER_TICKS`). Its panel has "Switch over". 0 lanes. Default priority High. | 40 active / 4 standby (defaults) | NETWORK_CONTROLLER_2U (default branch; see Open questions) | NetworkControllerPanel; variants `_standby`, `_failover`, `_conflict` |
| `network_controller_4u` | 4U Network Controller | 4 | Twice the 2U: 1,000,000 FE, 163,840 FE/t receive, 80 FE/t drain, 384 lanes. | 80 / 8 | NETWORK_CONTROLLER_4U | as 2U |
| `optical_multiplexer` (item `optical_multiplexer_rack`) | Optical Multiplexer (Rack) | 1 | The block's work in a rack. Its line is a Fiber Cable leaving the rack at any connection point and leading to its partner. Carries `optical.multiplexerChannels` (8) client networks. FAULT when paired but the line is down. | `optical.multiplexerDrain` 4.0 | MUX | BodyPanel.opticalMux (OpticalMuxPanel: Channels / Line) |
| `optical_amplifier` (item `optical_amplifier_rack`) | Optical Amplifier (Rack) | 1 | Patched in, it regenerates optical lines that pass through its rack between two connection points (the span count restarts). Needs Fiber Cable at 2 or more of the rack's points; with fewer it shows FAULT "input loss of signal". Unpowered, the rack passes no line through. | `optical.amplifierDrain` 1.0 | AMP | no panel |
| `optical_transport_shelf` | Optical Transport Shelf | 6 | Carrier transport between two shelves paired by Link Card (same dimension, different racks). 10 bays: 2 Controller Cards (C1, C2) and 8 line cards (L1-L8). Channels: 8 per Mux Card (1 without one), up to `optical.shelfChannels` 40; transponder channel 1-40. 1+1 protection with a Protection Card (see Chassis parts). Performance data: length, loss, lanes, quality (100 % minus 4 % per lane lost, 0 when down). Runs with a controller card. Shows `_degraded` while short of redundancy. | `optical.chassisDrain` 10.0 + `optical.chassisCardDrain` 2.0 per part | OTS | ChassisPanel (Slots / Channels / Spans); ChassisRender |
| `chassis_switch` | Chassis Switch | 10 | An L3 Switch built from cards: 2 supervisors (A, B; the standby takes over in the same tick, `*SUPFAILOVER`), 8 line cards, 4 PSUs. Runs with a supervisor and a PSU; degraded below 2 PSUs or 2 supervisors. Lane pool = Access Cards x `optical.accessCardLanes` (48), only while running with at least one uplink transceiver. Routing rate = Routing Cards x `optical.routingCardRate` (128 items/s). A port can only be set to trunk mode with a Trunk Card installed. | `optical.chassisDrain` 10.0 + `optical.chassisCardDrain` 2.0 per part | CSW | ChassisPanel (Slots / Cages / Switch); ChassisRender |
| `blade_enclosure` | Blade Enclosure | 10 | 16 front blade bays (F1-F16). Rear: 2 Interconnect Modules (R1, R2), 2 Management Modules (M1, M2) and 6 PSUs (P1-P6). Runs with at least one interconnect, management module and PSU; degraded below 2 interconnects or 2 PSUs. Blades take no rack lanes: the interconnect gives `optical.interconnectLanes` (64). It is also network storage (Storage Blades) and a crafting provider (Fabrication Blades, 9 schematic slots). | `optical.chassisDrain` 10.0 + `optical.chassisCardDrain` 2.0 per part, plus drive drain | BLD | ChassisPanel (Front / Rear / Drives / Fab); ChassisRender |

Notes:
- **Panels and render extras** are registered in `client/rack/RackClientDevices.java`. Devices with no panel (the three consoles and the rack Optical Amplifier) show only their name and status when picked.
- **Lit overlays** animate with 4 frames (16 for the Network Controllers). The Firewall, Router, UPS and the three chassis have 1 frame.
- **Tags:** `#encodedlogistics:storage_drives` (NAS/SAN/blade drive slots) and `#encodedlogistics:lto_tapes` (tape slots) are used in code. `#encodedlogistics:rack_devices` exists as JSON only (see Open questions).

### LTO tapes, the LTO Tape Drive and tiering
Sources: `storage/TapeGeneration.java`, `item/LtoTapeItem.java`, `rack/device/TapeLibraryDevice.java`, `rack/TapeTier.java`, `rack/TapeRecalls.java`, `rack/TapePicker.java`, `rack/TapeSource.java`, `storage/DriveCapacity.java`.

| Item id | Label | Capacity (bytes) | Read/write speed factor |
|---|---|---|---|
| `lto_tape_6` | LTO-6 | 8 M (8 x 2^20) | 1.0 |
| `lto_tape_7` | LTO-7 | 32 M | 0.85 |
| `lto_tape_8` | LTO-8 | 128 M | 0.7225 |
| `lto_tape_9` | LTO-9 | 512 M | 0.614 |
| `lto_tape_10` | LTO-10 | 2 G (2 x 2^30) | 0.522 |

- **Byte model** (the same as Storage Drives): 8 items per byte. Each stored type reserves bytes/128. At most `storage.driveTypeLimit` (63) types.
- **Tape items:** a tape keeps its contents in `DriveStorage` under its `drive_id`, given the first time it goes into a library. New tapes stack to 64 and copy freely; a tape with an id stacks to 1; an emptied tape goes back to blank in a player's inventory.
- **Upgrading tapes:** recipes of type `encodedlogistics:tape_upgrade` consume the previous generation (for example LTO-6 + 2 ferrite + logic die -> LTO-7).
- **LTO Tape Drive** (`lto_tape_drive`, stacks to 16): goes in a library's drive bay. A library without a drive still has its tapes on the cold tier, but can't read or write them.
- **Cold tier:** the tapes in online Tape Libraries plus Tape Reels on Midrange Tape Drives.
- **Archiving:** every `rack.tapeArchiveInterval` (200) ticks, if the library has an idle drive, it archives item types untouched for longer than its age setting. The default age is `rack.tapeDefaultAgeHours` (2 h), set in minutes, hours or days, 1-9,999. Items matching the keep-hot filter (9 entries, fuzzy allowed) or pinned (9 items) aren't archived. If the free-space trigger is on (the default), it only archives while hot storage is fuller than `rack.tapeDefaultHotPercent` (80 %). It archives oldest first, one type per drive. Of several libraries on a network, the one with the most free tape space archives. A type goes on the tape already holding it, otherwise on the fullest tape with room. CHGITMTIER *COLD forces an item to archive next.
- **Recalls:** the oldest waiting recall for an item on the library's tapes, one tape per drive. Pinned items found on tape are recalled automatically. If hot storage fills during a recall, the item is marked "hot storage full".
- **Timing:** `rack.tapeBaseTicks` (40) + `rack.tapeTicksPer4k` (20) per 4,096 items, times the generation's speed factor. The picker moves: the rail at 3 texels/tick and the head at 4, with a 4-tick grab and a 4-tick insert. The server and client both time it from `TapePicker`.
- **Sounds:** `block.rack.picker_move` and `block.rack.tape_load` at each picker leg.

---

## Chassis parts

All are `ChassisPartItem`s (`rack/chassis/ChassisPart.java`), each fitting one kind of bay (`ChassisLayout`). A part's faceplate is drawn in its bay from `models/block/rack_card/<id>.json` (with `_on` while it works). Parts are hot-swappable. They go in through the open rack front (used on the bay), by clicking a bay in the chassis panel, or by shift-clicking into the first bay that takes them. Sneak-click in the panel takes a part out. Parts with settings keep them on the item. Each bay has a WRKHW status, a light and its own popup. Each part adds `optical.chassisCardDrain` (2.0 FE/t).

| Part (item id) | Name | Bay kind | Chassis / bays | What it does |
|---|---|---|---|---|
| `supervisor_card` | Supervisor Card | SUPERVISOR | Chassis Switch A, B (top row) | Runs the switch. The standby takes over in the same tick if the active one fails or is removed (`*SUPFAILOVER`). |
| `access_card` | Access Card | LINE | Chassis Switch L1-L8 | `optical.accessCardLanes` (48) rack-local lanes, like an L2 Switch pool. |
| `uplink_card` | Uplink Card | LINE | Chassis Switch L1-L8 | 4 Optical Transceiver cages (Cages tab). The pool's uplinks, aggregated: any one keeps the pool up. With no transceiver in any cage, the pool is 0. |
| `routing_card` | Routing Card | LINE | Chassis Switch L1-L8 | Enables the L3 routes and QoS; each card adds `optical.routingCardRate` (128 items/s). |
| `trunk_card` | Trunk Card | LINE | Chassis Switch L1-L8 | Lets the switch's ports be VLAN trunks (`ACTION_PORT_MODE` is refused without one). |
| `chassis_psu` | Chassis Power Supply | CHASSIS_PSU | Chassis Switch P1-P4 (bottom row) | It runs on any one; degraded below two. |
| `controller_card` | Controller Card | SHELF_CONTROLLER | Transport Shelf C1, C2 | The shelf runs with one; the second takes over if the first fails. |
| `transponder_card` | Transponder Card | SHELF_LINE | Transport Shelf L1-L8 | One client network (a Link Card used on it) onto one long-haul channel 1-40 (a setting). Channel n at one shelf joins its network with channel n at the other, up to a Fiber Cable's lanes less span loss. |
| `mux_card` | Mux Card | SHELF_LINE | Transport Shelf L1-L8 | 8 transponder channels onto the line (without one the line carries a single channel), up to `optical.shelfChannels` (40). |
| `amplifier_card` | Amplifier Card | SHELF_LINE | Transport Shelf L1-L8 | Launches the line amplified: each end with one (at most 1 counted per end) adds `optical.fiberSpanThreshold` (256) blocks of reach before loss. |
| `protection_card` | Protection Card | SHELF_LINE | Transport Shelf L1-L8 | 1+1 protection: a working path (the shortest) and a protect path (from a different connection point, by different fibre; fibre plus a Network Bridge pair also counts). Switches within a tick (`*PROTSWITCH`). Revertive (setting, default true): switches back when the working path is restored. CHGPROT switches by hand. |
| `compute_blade` | Compute Blade | BLADE | Blade Enclosure F1-F16 | Threads for the rack Scheduler: per blade = computeServerThreads / 2 x (10/16 U) x (1 + `optical.bladeBonusPercent` 25 %) = 0.78125 at the defaults, summed and rounded up (8 blades give 7 threads). |
| `memory_blade` | Memory Blade | BLADE | F1-F16 | Job memory per blade = memoryServerMemory x 0.625 x 1.25 = 25,600 at the defaults. |
| `fabrication_blade` | Fabrication Blade | BLADE | F1-F16 | Crafts from the enclosure's 9 schematic slots as a Fabrication Server does. More blades craft faster: a second blade counts as one Throughput Module, a third as two (`fabricatorCraftTicks`). 50 FE a craft. |
| `storage_blade` | Storage Blade | BLADE | F1-F16 | Opens its 2 drive bays behind it (Drives tab) as network storage. |
| `interconnect_module` | Interconnect Module | INTERCONNECT | Blade Enclosure R1, R2 (rear) | Gives the blades `optical.interconnectLanes` (64); the second is redundant. |
| `blade_psu` | Blade Power Supply | BLADE_PSU | Blade Enclosure P1-P6 (rear) | It runs on any one; degraded below two. |
| `management_module` | Management Module | MANAGEMENT | Blade Enclosure M1, M2 (rear) | Needed to run; the second is a standby. |

Bay counts:
- **Chassis Switch:** 14 bays, plus 32 transceiver cages (4 per line slot).
- **Transport Shelf:** 10 bays.
- **Blade Enclosure:** 32 drive bays, 26 part bays and 9 schematic slots.

Fault/degraded events: `*CARDFAULT` (Blade Enclosure modules, switch cards), `*SUPFAILOVER`, `*PROTSWITCH`, `*SPANLOSS` (`elcl/exec/ElclEvents.java`, `elcl/job/Triggers.java`). Terminal OS: WRKHW, DSPOPT, RTVCHSSTS, RTVOPTSTS, CHGPROT (`terminal/HardwareScreens.java`).

---

## Menus and screens

Every menu type in `registry/ModMenuTypes.java`, with its screen from `client/EncodedLogisticsClient.registerScreens`.

| Menu id | Menu class | Screen class | What it shows |
|---|---|---|---|
| `network_controller` | menu/NetworkControllerMenu | client/screen/NetworkControllerScreen | A controller structure's Network screen: energy gauge, status, lanes, Stored / Usage / Generation, a scrolling device grid. No slots. |
| `capacitor_bank` | CapacitorBankMenu | CapacitorBankScreen | Energy bar, fill %, Stored / Input / Output / Capacity. No slots. |
| `lithography_press` | LithographyPressMenu | LithographyPressScreen | Photomask, wafer, additive and output slots; energy gauge, progress, status. |
| `drive_bay` | DriveBayMenu | DriveBayScreen | 10 drive slots (two columns of five) with fill bars; Online / Offline. |
| `access_terminal` | AccessTerminalMenu | AccessTerminalScreen | The network's items (hot and cold) in the terminal kit, plus the inventory. |
| `fabrication_terminal` | FabricationTerminalMenu | FabricationTerminalScreen | The Access Terminal plus a 3x3 crafting grid, result and clear button. |
| `terminal_desk` | TerminalDeskMenu | client/crt/CrtScreen | The green-screen Terminal OS (80x24); also the Integrated Midrange System console. |
| `rack_console` | RackConsoleMenu (an AccessTerminalMenu) | AccessTerminalScreen (laid out by `screens/rack_console.json`) | A Rack Console's Access Terminal, while it's online and its drawer is out. |
| `rack_console_fabrication` | RackConsoleFabricationMenu | FabricationTerminalScreen | The Rack Fabrication Console's Fabrication Terminal (grid kept in the console). |
| `schematic_terminal` | SchematicTerminalMenu | SchematicTerminalScreen | Schematics tab (holders with badges) and Jobs tab, for a cable-face Schematic Terminal or a Rack Schematic Console. |
| `port` | PortMenu | PortScreen | Ingress/Egress Port (and Wireless Port): 3x3 ghost filter, 4 module slots, redstone mode, filter options, resource type, fuzzy. |
| `inventory_tap` | InventoryTapMenu | InventoryTapScreen | Priority, access mode, 3x3 ghost filter. |
| `threshold_sensor` | ThresholdSensorMenu | ThresholdSensorScreen | Ghost item, threshold, comparison, emitting. |
| `schematic_encoder` | SchematicEncoderMenu | SchematicEncoderScreen | Item grid plus the encoding section (crafting or processing mode), blank and encoded card slots. |
| `fabricator` | FabricatorMenu | FabricatorScreen | 9 Crafting Schematic slots, 2 Throughput Module slots, progress. |
| `gateway` | GatewayMenu | GatewayScreen | Processing Schematic row, stock row with amounts, buffer. |
| `scheduler_core` | SchedulerCoreMenu | SchedulerCoreScreen | Running and queued jobs, thread and memory meters, recent jobs, formed / why not. |
| `relay_antenna` | RelayAntennaMenu | RelayAntennaScreen | Range, 4 Optical Transceiver slots, players in range with linked Handheld Terminals. |
| `block_panel` | BlockPanelMenu | BlockPanelScreen | A block's panel (PanelHost): Exchange Switch (ExchangePanel), Directory Server (DirectoryPanel), Trunk Port (TrunkPortPanel). |
| `network_bridge` | NetworkBridgeMenu | NetworkBridgeScreen | Link status, partner position and dimension, 32-segment lanes meter. |
| `handheld_terminal` | HandheldTerminalMenu | HandheldTerminalScreen | The terminal kit plus battery, link light and signal bars. |
| `point_to_point_link` | PointToPointMenu | PointToPointScreen | What it carries, in/out, Unpair, status and partner. |
| `collector_plane` | CollectorPlaneMenu | CollectorPlaneScreen | Module slot, 3x3 ghost filter, filter/fuzzy options. |
| `deployer_plane` | DeployerPlaneMenu | DeployerPlaneScreen | Place/drop mode, 3x3 ghost filter. |
| `server_rack` | RackMenu | client/screen/RackScreen | Rack elevation (16 units in view, scrolls) or the picked device's settings panel (RackScreen.Panel from RackClientDevices), plus the inventory and the device's slots. |
| `keypunch` | KeypunchMenu | client/crt/KeypunchScreen | KEYPUNCH green screen: 3x3 grid as item names, output, blank/punched cards; Punch F6, Clear F13. |
| `card_reader` | CardReaderMenu | client/crt/CardReaderScreen | CARDRDR: the deck in the hopper and what reading does; Read F6. |
| `line_printer` | LinePrinterMenu | client/crt/LinePrinterScreen | PRINTER: report 1-4, spooled file, pages, paper, status; Print F6. |
| `disk_drive` | DiskDriveMenu | client/crt/DiskDriveScreen | DSKDRV: state and pack, 4=Unload, 5=Display contents. |
| `tape_drive` | TapeDriveMenu | client/crt/TapeDriveScreen | TAPDRV: state and reel, 4=Unload, 5=Display, 7=Rewind. |
| `midrange_panel` | MidrangePanelMenu | client/crt/MidrangePanelScreen | MRCTL / IMCTL control panel: status, drive positions, jobs; IPL F7, hold/release queue. |
| `display_panel` | DisplayPanelMenu | client/screen/DisplayPanelScreen | Display Panel configuration: Mode / Layout / widget tabs (Display agent's area). |
| `plc` | PlcMenu | client/crt/PlcScreen | PLC green screens: PLCSTS / PLCIO / PLCMOD / module settings / retained (PLC agent's area). |
| `signal_device` | signal/SignalMenu | signal/SignalScreen | Cage Light / Alarm Strobe / Speaker settings rows (Signals agent's area). |

Screens with no menu type of their own:
- `RackElevationScreen` (editor) and `RackElevationMaterialsScreen`.
- `CraftAmountScreen`, `CraftPlanScreen` (ingredient tree, Have/Make/Miss, Scheduler pick, cold items in light blue with recall time) and `JobStatusScreen` (steps, Cancel job).
- `client/crt/PlcEditorScreen`.
- `FuzzyPopup`, `GhostPicker`, `ResourcePicker`, `RouteFilterEditor` (sub-widgets).

Rack settings panels (inside `RackScreen`; `client/screen/*Panel.java`, registered in `RackClientDevices`):

| Panel | Devices | Shows |
|---|---|---|
| FirewallPanel | Firewall | Default policy, player list with six permission toggles, add/remove. |
| RouterPanel (+ RouterAclTab, RouteFilterEditor) | Router | Routes (networks, routes, filters, cages, throughput), Mode (static/dynamic, transit, neighbours), route Table, ACLs (rule line syntax `"COAL deny out 64/m 2000/d"`). |
| UpsPanel | UPS | Battery gauge, Charge / Load / Runtime / Source, mode, load bar, last 3 switchovers, mute. |
| NetworkControllerPanel | 2U/4U Network Controller | Energy, status, usage, devices, lanes bar, pair role / partner / last failover, uplink chips per connection point, Switch over (confirm). |
| SwitchPanel | L2 Switches | Lane pool, devices with pooled flag and segment; Ports view (access/trunk, allowed, native). |
| L3SwitchPanel | L3 Switch (also the Chassis Switch's Switch tab) | Devices, Routes, QoS tabs. |
| ServerPanel | Compute, Memory Server | Provided / used / active, usage bar, jobs with cancel, recent jobs. |
| FabricationServerPanel | Fabrication Server | 9 schematic slots, progress, 2 module slots, current craft. |
| MonitoringPanel | Monitoring Server | Stat (items, energy, lanes, jobs) by range (1m, 10m, 1h, 1d) graph, max, now, hover. |
| StoragePanel | NAS, SAN | Drive slots (and SAN cages), priority (Shift steps 10), access mode, capacity bar, uplink. |
| TapeLibraryPanel | Tape Libraries | Magazine (tapes 8 per row, 3 rows visible, drives, cold bar, activity) and Policy (age, trigger %, keep-hot 9, pinned 9, hot fill). |
| WirelessControllerPanel | Wireless Controller | APs tab (online dot, clients "6 / 8 clients", "8 / 8 - full", "no uplink"); Linked tab (bridges, ports, handhelds with unlink X). |
| BodyPanel.directory / DirectoryPanel | Directory Server (Rack) | Published / Remote / Lookups. |
| BodyPanel.opticalMux / OpticalMuxPanel | Optical Multiplexer (Rack) | Channels / Line. |
| ChassisPanel | Transport Shelf, Chassis Switch, Blade Enclosure | Slots (face at 1:1, click bays, part settings), plus Channels/Spans, Cages/Switch, or Drives/Fab. |
| TrunkPortPanel, ExchangePanel | (block panels, via `block_panel`) | Trunk Port mode/segments; Exchange Switch Members/Peers. |

---

## Block entities

From `registry/ModBlockEntityTypes.java` (all `encodedlogistics:`):

| BE type id | Class | Blocks |
|---|---|---|
| `network_controller` | blockentity/NetworkControllerBlockEntity | network_controller |
| `cable` | blockentity/CableBlockEntity | every cable (all tiers and colours) and `part_host` |
| `power_inlet` | PowerInletBlockEntity | power_inlet |
| `capacitor_bank` | CapacitorBankBlockEntity | capacitor_bank, creative_capacitor_bank |
| `lithography_press` | LithographyPressBlockEntity | lithography_press |
| `terminal_desk` | TerminalDeskBlockEntity | terminal_desk (master half) |
| `plc` | plc/PlcBlockEntity | plc |
| `control_interface` | ControlInterfaceBlockEntity | control_interface |
| `swivel_chair` | SwivelChairBlockEntity | swivel_chair |
| `drive_bay` | DriveBayBlockEntity | drive_bay |
| `fabricator` | FabricatorBlockEntity | fabricator |
| `gateway` | GatewayBlockEntity | gateway |
| `scheduler_core` | SchedulerCoreBlockEntity | scheduler_core |
| `directory_server` | DirectoryServerBlockEntity | directory_server |
| `exchange_switch` | ExchangeSwitchBlockEntity | exchange_switch |
| `optical_multiplexer` | OpticalMultiplexerBlockEntity | optical_multiplexer |
| `optical_amplifier` | OpticalAmplifierBlockEntity | optical_amplifier |
| `relay_antenna` | RelayAntennaBlockEntity | relay_antenna |
| `network_bridge` | NetworkBridgeBlockEntity | network_bridge |
| `access_point` | AccessPointBlockEntity | access_point |
| `wireless_bridge` | WirelessBridgeBlockEntity | wireless_bridge |
| `display_panel` | display/DisplayPanelBlockEntity | display_panel |
| `wireless_port` | WirelessPortBlockEntity (extends CableBlockEntity) | wireless_ingress_port, wireless_egress_port |
| `midrange_system` | midrange/MidrangeSystemBlockEntity | midrange_system, integrated_midrange |
| `keypunch` | midrange/KeypunchBlockEntity | keypunch |
| `card_reader` | midrange/CardReaderBlockEntity | card_reader |
| `line_printer` | midrange/LinePrinterBlockEntity | line_printer |
| `disk_drive` | midrange/DiskDriveBlockEntity | disk_drive |
| `tape_drive` | midrange/TapeDriveBlockEntity | tape_drive |
| `cage_light` | signal/CageLightBlockEntity | every cage_light_<dye> (16) |
| `siren` | signal/SirenBlockEntity | every siren_<colour> (Alarm Strobes) |
| `speaker` | signal/SpeakerBlockEntity | speaker |
| `server_rack` | blockentity/RackBlockEntity | server_rack (master only) |

There are 33 types. Blocks with no BE of their own: segment_isolator, job_buffer, thread_unit, expansion_cabinet, small_wireless_bridge, the ores and metal blocks, and the scheduler blocks. (These were not verified individually; `small_wireless_bridge`'s state lives on the machine via MachineBridge.)

## Entities

| Entity id | Class | Notes |
|---|---|---|
| `encodedlogistics:seat` (lang "Seat") | entity/SeatEntity | The invisible rideable a player sits on in a Swivel Chair. MISC category, size 0.01 x 0.01, `noSummon`, client tracking range 8. Its rider's hips are 9/16 above the block bottom. Removed when empty or when the chair is gone. Registered in `registry/ModEntityTypes.java`. It is the only entity type. |

## Creative tabs

`registry/ModCreativeTabs.java`. Ids are numbered so they sort in order and ahead of other mods' tabs.

| Tab id | Title (en_us) | Icon | Contents |
|---|---|---|---|
| `0_main` | Encoded Logistics | Network Controller | Network blocks and machines: controller, inlet, capacitor banks, isolator, press, drive bay, fabricator, gateway, scheduler blocks, relay, bridge, exchange switch, directory server, optical mux and amplifier, wireless blocks, Small Wireless Bridge, display panel, the Midrange line, Server Rack, Terminal Desk, Swivel Chair, Control Interface, PLC, Cage Lights (dye order), Alarm Strobes, Speaker. |
| `1_cables` | Encoded Logistics: Cables | First cable | Every cable, Cable Anchor, Cable Facade. |
| `2_parts` | Encoded Logistics: Parts & Tools | Access Terminal | Every PartType; the modules and sensors, EEPROM Cartridge, Punch Card, 8" Diskette, Diskette Magazine, Tape Reel; every rack device (in catalogue order, with both Network Controllers after the Tape Libraries); every ChassisPart; LTO Tape Drive; LTO-6..10; Handheld Terminal; Link Card; Cross-Connect; Rack Elevation; Schematic Card; every Storage Drive (by resource type and tier). |
| `3_materials` | Encoded Logistics: Materials | Silicon Wafer | Ores, raw, dust, ingots, blocks (Nd, Ta, Ga), silica through processor die, photomasks, storage dies. |

## Sounds

`registry/ModSounds.java` and `src/main/resources/assets/encodedlogistics/sounds.json`. All are variable-range events. The `.ogg` files are under `sounds/block/{rack,terminal_desk,midrange,siren}/`.

| Event id | Subtitle (en_us) | Files | What plays it |
|---|---|---|---|
| `block.rack.door_open` | Rack door opens | rack_door_open1/2 | RackBlockEntity.doorMoved on open (front or rear), vol 0.8, pitch 0.95-1.05 |
| `block.rack.door_close` | Rack door closes | rack_door_close1/2 | RackBlockEntity.doorMoved on close |
| `block.rack.ups_alarm` | UPS alarm | ups_alarm (attenuation 16) | UpsDevice.alarm every 600 ticks on battery (not muted) |
| `block.rack.ups_alarm_low` | UPS low-battery alarm | ups_alarm_low (attenuation 16) | UpsDevice.alarm every 40 ticks while low |
| `block.rack.ups_beep` | UPS beeps | ups_beep (attenuation 16) | UpsDevice.alarm once when power returns |
| `block.rack.console_slide_out` | Console drawer slides out | console_slide_out1/2 | RackConsoleDevice opening (drawer) |
| `block.rack.console_slide_in` | Console drawer slides in | console_slide_in1/2 | RackConsoleDevice closing (after the lid) |
| `block.rack.console_hinge` | Console screen clicks | console_hinge1/2 | RackConsoleDevice lid up (after 8+6 ticks) / lid down (first on closing) |
| `block.rack.picker_move` | Tape picker moves | picker_move1/2 | TapeLibraryDevice at each picker MOVE leg (vol 0.35) |
| `block.rack.tape_load` | Tape loads | tape_load1/2 | TapeLibraryDevice grab/insert legs; also midrange/TapeDriveBlockEntity loading a reel |
| `block.terminal_desk.crt_hum` | CRT hums | crt_hum (stream false) | client/DeskSounds, looping while the desk screen is lit |
| `block.terminal_desk.degauss` | CRT degausses | crt_degauss | TerminalDeskBlockEntity when it boots |
| `block.terminal_desk.key_clack` | Keys clack | key_clack1-4 | client/DeskSounds as the player types (at most every 2 ticks) |
| `block.midrange.midrange_fan` | Midrange fans whir | midrange_fan | client/MidrangeSounds |
| `block.midrange.diskette_latch` | Diskette latch clunks | diskette_latch | Disk Drive, Midrange System, Midranges, Tape Drive |
| `block.midrange.drive_seek` | Diskette drive seeks | drive_seek | Disk Drive, Midrange System |
| `block.midrange.keypunch_clatter` | Card Punch clatters | keypunch_clatter | KeypunchBlockEntity |
| `block.midrange.card_reader_whirr` | Card reader whirrs | card_reader_whirr | CardReaderBlockEntity |
| `block.midrange.line_printer_chatter` | Line printer chatters | line_printer_chatter | LinePrinterBlockEntity |
| `block.siren.wail` | Siren wail | siren/wail (stream false) | signal/SirenSound (Alarm Strobe tone) |
| `block.siren.yelp` | Siren yelp | siren/yelp | SirenSound |
| `block.siren.klaxon` | Siren klaxon | siren/klaxon | SirenSound |
| `block.siren.bell` | Siren bell | siren/bell | SirenSound |
| `block.siren.horn` | Siren horn | siren/horn | SirenSound |
| `block.siren.beep` | Warning beeps | siren/beep | SirenSound |

- **UPS sounds** are filtered client-side by `upsAlarmSounds` (client config, default true; `client/EncodedLogisticsClient.java:120-127`). The server's `rack.upsAlarms` stops them for everyone.
- **Vanilla sound used:** `SoundEvents.CHEST_LOCKED` when rack access is denied.

---

## Device types and name prefixes

Names are the prefix plus `%02d` (UPS01). The prefix is the type code, except desks, which use `ELDESK`. Definitions:
- rack devices: `elcl/exec/ElclDevices.java` `code(RackDevice)` (lines ~143-165);
- cable parts: `code(CablePart)` (~168-185);
- Access Point, Wireless Bridge, Control Interface and desk: `candidates()` (~190-235);
- desk prefix: `prefix()`;
- blocks: `NamedDevice.deviceType()` / `TYPE` constants;
- machines: `machine/MachineBridge.typeCode`.

| Prefix (example) | Device | Block / item | Definition |
|---|---|---|---|
| FIREWALL (FIREWALL01) | Firewall | rack device `firewall` | ElclDevices.code default branch (id uppercased) |
| ROUTER | Router | `router` | code default branch |
| UPS | UPS | `ups` | code default branch |
| SWITCH | 24- and 48-Port L2 Switch | `l2_switch_24`, `l2_switch_48` | code: "SWITCH" |
| L3SWITCH | L3 Switch | `l3_switch` | code |
| CMPSRV | Compute Server | `compute_server` | code |
| MEMSRV | Memory Server | `memory_server` | code |
| FABSRV | Fabrication Server | `fabrication_server` | code |
| MONSRV | Monitoring Server | `monitoring_server` | code |
| DIR | Directory Server (rack and block) | `directory_server` rack type / `directory_server` block | code: "DIR"; DirectoryServerBlockEntity.deviceType() "DIR" |
| NAS | NAS | `nas` | code default branch |
| SAN | SAN | `san` | code default branch |
| RACKCON | Rack Console | `rack_console` | code |
| RACKFAB | Rack Fabrication Console | `rack_fabrication_console` | code |
| RACKSCH | Rack Schematic Console | `rack_schematic_console` | code |
| WLC | Wireless Controller | `wireless_controller` | code |
| TAPELIB | 4U / 6U Tape Library | `tape_library_4u`, `tape_library_6u` | code |
| NETWORK_CONTROLLER_2U / NETWORK_CONTROLLER_4U | 2U / 4U Network Controller | `network_controller_2u/4u` | code default branch (not special-cased; see Open questions) |
| MUX | Optical Multiplexer (rack and block) | `optical_multiplexer` rack / block | code "MUX"; OpticalMultiplexerBlockEntity.deviceType() |
| AMP | Optical Amplifier (rack and block) | `optical_amplifier` rack / block | code "AMP"; OpticalAmplifierBlockEntity.deviceType() |
| OTS | Optical Transport Shelf | `optical_transport_shelf` | code |
| CSW | Chassis Switch | `chassis_switch` | code |
| BLD | Blade Enclosure | `blade_enclosure` | code |
| INGRESS / EGRESS | Ingress / Egress Port (cable part) | PartType INGRESS_PORT / EGRESS_PORT | code(CablePart) |
| WINGRESS / WEGRESS | Wireless Ingress / Egress Port | `wireless_ingress_port` / `wireless_egress_port` (host is WirelessPortBlockEntity) | code(CablePart) |
| TAP | Inventory Tap | part | code(CablePart) |
| SENSOR | Threshold Sensor | part | code(CablePart) |
| COLLECTOR | Collector Plane | part | code(CablePart) |
| DEPLOYER | Deployer Plane | part | code(CablePart) |
| P2P | Point-to-Point Link | part | code(CablePart) |
| TERM | Access Terminal | part | code(CablePart) |
| FABTERM | Fabrication Terminal | part | code(CablePart) |
| ENCODER | Schematic Encoder | part | code(CablePart) |
| SCHTERM | Schematic Terminal | part | code(CablePart) |
| TRK | Trunk Port | part | code(CablePart) |
| AP | Access Point | `access_point` | candidates() literal "AP" |
| WBRIDGE | Wireless Bridge | `wireless_bridge` | candidates() literal "WBRIDGE" |
| CTLIF | Control Interface | `control_interface` | ControlInterfaceBlockEntity.TYPE |
| ELDESK (type DESK) | Terminal Desk | `terminal_desk` | candidates() "DESK" + prefix() -> "ELDESK" |
| GATEWAY | Gateway | `gateway` | GatewayBlockEntity.TYPE |
| XSW | Exchange Switch | `exchange_switch` | ExchangeSwitchBlockEntity.deviceType() |
| DSP | Display Panel (screen) | `display_panel` | display/DisplayPanelBlockEntity.TYPE |
| MIDRANGE | Midrange System / Integrated Midrange System | `midrange_system`, `integrated_midrange` | midrange/MidrangeSystemBlockEntity.TYPE |
| KEYPUNCH | Keypunch | `keypunch` | KeypunchBlockEntity.TYPE |
| CARDRDR | Card Reader | `card_reader` | CardReaderBlockEntity.TYPE |
| PRT | Line Printer | `line_printer` | LinePrinterBlockEntity.TYPE |
| DISK | Disk Drive (Midrange) | `disk_drive` | DiskDriveBlockEntity.TYPE |
| TAPE | Tape Drive (Midrange) | `tape_drive` | TapeDriveBlockEntity.TYPE |
| PLC | PLC | `plc` | plc/PlcBlockEntity.TYPE |
| LGT | Cage Light | `cage_light_<dye>` | signal/CageLightBlockEntity.TYPE |
| SRN | Alarm Strobe | `siren_<colour>` | signal/SirenBlockEntity.TYPE |
| SPK | Speaker | `speaker` | signal/SpeakerBlockEntity.TYPE |
| (machine code, e.g. ARCCRU, ELECTR) | An Arcforge machine with a Small Wireless Bridge on it | `small_wireless_bridge` | MachineBridge.typeCode: one word -> its first 6 letters; two or more -> 3 + 3 letters; empty -> MCH; a code starting with a digit -> "M" + 5 |

The Work with Devices row type (Controller, Drive Bay, Rack, Unit, Part, Machine, Terminal, Disk, Tape, Device, CTLIF, PLC, LGT/SRN/SPK) is a separate column (`multiblock/ControllerStructures.deviceRows`, ~line 1539). Controller structures and Drive Bays are listed but have no name prefix: they aren't naming candidates.

---

## Not implemented (from older docs)

How I checked: greps and reads of the current code (named files). "Changed" means the doc describes a different design from the one that shipped; leave it out or describe the code's version.

docs/rack/HANDOFF.txt (batch 1):
- The Router routes between Segment-Isolator segments of one network, with segments "known" through cables, P2P lanes links or Network Bridges ending at the rack. **Changed.** RouterDevice routes between separate networks linked by Link Card (header and `LinkCardItem`). Segment routing is the L3 Switch's.
- "Each installed device uses 1 lane; the rack itself 0", and devices are 1U or 2U only. **Changed.** Devices are 1-10U (`MAX_SIZE = 10`), switches pool lanes, and Network Controllers use 0.
- The rack connects only on its rear face and top. **Changed:** the bottom points (BOTTOM_FRONT / BOTTOM_REAR) also connect (`RackGeometry.Point`, `connectsOn`).
- The Firewall has 5 permissions. **Changed:** there are 6, with RACK added (`RackPermission`).
- "#encodedlogistics:rack_devices - the GUI accepts only these". **Not used by code:** grep finds no Java reference; RackMenu accepts any `RackDeviceItem`. The JSON also lacks `rack_fabrication_console` and `rack_schematic_console`.
- The popup can be up to 180 px wide. **Changed:** `MAX_W = 140`, and longer text wraps (`RackHud`).
- Rack culling distance: 24 (implemented as `rack.rackCullDistance`). The door pivots differ slightly: the doc gives (15.5,0,0.5) and (0.5,0,31.5), the code uses (14.5,0.5), (14.5,31.1), (1.5,31.1) per the NOTE at the top of the doc. Use the code's.
- Jade/WTHIT opt-out: Jade is hidden for the rack (implemented). There is no WTHIT compat (`compat/` has only arcforge, jade, jei).
- An "optional energy capability" per device. Not present: power enters through the rack's connection points only (`RackBlockEntity` PowerPort).

docs/rack/HANDOFF_batch2.txt:
- Rack Scheduler as "one rack = one Rack Scheduler", needing a Compute and a Memory Server in that rack. **Changed:** it pools network-wide across racks with a head rack (`RackScheduler` header; CHANGELOG "Rack Schedulers pool across racks").
- L3 QoS rules by "crafting jobs" or "a specific Ingress/Egress Port picked with a Link Card", serviced when the network budget is exhausted. **Changed:** `QosRule(ItemStack filter, int level)` only orders items within the L3 Switch's own routes. There are no crafting or port QoS kinds.
- The Router reaches "networks reachable through any Network Bridge". **Changed:** it links networks by Link Card (any distance or dimension). Dynamic routing is a later addition.
- SAN "+25 % access speed per extra transceiver (max +75 %)". **Not implemented:** `SanDevice` only adds +1 priority per extra transceiver (`priorityBonus`). There is no speed factor.
- A device on segment S "only sees network storage of segment S". Implemented in a different form: the device serves that segment's network (`RackBlockEntity.network(device)`).

docs/rack/HANDOFF_batch3.txt:
- Tape Library "mail slot" eject via an operator button. **Not implemented:** no "mail" or eject action in `TapeLibraryDevice` (actions are SET_AGE, CYCLE_UNIT, SET_PERCENT, TOGGLE_TRIGGER, SET_KEEP, SET_PINNED, SET_FUZZY).
- Archiving excludes "anything a running crafting job has reserved". **Not implemented:** `scanForArchive` checks only age, keep-hot, pinned, the trigger and `recalls.busy`.
- Rack Console Fabrication mode via a Memory Die upgrade (shift-use). **Removed:** it was replaced by the Rack Fabrication Console. Old consoles migrate (`RackConsoleDevice` header, CHANGELOG).
- Load time 60 ticks and read/write "2 s + 1 s per 4K". **Changed** defaults: `tapeBaseTicks` 40 + `tapeTicksPer4k` 20, times the generation's speed. The picker time is computed from the picker path, not a fixed 60.
- Tape contents "in a data component (item -> count, last-access tick)". **Changed:** contents live in `DriveStorage` under the tape's `drive_id`. The item carries only the id and the `drive_stats` cache.
- The Wireless Controller panel lists "head, player name, dimension, connected dot". **Changed:** APs and Linked tabs (WirelessControllerPanel); no dimension column is described.
- Console HUD lines "Network / Items stored / Drawer". Not verified line by line. Check `RackConsoleDevice.lines` if documenting popup lines.

docs/desk/HANDOFF.txt:
- The initial lowercase CLI (`help`, `show inventory|drives|lanes|jobs|devices|power`, `withdraw`, `craft`, `cancel job`, `clear`) and the Java `TerminalCommand` / `registerShowTopic` extension API. **Replaced:** every command-line line is an ELCL command (`terminal/TerminalCommands.java`). grep finds no `registerShowTopic`, `ShowTopic` or "show" command.
- The F24 "graphic item view" (the Access Terminal grid inside the CRT). **Not implemented:** F24 (Shift+F12) shows "more keys and the phosphor" (`client/crt/CrtScreen.java` header). grep finds no "graphic" view.
- The phosphor stored "per player (client config + a /settings key)". Implemented as the client config `phosphor` (default `*SYSVAL`, the system's PHOSPHOR value) and the F24 page. There is no `/settings` command (not searched exhaustively).
- The screen list (MAIN 1-4/90, WRKINV, WITHDRAW, CRAFT, WRKJOB, WRKDEV, DSPNETSTS, CMDENT 500 lines) exists, but the Terminal OS grew far beyond it. Document from `docs/elcl` and the code, not this handoff.

docs/optical/HANDOFF.txt:
- Power "200 FE/t per card / blade, 1,000 per chassis base". **Changed:** `optical.chassisCardDrain` 2.0 and `optical.chassisDrain` 10.0.
- Compute Blade "8 threads per blade". **Changed:** threads per blade are derived (0.78 at defaults; Chassis parts table).
- Transponder "32 lanes". **Changed:** a Fiber Cable's lanes (`lanes.perFiberCable` 32 by default) less span loss.
- The Cable Certifier tool. **Not in the repo:** grep finds no "certifier" in the Java sources.
- Its "not in the repo yet" notes about Rack Elevation, VLAN trunking and WRKHW are **out of date:** all three are implemented now.

README.md / CHANGELOG.md: consistent with the code for this area. The 1.x CHANGELOG entries describe what's implemented (rack consoles, elevation, break confirm, UPS alarm client option, pooled schedulers). No unimplemented claims found for this scope.

docs/rack/HANDOFF_texture_update.txt: art only; nothing to document.

---

## Open questions

1. **Rack Network Controller device names:** `ElclDevices.code` has no case for `network_controller_2u/4u`, so they fall to the default and are named `NETWORK_CONTROLLER_2U01`. That is longer than the 10 characters RNMDEV allows (`[A-Z][A-Z0-9_@#$]{0,9}`), and it contains no trailing short code. Intended, or a missing case (for example `NC`/`RACKCTL`)? (Auto-names aren't length-checked; a player can't rename *to* such a name. `COLLECTOR01`, 11 characters, has the same issue.) The `ElclDevices` header comment also omits DIR, TRK, XSW, DISK, TAPE and the network controllers.
2. **`#encodedlogistics:rack_devices` tag:** `data/encodedlogistics/tags/item/rack_devices.json` isn't referenced by Java and is missing the Rack Fabrication and Schematic Consoles. Is it kept for other mods or recipes, or is it stale?
3. **FirewallPanel's class comment** says "their five permissions", but it renders all six (`RackPermission.values()`). Documentation should say six.
4. **Rack Console popup lines** (`RackConsoleDevice.lines`), the Wireless Controller's popup lines and the Network Controller's uplink chip states weren't transcribed. Check them before writing per-device HUD docs.
5. **Router details** (dynamic routing, transit, ACL rule syntax, Exchange Switch) are summarised here only. The routing agent should own them (`docs/routing/HANDOFF.txt`, `rack/RouterAcl.java`, `RoutingDomain`).
6. **Which blocks have no BE type:** the list in "Block entities" is a reasonable reading. I did not confirm how `small_wireless_bridge` stores its state (no BE type is registered for it).
7. **Handheld across dimensions with a Relay Antenna:** `access()` looks for antennas in the player's level. Is cross-dimension handheld use only possible through a Wireless Controller? The code reads that way, but I didn't trace it fully.
