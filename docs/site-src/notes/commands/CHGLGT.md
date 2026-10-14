`CHGLGT` sets the network's half of a Cage Light's state, and its light level. A Cage Light has two inputs: the
redstone at its block and its network state. Either one turns it on, so `CHGLGT ... STATUS(*OFF)` doesn't put out a
light that redstone is holding on. How the light combines them is its **Control** setting, on its settings screen:

| Control | Lit when |
|---|---|
| Redstone (the default) | It has a redstone signal, or its network state is on |
| Inverted | It has no redstone signal, or its network state is on |
| Always on | Always: `STATUS` has no visible effect (the network state is still kept) |
| Network only | Its network state is on |

- `STATUS(*ON)` and `*OFF` set the network state; `*TOGGLE` flips it, light by light, so lights that started out
  different stay different. `*SAME` (the default) leaves it.
- `LVL` sets the light level it gives while lit, 1-15. It is kept whatever the light's state and Control. A level
  outside 1-15 stops the command with {{msg ELC2401}} before any light changes. `*SAME` leaves it.
- `DEV` takes up to 32 names, or `*ALL` for every online Cage Light on the network (none found: nothing happens).
  Every named light is checked before any is changed: not there ({{msg ELC1301}}), offline ({{msg ELC1302}}) or not a
  Cage Light ({{msg ELC1303}}).

Unlike the Alarm Strobe and Speaker commands, `CHGLGT` works whatever the light's Control setting, since a Cage Light
has no trigger to refuse network commands. It sends no completion message.

```elcl
CHGLGT DEV(LGT01 LGT02) STATUS(*ON) LVL(15)
CHGLGT DEV(*ALL) STATUS(*TOGGLE)
CHGLGT LGT03 LVL(4)            /* dim it; on or off as it was */
```

See [Cage Lights](page:control/lights-and-sirens#cage-lights) for the light itself, and {{cmd STRSRN}} for the
Alarm Strobes.
