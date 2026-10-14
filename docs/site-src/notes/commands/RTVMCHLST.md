Lists the device names of the network's Arcforge machines (those with a {{item small_wireless_bridge}} on) into a
`*LIST` variable, in Work with Devices' order.

- **`TYPE`**: a machine's type code, the start of its device name (`ARCCRU`), or its full type ID
  (`'arcforge:arc_crusher'`). `*ALL` (the default) keeps every machine.
- **`STATUS`**: keeps machines whose status, as {{cmd RTVMCHSTS}} returns it, is the one you give: `*IDLE`, `*RUNNING`,
  `*NOPOWER`, `*NOINPUT`, `*BLOCKED`, `*DISABLED`, `*NOTFORMED`, `*FAULT` or `*OFFLINE`.

Nothing matching gives an empty list. The command sends no completion message. See {{page integrations/arcforge}}.

```elcl
DCL VAR(&BROKEN) TYPE(*LIST)
DCL VAR(&MCH) TYPE(*CHAR) LEN(10)
RTVMCHLST STATUS(*FAULT) RTNLST(&BROKEN)
FOREACH VAR(&MCH) IN(&BROKEN)
  SNDMSG MSG(&MCH *BCAT 'has a fault') TOUSR(*SYSOPR)
ENDFOR
```
