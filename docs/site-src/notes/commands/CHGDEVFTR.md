Changes the item filter of an Ingress or Egress Port (wireless ones too), an Inventory Tap or a Collector or Deployer
Plane: the nine entries you'd otherwise set in the part's screen. Any other device is {{msg ELC1303}}.

| `ACTION` | What it does |
|---|---|
| `*ADD` | Puts the item in the first empty entry. An item already there is left as it is. With no empty entry: {{msg ELC1305}}. |
| `*RMV` | Empties every entry holding the item. An item that isn't there changes nothing. |
| `*CLR` | Empties all nine entries. `ITEM` isn't needed (and is ignored). |

`*ADD` and `*RMV` need `ITEM`: leaving it out is {{msg ELC0102}}, from the compiler in a program. `ITEM` takes the item ID
forms {{cmd RTVITMCNT}} describes (an ambiguous bare name is {{msg ELC1205}}); the command handles items only.

Only the entries change. Whether the list allows or denies, tag and component matching (with a Filter Module) and fuzzy
matching (with a Fuzzy Match Module) stay as they're set in the part's screen. See {{page networks/cable-parts}}.

The command sends no completion message.

```elcl
CHGDEVFTR DEV(EGRESS01) ACTION(*CLR)
CHGDEVFTR DEV(EGRESS01) ACTION(*ADD) ITEM(COAL)
CHGDEVFTR DEV(EGRESS01) ACTION(*ADD) ITEM(CHARCOAL)
CHGDEVFTR DEV(TAP01) ACTION(*RMV) ITEM('minecraft:cobblestone')
```
