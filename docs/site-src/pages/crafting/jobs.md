---
title: Crafting jobs
nav: Jobs
section: crafting
order: 70
summary: Asking the network to craft something, the plan screen, Job Status, the Schematic Terminal and Rack Schematic Console, stalls, the crafting history and job toasts.
---
Once the network has [schematics](page:crafting/schematics) in [providers](page:crafting/fabricators-and-gateways) and a
[Scheduler](page:crafting/schedulers) to run them, you can ask it for anything it knows how to make. It works out every
step down to what's in storage, takes the ingredients, and crafts the lot.

## Asking for a craft {#request}

From any terminal (an Access Terminal, Fabrication Terminal, Handheld Terminal or rack console):

1. **Middle-click** or **Ctrl-click** an item the network can craft. An item it can craft but has none of shows "Craft"
   in place of a count; any click on that one does the same.
2. The **Craft amount** screen asks how many. Type a number or use the buttons, then press **Next** (or Enter).
3. The **Craft plan** screen shows what the job takes (below). Press **Start**.
4. The **Job Status** screen opens on the new job.

You can also start crafts from the Terminal OS (7=Craft on {{cmd WRKINV}}) and from scripts with {{cmd STRCRAFT}}: see
{{page elcl/jobs-and-scheduling}}.

## The plan screen {#plan}

The plan is the job's **ingredient tree**: the item you asked for, then what it's made from, down to what's stored.
Each line shows how many of that item are:

| Column | Means |
|---|---|
| Have | In storage, to be used |
| Make | To be crafted by this job |
| Miss | Missing: the job can't start |

Click a line's arrow to fold its branch. The number missing shows top right. Items on tape count as Have, shown in light
blue with a tape marker; the time the recalls will take shows top right and under the tree ("Includes tape recall").

The **Scheduler** button cycles the host: Auto, or one of the network's Schedulers, its rack Scheduler ("Rack x, y, z")
or a Midrange System ("Midrange MIDRANGE01"). **Start** works only when nothing is missing and the chosen host has room
for the job's memory; otherwise it says "No Scheduler" or "No Scheduler has room". **Cancel** goes back to the terminal.

{{screenshot file="crafting/craft-plan.png" alt="The Craft plan screen for a Fabricator" show="The Craft plan screen for one Fabricator: a tree three levels deep with Have and Make counts, one tape item in light blue with the recall time, Scheduler: Auto, Start enabled" angle="GUI scale 3, screen centred" size="1280x720"}}

How the plan is worked out:

- Leftovers from the job's own crafts are used first, then what's in storage, then more crafting.
- When several schematics make the same item, the planner tries them in network order and uses the first whose whole
  tree can be made. If none can, it shows the first, so you can see what's missing.
- A plan has limits so a runaway recipe can't stall the server: 32 levels deep, 2,000 lines and 50,000 nodes of work.

Starting the job takes its hot ingredients out of storage at once (the job holds them from then on) and starts the
recalls of the cold ones. If storage no longer has everything by the time you press Start, nothing is taken and the job
doesn't start.

## Job Status {#job-status}

The Job Status screen shows the item and the job's overall progress, then its steps two to a row, each with its own
progress (scroll for more), and how many items are still to come back from tape. **Cancel job** cancels it. It updates
by itself and says the job finished once it's gone. It opens when you start a job and from a Scheduler Core's screen
(closing it goes back there).

When a job ends:

- **Completed:** everything it made, and anything left over, goes into storage.
- **Cancelled:** everything it holds goes back into storage. What doesn't fit drops at its host; fluids and gases that
  don't fit are lost.
- **Failed:** its host was broken (a Scheduler Core, or the rack heading a rack Scheduler). What the job held drops
  there.

### Stalls {#stalls}

A running job that makes no progress for {{config autocrafting.jobStallSeconds default}} seconds is shown as
**stalled**, with the reason, on the Schematic Terminal and the Terminal OS: tape recall pending, machine missing,
machine unpowered, machine output full, Gateway blocked, or ingredients missing. A stall fires the `*CRFSTALL` trigger
event, so a script can act on it (see {{page elcl/jobs-and-scheduling}}). Any progress clears it.

