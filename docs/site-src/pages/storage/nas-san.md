---
title: NAS and SAN
section: storage
order: 40
summary: The NAS and SAN rack devices as storage - their drive bays, priority, access mode and the SAN's uplinks.
---
The {{item nas}} and {{item san}} are rack devices that hold Storage Drives, as a Drive Bay does, but with a
**priority** and an **access mode** you can set. They go in a {{item server_rack}}. This page covers how they behave as
storage; installing them, their power and lanes, and their panels as rack devices are on {{page racks/devices}}.

| Device | Size | Drives | Drain |
|---|---|---|---|
| NAS | 2U | 6 | {{config rack.nasDrain default}} FE/t, plus {{config rack.rackDriveDrain default}} FE/t per drive |
| SAN | 4U | 24, plus 4 rear uplink cages | {{config rack.sanDrain default}} FE/t, plus {{config rack.rackDriveDrain default}} FE/t per drive |

{{recipe nas}}

{{recipe san}}

Both take [Storage Drives](page:storage/drives) of any tier and kind. Item, fluid and gas drives are hot storage;
Energy Storage Drives in them are part of the network's energy pool. Drives go in through the device's panel. A drive's contents go with it when it comes out, as with any holder.

{{screenshot file="storage/nas-san-rack.png" alt="A NAS and a SAN in a Server Rack with drive sleds lit" show="A Server Rack with a NAS and a SAN fitted, front door open, drive sleds showing green and yellow fill lights" angle="Facing the rack front from three blocks away, slightly below the SAN" size="1600x900"}}

## Priority and access mode {#settings}

The device's panel has its drive slots, a capacity bar, and two settings that apply to all its drives:

- **Priority**, -999 to 999 (default 0). Higher is filled first and emptied last. Type it and press Enter, or use the
  steppers (Shift steps by 10).
- **Access mode**: read-write, read-only (the network only takes out) or write-only (it only puts in).

How priorities and modes order the network's storage, against Drive Bays (always priority 0) and Inventory Taps, is on
{{page storage/hot-and-cold}}.

Both settings travel with the device: take it out of the rack and the item keeps them.

## The SAN's uplinks {#san-uplinks}

A SAN is reached through its four rear **uplink cages**, each taking an {{item optical_transceiver}}.

- **No transceiver:** the SAN shows FAULT, "Offline - no uplink", and the network can't reach any of its drives.
- **One transceiver:** it works at its set priority.
- **Each transceiver beyond the first** adds +1 to its storage priority. With all four it stores at its setting + 3.

Its popup and panel show the uplink count.

## Storage Blades {#blades}

A Blade Enclosure holds drives too: each {{item storage_blade}} in it opens two drive bays, which behave as a NAS's do,
with the enclosure's priority and access mode. See {{page racks/chassis}}.
