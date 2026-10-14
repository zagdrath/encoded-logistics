Work with System Values lists the system's seven system values with their current values and descriptions. It's
also option 13 on the main menu. Type an option next to one:

| Option | What it does |
|---|---|
| `2` | Change it, in a window that shows what it takes ({{cmd CHGSYSVAL}}). Only a security officer may: anyone else gets {{msg ELC0401}}. |
| `5` | Display it in a window: its value, default, description and allowed values. |

{{table sysvals}}

What each one does:

- `SYSNAME`: the system's name, shown at the top right of every screen, in compile listings, and as the folder name
  for folder sync (so changing it starts a new folder). Its default is the network's own name.
- `DATFMT`: how dates appear in lists and logs. `*DAY` shows `Day 2  07:14`; `*MDY`, `*DMY` and `*YMD` show the game
  day as a date on a calendar of 30-day months starting at year 1 (`01/02/01 07:14` for day 2). The clock in the
  screen header always shows `Day n`.
- `SECLVL`: at `30`, a network with a Firewall makes you sign on, and only a job's owner or a security officer may
  hold, change or end it (the same for schedule entries and triggers). At `10`, nobody signs on and anyone may.
- `QMAXJOB`: the most batch jobs running at once on the whole system; the rest wait on the job queue.
- `LOGRTN`: how many ended jobs' logs the system keeps for {{cmd DSPJOBLOG}}; the oldest go first.
- `CRFLOGRTN`: how many ended crafting jobs the crafting history keeps. Lowering it trims the history at once. It
  starts at {{config elcl.craftLogRetention}}.
- `PHOSPHOR`: the colour of every terminal on the network, unless a player has picked their own (see F24=More
  keys). Changed here, it shows on this screen at once.

A system keeps only the values changed from their defaults, with the rest of its saved data.

{{layout 14_wrksysval}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKSYSVAL
```

In a program, read a value with {{cmd RTVSYSVAL}}.
