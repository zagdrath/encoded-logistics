---
title: JEI
section: integrations
order: 20
summary: What Encoded Logistics adds to JEI - the Lithography category, crafting stations, recipe transfer, dragging items onto filters and ghost slots, and search sync with the terminals.
---
With JEI installed, the mod's crafting, smelting and other vanilla-type recipes show up in JEI as any mod's do. On top
of that, the mod adds the following.

## The Lithography category {#lithography}

Lithography Press recipes have a category of their own, laid out like the press: the photomask above, the wafer on the
left, the additive below and the result on the right, with the energy and time underneath. The photomask is marked
"Not consumed": it is never used up. The {{item lithography_press}} is the category's station, so looking up its uses
shows every lithography recipe. The recipes are the ones the server sends, so a datapack's changes show too. See
{{page crafting/lithography}}.

## Crafting stations and info {#stations}

- The {{item fabricator}} is listed as a station for crafting recipes, since it runs them for the network's crafting
  jobs.
- The {{item network_controller}} has an information page on how controllers form structures and what they provide.

## Recipe transfer (the "+" button) {#transfer}

| Where | What "+" does |
|---|---|
| A {{item fabrication_terminal}} (any crafting recipe) | Fills the crafting grid from the network first, then your inventory. Before that, the recipe marks what is missing: red for missing, blue for missing but craftable on the network. The "+" still works then, moving what there is |
| A {{item schematic_encoder}} (any recipe) | A crafting recipe fills the ghost grid in crafting mode; any other recipe becomes a processing schematic with its inputs (up to nine) and outputs (up to three) and their amounts. Only ghost entries are set: nothing is taken from anywhere |

## Dragging onto filters and ghost slots {#ghosts}

Drag an item from JEI's list onto a ghost slot to set it, as clicking with the item would:

- the filters on ports, Inventory Taps, Collector and Deployer Planes, and the Threshold Sensor's item;
- the Gateway's stock list;
- the Schematic Encoder's grid and outputs;
- the filter boxes in the Server Rack's screen (a Router or L3 Switch route, an L3 Switch QoS rule);
- the Rack Elevation editor: drag a rack device onto a free unit to place it there.

Dragging a fluid (an Arcforge gas is one too) sets a fluid or gas entry.

## Search sync {#search}

The terminals can keep their search field in step with JEI's search bar, both ways. Turn it on with the client option
{{config terminal.searchSynced default}} (Sync Search with JEI). See {{page terminals/block-terminals}}.
