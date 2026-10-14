---
title: Programmable Logic Controller
nav: PLC
section: control
order: 20
summary: The PLC runs one ELCL program over and over, with six redstone faces, four sensor module slots and its own green screens, on its own or as a network device.
---
The {{item plc}} (PLC) is a wall-mounted controller that runs one compiled ELCL program continuously, the way an
industrial PLC runs its scan. It has the six redstone faces of a [Control Interface](page:control/control-interface),
four slots for sensor modules, and its own green screens for status, I/O, modules and editing. It works entirely on its
own from a small FE buffer, or as a device on a network, where its program can use every ELCL command.

This page covers the block. How to write programs for it is on {{page elcl/control-and-plcs}}.

{{recipe plc}}

{{screenshot file="control/plc-on-wall.png" alt="A PLC on a wall with two sensor modules fitted, a lever and a lamp on its faces" show="A PLC mounted on a stone wall in RUN (green RUN LED), a Presence Sensor and a Timer Module in two slots, a lever on one side face and a redstone lamp on the other" angle="Front view, slightly to one side so a side face shows, 2-3 blocks away" size="1600x900"}}

## Placing and powering it {#placing}

Place it on a wall and it faces out from it (anywhere else it faces you). The block is a steel backplate with a DIN
rail, the CPU module (its LCD, RUN and ERROR LEDs and an I/O LED for each face) and four module slots beside it.

