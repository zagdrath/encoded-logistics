`ADDJOBSCDE` adds a job schedule entry: a command the system submits as a batch job on its own, once, every game day,
or every so many real seconds. Each run is exactly what {{cmd SBMJOB}} `CMD(...) JOB(name)` would do: a batch job
named after the entry, run as you with your Firewall permissions (whether or not you're online), on any job host
(`HOST(*ANY)`), not logging its commands.

## When it runs

| `FRQ` | Runs | `TIME` | `INTERVAL` |
|---|---|---|---|
| `*ONCE` (default) | Once, at `TIME`; then the entry is removed. | Used | Ignored |
| `*DAILY` | At `TIME` every game day. | Used | Ignored |
| `*INTERVAL` | Every `INTERVAL` real seconds, the first time that long after you add it. | Ignored | Required ({{msg ELC0102}} without it) |

- `TIME` is a time of day on the **game clock**, `hhmm` or `hhmmss`, the next time it comes round (a time that's now
  or already past today means tomorrow). The game day starts at 06:00 and lasts 20 real minutes, so a game hour is 50
  real seconds. Hours over 23, or minutes or seconds over 59, are {{msg ELC0004}}. `TIME(*CURRENT)`, the default, is
  now: a `*ONCE` entry runs on the next tick, a `*DAILY` one now and at this time every day after. A time from a
  variable that isn't four or six digits counts as `*CURRENT`.
- `INTERVAL` is in real seconds, 1 to 604,800 (a week), and doesn't follow the game clock.
- Entries run only while the network is loaded. One that came due while it wasn't runs once when it loads again,
  however many runs it missed, and then keeps to its timetable.

`CMD` is one command, checked as `SBMJOB`'s is: a program-only statement is {{msg ELC0103}}, a syntax error is reported
as that error. If a run can't be submitted (most often: no job host, {{msg ELC0301}}), the message goes to your message
queue ({{cmd DSPMSG}}) as `Schedule entry NAME: ...`; the entry still counts that run.

## Messages

{{msg ELC0312}} when it's added; {{msg ELC0305}} when an entry of that name exists. Entries are saved with the system.
To change one, remove it and add it again: Work with Job Schedule Entries option 2 does both for you
({{cmd WRKJOBSCDE}}). {{cmd HLDJOBSCDE}} and {{cmd RLSJOBSCDE}} pause and resume it, {{cmd RMVJOBSCDE}} removes it.

```elcl
ADDJOBSCDE JOB(NIGHTLY) CMD(CALL PGM(ELSYS/ARCHIVE)) FRQ(*DAILY) TIME(2200)
ADDJOBSCDE JOB(WALLCHK) CMD(CALL PGM(ELSYS/NOCWALL)) FRQ(*INTERVAL) +
           INTERVAL(600)
ADDJOBSCDE JOB(MORNING) CMD(SNDMSG MSG('Good morning') TOUSR(*ALL)) +
           TIME(0600)
```

See {{page elcl/jobs-and-scheduling}}.
