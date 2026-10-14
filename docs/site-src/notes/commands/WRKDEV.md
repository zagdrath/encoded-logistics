`WRKDEV` opens **Work with Devices** on your terminal: every device on the network, with its type, name, location,
the lanes it uses and its status (Online, Offline dim, a fault or what it's busy with bright). It's Main Menu option 3.
Like every screen command, you type it at a command line; it doesn't run in a program.

{{screen WRKDEV}}

The **Device** column starts with the name scripts use for it (`CTLIF01`, `UPS01`...), given when the device first
joins a network (its type and the lowest number free) and kept with the device until it's renamed. A rack's devices
are listed under the rack's location.

| Option | What it does |
|---|---|
| 2=Change | A window to rename the device; it runs {{cmd RNMDEV}} with the new name. |
| 5=Display | The device's details: type, name, where it is, lanes and status. |
| 8=Locate | Picks the device out in the world for ten seconds. |

In a program, {{cmd RTVDEVLST}} and {{cmd RTVDEVSTS}} give the same information, and the system file `ELSYS/DEVICES`
holds it as records ({{cmd DSPPFM}}, {{cmd RUNQRY}}). The screen needs the Firewall's view permission. See
{{page elcl/screens}}.

```elcl nocompile="a command-line command: type it at the ===> prompt"
WRKDEV
```
