`CHGSYSVAL` changes one of the system's seven system values. The table of values, defaults and what each one
does is on {{cmd WRKSYSVAL}}, whose option 2 runs this command from a window.

**Who may:** a security officer only, at either `SECLVL`. A security officer is the owner of the network's
{{item firewall}}, or a server operator who's online. Anyone else gets {{msg ELC0401}} (naming `*SECOFR`). On a
network with no Firewall, anyone may.

**What it takes:** the value is upper-cased, then checked: `SYSNAME` a letter and up to seven letters or digits;
`DATFMT` `*DAY`, `*MDY`, `*DMY` or `*YMD`; `SECLVL` `10` or `30`; `QMAXJOB` 1 to 999; `LOGRTN` and `CRFLOGRTN` 0 to
999; `PHOSPHOR` `*GREEN`, `*AMBER` or `*WHITE`. Anything else is {{msg ELC0103}}, and so is a system value name that
doesn't exist.

Done: {{msg ELC0222}}. Lowering `CRFLOGRTN` trims the crafting history straight away; a lower `LOGRTN` drops the
oldest job logs the next time a job ends. A new `SYSNAME` shows in your screen header at once and starts a new folder
for folder sync; a new `QMAXJOB` applies the next time the job queue is looked at (every tick).

```elcl
CHGSYSVAL SYSVAL(QMAXJOB) VALUE(32)
CHGSYSVAL SYSVAL(PHOSPHOR) VALUE(*AMBER)
CHGSYSVAL SYSVAL(SYSNAME) VALUE(ZAGBASE)
```
