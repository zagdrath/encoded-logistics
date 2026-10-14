Takes an item, fluid or gas out of the network's storage and puts it into the inventory a device faces, or into the
drawer of the Terminal Desk you're working at.

## Where it goes

- **`TODEV(name)`**: a cable part on the network, usually an {{item egress_port}} or {{item ingress_port}} (or an
  {{item inventory_tap}}), by its device name (`EGRESS01`). The items go into the block the part faces, through the side
  the part is on. A device that isn't a cable part is {{msg ELC1303}}; a part that's offline or disabled is
  {{msg ELC1302}}; one that faces no inventory is {{msg ELC1304}}. A qualified `SYSNAME/DEVICE` name reaches a
  published device on another network (see {{cmd RTVDIRENT}}).
- **`TODEV(*DESK)`**: the drawer of the {{item terminal_desk}} running the interactive job. In a batch job, or anywhere
  without a desk, `*DESK` is {{msg ELC1301}}.

## How much

`QTY(n)` moves up to *n*; `QTY(*ALL)` everything the network has, hot and cold. With `PARTIAL(*YES)` (the default) it
moves what it can (what there is, and what fits) and says how much in `RTNMOVED`, with no error for a shortfall. With
`PARTIAL(*NO)` it fails with {{msg ELC1202}} before moving anything when the network has fewer than you asked for, and
also afterwards when fewer fitted; in that second case the ones that did fit **stay moved**, and `RTNMOVED` says how
many.

The item ID forms (bare `COAL` or qualified `'minecraft:coal'`) are the ones {{cmd RTVITMCNT}} describes; an unknown ID,
or one the network holds none of, is {{msg ELC1201}}. The command sends no completion message (only {{msg ELC1203}}
when it recalls from tape).

## Items on tape

When there aren't enough hot items and more are on tape, the command asks the Tape Libraries to recall what's missing and
sends {{msg ELC1203}}. In a program, the job then waits (using none of its command budget) until enough of the item is
hot again or no recall of it is left, and then moves what's there. Typed at the `===>` prompt, it starts the recall and
moves what's hot now: run it again once the tape has been read. See {{page storage/tapes}}.

## Fluids and gases

With `TYPE(*FLUID)` or `TYPE(*PRES)` the ID is a fluid's or gas's full ID (`'minecraft:lava'`) and `QTY` is in mB (or the
gas's mod's unit). It goes into the faced block's tanks ({{msg ELC1208}} when the block has none for that type) or, with
`*DESK`, into empty containers in the drawer, such as buckets. Fluids and gases are never on tape. Naming one of the
other type is {{msg ELC1207}}.

```elcl
DCL VAR(&MOVED) TYPE(*INT)
MOVITM ITEM(COAL) QTY(256) TODEV(EGRESS01) RTNMOVED(&MOVED)
IF COND(&MOVED *LT 256) THEN(SNDPGMMSG MSG('Furnace feed short:' *BCAT %CHAR(&MOVED)))
MOVITM ITEM('minecraft:lava') QTY(4000) TODEV(EGRESS02) TYPE(*FLUID)
MOVITM ITEM(DIAMOND) QTY(9) TODEV(EGRESS03) PARTIAL(*NO)
MONMSG MSGID(ELC1202) EXEC(SNDPGMMSG MSG('Not 9 diamonds to spare'))
```

{{cmd IMPITM}} goes the other way.
