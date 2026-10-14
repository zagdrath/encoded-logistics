Looks a device up by its name and returns its status and type.

## Device names

Every device that takes part in ELCL has a name: its type and a number, given the first time it's on a network (the
lowest number free there) and kept with the device until someone renames it with {{cmd RNMDEV}} or Work with Devices'
2=Change. Examples: `CTLIF01` (Control Interface), `ELDESK01` (Terminal Desk), `UPS01`, `TAPELIB01`, `EGRESS01`,
`PLC01`, `ARCCRU01` (an Arc Crusher with a Small Wireless Bridge). {{page reference/device-types}} lists every type, and
{{cmd WRKDEV}} shows the names on your network. Names aren't case-sensitive. A name with a slash, `ELNET02/UPS01`, is a
device another network publishes through its Directory Servers (see {{cmd RTVDIRENT}}); your Firewall permission on that
network decides whether you may use it.

## Returns

| Variable | Gets |
|---|---|
| `RTNSTS` | `*DISABLED` for a cable part switched off with {{cmd CHGDEVSTS}}, `*FAULT` for a rack device in a fault state, otherwise `*ONLINE` or `*OFFLINE` |
| `RTNTYPE` | The device's type code, the start of its name: `UPS`, `TAPELIB`, `EGRESS`... (`DESK` for a Terminal Desk, whose names start `ELDESK`) |

A name no device on the network has is {{msg ELC1301}}: the command has no "not found" status, so monitor for it. It
sends no completion message.

```elcl
DCL VAR(&STS) TYPE(*CHAR) LEN(10)
DCL VAR(&TYPE) TYPE(*CHAR) LEN(10)
RTVDEVSTS DEV(UPS01) RTNSTS(&STS) RTNTYPE(&TYPE)
MONMSG MSGID(ELC1301) EXEC(CHGVAR VAR(&STS) VALUE('*MISSING'))
IF COND(&STS *NE '*ONLINE') THEN(SNDMSG MSG('UPS01 is' *BCAT &STS) TOUSR(*SYSOPR))
```
