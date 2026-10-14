---
title: Redstone, PLCs and signals from ELCL
nav: Control and PLCs
section: elcl
order: 80
summary: Programming the Control Interface's redstone, writing programs for PLCs, and switching Cage Lights, Alarm Strobes and Speakers from ELCL.
---
ELCL reaches into the world through three kinds of device: the {{item control_interface}} (redstone in and out on a
network), the {{item plc}} (a controller that runs one ELCL program by itself) and the signal devices (Cage Lights,
Alarm Strobes and Speakers). This page is about programming them. The blocks themselves, how to place, power and
configure them, are in [Control and facility](page:control/index).

## The Control Interface {#control-interface legacy="control-interface"}

A Control Interface is a network device with six redstone channels, one for each face (`*NORTH`, `*SOUTH`, `*EAST`,
`*WEST`, `*UP`, `*DOWN`), each with its own input and output level from 0 to 15. It has no screen of its own: you
drive it with two commands. It takes the first free name `CTLIF01`, `CTLIF02`... and keeps it; {{cmd RNMDEV}} renames
it. See [the Control Interface](page:control/control-interface) for the block.

| Command | Does | Firewall permission |
|---|---|---|
| {{cmd RTVRSIN}} `DEV() SIDE() RTNLVL(&L)` | Reads the level arriving on a face; `SIDE(*MAX)` gives the highest of the six. | View |
| {{cmd CHGRSOUT}} `DEV() SIDE() LVL()` | Sets what a face gives out; `SIDE(*ALL)` sets all six. | Build |

- A face never reads the block's own output back.
- The output is weak power: it powers what is right next to the face (a lamp, dust, a repeater) but not through a
  solid block.
- A level outside 0 to 15 is {{msg ELC0004}}. A name no device has is {{msg ELC1301}}; a device that isn't a Control
  Interface or PLC is {{msg ELC1303}}; an offline one is {{msg ELC1302}} (and `CHGRSOUT` then stores nothing).
