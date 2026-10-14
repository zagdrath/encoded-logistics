---
title: Hot and cold storage
section: storage
order: 30
summary: What counts as a network's storage, the order it fills and empties it in, priorities and access modes, and how items move to tape and back.
---
Everything a network stores is in one of two tiers. **Hot storage** is what it can hand out at once. **Cold storage** is
tape: cheap and huge, but an item on tape has to be brought back (recalled) before you can have it. Terminals show both
together, so day to day you only notice the tiers when a recall takes a moment.

## What's in each tier {#tiers}

| Tier | What it is | Holds |
|---|---|---|
| Hot | Drives in online [Drive Bays](page:storage/drive-bay), [Disk Drives](page:storage/midrange-storage#disk-drive), [NAS and SAN](page:storage/nas-san) devices and Blade Enclosure Storage Blades; inventories faced by online Inventory Taps | Items, fluids and gases, each on drives (and taps) that take it |
| Cold | [LTO tapes](page:storage/tapes) in online Tape Libraries; the Tape Reel on a [Tape Drive](page:storage/midrange-storage#tape-drive) | Items only |

Energy isn't storage: Energy Storage Drives are part of the network's energy pool (see {{page networks/power}}).

An Inventory Tap (a cable part on a chest, tank or any other inventory) makes that inventory part of hot storage, with
its own priority, access mode and filter. It's covered on {{page networks/cable-parts}}.

## Priorities {#priority}

Every place in hot storage has a **priority**. Higher priority is filled first and emptied last:

- **Putting in:** highest priority first. On a tie, drives before Inventory Taps, and within a priority the places that
  already hold that item before the rest. So a stack goes onto the drive that already has some, rather than adding a
  new type to another drive.
- **Taking out:** lowest priority first. On a tie, taps before drives.
- **Shared storage**, another segment's storage reached through a router's Share route, comes after all of the
  network's own, both ways (see {{page networking/routers}}).

Where you set it:

| Holder | Priority |
|---|---|
| Drive Bay, Disk Drive | Always 0 |
| NAS, SAN, Blade Enclosure storage | -999 to 999, in the device's panel (Shift steps by 10). A SAN adds +1 for each uplink transceiver beyond the first |
| Inventory Tap | -999 to 999, in its screen |

A typical use: give a NAS of big drives priority 10 so bulk goes there first, and an Inventory Tap on a furnace's output
chest priority -10 so it's emptied first and filled only when everything else is full.

## Access modes {#access}

NAS, SAN and Blade Enclosure storage, and Inventory Taps, also have an **access mode**:

| Mode | The network may |
|---|---|
| Read-write (default) | Put in and take out |
| Read-only | Only take out: nothing new goes in |
| Write-only | Only put in: nothing comes out (a drop-off) |

Drive Bays and Disk Drives are always read-write.

## Crafting jobs claim incoming items {#claims}

While a crafting job waits for an output (a Gateway's machine finishing, say), whatever arrives that it's waiting for
goes to the job instead of into storage, by whatever path it comes in: the Gateway, an Ingress Port, an Inventory Tap,
a terminal. See {{page crafting/jobs}}.

## Cold storage: archive and recall {#archive}

The network keeps track of when each item type on its item drives was last put in or taken out. A Tape Library (or a
Midrange Tape Drive) on the network uses that to **archive**: it moves item types nobody has touched for a while from
the drives onto tape, freeing the drives for what's in use. By default it does so only while the item drives are more
than {{config rack.tapeDefaultHotPercent default}} % full, and only for types untouched for
{{config rack.tapeDefaultAgeHours default}} hours. Only item drives are archived: taps' inventories, fluids and gases
stay where they are.

A **recall** brings items back. It starts when:

- you take more of an item than is hot, from a terminal, the Terminal Desk or a script: you get what's hot now, and the
  rest is recalled and can be taken once it's back. The terminal shows the item as on tape, with the time a recall will
  take;
- a crafting job needs it: the planner counts what's on tape as stored, and starting the job starts the recalls (see
  [the plan screen](page:crafting/jobs#plan));
- the item is pinned to stay hot, or {{cmd CHGITMTIER}} sets it to `*HOT`.

If hot storage fills up during a recall, what didn't fit stays on tape and the item is marked "hot storage full".

The archive policy (age, trigger, keep-hot and pinned lists), timings and {{cmd CHGITMTIER}} are on
{{page storage/tapes}}.

## Seeing it from the Terminal OS

{{cmd WRKINV}} lists the network's items, fluids and gases with a Location column (Hot, Cold, or Hot+Cold), and
{{cmd RTVSTGSTS}} gives a script how full the hot or cold tier is (`TIER(*HOT)`, `TIER(*COLD)` or `*ALL`). See
{{page elcl/screens}}.
