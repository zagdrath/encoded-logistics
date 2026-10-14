`WRKMCH` opens **Work with Machines**: the network's Arcforge machines that have a {{item small_wireless_bridge}} on
them, with each one's device name, machine type, status (bright when it needs attention), progress, energy and rate
(operations a minute). It's Main Menu option 9. You type it at a command line; it doesn't run in a program. Without
Arcforge, or with no bridged machines, the list is empty. See {{page integrations/arcforge}}.

| Option | What it does |
|---|---|
| 2=Change | A window with the machine's redstone mode, auto-eject, power from network and the Gateway that feeds it; F4 steps through each one's values. Enter runs {{cmd CHGMCHCFG}} with what you changed. |
| 5=Display | Everything about the machine: its statistics and settings. |
| 7=Enable/Disable | Turns it the other way to how it is ({{cmd CHGMCHSTS}}). |
| 8=Sides | For a single-block machine: a window with each side's mode, F4 stepping through the modes; {{cmd CHGMCHCFG}} for each side changed. |

Looking needs the Firewall's view permission; changing (2, 7, 8) needs its build permission, as the commands do. In
a program, {{cmd RTVMCHLST}}, {{cmd RTVMCHSTS}} and {{cmd RTVMCHSTAT}} read the same information. See
{{page elcl/screens}}.

{{screen WRKMCH}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKMCH
```
