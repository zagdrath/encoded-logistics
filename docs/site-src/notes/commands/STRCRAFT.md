Starts a crafting job, just as the terminals' Craft does: it plans the craft from the network's schematics and stock,
hands the job to a Scheduler and returns the job's ID (`C0042`) in `RTNCRFJOB`. Crafting jobs are separate from ELCL
jobs: watch them with {{cmd RTVCRFSTS}} and {{cmd RTVCRFSTP}}, stop one with {{cmd ENDCRAFT}}, or use Work with Crafting
Jobs. See {{page crafting/jobs}}.

## What it checks

1. The item has to be something the network's schematics make, or the command fails with {{msg ELC1402}}. `ITEM` takes
   the ID forms {{cmd RTVITMCNT}} describes. With `TYPE(*FLUID)` or `TYPE(*PRES)` it's a fluid's or gas's full ID
   (`'minecraft:lava'`), and `QTY` is in mB ({{msg ELC1207}} for an ID of the other type).
2. The network needs a Scheduler ({{msg ELC1401}} without one).
3. The plan has to be complete. With `MISSING(*FAIL)` (the default) any missing ingredient is {{msg ELC1403}}. With
   `MISSING(*PARTIAL)` it plans the largest amount, up to `QTY`, that the stock can make, and fails with
   {{msg ELC1403}} only when it can't make even one. Ingredients on tape count: the job recalls them.
4. `SCHEDULER(*ANY)` picks the first Scheduler with enough free memory and a free thread, or failing that the first with
   enough memory. `SCHEDULER(2)` asks for the second in the network's list, and a name picks the first Scheduler whose
   name starts with it. A Scheduler that doesn't match, or hasn't the memory for the job, is {{msg ELC1401}}.

The job records who asked: your user profile and, in a batch job, the ELCL job that ran the command. The command sends no
completion message; `RTNCRFJOB` is how you know it started.

## Waiting for it

With `WAIT(*YES)`, a program waits (using none of its command budget) until the job has left every Scheduler, whether it
finished, failed or was cancelled, and then goes on to the next command. It doesn't tell you which: ask
{{cmd RTVCRFSTS}} afterwards. Typed at the `===>` prompt, `WAIT` has no effect and the command returns as soon as the job
has started.

```elcl
DCL VAR(&JOB) TYPE(*CHAR) LEN(10)
DCL VAR(&STS) TYPE(*CHAR) LEN(10)
STRCRAFT ITEM('encodedlogistics:logic_die') QTY(32) MISSING(*PARTIAL) +
         WAIT(*YES) RTNCRFJOB(&JOB)
MONMSG MSGID(ELC1400) EXEC(DO)
  SNDPGMMSG MSG('Could not start the craft')
  RETURN
ENDDO
RTVCRFSTS CRFJOB(&JOB) RTNSTS(&STS)
SNDPGMMSG MSG('Job' *BCAT &JOB *BCAT 'ended' *BCAT &STS)
```
