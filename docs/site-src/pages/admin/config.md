---
title: Configuration
section: admin
order: 10
summary: Where the mod's two config files are, how they apply, and a guided tour of the options a server owner is most likely to change.
---
Encoded Logistics has two config files. This page says where they are and walks through the options that matter most;
{{page reference/config}} lists every one of them with its type, default, range and description.

## The files {#files}

| File | What it holds | Where it applies |
|---|---|---|
| `config/encodedlogistics-local.toml` | Everything about how the mod behaves: power, lanes, storage, crafting, racks, routing, the Terminal OS, PLCs, displays, speakers (13 sections) | On the server (and in single-player, the game you play) |
| `config/encodedlogistics-client.toml` | One player's own preferences: terminal sorting and layout, job toasts, web audio, UPS alarm sounds (3 sections) | On that player's game only |

Things to know about the main file:

- **It is per installation, not per world.** It sits in the game's (or the server's) `config/` folder and applies to
  every world that installation opens. There is no copy in the world's save, as some mods' server configs have.
- **It is not sent to players.** On a dedicated server the server's file is the one that counts. A player's own copy
  of `encodedlogistics-local.toml` doesn't change the server, though a few item tooltips and screens on that player's
  game read numbers from their own copy, and may differ from the server's if the two files differ.
- **Older betas.** On NeoForge betas before 26.3.0.37 the same file is called `encodedlogistics-common.toml`.
- **Editing.** Edit the file with the game or server stopped, or use the in-game config screen (Mods, Encoded
  Logistics, Config) in single-player. None of the options is marked as needing a restart.

The files are written with every option and its comment the first time the game starts with the mod.

## A guided tour {#tour}

The sections in the main file are `controller`, `lanes`, `power`, `storage`, `autocrafting`, `reach`, `lithography`,
`facades`, `rack`, `optical`, `elcl`, `plc` and `signals`. The `rack` section holds more than racks: routing, the UPS,
wireless, Arcforge machine power, the Midrange line, disk and tape drives, Display Panels and the Terminal Desk all
live there. The groups below are by feature, with each option's real path.

### Network size and power {#network}

How big a controller structure can be, how many lanes a network gets, and what it costs to run. See
{{page networks/controllers}}, {{page networks/lanes}} and {{page networks/power}}.

{{table config keys=controller.maxSize,controller.energyPerBlock,controller.drainPerBlock,lanes.perControllerFace,lanes.perCable,lanes.perDenseCable,lanes.perFiberCable,power.inletMaxInput,power.capacitorCapacity}}

### Storage {#storage}

{{table config keys=storage.driveTypeLimit,storage.portRates,storage.portFluidRates,storage.portEnergyRates}}

See {{page storage/drives}} and {{page networks/cable-parts}}.

### Crafting {#crafting}

How much a Scheduler can hold and run, and how fast Fabricators work. See {{page crafting/schedulers}} and
{{page crafting/fabricators-and-gateways}}.

{{table config keys=autocrafting.schedulerMaxSize,autocrafting.schedulerBaseMemory,autocrafting.jobBufferMemory,autocrafting.schedulerBaseThreads,autocrafting.threadUnitThreads,autocrafting.threadCrafts,autocrafting.fabricatorCraftTicks,autocrafting.jobStallSeconds,rack.computeServerThreads,rack.memoryServerMemory}}

### Security {#security}

Whether an offline Firewall still enforces its rules, and how a Server Rack is protected from being broken. See
{{page admin/permissions}}.

{{table config keys=rack.firewallFailClosed,rack.rackBreakProtection,rack.rackBreakConfirm}}

### Reach across dimensions and chunk loading {#reach}

{{table config keys=reach.bridgeCrossDimension,reach.bridgeChunkLoading,rack.exchangeChunkLoading,rack.wirelessCrossDimension,rack.wirelessAnyController}}

See {{page networking/bridges-and-links}}, {{page networking/exchange-and-directory}} and
{{page networking/wireless}}.

### The Terminal OS (`elcl`) {#elcl legacy="config"}

The `elcl` section sets the Terminal OS's budgets and limits: how much ELCL runs each tick, how much a system keeps, and
whether libraries sync with a folder on disk.

{{table config section=elcl}}

The budgets are explained on {{page admin/performance}}, folder sync on {{page admin/folders}}, and what the limits mean
for programs on {{page elcl/status-and-limits}}. Two more options elsewhere in the file belong with these:
{{config reach.controlInterfaceDrain}} (the Control Interface's power use) and {{config rack.firewallFailClosed}}.

### PLCs (`plc`) {#plc}

{{table config section=plc}}

See {{page control/plc}}.

### Files players can add: images and audio {#files-from-players}

Display Panel images and Speaker web audio are off on dedicated servers unless you turn them on. Audio files from the
world's folder always play, within the limits.

{{table config keys=rack.allowImages,rack.displayMaxImageBytes,rack.displayMaxImageSize,rack.displayMaxImagePixels,rack.displayImageColors,signals.allowWebAudio,signals.webAudioHosts,signals.audioMaxBytes,signals.audioMaxSeconds}}

See {{page admin/folders}}, {{page control/display-panels}} and {{page control/speakers}}.

### Player-facing extras {#extras}

{{table config keys=rack.allowJobToasts,rack.upsAlarms,elcl.messageChatNotice,rack.rackElevationCreativeFree,facades.blocklist}}

## The client file {#client}

Each player sets these for themselves, in the mod's config screen or their own `encodedlogistics-client.toml`:

{{table config section=terminal file=client}}

{{table config section=toasts file=client}}

{{table config section=audio file=client}}

`audio.allowWebAudio` is the player's own opt-in for web audio from Speakers: even where the server allows web audio,
a player's game fetches nothing until they turn it on. Two server options limit what players may choose:
{{config rack.allowJobToasts}} turns job toasts off for everyone, and {{config rack.upsAlarms}} silences every UPS alarm
for everyone.
