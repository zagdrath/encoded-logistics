Reads what an Arcforge machine is doing, through the {{item small_wireless_bridge}} on it. `MCH` is the machine's
device name, made from its type (`ARCCRU01` for an Arc Crusher, `ELECTR01` for an Electrolyzer). A name no device has is
{{msg ELC1301}}; a device that isn't a bridged machine is {{msg ELC1318}}, and so is every machine while the Arcforge
integration is off. See {{page integrations/arcforge}}.

| Variable | Gets | When the machine can't be read |
|---|---|---|
| `RTNSTS` | `*IDLE`, `*RUNNING`, `*NOPOWER`, `*NOINPUT`, `*BLOCKED` (output full), `*DISABLED`, `*NOTFORMED`, `*FAULT` or `*UNKNOWN` | `*OFFLINE` |
| `RTNRSN` | The machine's own words for its state ("Crushing", "No fuel") | blanks |
| `RTNPCT` | Progress through the current operation, 0 to 100; `-1` between operations | `-1` |
| `RTNFE` | FE stored (`0` for a machine without FE) | `0` |
| `RTNFECAP` | FE capacity (`0` without FE) | `0` |
| `RTNRCP` | What the current operation makes ("5 Bone Meal"); blanks when idle | blanks |

`*OFFLINE` means the bridge isn't on the network, or its machine is gone or not loaded; unlike {{cmd RTVMCHSTAT}}, this
command doesn't fail then. `*DISABLED` is a machine that's switched off, for instance with {{cmd CHGMCHSTS}}. Text longer
than your variable is cut to fit. The command sends no completion message.

```elcl
DCL VAR(&STS) TYPE(*CHAR) LEN(10)
DCL VAR(&PCT) TYPE(*INT)
RTVMCHSTS MCH(ARCCRU01) RTNSTS(&STS) RTNPCT(&PCT)
IF COND(&STS *EQ '*NOPOWER') THEN(CHGMCHCFG MCH(ARCCRU01) PWRNET(*YES))
```
