---
title: Jade
section: integrations
order: 30
summary: The Jade tooltips Encoded Logistics adds, and the blocks it keeps out of Jade because they have popups of their own.
---
With Jade installed, looking at some of the mod's blocks adds a few lines to Jade's tooltip. Jade's own energy bar
reads any block's FE, so it also shows, for example, a Network Controller structure's whole buffer.

| Block | Jade shows |
|---|---|
| {{item network_controller}} | Whether the network is online, its lanes in use against its capacity ("0 / 32 lanes"), and the structure ("3x3x3 frame, 20 blocks") |
| Network, Dense and Fiber Cables | The lanes running through it ("Lanes 8 / 32") |
| {{item power_inlet}} | What it is receiving ("Receiving 512 FE/t") |
| {{item segment_isolator}} | "Isolating two segments" or "Idle" |
| {{item lithography_press}} | Its progress ("Exposure 40%") |
| {{item drive_bay}} | How many drives it holds ("3 / 10 drives") |

Each of these can be turned on or off in Jade's own plugin settings, under Encoded Logistics.

## Names {#names}

A cable with parts or a facade on it, and the internal block that holds parts on a block face, are named in Jade after
the part or facade you are looking at, rather than "Network Cable".

Looking at any block of a [Midrange](page:crafting/midrange) machine that takes more than one block of room (a Tape
Drive or an Integrated Midrange System, say) shows the machine's main block, so you see the reel in a Tape Drive even
from its top.

## Blocks Jade leaves alone {#hidden}

These have their own popups, so Jade shows nothing for them:

- the {{item server_rack}} (a popup for each unit you look at);
- the {{item small_wireless_bridge}};
- the Midrange {{item disk_drive}} and {{item tape_drive}}.
