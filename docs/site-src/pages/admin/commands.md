---
title: Server commands
section: admin
order: 50
summary: The mod's one chat command, /encodedlogistics caches, which shows how often the network caches were rebuilt and can log it every minute.
---
Encoded Logistics has a single chat (Brigadier) command, for checking how hard the network caches are working.
Everything else is done in the game or in the [Terminal OS](page:elcl/index), whose commands are ELCL, not chat
commands.

## `/encodedlogistics caches` {#caches}

```text
/encodedlogistics caches
/encodedlogistics caches log true
/encodedlogistics caches log false
```

**Who may use it:** operators only: permission level 2 (gamemaster) or higher, which includes the server console and
command blocks.

Each network keeps caches of what it is made of, and rebuilds one only when something it depends on changes. The
command counts the rebuilds:

| Counted | Caches |
|---|---|
| For each controller structure | `storage`, `drive cells`, `rack devices serving` |
| For the whole server | `racks` (the racks in order) and `scheduler heads` (the rack Scheduler pools' heads) |

Rebuilds are counted over each minute of game time (1,200 ticks).

### Showing the counts {#show}

`/encodedlogistics caches` answers with a line for the whole server, then a line for each controller structure in each
dimension:

```text
Cache rebuilds per minute (last full minute | this one so far); generation <n>, content epoch <n>
server: <last minute> | <this minute>
<dimension> #<structure> (<status>): <last minute> | <this minute>
```

Each line gives the last full minute, then the minute so far, as the kinds that were rebuilt and how many times
(`none` when nothing was). A handful a minute is normal while players build. A cache rebuilt about every tick (some
1,200 a minute) means something is invalidating it every tick, which is worth reporting with the setup that causes it.

### Logging every minute {#log}

`/encodedlogistics caches log true` writes a line to the server log at the end of every minute in which a network
rebuilt anything:

```text
[caches] minecraft:overworld #3: storage 3, drive cells 3 rebuilds in the last minute
```

`/encodedlogistics caches log false` turns it off again. The command confirms either way ("Cache rebuild logging on:
each minute's rebuilds go to the server log", or "... off") and tells the other operators too. The setting isn't saved:
logging is off again after a restart.
