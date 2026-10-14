Changes an Arcforge machine's settings, as its own screen (or Work with Machines' 2=Change, which runs this command)
would. Every parameter left at `*SAME` stays as it is. `MCH` names the machine as for {{cmd RTVMCHSTS}}
({{msg ELC1301}}, {{msg ELC1318}}); a machine that's gone or not loaded is {{msg ELC1302}}. See
{{page integrations/arcforge}}.

| Parameter | Sets |
|---|---|
| `RSMODE` | The redstone mode, from the ones the machine's Redstone tab offers: `*IGNORE`, `*HIGH`, `*LOW`, `*PULSE`, `*THROTTLE` |
| `SIDE` with `SIDEMODE` | One side's mode, the side counted from the machine's front (`*TOP`, `*BOTTOM`, `*LEFT`, `*RIGHT`, `*BACK`, `*FRONT`), the mode by the name the machine's screen uses (`input`, `output`, `energy`, `none`...). Give both or neither: one alone is {{msg ELC0102}}. A machine with no side settings is {{msg ELC1320}} |
| `AUTOEJECT` | Whether it pushes its output out by itself |
| `PWRNET` | Whether the network's energy powers it, through its bridge (`*YES` for a machine without FE is {{msg ELC1320}}) |
| `GATEWAY` | The {{item gateway}} whose crafting steps it runs, by device name; `*NONE` unlinks it. A device that isn't a Gateway is {{msg ELC1303}} |

The settings go through the machine's own rules: one it doesn't have is {{msg ELC1320}}, a value it refuses (or can't
take now) is {{msg ELC1321}}, and a multiblock that isn't formed is {{msg ELC1319}}. They're applied in the order of the
table, and the command stops at the first one refused, so the ones before it **are** changed.

On success it sends {{msg ELC1322}}, also when everything was `*SAME`.

```elcl
CHGMCHCFG MCH(ARCCRU01) RSMODE(*IGNORE) AUTOEJECT(*YES)
CHGMCHCFG MCH(ARCCRU01) GATEWAY(GATEWAY01) PWRNET(*YES)
CHGMCHCFG MCH(ELECTR01) SIDE(*BACK) SIDEMODE(input)
```
