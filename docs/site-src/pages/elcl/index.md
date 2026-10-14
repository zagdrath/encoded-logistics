---
title: Terminal OS and ELCL
section: elcl
order: 0
summary: The green-screen operating system on the Terminal Desk, its control language ELCL, and how systems, saving, sign-on and authority work.
legacy: [top, overview]
---
Sit at a {{item terminal_desk}} and you get an 80 by 24 green-screen terminal running the **Terminal OS**. From it you
work with your network's inventory, crafting jobs and devices, and you write, compile and run programs in **ELCL**, the
Encoded Logistics Control Language. Programs run as jobs: straight away at the desk, as batch jobs on a job host, on a
schedule, or when something happens on the network.

## The desk, the OS, ELCL and the Control Interface {#what-is legacy="what-is"}

- **Terminal Desk.** The block you sit at (see {{page terminals/terminal-desk}}). Its screen is an 80 by 24 terminal with function keys,
  a command line and a message line. It works on the network it's connected to, and has a drawer that withdrawn items
  can go to. The {{item integrated_midrange}}'s console opens the same terminal.
- **Terminal OS.** The operating system the desk shows. It keeps **libraries** of source **members** and compiled **programs**, runs
  **jobs**, keeps job logs, user **message queues**, **spooled files** (printed output), **database files** and
  **system values**, and has a screen for each. The desk's own screens (inventory, crafting jobs, devices, network
  status) are options on its Main Menu.
- **ELCL.** The control language. It's modelled on midrange control languages: every statement is a command with keyword
  parameters, such as `CRTLIB LIB(ZAGLIB) TEXT('My scripts')`; variables start with `&`, special values with `*`, and
  errors are handled with `MONMSG`. You type ELCL commands on any command line, and you write ELCL programs in source
  members, edit them in the source editor and compile them. The same command definitions drive the parser, the
  compiler, the F4 prompter, F1 help and the [command reference](page:elcl/commands/index).
- **Control Interface.** A network block that gives ELCL redstone input and output on its six faces, read with {{cmd RTVRSIN}} and driven
  with {{cmd CHGRSOUT}}. It has no screen of its own: it's named `CTLIF01`, `CTLIF02` and so on and driven from the
  terminal. See {{page control/control-interface}}.
- **PLC.** A {{item plc}} runs one compiled ELCL program over and over (a scan), with the Control Interface's six redstone
  faces, four sensor module slots and its own green screens. It works on its own or as a network device (`PLC01`). See
  {{page control/plc}} and {{page elcl/control-and-plcs}}.

Everything on these pages works in the game today: the screens, the command line, libraries and members, the editor
and compiler, running programs (at the desk with {{cmd CALL}}, or as batch jobs on Compute Servers and Midrange
Systems), schedule entries and triggers, sign-on and Firewall authority, and every inventory, crafting, device, power,
routing and redstone command.

### Reading these pages

1. {{page elcl/using-the-terminal}}: the screen, the keys, the command line, the prompter and help.
2. {{page elcl/screens}}: every screen, how it opens, and its options.
3. {{page elcl/libraries-and-editor}}: libraries, members, the source editor, compiling, diskettes and folder sync.
4. {{page elcl/database-files}}: physical files, records, queries, import and export.
5. {{page elcl/language}}: the language itself, statement by statement.
6. {{page elcl/jobs-and-scheduling}}: jobs, schedule entries, triggers, messages, spooled output and system values.
7. {{page elcl/control-and-plcs}}: redstone, the Control Interface and PLCs from ELCL.
8. {{page elcl/status-and-limits}} and {{page elcl/examples}}.

Every command has its own page in the [command reference](page:elcl/commands/index), and every message ID is in the
[message reference](page:elcl/messages).

## Systems and networks {#systems legacy="systems"}

Each network is one **system**. Its libraries, jobs, message queues, spooled files, files and system values belong to
that system, and every desk on the network sees the same ones.

- The name at the top right of every screen (`System:  ELNET01`) is the system value `SYSNAME`. It starts out as the
  network's name, `ELNET` and the network's two-digit number, and you can change it with {{cmd CHGSYSVAL}} (or Work with
  System Values). A desk that isn't on a network shows `*OFFLINE`.
