---
title: Schedulers
section: crafting
order: 60
summary: The Scheduler multiblock and the rack Scheduler - what plans and runs crafting jobs, how threads and job memory work, and how jobs share them.
covers: [encodedlogistics:scheduler_core, encodedlogistics:job_buffer, encodedlogistics:thread_unit]
---
A **Scheduler** runs crafting jobs. When you ask the network for something it plans the job, then hands it to a
Scheduler, which holds the ingredients, works through the steps, offers each one to a
[provider](page:crafting/fabricators-and-gateways) and collects the results. A network can have several.

There are three kinds of job host:

| Host | Built from | Where |
|---|---|---|
| A **Scheduler** | A multiblock of Scheduler Core, Job Buffers and Thread Units | Below |
| The **rack Scheduler** | Compute and Memory Servers (and Blades) in Server Racks | [Below](#rack) |
| A **Midrange System** | One block, crafting its own diskette recipes | {{page crafting/midrange}} |

Each has **threads** (how much crafting it keeps going at once) and **job memory** (how big a job it can take).

## The Scheduler multiblock {#multiblock}

A Scheduler is built from three blocks:

| Block | Adds |
|---|---|
| {{item scheduler_core}} | The one block every Scheduler needs: {{config autocrafting.schedulerBaseThreads default}} thread and {{config autocrafting.schedulerBaseMemory default}} job memory on its own |
| {{item job_buffer}} | {{config autocrafting.jobBufferMemory default}} job memory each |
| {{item thread_unit}} | {{config autocrafting.threadUnitThreads default}} thread each |

{{recipe scheduler_core}}

{{recipe job_buffer}}

{{recipe thread_unit}}

### Forming rules {#forming}

Touching Scheduler blocks form one Scheduler when:

1. they **fill their bounding box**: a solid cuboid, no gaps, no other blocks inside;
2. no side is longer than {{config autocrafting.schedulerMaxSize default}} blocks;
3. they contain **exactly one** Scheduler Core.

Mix Job Buffers and Thread Units however you like. A formed Scheduler's blocks change to their connected look. It's
checked again whenever one of its blocks is placed or broken. If it doesn't form, the Core's screen says why: no
Scheduler Core, two or more Cores, not a solid cuboid, or too large.

{{screenshot file="crafting/scheduler-multiblock.png" alt="A formed 3 by 2 by 2 Scheduler" show="A formed Scheduler: a 3 x 2 x 2 cuboid with the Core at the front, Job Buffers and Thread Units glowing while a job runs, a cable to one side" angle="Three blocks away at a 45 degree angle, slightly above" size="1600x900"}}

A formed Scheduler is **one network device**: cables connect to any of its blocks, the Core uses one lane, and it drains
{{config autocrafting.schedulerDrain default}} FE a tick plus {{config autocrafting.schedulerDrainPerBlock default}} per
block. Nothing runs while it's unformed or offline. Thread Units glow while jobs run.

> [!WARNING] Breaking the Core fails its jobs
> Everything the Scheduler's jobs are holding drops at the Core, and the jobs end as failed. Breaking a Job Buffer or
> Thread Unit unforms the Scheduler until you put it back.

### The Core's screen {#core-screen}

Use any block of a formed Scheduler to open it. It shows:

- the **running jobs** (item, amount, progress, and an × to cancel each) and the **queue**;
- a **threads** meter and a **job buffer** (memory) meter;
- the last five jobs that finished there (hover one for what it made, when, how long it took and why it failed), with a
  pointer to the full [crafting history](page:crafting/jobs#history).

Click a job to open its [Job Status](page:crafting/jobs#job-status) screen.

## Threads and job memory {#threads-memory}

**Job memory** is the number of items across a job's whole ingredient tree. A job is accepted only when its memory fits
in what the host has free; a Scheduler with 4,096 memory can take a job needing 3,000 items' worth of steps alongside one
needing 1,000. When nothing has room, the plan screen says "No Scheduler has room".

**Threads** are shared among the jobs:

- Every queued job gets a thread first, so a big job never blocks a small one. If no thread is free, the job with the
  most gives one up.
- Free threads then go round the running jobs that can use more, one at a time.
- Each thread keeps {{config autocrafting.threadCrafts default}} craft going at once (0 means no limit). A job wants as
  many threads as it has crafts left, divided by that. Fabricators and Fabrication Servers run every craft they're
  given side by side, so a job holding twelve threads makes twelve crafts at a time.
- A job gives threads back as it nears its end, so the next job can start.

So the way to speed jobs up is more threads (Thread Units, Compute Servers) and more providers with the same schematic
(each run goes to the least busy one: see [several machines](page:crafting/fabricators-and-gateways#spreading)).

## The rack Scheduler {#rack}

Server Racks have their own Scheduler, built from rack devices:

| Device | Gives |
|---|---|
| {{item compute_server}} | {{config rack.computeServerThreads default}} threads |
| {{item memory_server}} | {{config rack.memoryServerMemory default}} job memory |
| {{item compute_blade}}, {{item memory_blade}} in a Blade Enclosure | Threads and memory as their servers give per rack unit, {{config optical.bladeBonusPercent default}} % more |

They **pool across every rack on the network**: all the online Compute and Memory Servers and Blades serving a network,
in whichever of its racks, make one rack Scheduler with all their threads and memory added up. You don't need a Memory
Server in the same rack as each Compute Server, and you don't get one Scheduler per rack.

- The pool runs jobs once it has both threads and memory.
- It appears on the plan screen's Scheduler choices as "Rack x, y, z": the rack that heads the pool (the first rack
  with an online Compute Server serving the network). If the head changes, its jobs move to the new head and carry on.
- It offers crafts to the network's Fabrication Servers and Fabrication Blades first, then to the other providers.
- Compute Servers blink while jobs run on the pool; Memory Servers while jobs hold memory.
- Breaking the head rack fails its jobs, as breaking a Core does.

The servers and blades themselves are on {{page racks/devices}} and {{page racks/chassis}}.

## Which host a job goes to {#auto}

The plan screen's Scheduler selector starts at **Auto**, which picks the first host with a free thread and room for the
job's memory, or failing that the first with room (the job then waits in its queue). You can cycle it to pick a
particular Scheduler, the rack Scheduler or a Midrange System instead. See {{page crafting/jobs}}.
