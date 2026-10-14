Returns the steps of a crafting job that's still running or queued, as Work with Crafting Jobs' 8=Display steps shows
them. `JOB` takes the job's ID (`C0042`) or its number.

`RTNLIST` gets one element per step: the item it makes, how many of its runs are done out of how many, and its state:

```text
IRON_INGOT 12/32 *ACTIVE
encodedlogistics:logic_die 0/8 *WAITING
```

A step's state is `*DONE`, `*ACTIVE`, `*STALLED`, `*MISSING` or `*WAITING`.

`RTNSTALL` gets why the job is stalled, or blanks when it isn't:

| Value | Reason |
|---|---|
| `*TAPE` | A tape recall of its ingredients is pending |
| `*NOMACHINE` | A machine it needs is missing |
| `*NOPOWER` | A machine it needs is unpowered |
| `*OUTPUTFULL` | A machine's output is full |
| `*BLOCKED` | A Gateway is blocked |
| `*INGREDIENTS` | Ingredients are missing |
| `*UNKNOWN` | Stalled, no reason found |

A job that has ended (it's in the history, not on a Scheduler), or one that doesn't exist, is {{msg ELC2502}}: use
{{cmd RTVCRFSTS}} for ended jobs. A job that hasn't been planned into steps yet returns an empty list with the warning
{{msg ELC2503}}. The command sends no completion message.

```elcl
DCL VAR(&STEPS) TYPE(*LIST)
DCL VAR(&STEP) TYPE(*CHAR) LEN(80)
DCL VAR(&WHY) TYPE(*CHAR) LEN(12)
RTVCRFSTP JOB(C0042) RTNLIST(&STEPS) RTNSTALL(&WHY)
IF COND(&WHY *NE ' ') THEN(SNDMSG MSG('C0042 stalled:' *BCAT &WHY) TOUSR(*SYSOPR))
FOREACH VAR(&STEP) IN(&STEPS)
  SNDPGMMSG MSG(&STEP)
ENDFOR
```
