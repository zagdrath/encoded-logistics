`DLYTICK` puts the program to sleep for a number of game ticks, then carries on with the next statement. A waiting
program uses none of its instruction budget. It is the fine-grained partner of {{cmd DLYJOB}}, which counts in seconds:
20 ticks make a second when the server keeps up, so `DLYTICK TICKS(10)` is half a second and the longest wait,
`TICKS(1200)`, is a minute.

- `TICKS` is 1 to 1,200. A literal outside that is {{msg ELC0004}} when you compile; an expression that works out
  outside it is {{msg ELC0004}} when it runs.
- In a job the job's status shows `*WAIT` meanwhile.
- On a [PLC](page:elcl/control-and-plcs) it pauses the scan where it is. The outputs keep the levels they were last set
  to until the program changes them, so `DLYTICK` is how a PLC program times a pulse or a sequence. It works on a PLC
  with no network too.
- Typed on a command line it does nothing and completes at once.

It sends no message.

```elcl target=PLC_LOCAL
DCL VAR(&LVL) TYPE(*INT)
RTVRSIN DEV(*SELF) SIDE(*NORTH) RTNLVL(&LVL)
IF COND(&LVL *GT 0) THEN(DO)
  CHGRSOUT DEV(*SELF) SIDE(*SOUTH) LVL(15)
  DLYTICK TICKS(10)
  CHGRSOUT DEV(*SELF) SIDE(*SOUTH) LVL(0)
  DLYTICK TICKS(30)
ENDDO
```
