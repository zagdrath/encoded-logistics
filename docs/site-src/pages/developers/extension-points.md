---
title: Extension points
section: developers
order: 10
summary: The capabilities Encoded Logistics exposes on its blocks and items, and the public registries another mod can use - ELCL commands, job hosts, displays, pressurized sources and machine access - with short examples.
---
These are the places another mod can hook into Encoded Logistics. All of them are public Java, and the mod uses each
one itself, but **none of them is a stable API**: names and signatures can change in any release. Add Encoded
Logistics as a `compileOnly` dependency of the exact version you build against, and keep your integration optional
(see {{page developers/arcforge-integration}} for one way to do that).

All classes below are under `net.zagdrath.encodedlogistics`. Register everything once, during your mod's setup (your
mod's constructor, or a setup event), and only when Encoded Logistics is loaded.

## Capabilities {#capabilities}

The mod registers only NeoForge's standard capabilities (in `registry.ModCapabilities`). Other mods use them as they
would anyone's:

| Capability | On |
|---|---|
| `Capabilities.Energy.BLOCK` | The Network Controller (the whole structure's buffer), Power Inlet, Capacitor Bank, Lithography Press, PLC (its own buffer), and the Server Rack (FE into any connection point feeds its network) |
| `Capabilities.Item.BLOCK` | The Lithography Press, the Gateway, and the Terminal Desk's drawer (through its pedestal half) |
| `Capabilities.Energy.ITEM` | The Handheld Terminal (its battery) and the Energy Storage Drives (out of a holder, a drive is a battery item) |

There are no capabilities for the network's storage itself: the way in and out for other mods' blocks is the cable
parts (ports and the Inventory Tap), which use the standard capabilities of the block they face.

## ELCL commands: `CommandRegistry` {#commands}

`elcl.cmd.CommandRegistry` holds every ELCL command by name. A command is registered once with its schema, and what
runs it is bound to the name separately:

```java
public static synchronized void register(CommandDefinition command)   // throws if the name is taken
public static synchronized void bind(String name, CommandExecutor executor)   // throws if it isn't registered
```

The schema drives everything else: the parser, the compiler's checks, the F4 prompter, F1 help, the command line and
the VM all read it, and the same executor runs on the command line, in interactive programs and in batch jobs.

```java
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.ElclMessages;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.exec.ElclContext;

public final class MyElclCommands {
    public static void register() {
        CommandRegistry.register(CommandDefinition.of("RTVWIDGET", "Retrieve Widget Count")
                .auth(CommandDefinition.Auth.VIEW)              // the Firewall permission it needs (default NONE)
                .positional(1)                                  // the first parameter may omit its keyword
                .p(ParamDef.of("DEV", "Device", ParamDef.Kind.DEVICE).req())
                .p(ParamDef.of("MODE", "Mode", ParamDef.Kind.SPECIAL).sv("*FAST", "*SLOW").dft("*FAST"))
                .p(ParamDef.of("RTNVAL", "Return value", ParamDef.Kind.VARIABLE).returns(ParamDef.VarType.INT))
                .build());

        CommandRegistry.bind("RTVWIDGET", call -> {
            ElclContext context = call.context(ElclContext.class);   // server, network, user; null off the server
            String device = call.text("DEV");                        // names and special values come upper-cased
            if (context == null || context.network() == null) {
                throw new ElclException("ELC1302", device);          // a message the mod defines: "Device &1 is offline."
            }
            if (!device.startsWith("WDG")) {
                // Your own message: an ID and text of your choosing, as SNDPGMMSG sends them.
                throw new ElclException(ElclMessage.user("WDG0001", ElclMessages.SEVERE, "No widget device " + device));
            }
            call.returns("RTNVAL", 42L);                             // sets the RTN* variable, if one was given
            call.send(ElclMessage.user("WDG0002", ElclMessages.INFO, "Widgets counted."));
        });
    }
}
```

What the pieces do:

- **`CommandDefinition.of(name, description)`** builds the schema. `.context(...)` says where it may run:
  `BOTH` (the default: command line and programs), `INTERACTIVE`, `BATCH` or `PROGRAM`. `.auth(...)` is one of
  `NONE`, `VIEW`, `INSERT`, `EXTRACT`, `CRAFT` or `CONFIGURE` (the Firewall's BUILD permission).
- **`ParamDef.of(keyword, label, kind)`** adds a parameter. The kinds are `CHAR`, `INT`, `DEC`, `LGL`, `NAME`,
  `QUALIFIED`, `ITEM`, `DEVICE`, `SPECIAL`, `MSGID`, `LABEL`, `VARIABLE`, `COMMAND`, `VALUE` and `TIME`. Builder calls:
  `.req()`, `.dft(value)`, `.sv(specials...)`, `.list(max)`, `.range(min, max)`, `.len(n)` (the prompter's field),
  `.returns(VarType)` (a `RTN*` parameter) and `.values(ValueList)` (what F4 lists).
- **The `Invocation`** (`call`) gives the arguments: `text(...)`, `integer(...)` (raises {{msg ELC0003}} or
  {{msg ELC0004}} itself), `list(...)`, `given(...)` (given rather than defaulted), `returns(...)`, `send(...)`,
  `interactive()` and `context(type)`. An executor that has to wait can call `await(Wait)` when `canWait()` is true;
  the job then waits and the command runs again with `resumed()` set.
- **Failing:** throw an `ElclException`. `new ElclException(id, data...)` takes an ID the mod defines (its text comes
  from the mod's message list, see {{page elcl/messages}}); for your own text use `ElclMessage.user(id, severity, text)`.

Things to know:

- A registered command with no executor bound answers {{msg ELC0107}}.
- A PLC that isn't on a network can't run your command ({{msg ELC1502}}): only a fixed set of commands runs there.
- `CommandRegistry` registers the built-in commands when the class loads; registering a built-in's name throws.
- `bind` replaces an executor, so it can also replace a built-in command's. Don't.

## Batch job hosts: `JobHosts` {#job-hosts}

`elcl.job.JobHosts.register(DeviceSources.Source<JobHost>)` adds a source of **job hosts**: devices that run batch
jobs (`SBMJOB`). A source lists the hosts it has on a system (a network); the Compute Servers are a source registered by
the mod itself, and the Midrange line adds another.

```java
import java.util.List;

import net.zagdrath.encodedlogistics.elcl.job.JobHost;
import net.zagdrath.encodedlogistics.elcl.job.JobHosts;

JobHosts.register(system -> List.of(new JobHost() {
    @Override public String name() { return "WIDGET01"; }    // its device name: SBMJOB HOST(), the screens
    @Override public int capacity() { return 2; }            // batch jobs it runs at once
    @Override public boolean resumes() { return false; }     // false: its jobs end (ELC0310) when it goes away
    @Override public boolean online() { return true; }       // loaded and powered: it can run jobs now
}));
```

`system` is an `elcl.screen.ElclSystem` (`server()`, `network()`); return the hosts on that network. Every host the mod
has returns `false` from `resumes()`.

## Text displays: `Displays` {#displays}

`elcl.device.Displays.register(DeviceSources.Source<DisplayDevice>)` adds displays that {{cmd SNDDSPTXT}} can write to.
The mod registers the Display Panels this way. A `DisplayDevice` has a `name()`, `online()`, the number of `lines()` it
shows, and `write(int line, String text, boolean clear)` (line 1-based; 0 is the line after the last one written). An
overload of `write` with a size, colour and alignment has a default that ignores them.

```java
import java.util.List;

import net.zagdrath.encodedlogistics.elcl.device.DisplayDevice;
import net.zagdrath.encodedlogistics.elcl.device.Displays;

Displays.register(system -> List.of(new DisplayDevice() {
    private final String[] lines = new String[8];
    @Override public String name() { return "SIGN01"; }
    @Override public boolean online() { return true; }
    @Override public int lines() { return lines.length; }
    @Override public void write(int line, String text, boolean clear) {
        if (clear) java.util.Arrays.fill(lines, "");
        lines[Math.max(1, Math.min(line, lines.length)) - 1] = text;   // show it on your block
    }
}));
```

Only `SNDDSPTXT` uses this interface; the other display commands (regions, widgets, graphs, images) work on Display
Panels only.

The same `DeviceSources` pattern is used for `elcl.device.Printers` (`PrinterDevice`), `elcl.device.Diskettes`
(`DisketteDevice`) and `crafting.RecipeLibraries` (`RecipeLibrarySource`), which the Midrange line registers. The
repository's [docs/elcl/INTERFACES.md](docs/elcl/INTERFACES.md) describes what each device interface expects. Each
registry also has `unregister`.

## Gases and chemicals: `PressurizedSources` {#pressurized}

`storage.PressurizedSources.register(PressurizedSource)` lets the network store another mod's gases (or chemicals) as
**pressurized** resources, in Pressurized Storage Drives and the Pressurized tab of the terminals. Each source has its
own id, which is also the namespace players filter by, and its own unit; there is no conversion between sources.
Registering a second source with the same id replaces the first.

```java
public interface PressurizedSource {
    String id();                                   // "arcforge"
    boolean isGas(Fluid fluid);                    // is this fluid one of your gases?
    Optional<Fluid> fluid(Identifier gas);         // the fluid a gas is, for a mod whose gases are fluids
    Component name(Identifier gas);
    int color(Identifier gas);                     // 0xAARRGGBB, where the gas has no texture
    List<Identifier> all();                        // every gas, sorted by id, for the filter picker
    @Nullable Container container(ItemAccess access);   // a gas container item's tank, or null
}
```

A mod whose gases are NeoForge fluids (as Arcforge's are) gets them moved by every block that moves fluids, and the
network keeps them under its id instead of as fluids. `Container` is a single gas tank in an item (`gas()`,
`amount()`, `insert(...)`, `extract(...)`), so the terminals can fill and empty your gas items. The mod's own
implementation is `compat.arcforge.ArcforgeGases`.

## Machines: `MachineBridges.setAccess` {#machines}

`machine.MachineBridges.setAccess(MachineAccess)` sets how the {{item small_wireless_bridge}} reads and works the
machine it is on: whether a block is a machine, its `MachineInfo` (status, progress, energy, heat, tanks, statistics,
settings), switching it on and off, its redstone mode, side modes and auto-eject, its item and fluid handlers (for
Gateways), and a listener for its status changes and finished operations.

> [!WARNING] One slot, and Arcforge has it
> `setAccess` holds a single `MachineAccess`, not a list. The Arcforge integration sets it at startup when Arcforge is
> installed. A second machine mod calling it would replace Arcforge's, and every bridge would then talk to that mod
> instead. In practice the Small Wireless Bridge works with Arcforge machines only.

With nothing set (`MachineBridges.enabled()` is false), bridges are kept but do nothing.

## Things that are not extension points {#not}

- Audio: Speakers decode OGG and MP3 only, with no way to add a decoder or an audio source.
- The ELCL trigger events are fired inside the Terminal OS (`elcl.exec.ElclEvents`), not as NeoForge events.
- Other public classes (device interfaces such as `crafting.CraftingProvider` or `rack.TapeSource`) are the mod's
  own plumbing between its blocks, not meant for other mods.
