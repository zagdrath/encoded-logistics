In a program, `DLYJOB` puts the job to sleep and moves on to the next command when the time is up. A waiting job uses
none of its command budget, so a long-running batch job that wakes up every few minutes costs almost nothing in between.
On the command line it does nothing and completes at once.

Give one of the two parameters (with neither, the compiler reports {{msg ELC0102}} for `DLY`):

- `DLY(n)` waits *n* game seconds: 20 game ticks each, so `DLY(60)` is one real minute when the server keeps up.
- `RSMTIME(hhmm)` or `RSMTIME(hhmmss)` waits until that time of day on the game clock, the next time it comes round.
  The game day starts at 06:00 (tick 0), and a whole game day (24,000 ticks) is 20 real minutes, so a game hour is
  50 real seconds. If the clock already shows that time, the job waits a full day. A value that isn't four or six
  digits stops the program with {{msg ELC0103}}.

If you give both, `DLY` wins and `RSMTIME` is ignored.

```elcl
DCL VAR(&COUNT) TYPE(*INT)
DOWHILE COND(&COUNT *LT 10)
  CHGVAR VAR(&COUNT) VALUE(&COUNT + 1)
  SNDPGMMSG MSG('Still running')
  DLYJOB DLY(300)
ENDDO
DLYJOB RSMTIME(0600)
SNDPGMMSG MSG('Good morning')
```

For waits shorter than a second, or counted in ticks (a PLC's timing), use {{cmd DLYTICK}}.