| | On its own | Cabled to a network |
|---|---|---|
| How | Nothing cabled to it | A cable on any face except its front |
| Power | {{config plc.plcEnergy default}} FE a tick from its own buffer of {{config plc.plcBuffer default}} FE, filled with FE from any face | The network powers it; it is a device with one lane |
| Name | - | `PLC01`, `PLC02`, ... (type `PLC` in Work with Devices, with its program's name) |
| What its program can do | The language itself, its own faces and its modules (see [Programs](#programs)) | Every ELCL command |

With no power it goes **OFF**: its outputs go dark and the program halts. When power comes back it runs again from the
top, if it was running. A world reload counts as a power-up.

Breaking a PLC keeps its program and its device name on the item; its modules drop separately. Placing a PLC item that
carries a program puts the program back.

## Modes {#modes}

| Mode | What it means |
|---|---|
| OFF | No power: outputs dark, program halted |
| STOP | Powered but not running; its outputs are cleared |
| RUN | Running its program; each face's I/O LED lights for 10 ticks after that face's level changes |
| FAULT | The program failed (see [Faults](#faults)); the ERROR LED shows it |

F6 (or {{cmd STRPLC}}) runs it and F7 (or {{cmd ENDPLC}}) stops it.

## The scan {#scan}

The program runs continuously, {{config plc.plcInstructionsPerTick default}} instructions a tick. A pass that needs
more carries on next tick. When it reaches `ENDPGM`, the next pass starts from the top on the next tick, with every
variable carrying its value over from the last pass: a variable's `VALUE()` is set only when the PLC goes from STOP to
RUN and at power-up. The status screen shows the scan time (ticks a pass takes) and the instructions a tick against the
budget.

Variables declared with `RETAIN(*YES)` are kept in the program itself, so they survive STOP and RUN, power loss, world
reloads, and being saved to a cartridge or carried on the item. Option 6 on the status screen lists them.

## Faults {#faults}

An escape message the program doesn't monitor (with `MONMSG`) puts the PLC in **FAULT**. The message, its text and the
line it came from show on the status screen and the LCD. What the outputs do then is set by the server:

| {{config plc.plcFaultOutputs}} | Outputs on FAULT |
|---|---|
| `HOLD` (the default) | Keep their last levels |
| `ZERO` | Drop to 0 |

F9 on the status screen (or {{cmd STRPLC}}) clears the fault and runs the program from the top. STOP clears the
outputs.

## Sensor modules {#modules}

Use a module on the PLC to put it in the first free slot; sneak-use the PLC with an empty hand to take the last one
out. Each reading gives a value, a status (`*OK`, `*NOMODULE` or `*NOTARGET`) and an extra ("aux") value:

| Module | `TYPE()` | Reads | Value | Aux | Setting |
|---|---|---|---|---|---|
| {{item presence_sensor}} | `*PRESENCE` | Players or mobs within its radius | How many | The nearest one's name | Radius 1-16 (default 8); `*PLAYERS` (default), `*MOBS` or `*ALL` |
| {{item inventory_sensor}} | `*INVENTORY` | The container on its face | Percent full (by slot, as a comparator reads it) | Item count | The face (default: behind the PLC) |
| {{item fluid_sensor}} | `*FLUID` | The tank on its face | Percent full | Amount in mB | The face (default: behind the PLC) |
| {{item light_sensor}} | `*LIGHT` | The light level at the PLC | 0-15 | `*DAY` or `*NIGHT` | None |
| {{item timer_module}} | `*TIMER` | The game clock | Game ticks | The day and time, such as `Day 2 14:32` | None |

The Timer Module reads the game's own clock, not real time. A program reads a module with {{cmd RTVSNSVAL}}:

```elcl target=PLC_LOCAL
DCL VAR(&N) TYPE(*INT)
DCL VAR(&S) TYPE(*CHAR) LEN(10)
RTVSNSVAL MODULE(1) RTNVAL(&N) RTNSTS(&S)     /* a Presence Sensor in slot 1 */
IF COND(&S *EQ '*OK' *AND &N *GT 0) THEN(CHGRSOUT DEV(*SELF) SIDE(*UP) LVL(15))
ELSE CMD(CHGRSOUT DEV(*SELF) SIDE(*UP) LVL(0))
```

Without `RTNSTS`, a missing module is {{msg ELC1501}} and a module with nothing to read is {{msg ELC1503}}.

The modules' recipes are a Circuit Substrate and a Logic Die with one more part each: a stone pressure plate, a
comparator, a bucket, a daylight detector or a clock.

{{recipe presence_sensor}}

## The PLC's screens {#screens}

Right-click the PLC to open its screens. Who may open them:

- **On a network:** players with the Firewall's **BUILD** permission (everyone, without a Firewall).
- **On its own:** anyone. With {{config plc.plcOpenToAnyone}} set to false, only the player who placed it and server
  operators.

The screens are green screens like the [Terminal OS](page:elcl/using-the-terminal), with the same keys:

| Screen | Title | What it shows |
|---|---|---|
| PLCSTS | PLC Status | The PLC, its network, program and size, mode, scan time, instructions a tick, who loaded the program (and their authority), power, and the last error. The menu below |
| PLCIO | PLC I/O Table | Each face's input and output and what drives it: the `CHGRSOUT` line that set an output, or the block next to a face giving it its input. Live, with level bars, refreshed every 10 ticks |
| PLCMOD | PLC Modules | Each slot's module, setting, value and status. 2=Change setting (radius and what it counts, or the face; F4 lists the values), 4=Remove module |
| PLCRTN | PLC Retained Variables | The program's `RETAIN(*YES)` variables, and the PLC's log: its `SNDPGMMSG` messages and escapes (16 entries) |

PLCSTS's menu: **1** Edit program, **2** Display I/O table, **3** Display modules, **4** Save program to cartridge,
**5** Load program from cartridge, **6** Display retained variables. Keys: F6 Run, F7 Stop, F9 Clear fault, F10 I/O
table, F11 Modules, F12 back. On a PLC cabled to a network, anything else typed on PLCSTS's command line runs as an
ELCL command on its network; on one that isn't, only 1-6 work there.

{{screenshot file="control/plc-plcsts.png" alt="The PLC Status screen of a running PLC" show="PLCSTS for PLC01 cabled to a network, mode RUN, a program loaded (name, size, scan time, instructions per tick), Loaded by filled in, Last error *NONE, the six options listed" angle="GUI scale 2, the whole green screen in view" size="1280x720"}}

### The editor {#editor}

Option 1 opens the program in the Terminal OS [source editor](page:elcl/libraries-and-editor), as member
`*PLC/<program>` (a new PLC's program is called `PLCPGM`). It has the editor's syntax check, F4 prompter and command
line, and it checks the program for this PLC: on its own, a command that needs a network is flagged ({{msg ELC1502}}).
Saving sends the source to the PLC, which compiles it. An error shows on its line; when the program goes in you get
{{msg ELC0213}}, and the PLC restarts it if it was running. Leaving the editor goes back to PLCSTS.

## Getting a program onto a PLC {#programs}

There are four ways:

1. **Write it on the PLC:** PLCSTS option 1, above.
2. **From an EEPROM Cartridge:** hold a cartridge and use PLCSTS option **4** to save the PLC's program (with its
   retained variables) onto it, or option **5** to load the cartridge's program into the PLC. Loading stops the PLC; F6
   runs the new program. Only one cartridge of a stack is written. The cartridge has no slot: it is used from your
   hand.
3. **From a library, over the network:** compile the program for a PLC with {{cmd CRTELPGM}} `TGT(*PLC)`, then send it
   with {{cmd SNDPLCPGM}} `PGM(MYLIB/DOORCTL) DEV(PLC01)`. A program not compiled for a PLC is {{msg ELC1504}}. The PLC
   stops first, then runs from the top (unless `RUN(*NO)`). Sending the same program again keeps its retained
   variables.
4. **On the item:** a PLC you broke carries its program; place it again.

The {{item eeprom_cartridge}} is either blank or carries a program, and its tooltip says which.

{{recipe eeprom_cartridge}}

Whoever loads a program (by any of these ways) becomes its **loader**. On a network the program runs with the Firewall
authority of that player, as PLCSTS shows. On its own a PLC's program may do everything it can run.

### What a program can do without a network {#local}

On its own a PLC runs the language statements (`PGM`, `DCL`, `CHGVAR`, `IF`, the loops, `SELECT`, subroutines,
`MONMSG`, `RCVMSG`, `SNDPGMMSG` ...), the list commands, {{cmd DLYJOB}}, {{cmd DLYTICK}}, its modules
({{cmd RTVSNSVAL}}) and its own faces ({{cmd RTVRSIN}} and {{cmd CHGRSOUT}} with `DEV(*SELF)`; `DEV` is required on both). Anything
else is {{msg ELC1502}}, both when you save it and at run time. Cabled to a network, every command works, and its
messages go to the PLC's own log. {{page elcl/control-and-plcs}} has the full list and examples.

A program that counts button presses on one face and mirrors the button onto another:

```elcl target=PLC_LOCAL
PGM
  DCL VAR(&IN) TYPE(*INT)
  DCL VAR(&OPENS) TYPE(*INT) RETAIN(*YES)    /* counted across STOP / RUN and reloads */
  DCL VAR(&WAS) TYPE(*INT)
  RTVRSIN DEV(*SELF) SIDE(*WEST) RTNLVL(&IN)  /* a button on the west face             */
  IF COND(&IN *GT 0 *AND &WAS *EQ 0) THEN(CHGVAR VAR(&OPENS) VALUE(&OPENS + 1))
  CHGVAR VAR(&WAS) VALUE(&IN)                 /* kept from scan to scan                */
  CHGRSOUT DEV(*SELF) SIDE(*EAST) LVL(&IN)    /* the door on the east face             */
ENDPGM
```

## Limits {#limits}

At most {{config plc.plcMaxPerChunk default}} PLCs run in one chunk and {{config plc.plcMaxPerServer default}} on the
whole server. Another one stays in STOP, with the note "PLC limit reached" on its status screen, until there is room.
See {{page admin/performance}}.

## Server options {#config}

{{table config section=plc}}
