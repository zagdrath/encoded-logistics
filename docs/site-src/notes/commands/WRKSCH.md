`WRKSCH` opens **Work with Schematics**: the network's schematic holders ({{item fabricator}}s, {{item gateway}}s,
{{item fabrication_server}}s and {{item midrange_system}}s) and the schematics in them, a row each, with what each
makes, how many, whether it's a crafting or a processing schematic, and where the holder is. A holder with no
schematics gets a row of its own. You type it at a command line; it doesn't run in a program.

- **`HOLDER(*ALL)`**, the default, lists every holder. Give a holder's device name, or the start of its name, to see
  only that one. A name that matches nothing gives an empty list.
- A Midrange System's schematics are its diskettes' recipes, marked `R/O`: they can be shown but not removed.

{{screen WRKSCH}}

| Option | What it does |
|---|---|
| 4=Remove | Takes the schematic out of its holder into the {{item terminal_desk}}'s drawer, after you confirm ({{msg ELC2504}}). It needs the Firewall's build permission and a Terminal Desk, and room in its drawer. |
| 5=Display | What the schematic makes and from what. |
| 8=Locate | Outlines its holder in the world for ten seconds; its coordinates show on the message line. |

In a program, {{cmd RTVSCHLST}} lists schematics. See {{page crafting/schematics}} and {{page elcl/screens}}.

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKSCH
WRKSCH HOLDER(FAB01)
```
