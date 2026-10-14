Work with Active Jobs lists the system's script jobs: the interactive job of each terminal session (type `INT`, its
host the terminal) and every batch job that hasn't ended (type `BCH`, its host the job host running it, or the host it
asked for while it waits). It's also option 6 on the main menu.

The **Status** column shows `*ACTIVE` (running), `*WAIT` (waiting, in {{cmd DLYJOB}} for instance), `*JOBQ` (on the
job queue, not started) or `*HELD`. **Budget** is how much of its own per-tick budget the job used on the last tick.
Row 2 adds up the budget all jobs used, the time since the screen opened, the active jobs and the job hosts busy. The
screen doesn't refresh by itself: press F5 (which also starts the elapsed time again). F11 sorts by job, user or
status.

| Option | What it does |
|---|---|
| `2` | Change its priority and command logging in a window ({{cmd CHGJOB}}). |
| `3` | Hold it ({{cmd HLDJOB}}). |
| `4` | End it, after you confirm ({{cmd ENDJOB}} `*CNTRLD`; F11 in the confirmation ends it `*IMMED`). |
| `5` | Work with it: status, definition, spooled files, log, call stack ({{cmd WRKJOB}}). |
| `6` | Release a held job ({{cmd RLSJOB}}). |
| `8` | Its spooled files ({{cmd WRKSPLF}}). |

Ended jobs leave the list; their logs stay, and {{cmd DSPJOBLOG}} `JOB()` shows them.

{{layout 08_wrkactjob}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKACTJOB
```

See {{page elcl/jobs-and-scheduling}}.
