`ENDJOB` ends a job.

- A **batch job** that's running stops before its next instruction, with `OPTION(*CNTRLD)` or `OPTION(*IMMED)` alike.
  One still waiting on the job queue (or held there) is simply taken off it. Either way it ends with
  {{msg ELC0303}}, which goes in its log and to the user's message queue, and its log is kept with the ended jobs.
- An **interactive job**: the program running in it stops, but the job itself stays until its terminal session
  closes.

Name the job by its number, by its name while only one job has it, or as `000123/USER/NAME`. An unknown or ambiguous
job, or one that has already ended, is {{msg ELC0302}}. You may end your own jobs; ending another user's takes a
security officer ({{msg ELC0401}}, naming `*JOBCTL`), except on a network with no Firewall or at `SECLVL` 10.

Done: {{msg ELC0311}} (`Job &1 ending (&2).`, with the option you gave), also written in the job's log. Work with
Active Jobs option 4 runs it after asking (F11 in that confirmation for `*IMMED`).

```elcl
ENDJOB JOB(NOCWALL)
ENDJOB JOB(000123/ZAG/RESTOCK) OPTION(*IMMED)
```

A job that's ended gets no chance to tidy up: nothing in the program runs after `ENDJOB`. To stop a long-running job
cleanly, have it check something you control, such as a record in a file or a redstone input, and `RETURN` when it
says so.
