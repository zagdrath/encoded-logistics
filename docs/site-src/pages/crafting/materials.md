---
title: Fabrication materials
nav: Materials
section: crafting
order: 20
summary: The fabrication chain - silicon from sand, Ferrite, Copper Foil, Fiberglass, Solder Paste, Circuit Substrate - and the components made from it.
---
Almost everything in the mod is built from a handful of intermediate materials. All of the first ones come from
vanilla resources: sand, iron, copper, redstone, glass, string, slime balls and coal. Make a stack of each early on;
you'll use them constantly.

## The chain at a glance

| Material | From | Mainly for |
|---|---|---|
| {{item silica}} | Sand in a **blast furnace** | Silica Blend |
| {{item silica_blend}} | 4 Silica + coal or charcoal | Silicon Boule |
| {{item silicon_boule}} | Silica Blend in a furnace | Silicon Wafers |
| {{item silicon_wafer}} | A Silicon Boule on a **stonecutter**: 4 wafers | Logic Die, 8K Storage Die, Doped Silicon |
| {{item doped_silicon}} | A Silicon Wafer + redstone | Memory and Processor Dies, the Memory Photomask |
| {{item ferrite}} | An iron ingot + redstone | Nearly everything: photomasks, drives, heatsinks, devices |
| {{item copper_foil}} | A copper ingot on a **stonecutter**: 2 foil | Circuit Substrate, Tantalum Capacitors, Fluid drives |
| {{item fiberglass}} | Glass + 2 string: 2 Fiberglass | Circuit Substrate, Fiber Cable |
| {{item solder_paste}} | A copper ingot + a slime ball: 2 paste | Circuit Substrate, Tantalum Capacitors, dies |
| {{item circuit_substrate}} | Fiberglass + Copper Foil + Solder Paste | Nearly every device |
| {{item heatsink}} | 5 copper ingots + 1 Ferrite | Scheduler Core, Thread Unit, servers |
| {{item tantalum_capacitor}} | A Tantalum Ingot + Copper Foil + Solder Paste: 2 capacitors | Energy drives, Gateway, ports, Capacitor Bank, UPS |
| {{item optical_transceiver}} | Fiberglass, a Logic Die, glass, Copper Foil, a Gallium Ingot | Fiber links, SAN uplinks, Relay Antennas |
| {{item radio_module}} | A Gallium Ingot, a Logic Die, Copper Foil, Circuit Substrate | Wireless devices |

## Silicon {#silicon}

Silicon takes four steps and three kinds of block: blast sand into Silica, mix four Silica with coal into Silica Blend,
smelt that into a Silicon Boule, and cut the boule on a stonecutter into four Silicon Wafers.

{{recipe silica_blend}}

{{recipe silicon_wafer}}

Wafers go into the [Lithography Press](page:crafting/lithography) for Logic Dies and 8K Storage Dies. For Memory and
Processor Dies, dope them first:

{{recipe doped_silicon}}

## Ferrite {#ferrite}

Ferrite is an iron ingot and a redstone dust, crafted together. It goes into almost every recipe in the mod, so it's
worth automating early. Nine make a {{item ferrite_block}} for storage (see {{page crafting/ores-and-metals}}).

{{recipe ferrite}}

## Circuit Substrate {#substrate}

Circuit Substrate is the board every device is built on. It takes Fiberglass, Copper Foil and Solder Paste, so you need
a stonecutter for the foil and slime balls for the paste.

{{recipe fiberglass}}

{{recipe solder_paste}}

{{recipe circuit_substrate}}

## Components {#components}

Three more parts are made from the metals:

- The **Heatsink** (copper and Ferrite) cools the autocrafting and server hardware.
- **Tantalum Capacitors** need [tantalum](page:crafting/ores-and-metals). They come two to a craft.
- The **Optical Transceiver** and **Radio Module** need gallium. Transceivers are for fiber links (see
  {{page networking/optical}}), a SAN's uplinks and Relay Antennas' range; Radio Modules are for wireless gear (see
  {{page networking/wireless}}).

{{recipe heatsink}}

{{recipe tantalum_capacitor}}

{{recipe optical_transceiver}}

{{recipe radio_module}}

The dies, the last step of the chain, are on {{page crafting/lithography}}.
