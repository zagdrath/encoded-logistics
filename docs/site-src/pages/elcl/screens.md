---
title: Screens
section: elcl
order: 20
summary: Every Terminal OS screen, how to open it, what it shows, and its options and keys.
legacy: [screens]
---
Every screen can be reached from the Main Menu, from another screen's option, or by typing its command on any command
line. The screens below are shown as the mod draws them; most use sample data, and those marked "as it opens" are
drawn with no data. Each screen's F1 help (see [help windows](page:elcl/using-the-terminal#help-windows)) says the
same as its section here, in short.

## All screens at a glance {#screen-list legacy="screen-list"}

The screens a command opens. Each command's page in the [command reference](page:elcl/commands/index) has its
parameters.

{{table screens}}

Also opened by a command: {{cmd WRKINV}} (Work with Inventory), `GO MAIN` (the Main Menu) and `GO HELP` (the current
screen's help window). {{cmd SIGNOFF}} signs off.

These open from keys, from options on other screens, or by themselves:

| Screen | Title | Opens from |
|---|---|---|
| `SIGNON` | Sign On | Opening a desk on a network with a Firewall (SECLVL 30); {{cmd SIGNOFF}} |
| `CMDENT` | Command Entry | F9 on any screen; opens by itself when a command has output |
| `PROMPT` | *Description (COMMAND)* | F4 on a command line |
| `MOREKEYS` | More Keys and Settings | F24 |
| (window) | Select Value - *label* | F4 on a prompter field |
| `DEPOSIT` | Deposit Items | Work with Inventory F6 |
| `WITHDRAW` | Withdraw Item (WITHDRAW) | Work with Inventory 1 |
| `CRAFT` | Craft Item (CRAFT) | Work with Inventory 7; crafting job history 7=Craft again |
| `DSPITM` | Display Item Details | Work with Inventory 5 |
| `DSPDEV` | Display Device | Work with Devices 5 |
| `DSPJOB` | Display Job / Display Ended Job | Work with Jobs 5 (active jobs / history) |
| `DSPJOBSTP` | Display Job Steps | Work with Jobs 8 |
| `DSPJOB` | Display job status / definition attributes / call stack | Work with Job 1, 2, 11 (30 for all) |
| `DSPLIB` | Display Library | Work with Libraries 5 |
| `DSPSPLF` | Display Spooled File | Work with Output 5 |
| `DSPMCH` | Display Machine | Work with Machines 5 |
| `DSPSCH` | Display Schematic | Work with Schematics 5 |
| `DSPHW` | Display Hardware | Work with Hardware 5 |
| `DSPIXMBR` | Exchange member | Work with Exchange 5 |
| (window) | Display System Value | Work with System Values 5 |

The machines have green screens of their own, opened by using the block. They work like the desk's screens (keys,
help, command line where they have one):

| Screen | Title | Opens from |
|---|---|---|
| `MRCTL` / `IMCTL` | Midrange Control Panel / Integrated System Control Panel | Using a {{item midrange_system}} or an {{item integrated_midrange}} (see {{page crafting/midrange}}) |
| `CARDPUNCH` | Card Punch - Punch a Card | Using a {{item keypunch}} |
| `CARDRDR` | Card Reader | Using a {{item card_reader}} |
| `PRINTER` | Line Printer | Using a {{item line_printer}} |
| `DSKDRV` | Disk Drive | Using a {{item disk_drive}} |
| `TAPDRV` | Tape Drive | Using a {{item tape_drive}} |
| `PLCSTS`, `PLCIO`, `PLCMOD`, `PLCRTN` | PLC Status, I/O Table, Modules, Retained Variables | Using a {{item plc}} (see {{page control/plc}}) |

The Terminal Desk and the Integrated Midrange System's console both run the full Terminal OS.

## Main Menu {#main-menu legacy="main-menu"}

