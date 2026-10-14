---
title: Worked examples
nav: Examples
section: elcl
order: 60
summary: The sample programs shipped in ELSYS, explained line by line, and short programs for common jobs you can copy.
legacy: [examples]
---
The first five programs here ship with the mod: they are in the `ELSYS` library of every system, already compiled, so
you can run them straight away or copy them into your own library to change them (WRKMBR option 3). The rest are
short programs for common tasks; type them into a member of your own and compile it with {{cmd CRTELPGM}}. Every
program on this page is compiled by the mod's own compiler when the documentation is built.

Each one uses the language described in {{page elcl/language}}, and the commands linked from it. For the sixth shipped
example, which logs item counts to a database file (`ITEMSETUP`, `LOGITEMS` and the file `ITEMHIST`), see
[Database files](page:elcl/database-files#db-example).

## RESTOCK: keep an item stocked {#ex-restock legacy="ex-restock"}

Crafts more of an item when the network holds fewer than you want.

{{example RESTOCK.elclp}}

- Two parameters, both declared: `CALL ELSYS/RESTOCK PARM('IRON_INGOT' 500)`.
- `NOTFND(*ZERO)` makes {{cmd RTVITMCNT}} return 0 instead of raising {{msg ELC1201}} for an item the network doesn't
  hold.
- `QTY(&MIN - &COUNT)`: parameters take expressions.
- The command-level `MONMSG MSGID(ELC1400)` straight after {{cmd STRCRAFT}} catches every crafting error
  (`ELC1401` to `ELC1499`) from that command.
- `*BCAT` joins with one blank; the last {{cmd SNDMSG}} is continued onto a second line with `+`.

Run it every five minutes with a [schedule entry](page:elcl/jobs-and-scheduling#schedules):

```elcl
ADDJOBSCDE JOB(RESTOCK) CMD(CALL PGM(ELSYS/RESTOCK) PARM('IRON_INGOT' 500)) +
           FRQ(*INTERVAL) INTERVAL(300)
```

## SORTDEMO: a language tour {#ex-sortdemo legacy="ex-sortdemo"}

Goes through every named device and prints the ones that aren't online.

{{example SORTDEMO.elclp}}

- `DOFOR` runs to `%SIZE(&DEVS)`, and `%ELEM` picks each element in turn.
- `THEN(ITERATE)` inside the `SELECT` goes on to the next pass of the `DOFOR` around it.
- The subroutine shares `&BAD` and `&DEV` with the main body. The main body ends where the first `SUBR` begins, so
  the `RETURN` before it only makes that plain.
- {{cmd PRTTXT}} writes lines to the job's spooled file, and `PRTRPT RPT(*SPLF)` prints it on the
  {{item line_printer}}. With no printer on the network that last step fails with {{msg ELC1301}} (not monitored, so
  the program ends there); the spooled file is still in Work with Output.

## UPSALERT: a trigger program {#ex-upsalert legacy="ex-upsalert"}

Sheds load when the network goes onto UPS power.

{{example UPSALERT.elclp}}

- A trigger program takes two parameters, `&EVENT` and `&DATA`; see
  [trigger events](page:elcl/jobs-and-scheduling#triggers). Install it with
  `ADDTRGEVT TRG(UPSALERT) EVENT(*PWRUPS) PGM(ELSYS/UPSALERT)`.
- `&EVENT` is declared `LEN(10)` here, which holds `*PWRUPS`. For a program that handles any event, declare it
  `LEN(12)`: the longest event name, `*SUPFAILOVER`, has 12 characters, and a shorter variable cuts it.
- The program-level `MONMSG MSGID(ELC1300)` (straight after the `DCL`s) ignores every device error for the whole
  program, so one device it can't switch off doesn't stop the rest.
- `FOREACH` ... `ENDFOR` walks a list. `CHGRSOUT SIDE(*ALL) LVL(15)` powers every face of the
  {{item control_interface}} named `CTLIF01`: put a lamp or an Alarm Strobe on it.

## NOCWALL: a long-running batch job {#ex-nocwall legacy="ex-nocwall"}

Keeps three lines of status on a Display Panel screen, refreshed every 30 seconds.

{{example NOCWALL.elclp}}

- `DOWHILE COND(*TRUE)` loops for ever; `DLYJOB DLY(30)` waits 30 seconds without using the job's budget. The job
  shows `*WAIT` on Work with Active Jobs for most of its life.
- The program-level `MONMSG ... EXEC(RETURN)` ends the job cleanly when the display is removed.
- Run it as a batch job: `SBMJOB CMD(CALL PGM(ELSYS/NOCWALL)) JOB(NOCWALL)` ({{cmd SBMJOB}}'s `CMD` takes a nested
  command). It runs until you end it with `ENDJOB JOB(NOCWALL)` or its job host goes down.
- Name a {{item display_panel}} screen `NOCDSP01` first (`RNMDEV DEV(DSP01) NEWNAME(NOCDSP01)`, or its configuration
  screen). With no display of that name the first {{cmd SNDDSPTXT}} raises {{msg ELC1301}} and the monitor ends the job
  at once.

## ARCHIVE: move items to tape {#ex-archive legacy="ex-archive"}

Sends every hot item that matches a filter to tape.

{{example ARCHIVE.elclp}}

- `CALL ELSYS/ARCHIVE PARM('*_ORE')`. {{cmd RTVITMLST}}'s `FILTER()` takes text with `*` wildcards, or a `#tag`.
- `SNDPGMMSG ... MSGTYPE(*ESCAPE)` ends the program with an escape message of its own (`USR0001`) when the network has
  no Tape Library.
- The nested command in `THEN( + ...)` is continued onto the next line.
- {{cmd CHGITMTIER}} `TIER(*COLD)` puts the item on the Tape Libraries' archive list; see
  [Hot and cold storage](page:storage/hot-and-cold).

## Keep several items stocked {#ex-stockall}

RESTOCK for a whole list. Each entry is an item and the count to keep, separated by a blank.

```elcl name=STOCKALL
/* STOCKALL - keep every item on the list stocked                  */
/* Run it every 10 minutes:                                         */
/*   ADDJOBSCDE JOB(STOCKALL) CMD(CALL PGM(ZAGLIB/STOCKALL)) +      */
/*              FRQ(*INTERVAL) INTERVAL(600)                        */
PGM
  DCL VAR(&WANT)  TYPE(*LIST)
  DCL VAR(&LINE)  TYPE(*CHAR) LEN(80)
  DCL VAR(&ITEM)  TYPE(*CHAR) LEN(64)
  DCL VAR(&AT)    TYPE(*INT)
  DCL VAR(&MIN)   TYPE(*INT)
  DCL VAR(&COUNT) TYPE(*INT)
  DCL VAR(&JOB)   TYPE(*CHAR) LEN(10)

  ADDLSTE LIST(&WANT) VALUE('IRON_INGOT 512')
  ADDLSTE LIST(&WANT) VALUE('COPPER_INGOT 256')
  ADDLSTE LIST(&WANT) VALUE('encodedlogistics:logic_die 64')

  FOREACH VAR(&LINE) IN(&WANT)
    CHGVAR VAR(&AT)   VALUE(%SCAN(' ' &LINE))
    CHGVAR VAR(&ITEM) VALUE(%SST(&LINE 1 &AT - 1))
    CHGVAR VAR(&MIN)  VALUE(%INT(%SST(&LINE &AT + 1 %LEN(&LINE) - &AT)))

    RTVITMCNT ITEM(&ITEM) RTNCOUNT(&COUNT) NOTFND(*ZERO)
    IF COND(&COUNT *GE &MIN) THEN(ITERATE)

    STRCRAFT ITEM(&ITEM) QTY(&MIN - &COUNT) WAIT(*YES) RTNCRFJOB(&JOB)
    MONMSG MSGID(ELC1400) EXEC(DO)
      SNDMSG MSG('STOCKALL: cannot craft' *BCAT &ITEM) TOUSR(*SYSOPR)
      ITERATE
    ENDDO
    PRTTXT TEXT('Crafted' *BCAT %CHAR(&MIN - &COUNT) *BCAT %TRIM(&ITEM) +
           *BCAT 'in job' *BCAT &JOB)
  ENDFOR
ENDPGM
```

- `%SCAN` finds the blank, `%SST` cuts the line in two and `%INT` turns the count into a number.
- `WAIT(*YES)` makes each craft finish before the next item is looked at, so a run never starts a second job for an
  item whose first is still crafting. Waiting costs the job no budget.
- `ITERATE` inside the `MONMSG`'s `DO` group goes on to the next item.

## Storage alarm on a Cage Light and an Alarm Strobe {#ex-stgalarm}

Turns on a red light above 80% storage use and the strobe above 95%, and turns them off again once there's room.

```elcl name=STGALARM
/* STGALARM - storage alarms: LGT01 above 80%, SRN01 above 95%       */
/* Submit: SBMJOB CMD(CALL PGM(ZAGLIB/STGALARM)) JOB(STGALARM)        */
PGM
  DCL VAR(&PCT)   TYPE(*DEC) LEN(5 1)
  DCL VAR(&LIGHT) TYPE(*LGL)
  DCL VAR(&SIREN) TYPE(*LGL)

  DOWHILE COND(*TRUE)
    RTVSTGSTS RTNPCT(&PCT)

    IF COND(&PCT *GE 80 *AND *NOT &LIGHT) THEN(DO)
      CHGLGT DEV(LGT01) STATUS(*ON) LVL(15)
      CHGVAR &LIGHT '1'
    ENDDO
    ELSE CMD(IF COND(&PCT *LT 75 *AND &LIGHT) THEN(DO))
      CHGLGT DEV(LGT01) STATUS(*OFF)
      CHGVAR &LIGHT '0'
    ENDDO

    IF COND(&PCT *GE 95 *AND *NOT &SIREN) THEN(DO)
      STRSRN DEV(SRN01) SOUND(*KLAXON) MODE(*FAST)
      SNDMSG MSG('Storage' *BCAT %CHAR(&PCT) *TCAT '% full') TOUSR(*SYSOPR)
      CHGVAR &SIREN '1'
    ENDDO
    ELSE CMD(IF COND(&PCT *LT 90 *AND &SIREN) THEN(DO))
      ENDSRN DEV(SRN01)
      CHGVAR &SIREN '0'
    ENDDO

    DLYJOB DLY(60)
  ENDDO
ENDPGM
```

- `&LIGHT` and `&SIREN` remember what the program last did, so it only sends a command when something changes, and
  the gap between the on and off levels (80 / 75, 95 / 90) stops the alarm flickering at the edge.
- `ELSE CMD(IF ... THEN(DO))` is an "else if": the `DO` group belongs to the nested `IF`.
- Set the {{item cage_light_red}}'s Control to *Network only* (or *Redstone*, where the network's state adds to the
  signal), and the {{item siren_red}}'s trigger to *Network* or *Both*: an Alarm Strobe on *Redstone* takes no network
  commands ({{msg ELC2408}}). See [Cage Lights and Alarm Strobes](page:control/lights-and-sirens).

## A nightly report on the Line Printer {#ex-nightly}

Writes a short report to a spooled file every night and prints it, with the inventory report after it.

```elcl name=NIGHTRPT
/* NIGHTRPT - the night's report, printed on the Line Printer       */
PGM
  DCL VAR(&PCT)    TYPE(*DEC) LEN(5 1)
  DCL VAR(&USED)   TYPE(*INT)
  DCL VAR(&TOTAL)  TYPE(*INT)
  DCL VAR(&DEVS)   TYPE(*LIST)
  DCL VAR(&DEV)    TYPE(*CHAR) LEN(10)
  DCL VAR(&FAILED) TYPE(*LIST)

  RTVSTGSTS RTNPCT(&PCT)
  RTVLANES RTNUSED(&USED) RTNTOTAL(&TOTAL)
  PRTTXT TEXT('NIGHTLY REPORT') SPLF(NIGHTLY)
  PRTTXT TEXT('Storage used ' *BCAT %CHAR(&PCT) *TCAT '%') SPLF(NIGHTLY)
  PRTTXT TEXT('Lanes in use ' *BCAT %CHAR(&USED) *TCAT '/' *TCAT %CHAR(&TOTAL)) +
         SPLF(NIGHTLY)

  RTVDEVLST STATUS(*FAULT) RTNLST(&DEVS)
  PRTTXT TEXT('Devices in fault:' *BCAT %CHAR(%SIZE(&DEVS))) SPLF(NIGHTLY)
  FOREACH VAR(&DEV) IN(&DEVS)
    PRTTXT TEXT('  ' *CAT &DEV) SPLF(NIGHTLY)
  ENDFOR

  RTVCRFLOG STATUS(*FAILED) RTNLST(&FAILED)
  PRTTXT TEXT('Failed crafting jobs kept:' *BCAT %CHAR(%SIZE(&FAILED))) SPLF(NIGHTLY)

  PRTRPT RPT(*SPLF) SPLF(NIGHTLY)
  MONMSG MSGID(ELC1300) EXEC(DO)      /* no printer, offline, out of paper */
    SNDMSG MSG('NIGHTRPT: not printed, see Work with Output') TOUSR(*SYSOPR)
    RETURN
  ENDDO
  PRTRPT RPT(*INV)
ENDPGM
```

Schedule it for two in the morning on the game clock:

```elcl
ADDJOBSCDE JOB(NIGHTRPT) CMD(CALL PGM(ZAGLIB/NIGHTRPT)) FRQ(*DAILY) TIME(0200)
```

- `SPLF(NIGHTLY)` collects the lines in one spooled file of the job, which `PRTRPT RPT(*SPLF) SPLF(NIGHTLY)` prints.
  The spooled file stays in Work with Output whether it printed or not.
- `PRTRPT RPT(*INV)` prints every item with its hot and cold counts. Both go to the first online
  {{item line_printer}} (`DEV(*DFT)`); see [Printing](page:elcl/jobs-and-scheduling#printing).

## React to a device going offline {#ex-devwatch}

One trigger program for three events: it reports a device that goes offline or into fault, sounds the strobe, and
stops it once every device is back.

```elcl name=DEVWATCH
/* DEVWATCH - devices going offline or into fault                   */
/* Install:                                                          */
/*   ADDTRGEVT TRG(DEVOFF) EVENT(*DEVOFFLINE) PGM(ZAGLIB/DEVWATCH)   */
/*   ADDTRGEVT TRG(DEVFLT) EVENT(*DEVFAULT)   PGM(ZAGLIB/DEVWATCH)   */
/*   ADDTRGEVT TRG(DEVON)  EVENT(*DEVONLINE)  PGM(ZAGLIB/DEVWATCH)   */
PGM PARM(&EVENT &DATA)
  DCL VAR(&EVENT) TYPE(*CHAR) LEN(12)
  DCL VAR(&DATA)  TYPE(*CHAR) LEN(256)
  DCL VAR(&DEV)   TYPE(*CHAR) LEN(10)
  DCL VAR(&TYPE)  TYPE(*CHAR) LEN(16)
  DCL VAR(&DOWN)  TYPE(*LIST)
  DCL VAR(&FAULT) TYPE(*LIST)

  MONMSG MSGID(ELC1300 ELC2400)    /* the strobe missing or busy: carry on */

  CHGVAR VAR(&DEV) VALUE(%TRIM(&DATA))

  IF COND(&EVENT *EQ '*DEVONLINE') THEN(DO)
    RTVDEVLST STATUS(*OFFLINE) RTNLST(&DOWN)
    RTVDEVLST STATUS(*FAULT) RTNLST(&FAULT)
    IF COND(%SIZE(&DOWN) + %SIZE(&FAULT) *EQ 0) THEN(ENDSRN DEV(SRN01))
    RETURN
  ENDDO

  RTVDEVSTS DEV(&DEV) RTNTYPE(&TYPE)
  SNDMSG MSG(&DEV *BCAT '(' *TCAT %TRIM(&TYPE) *TCAT ') is' +
         *BCAT %SST(&EVENT 5 8)) TOUSR(*SYSOPR)
  STRSRN DEV(SRN01) SOUND(*BELL) MODE(*SLOW)
ENDPGM
```

- `&EVENT` is `LEN(12)` so that every event name fits whole; `%SST(&EVENT 5 8)` turns `*DEVOFFLINE` into `OFFLINE`.
- `&DATA` is the device's name for these three events. The other events' `&DATA` is in the
  [trigger table](page:elcl/jobs-and-scheduling#triggers).
- Each firing is a batch job, so the network needs a job host (a {{item compute_server}} or a Midrange System).
- Triggers are edge-triggered and debounced: a device that flaps fires at most once a second for each trigger.

## A Display Panel dashboard {#ex-dashboard}

Sets up a dashboard of three regions on a Display Panel screen and keeps its text up to date.

```elcl name=DSPDASH
/* DSPDASH - a dashboard on DSP01: storage gauge, energy graph, text */
/* Submit: SBMJOB CMD(CALL PGM(ZAGLIB/DSPDASH)) JOB(DSPDASH)          */
PGM
  DCL VAR(&W)     TYPE(*INT)
  DCL VAR(&H)     TYPE(*INT)
  DCL VAR(&HALF)  TYPE(*INT)
  DCL VAR(&USED)  TYPE(*INT)
  DCL VAR(&TOTAL) TYPE(*INT)
  DCL VAR(&SRC)   TYPE(*CHAR) LEN(10)
  DCL VAR(&BAD)   TYPE(*LIST)

  MONMSG MSGID(ELC1301 ELC1302) EXEC(RETURN)   /* screen gone or off: stop */

  /* The layout: A the left half, B top right, C bottom right        */
  RTVDSPSIZ DEV(DSP01) RTNPXW(&W) RTNPXH(&H)
  CHGVAR VAR(&HALF) VALUE(&W / 2)
  CLRDSP DEV(DSP01)
  CHGDSPRGN DEV(DSP01) RGN(A) X(0) Y(0) W(&HALF) H(&H)
  CHGDSPRGN DEV(DSP01) RGN(B) X(&HALF) Y(0) W(&W - &HALF) H(&H / 2)
  CHGDSPRGN DEV(DSP01) RGN(C) X(&HALF) Y(&H / 2) W(&W - &HALF) H(&H - &H / 2)
  SNDDSPWDG DEV(DSP01) RGN(A) WDG(*STORAGE) COLOR(*GREEN)
  SNDDSPGPH DEV(DSP01) RGN(B) STAT(*ENERGY) RANGE(*10M)
  SNDDSPWDG DEV(DSP01) RGN(C) WDG(*TEXT)

  /* The text lines, every 10 seconds                                 */
  DOWHILE COND(*TRUE)
    RTVLANES RTNUSED(&USED) RTNTOTAL(&TOTAL)
    RTVPWRSTS RTNSRC(&SRC)
    RTVDEVLST STATUS(*FAULT) RTNLST(&BAD)
    SNDDSPTXT DEV(DSP01) TEXT('LANES' *BCAT %CHAR(&USED) *TCAT '/' *TCAT +
              %CHAR(&TOTAL)) LINE(1) CLEAR(*YES)
    SNDDSPTXT DEV(DSP01) TEXT('POWER' *BCAT &SRC) LINE(2)
    IF COND(%SIZE(&BAD) *GT 0) THEN( +
      SNDDSPTXT DEV(DSP01) TEXT('FAULTS' *BCAT %CHAR(%SIZE(&BAD))) LINE(3) COLOR(*RED))
    ELSE CMD(SNDDSPTXT DEV(DSP01) TEXT('NO FAULTS') LINE(3) COLOR(*GREEN))
    DLYJOB DLY(10)
  ENDDO
ENDPGM
```

{{screenshot file="elcl/dspdash-dashboard.png" alt="A 4 by 2 Display Panel screen showing the DSPDASH dashboard" show="A 4 x 2 Display Panel screen on a wall running DSPDASH: the storage gauge on the left half, the energy graph top right, the three text lines (LANES, POWER, NO FAULTS in green) bottom right" angle="Straight on, centred, close enough that the screen fills most of the frame" size="1600x900"}}

- {{cmd RTVDSPSIZ}} gives the screen's canvas in pixels (32 a panel), so the layout fits any size of screen.
- `CLRDSP DEV(DSP01)` clears the lines and the regions first; with no regions, region `A` is the whole screen, so `A`
  is set up first and the others fit beside it. Regions that overlap or leave the screen are {{msg ELC1314}}.
- The storage gauge and the graph keep themselves up to date; only the text needs the loop. The `*TEXT` widget in `C`
  shows the screen's text lines.
- Display commands need no Firewall permission. See [Display Panels](page:control/display-panels) for the widgets,
  graphs and colours.

## A PLC reading its sensors {#ex-plc}

A program for a {{item plc}} that isn't on a network: a Presence Sensor in slot 1 opens a door, a Light Sensor in slot
2 turns a lamp on at night, and a retained counter counts visitors.

```elcl name=DOORPLC target=PLC_LOCAL
/* DOORPLC - door on the east face, lamp on top, visitors counted    */
/* Slot 1: Presence Sensor (players, radius 4); slot 2: Light Sensor */
PGM
  DCL VAR(&NEAR)   TYPE(*DEC) LEN(5 0)
  DCL VAR(&STS)    TYPE(*CHAR) LEN(10)
  DCL VAR(&WHO)    TYPE(*CHAR) LEN(32)
  DCL VAR(&LUX)    TYPE(*DEC) LEN(5 0)
  DCL VAR(&TIME)   TYPE(*CHAR) LEN(10)
  DCL VAR(&OPEN)   TYPE(*LGL)
  DCL VAR(&VISITS) TYPE(*INT) RETAIN(*YES)   /* kept through STOP, power loss, reloads */

  RTVSNSVAL MODULE(1) TYPE(*PRESENCE) RTNVAL(&NEAR) RTNSTS(&STS) RTNAUX(&WHO)
  IF COND(&STS *NE '*OK') THEN(CHGVAR &NEAR 0)

  IF COND(&NEAR *GT 0 *AND *NOT &OPEN) THEN(DO)
    CHGVAR &VISITS (&VISITS + 1)
    SNDPGMMSG MSG('Visitor' *BCAT %CHAR(&VISITS) *TCAT ':' *BCAT &WHO)
  ENDDO
  CHGVAR VAR(&OPEN) VALUE(&NEAR *GT 0)

  IF COND(&OPEN) THEN(CHGRSOUT DEV(*SELF) SIDE(*EAST) LVL(15))
  ELSE CMD(CHGRSOUT DEV(*SELF) SIDE(*EAST) LVL(0))

  RTVSNSVAL MODULE(2) RTNVAL(&LUX) RTNAUX(&TIME)
  IF COND(&TIME *EQ '*NIGHT') THEN(CHGRSOUT DEV(*SELF) SIDE(*UP) LVL(15))
  ELSE CMD(CHGRSOUT DEV(*SELF) SIDE(*UP) LVL(0))
ENDPGM
```

- The PLC runs the program over and over, a scan at a time. Variables keep their values from one scan to the next (a
  `VALUE` is set only when the program starts), so `&OPEN` remembers whether the door was open on the last scan.
- `RETAIN(*YES)` keeps `&VISITS` through STOP and RUN, power loss and world reloads (PLCSTS option 6 shows it).
- With `RTNSTS` given, a missing module or one with nothing to watch comes back as `*NOMODULE` or `*NOTARGET` instead
  of an escape message.
- Off a network a PLC runs only the language, its own faces (`DEV(*SELF)`), its modules and the delays: anything else
  is {{msg ELC1502}}. `SNDPGMMSG` goes to the PLC's own log. See {{page elcl/control-and-plcs}}.

## Report stalled crafting jobs {#ex-stall}

A trigger program for `*CRFSTALL`, which fires when a crafting job makes no progress for a while. It tells the
operator what the job is stuck on.

```elcl name=STALLMSG
/* STALLMSG - a crafting job has stalled                              */
/* Install: ADDTRGEVT TRG(STALL) EVENT(*CRFSTALL) PGM(ZAGLIB/STALLMSG) */
PGM PARM(&EVENT &DATA)
  DCL VAR(&EVENT) TYPE(*CHAR) LEN(12)
  DCL VAR(&DATA)  TYPE(*CHAR) LEN(256)
  DCL VAR(&JOB)   TYPE(*CHAR) LEN(10)
  DCL VAR(&REST)  TYPE(*CHAR) LEN(256)
  DCL VAR(&AT)    TYPE(*INT)
  DCL VAR(&PCT)   TYPE(*DEC) LEN(5 1)

  /* &DATA: C0042 IRON_INGOT machine output full                      */
  CHGVAR VAR(&AT)   VALUE(%SCAN(' ' &DATA))
  CHGVAR VAR(&JOB)  VALUE(%SST(&DATA 1 &AT - 1))
  CHGVAR VAR(&REST) VALUE(%SST(&DATA &AT + 1 %LEN(&DATA) - &AT))

  RTVCRFSTS CRFJOB(&JOB) RTNPCT(&PCT)
  MONMSG MSGID(ELC1400)              /* ended meanwhile */
  SNDMSG MSG('Crafting job' *BCAT &JOB *BCAT 'stalled at' *BCAT %CHAR(&PCT) +
         *TCAT '%:' *BCAT &REST) TOUSR(*SYSOPR)
ENDPGM
```

- `ITEM()` on {{cmd ADDTRGEVT}} limits it to jobs crafting one item: `ITEM(LOGIC_DIE)`.
- How long a job must go without progress is the server's {{config autocrafting.jobStallSeconds default}} seconds.
- See [Crafting jobs](page:crafting/jobs) for what the reasons mean.
