`MONMSG` catches escape messages. When a command fails it raises an escape message (an ID such as `ELC1201`, with its
message data); a `MONMSG` that covers that command and names that ID takes it, and the program goes on instead of
ending. The general picture is on [the language page](page:elcl/language#errors).

## Where it goes

| Placed | Covers |
|---|---|
| Straight after the `DCL`s, before the first command (program level) | every command in the program, subroutines included |
| Straight after a command (command level) | that command only. Several `MONMSG`s may follow one command, also after another one's `EXEC(DO)` ... `ENDDO`. |

After an `IF` with a single-command `THEN` (or its `ELSE`), a command-level `MONMSG` covers what that `IF` runs. After
the `ENDDO`, `ENDFOR` or `ENDSELECT` of a block there is no command to monitor, so a `MONMSG` there is {{msg ELC0009}}
("MONMSG without matching command"), as is one in any other place. To monitor commands in a group, put a `MONMSG` after
each one inside the group, or use a program-level one. In a {{cmd SELECT}}, put the command in a `THEN(DO)` group with
its `MONMSG` inside the group, not straight after the `WHEN` line.

When a message escapes, the command-level monitors around the failing command are tried first, innermost first, then the
program-level ones, each in the order written; the first that takes the message wins.

## Which messages

`MSGID` takes up to 50 IDs (three letters and four digits). An ID ending in `00` takes its whole range of a hundred:
`ELC1200` takes `ELC1201` to `ELC1299`. `ELC0000` takes every message, your own `USR` ones included; another prefix
ending in `0000` takes all of that prefix (`USR0000` takes every `USR` message).

`CMPDTA('text')` narrows it down: the monitor takes the message only if the message data (the values in the message
text, joined by blanks, as {{cmd RCVMSG}} `RTNMSGDTA` returns them) start with that text, matched exactly, case
included. For instance `MONMSG MSGID(ELC1302) CMPDTA('UPS01')` takes "Device UPS01 is offline" but not another device's.
For a {{cmd SNDPGMMSG}} message the data is its text. Give `CMPDTA` as a quoted literal: an unquoted value or a variable
is accepted but compares nothing, so the monitor takes every message with that ID.

## What happens next

- Without `EXEC`, the program carries on with the statement after the one that failed.
- With `EXEC`, it runs that command first (or the `EXEC(DO)` group), then carries on after the failed statement. Usual
  `EXEC`s are `GOTO` to error handling, `RETURN`, `LEAVE` or `ITERATE` in a loop, or a `DO` group that reports the
  problem with {{cmd RCVMSG}} and {{cmd SNDMSG}}.
- A monitored message isn't written to the job log; one nothing takes ends the program (see {{cmd SNDPGMMSG}} and
  {{cmd CALL}} for where it goes).
- A command that failed may have left its `RTN*` variables as they were before it ran; don't rely on them after a
  caught failure.

## Examples

```elcl
DCL VAR(&N)  TYPE(*INT)
DCL VAR(&ID) TYPE(*CHAR) LEN(7)
MONMSG MSGID(ELC1300) EXEC(GOTO CMDLBL(DEVICE))    /* any device error, anywhere */

RTVITMCNT ITEM('minecraft:gold_ingot') RTNCOUNT(&N)
MONMSG MSGID(ELC1201) EXEC(CHGVAR VAR(&N) VALUE(0))
IF COND(&N *GT 64) THEN(MOVITM ITEM('minecraft:gold_ingot') QTY(64) TODEV(EGRESS01))
RETURN

DEVICE:
  RCVMSG RTNMSGID(&ID)
  SNDMSG MSG('Device problem:' *BCAT &ID) TOUSR(*SYSOPR)
```

One command, several monitors:

```elcl
DCL VAR(&JOB) TYPE(*CHAR) LEN(10)
STRCRAFT ITEM('encodedlogistics:logic_die') QTY(8) RTNCRFJOB(&JOB)
MONMSG MSGID(ELC1401) EXEC(DO)
  SNDMSG MSG('No Scheduler for the Logic Dies') TOUSR(*SYSOPR)
  RETURN
ENDDO
MONMSG MSGID(ELC1400 ELC1200) EXEC(RETURN)
```
