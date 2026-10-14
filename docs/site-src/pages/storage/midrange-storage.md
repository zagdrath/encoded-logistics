---
title: Midrange storage
section: storage
order: 60
summary: The Midrange line's Disk Drive and Tape Drive and the Tape Reel - one drive as hot storage, one reel as cold storage, from early materials.
---
The Midrange line (see {{page crafting/midrange}}) has its own storage peripherals. They're built from Circuit Substrate,
a Logic Die, Ferrite and vanilla materials, so they're within reach as soon as you have a Lithography Press, long before
a Server Rack. Neither needs a Midrange System: each is an ordinary network device with one lane.

| Block | Device name | Holds | Tier |
|---|---|---|---|
| {{item disk_drive}} | DISK01 | One Storage Drive | Hot |
| {{item tape_drive}} | TAPE01 | One Tape Reel | Cold |

Both open a green screen of their own when you use them. Like the other Midrange screens it's text only, with a command
line that runs Terminal OS commands as a Terminal Desk's does (see {{page elcl/using-the-terminal}}).

## Disk Drive {#disk-drive}

A Disk Drive holds one [Storage Drive](page:storage/drives), of **any tier and any kind**, and serves it to its network
exactly as a Drive Bay slot does: the same capacity, priority 0, the same fill rules. It's one block, takes its lane from
any side but its front, and passes lanes on.

{{recipe disk_drive}}

- **Loading:** use a drive on it. It spins up for {{config rack.diskSpinUpTicks default}} ticks before the network can
  read it; the lamp lights while the pack spins.
- **Unloading:** sneak-use it with an empty hand, or 4=Unload on its screen. It spins down for
  {{config rack.diskSpinDownTicks default}} ticks, then the drive comes out to you (or out of its front, if you've gone).
- **Its screen (DSKDRV):** the drive and its state; 4=Unload pack, 5=Display contents.

It drains {{config rack.diskDriveDrain default}} FE a tick while its network runs.

## Tape Drive {#tape-drive}

A Tape Drive is a 2.5-block-tall cabinet on a 1 × 3 footprint (place it with two free blocks above). It holds one
{{item tape_reel}} as **cold storage**, in the same tier as the Tape Libraries' tapes: what's on the reel shows in
terminals, and taking it starts a recall. Its lane is on the bottom block, from any side but the front.

{{recipe tape_drive}}

- **Loading:** use a reel on it. It threads the reel for {{config rack.tapeLoadTicks default}} ticks before it works.
- **Recalls:** a waiting recall of an item on its reel is read back into hot storage.
- **Archiving:** every {{config rack.tapeArchiveInterval default}} ticks, while the network's item drives are fuller than
  {{config rack.tapeDefaultHotPercent default}} %, it writes the item type untouched longest (for at least
  {{config rack.tapeDefaultAgeHours default}} hours) onto its reel. It uses the config's values directly: it has no
  policy screen, keep-hot or pinned list of its own.
- **Timing:** a read or write takes {{config rack.tapeDriveBaseTicks default}} ticks plus
  {{config rack.tapeDriveTicksPer4k default}} ticks for every 4,096 items. The lamps on top light while it works.
- **7=Rewind** rewinds the reel to its load point ({{config rack.tapeRewindTicks default}} ticks), stopping whatever it
  was doing. **4=Unload**, or a sneak-use with an empty hand, rewinds first, then the reel comes out to you (or out of its
  front).
- **Its screen (TAPDRV):** its state and reel; 4=Unload, 5=Display contents (most first), 7=Rewind.

It drains {{config rack.tapeDriveDrain default}} FE a tick, twice that while reading or writing.

{{screenshot file="storage/midrange-disk-tape.png" alt="A Disk Drive and a Tape Drive beside a Midrange System" show="A Midrange System with a Disk Drive (lamp lit) and a Tape Drive (top lamps lit) beside it, cabled together" angle="Three blocks in front, slightly to the left, eye level" size="1600x900"}}

## Tape Reel {#tape-reel}

A Tape Reel holds {{config rack.tapeReelItems default}} items of up to {{config rack.tapeReelTypes default}} types. Unlike
drives and LTO tapes, a reel reserves no room per type: it's a plain item count. Its tooltip shows the fill
("18,420 / 65,536 items") and its volume serial (VOL001 to VOL999).

{{recipe tape_reel}}

As with drives and LTO tapes, the contents are kept in the world's save under the reel's id, given the first time it's
mounted, so a reel keeps what's on it out of the drive. Reels don't stack.

> [!NOTE]
> Tape Reels go only in a Tape Drive. Tape Libraries take LTO tapes only. When you outgrow a reel, move up to
> [LTO tapes and a Tape Library](page:storage/tapes).
