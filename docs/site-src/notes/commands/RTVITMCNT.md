Counts one item, fluid or gas in the network's storage and puts the total in `RTNCOUNT`. Every stored kind of an item is
counted together (the same item with different components, such as damage or enchantments, is stored as separate
kinds), and so are both tiers unless you say otherwise: `TIER(*HOT)` counts only what's on the drives, `TIER(*COLD)`
only what the Tape Libraries hold on tape. See {{page storage/hot-and-cold}}.

## Naming the item

`ITEM` takes an item ID in either form:

- **Qualified**: `'minecraft:iron_ingot'`, `'encodedlogistics:logic_die'`. Exactly that item (quote it: a name can't
  hold the colon).
- **Bare**: `IRON_INGOT` (case doesn't matter). It's looked up as a `minecraft:` item first, then as an
  `encodedlogistics:` one, then in every other mod. If two other mods both have an item by that name, the command fails
  with {{msg ELC1205}}: use the qualified ID. A name no item has is {{msg ELC1201}}.

Commands that return IDs ({{cmd RTVITMLST}}, {{cmd RTVSCHLST}}, {{cmd RTVCRFSTP}}) give vanilla items as their bare name
in upper case (`IRON_INGOT`) and other mods' items in full (`encodedlogistics:logic_die`), so a returned ID always works
here as it is.

## Resource types

`TYPE` picks what the ID names:

| `TYPE` | Counts | Unit |
|---|---|---|
| `*ITEM` (default) | items | one each |
| `*FLUID` | fluids, by full registry ID (`'minecraft:water'`; a bare ID is taken as `minecraft:`) | mB |
| `*PRES` | pressurized gases and chemicals from another mod (`'arcforge:hydrogen'`) | that mod's unit (Arcforge's is mB) |
| `*ALL` | whichever of the three the ID names, added together | as above |

Naming a gas with `TYPE(*FLUID)` (or a fluid with `TYPE(*PRES)`) is {{msg ELC1207}}. Fluids and gases never go to tape,
so `TIER(*COLD)` counts none of them. Energy isn't counted here: use {{cmd RTVPWRSTS}}.

## When there's none

With `NOTFND(*ERROR)` (the default) the command fails with {{msg ELC1201}} when the ID names nothing, and also when the
network holds none of it at all, hot or cold. That second check always looks at both tiers: an item that's only on tape
gives `0` with `TIER(*HOT)`, not an error. With `NOTFND(*ZERO)` both cases set `RTNCOUNT` to `0` instead, which is
usually what a stock-keeping program wants. {{msg ELC1205}} and {{msg ELC1207}} still fail the command with `*ZERO`.

The command sends no completion message. Typed at the `===>` prompt, `RTNCOUNT` can be left out: the count is listed
under the command (`RTNCOUNT = 1234`).

```elcl
DCL VAR(&IRON) TYPE(*INT)
DCL VAR(&WATER) TYPE(*INT)
RTVITMCNT ITEM(IRON_INGOT) RTNCOUNT(&IRON) NOTFND(*ZERO)
RTVITMCNT ITEM('minecraft:water') TYPE(*FLUID) RTNCOUNT(&WATER) NOTFND(*ZERO)
IF COND(&IRON *LT 64) THEN(SNDPGMMSG MSG('Iron is low'))
IF COND(&WATER *LT 8000) THEN(SNDPGMMSG MSG('Under 8 buckets of water'))
```