The desk opens on the Main Menu (after [Sign On](page:elcl/index#sign-on) where that's needed).

{{layout 02_main_menu}}

Type an option's number on the command line and press Enter, or click an option (double-click to go). Any command can
be typed there too. Each option runs its screen's command, just as typing that command would. A number that isn't on
the menu answers *Option 14 is not on this menu.* The menu has two pages (PageDown / PageUp or the mouse wheel;
*More...* and *Bottom* say where you are): options 1–8 on the first, 9–13 and Sign Off on the second. Any option's
number works from either page. Its F1 help lists all fourteen options, then how to use the menu.

{{table main-menu}}

## Sign On {#scr-signon legacy="scr-signon"}

Described under [Signing on](page:elcl/index#sign-on).

## Command Entry {#scr-cmdent legacy="scr-cmdent"}

```term
 CMDENT                          Command Entry                 System:  ELNET01
                                                                Day 2  07:14:22
 All previous commands and messages:
 > CRTLIB LIB(ZAGLIB) TEXT('Zagdrath automation scripts')
   ELC0210  Library ZAGLIB created.
 > SBMJOB CMD(CALL PGM(ZAGLIB/RESTOCK)) JOB(RESTOCK)
   ELC0304  Job 000123/ZAGDRATH/RESTOCK submitted to job queue on MIDRANGE01.
 > CHGRSOUT DEV(CTLIF01) SIDE(*UP) LVL(20)
   ELC0004  Value 20 is outside the allowed range.











 Type command, press Enter.
 ===>


                                                                            MW
 F1=Help F3=Exit F4=Prompt F9=Retrieve F12=Cancel F13=Clear F24=More keys
```

The commands typed at this desk and what they said, newest at the bottom (the last 500 lines; Page Up rolls back).
Type any ELCL command and press Enter: it runs in your interactive job and its messages show here (ID and text), with
the values its `RTN` parameters return. F4 prompts the command on the command line, Tab completes its last word, F9
brings back earlier commands, F13 (<kbd>Shift</kbd>+<kbd>F1</kbd>) clears the list. Typing `clear` clears it too
(*Command history cleared.*). The command line has three rows for long commands. See
[the command line](page:elcl/using-the-terminal#command-line).

## Inventory, crafting jobs, devices and network status {#scr-desk legacy="scr-desk"}

These are the desk's own screens, Main Menu options 1–4. They work on the network's storage and machines rather than
on the OS. They use the desk's own authority check: without the Firewall permission they need they say *Not
authorised to ...* on the message line.

### Work with Inventory (WRKINV)

The network's items, fluids and gases, hot (on drives) and cold (on tape; items only). Columns: Opt, Item, Type
(`ITEM`, `FLUID`, `PRES`), Quantity (fluids and gases in B / mB), Location (*Hot*, *Cold*, *Hot+Cold*). After what's
stored, the list has what the network can craft but has none of (a Processing Schematic's or a diskette recipe's
output), dim, with Quantity 0 and Location *Craftable*: 7=Craft starts it there, and 1=Withdraw answers *None of Chest
stored: 7=Craft makes some.* *Position to* takes the starting characters of a name (blank or `*ALL`: all); *Type*
keeps one type (`*ITEM`, `*FLUID`, `*PRES`; `*ALL`, the default: all). The `FILTER()` parameter is accepted on a
command line but the screen opens unfiltered; use *Position to*. Clicking a row types 5.

- **1=Withdraw** opens the Withdraw Item screen: Item, Quantity 1–999999, Destination `*DRAWER` (the desk drawer, the
  default) or `*INV` (your inventory); it shows what's on hand and how long a tape recall would take. Withdrawing a
  fluid or gas (its Item reads `FLUID minecraft:water`, its Quantity in mB, a bucket to start with) fills empty
  buckets, tanks or gas containers at the destination.
- **5=Display details**: item id, name, hot and cold counts, recall time, whether it's craftable, tags.
- **7=Craft** opens the Craft Item screen: Item, Quantity, Scheduler (`*AUTO`, a number or a name), Destination
  (`*NETWORK`, `*DRAWER`, `*INV`) and a one-line plan: steps, missing, recall time.
- **F6** opens *Deposit Items*: what's in your inventory (the hotbar's slots, then the rest); 1=Deposit puts that stack
  into the network, F6 everything outside your hotbar (it needs the Firewall's insert permission). What doesn't fit
  stays with you.
- **F11** sorts by name, quantity or mod.

At the Integrated Midrange System's console, which has no drawer, Withdraw goes to your inventory and Craft to the
network. Keys: *F1=Help F3=Exit F5=Refresh F6=Deposit F9=Command Entry F11=Sort F12=Cancel*. See
{{page terminals/terminal-desk}} for the drawer.

### Work with Jobs (WRKCRFJOB)

{{screen WRKCRFJOB}}

The network's crafting jobs (separate from the OS's script jobs on [Work with Active Jobs](#scr-wrkactjob)).
Columns: Opt, Job, Item, Qty, Status (Active, Waiting, Recall), Progress (bar and %), Scheduler. It refreshes every two
seconds. Clicking a row types 5.

- **4=Cancel**: the message line asks you to confirm; press Enter again.
- **5=Display**: the job (Display Job).
- **8=Display steps**: Display Job Steps, a tree of the job's steps with each one's state and done/total, and a
  stalled step's reason in bright under it.

**F10=History** switches to the jobs that ended, the crafting job history (see {{page crafting/jobs}}), and F10 again
back to the active ones. The history's title is *Crafting Job History*; *Position to* at the top keeps the jobs whose
item starts with what's typed. Columns: Opt, Job, Item, Qty (asked for), Status (*Done*, *Failed* bright,
*Cancelled*), Ended (game day and time), Duration, Requested by (the player's user, or the ELCL job's name). Newest
first; **F11** sorts by ended time, item or status. Options: **4=Remove** (confirmed with the others; it takes the
Firewall's craft permission, as cancelling does), **5=Display** (Display Ended Job: the item and quantity asked for
and made, status and failure reason, who asked, the scheduler that ran it, when it started and ended, how long it ran,
and the ingredients it used up from storage), **7=Craft again** (the Craft Item screen with the same item and
quantity, while the network can still craft it).

### Work with Devices (WRKDEV)

{{screen WRKDEV}}

The network as a topology, each device once. First each Server Rack (Type *Rack*, its lanes on the network), with the
devices in it listed under it on tree lines, top unit first (`├─U39-42`, `└─U1-2`). Then everything that isn't in a
rack: a controller structure, Drive Bays, terminals, parts on cables (*Part*), Control Interfaces and other devices,
Access Points, Wireless Bridges and Wireless Ports (their Status names the Wireless Controller they work through,
*Online WLC01*, or what's wrong: *No controller*, *No access points*, *Over capacity*). Cables and a rack's other
blocks aren't listed. Columns: Opt, Type, Device, Location, Lanes, Status (Online, Offline dim, a fault or what it's
busy with bright). The Device column starts with the device's ELCL name (`UPS01`, `INGRESS02`, `CTLIF01`) when it has
one: these are the names programs use. Clicking a row types 5.

- **2=Change**: rename it (a window for the new name, which runs {{cmd RNMDEV}}; Firewall build permission;
  {{msg ELC1309}} when done, {{msg ELC1308}} if the name is in use).
- **5=Display**: device, name, location, units, lanes, status (and for wireless gear, its clients, controller, uplink,
  devices, lanes, inventory and what it moved).
- **8=Locate**: a box blinks around it in the world for ten seconds (a rack device's own units, or its block).

### Display Network Status (DSPNETSTS)

Storage (hot and cold; fluid and pressurized drives on their own lines when there are any, in buckets), energy (stored,
the part in Energy Storage Drives when there are any, in / out), lanes used, crafting (active jobs) and devices online,
with gauges. The bytes the system's source members and files take count as used hot storage here (as they do for
{{cmd RTVSTGSTS}} and the `*STGFULL` trigger). It refreshes every two seconds; Enter returns. Keys: *F1=Help F3=Exit
F5=Refresh F12=Cancel*.

## Work with Libraries (WRKLIB) {#scr-wrklib legacy="scr-wrklib"}

{{layout 03_wrklib}}

The libraries on this system. Columns: Opt, Library, Type (`*PROD`, `*TEST`, `*SYS`), Text. Clicking a row types 12.

| Option | What it does |
|---|---|
| 2=Change | A window with the library's Text and Authority (`*USE` lets others read, `*CHANGE` lets them change members). Runs {{cmd CHGLIB}}. |
| 4=Delete | Asks for confirmation (all rows given 4 are confirmed together), then runs {{cmd DLTLIB}}. Only the owner can delete; `ELSYS` and `ELGPL` can't be deleted. |
| 5=Display | Display Library: Library, Type, Text, Owner, Public authority, Members, Size (characters), Created. |
| 12=Work with members | Opens Work with Members for the library. |

F6 prompts {{cmd CRTLIB}}. ELSYS holds the samples and the system files and is read-only; ELGPL is for everyone. See
{{page elcl/libraries-and-editor}}.

## Work with Members (WRKMBR) {#scr-wrkmbr legacy="scr-wrkmbr"}

{{layout 04_wrkmbr}}

The source members of one library, then its files. `WRKMBR` with no library shows your current library. Columns: Opt,
Member, Type (`ELCLP` a program's source, `PF` a file's definition, `*FILE` a file), Chg (`*` when the source changed
after its program, or file, was made), Text. Clicking a row types 2.

| Option | What it does |
|---|---|
| 2=Edit | Opens the [source editor](page:elcl/libraries-and-editor#editor). |
| 3=Copy | A window asking for To library and To member; runs {{cmd CPYMBR}}. Copying from ELSYS suggests your current library. |
| 4=Delete | Confirmed, then {{cmd DLTMBR}}. |
| 5=Display | The editor, read-only (title *Display Member*; only FIND, TOP and BOTTOM on its command line). |
| 6=Print | The numbered source goes to a spooled file named after the member: *Member LIB/NAME printed: see Work with Output.* |
| 7=Rename | A window asking for the new name; runs {{cmd RNMMBR}}. |
| 9=Submit | Opens {{cmd SBMJOB}}'s prompter filled in with `CMD(CALL PGM(LIB/MEMBER)) JOB(MEMBER)`, so you can add parameters to the `CALL` (F4 on the Command field prompts it) or choose a host before pressing Enter, which submits the batch job. |
| 14=Compile | An `ELCLP` member: runs `CRTELPGM PGM(LIB/MEMBER)` ({{msg ELC0218}} or {{msg ELC0206}}); the listing goes to Work with Output. A `PF` member: {{cmd CRTPF}}, or {{cmd CHGPF}} (keeping the records) when its file is there already. |
| 16=Call | Opens {{cmd CALL}}'s prompter filled in with `PGM(LIB/MEMBER)`; Enter runs the program in your interactive job. |

On a file (Type `*FILE`) the options are Work with Files': 2=Change data, 4=Delete, 5=Display data, 8=Display
description (see {{page elcl/database-files}}).

{{layout 22_wrkmbr_files caption="A library with a file's definition (PF) and the file made from it (*FILE)."}}

9 and 16 need the member's program, compiled since the source last changed. A member with no program says *Program
LIB/MEMBER is not compiled.* on the message line, and one marked Chg `*` says *Member LIB/MEMBER changed since it was
compiled.*; either way a *Compile First* window offers to compile it: Enter runs 14=Compile and, once {{msg ELC0218}}
comes back, opens the prompter as if the program had been there (a compile that fails, {{msg ELC0206}}, stops there);
F12 leaves it. On a `PF` member they answer *Option 9 not valid for this item.* (there's nothing to run).

In `ELSYS` only 3, 5, 6, 8, 9, 14 (into a library you can change) and 16 work; 2, 4 and 7 give {{msg ELC0205}}
(`Library ELSYS is read-only.`). F6 prompts {{cmd CRTMBR}} with the library filled in. F11 sorts by name, by
changed-since-compile, or by text.

## Work with Active Jobs (WRKACTJOB) {#scr-wrkactjob legacy="scr-wrkactjob"}

{{layout 08_wrkactjob}}

The system's script jobs: the interactive job of each open terminal session and the batch jobs, queued, held or
running on job hosts. Row 2: the budget all jobs used on the last tick (each job's % added up, at most 100%), the time
since the screen opened (F5 restarts it), jobs `*ACTIVE`, and hosts running a batch job / all the system's job hosts.
Columns: Job, User, Type (`INT` or `BCH`), Host (the desk, `ELDESK01`; the job host a batch job runs on, `CMPSRV01` or
`MIDRANGE01`; or the `HOST()` it asked for while it waits, `*ANY`), Status (`*ACTIVE` bright, `*WAIT`, `*JOBQ`,
`*HELD`), Budget (the share of its per-tick instruction budget it used on the last tick; blank for `*JOBQ` and
`*HELD`). A job in a {{cmd DLYJOB}}, a tape recall or a craft wait shows `*WAIT` and uses none. There's no automatic
refresh: press F5.

| Option | What it does |
|---|---|
| 2=Change | A window: Job priority (1–9) and Log commands (`*NO`, `*YES`); runs {{cmd CHGJOB}}. |
| 3=Hold | {{cmd HLDJOB}}. |
| 4=End | Confirmed; Enter ends it controlled (`ENDJOB OPTION(*CNTRLD)`), F11 in the confirmation ends it immediately (`*IMMED`). |
| 5=Work with | Opens [Work with Job](#scr-wrkjob). (The default when you click a row.) |
| 6=Release | {{cmd RLSJOB}}. |
| 8=Spooled files | Work with Output for that job. |

F11 sorts by job, user, status, or back to the system's order. Holding, releasing, ending or changing another user's
job takes `*SECOFR`, or full authority (no Firewall, or `SECLVL 10`); otherwise {{msg ELC0401}} names `*JOBCTL`. See
{{page elcl/jobs-and-scheduling}}.

## Work with Job (WRKJOB) {#scr-wrkjob legacy="scr-wrkjob"}

{{layout 09_wrkjob}}

A job's menu. Rows 2–3 show Job, User, Number, Host, Status and Budget. Type a number on the command line (or click
it):

| Option | Text | Shows |
|---|---|---|
| 1 | Display job status attributes | Job, User, Number, Status, Host, Budget used |
| 2 | Display job definition attributes | Job type (`*INTER` / `*BATCH`), Job priority, Log commands, User |
| 4 | Work with spooled files | Work with Output for the job |
| 10 | Display job log | [Display Job Log](#scr-dspjoblog) |
| 11 | Display call stack | The programs it's running, newest first: each as `LIB/NAME  line 12`, with how many subroutines deep it is (a batch job's command runs as `QSYS/QCMD`). An interactive job with no program running shows `QCMD Command processor`. |
| 30 | All of the above | Status, definition, job log and call stack one after another (Enter or F12 walks back through them) |

`WRKJOB` with no job (or `JOB(*)`) shows your own interactive job. An ended job whose log is still kept (the last
`LOGRTN`) can be named too: its status is `*ENDED`.

## Display Job Log (DSPJOBLOG) {#scr-dspjoblog legacy="scr-dspjoblog"}

{{layout 09b_dspjoblog}}

A job's log, newest at the bottom (it opens there): the commands it logged as `>> command` (bright), and every message
indented with its ID and text. F10 opens *Detailed Messages*: each message's ID, severity, sender (`From`) and time,
then its text. Page Up / Page Down roll the log; Enter returns. `JOB(*)` (the default) is your own interactive job.
Ended jobs' logs stay on the system for the last `LOGRTN` jobs; show one by its number or full ID (see
{{page elcl/jobs-and-scheduling}}).

## Work with Job Schedule Entries (WRKJOBSCDE) {#scr-wrkjobscde legacy="scr-wrkjobscde"}

{{layout 10_wrkjobscde}}

Jobs submitted on a schedule (game clock times, real-time intervals); Main Menu option 11. Columns: Job, Status
(`*SCD` scheduled, `*HLD` held), Frequency (`*ONCE`, `*DAILY`, `*INTERVAL`), Next run (game day and time), Command
(cut to fit). Clicking a row types 2.

- **2=Change** opens {{cmd ADDJOBSCDE}}'s prompter with the entry's values; on Enter the entry is removed and added
  again.
- **3=Hold**: on a held entry, release; runs {{cmd HLDJOBSCDE}} / {{cmd RLSJOBSCDE}}.
- **4=Remove**: confirmed, {{cmd RMVJOBSCDE}}.
- **10=Submit now** runs {{cmd SBMJOB}} with its command and the entry's name; the entry and its next run stay as they
  are.

F6 prompts {{cmd ADDJOBSCDE}}. Entries fire by themselves at their next run while the network is loaded; see
{{page elcl/jobs-and-scheduling}}. Changing, holding, releasing or removing another user's entry takes `*SECOFR` or
full authority ({{msg ELC0401}} naming `*JOBCTL`).

## Work with Trigger Events (WRKTRGEVT) {#scr-wrktrgevt legacy="scr-wrktrgevt"}

{{layout 11_wrktrgevt}}

Programs run when something happens in the network; Main Menu option 12. Columns: Trigger, Event, Item/Device (the
item, else the device, else `*ANY`), Value, Program, Status (`*ACTIVE`, `*HELD`). Clicking a row types 2.

- **2=Change** opens {{cmd ADDTRGEVT}}'s prompter with its values; the trigger is replaced.
- **3=Hold** toggles: {{cmd HLDTRGEVT}} / {{cmd RLSTRGEVT}}.
- **4=Remove**: confirmed, {{cmd RMVTRGEVT}}.

F6 prompts {{cmd ADDTRGEVT}}. An active trigger runs its program as a batch job whenever its event happens; see
{{page elcl/jobs-and-scheduling}} for the events. Changing, holding or removing another user's trigger takes
`*SECOFR` or full authority.

## Display Messages (DSPMSG) {#scr-dspmsg legacy="scr-dspmsg"}

{{layout 12_dspmsg}}

A message queue, newest first: what your jobs and other users sent you. Row 2 shows *Queue* (whose queue it is) and
*Program* `*DSPMSG`. Columns: Sev, From (job or user), Sent (game day and time), Message (cut; 5 shows all of it).
Unread messages are bright; showing them marks them read and `MW` goes from the message line. Clicking a row types 5.

- **4=Remove** removes it straight away, without asking.
- **5=Display details**: a window with the whole text, then ID, severity, sender and time.
- **F11** removes every message (confirmed).

`DSPMSG` shows your own queue. `DSPMSG USR(*SYSOPR)` shows the operator queue (`QSYSOPR`, where
`SNDMSG TOUSR(*SYSOPR)` and the system's own notices go) and `DSPMSG USR(name)` another user's; both need `*SECOFR` or
full authority (no Firewall, or `SECLVL 10`), otherwise {{msg ELC0401}}.

## Work with Output (WRKSPLF) {#scr-wrksplf legacy="scr-wrksplf"}

{{screen WRKSPLF}}

Your spooled output: compile listings (programs' and files'), printed members, {{cmd PRTTXT}} text and
`RUNQRY OUTPUT(*PRINT)` reports. Newest first. Columns: File, Job (`nnnnnn/JOBNAME`), User, Pages, Status (`*RDY`,
`*PRT`, `*HLD`), Created. Clicking a row types 5.

- **4=Delete**: confirmed.
- **5=Display**: [Display Spooled File](#scr-dspsplf).
- **6=Print**: to the first online Line Printer, {{msg ELC1307}} (`Spooled file &1 printed on &2.`). With no printer
  it's {{msg ELC1301}}, with one offline {{msg ELC1302}}, and out of paper {{msg ELC1306}}. See
  {{page elcl/jobs-and-scheduling}}.

F11 sorts by created, file or status. `WRKSPLF JOB(name)`, Work with Active Jobs 8 and Work with Job 4 show only that
job's files.

## Display Spooled File (DSPSPLF) {#scr-dspsplf legacy="scr-dspsplf"}

{{layout 07_compile_listing caption="A compile listing in Display Spooled File."}}

A spooled file a page at a time (60 lines to a page). Row 2: File and *Page/Line*. *Control* takes `+n` or `-n` lines,
`T` (top), `B` (bottom) or a page number. *Columns* shows the 78 columns in view. *Find* looks for text (any case) from
the line after the top one, wrapping round. Row 5 is a column ruler. F19 / F20 shift 40 columns left / right (a
compile listing's change dates are past column 80). Page Up / Page Down roll it. The
[compile listing](page:elcl/libraries-and-editor#listing) is shown on this screen.

## Work with System Values (WRKSYSVAL) {#scr-wrksysval legacy="scr-wrksysval"}

{{layout 14_wrksysval}}

The system values (Main Menu option 13): `SYSNAME`, `DATFMT`, `SECLVL`, `QMAXJOB`, `LOGRTN`, `CRFLOGRTN`,
`PHOSPHOR`. Columns: System value, Value (bright), Description. Clicking a row types 5.

- **2=Change**: a window with the new value and what it takes; runs {{cmd CHGSYSVAL}}. It needs `*SECOFR`, or a
  network with no Firewall.
- **5=Display**: value, default, description and allowed values.

A changed `PHOSPHOR` or `SYSNAME` takes effect on this desk at once. See {{page elcl/jobs-and-scheduling}} for each
value.

## Work with Machines (WRKMCH) {#scr-wrkmch legacy="scr-wrkmch"}

{{screen WRKMCH}}

The network's Arcforge machines (Main Menu option 9): each machine with a linked {{item small_wireless_bridge}} on it.
Columns: Machine (its device name, `ARCCRU01`), Type (the machine's own name), Status (`*IDLE`, `*RUNNING`, `*NOPOWER`,
`*NOINPUT`, `*BLOCKED`, `*DISABLED`, `*NOTFORMED`, `*FAULT`, or `*OFFLINE` while the bridge isn't on the network or the machine is
gone; bright when it needs attention), Prog (progress through the current operation), Energy (FE stored / capacity)
and Rate (operations a minute over the last minute). Clicking a row types 5.

- **2=Change**: a window with the redstone mode, auto-eject, power from network and Gateway; runs {{cmd CHGMCHCFG}}
  with only the fields you changed. A setting the machine doesn't have shows `*NONE`.
- **5=Display**: *Display Machine*, with type and location, status in the machine's words, controller, progress and
  time left, what it's making, energy, heat, each tank, operations, rate, items and fluid produced and consumed,
  running time, and every setting including each side's mode or the number of ports. Enter returns; Page Up / Page
  Down roll it.
- **7=Enable/Disable**: {{cmd CHGMCHSTS}} to the other state (Arcforge's remote on/off switch).
- **8=Sides**: a single-block machine's sides: a window with each side's mode, Top, Bottom, Left, Right, Back and
  Front from the machine's front; runs `CHGMCHCFG SIDE() SIDEMODE()` for each side you changed. A multiblock says it
  has none.

In the 2=Change and 8=Sides windows, F4 on a field steps through its values. Viewing needs the Firewall's view
permission; 2, 7 and 8 need build. See {{page integrations/arcforge}}.

## Work with Schematics (WRKSCH) {#scr-wrksch}

{{screen WRKSCH}}

`WRKSCH`, or `WRKSCH HOLDER(name)` for one holder, lists the schematics in the network's schematic holders
(Fabricators, Gateways, Fabrication Servers and Midrange Systems). Columns: Holder, Output, Qty, Kind (`PROC` or
`CRAFT`), R/O, Location; a holder with none shows *(no schematics)*. A Midrange System's are its diskettes' recipes,
read-only (R/O). Clicking a row types 5.

- **4=Remove** moves the schematic into this desk's drawer (confirmed; build permission; not from a read-only holder
  or into a full drawer).
- **5=Display** opens *Display Schematic*: the holder, its location, whether it's read-only, the kind, what it makes
  and what it takes.
- **8=Locate** outlines the holder in the world and puts its coordinates on the message line.

See {{page crafting/schematics}} and {{page crafting/fabricators-and-gateways}}.

## Display Route Table and Display Neighbours (DSPROUTE, DSPNBR) {#scr-dsproute}

`DSPROUTE RTR(RTR01)` shows a Router's route table; `DSPNBR RTR(RTR01)` a dynamic Router's neighbours. Both refresh
every two seconds, have no options, and Enter returns. F11 on the route table shows the neighbours; F10 on the
neighbours shows the route table.

{{layout 23_dsproute}}

The route table has every network the Router can reach: the next hop on the best path (`direct`, a network, or
`IX:name` for an exchange), the path's cost and hops, where the entry came from (`*STATIC`: its own Link Card links,
which always win; `*CONNECTED`: a neighbour's network; `*DYNAMIC`: learned through other Routers) and how long it's
been so.

{{layout 24_dspnbr}}

The neighbours are the Routers on networks this one links to (or that link to it, or that it peers with at an
Exchange Switch): how each is reached, its state (`*INIT` coming up, `*FULL` in sync, `*DOWN` missed its hellos), the
hop's cost and any override ({{cmd CHGNBR}}), and how long it's been up. See {{page networking/routers}}.

## Work with ACLs (WRKACL) {#scr-wrkacl}

{{layout 25_wrkacl}}

`WRKACL RTR(RTR01) ACL(NOFUEL)` lists a Router ACL's rules in the order they're looked at: the first that matches a
resource (an item, a `#tag`, `*ITEM`, `*FLUID`, `*PRES`, `*ENERGY` or `*ANY`) going its way (`*IN` to the Router's
network, `*OUT` from it, `*BOTH`) permits or denies it, up to its rate (a minute) or quota (a game day). What no rule
matches gets the default, the `*DFT` row (which takes no options). Hits count what each rule matched. Clicking a row
types 2.

- **2=Change**: {{cmd CHGACLE}}'s prompter with the rule's values.
- **4=Remove**: confirmed, {{cmd RMVACLE}}.
- **6=Move up**, **7=Move down**: the rule's place in the order.

F6 adds a rule ({{cmd ADDACLE}}). Changing rules needs build permission. See {{page networking/routers}}.

## Work with Exchange (WRKIX) {#scr-wrkix}

{{layout 26_wrkix}}

`WRKIX IX(MAIN)` lists an {{item exchange_switch}}'s members: the Routers that joined it with a Cross-Connect, their
ports, whether they're up, how they peer (`*RS` through the route server, `*BILAT` with chosen peers), the routes they
learn through it and their traffic. Join requests show `*REQUEST`.

- **4=Remove member**: confirmed, {{cmd RMVIXMBR}}.
- **5=Display**: the member's details (*Exchange member*).
- **8=Approve request**: {{cmd APVIXMBR}}.

F10 shows the peering sessions and F11 only the requests (the same key again returns to the members). Approving and
removing are the exchange owner's, with build permission ({{msg ELC2612}} otherwise). See
{{page networking/exchange-and-directory}}.

## Work with Directory (WRKDIR) {#scr-wrkdir}

{{layout 27_wrkdir}}

`WRKDIR`, or `WRKDIR DIR(name)` for a particular Directory Server, lists the network's devices by their qualified
names (`SYSNAME/DEVICE`). Published ones can be reached from other networks: any command that takes a device takes the
qualified name, when a Router on the caller's network has a Control policy to this network and the caller has the
Firewall's permission here. Under them are the latest lookups made from this network. Clicking a row types 5.

- **2=Publish**, **3=Unpublish**: {{cmd CHGDIRENT}} (build permission).
- **5=Display**: {{cmd RTVDIRENT}}, its answer on the message line.

F10 shows a page of lookups only (again: back). See {{page networking/exchange-and-directory}}.

## Work with Hardware (WRKHW) {#scr-wrkhw}

{{layout 28_wrkhw}}

`WRKHW`, or `WRKHW DEV(CSW01)` for one chassis, lists every chassis on the network (Chassis Switches, Blade
Enclosures, Optical Transport Shelves) with its cards, blades, power supplies and modules: one row a filled bay (or a
failed part), with its status and a detail. Row 2 counts the chassis, cards and faults.

- **5=Display** opens *Display Hardware*: the part, as its popup on the chassis shows it.
- **8=Locate** outlines its faceplate in the world and shows its coordinates.

F11 shows the empty bays too (again: back). See {{page racks/chassis}}.

## Display Optical Status (DSPOPT) {#scr-dspopt}

{{layout 29_dspopt}}

`DSPOPT` shows the network's optical gear (multiplexers, amplifiers, transport shelves) and their spans: path, length,
lanes delivered, lanes lost, signal quality (100% less 4% a lane lost) and 1+1 protection, with the last protection
switch. F10 shows the channels (F10 again: the spans). It refreshes every two seconds, Page Up / Page Down roll it, and
Enter returns. See {{page networking/optical}}.

## More Keys and Settings (F24) {#scr-morekeys legacy="scr-morekeys"}

Lists every key the screens take and has the *Phosphor* field: `Green`, `Amber`, `White` or `*SYSVAL` (the word, its
start such as `am`, or 1–4; F4 there shows `1=Green  2=Amber  3=White  4=*SYSVAL (the system value)`). Enter saves the
choice (*Phosphor: Amber.*) and returns. Keys: *F1=Help F3=Exit F4=Prompt F12=Cancel*. See
[phosphor colours](page:elcl/using-the-terminal#phosphor).

## How option lists work {#list-screens legacy="list-screens"}

- Each row has a 3-character Opt field in column 0. Type option codes next to as many rows as you like, on any page;
  they stay with their rows when you page.
- Enter takes them top to bottom. Options that open a window or a screen wait until you're done with it, then the
  next one runs.
- Delete-type options (4=Delete, 4=End, 4=Remove) are gathered and confirmed together in one window, after the other
  options have run: *Press Enter to confirm your choices for 4=Delete, F12 to return.* followed by the rows. (Display
  Messages' 4=Remove doesn't ask, and Work with Jobs' 4=Cancel asks on the message line.)
- An option the screen doesn't have gives *Option N not valid for this item.*: nothing runs, and the cursor goes to
  that row so you can correct it.
- Commands an option runs come back with their message on the message line, and the list refreshes.
- Clicking a row types the screen's default option (5=Display on most screens, 2 where changing is the usual thing).
