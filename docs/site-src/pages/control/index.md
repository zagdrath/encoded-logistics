---
title: Control and facility
section: control
order: 0
summary: The blocks that connect a network to the world around it - redstone in and out, PLCs, screens, lights, alarms and sound.
---
The blocks in this section let a network do things in the world and react to it. Most of them are devices on a
network that you drive from the [Terminal OS](page:elcl/index) with ELCL, but several also work on their own from
plain redstone.

| Block | What it does | Needs a network? |
|---|---|---|
| {{item control_interface}} | Six redstone channels, one per face, that ELCL reads and drives | Yes |
| {{item plc}} | Runs one ELCL program over and over, with its own redstone faces, sensor modules and green screens | No (it can join one) |
| {{item display_panel}} | Merges into wall screens that show text, gauges, graphs, tables and images; touching one fires an event | Yes |
| [Cage Lights](page:control/lights-and-sirens#cage-lights) | Lamps in 16 colours, switched by redstone or the network | No |
| [Alarm Strobes](page:control/lights-and-sirens#alarm-strobes) | Flashing beacons with six siren tones | No |
| {{item speaker}} | Plays audio files, notes and MIDI files (and, if the server allows it, web audio) | No |

## Reading order

1. {{page control/control-interface}}: the simplest way to get redstone into and out of ELCL.
2. {{page control/plc}}: a small controller that runs a program on its own, with or without a network.
3. {{page control/display-panels}}: building screens and filling them.
4. {{page control/lights-and-sirens}}: Cage Lights and Alarm Strobes.
5. {{page control/speakers}}: Speakers and where their audio comes from.

The ELCL side of all of these (the commands, triggers and example programs) is on
{{page elcl/control-and-plcs}} and the [command reference](page:elcl/commands/index). Server owners will want
{{page admin/folders}} (where audio and image files go) and {{page admin/performance}} (the limits on PLCs, screens and
audio).

## Permissions in short

On a network with a [Firewall](page:racks/security), opening these blocks' settings screens needs the Firewall's
**BUILD** permission. So do the ELCL commands that drive redstone outputs, PLCs, lights, strobes and speakers; reading
(`RTVRSIN`, a PLC's sensors) needs **VIEW**. The Display Panel commands need no permission (a screen's own
configuration screen does). A block on no network is open to everyone, except a PLC when {{config plc.plcOpenToAnyone}}
is off.
