`SBMJOB` runs one command, usually a {{cmd CALL}} of a program, as a **batch job**: on a job host, away from your
terminal, so it keeps going after you sign off. The job runs as you, with your Firewall permissions, whether or not
you're online.

## Where it runs

A batch job needs a **job host** on the network:

| Job host | Batch jobs at once |
|---|---|
| {{item compute_server}} (in a Server Rack) | {{config elcl.computeServerJobs default}} each (0 stops Compute Servers taking jobs) |
| {{item midrange_system}} | 1, or 2 with an {{item expansion_cabinet}} |
| {{item integrated_midrange}} | 4 |

The job goes on the job queue (status `*JOBQ`) and, on the next tick, to an online host with room: any host with
`HOST(*ANY)`, or only the one you name (by device name, `CMPSRV01`). At most `QMAXJOB` batch jobs run at once on the
whole system (a [system value](cmd:WRKSYSVAL), 16 to start with); the rest wait their turn, lowest priority number
first ({{cmd CHGJOB}} `JOBPTY`, 5 by default), then in the order they were submitted. A host that's named but offline
just keeps the job waiting.

Each tick a batch job runs up to {{config elcl.batchBudget default}} instructions, within the server-wide
{{config elcl.globalBudget}}. A job waiting in {{cmd DLYJOB}} uses none.

## Parameters that matter

- `CMD` is one command. A program-only statement (`CHGVAR`, `IF` and so on) is refused when the line is checked
  ({{msg ELC0103}}); put the logic in a program and `CALL` it. A screen command such as `WRKLIB` makes the job end with
  {{msg ELC0105}} when it runs.
- `JOB(*JOBD)` names the job `QDFTJOBD`. Give it a name of your own to find it on {{cmd WRKACTJOB}}: commands take a
  job by its number (`000123`), by its name while only one job has it, or as `000123/USER/NAME`.
- `LOG(*YES)` writes every command the job runs into its job log (shown as `>> command` on {{cmd DSPJOBLOG}}). Messages
  are logged either way.

## Messages

- {{msg ELC0304}}: the job is on the queue (`&2` is the host you asked for, `*ANY` or a name). This is the job's
  first log entry.
- {{msg ELC0301}}: the network has no job host at all, or `HOST()` names one that isn't on the network.
- A syntax error in `CMD` is reported as that error.

When the job is over, its last message goes in its log: {{msg ELC0307}} when it ended normally, the escape message
that stopped it, {{msg ELC0303}} after {{cmd ENDJOB}}, or {{msg ELC0310}} when its host was unloaded, removed or lost
power. The last two are also sent to your message queue ({{cmd DSPMSG}}). The system keeps the logs of the last
`LOGRTN` ended jobs.

> [!WARNING] No host resumes a job
> A job that's running when its host goes down ends with {{msg ELC0310}}, and so does every running batch job when the
> server restarts. Jobs still waiting on the queue stay there. A long-running job (a status display that refreshes
> forever) has to be submitted again; a [schedule entry](cmd:ADDJOBSCDE) can do that for you.

```elcl
SBMJOB CMD(CALL PGM(ELSYS/NOCWALL)) JOB(NOCWALL)
SBMJOB CMD(CALL PGM(ELSYS/RESTOCK) PARM('IRON_INGOT' 500)) +
       JOB(RESTOCK) HOST(CMPSRV01) LOG(*YES)
```

Jobs, job hosts and job logs are explained in {{page elcl/jobs-and-scheduling}}.
