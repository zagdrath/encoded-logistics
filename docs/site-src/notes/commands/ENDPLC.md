Stops a {{item plc}}'s program, as F7 (STOP) on its status screen does. The program halts where it is, every output
goes to 0, and its `RETAIN` variables keep their values for the next start. The program stays loaded: {{cmd STRPLC}}
runs it again from the top.

`DEV` is the PLC's device name on your network ({{msg ELC1301}}, {{msg ELC1303}} for a device that isn't a PLC). Stopping
a PLC that isn't running is fine. On success the command sends {{msg ELC1508}}. See {{page control/plc}}.

```elcl
ENDPLC DEV(PLC01)
SNDPLCPGM PGM(PLCLIB/PUMPCTL2) DEV(PLC01) RUN(*NO)
STRPLC DEV(PLC01)
```
