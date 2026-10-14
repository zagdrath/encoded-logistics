---
title: Jobs, scheduling and triggers
nav: Jobs and scheduling
section: elcl
order: 70
summary: Interactive and batch jobs and their hosts, job logs, schedule entries, trigger events, message queues, spooled output, printing and system values.
legacy: [jobs]
---
All ELCL work runs in *jobs*. They are script jobs, separate from the network's crafting jobs (which
{{cmd WRKCRFJOB}} shows; see [Crafting jobs](page:crafting/jobs)). This page covers how jobs run and where, how to run
them on a timer or when something happens, and where their messages and printed output go.

## Jobs {#job-basics legacy="job-basics"}

There are two kinds of job.

- **Interactive job.** One for each open Terminal OS session (a {{item terminal_desk}} or an Integrated Midrange
  System's console), started by the session's first request. It is named `QINTER`, has type `INT`, runs on the
  desk's device name (`ELDESK01`), has priority 5 and always logs its commands. The commands you type run in it, and so
  does a program you start there with {{cmd CALL}}: one at a time, {{config elcl.interactiveBudget default}}
  instructions a tick. It ends when the terminal closes ({{msg ELC0307}} in its log), ending any program it is running,
  and its log joins the kept logs of ended jobs. Interactive jobs last only as long as their session and aren't saved.
- **Batch job.** Submitted with `SBMJOB CMD(command) JOB(name) HOST(*ANY|host) LOG(*NO|*YES)`
  ({{cmd SBMJOB}}), by a schedule entry or by a trigger. It runs on a job host, {{config elcl.batchBudget default}}
  instructions a tick, as the user who submitted it, with that player's Firewall permissions, whether they are online
  or not. Its command can be any command that works in batch; usually it is `CALL PGM(...)`. `SBMJOB` answers
  {{msg ELC0304}} and the job waits on the job queue (`*JOBQ`) until a host has room. With no `JOB()` the job is named
  `QDFTJOBD`.

### Job hosts {#hosts}

Batch jobs need a *job host* on the network. Each tick, queued jobs go to an online host with a free slot (any host
for `HOST(*ANY)`, else the one named), in priority order (1 first, 5 unless changed with `CHGJOB JOBPTY()`) and then
oldest first, as long as fewer than `QMAXJOB` batch jobs are running on the system (see [system values](#sysvals)). A
system with no job host at all refuses the job at once with {{msg ELC0301}}, and so does a `HOST()` it doesn't have.

| Host | Batch jobs at once | Name |
|---|---|---|
| {{item compute_server}} (in a Server Rack) | {{config elcl.computeServerJobs default}} each; 0 means Compute Servers aren't hosts | Its device name: `CMPSRV01` |
| {{item midrange_system}} | 1, or 2 with an {{item expansion_cabinet}} beside it | `MIDRANGE01` |
| {{item integrated_midrange}} | 4 | `MIDRANGE01` |

A host counts only while it is online: a Compute Server while it is powered in its rack, a Midrange System while it is
running (not during its IPL). See [the Midrange line](page:crafting/midrange) for the Midrange Systems.

**When a host goes down.** A running batch job's state is saved with the system, but no host carries a job on after
it stops: when a job's host goes offline (unloaded, removed, out of power, or a Midrange System's IPL) or the server
restarts, the job ends with {{msg ELC0310}}, which is logged and sent to the job's user. Jobs still on the job queue,
and held jobs that hadn't started, wait through a restart and start once a host has room. For work that must survive
a restart, use a [schedule entry](#schedules) or a [trigger](#triggers): they submit a new job each time.

### Job IDs and statuses {#job-ids}

- **Job IDs** are `nnnnnn/USER/JOBNAME`, for example `000123/ZAGDRATH/RESTOCK`. Commands take the number, the full
  ID, or the job name alone if only one job that hasn't ended has it ({{msg ELC0302}} otherwise). Numbers start at
  `000001` on each system, are shared by interactive and batch jobs, are saved, and wrap round after `999999`.
- **Statuses:** `*JOBQ` (waiting for a host), `*ACTIVE`, `*WAIT` (in a `DLYJOB` or `DLYTICK`, a tape recall or a
  craft it waits for), `*HELD`, and `*ENDED` (an ended job whose log is kept).
- **Holding, releasing, ending.** {{cmd HLDJOB}} holds a queued job (it won't start) or pauses a running one where it
  is; {{cmd RLSJOB}} lets it go on (back to `*JOBQ` if it hadn't started). {{cmd ENDJOB}} takes a queued job off the
  queue or stops a running program: `OPTION(*CNTRLD)` lets the command it is running finish, `*IMMED` stops it at
  once. The batch job then ends with {{msg ELC0303}} in its log and in its user's messages.
- **Other users' jobs.** You can hold, release, change and end your own jobs. Doing it to another user's job takes
  `*SECOFR` (the Firewall's owner or a server operator), or a network with no Firewall or at `SECLVL` 10; otherwise
  it is {{msg ELC0401}} naming `*JOBCTL`.
- **Unmonitored errors.** A program ended by an escape message it didn't catch puts that message in the job log and in
  the job user's message queue, then the job ends.
- **Budget.** Work with Active Jobs shows how much of its per-tick budget each job used on the last tick; see
  [the runtime model](page:elcl/language#limits).

Commands on jobs: {{cmd WRKACTJOB}}, {{cmd WRKJOB}}, {{cmd DSPJOBLOG}}, {{cmd CHGJOB}} (priority 1 to 9, command
logging), {{cmd HLDJOB}}, {{cmd RLSJOB}}, {{cmd ENDJOB}}, {{cmd SBMJOB}} and {{cmd RTVJOBA}} (a program's own job: its
user, name, type and host).

```elcl
SBMJOB CMD(CALL PGM(ELSYS/NOCWALL)) JOB(NOCWALL)    /* ELC0304, then *ACTIVE on CMPSRV01 */
SBMJOB CMD(CALL PGM(ZAGLIB/RESTOCK) PARM('IRON_INGOT' 500)) JOB(RESTOCK) LOG(*YES)
CHGJOB JOB(RESTOCK) JOBPTY(1)                      /* to the front of the queue          */
ENDJOB JOB(NOCWALL)                                /* ELC0311, then ELC0303 in its log    */
```

{{layout 08_wrkactjob}}

## Job logs {#job-logs legacy="job-logs"}

Every job keeps a log of the commands it ran (when it logs commands: always for the interactive job, and for a batch
job with `LOG(*YES)` on `SBMJOB` or `CHGJOB LOG(*YES)`) and every message it got, with its ID, severity, sender and
time. A batch job's log starts with {{msg ELC0304}} and ends with how it ended: {{msg ELC0307}}, the escape message
that ended it, {{msg ELC0303}} or {{msg ELC0310}}.

View a log with {{cmd DSPJOBLOG}} or WRKJOB option 10, or print it with `PRTRPT RPT(*JOBLOG)` from inside the job.
When a job ends its log is kept with the system's ended jobs: the last `LOGRTN` of them, saved with the system. Name an
ended job by its number or full ID to see its log.

{{layout 09b_dspjoblog}}

## Job schedule entries {#schedules legacy="schedules"}

{{cmd ADDJOBSCDE}} `JOB(name) CMD(command) FRQ(*ONCE|*DAILY|*INTERVAL) TIME(HHMM) INTERVAL(seconds)` adds an entry
that submits `CMD` as a batch job, named after the entry, when it is due.

- **`*ONCE`** (the default): the next time the game clock reaches `TIME` (`HHMM` or `HHMMSS`; the game day starts at
  06:00). `*CURRENT`, the default `TIME`, means now. The entry is removed once it has run.
- **`*DAILY`**: every day at `TIME` on the game clock. If its network was unloaded through one or more of its times,
  it runs once when the network is loaded again, not once for each day missed.
- **`*INTERVAL`**: every `INTERVAL` real seconds (1 to 604,800; needed with `FRQ(*INTERVAL)`), the first run that long
  after the entry is added. It is real time, so it keeps its pace whatever the game clock or the server's tick rate
  does.

How entries behave:

- They fire only while their network is loaded, whether or not anyone is signed on.
- The job runs as the user who added the entry, with their Firewall permissions. If it can't be submitted (no job host:
  {{msg ELC0301}}), the message goes to that user's message queue as "Schedule entry NAME: ...".
- Work with Job Schedule Entries ({{cmd WRKJOBSCDE}}) shows each entry's next run on the game clock (an `*INTERVAL`
  entry's as near as real seconds go). Option 10 submits it now without changing its next run.
- {{cmd HLDJOBSCDE}} holds an entry (status `*HLD`; it doesn't fire) and {{cmd RLSJOBSCDE}} releases it (`*SCD`);
  both answer with no message. {{cmd RMVJOBSCDE}} removes one. An entry name that is taken is {{msg ELC0305}}, and one
  that doesn't exist is {{msg ELC0302}}.
- Entries are saved with the system.

```elcl
ADDJOBSCDE JOB(ARCHIVE) CMD(CALL PGM(ELSYS/ARCHIVE) PARM('*_ORE')) FRQ(*DAILY) TIME(0200)
ADDJOBSCDE JOB(RESTOCK) CMD(CALL PGM(ZAGLIB/RESTOCK) PARM('IRON_INGOT' 500)) +
           FRQ(*INTERVAL) INTERVAL(300)
ADDJOBSCDE JOB(WAKEUP) CMD(SNDMSG MSG('Morning') TOUSR(*ALL)) TIME(0600)
```

{{layout 10_wrkjobscde}}

## Trigger events {#triggers legacy="triggers"}

{{cmd ADDTRGEVT}} `TRG(name) EVENT(event) PGM(lib/program) ITEM() DEV() VALUE() TYPE()` runs a program as a batch job,
named after the trigger, whenever something happens on the network. It runs as the user who added the trigger, as
`CALL PGM(program) PARM('event' 'data')`, so the program must take exactly two parameters:

```elcl
PGM PARM(&EVENT &DATA)
  DCL VAR(&EVENT) TYPE(*CHAR) LEN(12)    /* the event's name, *SUPFAILOVER the longest */
  DCL VAR(&DATA)  TYPE(*CHAR) LEN(256)   /* what happened: see the table               */
  SNDMSG MSG(%TRIM(&EVENT) *BCAT %TRIM(&DATA)) TOUSR(*SYSOPR)
ENDPGM
```

The event name arrives whole; a variable shorter than the name cuts it (a `LEN(10)` `&EVENT` gets `*DEVOFFLIN` for
`*DEVOFFLINE`), so declare `&EVENT` `LEN(12)`. `ITEM()`, `DEV()` and `VALUE()` narrow what fires the trigger, as the
table says; `*ANY` (the default) takes any. `VALUE()` takes text, so quote a side: `VALUE('*NORTH')`. `TYPE(*FLUID)` or `TYPE(*PRES)` makes `*ITMBELOW` and `*ITMABOVE` watch a
fluid or a gas.

{{table triggers}}

How triggers behave:

- **Edge-triggered.** A trigger fires once each time its condition becomes true, not for as long as it holds. Item
  counts, storage use, UPS power and device statuses are looked at every half second. The first look after a trigger is
  added (or after a restart) only records what holds, so a condition that is already true doesn't fire. The other
  events arrive as they happen: ended and stalled crafts, redstone changes, touches, Router, chassis and optical
  events, and Arcforge machine events (the machine ones only while the machine's Small Wireless Bridge is online on the
  network).
- **Debounced.** At least one second (real time) passes between two firings of the same trigger. A crossing during
  that second fires when it's over, if it still holds; events during it fire once then, with the last `&DATA`.
- **Needs a job host.** Each firing is a batch job. With no host, {{msg ELC0301}} goes to the trigger's user's message
  queue as "Trigger NAME: ...".
- **Only while the network is loaded.** Held triggers ({{cmd HLDTRGEVT}}, status `*HELD`) don't fire, and a firing
  waiting out its debounce when the trigger is held is dropped; {{cmd RLSTRGEVT}} makes it `*ACTIVE` again. Both answer
  with no message. {{cmd RMVTRGEVT}} removes one. Removing, holding or releasing a trigger that doesn't exist is
  {{msg ELC0103}}, and a name that is taken is {{msg ELC0306}}.
- Triggers are saved with the system. Device statuses aren't: a device that changed while the server was down
  doesn't fire.

```elcl
ADDTRGEVT TRG(LOWIRON) EVENT(*ITMBELOW) ITEM(IRON_INGOT) VALUE(500) PGM(ZAGLIB/IRONLOW)
ADDTRGEVT TRG(UPSALERT) EVENT(*PWRUPS) PGM(ELSYS/UPSALERT)
ADDTRGEVT TRG(BUTTON) EVENT(*RSCHANGE) DEV(CTLIF01) VALUE('*WEST') PGM(ZAGLIB/BUTTON)
ADDTRGEVT TRG(LOWWATER) EVENT(*ITMBELOW) ITEM(minecraft:water) TYPE(*FLUID) VALUE(8000) +
          PGM(ZAGLIB/PUMP)
```

{{layout 11_wrktrgevt}}

[Worked examples](page:elcl/examples) has complete trigger programs: UPSALERT, DEVWATCH and STALLMSG.

## Message queues {#msgqueues legacy="msgqueues"}

Each user has a message queue, shown newest first by {{cmd DSPMSG}} (Main Menu option 7). While it holds unread
messages, `MW` shows at the end of the message line on every screen.

{{cmd SNDMSG}} `MSG('text') TOUSR(...)` puts a message in a queue:

- `*REQUESTER` (the default): your own queue.
- a user's name: that user's queue.
- `*SYSOPR`: the system operator's queue, `QSYSOPR`. Its chat notice goes to the network's owner (the Firewall's owner).
- `*ALL`: every queue that already exists on the system, plus `QSYSOPR`.

`DSPMSG` shows your own queue. `DSPMSG USR(*SYSOPR)` shows `QSYSOPR`, and `DSPMSG USR(name)` another user's queue, to a
`*SECOFR` (or anyone, on a network with no Firewall or at `SECLVL` 10); anyone else gets {{msg ELC0401}}.

- Online recipients get a chat notice that a message is waiting ({{config elcl.messageChatNotice default}}).
- The system puts messages in queues too: a job's unmonitored escape message goes to its user's queue (sent by the
  program); a batch job ended by `ENDJOB` or a lost host ({{msg ELC0303}}, {{msg ELC0310}}) and a schedule entry or
  trigger that couldn't submit its job are sent by `QSYS`.
- Queues are saved with the system and keep at most {{config elcl.messageCap default}} messages each; past that the
  oldest go. No message needs a reply.
- `SNDMSG`'s `TOTRM()` is accepted and ignored.

{{layout 12_dspmsg}}

## Spooled output {#spooled legacy="spooled"}

Spooled files are printed output kept on the system, listed by Work with Output ({{cmd WRKSPLF}}, Main Menu option 8)
and shown with Display Spooled File (option 5). You see your own files. Each belongs to a job and has a name, a page
count (60 lines a page), a status (`*RDY` when made) and the time it was made. They are saved with the system, at most
{{config elcl.spooledFileCap default}} on each system; past that the oldest go.

| Where it comes from | File name |
|---|---|
| A program's compile listing ({{cmd CRTELPGM}}, WRKMBR option 14) | The program's name |
| A physical file's compile listing ({{cmd CRTPF}}, {{cmd CHGPF}}) | The file's name |
| A printed member (WRKMBR option 6) | The member's name |
| {{cmd PRTTXT}} `TEXT()` | `QPRINT` (`SPLF(*JOB)`, the default) or the name given; each line is added to the job's file of that name |
| `RUNQRY OUTPUT(*PRINT)` ({{cmd RUNQRY}}; also `*DISPLAY` where there is no screen, as in a batch job) | `QPQUPRFIL` |

Listings from a terminal go with your interactive job. Job logs aren't spooled: `PRTRPT RPT(*JOBLOG)` prints one
straight on paper.

{{layout 13_wrksplf}}

## Printing {#printing legacy="printing"}

Printing on paper goes to a printer on the network, the {{item line_printer}}: Work with Output option 6 (to the
first online printer) and {{cmd PRTRPT}}.

| `PRTRPT RPT()` | Prints |
|---|---|
| `*INV` | Every item in the network with its hot and cold counts, sorted by ID, then the total. |
| `*DEV` | Every named device with its type, status, lanes and location. |
| `*JOBLOG` | The log of the job it runs in. |
| `*SPLF` | One of the job's spooled files: `SPLF(name)`, or `*LAST` (the default) for its newest; {{msg ELC0103}} if it has none. |

- Each report starts with a heading line (its title, the date and `SYSNAME`).
- `DEV(*DFT)` is the first online printer; or name one (`PRT01`).
- Printed: {{msg ELC1307}}. No printer: {{msg ELC1301}} (naming `PRT01` for `*DFT`); offline: {{msg ELC1302}}; out of
  paper: {{msg ELC1306}}.
- The Line Printer prints a Printout, one paper a page. A report that runs out of paper part-way puts out the pages
  printed so far, and the rest waits until paper goes in. See [the Midrange line](page:crafting/midrange) for the
  printer itself and its own screen.

```elcl
PRTTXT TEXT('Shift report')
PRTTXT TEXT('All Fabricators idle at 06:00')
PRTRPT RPT(*SPLF)                   /* the job's QPRINT, on the first printer */
MONMSG MSGID(ELC1301 ELC1302 ELC1306)
PRTRPT RPT(*DEV) DEV(PRT01)
```

## The Midrange line, Display Panels and Arcforge machines {#devices}

These devices have their own pages; here is what ELCL does with them.

- **The Midrange line.** A {{item midrange_system}} or {{item integrated_midrange}} is a job host (above) and a
  diskette drive for {{cmd SAVLIB}} and {{cmd RSTLIB}}; its batch jobs end when it does an IPL. Its control panel's
  command line, and the Disk and Tape Drives' screens, run commands as a Terminal Desk's does. See
  [the Midrange line](page:crafting/midrange).
- **Display Panels.** A {{item display_panel}} screen is a device named `DSP01`, `DSP02`... {{cmd SNDDSPTXT}} writes its
  text lines, {{cmd CHGDSPRGN}}, {{cmd SNDDSPWDG}}, {{cmd SNDDSPGPH}} and {{cmd SNDDSPIMG}} set up a dashboard,
  {{cmd CLRDSP}} clears it and {{cmd RTVDSPSIZ}} gives its size. A screen has two modes, Text and Dashboard; one that a
  program sets up regions or widgets on becomes a Dashboard. Touching a screen fires `*DSPTOUCH`. These commands need
  no Firewall permission. See [Display Panels](page:control/display-panels#elcl) and the DSPDASH example in
  [Worked examples](page:elcl/examples#ex-dashboard).
- **Arcforge machines.** With Arcforge installed, a {{item small_wireless_bridge}} puts a machine on the network as a
  device (`ARCCRU01`, by type). {{cmd WRKMCH}}, {{cmd RTVMCHSTS}}, {{cmd RTVMCHSTAT}} and {{cmd RTVMCHLST}} read them
  (the Firewall's view permission); {{cmd CHGMCHSTS}} and {{cmd CHGMCHCFG}} switch them and change their settings
  (build permission). The `*MCH...` trigger events report them. Without Arcforge the machine commands answer
  {{msg ELC1318}}. See [Arcforge](page:integrations/arcforge).

## System values {#sysvals legacy="sysvals"}

System values are settings for the whole system (the network). Work with them on {{cmd WRKSYSVAL}}, or with
{{cmd RTVSYSVAL}} and {{cmd CHGSYSVAL}}. Changing one takes `*SECOFR` (the Firewall's owner or a server operator), or a
network with no Firewall ({{msg ELC0401}} otherwise); a value outside its allowed ones is {{msg ELC0103}}, and a change
answers {{msg ELC0222}}. Values are folded to upper case and saved with the system (only those changed from their
defaults).

{{table sysvals}}

What each one does:

| Value | Effect |
|---|---|
| `SYSNAME` | The system's name: in the screen header ("System: ..."), compile listings, printed members and reports, saved diskette images, the chat notice, and the names of the folders for [folder sync](page:elcl/libraries-and-editor#folder-sync), Display Panel images and Speaker audio. |
| `DATFMT` | How the system writes dates: messages' sent times, spooled files, job logs, listings and reports. `*DAY` writes "Day 2"; `*MDY`, `*DMY` and `*YMD` write the game day as a date on a calendar of 30-day months from year 1 (day 2 is `01/02/01` in `*MDY`). The clock in each screen's header always shows "Day n". |
| `SECLVL` | 10 skips the Sign On screen and gives everyone full authority in the OS; 30 signs on and checks the Firewall's authority. See [SECLVL](page:elcl/index#seclvl). |
| `QMAXJOB` | At most this many batch jobs run at once on the system; the rest wait on the job queue. |
| `LOGRTN` | The logs of this many ended jobs are kept (0: none). |
| `CRFLOGRTN` | The crafting job history keeps this many ended jobs, the oldest removed first (0: none). Lowering it trims the history at once. Its default comes from {{config elcl.craftLogRetention}}. |
| `PHOSPHOR` | The screen colour of every desk whose player chose `*SYSVAL`. |

```elcl
DCL VAR(&NAME) TYPE(*CHAR) LEN(10)
RTVSYSVAL SYSVAL(SYSNAME) RTNVAR(&NAME)
CHGSYSVAL SYSVAL(QMAXJOB) VALUE(32)
CHGSYSVAL SYSVAL(DATFMT) VALUE(*YMD)
```

{{layout 14_wrksysval}}
