---
title: Performance and limits
section: admin
order: 40
summary: Every budget and limit that keeps the mod's cost on a server bounded - ELCL, PLCs, Display Panels, Speakers, networks and chunk loading - and the options that set them.
---
Most of what Encoded Logistics does on a server is bounded by a budget or a limit, so one player's program, screen or
speaker can't take the server down. This page gathers them in one place. The defaults suit a small server; raise them
with care. The numbers that aren't options are fixed in the code.

## ELCL programs {#elcl}

Programs run a few instructions at a time, taking turns:

| Option | What it limits |
|---|---|
| {{config elcl.interactiveBudget default}} | Instructions an interactive job (a program called from a terminal) runs each tick before it yields |
| {{config elcl.batchBudget default}} | Instructions each batch job runs each tick |
| {{config elcl.globalBudget default}} | Instructions all jobs on the server run each tick, together. Jobs take turns, so the cap is shared out fairly |

A job that is waiting (`DLYJOB`, a recall, a craft) costs nothing. An endless loop only ever uses its budget, and can be
ended from Work with Active Jobs. How many batch jobs run at once is set per system by the QMAXJOB system value, and per
host by {{config elcl.computeServerJobs default}} (jobs each Compute Server runs; 0 makes Compute Servers no job hosts
at all).

What a system keeps:

| Option | What it limits |
|---|---|
| {{config elcl.maxSourceLines default}} | Lines in a source member |
| {{config elcl.maxListSize default}} | Elements in a `*LIST` variable |
| {{config elcl.maxRecordsPerFile default}} | Records in a physical file ({{msg ELC2208}} past it) |
| {{config elcl.spooledFileCap default}} | Spooled files a system keeps (the oldest go first) |
| {{config elcl.messageCap default}} | Messages each user's queue keeps (the oldest go first) |
| {{config elcl.craftLogRetention default}} | Ended crafting jobs each network's history keeps, by default (the CRFLOGRTN system value) |
| {{config elcl.disketteBytes default}} | Bytes of library source an 8" Diskette holds |
| {{config elcl.charsPerStorageByte default}} | Characters of source that take one byte of the network's drive storage |

In the code: a program declares at most 5 files (`DCLF`).

## PLCs {#plc}

| Option | What it limits |
|---|---|
| {{config plc.plcInstructionsPerTick default}} | Instructions a PLC runs each tick (a scan that needs more carries on next tick) |
| {{config plc.plcMaxPerChunk default}} | PLCs running in one chunk |
| {{config plc.plcMaxPerServer default}} | PLCs running on the server |

A PLC over either limit stays in STOP ("PLC limit reached") until there is room. Each PLC's log keeps 16 entries.

## Display Panels {#displays}

| Option | What it limits |
|---|---|
| {{config rack.displayMaxWidth default}}, {{config rack.displayMaxHeight default}} | A screen's size in panels |
| {{config rack.displayHistorySeconds default}} | Seconds of graph history a screen keeps itself |
| {{config rack.displayMaxImageBytes default}} | An image file's size, in MB |
| {{config rack.displayMaxImageSize default}} | An image's width and height, in pixels |
| {{config rack.displayMaxImagePixels default}} | An image's width x height |
| {{config rack.displayImageColors default}} | The most colours a player may pick for an image |

In the code: at most 16 regions a screen, 64 rows in a table widget, four touches a second for each player, and a wall
of at most 1,024 panels is merged at once. Live widgets are worked out once a second, only for players near the
screen. Images are decoded off the server thread, at the region's size, and cached.

## Speakers and Alarm Strobes {#audio}

| Option | What it limits |
|---|---|
| {{config signals.audioMaxBytes default}} | An audio file (or web audio), in MB |
| {{config signals.audioMaxSeconds default}} | An audio file's length, in seconds |
| {{config signals.midiMaxKilobytes default}} | A MIDI file, in KB |
| {{config signals.midiMaxSeconds default}} | A MIDI file's length, in seconds |
| {{config signals.midiMaxNotes default}} | Notes one Speaker starts in a tick from a MIDI file (the loudest are kept) |
| {{config signals.speakerMaxRange default}} | How far a Speaker may be set to carry |
| {{config signals.sirenMaxRange default}} | How far an Alarm Strobe may be set to carry |

In the code: files go to players in 24 KB chunks, four a tick for each player and one file at a time; players keep at
most 64 MB of audio in memory; the server remembers its last 256 checked files; a note sequence has at most 32 notes
and a MIDI play at most 32 parts; a web fetch gives up after 30 seconds.

## Networks {#networks}

| Option | What it limits |
|---|---|
| {{config controller.maxSize default}} | A Network Controller structure's size along each axis |
| {{config autocrafting.schedulerMaxSize default}} | A Scheduler structure's size along each axis |
| {{config lanes.perControllerFace default}} | Lanes each connected controller face provides |
| {{config lanes.perCable default}}, {{config lanes.perDenseCable default}}, {{config lanes.perFiberCable default}} | Lanes a cable carries |
| {{config storage.driveTypeLimit default}} | Item types on one Storage Drive |
| {{config rack.directoryCacheSeconds default}} | How long a resolved `SYSNAME/DEVICE` name is kept |
| {{config rack.maxHops default}} | Hops a dynamically routed path may take |

Lanes are what limit how many devices a network runs; see {{page networks/lanes}}.

### How often networks are worked out {#solves}

A network rediscovers its blocks and works out its lanes again (a solve) only when something it's made of changes:
a block placed, broken or reconnected on it or next to it, or a remote link to it coming up or going down. A remote
link's end loading or unloading as players move about costs nothing unless the link really changed. A burst of
changes costs one solve:

| Option | What it does |
|---|---|
| {{config lanes.resolveInterval default}} | Ticks a network waits after a solve before it solves again; changes in the meantime are gathered into one. 0 solves after every change |
| {{config lanes.slowSolveMillis default}} | A solve slower than this many milliseconds is logged as a warning, with the network and its cause; 0 logs none |

`/encodedlogistics debug solves` shows each network's solves over the last minute, what they cost and what caused
them, `debug ticks` what the networks cost every tick, and `debug watch solves` shows slow solves in your chat as they
happen. See {{page admin/commands#debug}}.

### Checking the network caches {#caches}

Networks keep caches (their storage, drive cells, the rack devices serving them, racks in order and the rack Scheduler
pools) and rebuild them only when something changes. If a server lags around a large network,
`/encodedlogistics caches` shows how often each was rebuilt over the last minute. A cache rebuilt about every tick
(some 1,200 a minute) means something invalidates it every tick. See {{page admin/commands}}.

## Chunk loading {#chunk-loading}

Only two things load chunks:

| Option | What it loads |
|---|---|
| {{config reach.bridgeChunkLoading default}} | A linked Network Bridge keeps its own chunk loaded, so its partner's network reaches it while nobody is near |
| {{config rack.exchangeChunkLoading default}} | An Exchange Switch keeps its chunk loaded while it has members |

Everything else works only while its chunk is loaded by players or another mod.