- `SYSNAME` also appears in compile listings, printed members and reports, and saved diskettes; it's the first half of
  a device's qualified name (`ELNET01/LGT01`) on a [Directory Server](page:networking/exchange-and-directory); and it
  names the system's [sync folder](page:elcl/libraries-and-editor#folder-sync), so changing it starts a new folder.
- While a desk has no network, OS commands and screen requests answer {{msg ELC1302}} (`Device *NETWORK is offline.`).

## What is saved {#persistence legacy="persistence"}

Each system's data is saved with the world, one record per network (keyed by its controller structure), and loaded
with it:

- libraries, source members (with their type, sequence numbers and change dates) and compiled programs (kept as their
  source, compile date and the formats of the files they declare, and compiled again on load);
- [database files](page:elcl/database-files) and their records;
- message queues, spooled files and system values;
- batch jobs: queued, held and running ones; the logs of the last `LOGRTN` ended jobs; job schedule entries and
  trigger events, each with the player who made it;
- the crafting job history: the last `CRFLOGRTN` crafting jobs that ended (see {{page crafting/jobs}});
- user profiles (library list, current library, last sign-on) and which device holds each device name.

Not saved: interactive jobs (they belong to an open terminal session and end with it) and member edit locks. Queued and held
batch jobs wait for a host again after a restart, but a job that was running doesn't carry on: no job host resumes
jobs, so it ends with {{msg ELC0310}}, as it does when its host is unloaded or loses power. `ELSYS` is never saved either: it's rebuilt from the mod's bundled samples, and its programs compiled,
every time a system is made or loaded, so a mod update brings new samples with it.

Two caps keep a system's data in bounds: at most {{config elcl.spooledFileCap default}} spooled files per system and
{{config elcl.messageCap default}} messages in each user's queue. Past either, the oldest go first. Source members and
records also take [network storage](page:elcl/libraries-and-editor#storage-cost).

## Signing on {#sign-on legacy="sign-on"}

The **Sign On** screen appears when you open a desk on a network that has a {{item firewall}}, unless the system value
`SECLVL` is `10`. Without a Firewall there's no sign-on: the desk opens on the Main Menu (and everyone has full
authority anyway). Signing on happens on the server: until the session has signed on, the desk answers nothing but the
screen's system information and the sign-on itself (anything else is refused with {{msg ELC0402}}), so no other screen
can be reached around it. You sign on each time you open the desk.

{{layout 01_signon}}

| Field | What to type |
|---|---|
| User | Your own player name, in any case. There's no password. Any other name is refused with {{msg ELC0402}} (`Sign-on failed for user NAME.`) and the field is cleared; a blank one gives *Type your user name.* Signing on needs the Firewall's view permission: without it you get {{msg ELC0401}}. |
| Program/procedure | A program to {{cmd CALL}} straight after sign-on (`LIB/NAME`, or a name on your library list). Blank or `*NONE`: none. It runs in your interactive job, as a `CALL` typed on the command line would. |
| Menu | The menu to start on. `MAIN` is the only one; anything else gives *Menu X not found. MAIN is the menu there is.* |
| Current library | Where new members go, where a name that isn't on your library list is looked for, and what {{cmd WRKMBR}} shows when no library is given. `*USRPRF` (the default) keeps your profile's (`ELGPL` until you change it). A library name is checked on the server ({{msg ELC0201}} if there's none), saved in your profile and put first on your library list. |

Enter signs on (*Signed on as NAME.*). Esc, F3 or F12 on the Sign On screen closes the terminal. {{cmd SIGNOFF}} (Main
Menu option 90) signs you off (*Signed off.*) and shows Sign On again where sign-on is needed; otherwise the terminal
closes.

## Users, security and the Firewall {#security legacy="security"}

- **User.** Commands always run as the player at the desk, as their name in upper case (`ZAGDRATH`). Signing on
  doesn't let you become someone else.
- **User profiles.** Each player has a profile on each system, made the first time they open a terminal there, and
  saved with the system: their library list (`ELGPL ELSYS` to start with, the current library first), current library,
  when it was made and their last sign-on.
- **`*SECOFR`.** The security officer class: the Firewall's owner, and server operators (players with operator
  permission) while they're online. Everyone else is a plain user. The class is worked out each time, so a new
  Firewall owner takes effect at once.
