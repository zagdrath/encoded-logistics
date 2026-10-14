`RLSJOB` releases a job held with {{cmd HLDJOB}}. A batch job that had started carries on from where it stopped
(status `*ACTIVE`); one that was held on the job queue goes back to `*JOBQ` and waits its turn for a job host. A held
interactive job's program carries on.

Name the job by its number, by its name while only one job has it, or as `000123/USER/NAME`; an unknown or ambiguous
job is {{msg ELC0302}}. You may release your own jobs; releasing another user's takes a security officer
({{msg ELC0401}}, naming `*JOBCTL`), except on a network with no Firewall or at `SECLVL` 10.

Done: {{msg ELC0309}}, which also goes in the job's log. Work with Active Jobs option 6 runs this command.

```elcl
RLSJOB JOB(NOCWALL)
```
