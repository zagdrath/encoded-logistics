`CHGJOB` changes a job's priority, whether it logs its commands, or both. Leave a parameter at `*SAME` to keep it.
Work with Active Jobs option 2 runs it from a window.

- `JOBPTY(1)` to `JOBPTY(9)`: the job's place on the job queue. Waiting batch jobs start lowest number first (then in
  the order they were submitted); every job starts at 5. Once a job is running, its priority makes no difference.
- `LOG(*YES)` writes each command the job runs into its job log from now on (`>> command` on {{cmd DSPJOBLOG}});
  `LOG(*NO)` stops that. A running batch job is changed at once. On an interactive job it applies to the programs you
  `CALL` afterwards; interactive jobs log their commands to start with.

Name the job by its number, by its name while only one job has it, or as `000123/USER/NAME`; an unknown or ambiguous
job is {{msg ELC0302}}. You may change your own jobs; changing another user's takes a security officer
({{msg ELC0401}}, naming `*JOBCTL`), except on a network with no Firewall or at `SECLVL` 10, where anyone may.

`CHGJOB` sends no completion message when it works: check the result on Work with Job option 2 ({{cmd WRKJOB}}).

```elcl
SBMJOB CMD(CALL PGM(ELSYS/ARCHIVE)) JOB(ARCHIVE)
CHGJOB JOB(ARCHIVE) JOBPTY(1) LOG(*YES)
```