- **Interactive job.** Each open desk session has an interactive job named `QINTER`, started by the session's first
  request and ended (with {{msg ELC0307}} in its log) when you close the terminal; a program it's running ends with
  it, and its log is kept with the other ended jobs'. Closing also releases any member edit locks you hold.
- **Authority comes from the Firewall.** Each command needs a Firewall permission: none, view, insert, extract, craft
  or configure (configure is the Firewall's **BUILD** permission). Without it the command fails with {{msg ELC0401}}
  (`Not authorized: user &1 lacks &2 permission.`), where &2 is `VIEW`, `INSERT`, `EXTRACT`, `CRAFT` or `BUILD`. One
  check serves everything: commands on the command line, commands in programs, the OS screens (whose data needs view)
  and their options. (The desk's own inventory, crafting and device screens say *Not authorised to ...* on the message
  line instead.) Each command's page in the [command reference](page:elcl/commands/index) says which permission
  it needs. The OS's own commands (libraries, jobs, system values, messages, printing, displays) need no Firewall
  permission: the OS checks its own library, job and system value authority instead. Server operators at a terminal
  may do everything.
- **Batch jobs run as their submitter.** A batch job, and a job submitted by a schedule entry or trigger, is checked
  against the Firewall's permissions for the player who submitted or made it, by their player id, whether they're
  online or not.
- **No Firewall: full Firewall authority.** Every player who can open a desk has every Firewall permission, may
  change system values and may manage anyone's jobs (single-player friendly). Library ownership still applies (see
  below). With a Firewall, its per-player permissions and its default policy for unknown players apply. An offline
  Firewall still
  enforces its rules unless {{config rack.firewallFailClosed}} is turned off. See {{page racks/security}} and
  {{page admin/permissions}}.
- **Changing system values** ({{cmd CHGSYSVAL}}, or Work with System Values 2=Change) takes `*SECOFR`, or a network with
  no Firewall (`SECLVL 10` doesn't lift this); otherwise {{msg ELC0401}} names `*SECOFR`.
- **Other users' jobs.** Holding, releasing, ending or changing another user's job, schedule entry or trigger takes
  `*SECOFR` (or full authority: no Firewall, or `SECLVL 10`); otherwise {{msg ELC0401}} names `*JOBCTL`. Your own are
  always yours to manage.
- **Other users' messages.** {{cmd DSPMSG}} `USR(*SYSOPR)` (the operator queue, `QSYSOPR`) or `USR(name)` shows
  someone else's queue to a `*SECOFR` or anyone with full authority (no Firewall, or `SECLVL 10`); others get
  {{msg ELC0401}}.
- **Library authority.** `ELSYS` is read-only for everyone ({{msg ELC0205}}). `ELGPL` has public authority `*CHANGE`,
  so everyone can change its members. A new library is owned by its creator with public authority `*USE`: others can
  read it but not change it ({{msg ELC0401}} naming `*CHANGE`) unless the owner sets `*CHANGE`. Only the owner may
  change a library's text or authority or delete it ({{msg ELC0401}} naming `*OWNER`). `*SECOFR` may do all of these
  in any library but `ELSYS`, and anyone may change the text and authority of a library the system owns (`QSYS`:
  `ELGPL`, and libraries made by [folder sync](page:elcl/libraries-and-editor#folder-sync)). Nobody can delete `ELGPL`.
  These rules hold with or without a Firewall and at either `SECLVL`.

The Firewall guards the network's hardware too (Server Racks, Link Cards): see {{page racks/security}}.

### SECLVL {#seclvl legacy="seclvl"}

The system value `SECLVL` sets how much the OS asks the Firewall.

| Value | Meaning |
|---|---|
| `10` | No sign-on screen, and the Firewall isn't asked inside the OS: everyone has every Firewall permission at the terminals and in their jobs, and may manage anyone's jobs. Library authority and changing system values still follow the rules above, and the Firewall still guards the blocks themselves. |
| `30` (default) | Sign-on (when the network has a Firewall), then the Firewall's authority for everything. |
