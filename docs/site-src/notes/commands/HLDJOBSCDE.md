`HLDJOBSCDE` holds a job schedule entry: its status becomes `*HLD` and it doesn't run while it's held, though it keeps
its next run time. Release it with {{cmd RLSJOBSCDE}}. A batch job it already submitted isn't affected (hold that with
{{cmd HLDJOB}}).

`JOB` is the entry's name; no entry of that name is {{msg ELC0302}}. You may hold your own entries; holding another
user's takes a security officer ({{msg ELC0401}}, naming `*JOBCTL`), except on a network with no Firewall or at
`SECLVL` 10.

`HLDJOBSCDE` sends no completion message when it works: the entry's status on {{cmd WRKJOBSCDE}} shows it (option 3
there holds an entry, and releases a held one).

```elcl
HLDJOBSCDE JOB(NIGHTLY)
```
