---
title: Your first network
nav: First network
section: getting-started
order: 10
summary: From vanilla materials to a working storage network with a Drive Bay, a Storage Drive and an Access Terminal, step by step.
---
This guide takes you from a fresh world to a storage network you can put items into and take them out of. You'll
make the first silicon and circuit boards, build a {{item lithography_press}} to etch the first dies, and then
assemble a network from six things: a {{item network_controller}}, a {{item power_inlet}}, {{item network_cable}}, a
{{item drive_bay}}, a {{item storage_drive_8k}} and an {{item access_terminal}}.

## What you need {#shopping-list}

All vanilla, from the Overworld:

| Material | For |
|---|---|
| {{item minecraft:iron_ingot}} (a stack or two) | Almost everything |
| {{item minecraft:redstone}} and a {{item minecraft:redstone_block}} | Ferrite, dies, cable, the Power Inlet |
| {{item minecraft:copper_ingot}} | Copper Foil and Solder Paste |
| {{item minecraft:sand}}, {{item minecraft:glass}} and {{item minecraft:string}} | Silicon, Fiberglass, photomasks |
| {{item minecraft:slime_ball}} | Solder Paste |
| {{item minecraft:coal}} or charcoal | Silica Blend |
| {{item minecraft:quartz}} | Network Cable |
| One {{item minecraft:diamond}} | The Network Controller |
| A {{item minecraft:chest}} and a {{item minecraft:glass_pane}} | The Drive Bay and the Access Terminal |

You also need a **Blast Furnace**, a **Furnace**, a **Stonecutter** and a crafting table, and **a source of FE from
another mod**: Encoded Logistics has no generator of its own. Anything that pushes FE into an adjacent block will do.

## 1. Ferrite, foil, fiberglass and solder {#basics}

Four simple materials go into nearly everything:

- **{{item ferrite}}**: an iron ingot and a redstone dust, shapeless.
- **{{item copper_foil}}**: a copper ingot in a Stonecutter gives two.
- **{{item fiberglass}}**: a glass block and two string give two.
- **{{item solder_paste}}**: a copper ingot and a slime ball give two.

Fiberglass, Copper Foil and Solder Paste together make a **{{item circuit_substrate}}**, the circuit board almost every
device is built on. Make a handful.

{{recipe circuit_substrate}}

## 2. The first silicon {#silicon}

Silicon Wafers take four steps:

1. Blast {{item minecraft:sand}} in a Blast Furnace to get **{{item silica}}** (it only works in a Blast Furnace).
2. Four Silica and a coal (or charcoal) make a **{{item silica_blend}}**.
3. Smelt the blend in a Furnace to get a **{{item silicon_boule}}**.
4. Cut the boule in a Stonecutter: one boule gives four **{{item silicon_wafer}}**.

{{recipe silicon_wafer}}

Each die below uses one wafer, and the first network needs four Logic Dies and one 8K Storage Die, so a couple of
boules is enough to start.

## 3. The Lithography Press {#press}

The {{item lithography_press}} etches wafers into dies. It needs no network: it's a machine of its own, and it runs on
FE fed into any of its faces. It buffers {{config lithography.pressCapacity default}} FE and takes up to
{{config lithography.pressMaxInput default}} FE per tick, so put it next to your generator (or a cable or conduit from
another mod).

{{recipe lithography_press}}

It has four slots: a **photomask**, a **wafer**, an **additive**, and the output. The photomask is never used up;
each etch uses one wafer and one additive. While a recipe matches it spends the recipe's energy evenly over its time,
and if the power runs out it pauses and keeps its progress. Hoppers work too: from the top into the photomask slot,
from the sides into the wafer and additive slots, and out of the bottom from the output.

{{screenshot file="getting-started/lithography-press-gui.png" alt="The Lithography Press screen etching a Logic Die" show="The Lithography Press screen with a Logic Photomask, Silicon Wafers and redstone in their slots, part-way through an etch, with the energy bar partly full" angle="GUI scale 3, the screen centred, no tooltip showing" size="1280x720"}}

Make the two photomasks you need now. Each is glass and Ferrite around a centre item:

- **{{item logic_photomask}}**: redstone in the centre.
- **{{item storage_photomask}}**: Ferrite in the centre.

{{recipe logic_photomask}}

{{recipe storage_photomask}}

## 4. Logic Dies and the 8K Storage Die {#dies}

With a photomask in the press:

| Die | Wafer | Additive | Photomask |
|---|---|---|---|
| {{item logic_die}} | Silicon Wafer | Redstone | Logic Photomask |
| {{item storage_die_8k}} | Silicon Wafer | Ferrite | Storage Photomask |

