---
title: Exchange Switch and Directory Server
nav: Exchange and Directory
section: networking
order: 30
summary: The Exchange Switch where many networks' Routers meet and peer, the Cross-Connect that joins them, and the Directory Server that lets other networks reach your devices by name.
---
Once several networks have [Routers](page:networking/routers), two questions come up. How do lots of networks
connect without every Router linking to every other? And how does one network's Terminal OS work a device on another?
The Exchange Switch answers the first, the Directory Server the second.

## The Exchange Switch {#exchange}

The {{item exchange_switch}} is an Internet Exchange: a place where Routers from any number of networks meet and peer
with each other. It is a block, two blocks tall.

{{recipe exchange_switch}}

- **Placing:** it needs the space above free, and faces you. Whoever places it **owns** it. Breaking either half breaks
  both.
- **On the network:** its lower block is a network device, cabled on any side except the top. It uses one lane and drains
  {{config rack.exchangeDrain default}} FE/t. Its LEDs and member count light up while it's online.
- **Its name** shows in route tables as `IX:name`. Rename it in an anvil, or in its panel's Setup tab: up to 8 letters
  and digits, starting with a letter. Without one it uses its device name (`XSW01` and so on).
- **Chunk loading:** while it has members it keeps its own chunk loaded, as long as
  {{config rack.exchangeChunkLoading default}}.

{{screenshot file="networking/exchange-switch.png" alt="An Exchange Switch block with its LEDs lit" show="An Exchange Switch standing on the floor next to a Server Rack, online with its LEDs and member count lit, a cable into its lower block" angle="Two blocks in front, eye level" size="1600x900"}}

### Joining with a Cross-Connect {#cross-connect}

A Router joins an exchange with a {{item cross_connect}}, a patch lead from the Router to the exchange.

{{recipe cross_connect}}

1. Open the front door of the Router's rack and use the Cross-Connect on the Router's unit. It records that Router. This
   needs **Build** on the Router's network.
2. Use it on the Exchange Switch. The Router joins (or asks to), and one Cross-Connect is used up.

Sneak-use a Cross-Connect in the air to clear it.

**Membership** is **Open** (members join at once) or **Approval** (a join is a request the owner approves in the
Members tab). Each member takes a **port**: at most {{config rack.exchangePorts default}}, and the owner can offer fewer
(never fewer than it has members).

### Peering {#peering}

Members exchange traffic in **peering sessions**, two ways:

- **The route server:** while it's on (the default), every member who has opted in (also the default) peers with every
  other such member.
- **Bilateral peering:** any two members can peer directly once each has accepted the other in the Peers tab.

A session is up while the exchange is online and both Routers are up: online and in **Dynamic** routing mode. A Static
Router can join but never peers.

Each member decides for its own side, per peer:

- whether it **exchanges routes** with that peer. If not, the peering only carries traffic between their two networks,
  never on to others;
- whether it allows that peer's traffic to **transit** its network: as its Router's transit setting says (the default),
  always, or never;
- an **ACL** for the peer, applied in its Router's ACLs tab (see [ACLs](page:networking/routers#acls)).

A peering carries up to {{config rack.exchangePortLanes default}} items a second, or less if the Routers at either end are
slower. Peerings appear in the Routers' route tables with `IX:name` as the next hop.

### The panel and who may do what {#exchange-panel}

Right-click the Exchange Switch for its panel, with three tabs: **Members**, **Peers** and **Setup**.

| Action | Who |
|---|---|
| Approve requests, switch Open/Approval, turn the route server on or off, change the ports, rename | The owner (or a server operator) |
| Remove a member | The owner, or a player with Build on that member's network |
| Opt in or out of the route server, accept a bilateral peer, set peer routes and transit | A player with Build on that member's network |

The owner can remove members but never touches their Routers: each network's own Firewall governs its Router.

In the Terminal OS, {{cmd WRKIX}} shows an exchange's members and peerings, and {{cmd APVIXMBR}} and {{cmd RMVIXMBR}}
approve and remove members.

{{layout 26_wrkix}}

## The Directory Server {#directory}

A device's name, like `LGT01` or `PLC02`, normally means a device on your own network. A
{{item directory_server}} lets **other** networks reach chosen devices of yours too, as **`SYSNAME/DEVICE`**: your
system's name, a slash, and the device's name. Any Terminal OS command that takes a device accepts such a name.

{{recipe directory_server}}

It comes in two forms that work the same: the block, and the 1U {{item directory_server_rack}} for a
[Server Rack](page:racks/devices#directory-server).

- **The block** connects to cables on every side, uses one lane and drains {{config rack.directoryDrain default}} FE/t
  (the rack form the same). Its display glows while it's online.
- **Its device name** is `DIR01` and so on.

### Publishing devices {#publish}

Only devices you publish can be reached from elsewhere. Open the Directory Server's panel (right-click the block, or pick
the rack form in the rack screen):

- **Published:** your network's devices. Click one to publish it, or to stop publishing it. Publishing needs **Build** on
  the network.
- **Remote:** the other networks your Routers reach that have a Directory Server, with their hop count and whether a
  Control policy goes there.
- **Lookups:** the last 16 lookups made from your network, and how each went.

{{cmd CHGDIRENT}} publishes or unpublishes a device from a program, and {{cmd WRKDIR}} shows a directory on screen.

### How a name resolves {#resolve}

When a program on network A uses `ELNET02/LGT01`, the name resolves only when all of these hold:

1. a [Router](page:networking/routers) on network A has a route to ELNET02's network (in its route table);
2. an online Directory Server on ELNET02's network publishes `LGT01`;
3. one of those Routers has a **Control** route from network A to that network, with a path, and its ACLs permit
   `*CONTROL`;
4. the player running the command has the command's Firewall permission on **ELNET02's** network.

A name on your own network resolves as a plain name. If the name isn't published or there's no route to its network,
the command fails with {{msg ELC2603}}; if the Control policy, an ACL or the Firewall doesn't permit it, with
{{msg ELC2604}}.

Where a name's network is gets remembered for {{config rack.directoryCacheSeconds default}} seconds (or until a route or
any network changes). Whether the device is still published, the Control policy, the ACL and the Firewall are checked
every time.

```elcl
/* Disable Cage Light LGT01 on the warehouse network (ELNET02), through its Directory Server. */
CHGDEVSTS DEV(ELNET02/LGT01) STATUS(*DISABLE)
```

{{cmd RTVDIRENT}} looks a name up without using it: its device type, its network and how many hops away it is.

{{layout 27_wrkdir}}
