---
title: Permissions
section: admin
order: 20
summary: Who may do what on a network - the Firewall's six permissions as an admin sees them, what operators can always do, the Terminal OS's SECLVL and *SECOFR, and firewallFailClosed.
---
Encoded Logistics has no permission nodes and no permissions plugin hooks. Who may use a network is decided by the
network itself, through its **Firewall**, and by whether the player is a server **operator**. This page is the admin's
view; players set Firewalls up as described on {{page racks/security}}.

## The Firewall {#firewall}

A {{item firewall}} is a 1U rack device. A network uses one Firewall; a second one shows a fault and is ignored.
Without a Firewall, everyone may do everything on that network. A block on no network is open to everyone.

| Permission | Lets a player |
|---|---|
| VIEW | See the network's items (terminals, popups, Terminal OS screens that read the network) |
| INSERT | Put items in |
| EXTRACT | Take items out |
| CRAFT | Ask for crafts |
| BUILD | Place, break and configure the network's blocks and parts (and change the Firewall's rules) |
| RACK | Open Server Racks and install, remove or set up their devices |

- The Firewall's **owner** is whoever installed it, and always has every permission.
- Its **default policy** is no access, view only, or full access (the default).
- Up to 64 listed players each have every permission set to Inherit (the default policy), On or Off. RACK on Inherit
  follows that player's BUILD.
- Placing or breaking a block on or next to a network, and mounting parts, anchors or facades on its cables, needs
  BUILD. Breaking a Server Rack also needs RACK while {{config rack.rackBreakProtection default}} is on.

In ELCL, a command's CONFIGURE authority is the Firewall's BUILD permission; the command reference gives each
command's authority.

## Operators {#operators}

Players with operator permission level 2 ("gamemaster", the level that may use `/gamemode` and `/give`) are never
stopped by a Firewall: on any network they may do everything, so a network can't lock everyone out. In the Terminal
OS they are `*SECOFR` (below) on every system while they are online. They may also put a Small Wireless Bridge on any
Arcforge machine, open any PLC, and use {{page admin/commands}}.

## When the Firewall is offline {#fail-closed}

A Firewall with no lane or no power is offline. {{config rack.firewallFailClosed default}} decides what that means:

| `firewallFailClosed` | An offline Firewall |
|---|---|
| `true` (the default) | Still enforces its rules: players keep only what it grants them |
| `false` | Enforces nothing: everyone may do everything until it is back online |

Leave it on unless you have a reason: with it off, cutting a network's power opens it to everyone.

## In the Terminal OS {#terminal-os}

The Terminal OS has its own users and security on top of the Firewall (see {{page elcl/index}}):

- Each player has a **user profile** on each system (network), made the first time they use it.
- **`*SECOFR`** (security officer) is the network's owner (its Firewall's owner) and any operator who is online. Every
  other user is `*USER`. Only `*SECOFR` may change system values ({{msg ELC0401}} otherwise), manage other users' jobs,
  or read another user's message queue.
- The system value **SECLVL** sets how much the OS checks:

| SECLVL | Sign-on | Authority in the OS |
|---|---|---|
| `30` (the default) | Needed on a network with a Firewall: each player signs on as themselves | The Firewall's permissions apply to every command |
| `10` | None | Everyone has full authority in the OS; the Firewall still guards the blocks themselves |

On a network without a Firewall there is nothing to sign on to, and everyone has full authority.

Programs run with someone's authority: a batch job with its submitter's, and a PLC's program on a network with the
authority of the player who last loaded it.

## What isn't guarded {#unguarded}

- Display Panel commands, and touching a screen, need no permission (the screen's configuration screen needs BUILD).
- Every library gives the public read access, so any user may read any library's files; only the system's own files in
  ELSYS are guarded (VIEW).
- Display, print and OS commands need no Firewall permission; the OS checks its own library, job and system value
  authority.
