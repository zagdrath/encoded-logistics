---
title: Routers
section: networking
order: 20
summary: The Router - linking separate networks with a Link Card, Move and Share routes with filters, static and dynamic routing, transit, the route table and neighbours, and ACLs.
---
A {{item router}} is a 1U [rack device](page:racks/devices) at the edge of your network. It moves items between
**separate networks**: networks that each keep their own controller, storage and Firewall, however far apart and in
whichever dimension. Your mining outpost, a friend's base, the nether factory: link each to a Router, and set up routes
for what should flow where.

{{recipe router}}

A Router is not how you join places into one network (that's a [Network Bridge](page:networking/bridges-and-links) or
an [optical channel](page:networking/optical)), and it doesn't route between the segments of one rack (that's the
[L3 Switch](page:networking/switches#l3-switch)). It routes between whole networks.

## Linking networks {#linking}

A Router's **endpoints** are its own network (the one its rack is on) plus up to five networks you link to it, six in
all.

1. Sneak-use a {{item link_card}} on any block of the other network: a cable, a device, a Drive Bay. The card stores
   that network. (A Segment Isolator half stores a segment instead, which a Router also accepts.)
2. Open the front door of the Router's rack and use the card on the Router's unit. The action bar says it's linked, that
   the Router already knows it, or that the Router is full.

Linking needs **Rack access** at the Router's rack and **Build** on the other network. The Router finds a linked network
through the block you stored on the card, so a route only works while that block is loaded and on a running network.

In the panel's **Routes** tab you can rename endpoints (up to 24 characters) and unlink a network, which removes the
routes that use it.

## Routes {#routes}

A Router holds up to 6 routes. A route goes from one endpoint to another (click its source or destination to step
through them) and has a mode. Click the mode to step Move, Share, Control.

| Mode | What it does |
|---|---|
| **Move** | Once a second, moves the items its filter passes from the source network's storage into the destination's |
| **Share** | Moves nothing. The source's items that pass the filter become part of the destination's storage, as its terminals and crafting see them |
| **Control** | Moves nothing and shows nothing. Lets the source network's Terminal OS reach devices the destination's Directory Servers publish (see {{page networking/exchange-and-directory#directory}}) |

**Share options:** **Read** (see and take out) or **Read/Write** (put in too, once the destination's own storage is
full); whether crafting may use the shared items; and one way or both ways (bidirectional).

**Filters.** Each route has a 9-entry filter with deny, tag and component options, as a port's has. An empty allow list
passes nothing, so a new route moves nothing until you set it up; an empty deny list passes everything. You can drag
items from JEI onto a route's filter box.

**Throughput.** A Router moves up to {{config rack.routerBaseRate default}} items a second, plus
{{config rack.routerRatePerTransceiver default}} for each {{item optical_transceiver}} in its three cages, shared between
its active Move routes in turn. Each transceiver also adds {{config rack.routerDrainPerTransceiver default}} FE/t to its
drain of {{config rack.routerDrain default}}. The Routes tab shows the rate, the items actually moved per second, and
which endpoints are reachable now.

**Permissions.** Rack access is enough to add and edit routes. Removing or retargeting a route that has an ACL, and
stepping a route into or out of Control (or out of Share), need **Build** as well, so nobody with only rack access can
get round an ACL.

{{screenshot file="networking/router-routes.png" alt="The Router panel's Routes tab with linked networks and routes" show="The rack screen with a Router picked, Routes tab: three linked networks with names, three routes (two Move with filters set, one Share), two transceivers in the cages, the throughput figure" angle="GUI scale 3" size="1280x720"}}

## Static and dynamic routing {#dynamic}

Every Router starts in **Static** mode: it routes between its own endpoints and nothing more. Switch it to **Dynamic**
in the **Mode** tab (needs **Build**) and it takes part in dynamic routing with every other Dynamic Router on the server.

- **Neighbours.** Two Dynamic Routers are neighbours when one is linked to the other's network, or both are members
  peering at an [Exchange Switch](page:networking/exchange-and-directory#exchange).
- **Hellos.** Each Dynamic Router says hello once a second. A neighbour heard from goes **Init**, then **Full** once
  each has seen the other. One that misses about three hellos goes **Down** (sooner if needed to reconverge within
  {{config rack.reconvergeSeconds default}} seconds).
- **What it learns.** Each Router advertises its network, the networks it links to, and its transit policy. Through its
  Full neighbours it learns every network beyond them, and works out the cheapest path to each, at most
  {{config rack.maxHops default}} hops long (fewer hops break ties). An advertisement not refreshed for a minute ages out,
  so an unloaded Router drops off.
- **Cost.** Each hop costs more the slower the Routers on it: {{config rack.routingBaseCost default}} scaled down by
  the hop's throughput, so Routers with more transceivers make cheaper paths. In the Mode tab you can raise or lower
  the cost to a neighbour in steps of 10 to steer traffic, or clear it; {{cmd CHGNBR}} does the same.

With dynamic routing a route's destination can be **any network in the route table**, not just an endpoint: click the
destination to step past the endpoints into the table. Items on such a remote route travel hop by hop along the best
path, each Router on the way carrying them and spending its own throughput, and never land in a network on the way.
A remote route with no path right now pauses (nothing moves, a Share shows nothing) until there's one again.

A Router's own links always win over paths it learns. Its whole throughput, for its own routes and for traffic passing
through it, is the one rate above.

### Transit {#transit}

Whether other networks' traffic may pass *through* a Router's network is its **transit** setting, in the Mode tab:

| Transit | Lets through |
|---|---|
| **None** (the default) | Nothing: its network is only ever the start or end of a path |
| **Listed** | Traffic from the networks you tick in its route table, up to 16 |
| **All** | Everyone's traffic |

A path crosses a network only where its Routers allow transit for the traffic's source. At an exchange, each member can
also allow or refuse transit to each peer.

## The route table and neighbours {#table}

The **Table** tab lists every network the Router can reach:

| Column | Shows |
|---|---|
| Network | The destination network's name |
| Next | Where the path goes first: `direct`, a network, or `IX:name` for an exchange |
| Cost, hops | The path's cost and length |
| Source | `*STATIC` (its own Link Card links, always listed first), `*CONNECTED` (one hop away, through a neighbour), `*DYNAMIC` (learned, further away) |
| Age | How long the entry has been the same |

The **Mode** tab lists the neighbours: their network, how they're reached (a link, or an exchange), their state, and
their cost and any override.

In the Terminal OS, {{cmd DSPROUTE}} and {{cmd DSPNBR}} show the same tables, {{cmd RTVRTE}} and {{cmd RTVNBRSTS}} read
them into a program, and the trigger events `*RTECHG`, `*NBRUP` and `*NBRDOWN` fire when a best path changes or a
neighbour comes up or goes down.

{{layout 23_dsproute}}

## ACLs {#acls}

An ACL (access control list) limits what a Router lets through. Each Router holds up to 8 named ACLs (names up to 10
letters and digits, starting with a letter) of up to 16 rules each.

**Applying an ACL.** In the **ACLs** tab, apply an ACL to a **route**, or to a **network the Router reaches directly**
(a linked network, a neighbour's, or an exchange peer's). A Move then moves only what the ACLs on its way permit, and a
Share shows only what they permit. Creating, editing and applying ACLs needs **Build**.

**How a rule is chosen.** The rules are checked in order; the first that matches the resource and its direction
decides. If none matches, the default applies: permit while {{config rack.aclDefaultPermit default}}, deny otherwise.
Each rule counts its hits and when it last matched.

### Rule syntax {#acl-syntax}

The panel's rule editor takes a rule as one line:

```text
MATCH [permit|deny] [in|out|both] [N/m] [N/d]
```

| Part | Means |
|---|---|
| `MATCH` | What it matches: an item (`COAL`, `minecraft:coal`), a tag (`#minecraft:logs`), a fluid or gas (`FLUID minecraft:water`), `*ITEM`, `*FLUID`, `*PRES` (gases), `*ENERGY`, `*ANY`, or `*CONTROL` (Directory control requests) |
| `permit` / `deny` | Whether it lets the match through. Permit if left out |
| `in` / `out` / `both` | Which way: `in` is into the Router's own network, `out` is out of it. Both if left out |
| `N/m` | A rate: at most N a minute. Past it the rule denies until the minute turns |
| `N/d` | A quota: at most N a game day. Past it the rule denies until the day turns |

For example, `COAL deny out` keeps your coal at home, and `*ANY permit out 64/m 2000/d` lets anything out but only 64 a
minute and 2,000 a day. A rule's rate and quota count what Move routes move; a Share honours them once Moves have used
them up.

When a rule denies something, the Terminal OS's `*ACLDENY` trigger event fires (at most once a second for each rule).

### ACLs from the Terminal OS {#acl-elcl}

{{cmd WRKACL}} works with a Router's ACL on screen. {{cmd ADDACLE}}, {{cmd CHGACLE}} and {{cmd RMVACLE}} add, change and
remove rules from a program; {{cmd ADDACLE}} makes the ACL if it doesn't exist yet. Rules are numbered in tens, so you
can insert between them.

```elcl
/* Keep coal at home, and limit logs leaving to 64 a minute. */
ADDACLE RTR(ROUTER01) ACL(EXPORT) MATCH(COAL) DIR(*OUT) ACTION(*DENY)
ADDACLE RTR(ROUTER01) ACL(EXPORT) MATCH('#minecraft:logs') DIR(*OUT) ACTION(*PERMIT) RATE(64)
```

You still apply the ACL to a route or network in the Router's ACLs tab.

{{layout 25_wrkacl}}

## Exchanges {#exchanges}

A Dynamic Router can join an [Exchange Switch](page:networking/exchange-and-directory#exchange) with a {{item cross_connect}}:
use the Cross-Connect on the Router's unit (needs **Build** on its network), then on the Exchange Switch. At the exchange
it peers with other members' Routers. Exchange peerings show in its route table with `IX:name` as the next hop.
