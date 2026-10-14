Cancels a crafting job that's still on a Scheduler, as Work with Crafting Jobs' 4=Cancel does. Everything the job holds
(the ingredients it took and what it has made so far) goes back into the network's storage, and the job goes into the
crafting history as `*CANCELLED`.

`CRFJOB` takes the job's ID (`C0042`) or its number (`42`). A job that has already ended, a number no job has, or an ID
that isn't a number is {{msg ELC1404}}. The command sends no completion message: check with {{cmd RTVCRFSTS}} if you
need to.

```elcl
DCL VAR(&JOB) TYPE(*CHAR) LEN(10)
DCL VAR(&STALL) TYPE(*CHAR) LEN(12)
DCL VAR(&STEPS) TYPE(*LIST)
STRCRAFT ITEM(PISTON) QTY(64) RTNCRFJOB(&JOB)
DLYJOB DLY(600)
RTVCRFSTP JOB(&JOB) RTNLIST(&STEPS) RTNSTALL(&STALL)
MONMSG MSGID(ELC2502) EXEC(RETURN)
IF COND(&STALL *EQ '*NOMACHINE') THEN(ENDCRAFT CRFJOB(&JOB))
```
