---
title: Server commands
section: admin
order: 50
summary: The /encodedlogistics chat commands - config to read, set and reset the config in game, debug to see how the networks are performing, and caches.
---
Encoded Logistics has one chat (Brigadier) command, `/encodedlogistics`, with three parts: [`config`](#config) reads,
sets and resets the mod's config in game, [`debug`](#debug) shows how the networks are performing, and
[`caches`](#caches) how hard the network caches are working. Everything else is done in the game or in the
[Terminal OS](page:elcl/index), whose commands are ELCL, not chat commands.

**Who may use them:** operators only: permission level 2 (gamemaster) or higher, which includes the server console and
command blocks. Tab completion lists every choice.

## `/encodedlogistics config` {#config}

```text
/encodedlogistics config get <option>
/encodedlogistics config set <option> <value>
/encodedlogistics config reset all
/encodedlogistics config reset <section>
/encodedlogistics config reset <option>
```

The mod's main config, `encodedlogistics-local.toml` (see {{page admin/config}}), from in game, as Arcforge's
`/arcforge config` does. Options are written as `section.key`, `lanes.resolveInterval` say; tab completion lists every
section and option, and `set` suggests the values an option takes.

- `get` answers with the option's value, its default and what it allows:
  `lanes.resolveInterval = 20 (default 20, 0 to 1200)`.
- `set` checks the value against the option's range or allowed values and refuses one it doesn't take.
- `reset all` puts every option back to its default, `reset <section>` every option in a section (`reset lanes`),
  and `reset <option>` one option. It names the options it changed.

A change is saved to the file straight away, takes effect at once, and every network works out its lanes again with
the new values. The client config (`encodedlogistics-client.toml`, each player's own) isn't covered: change that in the
game's Mods screen.

## `/encodedlogistics debug` {#debug}

```text
/encodedlogistics debug solves
/encodedlogistics debug ticks [count]
/encodedlogistics debug networks
/encodedlogistics debug watch solves [ms]
/encodedlogistics debug watch off
```

For finding what a network costs the server. Each answers whoever asked, in chat or the console; nothing goes to the
server log.

A network is **solved** when something it's made of changes: it finds its blocks again and works out its lanes. Only
the networks a change touches solve, at most once every {{config lanes.resolveInterval default}} ticks (a burst of
changes costs one), and a lone change is worked out on the next tick. See {{page networks/lanes}}.

### `debug solves` {#debug-solves}

Each network that solved in the last two minutes, busiest first:

```text
Network solves: 6 in the last full minute; re-solve interval 20 ticks, 0 waiting
minecraft:overworld #3 (ONLINE): 4 solves last minute (1 so far), avg 3.1 ms, max 5.0 ms, last 2.9 ms (2.1 finding its blocks), 1615 nodes; block placed, broken or reconnected 3, remote link changed 2
```

A network solving every few seconds while nobody builds on it means something keeps changing it; the causes say what
(a block placed, broken or reconnected; parts or lanes; a remote link; rack devices; an optical channel; a controller
structure). A large network solving in tens of milliseconds is worth looking at with `debug networks`.

### `debug ticks` {#debug-ticks}

How long each level's networks take to tick, all together, then the costliest networks (10, or `count`), as the
average and the most per tick over the last minute. This is the work every tick does - energy, devices, ports - with
any solves on top.

### `debug networks` {#debug-networks}

Every network: its status, what runs it (controller blocks, a rack or a Midrange System, and for a redundant pair the
rack it follows), its nodes, devices and racks, the lanes in use and short, its energy, and how long ago it was last
solved.

### `debug watch solves` {#debug-watch}

`/encodedlogistics debug watch solves` tells you in chat of every network solve as it happens, with the network, its
size, the time it took and why it solved; `debug watch solves 20` only of those taking 20 ms or more.
`/encodedlogistics debug watch off` stops it. It lasts until you stop it or the server restarts. (A solve slower than
{{config lanes.slowSolveMillis default}} ms is also written to the server log as a warning; 0 turns that off.)

## `/encodedlogistics caches` {#caches}

```text
/encodedlogistics caches
/encodedlogistics caches log true
/encodedlogistics caches log false
```

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
