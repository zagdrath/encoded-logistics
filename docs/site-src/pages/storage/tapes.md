---
title: LTO tapes and Tape Libraries
nav: Tapes
section: storage
order: 50
summary: LTO tapes and the LTO Tape Drive, the 4U and 6U Tape Libraries, the archiving policy, recalls, upgrading tapes, and what CHGITMTIER does.
---
Tape is a network's **cold storage**: far more room than drives, for items nobody is using. A Tape Library in a
{{item server_rack}} holds LTO tapes and the drives that read and write them. On its own it moves item types that
haven't been touched for a while from the network's drives onto tape, and brings them back when they're wanted. You
keep seeing them in terminals the whole time; the only difference is the moment a recall takes.

For how cold storage fits with the rest of the network's storage, see {{page storage/hot-and-cold}}. Installing a
library in a rack, and its power and lanes, are on {{page racks/devices}}.

## LTO tapes {#tapes}

There are five generations. Each holds four times the one before and reads and writes 15% faster:

| Tape | Bytes | Items, one type | Read/write time |
|---|---|---|---|
| {{item lto_tape_6}} | 8,388,608 (8M) | 66,584,576 | × 1 |
| {{item lto_tape_7}} | 33,554,432 (32M) | 266,338,304 | × 0.85 |
| {{item lto_tape_8}} | 134,217,728 (128M) | 1,065,353,216 | × 0.72 |
| {{item lto_tape_9}} | 536,870,912 (512M) | 4,261,412,864 | × 0.61 |
| {{item lto_tape_10}} | 2,147,483,648 (2G) | 17,045,651,456 | × 0.52 |

