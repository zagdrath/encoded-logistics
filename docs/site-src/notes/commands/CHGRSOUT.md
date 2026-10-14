Sets the redstone level a {{item control_interface}} or {{item plc}} gives out on one face, or on all six with
`SIDE(*ALL)`. `LVL` is 0 (off) to 15; a value outside that is {{msg ELC0004}}. {{cmd RTVRSIN}} reads what arrives. See
{{page control/control-interface}} and {{page control/plc}}.

`DEV` and `SIDE` work as for {{cmd RTVRSIN}}: a device name or `*SELF` (a PLC's own program only), and world directions.
A name no device has is {{msg ELC1301}}, a device that's neither kind is {{msg ELC1303}}, and an offline one is
{{msg ELC1302}}, in which case nothing changes.

The level is kept until it's set again. A Control Interface gives it out only while it's online. A PLC gives its outputs
out while it's powered; stopping it ({{cmd ENDPLC}}, F7), starting it from the top ({{cmd STRPLC}}, F6) or loading a
program sets them all to 0, and a fault keeps or drops them as {{config plc.plcFaultOutputs default}} says. The command
sends no completion message.

## On a PLC

`CHGRSOUT DEV(*SELF)` runs on a PLC with no network (the badge above). In a program compiled for a PLC on its own, any
`DEV` other than `*SELF` is {{msg ELC1502}}. The PLC's I/O screen shows which program line last set each face. See
{{page elcl/control-and-plcs}}.

```elcl target=PLC_LOCAL
DCL VAR(&ON) TYPE(*LGL)
DOWHILE COND('1')
  CHGVAR VAR(&ON) VALUE(*NOT &ON)
  IF COND(&ON) THEN(CHGRSOUT DEV(*SELF) SIDE(*ALL) LVL(15))
  ELSE CMD(CHGRSOUT DEV(*SELF) SIDE(*ALL) LVL(0))
  DLYTICK TICKS(10)
ENDDO
```

From a batch job, light a lamp wired to a Control Interface:

```elcl
CHGRSOUT DEV(CTLIF01) SIDE(*UP) LVL(15)
DLYJOB DLY(30)
CHGRSOUT DEV(CTLIF01) SIDE(*UP) LVL(0)
```
