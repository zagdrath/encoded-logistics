`ADDTRGEVT` adds a trigger: whenever its event happens on the network, the system runs your program as a batch job.
Each firing is a job named after the trigger, run as you with your Firewall permissions (whether or not you're
online), on any job host. For a trigger `LOWIRON` on `*ITMBELOW` that calls `ZAGLIB/ALERT`, it's as if you had typed

```text
SBMJOB CMD(CALL PGM(ZAGLIB/ALERT) PARM('*ITMBELOW' 'IRON_INGOT 499')) JOB(LOWIRON)
```

So the program takes two parameters: the event's name (`&EVENT`) and a line of data about it (`&DATA`).

```elcl name=TRGPGM
/* ALERT - tell the operator what happened */
PGM PARM(&EVENT &DATA)
  DCL VAR(&EVENT) TYPE(*CHAR) LEN(12)
  DCL VAR(&DATA)  TYPE(*CHAR) LEN(256)
  SNDMSG MSG(&EVENT *BCAT &DATA) TOUSR(*SYSOPR)
ENDPGM
```

> [!TIP] Declare `&EVENT` with `LEN(12)`
> The event name arrives whole and is cut only by your own declaration. The longest, `*SUPFAILOVER`, is 12
> characters: with `LEN(10)` it arrives as `*SUPFAILOV` and `*PROTSWITCH` as `*PROTSWITC`. (The `ELSYS/UPSALERT`
> sample declares `LEN(10)`, which is enough for the power events it handles.) Give `&DATA` room too: 256 is plenty.

## The events

{{table triggers}}

## What the other parameters do

- `ITEM`: the item for `*ITMBELOW` and `*ITMABOVE` (give one: with `*ANY` they never fire), and the item a crafting
  job or machine must make for `*CRAFTEND`, `*CRFSTALL` and `*MCHDONE` (`*ANY`: any). An item ID that doesn't exist
  isn't refused here; the trigger just never fires.
- `TYPE(*FLUID)` or `TYPE(*PRES)` makes `ITEM` a fluid or a gas for `*ITMBELOW` and `*ITMABOVE`, counted in mB. It's
  checked when you add the trigger ({{msg ELC1201}} if there's no such fluid or gas); a bare name is taken as
  `minecraft:`.
- `DEV`: the device the event must come from, by name (`*ANY`: any). Used by the device, redstone, Display Panel,
  machine, Router, chassis and optical events.
- `VALUE`: the count for `*ITMBELOW` and `*ITMABOVE` (with `*NONE` it's 0, so `*ITMBELOW` never fires); the percent
  for `*STGFULL` (100 with `*NONE`); a side for `*RSCHANGE` (`*NORTH` and so on; `*NONE`: any side).

Parameters an event doesn't use are kept and ignored.

## When a trigger fires

- **Once per crossing.** The count, storage and power events are looked at every half second and fire when their
  condition starts to hold, not again until it has stopped holding and holds once more. What already holds when the
  trigger is added doesn't count as a crossing: an `*ITMBELOW` added while you're already short fires the next time
  the count drops below `VALUE` after rising to it.
- **At most once a second** for one trigger. An event in that second fires when it's over, once, with the last
  `&DATA`; a crossing in it fires then if it still holds.
- **Only while the network is loaded**, and not while the trigger is held ({{cmd HLDTRGEVT}}): events while it's held
  are dropped. Device statuses aren't saved, so the first look after a restart only records them: a device that
  changed while the server was down doesn't fire anything.
- Each firing is a batch job like any other: it needs a job host and waits its turn under `QMAXJOB`. If it can't be
  submitted (no job host, {{msg ELC0301}}), the message goes to your message queue as `Trigger NAME: ...`.

Done: {{msg ELC0314}}; a trigger of that name already there is {{msg ELC0306}}. Triggers are saved with the system.
To change one, remove it and add it again: Work with Trigger Events option 2 does both ({{cmd WRKTRGEVT}}).

## Examples

```elcl
ADDTRGEVT TRG(LOWIRON) EVENT(*ITMBELOW) PGM(ZAGLIB/ALERT) +
          ITEM(IRON_INGOT) VALUE(500)
ADDTRGEVT TRG(LOWWATER) EVENT(*ITMBELOW) PGM(ZAGLIB/ALERT) +
          ITEM(WATER) TYPE(*FLUID) VALUE(8000)
ADDTRGEVT TRG(SRVDOWN) EVENT(*DEVOFFLINE) PGM(ZAGLIB/ALERT) DEV(CMPSRV01)
ADDTRGEVT TRG(UPSALERT) EVENT(*PWRUPS) PGM(ELSYS/UPSALERT)
```

See [triggers](page:elcl/jobs-and-scheduling) on the jobs page.
