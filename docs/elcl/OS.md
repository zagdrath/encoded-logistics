# Terminal OS — Object Model, Jobs, Security, Screens

## 1. Systems

Each network is a **system** with a name (system value `SYSNAME`, default
`ELNETnn`, shown top-right on every screen). All libraries, jobs, message
queues and spooled files belong to the system and are stored in the
network's saved data.

## 2. Objects

- **Library** (`*LIB`): up to 10-character name. Contains members and programs.
  - `ELSYS`: read-only, ships with the mod. Holds the sample programs in
    `examples/` so players can browse, copy and learn from them.
  - `ELGPL`: general-purpose library, created on every system.
  - Players create their own with `CRTLIB`.
- **Source member** (type `ELCLP`): the program source, up to 5,000 lines of
  80 columns. Each line carries a sequence number (6.2 format, e.g. 0012.00)
  and a change date, as midrange editors do.
- **Program** (`*PGM`): compiled from a member; records the source member
  and compile date. If the source changed after compile, Work with Members
  shows the member flagged as "changed since compile".
- **Library list:** each user profile has a library list (default
  `ELGPL ELSYS`). `*LIBL` in a qualified name searches it in order.

## 3. Storage

Source members consume network storage (proposal: 1 storage unit per 64
characters, rounded up). Programs, job logs and spooled files do not.
If storage is full, saving a member fails with `ELC0207`.

## 4. Folder sync

Per-system folder in the world save:

```
<world>/encodedlogistics/libraries/<SYSNAME>/<LIB>/<MEMBER>.elclp
```

- **Out:** saving a member in-game writes the file.
- **In:** the server watches the folder; a new or changed file becomes a
  member (sequence numbers are regenerated; existing ones are preserved for
  unchanged lines). New folders become libraries.
- **Conflicts:** last write wins; the losing version is kept as
  `<MEMBER>.elclp.bak`.
- **Deletes:** deleting a file in the folder does NOT delete the member
  (safety); deleting the member in-game moves the file to `.deleted/`.
- **Config:** `allowFolderSync` (proposal: true in single-player, false by
  default on dedicated servers). `ELSYS` is never synced in.
*(Implemented in `elcl.sync.FolderSync`: the folder is read every two seconds
while the network is loaded; what was last synced is remembered per member, so
an in-game save over a file changed outside wins and keeps the file's version
as `.bak`. Libraries made from new folders are owned by the system with
`*CHANGE` authority. ELSYS isn't written out either. Off by default on
dedicated servers: config `elcl.allowFolderSync` = AUTO / TRUE / FALSE.)*

- **Diskettes:** `SAVLIB` writes a library to an 8" Diskette item (stored in
  item data; size limit proposal 64 KB of source per diskette); `RSTLIB`
  restores it on any system.

## 5. Jobs

- **Interactive job:** one per signed-on Terminal Desk session. Runs `CALL`
  and commands typed on the command line. Ends at sign-off. Budget in
  ELCL_SPEC.md §9.
- **Batch job:** submitted with `SBMJOB`, by a schedule entry, or by a
  trigger. Needs a **job host**:

| Host | Concurrent batch jobs | On unload / restart / power loss |
|------|------|------|
| Midrange System | 1 | Job ended, `ELC0310` |
| Compute Server (each) | 4 | Job ended, `ELC0310` |
| Mainframe | 32 per CPC Drawer | Job state is journaled and **resumes** |

  If every host is busy, the job waits on the job queue (`*JOBQ` status). If
  there is no host at all, `SBMJOB` fails with `ELC0301`.
- **Job IDs:** `nnnnnn/USER/JOBNAME`, e.g. `000123/ZAGDRATH/RESTOCK`, shown
  that way on screens. Commands accept the job name alone if unique.
- **Statuses:** `*JOBQ`, `*ACTIVE`, `*WAIT` (delay, recall, craft wait),
  `*HELD`, `*MSGW` (interactive only: waiting for the user to answer a
  message), `*ENDED`.
- **Job log:** every job keeps a log of commands run (optional, `LOG(*YES)`
  on SBMJOB) and all messages. Kept for the last 50 ended jobs per system.
- **Schedule entries** and **triggers** persist in system data and fire
  even when no one is signed on, as long as the network is loaded.

## 6. Security

- **User profile:** one per player, created at first sign-on. The network
  owner is `*SECOFR`-class and can do everything.
