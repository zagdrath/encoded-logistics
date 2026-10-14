---
title: Crafting and automation
section: crafting
order: 0
summary: The mod's materials from ore to Processor Die, and autocrafting - schematics, the machines that carry them out, the Schedulers that run jobs, and the early Midrange line.
---
This section has two halves. The first is **making things**: the mod's three ores, the fabrication chain that turns
sand, iron and copper into Circuit Substrate and Silicon Wafers, and the Lithography Press that etches those into the
dies nearly every device is built from. The second is **autocrafting**: teaching the network recipes and letting it
craft for you.

## Materials

1. {{page crafting/ores-and-metals}}: neodymium, tantalum and gallium, where they generate, and their ingots and blocks.
2. {{page crafting/materials}}: Ferrite, Copper Foil, Solder Paste, Circuit Substrate, silicon and the other parts of
   the chain.
3. {{page crafting/lithography}}: the Lithography Press, photomasks, and the Logic, Memory and Processor Dies.

The dies and metals set the pace of the mod: Logic and Memory Dies are made from vanilla materials, most of what's built
with Memory Dies also needs neodymium or tantalum, and Processor Dies need gallium. {{page getting-started/progression}} follows that path.

## Autocrafting

Autocrafting on a network has three parts:

| Part | What it does | Where |
|---|---|---|
| A **schematic** | A recipe written on a card: what goes in, what comes out | {{page crafting/schematics}} |
| A **provider** | A machine holding schematics that carries them out: a Fabricator crafts, a Gateway runs other mods' machines | {{page crafting/fabricators-and-gateways}} |
| A **Scheduler** | Plans and runs jobs: works out the steps, holds the ingredients, hands each step to a provider | {{page crafting/schedulers}} |

Then you ask for something from any terminal and watch it happen: {{page crafting/jobs}}.

Before any of that (before Processor Dies), the **Midrange line** gives you a smaller autocrafting system from early
materials: recipes punched on cards, read onto diskettes, and crafted by a Midrange System. See {{page crafting/midrange}}.
