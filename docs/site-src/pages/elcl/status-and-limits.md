---
title: Limits and behaviour to know
nav: Limits
section: elcl
order: 85
summary: The Terminal OS's and ELCL's limits, and the places where they behave in ways you might not expect.
legacy: [status, hardware, differences]
---
The Terminal OS and ELCL are complete: every command in the [command reference](page:elcl/commands/index) and every
message in the [message reference](page:elcl/messages) works as its page describes. This page gathers the numbers
that bound them and the behaviour that most often surprises people. The server options that set many of these numbers
are on [Server configuration](page:admin/config) (the `elcl` and `plc` sections).

## The numbers {#numbers}

| What | Limit | Set by |
|---|---|---|
| Lines in a source member | {{default elcl.maxSourceLines}} | {{config elcl.maxSourceLines}} |
| Elements in a `*LIST` | {{default elcl.maxListSize}} ({{msg ELC0015}}) | {{config elcl.maxListSize}} |
| `*CHAR` length | 1 to 1,024 | |
| `*DEC` digits | 1 to 31 | |
| Call depth (`CALL`) and subroutine depth (`CALLSUBR`) | 16 each ({{msg ELC0011}}) | |
| Message IDs on one `MONMSG` | 50 | |
| Files a program declares (`DCLF`) | 5 | |
| Instructions a tick: an interactive job | {{default elcl.interactiveBudget}} | {{config elcl.interactiveBudget}} |
| Instructions a tick: each batch job | {{default elcl.batchBudget}} | {{config elcl.batchBudget}} |
| Instructions a tick: all jobs on the server together | {{default elcl.globalBudget}} | {{config elcl.globalBudget}} |
| Instructions a tick: each PLC | {{default plc.plcInstructionsPerTick}} | {{config plc.plcInstructionsPerTick}} |
| Batch jobs each Compute Server runs | {{default elcl.computeServerJobs}} | {{config elcl.computeServerJobs}} |
| Batch jobs running on a system at once | 16 | the `QMAXJOB` [system value](page:elcl/jobs-and-scheduling#sysvals) |
| Ended job logs kept | 50 | `LOGRTN` |
| Ended crafting jobs kept | {{default elcl.craftLogRetention}} | `CRFLOGRTN` ({{config elcl.craftLogRetention}} is its default) |
| Spooled files on a system | {{default elcl.spooledFileCap}}, then the oldest go | {{config elcl.spooledFileCap}} |
| Messages in each message queue | {{default elcl.messageCap}}, then the oldest go | {{config elcl.messageCap}} |
| Records in a database file | {{default elcl.maxRecordsPerFile}} ({{msg ELC2208}}) | {{config elcl.maxRecordsPerFile}} |
| A record sent from Update Data | 8,000 characters in all | |
| Source text per byte of drive storage | {{default elcl.charsPerStorageByte}} characters | {{config elcl.charsPerStorageByte}} |
| Library source on an 8" Diskette | {{default elcl.disketteBytes}} bytes | {{config elcl.disketteBytes}} |
| `DLYJOB DLY()` | 1 to 86,400 game seconds | |
| `DLYTICK TICKS()` | 1 to 1,200 ticks | |
| Schedule entry `INTERVAL()` | 1 to 604,800 real seconds | |
| Firings of one trigger | at most one a second | |
| PLCs running | {{default plc.plcMaxPerChunk}} in a chunk, {{default plc.plcMaxPerServer}} on the server | {{config plc.plcMaxPerChunk}}, {{config plc.plcMaxPerServer}} |

## Known limits {#known-limits legacy="known-limits"}

### Jobs

- **Batch jobs end when their host goes down.** A job's state is saved with the system, but no job host carries a job
  on: when a Compute Server or Midrange System goes offline (unloaded, removed, out of power, an IPL) or the server
  restarts, its running batch jobs end with {{msg ELC0310}}. Queued and held jobs wait. Use a
  [schedule entry](page:elcl/jobs-and-scheduling#schedules) or a [trigger](page:elcl/jobs-and-scheduling#triggers) for
  work that has to start again by itself.
- **Interactive jobs aren't saved.** A program called from a terminal ends when its terminal closes or the server
  stops; use {{cmd SBMJOB}} for anything long-running.
- **Game time.** `DLYJOB DLY()` counts game seconds (20 ticks each), so it stretches when the server lags; schedule
  entries with `FRQ(*INTERVAL)` count real seconds. On a command line `DLYJOB` and `DLYTICK` complete at once.
- **No message waits for a reply.** Every message is informational or an escape; nothing stops a job to ask.

### Triggers

- **Declare `&EVENT` `LEN(12)`.** The event name arrives whole, and the longest, `*SUPFAILOVER`, has 12 characters. A
  shorter variable cuts it: in a `LEN(10)` `&EVENT`, `*DEVOFFLINE` arrives as `*DEVOFFLIN`, `*PWRRESTORED` as
  `*PWRRESTOR`, `*SUPFAILOVER` as `*SUPFAILOV` and `*PROTSWITCH` as `*PROTSWITC`.
- **Device statuses aren't saved.** The `*DEV...` triggers compare each device with how it was half a second before;
  after a restart the first look only records them, so a change while the server was down doesn't fire.
- **A condition that already holds doesn't fire.** A new trigger (and every polled trigger after a restart) first
  records whether its condition holds; it fires the next time the condition becomes true.

### Security and authority

- **`*SECOFR`** is the Firewall's owner and the server's operators. On a network without a Firewall everyone has full
  authority, so anyone may change system values there, and the Sign On screen appears only when the network has a
  Firewall and `SECLVL` is 30.
- **Reading files.** Every library gives the public `*USE`, so any user may read any library's files; only ELSYS's
  system files are guarded (by the Firewall's view permission). There is no `*EXCLUDE` authority: `CHGLIB AUT()` takes
  `*USE` and `*CHANGE`.
- **Commands with no Firewall permission.** The display commands ({{cmd SNDDSPTXT}}, {{cmd CLRDSP}},
  {{cmd CHGDSPRGN}}, {{cmd SNDDSPWDG}}, {{cmd SNDDSPGPH}}, {{cmd SNDDSPIMG}}, {{cmd RTVDSPSIZ}}), the print commands
  ({{cmd PRTTXT}}, {{cmd PRTRPT}}) and the OS commands need no Firewall permission: any user's program can drive any
  Display Panel and printer on the network. The OS commands check the OS's own authority instead (libraries, jobs and
  system values). The command pages show each command's permission.

### Storage and data

- **Source storage is counted, not reserved.** Members count against free drive space ({{msg ELC0207}} stops a save
  that doesn't fit), but items going into the network don't check for them, so items can fill the drives past the
  members' share. See [Storage cost](page:elcl/libraries-and-editor#storage-cost).
- **Long records in Update Data.** A record whose values add up to more than 8,000 characters can't be sent from the
  Update Data screen; change it with a program. Long values scroll in their field.
- **Item tiers.** There is no tier stored on each item: {{cmd CHGITMTIER}} works through the Tape Libraries' keep-hot,
  pin and archive lists. See [Hot and cold storage](page:storage/hot-and-cold).

### Names and text from the server

- **`%NAME` on dedicated servers.** Item names are worked out on the server, which may not have a mod's translations,
  so `%NAME` of a mod's item can come back as its translation key (`item.encodedlogistics.logic_die`). The reason in a
  `*CRFSTALL` trigger's `&DATA` is worked out the same way.
- **Device names** are given by the mod (`CTLIF01`, `CMPSRV01`...) and changed with {{cmd RNMDEV}} or Work with
  Devices option 2.

### Things accepted but not used

- `SNDMSG TOTRM()` and `WRKINV FILTER()` are accepted and ignored.
- `DATFMT` changes the dates the system writes, not the screen header's clock, which always shows "Day n".
- Spooled files have the statuses `*RDY`, `*PRT` and `*HLD`, but the system makes them `*RDY`. Job logs are never
  spooled: `PRTRPT RPT(*JOBLOG)` prints one straight on paper.

### Commands that answer with no message

These succeed without a completion message: {{cmd CHGJOB}}, {{cmd HLDJOBSCDE}}, {{cmd RLSJOBSCDE}},
{{cmd HLDTRGEVT}}, {{cmd RLSTRGEVT}}, {{cmd SNDMSG}}, {{cmd PRTTXT}}, {{cmd CHGDEVSTS}}, {{cmd CHGDEVFTR}} and
{{cmd CHGMCHSTS}}. Check the result with the matching Work with screen or `RTV` command if you need to.

### Messages for things that aren't there

- Removing, holding or releasing a trigger that doesn't exist is {{msg ELC0103}} (the value of `TRG` isn't valid).
- Removing, holding or releasing a schedule entry that doesn't exist is {{msg ELC0302}}.
