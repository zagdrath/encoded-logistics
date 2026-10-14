`RLSJOBSCDE` releases a job schedule entry held with {{cmd HLDJOBSCDE}}: its status goes back to `*SCD` and it runs
at its next run time. If that time passed while the entry was held, it runs straight away, once, and then keeps to
its timetable (a `*ONCE` entry runs and is removed).

`JOB` is the entry's name; no entry of that name is {{msg ELC0302}}. You may release your own entries; releasing
another user's takes a security officer ({{msg ELC0401}}, naming `*JOBCTL`), except on a network with no Firewall or
at `SECLVL` 10.

`RLSJOBSCDE` sends no completion message when it works: the entry's status on {{cmd WRKJOBSCDE}} shows it.

```elcl
RLSJOBSCDE JOB(NIGHTLY)
```
