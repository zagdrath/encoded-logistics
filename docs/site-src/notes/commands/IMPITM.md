Pulls items, fluids or gases from the inventory a device faces into the network's storage: the opposite of
{{cmd MOVITM}}. `FROMDEV` is a cable part by its device name, usually an {{item ingress_port}} or
{{item inventory_tap}}; the command takes from the block the part faces, through the side the part is on. A device that
isn't a cable part is {{msg ELC1303}}, a part that's offline or disabled {{msg ELC1302}}, one facing no inventory
{{msg ELC1304}}.

- **`ITEM(*ALL)`** (the default) takes everything; an item ID (forms as in {{cmd RTVITMCNT}}) takes only that item, all
  its kinds.
- **`QTY`** caps the total moved; `*ALL` (the default) has no cap.
- **`TYPE(*FLUID)`** or **`TYPE(*PRES)`** takes from the block's tanks instead (amounts in mB, or the gas's mod's unit;
  {{msg ELC1208}} when the block has no tanks of that type). **`TYPE(*ALL)`** takes items, then fluids, then gases, and
  skips a type the block has no tanks for. Leave `ITEM` at `*ALL` with `TYPE(*ALL)`: a single ID would have to be an
  item, a fluid and a gas at once, and fails with {{msg ELC1201}} or {{msg ELC1207}}.

`RTNMOVED` gets the amount stored. Finding nothing to take isn't an error (`RTNMOVED` is `0`). When nothing could be
stored because there was no room for it, the command fails with {{msg ELC1204}}; when some went in, it succeeds with
the amount that fitted. It sends no completion message.

```elcl
DCL VAR(&IN) TYPE(*INT)
IMPITM FROMDEV(INGRESS01) RTNMOVED(&IN)
SNDPGMMSG MSG('Imported' *BCAT %CHAR(&IN) *BCAT 'items')
IMPITM FROMDEV(TAP02) ITEM(COBBLESTONE) QTY(640)
IMPITM FROMDEV(INGRESS02) TYPE(*FLUID)
MONMSG MSGID(ELC1204) EXEC(SNDMSG MSG('Network storage is full') TOUSR(*SYSOPR))
```
