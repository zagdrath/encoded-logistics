Reports on a chassis: a {{item chassis_switch}} (`CSW01`), {{item blade_enclosure}} (`BLD01`) or
{{item optical_transport_shelf}} (`OTS01`), by its device name. It returns every bay, empty ones included, as
{{cmd WRKHW}} with F11 shows them. A name that isn't a chassis on your network (or no device at all) is
{{msg ELC2701}}. See {{page racks/chassis}}.

`RTNLIST` gets one element per bay: its label, the part in it (`*NONE` when empty) and its state.

```text
A SUPERVISOR_CARD *ACTIVE
B SUPERVISOR_CARD *STANDBY
P4 *NONE *EMPTY
```

A bay's state is one of `*ONLINE`, `*ACTIVE`, `*STANDBY`, `*FAILED`, `*EMPTY`, `*OFFLINE` or `*PROTECT`, as
{{cmd WRKHW}} shows it. `RTNSTS` gets the chassis as a whole: `*ONLINE`, `*DEGRADED` (working, with a warning) or
`*FAULT` (in a fault, or offline). When it's degraded the command also sends the warning {{msg ELC2702}}, with the
reason; otherwise it sends no message.

```elcl
DCL VAR(&BAYS) TYPE(*LIST)
DCL VAR(&BAY) TYPE(*CHAR) LEN(40)
DCL VAR(&STS) TYPE(*CHAR) LEN(10)
RTVCHSSTS DEV(CSW01) RTNLIST(&BAYS) RTNSTS(&STS)
IF COND(&STS *NE '*ONLINE') THEN(DO)
  FOREACH VAR(&BAY) IN(&BAYS)
    IF COND(%SCAN('*FAILED' &BAY) *GT 0) THEN(SNDMSG MSG('CSW01 bay failed:' *BCAT &BAY) TOUSR(*SYSOPR))
  ENDFOR
ENDDO
```
