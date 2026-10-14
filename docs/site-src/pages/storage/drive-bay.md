---
title: Drive Bay
section: storage
order: 20
summary: The Drive Bay, a block that holds ten Storage Drives and makes them part of its network's storage.
---
The {{item drive_bay}} is the first drive holder you'll build, and the one most networks are built around. It holds ten
[Storage Drives](page:storage/drives) of any tier and any kind (items, fluids, gases, energy), and while it's on a running
network they're part of that network's storage.

{{recipe drive_bay}}

## Setting one up

1. Place it. It faces you, and cables connect to it on every face except the front.
2. Cable it to the network. It's a network device and uses one lane (see {{page networks/lanes}}).
3. Open it and put drives in its slots. The drives are part of the network's storage at once.

The Drive Bay drains {{config storage.driveBayDrain default}} FE a tick while its network runs, plus
{{config storage.driveBayDrainPerDrive default}} FE a tick for each drive in it.

{{screenshot file="storage/drive-bay-gui.png" alt="The Drive Bay screen with drives and their fill bars" show="The Drive Bay screen with six or seven drives of different tiers in it, their fill bars at different levels, and Online in the title strip" angle="GUI scale 3, screen centred" size="1280x720"}}

## The screen and the front

The screen lays out the ten slots as the front does, two columns of five. Each drive has a fill bar beside it in its
light's colour (green, yellow, orange, red: see [fill lights](page:storage/drives#lights)), and the title strip says
**Online** or **Offline**.

On the block, each drive shows as a sled in its slot, coloured by tier, with its status light. The lights go dark while
the bay is offline (no power, no lane, or no controller).

## How the network uses it

- Item, fluid and gas drives in a Drive Bay are hot storage at **priority 0**, read and write. A Drive Bay has no
  priority or access setting of its own: for those, use a [NAS or SAN](page:storage/nas-san) or an Inventory Tap. See
  {{page storage/hot-and-cold}} for how priorities order the network's storage.
- Energy drives in a Drive Bay are part of the network's energy pool, not storage. See
  [Energy drives as batteries](page:storage/drives#energy).

Taking a drive out (or breaking the bay) takes its contents with it: they live under the drive's id, not in the bay. Put
the drive in any holder and they're back.

> [!TIP]
> One Drive Bay of 8K drives is plenty to start with. When you run short, swap drives for bigger ones rather than adding
> bays: a 32K drive holds four times an 8K and costs the same lane and drain.
