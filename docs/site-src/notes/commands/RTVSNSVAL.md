Reads one of a {{item plc}}'s sensor modules. A PLC has four module slots; `MODULE` is the slot, 1 to 4. Each read
is taken fresh, at the moment the command runs. See {{page control/plc}}.

| Module | `TYPE` | `RTNVAL` | `RTNAUX` |
|---|---|---|---|
| {{item presence_sensor}} | `*PRESENCE` | Players or mobs (as set in the Modules screen) within its radius | The nearest one's name |
| {{item inventory_sensor}} | `*INVENTORY` | How full the container on its face is, in percent, slot by slot as a comparator sees it | The number of items in it |
| {{item fluid_sensor}} | `*FLUID` | How full the tank on its face is, in percent | The amount, in mB |
| {{item light_sensor}} | `*LIGHT` | The light level at the PLC, 0 to 15 | `*DAY` or `*NIGHT` |
| {{item timer_module}} | `*TIMER` | The game time, in ticks | The day and time (`Day 2 14:32`) |

An Inventory or Fluid Sensor's face is the block behind the PLC unless its setting in the Modules screen names another
face; a Presence Sensor's radius and what it counts are set there too. `TYPE(*ANY)` (the default) reads whatever is in
the slot; any other `TYPE` requires that kind of module there.

## Errors or a status

`RTNSTS` decides how problems come back:

- **With `RTNSTS`**, nothing fails: `RTNSTS` gets `*OK`, `*NOMODULE` (an empty slot, or a module of another kind than
  `TYPE`) or `*NOTARGET` (an Inventory or Fluid Sensor with no container or tank on its face; `RTNAUX` then names the
  face, `*WEST`). `RTNVAL` is `0` unless the status is `*OK`.
- **Without `RTNSTS`**, an empty or wrong slot is {{msg ELC1501}} and a sensor with nothing to read is {{msg ELC1503}}.

The command sends no completion message.

## Where it runs

`DEV(*SELF)` (the default) is the PLC running the program, and that works with no network: RTVSNSVAL is one of the
commands a PLC on its own can run (the badge above). From a batch job, or from another PLC, `DEV(PLC01)` reads a PLC on
the same network by name ({{msg ELC1301}} for no such device, {{msg ELC1303}} for one that isn't a PLC). `*SELF` outside
a PLC's own program is {{msg ELC1301}}, and in a program for a PLC on its own any other `DEV` is {{msg ELC1502}}. See
{{page elcl/control-and-plcs}}.

```elcl target=PLC_LOCAL
DCL VAR(&WHO) TYPE(*DEC) LEN(5 0)
DCL VAR(&STS) TYPE(*CHAR) LEN(10)
DOWHILE COND('1')
  RTVSNSVAL MODULE(1) TYPE(*PRESENCE) RTNVAL(&WHO) RTNSTS(&STS)
  IF COND(&STS *EQ '*OK' *AND &WHO *GT 0) THEN(CHGRSOUT DEV(*SELF) SIDE(*ALL) LVL(15))
  ELSE CMD(CHGRSOUT DEV(*SELF) SIDE(*ALL) LVL(0))
  DLYTICK TICKS(10)
ENDDO
```

From a batch job, check a networked PLC's tank:

```elcl
DCL VAR(&PCT) TYPE(*DEC) LEN(5 0)
RTVSNSVAL MODULE(2) TYPE(*FLUID) DEV(PLC01) RTNVAL(&PCT)
IF COND(&PCT *LT 10) THEN(SNDMSG MSG('PLC01 tank under 10%') TOUSR(*SYSOPR))
```
