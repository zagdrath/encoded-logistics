---
title: Ores and metals
section: crafting
order: 10
summary: Neodymium, tantalum and gallium - where their ores generate, mining and smelting them, dusts with Arcforge, and the storage blocks.
covers: [encodedlogistics:raw_*, encodedlogistics:*_ingot, encodedlogistics:*_dust]
---
The mod adds three metals. **Neodymium** and **tantalum** open up the middle of the mod: 128K and larger dies and
drives, Tantalum Capacitors, Pressurized and Energy drives, ports and the Gateway. **Gallium** is for the top of it:
Processor Dies, Optical Transceivers and Radio Modules. Each has an ore, a raw metal, an ingot, and a storage block
for each of the raw metal and the ingot.
Everything before them (Logic and Memory Dies, the first network, the Midrange line) needs only vanilla materials.

## Where the ores are {#ores}

All three generate in the **Overworld only**, in every Overworld biome.

| Ore | Height | Veins | Vein size | Notes |
|---|---|---|---|---|
| {{item neodymium_ore}}, {{item deepslate_neodymium_ore}} | y -32 to 48, most around y 8 | 8 per chunk | up to 8 | The commonest. Generates whether or not it's exposed to air |
| {{item tantalum_ore}}, {{item deepslate_tantalum_ore}} | The bottom of the world (y -64) to y -8, evenly | 4 per chunk | up to 6 | Below y -8 it's mostly in deepslate. 40% of veins touching air are skipped |
| {{item deepslate_gallium_ore}} | The bottom of the world (y -64) to y -32, most around y -48 | 2 per chunk | up to 4 | Deepslate only, the deepest and rarest. 60% of veins touching air are skipped |

"Most around" is the peak of a triangular spread: the ore is commonest there and thins out towards both ends. Because
tantalum and gallium avoid air, look for them by branch mining rather than in caves.

{{screenshot file="crafting/ores.png" alt="Neodymium, tantalum and gallium ore in a mine wall" show="A mine wall at about y -40 with deepslate neodymium, deepslate tantalum and deepslate gallium ore exposed side by side" angle="Facing the wall from two blocks away, eye level, torch-lit" size="1600x900"}}

## Mining {#mining}

Every ore needs an **iron pickaxe** or better. Deepslate ores are harder to break, as vanilla's are.

Mined normally, an ore drops one raw metal ({{item raw_neodymium}}, {{item raw_tantalum}}, {{item raw_gallium}}), more
with Fortune. With Silk Touch it drops the ore block. The ores are in the common `c:ores` tags (`c:ores/neodymium` and
so on), so other mods' ore processing recognises them.

## Ingots {#ingots}

Smelt or blast the raw metal, the ore block itself or the dust into an ingot: {{item neodymium_ingot}},
{{item tantalum_ingot}}, {{item gallium_ingot}}. The furnace and the blast furnace both work, for all three inputs.

{{recipe-id encodedlogistics:neodymium_ingot_from_smelting_raw}}

## Dusts (with Arcforge) {#dusts}

{{item neodymium_dust}}, {{item tantalum_dust}} and {{item gallium_dust}} are made **only by Arcforge's Arc Crusher**.
Without Arcforge installed there's no way to make them (the recipes simply don't load), though a dust you have still
smelts into an ingot.

| Arc Crusher input | Gives |
|---|---|
| Ore | 2 dust, and a 25% chance of 1 more |
| Raw metal | 1 dust, and a 25% chance of 1 more |
| Ingot | 1 dust |

Each takes 200 ticks. So with Arcforge, crushing ore before smelting gives more ingots. See {{page integrations/arcforge}}.

## Storage blocks {#blocks}

Nine ingots make a block, and a block crafts back into nine:

| Block | From |
|---|---|
| {{item neodymium_block}} | 9 Neodymium Ingots |
| {{item tantalum_block}} | 9 Tantalum Ingots |
| {{item gallium_block}} | 9 Gallium Ingots |
| {{item ferrite_block}} | 9 [Ferrite](page:crafting/materials#ferrite) |

{{recipe neodymium_block}}

Nine raw metals likewise make a raw block, as vanilla's raw iron does, and it crafts back into nine:

| Block | From |
|---|---|
| {{item raw_neodymium_block}} | 9 Raw Neodymium |
| {{item raw_tantalum_block}} | 9 Raw Tantalum |
| {{item raw_gallium_block}} | 9 Raw Gallium |

They all need an iron pickaxe to mine, and are in the `c:storage_blocks` tags. The three metal blocks (not Ferrite or
the raw blocks) can be used in a beacon's base.
