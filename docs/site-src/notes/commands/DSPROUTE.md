`DSPROUTE` opens **Display Route Table** for a {{item router}} on your network: every network it can reach, with the
next hop on the best path (direct, a network, or `IX:name` through an {{item exchange_switch}}), the path's cost and
hops, where the entry came from and how long it has been so. The top line names the Router, its mode and how many
routes it has. You type it at a command line; it doesn't run in a program.

| Source | Meaning |
|---|---|
| `*STATIC` | The Router's own {{item link_card}} links. They always win over learned routes. |
| `*CONNECTED` | A neighbouring Router's network. |
| `*DYNAMIC` | Learned through other Routers (a Router in `*DYNAMIC` mode). |

A note under the table says when a dynamic Router last reconverged. The screen refreshes every two seconds;
PageUp / PageDown roll it, **F11** switches to the Router's neighbours ({{cmd DSPNBR}}), and Enter goes back.

`RTR` is the Router's device name (F4 lists the devices). A name that isn't a Router on your network shows
{{msg ELC2601}}. In a program, {{cmd RTVRTE}} looks up one route. See {{page networking/routers}} and
{{page elcl/screens}}.

{{layout 23_dsproute}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
DSPROUTE RTR(RTR01)
```
