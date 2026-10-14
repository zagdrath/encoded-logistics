`ENDSRN` turns off the network's half of an Alarm Strobe's trigger. The strobe goes quiet and dark unless its
**Trigger** is Both and the redstone at its block is still on: then it stays active until the signal goes.

Its tone and light mode are kept, so a later {{cmd STRSRN}} with `*SAME` starts it the same way again.

`DEV` takes up to 32 names, or `*ALL` for every online strobe on the network that takes network commands. Every named
strobe is checked first: not there ({{msg ELC1301}}), offline ({{msg ELC1302}}), not an Alarm Strobe
({{msg ELC1303}}), or its Trigger is Redstone ({{msg ELC2408}}; `*ALL` leaves those out). It sends no completion
message.

```elcl
ENDSRN DEV(SRN01 SRN02)
ENDSRN DEV(*ALL)
```

See [Alarm Strobes](page:control/lights-and-sirens#alarm-strobes).
