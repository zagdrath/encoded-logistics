---
title: Firewall and security
nav: Security
section: racks
order: 40
summary: The Firewall - its six per-player permissions, the default policy, owners and operators, what happens when it's offline, and exactly what each permission guards.
---
A network with no Firewall lets everyone do everything. That's fine on your own, but on a shared server anyone could
walk up to your terminal and empty it. The {{item firewall}} is a 1U rack device that decides, player by player, who
may use its network and how.

{{recipe firewall}}

## Setting one up {#setup}

1. Put a Firewall in a [Server Rack](page:racks/server-rack) on the network. Whoever installs it becomes its **owner**,
   and the owner always has every permission.
2. Open its panel in the rack screen. Pick the **default policy** for everyone not listed.
3. Add the players you want to treat differently, and set their permissions.

One Firewall guards a whole network. A second Firewall on the same network shows a fault ("Fault - another Firewall is
in use") and is ignored. The Firewall drains {{config rack.firewallDrain default}} FE/t and uses one lane.

{{screenshot file="racks/firewall-panel.png" alt="The Firewall panel with the default policy and a list of players with permission toggles" show="The rack screen with a Firewall picked: the default policy button, the owner at the top of the list, two or three other players with a mix of allowed, denied and inherited permission icons, and the name field" angle="GUI scale 3" size="1280x720"}}

## The six permissions {#permissions}

| Permission | Lets a player |
|---|---|
| **View** | See the network's contents: open terminals, the Terminal Desk, the Schematic Terminal |
| **Insert** | Put items, fluids and gases into the network |
| **Extract** | Take things out of the network |
| **Craft** | Ask the network to craft |
| **Build** | Place, break and configure the network's blocks and parts, including mounting parts, anchors and facades on its cables |
| **Rack access** | Open the network's Server Racks (doors and screen), and install, remove and set up their devices |

Each permission, for each listed player, is one of:

- **Allowed** (on),
- **Denied** (off), or
- **Inherit**: it follows the default policy. **Rack access** on Inherit follows the player's **Build** permission
  instead, so by default the players who may build may also open racks. You can grant rack access without build, or
  build without rack access.

### The default policy {#policy}

Everyone without a setting of their own (and every Inherit setting) follows the default policy:

| Policy | Means |
|---|---|
| **No access** | Nothing |
| **View only** | View, nothing else |
| **Full access** | Everything. This is the default for a new Firewall |

So a fresh Firewall changes nothing until you change its policy or list someone. A common setup is **View only** as the
default, with your friends set to Allowed for what they need.

### The panel {#panel}

- **Default policy:** click to step forward through the three, right-click to step back.
- **The player list:** the owner heads it ("Owner: always has every permission"). Each player has six toggles; click
  one to step it Inherit, Allowed, Denied. Hover a toggle to see what it does for that player.
- **Removing a player:** right-click their row.
- **Adding a player:** type an online player's name in the name field (<kbd>Tab</kbd> completes it) and press Add.
- A Firewall lists up to 64 players.

Only players who may **Build** on the network (and server operators) can change the Firewall. Everyone else sees the
panel dimmed, and is told "Only players who may build on this network can change its Firewall" if they try.

Looking at the Firewall in the rack shows the default policy and how many players have custom permissions.

## Who is never stopped {#exceptions}

- **Server operators** (players with the gamemaster permission level) are never stopped by a Firewall, so a network can
  never lock everyone out.
- **The owner** has every permission on their own Firewall.
- **Blocks on no network** (no controller) are open to everyone.
- **A network with no Firewall** is open to everyone.

## When the Firewall is offline {#fail-closed}

A Firewall that's offline (no power, no lane) still enforces its rules while {{config rack.firewallFailClosed default}}.
That way, cutting a network's power doesn't open it up. Turn the option off and an offline Firewall stops guarding
anything until it's back.

## When you're denied {#denied}

- For most things, the action bar says "Firewall: you have no *permission* permission on this network", and nothing
  happens.
- At a rack, or with a Link Card, the latch rattles (at a rack) and the action bar says "Access denied: SYSNAME
  Firewall", naming the network's system.

## What each permission guards {#guards}

| Action | Permission |
|---|---|
| Open an Access, Fabrication or Handheld Terminal, or a Rack Console's terminal | View |
| Open the Terminal Desk's Terminal OS | View |
| Put items into storage from any terminal | Insert |
| Take items out of storage from any terminal; fill a Fabrication Terminal's grid from the network | Extract |
| Request a craft | Craft |
| Place or break a network block on, or next to, a network | Build |
| Mount a part, Cable Anchor or Cable Facade on a cable | Build |
| Open any cable part's screen other than a terminal (ports, taps, sensors, planes, Trunk Port) | Build |
| Open a rack's doors or screen; install, remove or use a rack device; change device settings | Rack access |
| Break a Server Rack (while {{config rack.rackBreakProtection}} is on) | Rack access and Build |
| Change Router routing settings, ACLs or a Control policy; switch port modes; chassis part settings | Build (on top of Rack access) |
| Put parts in or take them out of a chassis | Rack access |
| Record or apply a Rack Elevation | Rack access (and Extract to use network storage as a source) |
| Write or apply a Link Card, or use a Cross-Connect on a Router | Build at each end (see below) |
| Change the Firewall's own rules | Build |

## Server Racks and Link Cards {#rack-security legacy="rack-security"}

The Firewall guards the network's hardware as well as its storage. These rules work the same as everywhere else: no
Firewall means everyone may, an offline Firewall follows {{config rack.firewallFailClosed}}, and server operators are
never stopped. The Terminal OS's `SECLVL` system value doesn't change them: it only relaxes the OS.

- **Rack access** is needed to open a Server Rack's front or rear doors, open its screen, install or remove devices, use
  a device through the open front, and change device settings on their panels. Denied, the door doesn't open, the latch
  rattles, and the action bar shows "Access denied: SYSNAME Firewall".
- **Unlocked racks:** a rack on no network is open to everyone, as is a rack on a network with no Firewall. There is no
  lock or key item; "locking" a rack is the Firewall's Rack access permission.
- **Breaking:** breaking any network block needs Build. Breaking a Server Rack, which drops its devices, needs Rack
  access as well, unless {{config rack.rackBreakProtection}} is off.
- **Automation:** a rack has no inventory for hoppers or pipes, so nothing can pull devices or drives out of it.
- **Link Cards:** pairing or linking with a Link Card needs Build on the network at *each* end. Where an end is a device
  in a Server Rack (a Router, a switch, a Wireless Controller, a multiplexer or shelf), that end needs Rack access
  instead. It's checked twice: when the card is written (the end you click) and when it's applied (both ends). That
  covers Network Bridges, Point-to-Point Links, Routers, switch segments, Trunk Ports, optical pairs, and Wireless
  Bridges and Ports. Denied, the card isn't written or applied, and you get the same "Access denied" message.
- **Existing links** made before these checks, or before a player lost permission, keep working.

## The Terminal OS {#terminal-os}

Inside the Terminal OS each command needs a Firewall permission too: View, Insert, Extract, Craft, or **Configure**,
which is the Firewall's Build permission. Display, print and OS commands need none (the OS checks its own library, job
and system value authority). A batch job is checked against the permissions of the player who submitted it, whether
they're online or not. The Firewall's owner and online server operators are the OS's security officers (`*SECOFR`).
All of that, and the `SECLVL` system value, are on {{page elcl/index}}; the admin view is on {{page admin/permissions}}.

A device on another network, reached as `SYSNAME/DEVICE` through a [Directory Server](page:networking/exchange-and-directory#directory),
needs the command's permission on *that* network's Firewall.
