---
title: Handheld Terminal
section: terminals
order: 30
summary: An Access Terminal you carry - linking it to a network, how far it reaches through Relay Antennas or a Wireless Controller, its battery and its signal bars.
covers: [encodedlogistics:handheld_terminal]
---
The {{item handheld_terminal}} is an [Access Terminal](page:terminals/block-terminals) in your pocket. It opens the same
storage screen, from anywhere its network can reach you. It needs three things to open: a **link** to a network,
**reach** to that network (a Relay Antenna covering you, or a Wireless Controller), and **charge** in its battery.

{{recipe handheld_terminal}}

## Linking it {#linking}

There are two ways to link a Handheld Terminal, and they reach differently.

| Link it by using it on | It reaches you | Needs |
|---|---|---|
| A {{item relay_antenna}} or a {{item network_controller}} block | Inside the coverage of any of that network's Relay Antennas, in the same dimension | Relay Antennas where you want to use it |
| A {{item wireless_controller}} in a rack (its unit through the open front door, or anywhere on its rack) | Anywhere in the dimension, and in other dimensions while {{config rack.wirelessCrossDimension default}} | A Wireless Controller online on the network |

- **Relay Antenna or Network Controller:** the action bar says "Linked to network #n". Linking this way also clears any
  earlier Wireless Controller link.
- **Wireless Controller:** the action bar says "Linked to this Wireless Controller (network n): works anywhere". This
  needs **Rack access** on the rack's network, as anything you do at a rack does. The controller remembers you (its
  panel's Linked tab lists the players it serves, and an X there unlinks one). A terminal linked to a controller still
  uses the network's Relay Antennas too, when one covers you.

The terminal must be linked to a network that is running. Used on a block that isn't on one, it says "Not on a working
network".

While {{config rack.wirelessAnyController default}}, any online Wireless Controller on the network serves a terminal
linked through one of them, so a second controller backs up the first. With it off, only the controller it was linked
to will do. More on Wireless Controllers on the {{page networking/wireless}} page.

You don't need Access Points for Handheld Terminals: Access Points are for wireless *clients* (Wireless Bridges and
Ports), not for terminals.

## Opening it {#opening}

Use it in the air (right-click with it in your hand, main hand or off hand). If it isn't linked, the action bar says
"Not linked"; with an empty battery "Battery empty"; and with no antenna or controller reaching you "Out of range".

{{screenshot file="terminals/handheld-screen.png" alt="The Handheld Terminal screen with its battery bar, link light and signal bars" show="An open Handheld Terminal with items in the grid; the side panel showing the battery bar, the link light and the signal bars" angle="GUI scale 3" size="1280x720"}}

The screen is the terminal screen described on {{page terminals/block-terminals}}, with a side panel showing:

- **the battery bar,**
- **a link light,**
- **signal bars,** 0 to 4.

If you walk out of range while it's open, the grid says why it can't reach the network.

## Range and signal {#range}

**Through Relay Antennas.** A [Relay Antenna](page:networking/bridges-and-links#relay-antenna) covers a sphere around
itself of {{config reach.relayBaseRange default}} blocks, plus {{config reach.relayRangePerTransceiver default}} blocks
for each Optical Transceiver in its four slots. The terminal uses whichever of its network's antennas covers you best
(the nearest relative to its range), picked again every half second as you move. Antenna coverage works only in the
antenna's own dimension.

The signal bars show how deep in an antenna's coverage you are:

| Your distance, as a share of the antenna's range | Bars |
|---|---|
| Up to a quarter | 4 |
| Up to a half | 3 |
| Up to three quarters | 2 |
| Out to the edge | 1 |
| Not covered | 0 |

**Through a Wireless Controller.** No range limit and full bars, as long as the controller is online (and, across
dimensions, {{config rack.wirelessCrossDimension}} is on).

The terminal's icon shows its link state: not linked, linked (in reach), or out of range. It updates once a second
while the terminal is in your inventory.

## Battery {#battery}

The terminal holds {{config reach.handheldCapacity default}} FE. It uses:

- {{config reach.handheldDrainPerSecond default}} FE a second while its screen is open, and
- {{config reach.handheldEnergyPerItem default}} FE for every item you move with it.

The tooltip shows the charge. To charge it:

- **In any FE charger** from another mod: it takes up to {{config reach.handheldChargeRate default}} FE/t.
- **At a {{item capacitor_bank}}:** use the terminal on the bank and hold the button. It draws up to
  {{config reach.handheldChargeRate}} FE a tick from the bank until it's full or you look away.

## Permissions {#permissions}

The terminal works with your own Firewall permissions on its network, the same as a terminal on a cable: View to open
it, Insert, Extract and Craft for the rest. See {{page racks/security}}.