### Holding a job {#hold}

A held job starts no new steps (those running finish) until it's released. You can hold and release jobs from the
Schematic Terminal, a Midrange control panel (3=Hold, 6=Release) or the Terminal OS. Holds are saved.

## The Schematic Terminal {#schematic-terminal}

The {{item schematic_terminal}} is a cable part for managing the network's autocrafting: its schematics and its jobs. It
uses one lane and lights while online. The {{item rack_schematic_console}} is the same terminal in a Server Rack: see
{{page terminals/rack-consoles}}.

{{recipe schematic_terminal}}

It has a summary strip and two tabs.

**Schematics** lists every provider on the network with the schematics in it: Fabricators (FAB01...), Gateways and the
machine each feeds (GATEWAY01 > the machine), Fabrication Servers (FABSRV01...) and Midrange Systems (their diskette
recipes, read-only). You can:

- take a schematic out onto your cursor, or put one in a slot;
- sneak-click a schematic in your inventory to put it in the selected holder;
- right-click a holder to **Locate** it (outlined through walls for ten seconds, and its position in chat) or
  **Rename** it;
- search, sort and filter the list.

Badges flag a schematic that's also in other holders (they share its work), an item with different recipes in
different schematics (the badge says which one the Scheduler uses), and a Gateway whose machine is missing or doesn't
accept the inputs.

**Jobs** lists the jobs with their number, output, status (Queued, Active, Waiting, Stalled, Held, Done, Failed,
Cancelled), progress, Scheduler and time. Click one for its step tree, with each step's state, a stalled step's reason,
and Locate for the machine. Right-click a job to cancel it (you're asked to confirm), hold it or release it. Filter by
Scheduler, by who asked, and by active jobs only or also the last ended ones.

Seeing it needs the Firewall's VIEW permission; moving schematics and renaming holders, BUILD; cancelling, holding or
releasing someone else's job, CRAFT (your own jobs you can always change). See {{page racks/security}}.

{{screenshot file="crafting/schematic-terminal-jobs.png" alt="The Schematic Terminal's Jobs tab with one job expanded" show="The Schematic Terminal on its Jobs tab with three jobs, one expanded to its step tree with one step stalled and its reason shown" angle="GUI scale 3, screen centred" size="1280x720"}}

## Crafting history {#history legacy="crafthistory"}

Every crafting or processing job that ends, done, failed or cancelled, leaves a record in its network's history. A
record has:

- the job's number (C0042), the item, the amount asked for and the amount made;
- how it ended, and why it failed;
- who asked: the player, or the ELCL job that ran {{cmd STRCRAFT}} and the schedule entry or trigger behind it, with its
  user;
- the Scheduler that ran it and where it is;
- when it started and ended (the game clock), how long it ran, and the ingredients it used up from storage.

The history is kept with the network's Terminal OS data. It keeps the last CRFLOGRTN jobs (a system value, default
{{config elcl.craftLogRetention default}}), removing the oldest first. A new job's number skips numbers still in the
history, so a number always means one job.

Where it shows:

- {{cmd WRKCRFJOB}}: F10=History;
- {{cmd RTVCRFSTS}} on an ended job (*DONE, *FAILED, *CANCELLED; {{msg ELC1404}} once it has aged out) and
  {{cmd RTVCRFLOG}} (job numbers, filtered by item and status, newest first);
- the `*CRAFTEND` trigger event;
- the Scheduler Core's screen and the Compute and Memory Servers' panels, which list the last five jobs that finished
  there.

## Job toasts {#toasts}

When a job ends you get a toast: "Crafting complete", "Crafting failed", "Processing cancelled" and so on, with the item.
Several jobs ending together are batched into one ("3 jobs complete"). If you were offline when your job ended, you get
a message in your Terminal OS message queue instead.

The server can turn toasts off for everyone with {{config rack.allowJobToasts default}}. Each player chooses the rest in
their client config (the **Job Toasts** section of the mod's config screen):

{{table config keys=toasts.enabled,toasts.jobs,toasts.completed,toasts.failed,toasts.cancelled,toasts.minimumSeconds,toasts.sound}}
