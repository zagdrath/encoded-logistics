---
title: Networking
section: networking
order: 0
summary: Joining, splitting and connecting networks - switches and VLANs, Routers, exchanges and directories, bridges and links, optical fibre and wireless.
---
A single network, one controller structure and everything cabled to it, gets you a long way (see
{{page networks/index}}). This section is about what comes next: sharing lanes inside a rack, carrying several
segments down one cable, moving items between separate networks, and reaching far-off places by fibre, bridge or radio.

## Joining versus routing {#join-or-route}

Most of the hardware here does one of two quite different things, and it helps to keep them apart.

**Joining** makes two places one network: the same storage, the same lanes, the same controller. A Network Bridge pair,
a Point-to-Point Link carrying lanes, an optical channel or a Wireless Bridge all join: whatever is cabled at the far
end becomes part of the network at the near end. A network has exactly one controller structure, so only one of the
places you join should have one.

**Routing** keeps networks separate and moves items between them by rule. Each network keeps its own controller, storage
and Firewall. The L3 Switch routes between the segments a rack knows; the Router routes between whole networks, any
distance apart, and can learn routes from other Routers.

| You want to | Use | Page |
|---|---|---|
| Give a rack's devices lanes without loading the network | An L2 Switch | {{page networking/switches}} |
| Carry several Segment Isolator segments to your racks over one network | A Trunk Port and switch VLAN ports | {{page networking/switches}} |
| Move items between segments of one installation | An L3 Switch | {{page networking/switches}} |
| Move or share items between separate networks | A Router | {{page networking/routers}} |
| Let many networks' Routers meet and exchange routes | An Exchange Switch | {{page networking/exchange-and-directory}} |
| Reach another network's devices from the Terminal OS | A Directory Server | {{page networking/exchange-and-directory}} |
| Join two distant places, even in another dimension | A Network Bridge pair | {{page networking/bridges-and-links}} |
| Pass items, fluids, energy, redstone or lanes straight across | A Point-to-Point Link | {{page networking/bridges-and-links}} |
| Carry several networks over one long fibre line | Optical Multiplexers, or Optical Transport Shelves | {{page networking/optical}} |
| Reach devices without cables | A Wireless Controller with Access Points | {{page networking/wireless}} |
| Use a terminal away from your base | A Handheld Terminal and Relay Antennas | {{page terminals/handheld}} |

## The Link Card {#link-card}

Almost everything here is set up with a {{item link_card}}: you sneak-use it on one end to store that end, then use it
on the other end to pair, link or assign. The card's uses are collected on
{{page networking/bridges-and-links#link-card}}.

## Reading order

1. {{page networking/switches}}: lane pools, segments and VLANs inside a rack.
2. {{page networking/routers}}: moving items between networks.
3. {{page networking/exchange-and-directory}}: many networks meeting, and reaching devices by name.
4. {{page networking/bridges-and-links}}: joining places, and the Link Card.
5. {{page networking/optical}}: fibre, span loss, multiplexers and transport shelves.
6. {{page networking/wireless}}: Access Points, Wireless Bridges and Ports, the Wireless Controller.

Segments themselves (the Segment Isolator) are on {{page networks/segments}}, and lanes on {{page networks/lanes}}.