{{recipe logic_die}}

{{recipe storage_die_8k}}

Make **four Logic Dies** (two for the controller, one each for the Drive Bay and the Access Terminal) and **one 8K
Storage Die**. Swap the photomask by hand to switch recipes: hoppers can put one in, but never take it out. More about
the press and the later dies is in {{page crafting/lithography}}.

## 5. Craft the network {#craft}

**The {{item network_controller}}** is the heart of the network. It stores energy and hands out lanes to everything
on the network.

{{recipe network_controller}}

**The {{item power_inlet}}** feeds FE into the network. FE goes in only through its front (its port); cables join its
other five faces.

{{recipe power_inlet}}

**{{item network_cable}}** joins network blocks together. One recipe makes eight.

{{recipe network_cable}}

**The {{item drive_bay}}** holds up to ten Storage Drives, and its drives are the network's storage.

{{recipe drive_bay}}

**The {{item storage_drive_8k}}** goes in the Drive Bay. It holds 8,192 bytes, eight items to a byte, and each item
type it holds reserves a share of them: about 65,000 items of one type, fewer as you add types, and up to
{{config storage.driveTypeLimit default}} types. [Storage Drives](page:storage/drives) explains the
byte model.

{{recipe storage_drive_8k}}

**The {{item access_terminal}}** is how you see and use what's stored. It's a part: it mounts on the face of a cable or
of a network block, not as a block of its own.

{{recipe access_terminal}}

## 6. Put it together {#build}

1. **Place the Network Controller.** A single block is a complete controller structure.
2. **Place the Power Inlet** against the controller (or on a cable from it), with its **front (the port) facing your
   generator**. Like a dispenser, the port faces you when you place it, so stand where the generator will go, or
   place the generator in front of the port afterwards.
3. **Run Network Cable** from the controller to where you want your storage.
4. **Place the Drive Bay** at the end of the cable. Cables join every face but its front, and the front shows the
   drives.
5. **Put the Storage Drive in the Drive Bay**: right-click the bay and drop the drive into one of its ten slots.
6. **Mount the Access Terminal**: use it on the face of a cable (it goes on the face you click), or on a side of the
   controller or the Drive Bay (not the bay's front, which doesn't connect).
7. **Connect the power.** Once FE flows into the inlet, the controller lights up and the network comes online.

{{screenshot file="getting-started/first-network.png" alt="A first network: controller, Power Inlet, cable, Drive Bay and Access Terminal" show="A single Network Controller with a Power Inlet on one side (port facing a generator from another mod), a short run of Network Cable to a Drive Bay with one drive sled lit green, and an Access Terminal mounted on the cable, screen lit" angle="Standing two blocks back and slightly above, looking down at about 30 degrees so every block and the cable run are visible" size="1600x900"}}

Right-click the Access Terminal to open it and move items from your inventory into the network. They're stored on
the drive, and every terminal on this network sees them. See
{{page terminals/block-terminals}} for search, sorting and the fluid and energy tabs.

## Checking it works {#check}

Right-click the controller to open its **Network** screen. The status line should say **Online**, the lanes line
should show two lanes in use (one each for the Drive Bay and the terminal), and the device grid lists what's on the
network.

{{screenshot file="getting-started/controller-screen-first.png" alt="The Network screen of a first network, online" show="The Network Controller's screen for the network built above: status Online, 2 lanes in use, the energy gauge filling, and the device grid with a Drive Bay and an Access Terminal" angle="GUI scale 3, the screen centred" size="1280x720"}}

If something is wrong:

| You see | Why | Fix |
|---|---|---|
| **No power** | The controller's buffer is empty. | Check the inlet's port faces the generator, and that the generator is running. |
| The terminal's screen stays dark | The terminal has no lane, or the network has no power. | Check the network is Online; make sure the terminal's cable actually joins the network. |
| **Invalid structure** | Touching controllers that don't make a valid shape. | One controller block is enough for now; see [controller shapes](page:networks/controllers#shapes). |
| **Multiple controllers** | Two controller structures joined by cable. | A network has one controller structure; see [conflicts](page:networks/controllers#conflicts). |

## Where next {#next}

- Add drives: more 8K drives, then 32K ones (four 8K dies, a Logic Die, redstone and Solder Paste make a
  {{item storage_die_32k}}). An {{item fluid_storage_drive_8k}} stores fluids. See {{page storage/drives}}.
- Learn how lanes run out and what to do about it: {{page networks/lanes}}.
- Find out what comes next: {{page getting-started/progression}}.
