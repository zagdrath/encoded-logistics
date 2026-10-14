`RMVJOBSCDE` removes a job schedule entry, so it doesn't run again. A batch job it already submitted carries on: end
that with {{cmd ENDJOB}} if you need to.

`JOB` is the entry's name as {{cmd ADDJOBSCDE}} gave it (not a job number); no entry of that name is {{msg ELC0302}}.
You may remove your own entries; removing another user's takes a security officer ({{msg ELC0401}}, naming
`*JOBCTL`), except on a network with no Firewall or at `SECLVL` 10. Done: {{msg ELC0313}}. A `*ONCE` entry removes
itself once it has run. Work with Job Schedule Entries option 4 runs this command after asking.

```elcl
RMVJOBSCDE JOB(NIGHTLY)
```
