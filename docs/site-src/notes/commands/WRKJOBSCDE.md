Work with Job Schedule Entries lists the system's job schedule entries: name, status (`*SCD` scheduled or `*HLD`
held), frequency (`*ONCE`, `*DAILY` or `*INTERVAL`), the next run (game day and time) and the command. It's also
option 11 on the main menu. Type an option next to an entry:

| Option | What it does |
|---|---|
| `2` | Change it: {{cmd ADDJOBSCDE}}'s prompter with its values. Enter removes the entry and adds it again as you changed it, so it comes back scheduled (`*SCD`), with its next run worked out afresh. |
| `3` | Hold it ({{cmd HLDJOBSCDE}}); on a held entry, release it ({{cmd RLSJOBSCDE}}). |
| `4` | Remove it, after you confirm ({{cmd RMVJOBSCDE}}). |
| `10` | Submit now: runs its command as a batch job straight away ({{cmd SBMJOB}}). The entry and its timetable stay as they were. |

F6 prompts {{cmd ADDJOBSCDE}} to add an entry. Hold and release send no message: the status column shows the change.

{{layout 10_wrkjobscde}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKJOBSCDE
```

How entries are timed (game clock times, real-time intervals) is on {{cmd ADDJOBSCDE}} and
{{page elcl/jobs-and-scheduling}}.
