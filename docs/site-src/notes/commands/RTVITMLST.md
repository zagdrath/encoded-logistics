Lists the IDs of what the network stores (only things it has some of) into a `*LIST` variable. It returns IDs only, no
amounts: get those with {{cmd RTVITMCNT}} for each ID, or read IDs and counts together from the system file
`ELSYS/INVITEMS` with {{cmd DCLF}} and {{cmd RCVF}} (see {{page elcl/database-files}}).

- **`FILTER`**: `*ALL`, a pattern with `*` wildcards, or a tag. A pattern is matched, ignoring case, against the full
  ID, the ID without its mod, and the display name, and it has to match the whole of one of them: `'*ingot'`,
  `'minecraft:*'`, `'*copper*'`. Without a `*` it matches only an exact name. `'#c:ingots'` lists what's in a tag.
- **`TYPE`**: `*ITEM` (default), `*FLUID`, `*PRES` or `*ALL`. Items come back as {{cmd RTVITMCNT}} takes them
  (`IRON_INGOT`, `encodedlogistics:logic_die`), fluids and gases by their full ID (`minecraft:water`).
- **`TIER`**: counts hot, cold or both when deciding what's there and how much. Fluids and gases are always hot, so
  `TIER(*COLD)` lists items only.
- **`SORT`**: `*NAME` sorts by the ID text, so vanilla items (upper case) come before other mods' IDs; `*QTY` puts the
  largest amount first (each item's kinds added together).
- **`MAX`**: keeps the first *n* after sorting. `SORT(*QTY) MAX(10)` is the ten most plentiful.

An empty network gives an empty list, not an error. A list longer than a `*LIST` variable may hold
({{config elcl.maxListSize default}}) isn't returned at all: `RTNLST` keeps the value it had, so use `FILTER` or `MAX`
on a big network. The command sends no completion message.

```elcl
DCL VAR(&ITEMS) TYPE(*LIST)
DCL VAR(&ITEM) TYPE(*CHAR) LEN(64)
DCL VAR(&COUNT) TYPE(*INT)
RTVITMLST FILTER('*ingot') SORT(*QTY) MAX(10) RTNLST(&ITEMS)
FOREACH VAR(&ITEM) IN(&ITEMS)
  RTVITMCNT ITEM(&ITEM) RTNCOUNT(&COUNT)
  SNDPGMMSG MSG(&ITEM *BCAT %CHAR(&COUNT))
ENDFOR
```
