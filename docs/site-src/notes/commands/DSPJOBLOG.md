Display Job Log shows a job's log, newest at the bottom (it opens there). `JOB(*)`, the default, is your own
interactive job; otherwise give the job's number, its name while only one job has it, or `000123/USER/NAME`. It works
for jobs that have ended too, as long as the system still keeps their logs: the last `LOGRTN` of them (a
[system value](cmd:WRKSYSVAL), 50 to start with). Work with Job option 10 opens it as well.

Each entry is either a command the job ran, shown bright as `>> command` (only while the job logs its commands:
`SBMJOB LOG(*YES)` or {{cmd CHGJOB}} `LOG(*YES)`), or a message it got, with its ID and text. A batch job's log starts
with {{msg ELC0304}} and ends with the message that ended it (see {{cmd SBMJOB}}). F10 shows every message's details
(ID, severity, where it came from, time and text) in a window; PageUp and PageDown roll the log.

Logs aren't spooled files: to put one on paper, use {{cmd PRTRPT}} `RPT(*JOBLOG)`.

{{layout 09b_dspjoblog}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
DSPJOBLOG JOB(NOCWALL)
```

See {{page elcl/jobs-and-scheduling}}.