- While it is offline it gives out nothing but keeps its output levels, and gives them out again when it comes back.
- When the level arriving on a face changes, it fires the `*RSCHANGE` [trigger event](page:elcl/jobs-and-scheduling#triggers)
  once for each change, with `&DATA` such as `*NORTH 7`. `ADDTRGEVT ... EVENT(*RSCHANGE) DEV(CTLIF01) VALUE('*NORTH')`
  reacts to one face only.

```elcl
DCL VAR(&LVL) TYPE(*INT)
RTVRSIN DEV(CTLIF01) SIDE(*MAX) RTNLVL(&LVL)
IF COND(&LVL *GE 8) THEN(CHGRSOUT DEV(CTLIF01) SIDE(*ALL) LVL(15))
ELSE CMD(CHGRSOUT DEV(CTLIF01) SIDE(*UP) LVL(0))
```

Typed on a command line, the return parameter is left out and Command Entry shows the value:

```elcl nocompile="typed at the ===> prompt, where RTN* parameters are left out"
RTVRSIN CTLIF01 *MAX
CHGRSOUT CTLIF01 *ALL 15
```

A trigger program for a button wired to the Control Interface's west face, which pulses the east face for two
seconds:

```elcl
/* BUTTON - ADDTRGEVT TRG(BUTTON) EVENT(*RSCHANGE) DEV(CTLIF01) +  */
/*          VALUE('*WEST') PGM(ZAGLIB/BUTTON)                        */
PGM PARM(&EVENT &DATA)
  DCL VAR(&EVENT) TYPE(*CHAR) LEN(12)
  DCL VAR(&DATA)  TYPE(*CHAR) LEN(256)
  DCL VAR(&LVL)   TYPE(*INT)

  /* &DATA is "*WEST 15": the level after the blank */
  CHGVAR VAR(&LVL) VALUE(%INT(%SST(&DATA %SCAN(' ' &DATA) + 1 3)))
  IF COND(&LVL *EQ 0) THEN(RETURN)              /* released: nothing to do */
  CHGRSOUT DEV(CTLIF01) SIDE(*EAST) LVL(15)
  DLYJOB DLY(2)
  CHGRSOUT DEV(CTLIF01) SIDE(*EAST) LVL(0)
ENDPGM
```

## Programmable Logic Controllers {#plc legacy="plc"}

A PLC runs one compiled ELCL program over and over, like an industrial controller's scan, with the six redstone faces
of a Control Interface and four slots for sensor modules. It works on its own, powered from any face, or as a device
on a network (`PLC01`, `PLC02`...). The block, its modes, screens and modules are on [the PLC page](page:control/plc).

### How a PLC runs a program {#plc-scan}

- **The scan.** The PLC runs {{config plc.plcInstructionsPerTick default}} instructions a tick; a pass that needs more
  carries on next tick. At `ENDPGM` the next pass starts from the top on the next tick. Variables keep their values
  from one pass to the next: a `VALUE` is set only when the program starts (STOP to RUN, and power-up, which a world
  reload counts as).
- **Retained variables.** `DCL ... RETAIN(*YES)` keeps a variable's value through STOP and RUN, power loss and
  reloads, and it goes with the program onto the PLC's item and onto EEPROM Cartridges. PLCSTS option 6 shows them.
  `RETAIN` in a program compiled for a job is a warning, {{msg ELC1506}}, and does nothing.
- **Delays.** {{cmd DLYTICK}} `TICKS(n)` waits *n* game ticks (1 to 1,200), {{cmd DLYJOB}} `DLY(n)` *n* seconds. The
  scan doesn't go on while it waits.
- **Messages.** {{cmd SNDPGMMSG}} writes to the PLC's own log, which PLCSTS option 6 shows with its escapes.
- **Faults.** An escape message nothing catches puts the PLC in FAULT, with the message and its line on the status
  screen, and its outputs hold their last levels (or drop to 0: {{config plc.plcFaultOutputs default}}). F9 or
  {{cmd STRPLC}} clears it and runs from the top. Use `MONMSG` for errors you expect.

### Compiling for a PLC {#plc-targets}

A PLC's program is compiled for one of two targets, and the target decides what it may use:

| Target | When | What it can use |
|---|---|---|
| PLC (on a network) | The PLC is cabled to a network; or `CRTELPGM TGT(*PLC)` in a library | Every command, with the Firewall authority of the player who last loaded the program. `CALL` finds programs on their library list; database files are the network's. |
| PLC on its own | The PLC isn't on a network | Only the language statements (`PGM`, `DCL`, `CHGVAR`, `IF`, `ELSE`, `DO`, the loops, `LEAVE`, `ITERATE`, `SELECT`, `GOTO`, `RETURN`, subroutines, `MONMSG`, `RCVMSG`, `SNDPGMMSG`), the list commands ({{cmd ADDLSTE}}, {{cmd RMVLSTE}}, {{cmd CLRLST}}), {{cmd DLYJOB}}, {{cmd DLYTICK}}, its modules ({{cmd RTVSNSVAL}}) and its own faces ({{cmd RTVRSIN}} and {{cmd CHGRSOUT}} with `DEV(*SELF)`). Anything else, `CALL` and the file commands included, is {{msg ELC1502}}. |

The PLC's editor (PLCSTS option 1) checks the program for the target that fits the PLC as it is now, and flags a
command that needs a network on its line. The PLC checks again when it runs: a program written on a network that is
later run without one stops at the first command that needs the network, with {{msg ELC1502}}.

In a PLC program, `DEV(*SELF)` names the PLC itself. On a network, other programs reach a PLC by its name, as they
would a Control Interface: `RTVRSIN DEV(PLC01) ...`, `RTVSNSVAL DEV(PLC01) MODULE(1) ...`.

### The PLC commands {#plc-commands}

| Command | Does | Firewall permission |
|---|---|---|
| {{cmd RTVSNSVAL}} `MODULE(1-4) TYPE() DEV(*SELF) RTNVAL() RTNSTS() RTNAUX()` | Reads a sensor module: its value, its status and an extra value (below). | View |
| {{cmd SNDPLCPGM}} `PGM(lib/name) DEV(PLC01) RUN(*YES)` | Loads a program compiled with `CRTELPGM TGT(*PLC)` into a PLC on the network ({{msg ELC1504}} for any other). The PLC stops first, then runs from the top unless `RUN(*NO)`. The same program loaded again keeps its retained variables. | Build |
| {{cmd STRPLC}} `DEV()` | RUN, clearing a fault ({{msg ELC1505}} with no program loaded). | Build |
| {{cmd ENDPLC}} `DEV()` | STOP: the program halts and the outputs go off; retained variables are kept. | Build |

What {{cmd RTVSNSVAL}} gives for each module (`RTNVAL` is a `*DEC`, the others `*CHAR`):

| Module | `RTNVAL` | `RTNAUX` |
|---|---|---|
| {{item presence_sensor}} (`*PRESENCE`) | Players, mobs or both within its radius (1 to 16 blocks) | The nearest one's name |
| {{item inventory_sensor}} (`*INVENTORY`) | How full the container on its face is, in percent | The item count |
| {{item fluid_sensor}} (`*FLUID`) | How full the tank on its face is, in percent | The amount in mB |
| {{item light_sensor}} (`*LIGHT`) | The light at the PLC, 0 to 15 | `*DAY` or `*NIGHT` |
| {{item timer_module}} (`*TIMER`) | The game time in ticks | The day and time, `Day 2 14:32` |

`RTNSTS` is `*OK`, or `*NOMODULE` / `*NOTARGET` when the slot is empty or the module has nothing to watch. Without
`RTNSTS` those are escape messages instead: {{msg ELC1501}} and {{msg ELC1503}}. A `TYPE()` other than `*ANY` must
match the module in the slot ({{msg ELC1501}}).

### Examples {#plc-examples}

A latch: a button on the west face toggles the east face, and a retained counter counts the presses.

```elcl target=PLC_LOCAL
PGM
  DCL VAR(&IN)     TYPE(*INT)
  DCL VAR(&WAS)    TYPE(*INT)
  DCL VAR(&ON)     TYPE(*LGL)
  DCL VAR(&PRESSES) TYPE(*INT) RETAIN(*YES)

  RTVRSIN DEV(*SELF) SIDE(*WEST) RTNLVL(&IN)
  IF COND(&IN *GT 0 *AND &WAS *EQ 0) THEN(DO)     /* a rising edge */
    CHGVAR &ON (*NOT &ON)
    CHGVAR &PRESSES (&PRESSES + 1)
  ENDDO
  CHGVAR &WAS &IN
  IF COND(&ON) THEN(CHGRSOUT DEV(*SELF) SIDE(*EAST) LVL(15))
  ELSE CMD(CHGRSOUT DEV(*SELF) SIDE(*EAST) LVL(0))
ENDPGM
```

A fuel feeder: an Inventory Sensor watches a furnace's fuel chest; when it is under a quarter full, the PLC (on a
network) moves coal into it from the network through a Deployer Plane facing the chest, and lights a lamp on its
bottom face while it does.

