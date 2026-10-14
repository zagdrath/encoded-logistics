Reports how full the network's storage is, in bytes: the measure the drives' sizes are given in. A byte of a Storage
Drive holds 8 items; a byte of a Fluid or Pressurized Storage Drive holds 1,000 mB.

| Variable | Gets |
|---|---|
| `RTNUSED` | Bytes in use |
| `RTNTOTAL` | Bytes in all |
| `RTNPCT` | `RTNUSED` as a percentage of `RTNTOTAL`; `0` when there's no storage of that kind |

What's counted depends on `TYPE` and `TIER`:

- **Hot** (`TIER(*HOT)` or `*ALL`): the network's drives of the type you ask for (`*ITEM`, the default, is Storage
  Drives; `*FLUID` and `*PRES` the fluid and pressurized drives; `*ALL` adds the three). For items, the bytes the
  network's ELCL libraries take (source members and the like) count as used too, but not towards the total, so a
  network short of drives can show more than 100%.
- **Cold** (`TIER(*COLD)` or `*ALL`): the tapes in the network's online Tape Libraries. Only items go to tape, so cold
  storage is counted only when `TYPE` is `*ITEM` or `*ALL`.

Energy Storage Drives aren't counted: {{cmd RTVPWRSTS}} reports energy. The command sends no completion message.
{{page storage/drives}} explains the drive sizes.

```elcl
DCL VAR(&PCT) TYPE(*DEC) LEN(7 2)
DCL VAR(&FLUIDPCT) TYPE(*DEC) LEN(7 2)
RTVSTGSTS TIER(*HOT) RTNPCT(&PCT)
RTVSTGSTS TYPE(*FLUID) RTNPCT(&FLUIDPCT)
IF COND(&PCT *GT 90) THEN(SNDMSG MSG('Item drives over 90% full') TOUSR(*SYSOPR))
```