- **Authority** comes from the Firewall's per-player permissions (view,
  insert, extract, craft, build/configure). If no Firewall is installed,
  every player who can open a terminal has full authority (single-player
  friendly); with a Firewall, its default policy applies to unknown players.
- Jobs run with the authority of the **submitting user**. Schedule entries
  and triggers run as the user who created them.
- Library authority: owner can change; others read-only unless the owner
  grants `*CHANGE` (WRKLIB option 2=Change, Authority field).
- Command-level checks use the `Auth` column in COMMANDS.md; failures raise
  `ELC0401`.

*(Implemented: profiles are made the first time a player uses a terminal on the
system, or signs on. Sign-on is needed at SECLVL 30 on a network with a Firewall
(none without one: full authority there anyway); until then a session is
answered nothing but its info and the sign-on (ELC0402). *SECOFR: the
Firewall's owner and server operators. At SECLVL 10 the Firewall isn't asked
in the OS (it still guards the blocks). Changing a system value takes *SECOFR,
or no Firewall. Holding, ending or changing another user's job, schedule entry
or trigger takes *SECOFR or full authority (ELC0401 *JOBCTL). DATFMT shows the
date as `Day 2`, or on a calendar of 30-day months from year 1.)*

## 7. System values

| Value | Default | Meaning |
|-------|---------|---------|
| `SYSNAME` | `ELNETnn` | System name |
| `DATFMT` | `*DAY` | Date display (`*DAY` = "Day 2", or `*MDY` style) |
| `SECLVL` | `30` | 10 = no sign-on, 30 = sign-on + Firewall authority |
| `QMAXJOB` | `16` | Max batch jobs per system |
| `LOGRTN` | `50` | Job logs retained |
| `CRFLOGRTN` | `200` | Crafting jobs retained in the crafting job history (default: the `craftLogRetention` config) |
| `PHOSPHOR` | `*GREEN` | Default screen colour (`*AMBER`, `*WHITE`) |

## 8. Screens (art coming from Claude Design)

New screens, all in the existing green-screen style:

1. **Sign On** — system name, user, (no password needed by default), program/menu, current library.
2. **Main Menu** — 1. Work with Inventory (WRKINV), 2. Work with Jobs (WRKCRFJOB), 3. Work with Devices (WRKDEV),
   4. Display Network Status (DSPNETSTS), 5. Work with Libraries (WRKLIB), 6. Work with Active Jobs (WRKACTJOB),
   7. Display Messages (DSPMSG), 8. Work with Output (WRKSPLF), 90. Sign Off (SIGNOFF). The menu and its F1 help are
   drawn from one definition (`client/crt/MainMenu.java`); an option whose screen isn't available says "Option n not
   available." on the message line, and a number not on the menu says so.
3. **Work with Libraries** — options 2=Change 4=Delete 5=Display 12=Work with members.
4. **Work with Members** — options 2=Edit 3=Copy 4=Delete 5=Display 6=Print 7=Rename 14=Compile; columns: member, type, text, changed-since-compile flag.
5. **Source Editor** — sequence-number margin with line commands (I, In, D, Dn, DD…DD, C/CC, M/MM with A/B, R/Rn repeat, X exclude); command line supporting FIND, CHANGE, TOP, BOTTOM, SAVE, FILE, CANCEL; syntax check on Enter that highlights the bad line and shows the message on the message line; F4 on a command line opens the prompter.
6. **Command Prompter (F4)** — one field per parameter from the command schema, with type hints, special values listed, F4 on a field for value lists (e.g. item search), F10 for additional parameters.
7. **Compile Listing** — scrollable listing as described in ELCL_SPEC.md §10.
8. **Work with Active Jobs** — job, user, type, host, status, budget use; options 2=Change 3=Hold 4=End 5=Work with 6=Release 8=Spooled files.
9. **Work with Job / Display Job Log**.
10. **Work with Job Schedule Entries** — options 2=Change 3=Hold 4=Remove 10=Submit now.
11. **Work with Trigger Events** — options 2=Change 3=Hold 4=Remove.
12. **Display Messages** — the user's message queue, newest first, with "Messages waiting" indicator on every screen's status line while unread messages exist.
13. **Work with Output (spooled files)** — options 4=Delete 5=Display 6=Print (Line Printer).
14. **Work with System Values**.
15. **Help panels (F1)** — context help for every screen, field and command.
16. **Command Entry** — the existing CLI screen, now running ELCL commands.

New block: **Control Interface** (redstone I/O for scripts), named
`CTLIFnn` by default, 1 lane, six sides independently readable/writable.