Tapes use the same [byte model](page:storage/drives#capacity) as Storage Drives: 8 items a byte, each type reserving a
128th of the tape, and at most {{config storage.driveTypeLimit default}} types a tape. Tapes hold items only.

They behave like drives as items, too. A new tape stacks to 64; the first time it goes into a library it gets an id,
its contents are kept in the world's save under that id, and from then on it stacks to 1. A tape taken out keeps what's
on it until it goes back into a library; one that's empty again goes back to blank in your inventory.

{{recipe lto_tape_6}}

### Upgrading tapes {#upgrade}

Each generation is crafted from the one before. The new tape takes the old one's id, so it **keeps everything on it**:
you can upgrade a full tape.

| To | Old tape plus |
|---|---|
| LTO-7 | 2 Ferrite, a Logic Die |
| LTO-8 | 2 Neodymium Ingots, a Memory Die |
| LTO-9 | 2 Neodymium Ingots, a Tantalum Capacitor |
| LTO-10 | 2 Gallium Ingots, a Processor Die |

{{recipe lto_tape_7}}

The new tape's fill is worked out again once it's in a library.

## The LTO Tape Drive {#drive}

An {{item lto_tape_drive}} goes in one of a library's drive bays; it stacks to 16. A library's tapes are part of the
network's cold storage while the library is online, drive or no drive, but reading or writing a tape needs a drive. With
no drive the library can't archive or recall anything.

{{recipe lto_tape_drive}}

## The Tape Libraries {#libraries}

| Library | Size | Tape slots | Drive bays | Idle drain |
|---|---|---|---|---|
| {{item tape_library_4u}} | 4U | 24 | 2 | {{config rack.tapeLibrary4uDrain default}} FE/t |
| {{item tape_library_6u}} | 6U | 48 | 4 | {{config rack.tapeLibrary6uDrain default}} FE/t |

On top of the idle drain, each drive reading or writing adds {{config rack.tapeDriveBusyDrain default}} FE/t and the
picker {{config rack.tapePickerDrain default}} FE/t while it moves. Libraries take LTO tapes only, in their tape slots,
and LTO Tape Drives in their bays.

{{recipe tape_library_4u}}

The library's panel has two tabs:

- **Magazine:** the tape slots (eight to a row, three rows at a time; scroll for the rest), a fill bar under each tape,
  the drive bays and what each drive is doing, the cold capacity bar, and what the library is doing now.
- **Policy:** the archiving rules below.

{{screenshot file="storage/tape-library-panel.png" alt="The Tape Library panel's Magazine tab" show="A 6U Tape Library's panel on the Magazine tab with a dozen tapes of mixed generations and fill, four drives with one reading, and the activity line" angle="GUI scale 3, panel centred" size="1280x720"}}

On its front you can watch the picker robot carry tapes between the slots and the drives, with its own sounds.

{{screenshot file="storage/tape-library-front.png" alt="A Tape Library's picker loading a tape into a drive" show="A 6U Tape Library in a Server Rack with the front door open, the picker mid-move carrying a tape to a drive" angle="Facing the rack front from two blocks away, eye level with the library" size="1600x900"}}

## The archiving policy {#policy}

Set on the library's Policy tab:

| Setting | What it does | Default |
|---|---|---|
| Archive after | How long an item type must go untouched on the drives before it's archived: 1 to 9,999 minutes, hours or days (game time: an hour is 72,000 ticks) | {{default rack.tapeDefaultAgeHours}} hours |
| Only while hot storage is above | The free-space trigger: archive only while the network's item drives are fuller than this % (0 to 100). It can be turned off, and then age alone decides | On, {{default rack.tapeDefaultHotPercent}} % |
| Keep hot | Nine filter entries (right-click one for its fuzzy match). Items that match are never archived | Empty |
| Pinned (always hot) | Nine items that are never archived, and are recalled by themselves if any of them is found on tape | Empty |

Click an entry with an item to set it, or empty-handed to clear it; you can drag items from JEI onto them. The tab also
shows how full hot storage is now. The defaults for new libraries come from the config ({{config rack.tapeDefaultAgeHours}},
{{config rack.tapeDefaultHotPercent}}).

### How archiving works {#archiving}

Every {{config rack.tapeArchiveInterval default}} ticks, a library with an idle drive and at least one tape looks for
work:

1. If the trigger is on and the item drives aren't fuller than its percentage, it does nothing.
2. Of several libraries on one network, only the one with the most free tape space archives (the first, on a tie).
3. It lists the item types on the network's drives untouched for longer than its age, leaving out keep-hot matches,
   pinned items, and anything being recalled. Oldest first.
4. Each idle drive takes one type: the picker loads a tape, the drive writes, the picker puts the tape back. A type goes
   on the tape that already holds some of it, if that has room; otherwise on the fullest tape with room, so tapes fill
   one at a time.

If the item is touched (put in or taken out) while its tape is being loaded, it stays hot. Only item drives are archived:
fluids, gases and what's in Inventory Taps' inventories stay where they are.

### Recalls {#recalls}

A recall brings an item back from tape into hot storage: when someone takes more than is hot, when a crafting job
needs it, or for a pinned item. Recalls queue oldest first (asking again for an item already waiting takes the larger
amount). A library with an idle drive takes the oldest waiting recall for an item on its tapes, from the tape holding
most of it, one tape a drive. Recalls go before archiving.

If hot storage fills during a recall, what doesn't fit stays on tape and the item shows "hot storage full" until it's
asked for again.

### Timing {#timing}

A read or write takes {{config rack.tapeBaseTicks default}} ticks plus {{config rack.tapeTicksPer4k default}} ticks for
every 4,096 items, times the tape's speed from the table above, plus the picker's trip to and from the drive. So
4,096 items take 60 ticks on LTO-6 and 31 on LTO-10, before the picker. Terminals and the plan screen show how long a
recall will take, counting the queue ahead of it.

## CHGITMTIER {#chgitmtier}

{{cmd CHGITMTIER}} sets how an item is tiered, from the Terminal OS or a script. There is no per-item tier of its own:
the command works **through the Tape Libraries' keep-hot and pinned lists**. It first takes the item off every
library's lists on the network, then:

| `TIER` | What it does |
|---|---|
| `*HOT` | Adds it to the first library's keep-hot list with a free entry, and recalls everything of it that's on tape |
| `*PIN` | Adds it to the first library's pinned list with a free entry (so it's recalled whenever it's found on tape) |
| `*COLD` | Has the first online library archive it on its next go, whatever its age or the trigger (only what's on the drives; a restart forgets the request) |
| `*AUTO` | Nothing more: the archiving rules decide |

```elcl
CHGITMTIER ITEM(COBBLESTONE) TIER(*COLD)
CHGITMTIER ITEM(LOGIC_DIE) TIER(*PIN)
```

It fails with {{msg ELC1206}} when no Tape Library serves the network (a Midrange Tape Drive doesn't count), and with
{{msg ELC1305}} when every list it could go on is full. It needs the Firewall's BUILD permission (ELCL's CONFIGURE).
You see the result on the libraries' Policy tabs.
