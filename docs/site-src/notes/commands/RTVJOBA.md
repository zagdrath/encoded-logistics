`RTVJOBA` tells a program about the job it is running in: who it runs as, the job's name, whether it is interactive or
batch, and where it runs. All four return parameters are optional; give the ones you need, each a `*CHAR` variable. A
value longer than its variable is cut: job names fit in 10 characters, but a player's name can have up to 16, so
declare `RTNUSR`'s variable `LEN(16)`.

| Parameter | Receives |
|---|---|
| `RTNUSR` | the user the job runs as, in upper case: the user signed on at the terminal, or the user who submitted the batch job (for a schedule entry or a trigger, the user who added it) |
| `RTNJOB` | the job's name: `QINTER` for an interactive job, the `JOB()` name a batch job was submitted with |
| `RTNTYPE` | `*INTER` in an interactive job (a program called from a terminal), `*BATCH` in a batch job |
| `RTNHOST` | where the job runs: the Terminal Desk's device name for an interactive job, the job host's name for a batch job |

Use it to make one program behave sensibly both ways, for instance to report to the screen when someone is watching
and to a message queue when nobody is.

Typed on a command line there are no variables: leave the parameters out and Command Entry shows the values for your
own session. It sends no message.

A PLC isn't a job, so `RTVJOBA` has nothing to report there: a PLC program shouldn't use it (on a PLC without a network
it is {{msg ELC1502}}).

```elcl
DCL VAR(&USER) TYPE(*CHAR) LEN(16)
DCL VAR(&JOB)  TYPE(*CHAR) LEN(10)
DCL VAR(&TYPE) TYPE(*CHAR) LEN(10)
DCL VAR(&HOST) TYPE(*CHAR) LEN(10)
RTVJOBA RTNUSR(&USER) RTNJOB(&JOB) RTNTYPE(&TYPE) RTNHOST(&HOST)
IF COND(&TYPE *EQ '*BATCH') THEN(SNDMSG MSG('Job' *BCAT &JOB *BCAT 'on' *BCAT &HOST *BCAT 'done') TOUSR(&USER))
ELSE CMD(SNDPGMMSG MSG('Done'))
```
