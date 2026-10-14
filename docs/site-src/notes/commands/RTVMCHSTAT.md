Reads an Arcforge machine's running totals, as Work with Machines' statistics show them. `MCH` names the machine as for
{{cmd RTVMCHSTS}} ({{msg ELC1301}}, {{msg ELC1318}}). A machine that's gone or not loaded is {{msg ELC1302}}.

| Variable | Gets |
|---|---|
| `RTNOPS` | Operations completed |
| `RTNPRD` | Items produced |
| `RTNCNS` | Items consumed |
| `RTNFLDPRD` | Fluid produced, in mB |
| `RTNFLDCNS` | Fluid consumed, in mB |
| `RTNUPTIME` | Seconds spent running |
| `RTNOPM` | Operations a minute, recently (two decimals) |

The totals are Arcforge's own: kept while the machine stands, lost when it's broken. The command sends no completion
message.

```elcl
DCL VAR(&OPS) TYPE(*INT)
DCL VAR(&OPM) TYPE(*DEC) LEN(7 2)
RTVMCHSTAT MCH(ELECTR01) RTNOPS(&OPS) RTNOPM(&OPM)
SNDPGMMSG MSG('ELECTR01:' *BCAT %CHAR(&OPS) *BCAT 'ops,' *BCAT %CHAR(&OPM) *BCAT 'a minute')
```
