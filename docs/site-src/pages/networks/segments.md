---
title: Segments
section: networks
order: 50
summary: Splitting a cable run into separate networks with the Segment Isolator, and using those segments from racks, Trunk Ports and routers.
---
A {{item segment_isolator}} cuts a cable run in two. The two sides become separate networks, called **segments**:
lanes, storage, devices and energy don't cross it. Segments let you keep parts of a base apart (a public storage
network beside a private one, say) while still letting racks and routers reach across in controlled ways.

## Segment Isolator {#isolator}

{{recipe segment_isolator}}

- **Placing.** It's an inline housing, placed like a log: its axis follows the face you click. It connects **only on
  its two ends**, so put it in line with the cable you want to cut.
- **What it does.** The network on each side stops at the isolator. Each side is its own network, with its own lanes,
  storage, devices and energy.
- **Cost.** It uses no lanes. It drains {{config power.isolatorDrain default}} FE per tick, and since it's on both
  networks, each side pays that.
- **Its light.** The amber light is on while both ends are attached to network blocks: it's actually separating two
  segments. Jade shows "Isolating two segments" or "Idle".

{{screenshot file="networks/segment-isolator.png" alt="A Segment Isolator splitting a cable run between two controllers" show="A cable run with a Segment Isolator in line, its amber light on, a Network Controller on each side so both segments are online, and a Drive Bay on each side" angle="Side view from three blocks away at cable height, so the isolator's housing and both controllers are in frame" size="1600x900"}}

> [!NOTE] Each side needs its own controller
> The side without a controller isn't a network on its own: its devices are offline. That's
> also what makes isolators useful for separating two controllers: two controllers on one network are a
> [conflict](page:networks/controllers#conflicts), but with an isolator between them each runs its own segment.

## Using a segment elsewhere {#link-card}

A segment is named by a {{item link_card}}. **Sneak-use the card on the isolator** to store the segment on the half you
clicked ("Segment on this side stored"). You can then use the card on:

| Use the card on | What it does | Guide |
|---|---|---|
| A switch's unit in an open {{item server_rack}} | The rack learns the segment, and its pooled devices can be set to serve it. A rack knows up to five segments besides its own network. | {{page networking/switches}} |
| A {{item trunk_port}} | The Trunk Port carries the segment into the network it's on, for every rack on that network. | [Trunk Port](page:networks/cable-parts#trunk-port), {{page networking/switches}} |
| A Router's unit | The Router links to that segment's network, to route between them. | {{page networking/routers}} |
| An Optical Multiplexer | One of its channels carries the segment over a fibre line. | {{page networking/optical}} |

Using the card again on the same rack switch or Trunk Port unlinks the segment. Storing or using a segment needs the
Firewall's BUILD permission on that network, if it has one.

## Segments stay apart {#isolation}

Segments never see each other's storage, even when a rack or a Trunk Port serves several of them. Only two things
move items between segments:

- an **L3 Switch**, with routes between the segments its rack knows;
- a **Router**, with routes between the networks it's linked to.

Both are rack devices; see {{page networking/switches}} and {{page networking/routers}}.

## Segment IDs {#ids}

Racks and Trunk Ports give the segments they know a number, a VLAN-style segment ID, used in their panels, routes and
port settings:

- In a **Server Rack**, its own network is segment 1, and each segment you link gets the next free ID (up to 4094).
- On a **Trunk Port**, linked segments get IDs 10, 20, 30 and so on, up to eight segments.

VLAN trunking itself (access and trunk ports, native segments, what each rack device serves) is explained on
{{page networking/switches}}.
