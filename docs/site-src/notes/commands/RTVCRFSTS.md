Tells you where a crafting job has got to. `CRFJOB` takes the ID {{cmd STRCRAFT}} returned (`C0042`) or just its number
(`42`).

| Variable | A job still on a Scheduler | A job that has ended |
|---|---|---|
| `RTNSTS` | `*ACTIVE` (running) or `*QUEUED` | `*DONE`, `*FAILED` or `*CANCELLED` |
| `RTNPCT` | How much of the job is done, as a percentage | `100` when done; otherwise what it made as a percentage of what was asked |

An ended job is found in the network's crafting history, which keeps the last `CRFLOGRTN` jobs (a system value; its
default is {{config elcl.craftLogRetention default}}). A job that has aged out of it, or a number no job has had, is
{{msg ELC1404}}; so is an ID that isn't a number. The command sends no completion message.

```elcl
DCL VAR(&STS) TYPE(*CHAR) LEN(10)
DCL VAR(&PCT) TYPE(*DEC) LEN(5 1)
RTVCRFSTS CRFJOB(C0042) RTNSTS(&STS) RTNPCT(&PCT)
MONMSG MSGID(ELC1404) EXEC(CHGVAR VAR(&STS) VALUE('*GONE'))
SNDPGMMSG MSG('C0042:' *BCAT &STS *BCAT %CHAR(&PCT) *TCAT '%')
```
