`WRKINV` opens **Work with Inventory**: the network's items, fluids and gases, eleven to a page, with their type
(`ITEM`, `FLUID`, `PRES`), quantity (fluids and gases in buckets and millibuckets) and location (Hot on the drives,
Cold on tape, or Hot+Cold). After what's stored come the things the network can craft but has none of (quantity 0,
location Craftable, dim). It's Main Menu option 1. You type it at a command line; it doesn't run in a program.

**`FILTER` is accepted and has no effect:** the screen always opens on the whole list. To narrow it, type the start of
a name in the screen's own **Position to** field (blank or `*ALL` for everything) and choose a **Type** (`*ALL`,
`*ITEM`, `*FLUID`, `*PRES`), then press Enter.

| Option | What it does |
|---|---|
| 1=Withdraw | The Withdraw Item screen: how many, and where to (the Terminal Desk's drawer or your inventory). A fluid or gas goes into containers there. Needs the Firewall's extract permission. |
| 5=Display | The item's details: ID and name, how many hot and cold, how long a recall from tape would take, whether it can be crafted, its tags. |
| 7=Craft | The Craft Item screen: quantity and scheduler, to start a crafting job. Needs the craft permission. |

You can type options next to several items; they're done one after another. **F6** opens Deposit Items, to put stacks
from your inventory into the network (insert permission). **F11** sorts by name, quantity or mod.

In a program, {{cmd RTVITMCNT}}, {{cmd RTVITMLST}}, {{cmd MOVITM}} and {{cmd STRCRAFT}} do the same jobs, and
`ELSYS/INVITEMS` holds the list as records. See {{page elcl/screens}}.

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKINV
```
