`HLDJOB` holds a job: its status becomes `*HELD` and it stops where it is until you release it with {{cmd RLSJOB}}.

- A running batch job pauses before its next instruction, keeping its place, its variables and its slot on the job
  host. A held job uses no budget.
- A batch job still on the job queue stays there and isn't started while it's held.
- Your interactive job can be held too: a program running in it pauses the same way.

Name the job by its number, by its name while only one job has it, or as `000123/USER/NAME`; an unknown or ambiguous
job is {{msg ELC0302}}. You may hold your own jobs; holding another user's takes a security officer
({{msg ELC0401}}, naming `*JOBCTL`), except on a network with no Firewall or at `SECLVL` 10.

Done: {{msg ELC0308}}, which also goes in the job's log. A held job on a host that goes down still ends
({{msg ELC0310}}). Work with Active Jobs option 3 runs this command.

```elcl
HLDJOB JOB(NOCWALL)
```
