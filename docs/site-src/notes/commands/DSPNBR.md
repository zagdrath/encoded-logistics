`DSPNBR` opens **Display Neighbours** for a {{item router}}: the Routers it exchanges routes with, those on networks it
links to, that link to it, or that it peers with at an {{item exchange_switch}}. Each row shows how the neighbour is
reached, its state, the hop's cost, any cost override, and how long it has been up. The top line counts the neighbours
up and down. You type it at a command line; it doesn't run in a program.

| State | Meaning |
|---|---|
| `*INIT` | Coming up. |
| `*FULL` | In sync: routes are being exchanged. |
| `*DOWN` | It missed its hellos. Its cost shows `-`. |

The override is what {{cmd CHGNBR}} (or the Router's Table tab) set; `-` for none. A Router in `*STATIC` mode has no
neighbours, and the screen says so. It refreshes every two seconds; PageUp / PageDown roll it, **F10** switches to the
route table ({{cmd DSPROUTE}}), and Enter goes back.

`RTR` is the Router's device name (F4 lists the devices); a name that isn't a Router on your network shows
{{msg ELC2601}}. In a program, {{cmd RTVNBRSTS}} reads a neighbour's state. See {{page networking/routers}} and
{{page elcl/screens}}.

{{layout 24_dspnbr}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
DSPNBR RTR(RTR01)
```