```elcl target=PLC
PGM
  DCL VAR(&PCT)  TYPE(*DEC) LEN(5 1)
  DCL VAR(&STS)  TYPE(*CHAR) LEN(10)
  DCL VAR(&MOVED) TYPE(*INT)

  RTVSNSVAL MODULE(1) TYPE(*INVENTORY) RTNVAL(&PCT) RTNSTS(&STS)
  IF COND(&STS *EQ '*OK' *AND &PCT *LT 25) THEN(DO)
    CHGRSOUT DEV(*SELF) SIDE(*DOWN) LVL(15)
    MOVITM ITEM(COAL) QTY(64) TODEV(DEPLOYER01) RTNMOVED(&MOVED)
    MONMSG MSGID(ELC1200 ELC1300)          /* none left, or the Deployer is off */
    DLYTICK TICKS(20)
    CHGRSOUT DEV(*SELF) SIDE(*DOWN) LVL(0)
  ENDDO
  DLYJOB DLY(5)                             /* look again in five seconds */
ENDPGM
```

Loading a program from a library into a PLC on the network:

```elcl
CRTELPGM PGM(ZAGLIB/FEEDER) TGT(*PLC)
SNDPLCPGM PGM(ZAGLIB/FEEDER) DEV(PLC01)
ENDPLC DEV(PLC01)
STRPLC DEV(PLC01)
```

