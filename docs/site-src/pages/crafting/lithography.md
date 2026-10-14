---
title: Lithography
section: crafting
order: 30
summary: The Lithography Press, the four photomasks it keeps, and the Logic, Memory, Processor and 8K Storage Dies it etches, with the power and time each takes.
---
Dies are the chips at the heart of the mod's devices, and the {{item lithography_press}} is the only way to make them.
It etches a pattern onto a wafer using a **photomask**, which it keeps: you make each photomask once and use it for
every die of that kind.

The press is a machine of its own: it needs **FE but no network**. Encoded Logistics has no generator, so power it from
another mod's generator, battery or cable. It takes FE on every face.

{{recipe lithography_press}}

## Using the press

The press has four slots:

| Slot | Takes |
|---|---|
| Photomask | The photomask for the die you want. **Never used up** |
| Wafer | A Silicon Wafer or Doped Silicon |
| Additive | Redstone, Ferrite or a Gallium Ingot, by recipe |
| Output | The finished die |

Put a photomask, a wafer and the additive in, and give it power. While the inputs match a recipe and the output has
room, the press spends the recipe's energy evenly over its time. If it runs out of power it pauses and keeps its
progress. At the end it uses up one wafer and one additive and puts the die in the output.

It buffers {{config lithography.pressCapacity default}} FE and takes in up to {{config lithography.pressMaxInput default}}
FE a tick, which is enough for any recipe at full speed. The screen shows the buffer, the exposure progress and its
status; the window lights while it works, and a comparator reads its progress (0 to 15).

{{screenshot file="crafting/lithography-press-gui.png" alt="The Lithography Press screen etching a Logic Die" show="The Lithography Press screen with the Logic Photomask, Silicon Wafers and redstone in, part way through an exposure, energy gauge half full" angle="GUI scale 3, screen centred" size="1280x720"}}

### Automation {#automation}

- The **top** face takes photomasks into the photomask slot.
- The **sides** take wafers and additives, each into its own slot (wafers to the wafer slot first).
- The **bottom** gives out finished dies only. The photomask never comes out by automation.

So a hopper or an [Ingress or Egress Port](page:networks/cable-parts) on its sides and bottom makes it a die factory.
You can also run it from autocrafting with a [Gateway](page:crafting/fabricators-and-gateways#gateway) and a Processing
Schematic.

## Photomasks {#photomasks}

Each photomask is a ring of glass and Ferrite around a centre that sets its pattern. They stack to 1.

| Photomask | Centre | Makes |
|---|---|---|
| {{item logic_photomask}} | Redstone | Logic Die |
| {{item storage_photomask}} | Ferrite | 8K Storage Die |
| {{item memory_photomask}} | Doped Silicon | Memory Die |
| {{item processor_photomask}} | A Gallium Ingot | Processor Die |

{{recipe logic_photomask}}

## The dies {#dies}

| Die | Wafer | Additive | Photomask | Energy | Time |
|---|---|---|---|---|---|
| {{item logic_die}} | Silicon Wafer | Redstone | Logic | 4,000 FE | 100 ticks (5 s) |
| {{item storage_die_8k}} | Silicon Wafer | Ferrite | Storage | 4,000 FE | 100 ticks (5 s) |
| {{item memory_die}} | Doped Silicon | Ferrite | Memory | 6,000 FE | 140 ticks (7 s) |
| {{item processor_die}} | Doped Silicon | Gallium Ingot | Processor | 8,000 FE | 180 ticks (9 s) |

The three die tiers set the mod's progression:

- **Logic Dies** need only vanilla materials. They're in the first network, the Drive Bay and the whole Midrange line.
- **Memory Dies** need Doped Silicon (a wafer and redstone), so they too are made from vanilla materials. Many of the
  things built with them also need neodymium or tantalum: larger dies and drives, ports and modules, the Gateway.
  Schematic Cards and Job Buffers don't.
- **Processor Dies** need gallium. They're in the Fabricator, the Scheduler, the Schematic Encoder, the 2M die and the
  servers.

The 8K Storage Die starts the storage tiers; the larger ones are crafted from it (see
[Storage Dies](page:storage/drives#dies)).

## The recipe type {#recipe-type}

Press recipes are their own recipe type, `encodedlogistics:lithography`. With JEI installed they have their own
category, with the press as its catalyst (see {{page integrations/jei}}). Every lithography recipe is listed on
[the recipe reference](page:reference/recipes).

For pack makers, a recipe names a wafer, an additive and a photomask (each an ingredient), a result, and optionally
`energy` (FE, default 4000) and `time` (ticks, default 100):

```json
{
  "type": "encodedlogistics:lithography",
  "wafer": "encodedlogistics:silicon_wafer",
  "additive": "minecraft:redstone",
  "photomask": "encodedlogistics:logic_photomask",
  "result": { "id": "encodedlogistics:logic_die", "count": 1 },
  "energy": 4000,
  "time": 100
}
```
