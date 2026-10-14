---
title: Storage
section: storage
order: 0
summary: How a network stores items, fluids, gases and energy on drives, and how it moves what nobody uses out to tape.
---
A network keeps what you put into it on **Storage Drives**. A drive is an item: you craft it from a Storage Die, put it in
a drive holder on the network, and from then on everything the network stores goes onto the drives in its holders. Take
a drive out and its contents go with it; put it back, or into another holder, and they come back.

There are four kinds of drive, one for each resource the network handles (items, fluids, pressurized gases and energy),
in five sizes from 8K to 2M. All of them use the same byte model, so a die of a given size makes a drive of the same
capacity whatever it stores.

## Hot and cold

Storage comes in two tiers:

- **Hot storage** is what the network can hand you at once: the drives in its Drive Bays, Disk Drives, NAS and SAN rack
  devices and Blade Enclosure Storage Blades, plus any inventories its Inventory Taps face.
- **Cold storage** is tape: LTO tapes in Tape Libraries, and the Tape Reel on a Midrange Tape Drive. A Tape Library moves
  items nobody has touched for a while from the drives onto tape, and brings them back (a *recall*) when you ask for them.

Terminals show both tiers together, so you never have to know where an item is. Taking more of an item than is hot
starts a recall of the rest. The crafting planner counts what's on tape as stored, too.

## Where to go next

1. {{page storage/drives}}: the dies and drives, how much each holds, the fill lights, energy drives as batteries.
2. {{page storage/drive-bay}}: the first drive holder you'll build.
3. {{page storage/hot-and-cold}}: priorities, access modes, the order the network fills and empties its storage, archive
   and recall.
4. {{page storage/nas-san}}: drives in a Server Rack.
5. {{page storage/tapes}}: LTO tapes, Tape Libraries and their archiving policy.
6. {{page storage/midrange-storage}}: the Midrange line's Disk Drive, Tape Drive and Tape Reel, the early way to the same
   features.

To reach what's stored you need a terminal: see {{page terminals/block-terminals}}. Inventory Taps, which make a chest or
tank part of the network's storage, are on {{page networks/cable-parts}}.
