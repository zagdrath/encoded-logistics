Reads the redstone level arriving on one face of a {{item control_interface}} or a {{item plc}}, 0 to 15, into
`RTNLVL`. {{cmd CHGRSOUT}} sets what a face gives out. See {{page control/control-interface}} and {{page control/plc}}.

- **`DEV`**: a Control Interface or PLC on your network by device name (`CTLIF01`, `PLC01`), or `*SELF`: the PLC whose
  program is running. A name no device has is {{msg ELC1301}}, a device that's neither is {{msg ELC1303}}, and an offline
  one is {{msg ELC1302}}. `*SELF` anywhere but in a PLC's own program is {{msg ELC1301}}.
- **`SIDE`**: a face by compass direction (`*NORTH`, `*SOUTH`, `*EAST`, `*WEST`) or `*UP` / `*DOWN`. These are the world's
  directions, whichever way the block faces. `*MAX` returns the highest level arriving on any face.

The command sends no completion message.

## On a PLC

`RTVRSIN DEV(*SELF)` is one of the commands a PLC runs with no network at all (the badge above): in a program compiled for
a PLC on its own, `DEV` has to be `*SELF`, and any other device is {{msg ELC1502}}, from the compiler and at run time. On a
PLC that's cabled to a network, its program can read a Control Interface or another PLC by name as well. See
{{page elcl/control-and-plcs}}.

```elcl target=PLC_LOCAL
DCL VAR(&LVL) TYPE(*INT)
DOWHILE COND('1')
  RTVRSIN DEV(*SELF) SIDE(*MAX) RTNLVL(&LVL)
  IF COND(&LVL *GT 0) THEN(CHGRSOUT DEV(*SELF) SIDE(*UP) LVL(15))
  ELSE CMD(CHGRSOUT DEV(*SELF) SIDE(*UP) LVL(0))
  DLYTICK TICKS(2)
ENDDO
```

From a batch job on the network, read a Control Interface by name:

```elcl
DCL VAR(&LVL) TYPE(*INT)
RTVRSIN DEV(CTLIF01) SIDE(*NORTH) RTNLVL(&LVL)
IF COND(&LVL *GE 8) THEN(SNDMSG MSG('Tank alarm on CTLIF01') TOUSR(*SYSOPR))
```
