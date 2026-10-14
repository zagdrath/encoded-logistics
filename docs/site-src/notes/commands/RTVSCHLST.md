Lists what the network can craft: every output of the schematics in its schematic holders (Fabrication Servers,
Fabricators, Gateways, a Midrange System and the others Work with Schematics lists), each ID once, into `RTNLIST`, and
how many there are into `RTNCOUNT`. Items come back as {{cmd STRCRAFT}} takes them (`IRON_INGOT`,
`encodedlogistics:logic_die`); a fluid or gas output comes back as its type and ID, `FLUID minecraft:water` or
`PRES arcforge:hydrogen`.

`HOLDER(*ALL)` (the default) looks at every holder. A name looks only at holders whose device name is that name, or
whose shown name starts with it (`FABSRV01`, `FAB`). No holder matching a name is {{msg ELC2501}}; a network with no
holders at all gives an empty list with `*ALL`. The command sends no completion message. See
{{page crafting/schematics}}.

```elcl
DCL VAR(&MAKES) TYPE(*LIST)
DCL VAR(&N) TYPE(*INT)
RTVSCHLST HOLDER(FABSRV01) RTNLIST(&MAKES) RTNCOUNT(&N)
SNDPGMMSG MSG('FABSRV01 holds schematics for' *BCAT %CHAR(&N) *BCAT 'outputs')
```
