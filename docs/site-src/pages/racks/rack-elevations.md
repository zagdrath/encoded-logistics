---
title: Rack Elevations
section: racks
order: 50
summary: The Rack Elevation - a clipboard plan of a rack that you record, edit and build in one go, its editor and materials screens, and its copy and clear recipes.
---
A {{item rack_elevation}} is a clipboard holding a plan of a Server Rack: which device goes in which unit, and optionally
what each device holds and how it's set up. Record a rack you've built, then fill a new rack from the plan in one click.
Or draw the plan from scratch in its editor and see what you still need to make.

{{recipe-id encodedlogistics:rack_elevation}}

Blank Rack Elevations stack to 16. A written one stacks to 1.

## Recording a rack {#record}

Sneak-use a Rack Elevation on a [Server Rack](page:racks/server-rack), from any side, door open or shut. It records
every installed device's unit, type and height, and the action bar says how many devices it recorded.

Two toggles in the plan decide what else is recorded (set them in the [editor](#editor) first):

- **Contents:** each device's items, such as the drives in a NAS, the tapes in a library or the cards in a chassis. Only
  the plain items are recorded: a drive or tape's identity, its contents and its charge are not, so applying the plan
  needs blank ones of the same kind.
- **Settings:** each device's settings, including its lane priority and device name.

Recording needs **Rack access** on the rack's network. Recording again replaces the plan.

## Applying a plan {#apply}

Use a written Rack Elevation on a Server Rack (without sneaking). It works through the plan from U1 up, and for each
planned device:

- **its units are free:** the device is installed, then given its planned contents and settings if those toggles are
  on;
- **the same device is already at that unit:** it's left in place; its planned contents go into its empty slots and its
  settings are applied, if those toggles are on;
- **anything else** (another device there, or the units partly taken): it's skipped.

**Nothing is ever removed or replaced**, so applying a plan to a half-built rack is safe: it fills in what's missing.
If the front door is shut, it swings open while the devices go in and closes again afterwards.

**Where the items come from**, in order:

1. your inventory,
2. shulker boxes in your inventory,
3. the rack network's storage, if the plan's **Network storage** toggle is on and you have **Extract** on that network.

In creative mode nothing is used up while {{config rack.rackElevationCreativeFree default}}.

When it's done, the action bar and chat say "Installed *n* devices, *n* skipped, *n* missing", with a line for each
skipped unit (for instance "past the top of the rack" or "too close to the Rack Console at U12") and each missing item,
and a **[show]** link to the [materials list](#materials).

Applying needs **Rack access**.

### Previewing it {#preview}

Hold a written Rack Elevation and look at a rack. Ghosts over the rack's front show what applying would do:

| Ghost | Means |
|---|---|
| Green | Would be installed, or is already there |
| Amber | Would be skipped |
| Red | The units are free, but you don't have the item |

The rack's popup reads "Rack Elevation *name*" with the **Fill**, **Skip** and **Missing** counts.

{{screenshot file="racks/rack-elevation-preview.png" alt="A Rack Elevation's ghost preview over a Server Rack's front" show="Holding a written Rack Elevation, looking at a partly filled rack: green ghosts on free units, an amber ghost where another device is in the way, a red ghost for a missing item, and the popup with Fill / Skip / Missing" angle="Two blocks in front of the rack, front door open, crosshair on the rack" size="1600x900"}}

## The editor {#editor}

Use a Rack Elevation in the air to open its editor.

{{screenshot file="racks/rack-elevation-editor.png" alt="The Rack Elevation editor with a planned rack and the device list" show="The editor with a plan of about ten devices as faded ghosts on the left elevation, one selected and outlined; the side panel with the name, search field, device list, the Contents / Settings / Network storage toggles and Materials showing a missing count" angle="GUI scale 3" size="1280x720"}}

- **Left:** the rack elevation, 16 units at a time with a scrollbar, the planned devices drawn as faded ghosts of their
  fronts.
- **Right:** the plan's name (up to 32 characters), a search field over every kind of rack device (icon, name and
  height), the **Contents**, **Settings** and **Network storage** toggles, and **Materials** with how many items are
  missing.

Planning:

- **Add a device:** drag it from the list onto a unit, or click a free unit and then a device. With
  [JEI](page:integrations/jei), dragging a rack device from JEI's list onto a unit works too.
- **Move a device:** drag its ghost.
- **Look at a device:** click its ghost to see its units and its planned contents and settings, with **Remove**.

The drop target turns red where a device would break the rack's rules. A plan must keep to them:

- every device a known kind, at its real height,
- inside the 42 units, with no overlaps,
- [Rack Consoles](page:terminals/rack-consoles) at least 8U apart.

Every change goes to the server, which checks it and writes it onto the item in your hand.

## The materials list {#materials}

Click **Materials** in the editor, or **[show]** in an apply report, for the list of what the plan needs.

It shows "Have / need" overall and how many items are missing, then a row for each kind of device (and, with Contents
on, each planned content item) with its name and have / need, green where you have enough and red where you're short.
"Have" counts your inventory and the shulker boxes in it, plus the rack network's storage while **Use network
storage** (the field at the bottom) is on and you may take from it. The network counted is the last rack you previewed.

## Copying and clearing {#recipes}

- **Copy:** a written Rack Elevation and a blank one in a crafting grid make a second written copy. The original stays
  in the grid, as a book's does when you copy it.
- **Clear:** a written Rack Elevation on its own in a crafting grid goes back to blank.

{{recipe-id encodedlogistics:rack_elevation_copy}}

{{recipe-id encodedlogistics:rack_elevation_clear}}
