Loads a program from a library into a {{item plc}} on your network, the way you'd otherwise load it from a cartridge
or the PLC's editor. Only a program compiled for a PLC, with {{cmd CRTELPGM}} `TGT(*PLC)`, can be loaded; any other is
{{msg ELC1504}}. See {{page control/plc}} and {{page elcl/control-and-plcs}}.

- **`PGM`**: `LIB/NAME`, or a bare name found down your library list (`*LIBL`; {{msg ELC0203}} when no library on it has
  the program). `*CURLIB/NAME` is your current library.
- **`DEV`**: the PLC's device name. {{msg ELC1301}} for no such device, {{msg ELC1303}} for one that isn't a PLC.
- **`RUN(*YES)`** (the default) starts the program from the top once it's in; `RUN(*NO)` leaves the PLC stopped, ready
  for {{cmd STRPLC}}.

Loading stops the PLC first, which sets its outputs to 0, and clears its log and any fault. You become the program's
loader: when the PLC is on a network, its program's commands run with your Firewall permissions. Loading the same
program (by name) again keeps its `RETAIN` variables; a different program starts without them.

On success the command sends {{msg ELC1509}}.

```elcl
SNDPLCPGM PGM(PLCLIB/PUMPCTL) DEV(PLC01)
SNDPLCPGM PGM(LAMPTEST) DEV(PLC02) RUN(*NO)
```
