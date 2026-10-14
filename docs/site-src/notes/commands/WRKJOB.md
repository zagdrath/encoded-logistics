Work with Job is a menu for one job. `JOB(*)`, the default, is your own interactive job; otherwise give a job's
number (`000123`), its name while only one job has it, or `000123/USER/NAME`. Ended jobs whose logs are still kept can
be named too. An unknown or ambiguous job is {{msg ELC0302}}. Work with Active Jobs option 5 opens it for the job on
that row.

Type a number on the command line and press Enter, or click an option:

| Option | Shows |
|---|---|
| `1` | Its status attributes: name, user, number, status, host and budget used. |
| `2` | Its definition attributes: type (`*INTER` or `*BATCH`), priority, whether it logs its commands, and user. |
| `4` | Its spooled files ({{cmd WRKSPLF}}). |
| `10` | Its job log ({{cmd DSPJOBLOG}}). |
| `11` | Its call stack: the programs it's running, each with the line it has reached. |
| `30` | All of them, one after another. |

{{layout 09_wrkjob}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKJOB JOB(NOCWALL)
```

To change, hold, release or end the job, use {{cmd CHGJOB}}, {{cmd HLDJOB}}, {{cmd RLSJOB}} and {{cmd ENDJOB}}, or
Work with Active Jobs. See {{page elcl/jobs-and-scheduling}}.
