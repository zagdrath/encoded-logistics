Starts a {{item plc}}'s program, as F6 (RUN) on its status screen does. The program starts from the top: its outputs
go to 0 first, and only its `RETAIN` variables keep their values. A PLC that's already running is restarted from the
top. A fault is cleared (it's kept as the last error the status screen shows).

`DEV` is the PLC's device name on your network ({{msg ELC1301}}, {{msg ELC1303}} for a device that isn't a PLC). A PLC
with no program loaded is {{msg ELC1505}}: load one with {{cmd SNDPLCPGM}} first. On success the command sends
{{msg ELC1507}}. See {{page control/plc}}.

```elcl
STRPLC DEV(PLC01)
MONMSG MSGID(ELC1505) EXEC(SNDPLCPGM PGM(PLCLIB/PUMPCTL) DEV(PLC01))
```
