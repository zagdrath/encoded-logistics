---
title: Optical networking
nav: Optical
section: networking
order: 50
summary: Fiber Cable span loss, the Optical Amplifier and Optical Multiplexer in block and rack form, and the Optical Transport Shelf's channels and 1+1 protection.
---
Fiber Cable carries a network over long distances, but light fades: a very long run of fibre loses lanes. The optical
gear deals with that. Amplifiers regenerate the light so a run can go on, and multiplexers carry several separate
networks over a single fibre line between two points.

## Fiber Cable and span loss {#span-loss}

[Fiber Cable](page:networks/cables) carries {{config lanes.perFiberCable default}} lanes, the same as Dense Cable, but
it's the only cable that can carry optical lines, and the only one with **span loss**.

A continuous run of Fiber Cable longer than {{config optical.fiberSpanThreshold default}} blocks loses one lane for
every further {{config optical.fiberSpanLossStep default}} blocks, down to a minimum of 1. With the default settings:

| Run length | Lanes lost | Lanes left |
|---|---|---|
| Up to 256 blocks | 0 | 32 |
| 300 blocks | 1 | 31 |
| 512 blocks | 8 | 24 |
| 1,024 blocks | 24 | 8 |

How it's worked out:

- A **run** is a stretch of Fiber Cable blocks, plus any unpowered Optical Amplifiers in it. Anything else ends the run
  there: a powered amplifier, another kind of cable or block, or a remote link such as a Network Bridge.
- A run's **span** is its longest path between its ends. Every link in the run, and every link into it, carries at most
  the lanes left after its span's loss.
- **Signal quality** is 100 % less 4 % for each lane lost.
- Network Cable, Dense Cable, Bridges and Point-to-Point Links don't lose anything.

Look at a Fiber Cable to see a popup with the network it's on (or *Not on a network*), its span length since the last
amplifier, and the lanes running through it out of those it carries after loss. Turn span loss off
altogether with {{config optical.fiberSpanLoss default}}.

## Optical Amplifier {#amplifier}

The {{item optical_amplifier}} is an inline housing that sits in a Fiber Cable run and regenerates the light.

{{recipe optical_amplifier}}

- **Placing:** it lies along the direction you're looking (horizontally). Cables join it only at its two ends: light comes
  in at the back and leaves at the front.
- **On the network:** it passes lanes like a Fiber Cable, takes one lane itself, and drains
  {{config optical.amplifierDrain default}} FE/t.
- **Powered,** it ends the span: span loss starts counting again from zero after it. **Unpowered,** it's just more fibre
  and the run goes on through it.
- **It's powered** while it's online on its network, or while an online Optical Multiplexer or Transport Shelf whose line
  runs through it powers the line.
- **States:** off, on (amplifying), or fault: "input loss of signal", with no fibre at its back.
- **Popup:** its input and output lanes, and the spans on either side. Its device name is `AMP01` and so on.

Because an amplifier needs a lane, one on a run that has run out of lanes may not get one; then it isn't powered by its
network, though a multiplexer's or shelf's line through it still powers it.

{{screenshot file="networking/optical-amplifier.png" alt="An Optical Amplifier inline on a Fiber Cable run" show="A long Fiber Cable run with an Optical Amplifier inline, powered (lit), with the HUD popup showing input and output lanes and the spans either side" angle="Standing beside the run, crosshair on the amplifier" size="1600x900"}}

### The rack amplifier {#rack-amplifier}

The {{item optical_amplifier_rack}} does the same job for lines that pass **through a Server Rack**: a multiplexer's or
shelf's line can come into the rack at one connection point and leave at another, and its span starts again there. It
needs Fiber Cable at two or more of the rack's connection points (its input and output); with fewer it faults with
"input loss of signal". While it's unpowered, the rack passes no line through. It's made from the block form (see
{{page racks/devices#optical-amplifier}}).

## Optical Multiplexer {#multiplexer}

An {{item optical_multiplexer}} carries several separate networks over one fibre line. A pair of them, one at each end
of a Fiber Cable line, joins channel 1 at one end to channel 1 at the other, channel 2 to channel 2, and so on. The
networks on the channels never mix with each other, and the line itself joins nothing.

{{recipe optical_multiplexer}}

- **Placing:** it faces you. Its **front** is the line port: the Fiber Cable there is its line. Every other side joins the
  network it draws power from, as a device with one lane, draining {{config optical.multiplexerDrain default}} FE/t.
- **Channels:** {{config optical.multiplexerChannels default}} of them (the option goes up to 16).
- **Its device name** is `MUX01` and so on.

### Setting up a pair {#mux-setup}

1. **Lay the line:** run Fiber Cable from one multiplexer's front to the other's. The line may pass through Optical
   Amplifiers (along their axis), a Network Bridge pair, and Server Racks with an online rack amplifier. It follows
   **only** Fiber Cable: any other cable breaks it.
2. **Pair them:** sneak-use a Link Card on one multiplexer, then use it on the other.
3. **Give channels their networks:** sneak-use a Link Card on a cable or device of a network (or on a Segment Isolator
   half, for a segment), then use it on a multiplexer. It goes on the channel selected in the multiplexer's panel, or the
   first free one; if none is free the action bar says so. Do the same at the other end for the same channel number.

A channel is **up** while the line is up, both multiplexers are online, and both ends of the channel have a network. It
carries up to a Fiber Cable's lanes, less the line's span loss (the line's loss is that of its longest span). The
multiplexer looks at its line whenever the networks change, and every two seconds anyway.

**States:** off, on (its channel lights), or fault when it's paired but the line is down.

**The panel** (right-click the block) has two tabs: **Channels** (each channel's network and state; select a channel for
the next Link Card, or clear one) and **Line** (its partner, the line and its spans). Clearing and unpairing need
**Build**.

The {{item optical_multiplexer_rack}} is the same in a rack: its line is a Fiber Cable leaving the rack at any connection
point and leading to its partner (see {{page racks/devices#optical-multiplexer}}).

{{screenshot file="networking/optical-mux-panel.png" alt="The Optical Multiplexer panel's Channels tab" show="The Optical Multiplexer panel, Channels tab: eight channels, three with networks and up, one selected for the next Link Card" angle="GUI scale 3" size="1280x720"}}

## Optical Transport Shelf {#transport-shelf}

For heavier long-haul links, the 6U {{item optical_transport_shelf}} does a multiplexer's job at a larger scale, built
from cards: Transponder Cards for the channels (1 to 40), Mux Cards to carry more than one channel, Amplifier Cards for
extra reach, and Protection Cards for **1+1 protection**.

With a Protection Card, the shelf keeps two paths to its partner: a **working** path (the shortest) and a **protect**
path, which leaves its rack from a different connection point over a different fibre. If the working path is cut, it
switches to protect within a tick, and (if the card is set to revertive, the default) back again once the working path is
restored. {{cmd CHGPROT}} switches by hand for testing.

Everything about the shelf is on {{page racks/chassis#transport-shelf}}.

## Watching it from the Terminal OS {#terminal-os}

Display Optical ({{cmd DSPOPT}}) lists the network's multiplexers and shelves with their spans and channels: length,
loss, lanes and quality. {{cmd RTVOPTSTS}} reads them into a program. The trigger event `*SPANLOSS` fires when an
optical span loses lanes or gets them back, and `*PROTSWITCH` when a shelf switches path.

{{layout 29_dspopt}}