The [Worked examples](page:elcl/examples#ex-plc) have one more, reading a Presence Sensor and a Light Sensor.

## Cage Lights, Alarm Strobes and Speakers {#signals legacy="signals"}

On a network, each signal device is a device with a name: `LGT01` for a Cage Light, `SRN01` for an Alarm Strobe,
`SPK01` for a Speaker. Their commands take `DEV()` as a list of names (`DEV(LGT01 LGT02)`) or `*ALL` for every device of
that kind on the network. Every name is checked before any device changes: not found is {{msg ELC1301}}, offline
{{msg ELC1302}}, another kind of device {{msg ELC1303}}. They all need the Firewall's build permission.

| Command | Does |
|---|---|
| {{cmd CHGLGT}} `DEV() STATUS(*ON\|*OFF\|*TOGGLE) LVL(1-15)` | A Cage Light's network state and light level ({{msg ELC2401}} outside 1 to 15). The network's state adds to the light's redstone. |
| {{cmd STRSRN}} `DEV() SOUND() MODE()` | Starts an Alarm Strobe, with its tone (`*WAIL`, `*YELP`, `*KLAXON`, `*BELL`, `*HORN`, `*BEEP` or `*NONE`) and light mode (`*SOLID`, `*SLOW`, `*MEDIUM`, `*FAST`); `*SAME` keeps its own setting. |
| {{cmd ENDSRN}} `DEV()` | Stops an Alarm Strobe. |
| {{cmd PLYAUD}} `DEV() SRC() VOL() LOOP()` | A Speaker plays a file from the system's audio folder or a web URL. |
| {{cmd PLYNOTE}} `DEV() INST() NOTE(0-24)` | A Speaker plays a note block note; several notes make a sequence, the rest played one per rising redstone edge ({{msg ELC2407}} outside 0 to 24). |
| {{cmd PLYMID}} `DEV() FILE() MAP() MAPBY()` | A Speaker plays a MIDI file, its parts on the speakers `MAP` names, all in time. |
| {{cmd STPAUD}} `DEV()` | Stops a Speaker (and every speaker in its MIDI play). |

An Alarm Strobe or Speaker whose trigger is set to *Redstone* takes no network commands ({{msg ELC2408}}): set it to
*Network* or *Both* on its settings screen. A Cage Light set to *Network only* follows the network alone. The
[Cage Lights and Alarm Strobes](page:control/lights-and-sirens) and [Speakers](page:control/speakers) pages have the
settings, the audio folders and the file limits.

An evacuation alarm:

```elcl
STRSRN DEV(*ALL) SOUND(*WAIL) MODE(*FAST)        /* every strobe on the network */
CHGLGT DEV(LGT01 LGT02) STATUS(*ON) LVL(15)
PLYAUD DEV(SPK01) SRC('evacuate.ogg') LOOP(*YES)
DLYJOB DLY(30)
ENDSRN DEV(*ALL)
CHGLGT DEV(LGT01 LGT02) STATUS(*OFF)
STPAUD DEV(SPK01)
PLYMID DEV(SPK01) FILE('all-clear.mid') VOL(60) +
       MAP((1 SPK01) (2 SPK02) (10 SPK04)) MAPBY(*CHANNEL)   /* melody, bass, drums */
MONMSG MSGID(ELC2400)                             /* a missing file isn't fatal  */
```

A doorbell as a trigger program: three notes on a Speaker when a button on `CTLIF01`'s north face is pressed.

```elcl
/* DOORBELL - ADDTRGEVT TRG(BELL) EVENT(*RSCHANGE) DEV(CTLIF01) +  */
/*            VALUE('*NORTH') PGM(ZAGLIB/DOORBELL)                    */
PGM PARM(&EVENT &DATA)
  DCL VAR(&EVENT) TYPE(*CHAR) LEN(12)
  DCL VAR(&DATA)  TYPE(*CHAR) LEN(256)
  IF COND(%TRIM(&DATA) *EQ '*NORTH 0') THEN(RETURN)     /* released */
  PLYNOTE DEV(SPK01) INST(*CHIME) NOTE(18)
  DLYTICK TICKS(5)
  PLYNOTE DEV(SPK01) INST(*CHIME) NOTE(14)
  DLYTICK TICKS(5)
  PLYNOTE DEV(SPK01) INST(*CHIME) NOTE(11)
ENDPGM
```

Signal commands work from a PLC on a network too, so a PLC can sound an alarm from its own sensors without a job
host.
